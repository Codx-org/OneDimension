package codx.onedimension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import codx.OneDimension;

import codx.planeshift.plane.Plane;
import codx.planeshift.plane.PlaneBoundary;
import codx.planeshift.plane.PlaneShape;
import codx.planeshift.registry.Planes;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

/**
 * The worlds as one column: every dimension the game has, stacked, each joined to the one
 * below it.
 *
 * <p>A join is a pair of edgeless planes — one in each world, at the height the upper one
 * is left through and the height the lower one is entered at. Falling out of the bottom
 * of a world puts you at the top of the next, still falling, because that is all a
 * Planeshift crossing does: it carries momentum across rather than stopping to arrive.
 *
 * <p>The planes are handed to Planeshift as a provider rather than stored there. Nothing
 * about the column lives in the library: it asks for this world's planes and this decides,
 * every time, from the order as it stands.
 */
public final class Column {
	private static final Map<ResourceKey<Level>, List<Plane>> PLANES = new HashMap<>();

	private Column() {
	}

	public static void init() {
		// One provider for every dimension; it answers with whatever this column says
		// belongs in the one being asked about.
		Planes.registerGlobal((level, out) ->
				PLANES.getOrDefault(level.dimension(), List.of()).forEach(out));
	}

	/** Works out the column for this server and tells Planeshift to forget what it knew. */
	public static void build(MinecraftServer server) {
		List<ResourceKey<Level>> present = new ArrayList<>();

		for (ServerLevel level : server.getAllLevels()) {
			present.add(level.dimension());
		}

		StackConfig.Stack stack = StackConfig.read(present);
		List<StackConfig.Layer> layers = stack.layers();
		OpenFloors.becomes(stack.bedrock());
		PLANES.clear();

		// One join per pair of neighbours, and one more from the last back to the first when
		// the column loops: the bottom of the world is the top of the world, and a fall
		// through everything is a fall through everything again. A column of one loops onto
		// itself, which is a world whose floor is its own sky — odd, and asked for.
		int joins = stack.loop() ? layers.size() : layers.size() - 1;

		for (int i = 0; i < joins; i++) {
			StackConfig.Layer above = layers.get(i);
			StackConfig.Layer below = layers.get((i + 1) % layers.size());
			ServerLevel upper = server.getLevel(above.dimension());
			ServerLevel lower = server.getLevel(below.dimension());

			if (upper == null || lower == null) {
				continue;
			}

			int leave = above.leave().orElseGet(() -> floor(upper));
			int arrive = below.arrive().orElseGet(() -> ceiling(lower));
			join(upper, leave, lower, arrive);
			OneDimension.LOGGER.info("{} at y {} falls into {} at y {}",
					upper.dimension().identifier(), leave, lower.dimension().identifier(), arrive);
		}

		// The providers are the same objects; what they answer has changed.
		Planes.invalidateGlobal();
		OneDimension.LOGGER.info("Stacked {} world(s) into one column{}", layers.size(),
				stack.loop() ? ", joined bottom to top" : "");
	}

	/**
	 * Joins two worlds at a height in each.
	 *
	 * <p>The upper plane is met from above, so it faces up; the lower one is met from
	 * below coming back, so it faces down. {@link PlaneBoundary} works the transform
	 * between them out from that.
	 */
	private static void join(ServerLevel upper, int leave, ServerLevel lower, int arrive) {
		PlaneBoundary boundary = PlaneBoundary.between(
				upper.dimension(), PlaneShape.Infinite.atSurface(leave, Direction.UP), Direction.UP,
				lower.dimension(), PlaneShape.Infinite.atSurface(arrive, Direction.DOWN), Direction.DOWN,
				Rotation.NONE);

		PLANES.computeIfAbsent(upper.dimension(), key -> new ArrayList<>()).add(boundary.a());
		PLANES.computeIfAbsent(lower.dimension(), key -> new ArrayList<>()).add(boundary.b());
	}

	/** The height a world is left through: its own floor, unless told otherwise. */
	private static int floor(ServerLevel level) {
		return level.dimensionType().minY();
	}

	/**
	 * The height a world is entered at: one past its highest placeable block.
	 *
	 * <p>Worth overriding where a world's own ceiling is solid. The nether's is bedrock,
	 * so arriving at it puts you inside the roof rather than under it — a dark, blank
	 * view and nowhere to fall to.
	 */
	private static int ceiling(ServerLevel level) {
		return level.dimensionType().minY() + level.dimensionType().logicalHeight();
	}
}
