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
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import codx.OneDimension;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

/**
 * One world's answer: whether it is one dimension, what it is stacked from, and whether
 * the bottom joins the top.
 *
 * <p>Made once, when the world is created, and kept in the world's folder as
 * {@code one-dimension.json}. A world without one was made without this mod, or with it
 * turned off, and is left exactly as it is.
 *
 * @param enabled       whether the overworld is the stack
 * @param order         the dimensions stacked, top first; always includes the overworld
 * @param loop          whether falling out of the bottom arrives at the top
 * @param gapsBelow     for each stacked world, the empty blocks between it and the world below it
 * @param trim          whether each world is only as tall as its players can use — the
 *                      Nether up to its roof, not the empty half above it
 * @param separateSkies whether each world has its own sky, so one above casts no shadow on
 *                      one below, and one without a sky stays dark
 * @param bedrock       what bedrock floors and roofs become, as a block id, or empty to keep them
 * @param spawn         the world players spawn in, and respawn in without a bed of their own
 * @param scalePortals  whether nether portals take you eight overworld blocks for every nether
 *                      block, as in the game, or straight up and down
 */
public record WorldChoice(boolean enabled, List<ResourceKey<Level>> order, boolean loop, Map<ResourceKey<Level>, Integer> gapsBelow,
		boolean trim, boolean separateSkies, String bedrock, ResourceKey<Level> spawn, boolean scalePortals) {
	private static final String FILE = "one-dimension.json";

	/** What a world made without this mod is. */
	public static final WorldChoice OFF = new WorldChoice(false, List.of(), false, Map.of(), false, false, "", Level.OVERWORLD, true);

	/**
	 * The choice made on the create-world screen, waiting for the world it was made for to
	 * start. Set on the client, taken by the integrated server in the same game.
	 */
	private static volatile @Nullable WorldChoice pending;

	public WorldChoice {
		order = List.copyOf(order);
		gapsBelow = Map.copyOf(gapsBelow);
	}

	/** Called by the create-world screen, just before the world is created. */
	public static void choose(WorldChoice choice) {
		pending = choice;
	}

	/**
	 * This world's choice, deciding it now if the world is new.
	 *
	 * @param isNew    whether the world is being created, rather than opened again
	 * @param fallback what a new world made without the screen chooses
	 */
	public static WorldChoice forWorld(MinecraftServer server, boolean isNew, WorldChoice fallback) {
		Path file = server.getWorldPath(LevelResource.ROOT).resolve(FILE);
		Optional<WorldChoice> kept = read(file);

		if (kept.isPresent()) {
			return kept.get();
		}

		if (!isNew) {
			// Made before this mod was installed. Turning it into a stack now would bury
			// what was built in it under whatever the other bands generate.
			return OFF;
		}

		WorldChoice choice = pending;
		pending = null;

		if (choice == null) {
			// No screen: a dedicated server, or a world created by another mod's code.
			choice = fallback;
		}

		write(file, choice);
		return choice;
	}

	private static Optional<WorldChoice> read(Path file) {
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			List<ResourceKey<Level>> order = new ArrayList<>();

			for (JsonElement element : root.getAsJsonArray("order")) {
				order.add(dimension(element.getAsString()));
			}

			// Worlds made before the later settings existed were made edge to edge, full height,
			// under one sky, with their bedrock turned to obsidian.
			Map<ResourceKey<Level>, Integer> gaps = new LinkedHashMap<>();

			if (root.has("gaps_below")) {
				root.getAsJsonObject("gaps_below").entrySet().forEach(entry -> gaps.put(dimension(entry.getKey()), entry.getValue().getAsInt()));
			} else if (root.has("gap")) {
				// One space for every pair, as worlds were made before each pair had its own.
				order.forEach(key -> gaps.put(key, root.get("gap").getAsInt()));
			}

			return Optional.of(new WorldChoice(root.get("enabled").getAsBoolean(), order, root.get("loop").getAsBoolean(), gaps,
					root.has("trim") && root.get("trim").getAsBoolean(),
					root.has("separate_skies") && root.get("separate_skies").getAsBoolean(),
					root.has("bedrock_becomes") ? root.get("bedrock_becomes").getAsString() : "minecraft:obsidian",
					root.has("spawn") ? dimension(root.get("spawn").getAsString()) : Level.OVERWORLD,
					!root.has("scale_portals") || root.get("scale_portals").getAsBoolean()));
		} catch (NoSuchFileException absent) {
			return Optional.empty();
		} catch (IOException | RuntimeException e) {
			// Better to refuse than to guess: a wrong guess stacks a world that was not, or
			// unstacks one that was.
			throw new IllegalStateException("Could not read this world's " + file, e);
		}
	}

	private static ResourceKey<Level> dimension(String id) {
		return ResourceKey.create(Registries.DIMENSION, Identifier.parse(id));
	}

	private static void write(Path file, WorldChoice choice) {
		JsonObject root = new JsonObject();
		root.addProperty("_about", "Chosen when this world was created. Its layout is fixed now that it has "
				+ "generated; changing the order here does not move what is already there.");
		root.addProperty("enabled", choice.enabled());
		JsonArray order = new JsonArray();
		choice.order().forEach(key -> order.add(key.identifier().toString()));
		root.add("order", order);
		root.addProperty("loop", choice.loop());
		JsonObject gaps = new JsonObject();
		choice.gapsBelow().forEach((key, blocks) -> gaps.addProperty(key.identifier().toString(), blocks));
		root.add("gaps_below", gaps);
		root.addProperty("trim", choice.trim());
		root.addProperty("separate_skies", choice.separateSkies());
		root.addProperty("bedrock_becomes", choice.bedrock());
		root.addProperty("spawn", choice.spawn().identifier().toString());
		root.addProperty("scale_portals", choice.scalePortals());

		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
		} catch (IOException e) {
			OneDimension.LOGGER.error("Could not write {}", file, e);
		}
	}
}
