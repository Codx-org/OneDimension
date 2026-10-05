package codx.onedimension;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import codx.OneDimension;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * What a new world starts with, and what happens to bedrock in a stacked one.
 *
 * <p>The order and the loop here are defaults. Each world keeps its own choice (see
 * {@link WorldChoice}), made on the create-world screen or, on a dedicated server, taken
 * from here when the world is made. Changing this file changes new worlds, never old ones:
 * a world's layout is fixed once it has generated.
 *
 * <p>Written out on first run with every dimension the game has, so a pack that adds one
 * gets it in the list rather than silently left out.
 */
public final class StackConfig {
	private static final String FILE = "one-dimension.json";

	private static final String ENABLED = "one_dimension_on_new_worlds";

	private static final String ORDER = "order";

	private static final String LEFT_OUT = "left_out";

	private static final String LOOP = "loop";

	private static final String GAP = "gap";

	private static final String TRIM = "trim";

	private static final String SKIES = "separate_skies";

	private static final String BEDROCK = "bedrock_becomes";

	private static final String KEEP_BEDROCK = "bedrock_kept_in";

	/**
	 * Worlds whose bedrock is left alone, whatever {@code bedrock_becomes} says.
	 *
	 * <p>The End by default. Nothing there needs digging through — step off the island and
	 * you are already falling out of the bottom of it — and its bedrock is not a floor but
	 * scenery: the frame of the exit portal, and the feet of the obsidian pillars.
	 */
	private static final List<String> KEEP_BEDROCK_BY_DEFAULT = List.of("minecraft:the_end");

	/** What bedrock turns into, unless the file says otherwise. */
	private static final String BEDROCK_BY_DEFAULT = "minecraft:obsidian";

	/** Whether the column joins back on itself, unless the file says otherwise. */
	private static final boolean LOOP_BY_DEFAULT = true;

	/**
	 * The defaults, as configured.
	 *
	 * @param enabled  whether a world made without the create-world screen — on a dedicated
	 *                 server — is one dimension
	 * @param order    every dimension, top first
	 * @param leftOut  dimensions not stacked by default
	 * @param loop     whether falling out of the bottom arrives at the top
	 * @param gap      empty blocks between each two worlds, to start with
	 * @param trim     whether worlds are only as tall as their players can use
	 * @param skies    whether each world has its own sky
	 * @param bedrock  what bedrock becomes as chunks load, or empty to leave it alone
	 * @param keepsIts the dimensions whose bands keep their bedrock regardless, by id
	 */
	public record Defaults(boolean enabled, List<ResourceKey<Level>> order, Set<ResourceKey<Level>> leftOut, boolean loop,
			int gap, boolean trim, boolean skies, String bedrock, List<String> keepsIts) {
		/** The world choice these defaults make, for a world made without the screen. */
		public WorldChoice choice() {
			List<ResourceKey<Level>> stacked = this.order.stream().filter(key -> !this.leftOut.contains(key)).toList();
			Map<ResourceKey<Level>, Integer> gaps = new LinkedHashMap<>();
			stacked.forEach(key -> gaps.put(key, this.gap));
			return new WorldChoice(this.enabled, stacked, this.loop, gaps, this.trim, this.skies, this.bedrock, Level.OVERWORLD, true);
		}
	}

	private StackConfig() {
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE);
	}

	/**
	 * The configured defaults, with the order filtered to the dimensions that exist and
	 * every dimension the file does not name yet added at the bottom.
	 *
	 * @param present every dimension the game has, in the order it reports them
	 */
	public static Defaults read(List<ResourceKey<Level>> present) {
		boolean enabled = true;
		Set<ResourceKey<Level>> configured = new LinkedHashSet<>();
		Set<ResourceKey<Level>> leftOut = new LinkedHashSet<>();
		boolean loop = LOOP_BY_DEFAULT;
		int gap = 0;
		boolean trim = true;
		boolean skies = true;
		String bedrock = BEDROCK_BY_DEFAULT;
		List<String> keepsIts = KEEP_BEDROCK_BY_DEFAULT;

		try (Reader reader = Files.newBufferedReader(path(), StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

			if (root.has(ENABLED)) {
				enabled = root.get(ENABLED).getAsBoolean();
			}

			if (root.has(LOOP)) {
				loop = root.get(LOOP).getAsBoolean();
			}

			if (root.has(GAP)) {
				gap = root.get(GAP).getAsInt();
			}

			if (root.has(TRIM)) {
				trim = root.get(TRIM).getAsBoolean();
			}

			if (root.has(SKIES)) {
				skies = root.get(SKIES).getAsBoolean();
			}

			if (root.has(BEDROCK)) {
				bedrock = root.get(BEDROCK).getAsString();
			}

			if (root.has(KEEP_BEDROCK)) {
				List<String> named = new ArrayList<>();

				for (JsonElement element : root.getAsJsonArray(KEEP_BEDROCK)) {
					named.add(element.getAsString());
				}

				keepsIts = named;
			}

			if (root.has(ORDER)) {
				for (JsonElement element : root.getAsJsonArray(ORDER)) {
					// Entries were objects with heights once; the heights are the stack's now.
					String id = element.isJsonObject() ? element.getAsJsonObject().get("dimension").getAsString() : element.getAsString();
					configured.add(dimension(id));
				}
			}

			if (root.has(LEFT_OUT)) {
				for (JsonElement element : root.getAsJsonArray(LEFT_OUT)) {
					leftOut.add(dimension(element.getAsString()));
				}
			}
		} catch (NoSuchFileException absent) {
			// First run: everything the game has, in the order it has it.
		} catch (IOException | RuntimeException e) {
			OneDimension.LOGGER.warn("Could not read {}; using the defaults", path(), e);
		}

		List<ResourceKey<Level>> order = new ArrayList<>();

		for (ResourceKey<Level> dimension : configured) {
			// A dimension named in the file but not in the game is not an error: packs come
			// and go, and the rest of the column still works without it.
			if (present.contains(dimension)) {
				order.add(dimension);
			}
		}

		for (ResourceKey<Level> dimension : present) {
			if (!order.contains(dimension)) {
				order.add(dimension);
			}
		}

		Defaults defaults = new Defaults(enabled, List.copyOf(order), Set.copyOf(leftOut), loop, gap, trim, skies, bedrock, List.copyOf(keepsIts));
		write(defaults, configured);
		return defaults;
	}

	private static ResourceKey<Level> dimension(String id) {
		return ResourceKey.create(Registries.DIMENSION, Identifier.parse(id));
	}

	/**
	 * Writes the defaults back, so what the game decided is visible and editable, keeping
	 * dimensions the file named that are not in this game — a pack may bring them back.
	 */
	private static void write(Defaults defaults, Set<ResourceKey<Level>> named) {
		JsonObject root = new JsonObject();
		root.addProperty("_about", "Defaults for new worlds. Each world keeps its own order and loop, chosen when it is "
				+ "created; changing this file never changes a world that exists.");
		root.addProperty("_" + ENABLED, "Whether a world created without the create-world screen, on a dedicated "
				+ "server, is one dimension.");
		root.addProperty(ENABLED, defaults.enabled());
		root.addProperty("_" + ORDER, "Top to bottom. Fall out of the bottom of one world and you arrive at the top of "
				+ "the next.");
		JsonArray order = new JsonArray();
		Set<ResourceKey<Level>> all = new LinkedHashSet<>(defaults.order());
		all.addAll(named);
		all.forEach(key -> order.add(key.identifier().toString()));
		root.add(ORDER, order);
		root.addProperty("_" + LEFT_OUT, "Dimensions not stacked by default. The overworld is always stacked: it is "
				+ "the world itself.");
		JsonArray leftOut = new JsonArray();
		defaults.leftOut().forEach(key -> leftOut.add(key.identifier().toString()));
		root.add(LEFT_OUT, leftOut);
		root.addProperty("_" + LOOP, "Whether the bottom of the last world joins the top of the first, so a fall "
				+ "through the column never ends. Off, the last world's floor is simply its floor.");
		root.addProperty(LOOP, defaults.loop());
		root.addProperty("_" + GAP, "Empty blocks between each two neighbouring worlds, to start with; each pair can be "
				+ "set on its own when creating a world. Rounded up to a multiple of 16. 0 puts the floor of one right on the "
				+ "roof of the next.");
		root.addProperty(GAP, defaults.gap());
		root.addProperty("_" + TRIM, "Whether each world is only as tall as its players can use: the Nether up to its "
				+ "roof, without the empty half above it.");
		root.addProperty(TRIM, defaults.trim());
		root.addProperty("_" + SKIES, "Whether each world has its own sky: one above casts no shadow on one below, "
				+ "and one without a sky stays dark whatever is above it.");
		root.addProperty(SKIES, defaults.skies());
		root.addProperty("_" + BEDROCK, "What bedrock floors and roofs turn into as chunks load, so they can be dug "
				+ "through. Empty leaves bedrock alone.");
		root.addProperty(BEDROCK, defaults.bedrock());
		root.addProperty("_" + KEEP_BEDROCK, "Worlds that keep their bedrock whatever bedrock_becomes says. The End by "
				+ "default: nothing there needs digging through, and its bedrock is scenery rather than a floor.");
		JsonArray kept = new JsonArray();
		defaults.keepsIts().forEach(kept::add);
		root.add(KEEP_BEDROCK, kept);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();

		try (Writer writer = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
			gson.toJson(root, writer);
		} catch (IOException e) {
			OneDimension.LOGGER.warn("Could not write {}", path(), e);
		}
	}
}
