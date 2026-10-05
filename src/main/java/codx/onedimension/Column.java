package codx.onedimension;

import java.util.ArrayList;
import java.util.List;

import codx.OneDimension;
import codx.customchunks.Stack;
import codx.customchunks.Stacks;

import codx.planeshift.crossing.CrossingDetector;
import codx.planeshift.plane.Plane;
import codx.planeshift.plane.PlaneBoundary;
import codx.planeshift.plane.PlaneShape;
import codx.planeshift.registry.Planes;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Where the one dimension joins back on itself: the bottom of the stack to the top.
 *
 * <p>Inside the stack nothing needs joining — the worlds are one level, and falling out of
 * the bottom of one is falling into the top of the next. Only the ends are ends. When the
 * world loops, a pair of edgeless planes makes them one: falling out of the bottom of the
 * lowest world puts you at the top of the highest, still falling, because that is all a
 * Planeshift crossing does — it carries momentum across rather than stopping to arrive.
 *
 * <p>The planes are handed to Planeshift as a provider rather than stored there; it asks
 * for this world's planes and this answers from the stack as it is.
 */
public final class Column {
	private static final List<Plane> PLANES = new ArrayList<>();

	/**
	 * How far past an end of the world something is before it is moved regardless. A crossing
	 * happens as the eye passes the plane; this far past it, the crossing did not happen.
	 */
	private static final double PAST = 2.0;

	/** The ends of the looping world, or null when the world does not loop. */
	private static volatile Stack looping;

	private Column() {
	}

	public static void init() {
		Planes.registerGlobal((level, out) -> {
			if (level.dimension().equals(Level.OVERWORLD)) {
				PLANES.forEach(out);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Column::catchWhatFellThrough);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> looping = null);
	}

	/**
	 * Moves whatever is past an end of a looping world to the other end, whatever it is and
	 * however it got there.
	 *
	 * <p>The plane is a surface with no thickness, and a crossing is noticed by something
	 * passing through it. Nearly everything does — but a step too long to be taken for
	 * movement, a crossing a player's game claimed and the server never finished, or anything
	 * the planes are told not to carry, is then under the world with nothing below it but the
	 * void. In a world that joins back on itself there is no under the world: being there is
	 * reason enough to be at the top, the same distance in, still falling as fast.
	 */
	private static void catchWhatFellThrough(MinecraftServer server) {
		Stack stack = looping;

		if (stack == null) {
			return;
		}

		ServerLevel level = server.overworld();
		List<Entity> fallen = new ArrayList<>();

		for (Entity entity : level.getAllEntities()) {
			// A rider goes where what it rides goes. The eye for a player, as for a crossing.
			double y = entity instanceof ServerPlayer ? entity.getEyeY() : entity.getY();

			if (!entity.isPassenger() && !entity.isRemoved() && (y < stack.minY() - PAST || y > stack.maxYExclusive() + PAST)) {
				fallen.add(entity);
			}
		}

		for (Entity entity : fallen) {
			double y = entity instanceof ServerPlayer ? entity.getEyeY() : entity.getY();
			Vec3 to = entity.position().add(0.0, y < stack.minY() ? stack.height() : -stack.height(), 0.0);

			if (entity instanceof ServerPlayer player) {
				// A player's speed is their own game's; asked for relatively, it is left as it is.
				ServerPlayer moved = player.teleport(new TeleportTransition(level, to, Vec3.ZERO, player.getYRot(), player.getXRot(),
						Relative.DELTA, TeleportTransition.DO_NOTHING));

				if (moved != null) {
					// Or the next tick measures a step from under the world to the top of it.
					CrossingDetector.resync(moved);
				}
			} else {
				entity.teleport(new TeleportTransition(level, to, entity.getDeltaMovement(), entity.getYRot(), entity.getXRot(),
						TeleportTransition.DO_NOTHING));
			}
		}
	}

	/** Works out the join for this server's world and tells Planeshift to forget what it knew. */
	public static void build(MinecraftServer server) {
		PLANES.clear();
		looping = null;
		WorldChoice choice = OneWorld.current();
		ServerLevel overworld = server.overworld();
		Stack stack = Stacks.of(overworld).orElse(null);

		if (choice.enabled() && choice.loop() && stack != null) {
			// The bottom plane is met from above, so it faces up; the top one is met from
			// below coming back, so it faces down. PlaneBoundary works the transform out.
			PlaneBoundary boundary = PlaneBoundary.between(
					Level.OVERWORLD, PlaneShape.Infinite.atSurface(stack.minY(), Direction.UP), Direction.UP,
					Level.OVERWORLD, PlaneShape.Infinite.atSurface(stack.maxYExclusive(), Direction.DOWN), Direction.DOWN,
					Rotation.NONE);
			PLANES.add(boundary.a());
			PLANES.add(boundary.b());
			looping = stack;
			OneDimension.LOGGER.info("Falling out of the bottom at y {} arrives at the top at y {}", stack.minY(), stack.maxYExclusive());
		}

		// The provider is the same object; what it answers has changed.
		Planes.invalidateGlobal();
	}
}
