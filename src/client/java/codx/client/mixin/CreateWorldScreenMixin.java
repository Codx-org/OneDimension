package codx.client.mixin;

import java.util.HashMap;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import codx.client.StackScreen;
import codx.onedimension.WorldChoice;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;

/**
 * Puts the one-dimension question between pressing "Create New World" and the world being
 * created.
 *
 * <p>Asked at that moment rather than in a tab of its own so that it cannot be missed, and
 * so that the list is of the dimensions the world will really have: by then the world type
 * and the datapacks are settled.
 */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin extends Screen {
	@Shadow
	@Final
	private WorldCreationUiState uiState;

	/** Set while the world is being created with an answer, so it is asked only once. */
	@Unique
	private boolean onedimension$answered;

	protected CreateWorldScreenMixin(Component title) {
		super(title);
	}

	@Shadow
	private void onCreate() {
	}

	@Inject(method = "onCreate", at = @At("HEAD"), cancellable = true)
	private void onedimension$askFirst(CallbackInfo info) {
		if (this.onedimension$answered) {
			return;
		}

		info.cancel();
		CreateWorldScreen self = (CreateWorldScreen) (Object) this;
		this.minecraft.gui.setScreen(new StackScreen(self, this.onedimension$dimensions(), choice -> {
			WorldChoice.choose(choice);
			this.minecraft.gui.setScreen(self);
			this.onedimension$answered = true;

			try {
				this.onCreate();
			} finally {
				this.onedimension$answered = false;
			}
		}));
	}

	/** Every dimension the world will have: the world type's, and every datapack's. */
	@Unique
	private Map<ResourceKey<LevelStem>, LevelStem> onedimension$dimensions() {
		WorldCreationContext context = this.uiState.getSettings();
		Map<ResourceKey<LevelStem>, LevelStem> dimensions = new HashMap<>();
		context.datapackDimensions().entrySet().forEach(entry -> dimensions.put(entry.getKey(), entry.getValue()));
		dimensions.putAll(context.selectedDimensions().dimensions());
		return dimensions;
	}
}
