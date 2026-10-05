package codx.onedimension;

import java.util.List;
import java.util.Set;

import codx.OneDimension;
import codx.customchunks.Band;
import codx.customchunks.Stack;
import codx.customchunks.Stacks;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;

import net.minecraft.core.SectionPos;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Turns the bedrock out of the column into something you can get through.
 *
 * <p>A stack of worlds joined at their floors is only a stack if you can reach the join.
 * Every one of those floors is bedrock, and so is the Nether's roof — which leaves the
 * column perfectly built and perfectly sealed, and the only way down a hole somebody made
 * with a command.
 *
 * <p>So bedrock becomes obsidian: still the hardest thing in the world, still worth the
 * walk back for a diamond pickaxe, and no longer a wall the game refuses to let you past.
 * Both are full, opaque, blast-resistant blocks, so nothing else about the world changes —
 * light does not move, mobs do not spawn where they could not, and the floor still holds
 * the sea up.
 *
 * <p>Done as chunks load rather than as they generate, so a world that already exists is
 * opened up the first time each of its chunks is visited, not only the ground nobody has
 * walked on yet. It is not a generation change and makes no claim to be one: the bedrock
 * was generated, and this puts something else in its place.
 *
 * <p>Cost is a palette question per section — "does anything in here look like bedrock" —
 * which for all but a handful of sections is a no, and those are the only ones walked
 * block by block.
 */
public final class OpenFloors {
	/** What bedrock becomes, or null to leave it exactly as it is. */
	private static Block into = Blocks.OBSIDIAN;

	/** The worlds whose bands keep theirs whatever that says, by dimension id. */
	private static Set<String> kept = Set.of();

	private OpenFloors() {
	}

	public static void init() {
		ServerChunkEvents.CHUNK_LOAD.register(OpenFloors::open);
	}

	/** Reads the config's answer. An unknown or empty block name leaves bedrock alone. */
	public static void becomes(String name) {
		if (name == null || name.isBlank()) {
			into = null;
			return;
		}

		Identifier id = Identifier.tryParse(name);
		Block found = id == null ? null : BuiltInRegistries.BLOCK.getValue(id);

		if (found == null || found == Blocks.AIR) {
			OneDimension.LOGGER.warn("{} is not a block; leaving bedrock as it is", name);
			into = null;
			return;
		}

		into = found;
	}

	/**
	 * Names the worlds whose bedrock is left alone.
	 *
	 * <p>The End, by default. Its bedrock is not a floor anybody has to get through — step
	 * off the island and you are already falling out of the bottom of it — and it is the one
	 * place where bedrock is scenery: the frame of the exit portal, and the feet of the
	 * obsidian pillars. Turning that into obsidian gains nothing and costs the look of the
	 * thing you fought a dragon for.
	 */
	public static void keptIn(List<String> dimensions) {
		kept = Set.copyOf(dimensions);
	}

	private static void open(ServerLevel level, LevelChunk chunk, boolean generated) {
		// Only the one dimension: a world that is not one keeps the floors it was made with,
		// and so do the nether and the end outside it, which still exist on their own.
		Stack stack = Stacks.of(level).orElse(null);

		if (into == null || stack == null || !OneWorld.current().enabled()) {
			return;
		}

		BlockState replacement = into.defaultBlockState();
		LevelChunkSection[] sections = chunk.getSections();
		boolean changed = false;

		for (int index = 0; index < sections.length; index++) {
			LevelChunkSection section = sections[index];

			// The palette knows what a section is made of without anybody walking it, and
			// the answer is no for every section but the few at the top and bottom of a
			// world. Asking first is what keeps this off the cost of loading a chunk.
			if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(Blocks.BEDROCK))) {
				continue;
			}

			Band band = stack.bandAt(SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index)));

			if (band == null || kept.contains(band.source().identifier().toString())) {
				continue;
			}

			for (int x = 0; x < 16; x++) {
				for (int y = 0; y < 16; y++) {
					for (int z = 0; z < 16; z++) {
						if (section.getBlockState(x, y, z).is(Blocks.BEDROCK)) {
							// Straight into the section: the chunk is being loaded, so there
							// is nothing yet to tell about it — no neighbours to update, no
							// client to send it to, and light does not change between two
							// full opaque blocks.
							section.setBlockState(x, y, z, replacement, false);
							changed = true;
						}
					}
				}
			}

		}

		if (changed) {
			// Or the world is opened again, from scratch, every time it is loaded.
			chunk.markUnsaved();
		}
	}
}
