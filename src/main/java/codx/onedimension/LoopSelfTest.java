package codx.onedimension;

import codx.OneDimension;
import codx.customchunks.Stack;
import codx.customchunks.Stacks;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;

/**
 * Checks, headlessly, that nothing stays under a looping world. Run a server with
 * {@code -Done-dimension.selftest=true}: a pig is put ten blocks under the world, the server
 * ticks for a second, and the pig must be near the top of the world, not under it.
 */
public final class LoopSelfTest {
	private static Entity pig;

	private static int ticks;

	private static net.minecraft.server.level.ServerPlayer flyer;

	private static long last;

	private static long worst;

	private static long total;

	private LoopSelfTest() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel level = server.overworld();
			Stack stack = Stacks.of(level).orElseThrow();
			level.setChunkForced(0, 0, true);
			level.getChunk(0, 0);
			pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
			pig.setNoGravity(true);
			pig.snapTo(8.5, stack.minY() - 10, 8.5, 0, 0);
			level.addFreshEntity(pig);
			OneDimension.LOGGER.info("SELFTEST pig put at y {}, ten under the world (Y {} to {})", pig.getY(), stack.minY(), stack.maxYExclusive() - 1);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (pig != null && ++ticks == 20) {
				Stack stack = Stacks.of(server.overworld()).orElseThrow();
				boolean moved = pig.getY() > stack.maxYExclusive() - 16 && pig.getY() < stack.maxYExclusive();
				OneDimension.LOGGER.info("SELFTEST pig is now at y {}: {}", pig.getY(), moved ? "PASSED" : "FAILED");

				if (!Boolean.getBoolean("one-dimension.selftest.load")) {
					server.halt(false);
					return;
				}

				// A player, as far as the server can tell: chunks load and are sent around it, and
				// mobs spawn near it. It flies east, into ground nobody has generated.
				flyer = net.fabricmc.fabric.api.entity.FakePlayer.get(server.overworld());
				flyer.snapTo(0.5, 120, 0.5, 0, 0);
				server.overworld().addNewPlayer(flyer);
				last = System.nanoTime();
			}

			if (flyer != null) {
				long now = System.nanoTime();
				worst = Math.max(worst, now - last);
				total += now - last;
				last = now;
				flyer.snapTo(flyer.getX() + 0.6, 120, 0.5, 0, 0);
				server.overworld().getChunkSource().move(flyer);

				if (ticks % 100 == 0) {
					OneDimension.LOGGER.info("SELFTEST tick {}: {} ms a tick on average, worst {} ms, {} chunks loaded, {} entities", ticks,
							String.format("%.1f", total / 100 / 1.0e6), String.format("%.0f", worst / 1.0e6),
							server.overworld().getChunkSource().getLoadedChunksCount(),
							com.google.common.collect.Iterables.size(server.overworld().getAllEntities()));
					worst = 0;
					total = 0;
				}
			}

			if (pig != null && ticks == 1500) {
				server.halt(false);
			}
		});
	}
}
