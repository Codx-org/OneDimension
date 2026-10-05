package codx.onedimension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import codx.OneDimension;
import codx.customchunks.StackDefinition;
import codx.customchunks.Stacks;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/**
 * Makes the overworld the stack, in the worlds that chose to be one dimension.
 *
 * <p>The overworld rather than a dimension of its own, because the overworld is the world:
 * it is where players spawn and respawn, where time passes and raids come from. Stacked, it
 * is still the overworld to everything that asks — only taller, with every chosen
 * dimension a band of it. Its own generator is not lost; it generates the overworld band,
 * which keeps the overworld's own heights.
 */
public final class OneWorld {
	private static volatile WorldChoice current = WorldChoice.OFF;

	private static volatile StackConfig.Defaults defaults;

	private OneWorld() {
	}

	public static void init() {
		Stacks.replace(Level.OVERWORLD, OneWorld::stackFor);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> current = WorldChoice.OFF);
	}

	/** The running world's choice; {@link WorldChoice#OFF} when no world is running. */
	public static WorldChoice current() {
		return current;
	}

	/** The defaults read when the running world started. */
	public static StackConfig.Defaults defaults() {
		return defaults;
	}

	private static Optional<StackDefinition> stackFor(MinecraftServer server) {
		// Read before the overworld is replaced, while every dimension is still itself.
		StackConfig.Defaults read = StackConfig.read(Dimensions.present(server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM)));
		boolean isNew = !server.getWorldData().overworldData().isInitialized();
		WorldChoice choice = WorldChoice.forWorld(server, isNew, read.choice());
		defaults = read;
		current = choice;
		// Now, not once the server has started: the chunks around spawn load before that.
		OpenFloors.becomes(choice.bedrock());
		OpenFloors.keptIn(read.keepsIts());

		if (!choice.enabled()) {
			return Optional.empty();
		}

		List<ResourceKey<Level>> order = new ArrayList<>(choice.order());

		if (!order.contains(Level.OVERWORLD)) {
			// The world itself cannot be left out of the world.
			order.addFirst(Level.OVERWORLD);
		}

		OneDimension.LOGGER.info("One dimension, top to bottom: {}{}",
				order.stream().map(key -> key.identifier().toString()).toList(), choice.loop() ? ", joined bottom to top" : "");
		// Spawning in a world left out of the stack is spawning in the overworld.
		Optional<ResourceKey<Level>> spawn = Optional.of(order.contains(choice.spawn()) ? choice.spawn() : Level.OVERWORLD);
		return Optional.of(new StackDefinition(order, Optional.of(Level.OVERWORLD), false, List.of(),
				0, choice.gapsBelow(), choice.trim(), choice.separateSkies(), spawn, choice.scalePortals()));
	}
}
