package codx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import codx.onedimension.Column;
import codx.onedimension.OpenFloors;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * One world, made of all of them.
 *
 * <p>Every dimension the game has is stacked into a single column and joined at its
 * edges, so the sky of one is the floor of the next. Walking off the bottom of the
 * overworld drops you into whatever is beneath it, without a loading screen and without
 * losing your fall — Planeshift carries the crossing, this decides what is above what.
 */
public class OneDimension implements ModInitializer {
	public static final String MOD_ID = "one-dimension";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Column.init();
		// Bedrock is what would otherwise seal every floor the column is joined at.
		OpenFloors.init();
		// Once the worlds exist: dimensions are registered by the time the server has
		// started, which is the earliest the column can know what it is stacking.
		ServerLifecycleEvents.SERVER_STARTED.register(Column::build);
	}
}
