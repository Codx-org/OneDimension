package codx.onedimension;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

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
 * The order the worlds are stacked in, and where each one is entered and left.
 *
 * <p>Written out on first run with every dimension the game has, so a pack that adds one
 * gets it in the list rather than silently left out of the world. Order in the file is
 * order in the column, top first; heights are optional and default to each dimension's
 * own limits.
 */
public final class StackConfig {
	private static final String FILE = "one-dimension.json";

	private static final String ORDER = "order";

	private static final String LOOP = "loop";

	private static final String BEDROCK = "bedrock_becomes";

	/** What bedrock turns into, unless the file says otherwise. */
	private static final String BEDROCK_BY_DEFAULT = "minecraft:obsidian";

	/** Whether the column joins back on itself, unless the file says otherwise. */
	private static final boolean LOOP_BY_DEFAULT = true;

	/**
	 * One world's place in the column.
	 *
	 * @param leave  the height you fall out of this world through
	 * @param arrive the height you appear at, coming in from the world above
	 */
	public record Layer(ResourceKey<Level> dimension, OptionalInt leave, OptionalInt arrive) {
	}

	/**
	 * The column as configured: the worlds in order, and whether the bottom joins the top.
	 *
	 * @param layers top first
	 * @param loop   whether falling out of the last world arrives at the top of the first.
	 *               A column that ends has an end you can fall out of and never come back
	 *               from; one that joins back on itself has neither a bottom nor a top, and
	 *               a fall through all of it is a fall through all of it again.
	 * @param bedrock what bedrock becomes as chunks load, or empty to leave it alone
	 */
	public record Stack(List<Layer> layers, boolean loop, String bedrock) {
	}

	private StackConfig() {
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE);
	}

	/**
	 * The configured column, top first, filtered to the dimensions that actually exist.
	 *
	 * @param present every dimension the running game has, in the order it reports them,
	 *                used to write a starting file and to add newcomers to an existing one
	 */
	public static Stack read(List<ResourceKey<Level>> present) {
		Map<ResourceKey<Level>, Layer> configured = new LinkedHashMap<>();
		boolean loop = LOOP_BY_DEFAULT;
		String bedrock = BEDROCK_BY_DEFAULT;

		try (Reader reader = Files.newBufferedReader(path(), StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

			if (root.has(LOOP)) {
				loop = root.get(LOOP).getAsBoolean();
			}

			if (root.has(BEDROCK)) {
				bedrock = root.get(BEDROCK).getAsString();
			}

			for (JsonElement element : root.getAsJsonArray(ORDER)) {
				JsonObject entry = element.getAsJsonObject();
				ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
						Identifier.parse(entry.get("dimension").getAsString()));
				configured.put(dimension, new Layer(dimension,
						height(entry, "leave"), height(entry, "arrive")));
			}
		} catch (NoSuchFileException absent) {
			// First run: everything the game has, in the order it has it.
		} catch (IOException | RuntimeException e) {
			OneDimension.LOGGER.warn("Could not read {}; stacking nothing", path(), e);
			return new Stack(List.of(), false, "");
		}

		List<Layer> layers = new ArrayList<>();

		for (Layer layer : configured.values()) {
			// A dimension named in the file but not in the game is not an error: packs
			// come and go, and the rest of the column still works without it.
			if (present.contains(layer.dimension())) {
				layers.add(layer);
			} else {
				OneDimension.LOGGER.info("{} is in the order but not in this game; skipping it",
						layer.dimension().identifier());
			}
		}

		for (ResourceKey<Level> dimension : present) {
			if (!configured.containsKey(dimension)) {
				OneDimension.LOGGER.info("{} is new; adding it to the bottom of the order",
						dimension.identifier());
				layers.add(new Layer(dimension, OptionalInt.empty(), OptionalInt.empty()));
			}
		}

		write(layers, loop, bedrock);
		return new Stack(layers, loop, bedrock);
	}

	private static OptionalInt height(JsonObject entry, String key) {
		return entry.has(key) ? OptionalInt.of(entry.get(key).getAsInt()) : OptionalInt.empty();
	}

	/** Writes the order back, so what the game decided is visible and editable. */
	private static void write(List<Layer> layers, boolean loop, String bedrock) {
		JsonObject root = new JsonObject();
		root.addProperty("_order", "Top to bottom. Fall out of the bottom of one world and you "
				+ "arrive at the top of the next.");
		root.addProperty("_heights", "Optional per entry: \"leave\" is the height you fall out "
				+ "through, \"arrive\" the height you come in at. Without them each world's own "
				+ "limits are used.");
		root.addProperty("_loop", "Whether the bottom of the last world joins the top of the "
				+ "first, so a fall through the column never ends. Off, the last world's floor "
				+ "is simply its floor.");
		root.addProperty(LOOP, loop);
		root.addProperty("_" + BEDROCK, "What bedrock turns into as chunks load, so the floors "
				+ "the column is joined at can be dug through. Empty leaves bedrock alone.");
		root.addProperty(BEDROCK, bedrock);
		JsonArray order = new JsonArray();

		for (Layer layer : layers) {
			JsonObject entry = new JsonObject();
			entry.addProperty("dimension", layer.dimension().identifier().toString());
			layer.leave().ifPresent(y -> entry.addProperty("leave", y));
			layer.arrive().ifPresent(y -> entry.addProperty("arrive", y));
			order.add(entry);
		}

		root.add(ORDER, order);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();

		try (Writer writer = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
			gson.toJson(root, writer);
		} catch (IOException e) {
			OneDimension.LOGGER.warn("Could not write {}", path(), e);
		}
	}
}
