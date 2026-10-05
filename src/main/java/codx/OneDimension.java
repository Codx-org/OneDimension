package codx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import codx.onedimension.Column;
import codx.onedimension.LoopSelfTest;
import codx.onedimension.OneWorld;
import codx.onedimension.OpenFloors;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * One world, made of all of them.
 *
 * <p>Every dimension the world is made with is stacked into the overworld, each a band of
 * its height, so the sky of one is the floor of the next. Digging through the bottom of
 * the overworld drops you into whatever is beneath it, in the same world — CustomChunks
 * generates the stack; this decides what is in it, and Planeshift joins the bottom back to
 * the top.
 *
 * <p>Chosen per world, on the create-world screen.
 */
public class OneDimension implements ModInitializer {
	public static final String MOD_ID = "one-dimension";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		OneWorld.init();
		Column.init();
		// Bedrock is what would otherwise seal every floor the column is joined at.
		OpenFloors.init();
		// Once the world exists: the stack's height is known once its levels are made.
		ServerLifecycleEvents.SERVER_STARTED.register(Column::build);

		if (Boolean.getBoolean("one-dimension.selftest")) {
			LoopSelfTest.init();
		}
	}
}
