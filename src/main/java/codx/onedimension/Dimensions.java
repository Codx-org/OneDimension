package codx.onedimension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import codx.customchunks.gen.StackedChunkGenerator;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;

/**
 * The dimensions a world could be stacked from.
 */
public final class Dimensions {
	private Dimensions() {
	}

	/** Every dimension of a registry that could be a band, in the game's own order. */
	public static List<ResourceKey<Level>> present(Registry<LevelStem> stems) {
		return present(stems.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
	}

	/**
	 * Every dimension that could be a band, in the game's own order: the overworld, the
	 * nether and the end first, then the rest.
	 */
	public static List<ResourceKey<Level>> present(Map<ResourceKey<LevelStem>, LevelStem> stems) {
		Set<ResourceKey<LevelStem>> candidates = stems.entrySet().stream()
				// A stack is made of dimensions; it is not one to stack.
				.filter(entry -> !(entry.getValue().generator() instanceof StackedChunkGenerator))
				.map(Map.Entry::getKey)
				.collect(Collectors.toSet());
		return WorldDimensions.keysInOrder(candidates).map(Registries::levelStemToLevel).toList();
	}
}
