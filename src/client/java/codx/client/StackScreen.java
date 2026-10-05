package codx.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.google.common.collect.ImmutableList;

import codx.onedimension.Dimensions;
import codx.onedimension.StackConfig;
import codx.onedimension.WorldChoice;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.LevelStem;

/**
 * Asked just before a new world is created: is it one dimension, what is it stacked from,
 * in what order, how the worlds meet, and whether the bottom joins the top.
 *
 * <p>Every dimension the new world will have is listed — the game's own and every one a
 * mod or datapack adds — top of the world first. Each can be moved up and down, or left
 * out and kept as a dimension of its own. The overworld is always in: it is the world.
 */
public final class StackScreen extends Screen {
	private static final Component TITLE = Component.translatable("one-dimension.stack.title");

	/** The spaces offered between worlds, in blocks. */
	private static final List<Integer> GAPS = List.of(0, 16, 32, 64, 128, 256);

	private static final String OBSIDIAN = "minecraft:obsidian";

	private final Screen parent;

	private final Consumer<WorldChoice> create;

	private final List<Row> rows = new ArrayList<>();

	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

	private boolean enabled;

	private boolean loop;

	private boolean trim;

	private boolean skies;

	private boolean replaceBedrock;

	private String bedrock;

	private ResourceKey<Level> spawn = Level.OVERWORLD;

	private boolean scalePortals = true;

	private StackList list;

	private Button createButton;

	/** One dimension in the list, whether it is stacked, and the space below it. */
	private static final class Row {
		private final ResourceKey<Level> dimension;

		private boolean included;

		private int gapBelow;

		private Row(ResourceKey<Level> dimension, boolean included, int gapBelow) {
			this.dimension = dimension;
			this.included = included;
			this.gapBelow = gapBelow;
		}
	}

	/**
	 * @param dimensions every dimension the new world will have
	 * @param create     called with the choice when the player goes ahead with the world
	 */
	public StackScreen(Screen parent, Map<ResourceKey<LevelStem>, LevelStem> dimensions, Consumer<WorldChoice> create) {
		super(TITLE);
		this.parent = parent;
		this.create = create;
		StackConfig.Defaults defaults = StackConfig.read(Dimensions.present(dimensions));
		this.enabled = defaults.enabled();
		this.loop = defaults.loop();
		this.trim = defaults.trim();
		this.skies = defaults.skies();
		this.replaceBedrock = !defaults.bedrock().isBlank();
		this.bedrock = this.replaceBedrock ? defaults.bedrock() : OBSIDIAN;

		for (ResourceKey<Level> dimension : defaults.order()) {
			this.rows.add(new Row(dimension, dimension.equals(Level.OVERWORLD) || !defaults.leftOut().contains(dimension),
					GAPS.contains(defaults.gap()) ? defaults.gap() : 0));
		}
	}

	@Override
	protected void init() {
		this.layout.addTitleHeader(this.title, this.font);
		this.list = this.layout.addToContents(new StackList());
		LinearLayout buttons = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		buttons.addChild(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose()).build());
		this.createButton = buttons.addChild(Button.builder(Component.translatable("selectWorld.create"),
				button -> this.create.accept(this.choice())).build());
		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
		this.refreshCreate();
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();

		if (this.list != null) {
			this.list.updateSize(this.width, this.layout);
		}
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	private WorldChoice choice() {
		Map<ResourceKey<Level>, Integer> gaps = new LinkedHashMap<>();
		this.rows.stream().filter(row -> row.included).forEach(row -> gaps.put(row.dimension, row.gapBelow));
		return new WorldChoice(this.enabled, this.rows.stream().filter(row -> row.included).map(row -> row.dimension).toList(), this.loop,
				gaps, this.trim, this.skies, this.replaceBedrock ? this.bedrock : "", this.spawn, this.scalePortals);
	}

	/** Whether a stacked world comes after this row: the space below a row is only between two. */
	private boolean stackedBelow(int index) {
		return this.rows.subList(index + 1, this.rows.size()).stream().anyMatch(row -> row.included);
	}

	private void move(int from, int to) {
		if (to < 0 || to >= this.rows.size()) {
			return;
		}

		this.rows.add(to, this.rows.remove(from));
		this.list.fill();
	}

	/** Whether the block bedrock is to become is one the game has, and not air. */
	private boolean bedrockValid() {
		Identifier id = Identifier.tryParse(this.bedrock);
		return id != null && BuiltInRegistries.BLOCK.containsKey(id) && BuiltInRegistries.BLOCK.getValue(id) != Blocks.AIR;
	}

	/** A world cannot be created with bedrock turning into something that is not a block. */
	private void refreshCreate() {
		if (this.createButton != null) {
			this.createButton.active = !this.enabled || !this.replaceBedrock || this.bedrockValid();
		}
	}

	/** A dimension's name: the game's own for the three it has, its id for the rest. */
	private static Component name(ResourceKey<Level> dimension) {
		if (dimension.equals(Level.OVERWORLD)) {
			return Component.translatable("one-dimension.dimension.overworld");
		} else if (dimension.equals(Level.NETHER)) {
			return Component.translatable("one-dimension.dimension.the_nether");
		} else if (dimension.equals(Level.END)) {
			return Component.translatable("one-dimension.dimension.the_end");
		}

		return Component.literal(dimension.identifier().getPath()).append(
				Component.literal("  " + dimension.identifier().getNamespace()).withStyle(ChatFormatting.GRAY));
	}

	private static Component gapName(int blocks) {
		return Component.literal("↓ " + blocks);
	}

	private final class StackList extends ContainerObjectSelectionList<StackList.Entry> {
		private StackList() {
			super(StackScreen.this.minecraft, StackScreen.this.width, StackScreen.this.layout.getContentHeight(),
					StackScreen.this.layout.getHeaderHeight(), 24);
			this.fill();
		}

		private void fill() {
			double scroll = this.scrollAmount();
			boolean on = StackScreen.this.enabled;
			this.clearEntries();
			this.addEntry(new WidgetsEntry(List.of(CycleButton.onOffBuilder(on)
					.create(0, 0, 310, 20, Component.translatable("one-dimension.stack.enabled"), (button, value) -> {
						StackScreen.this.enabled = value;
						StackScreen.this.refreshCreate();
						this.fill();
					})), "one-dimension.stack.enabled.tooltip", true));
			this.addEntry(new WidgetsEntry(List.of(CycleButton.onOffBuilder(StackScreen.this.loop)
					.create(0, 0, 310, 20, Component.translatable("one-dimension.stack.loop"), (button, value) -> StackScreen.this.loop = value)),
					"one-dimension.stack.loop.tooltip", on));
			this.addEntry(new WidgetsEntry(List.of(CycleButton.onOffBuilder(StackScreen.this.trim)
					.create(0, 0, 310, 20, Component.translatable("one-dimension.stack.trim"), (button, value) -> StackScreen.this.trim = value)),
					"one-dimension.stack.trim.tooltip", on));
			this.addEntry(new WidgetsEntry(List.of(CycleButton.onOffBuilder(StackScreen.this.skies)
					.create(0, 0, 310, 20, Component.translatable("one-dimension.stack.skies"), (button, value) -> StackScreen.this.skies = value)),
					"one-dimension.stack.skies.tooltip", on));
			this.addEntry(this.bedrockEntry(on));
			this.addEntry(this.spawnEntry(on));
			this.addEntry(new WidgetsEntry(List.of(CycleButton.builder(
							(Boolean scaled) -> Component.translatable(scaled ? "one-dimension.stack.portals.scaled" : "one-dimension.stack.portals.straight"),
							StackScreen.this.scalePortals)
					.withValues(List.of(true, false))
					.create(0, 0, 310, 20, Component.translatable("one-dimension.stack.portals"), (button, value) -> StackScreen.this.scalePortals = value)),
					"one-dimension.stack.portals.tooltip", on));
			this.addEntry(new TextEntry(Component.translatable("one-dimension.stack.order").withStyle(ChatFormatting.GRAY)));

			for (int i = 0; i < StackScreen.this.rows.size(); i++) {
				this.addEntry(new DimensionEntry(i));
			}

			this.setScrollAmount(scroll);
		}

		/** Whether bedrock floors and roofs are replaced, and by which block. */
		private WidgetsEntry bedrockEntry(boolean on) {
			EditBox block = new EditBox(StackScreen.this.font, 0, 0, 154, 20, Component.translatable("one-dimension.stack.bedrock.block"));
			block.setMaxLength(128);
			block.setValue(StackScreen.this.bedrock);
			block.setHint(Component.literal(OBSIDIAN).withStyle(ChatFormatting.DARK_GRAY));
			block.setResponder(value -> {
				StackScreen.this.bedrock = value.trim();
				block.setTextColor(StackScreen.this.bedrockValid() ? 0xFFE0E0E0 : 0xFFFF5555);
				StackScreen.this.refreshCreate();
			});
			block.setTextColor(StackScreen.this.bedrockValid() ? 0xFFE0E0E0 : 0xFFFF5555);
			block.setTooltip(Tooltip.create(Component.translatable("one-dimension.stack.bedrock.block.tooltip")));
			block.active = on && StackScreen.this.replaceBedrock;
			block.setEditable(block.active);
			CycleButton<Boolean> replace = CycleButton.onOffBuilder(StackScreen.this.replaceBedrock)
					.create(0, 0, 152, 20, Component.translatable("one-dimension.stack.bedrock"), (button, value) -> {
						StackScreen.this.replaceBedrock = value;
						block.active = value;
						block.setEditable(value);
						StackScreen.this.refreshCreate();
					});
			return new WidgetsEntry(List.of(replace, block), "one-dimension.stack.bedrock.tooltip", on);
		}

		/** Which of the stacked worlds players spawn in; the overworld unless chosen otherwise. */
		private WidgetsEntry spawnEntry(boolean on) {
			List<ResourceKey<Level>> stacked = StackScreen.this.rows.stream().filter(row -> row.included).map(row -> row.dimension).toList();

			if (!stacked.contains(StackScreen.this.spawn)) {
				StackScreen.this.spawn = Level.OVERWORLD;
			}

			return new WidgetsEntry(List.of(CycleButton.builder(StackScreen::name, StackScreen.this.spawn)
					.withValues(stacked)
					.create(0, 0, 310, 20, Component.translatable("one-dimension.stack.spawn"), (button, value) -> StackScreen.this.spawn = value)),
					"one-dimension.stack.spawn.tooltip", on);
		}

		@Override
		public int getRowWidth() {
			return 310;
		}

		abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {
		}

		/** Widgets side by side, the whole width of the list; the first has the tooltip. */
		private final class WidgetsEntry extends Entry {
			private final List<AbstractWidget> widgets;

			private WidgetsEntry(List<AbstractWidget> widgets, String tooltip, boolean active) {
				this.widgets = widgets;

				for (AbstractWidget widget : widgets) {
					// A text field keeps its own state; everything else follows the row.
					if (!(widget instanceof EditBox)) {
						widget.active = active;
					}
				}

				widgets.getFirst().setTooltip(Tooltip.create(Component.translatable(tooltip)));
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
				int x = this.getContentX();

				for (AbstractWidget widget : this.widgets) {
					widget.setPosition(x, this.getContentY());
					widget.extractRenderState(graphics, mouseX, mouseY, a);
					x += widget.getWidth() + 4;
				}
			}

			@Override
			public List<? extends GuiEventListener> children() {
				return this.widgets;
			}

			@Override
			public List<? extends NarratableEntry> narratables() {
				return this.widgets;
			}
		}

		/** A line of explanation. */
		private final class TextEntry extends Entry {
			private final Component text;

			private TextEntry(Component text) {
				this.text = text;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
				graphics.text(StackScreen.this.font, this.text, this.getContentX(), this.getContentYMiddle() - 4, -1);
			}

			@Override
			public List<? extends GuiEventListener> children() {
				return List.of();
			}

			@Override
			public List<? extends NarratableEntry> narratables() {
				return List.of();
			}
		}

		/**
		 * A dimension: its place in the stack, whether it is in it, buttons to move it, and the
		 * space between it and the next stacked world below.
		 */
		private final class DimensionEntry extends Entry {
			private final Row row;

			private final Button up;

			private final Button down;

			private final CycleButton<Integer> gap;

			private final CycleButton<Boolean> included;

			private DimensionEntry(int index) {
				this.row = StackScreen.this.rows.get(index);
				this.up = Button.builder(Component.literal("▲"), button -> StackScreen.this.move(index, index - 1))
						.bounds(0, 0, 20, 20)
						.tooltip(Tooltip.create(Component.translatable("one-dimension.stack.up")))
						.build();
				this.down = Button.builder(Component.literal("▼"), button -> StackScreen.this.move(index, index + 1))
						.bounds(0, 0, 20, 20)
						.tooltip(Tooltip.create(Component.translatable("one-dimension.stack.down")))
						.build();
				this.gap = CycleButton.builder(StackScreen::gapName, this.row.gapBelow)
						.withValues(GAPS)
						.displayOnlyValue()
						.create(0, 0, 52, 20, Component.translatable("one-dimension.stack.gap"), (button, value) -> this.row.gapBelow = value);
				this.gap.setTooltip(Tooltip.create(Component.translatable("one-dimension.stack.gap.tooltip", name(this.row.dimension))));
				this.gap.active = StackScreen.this.enabled && this.row.included && StackScreen.this.stackedBelow(index);
				this.included = CycleButton.onOffBuilder(this.row.included)
						.displayOnlyValue()
						.create(0, 0, 44, 20, Component.translatable("one-dimension.stack.included"), (button, value) -> {
							this.row.included = value;
							// Which rows have a world below them to be spaced from has changed.
							StackList.this.fill();
						});
				boolean overworld = this.row.dimension.equals(Level.OVERWORLD);
				this.included.setTooltip(Tooltip.create(Component.translatable(overworld
						? "one-dimension.stack.included.overworld"
						: "one-dimension.stack.included.tooltip")));
				this.up.active = StackScreen.this.enabled && index > 0;
				this.down.active = StackScreen.this.enabled && index < StackScreen.this.rows.size() - 1;
				this.included.active = StackScreen.this.enabled && !overworld;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
				int x = this.getContentX();
				int y = this.getContentY();
				this.up.setPosition(x, y);
				this.down.setPosition(x + 22, y);
				this.included.setPosition(x + this.getContentWidth() - this.included.getWidth(), y);
				this.gap.setPosition(this.included.getX() - 4 - this.gap.getWidth(), y);
				this.up.extractRenderState(graphics, mouseX, mouseY, a);
				this.down.extractRenderState(graphics, mouseX, mouseY, a);
				this.gap.extractRenderState(graphics, mouseX, mouseY, a);
				this.included.extractRenderState(graphics, mouseX, mouseY, a);
				boolean lit = StackScreen.this.enabled && this.row.included;
				graphics.text(StackScreen.this.font, name(this.row.dimension), x + 50, this.getContentYMiddle() - 4, lit ? -1 : 0xFF808080);
			}

			@Override
			public List<? extends GuiEventListener> children() {
				return ImmutableList.of(this.up, this.down, this.gap, this.included);
			}

			@Override
			public List<? extends NarratableEntry> narratables() {
				return ImmutableList.of(this.up, this.down, this.gap, this.included);
			}
		}
	}
}
