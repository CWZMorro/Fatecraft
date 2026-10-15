package io.github.cwzmorro.fatecraft.client.vault;

import io.github.cwzmorro.alayacore.AlayaCore;
import io.github.cwzmorro.alayacore.client.screen.SideTabs;
import io.github.cwzmorro.fatecraft.network.VaultActionPayload;
import io.github.cwzmorro.fatecraft.network.VaultDragPayload;
import io.github.cwzmorro.fatecraft.vault.Vault;
import io.github.cwzmorro.fatecraft.vault.VaultEntry;
import io.github.cwzmorro.fatecraft.vault.VaultGrid;
import io.github.cwzmorro.fatecraft.vault.VaultMenu;
import io.github.cwzmorro.fatecraft.vault.VaultSettings;
import io.github.cwzmorro.fatecraft.vault.VaultSort;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The vault screen: the open section's endless grid (up to {@link #MOST_COLUMNS} slots a row) with search and sort above
 * it and the inventory under it. The sections are tabs on the left, the one thrown from ringed in gold; the Gate of
 * Babylon settings are a tab on the right. The menu holds only the inventory's slots ({@link #leftPos}, {@link #topPos}).
 */
public final class VaultScreen extends AbstractContainerScreen<VaultMenu> {
	private static final Identifier PANEL = AlayaCore.id("panel");
	private static final Identifier INSET = AlayaCore.id("inset");
	private static final Identifier SLOT = Identifier.withDefaultNamespace("container/slot");
	private static final Identifier HIGHLIGHT_BACK = Identifier.withDefaultNamespace("container/slot_highlight_back");
	private static final Identifier HIGHLIGHT_FRONT = Identifier.withDefaultNamespace("container/slot_highlight_front");
	private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/creative_inventory/scroller");
	private static final Identifier SCROLLER_DISABLED = Identifier.withDefaultNamespace("container/creative_inventory/scroller_disabled");
	private static final Identifier SEARCH = Identifier.withDefaultNamespace("icon/search");
	private static final Identifier TEXT_FIELD = Identifier.withDefaultNamespace("widget/text_field");
	private static final Identifier TAB_LEFT_FIRST = Identifier.withDefaultNamespace("advancements/tab_left_top");
	private static final Identifier TAB_LEFT_FIRST_SELECTED = Identifier.withDefaultNamespace("advancements/tab_left_top_selected");
	private static final Identifier TAB_LEFT = Identifier.withDefaultNamespace("advancements/tab_left_middle");
	private static final Identifier TAB_LEFT_SELECTED = Identifier.withDefaultNamespace("advancements/tab_left_middle_selected");
	private static final Identifier CHECKBOX = Identifier.withDefaultNamespace("widget/checkbox");
	private static final Identifier CHECKBOX_SELECTED = Identifier.withDefaultNamespace("widget/checkbox_selected");

	// Layout metrics in GUI pixels, matching vanilla's containers where one exists.
	private static final int SLOT_SIZE = 18;
	private static final int ITEM_SIZE = 16;
	private static final int ITEM_INSET = 1;
	private static final int HIGHLIGHT_SIZE = 24;
	private static final int HIGHLIGHT_OUT = 4;
	private static final int COUNT_RIGHT = ITEM_SIZE;
	private static final int COUNT_TOP = 9;
	private static final int EDGE = 7;
	private static final int CONTROL_HEIGHT = 14;
	private static final int CONTROL_GAP = 3;
	private static final int SEARCH_ICON = 12;
	private static final int FIELD_INSET = 4;
	// Vanilla text baselines sit one pixel below geometric centring; inventory labels have a three-pixel top inset.
	private static final int TEXT_BASELINE_OFFSET = 1;
	private static final int INVENTORY_LABEL_INSET = 3;
	private static final int GLYPH_SPACING = 1;
	private static final int SEARCH_LENGTH = 50;
	private static final int BUTTON_PADDING = 6;
	private static final int SEARCH_LINE = EDGE + CONTROL_HEIGHT + CONTROL_GAP;
	private static final int FEWEST_COLUMNS = 9;
	private static final int MOST_COLUMNS = 12;
	private static final int FEWEST_ROWS = 3;
	private static final int SCROLLBAR_GAP = 2;
	private static final int SCROLLBAR_FRAME = 1;
	private static final int SCROLLBAR_WIDTH = 14;
	private static final int SCROLLER_WIDTH = 12;
	private static final int SCROLLER_HEIGHT = 15;
	private static final int INVENTORY_LABEL = 14;
	private static final int INVENTORY_ROWS = 3;
	private static final int INVENTORY_COLUMNS = 9;
	private static final int HOTBAR_GAP = 4;
	private static final int INVENTORY_HEIGHT = INVENTORY_ROWS * SLOT_SIZE + HOTBAR_GAP + SLOT_SIZE;
	private static final int INVENTORY_WIDTH = INVENTORY_COLUMNS * SLOT_SIZE;
	private static final int HEIGHT_BUT_ROWS = SEARCH_LINE + INVENTORY_LABEL + INVENTORY_HEIGHT + EDGE;
	private static final int CAP_HEIGHT = 7;
	private static final int TAB_GAP = 2;
	private static final int TAB_STEP = SideTabs.HEIGHT + TAB_GAP;
	private static final int MARGIN = 4;
	private static final int SETTINGS_TITLE = EDGE + SideTabs.COG_SIZE + CONTROL_GAP;
	private static final int CHECKBOX_SIZE = 20;
	private static final int SETTINGS_ROW = 18;
	private static final int SETTINGS_GAP = 6;
	private static final int PICKER_WIDTH = 46;
	private static final int PICKER_TEXT_INSET = 3;
	private static final int GATES_PER_SECOND_PICKER = VaultSettings.TIERS;
	private static final int RETURN_PICKER = VaultSettings.TIERS + 1;
	private static final int CHAINS_PICKER = VaultSettings.TIERS + 2;
	private static final int PICKERS = VaultSettings.TIERS + 3;
	private static final int STEP = 1;
	private static final int BIG_STEP = 10;
	private static final int RING = 5;
	private static final int RING_GAP = 1;
	private static final int GOLD = 0xFFFFC83C;
	private static final int PALE_GOLD = 0xFFFFF0B4;

	private static final int TEXT = 0xFF404040;
	private static final int LIGHT_TEXT = 0xFFFFFFFF;
	private static final int HOVER = 0x80FFFFFF;

	// Remembered while the game runs.
	private static boolean settingsOpen;

	private int columns;
	private int rows;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int gridX;
	private int gridY;
	private int section;
	private int scrollRow;
	private boolean draggingScroller;
	private boolean skipRelease;
	private int hoveredCell = -1;
	private @Nullable List<Integer> dragCells;
	private VaultDragPayload.Kind dragKind = VaultDragPayload.Kind.SPLIT;
	private @Nullable EditBox search;
	private ScreenRectangle searchField = ScreenRectangle.empty();
	private @Nullable Button sortBy;
	private List<Integer> shown = List.of();
	private int shownVersion = -1;
	private int shownSection = -1;
	private String shownQuery = "";

	public VaultScreen(VaultMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, INVENTORY_WIDTH, INVENTORY_HEIGHT);
	}

	@Override
	protected void init() {
		// Room for the section tabs on the left and, on the right, the Gate of Babylon tab, closed or open.
		int right = settingsOpen ? SideTabs.SETTINGS_WIDTH - SideTabs.UNDER : SideTabs.WIDTH - SideTabs.UNDER;
		int widthForGrid = this.width - 2 * MARGIN - (SideTabs.WIDTH - SideTabs.UNDER) - right - 2 * EDGE - SCROLLBAR_GAP - SCROLLBAR_WIDTH;
		this.columns = Mth.clamp(widthForGrid / SLOT_SIZE, FEWEST_COLUMNS, MOST_COLUMNS);
		this.rows = Math.max(FEWEST_ROWS, (this.height - 2 * MARGIN - HEIGHT_BUT_ROWS) / SLOT_SIZE);
		this.panelWidth = 2 * EDGE + this.columns * SLOT_SIZE + SCROLLBAR_GAP + SCROLLBAR_WIDTH;
		this.panelHeight = HEIGHT_BUT_ROWS + this.rows * SLOT_SIZE;
		this.panelX = Math.min((this.width - this.panelWidth) / 2, this.width - MARGIN - right - this.panelWidth);
		this.panelY = (this.height - this.panelHeight) / 2;
		this.gridX = this.panelX + EDGE;
		this.gridY = this.panelY + SEARCH_LINE;
		super.init();
		this.leftPos = this.panelX + (this.panelWidth - INVENTORY_WIDTH) / 2 + ITEM_INSET;
		this.topPos = this.gridY + this.rows * SLOT_SIZE + INVENTORY_LABEL + ITEM_INSET;
		this.inventoryLabelX = -ITEM_INSET;
		this.inventoryLabelY = -INVENTORY_LABEL + INVENTORY_LABEL_INSET;

		int lineY = this.panelY + EDGE;
		int lineRight = this.gridX + this.columns * SLOT_SIZE + SCROLLBAR_GAP + SCROLLBAR_WIDTH;
		int sortByWidth = 0;
		for (VaultSort sort : VaultSort.values()) {
			sortByWidth = Math.max(sortByWidth, this.font.width(sort.label()) + 2 * BUTTON_PADDING);
		}
		Component sortLabel = Component.translatable("screen.fatecraft.vault.sort");
		int sortWidth = this.font.width(sortLabel) + 2 * BUTTON_PADDING;
		int sortByX = lineRight - sortByWidth;
		int sortX = sortByX - CONTROL_GAP - sortWidth;
		this.searchField = new ScreenRectangle(this.gridX + SEARCH_ICON + CONTROL_GAP, lineY, sortX - CONTROL_GAP - this.gridX - SEARCH_ICON - CONTROL_GAP,
			CONTROL_HEIGHT);
		String query = this.search == null ? "" : this.search.getValue();
		this.search = new EditBox(this.font, this.searchField.left() + FIELD_INSET, lineY + (CONTROL_HEIGHT - this.font.lineHeight) / 2 + TEXT_BASELINE_OFFSET,
			this.searchField.width() - 2 * FIELD_INSET, this.font.lineHeight, Component.translatable("screen.fatecraft.vault.search"));
		this.search.setBordered(false);
		this.search.setMaxLength(SEARCH_LENGTH);
		this.search.setHint(Component.translatable("screen.fatecraft.vault.search"));
		this.search.setTooltip(Tooltip.create(Component.translatable("screen.fatecraft.vault.search.tooltip")));
		this.search.setValue(query);
		this.addRenderableWidget(this.search);
		this.addRenderableWidget(Button.builder(sortLabel, button -> this.act(VaultActionPayload.Action.SORT, 0, ItemStack.EMPTY))
			.bounds(sortX, lineY, sortWidth, CONTROL_HEIGHT)
			.tooltip(Tooltip.create(Component.translatable("screen.fatecraft.vault.sort.tooltip")))
			.build());
		this.sortBy = this.addRenderableWidget(Button.builder(ClientVault.settings().sort().label(),
				button -> ClientVault.changeSettings(ClientVault.settings().withSort(ClientVault.settings().sort().next())))
			.bounds(sortByX, lineY, sortByWidth, CONTROL_HEIGHT)
			.tooltip(Tooltip.create(Component.translatable("screen.fatecraft.vault.sort_by.tooltip")))
			.build());
		ClientPlayNetworking.send(VaultActionPayload.view(this.menu.containerId, this.section));
	}

	private void openSection(int section) {
		this.section = section;
		this.scrollRow = 0;
		ClientPlayNetworking.send(VaultActionPayload.view(this.menu.containerId, section));
	}

	private void act(VaultActionPayload.Action action, int index, ItemStack expected) {
		this.act(action, index, expected, 0);
	}

	private void act(VaultActionPayload.Action action, int index, ItemStack expected, int slot) {
		ClientPlayNetworking.send(new VaultActionPayload(this.menu.containerId, this.section, action, index, expected, slot));
	}

	private boolean actOnCell(int cell, VaultActionPayload.Action action, int slot) {
		int index = this.indexAt(cell);
		if (index < 0) {
			return false;
		}
		this.act(action, index, ClientVault.section(this.section).get(index).item(), slot);
		return true;
	}

	private int indexAt(int cell) {
		int at = this.scrollRow * this.columns + cell;
		List<Integer> shown = this.shown();
		return cell < 0 || at >= shown.size() || ClientVault.section(this.section).get(shown.get(at)).isEmpty() ? -1 : shown.get(at);
	}

	private int cellIndexAt(int cell) {
		return this.search == null || this.search.getValue().isBlank() ? this.scrollRow * this.columns + cell : -1;
	}

	private List<Integer> shown() {
		String query = this.search == null ? "" : this.search.getValue();
		if (this.shownVersion != ClientVault.version() || this.shownSection != this.section || !this.shownQuery.equals(query)) {
			List<VaultEntry> entries = ClientVault.section(this.section);
			List<Integer> shown = new ArrayList<>();
			// Every cell, empty ones too, as placed; searching, only the entries that match.
			for (int i = 0; i < entries.size(); i++) {
				if (query.isBlank() || !entries.get(i).isEmpty() && VaultSearch.matches(entries.get(i).item(), query)) {
					shown.add(i);
				}
			}
			this.shown = List.copyOf(shown);
			this.shownVersion = ClientVault.version();
			this.shownSection = this.section;
			this.shownQuery = query;
			this.scrollRow = Math.min(this.scrollRow, this.lastScrollRow());
			if (this.sortBy != null) {
				this.sortBy.setMessage(ClientVault.settings().sort().label());
			}
		}
		return this.shown;
	}

	private int lastScrollRow() {
		if (this.search == null || this.search.getValue().isBlank()) {
			return VaultGrid.lastScrollRow(ClientVault.section(this.section), this.columns, this.rows);
		}
		return Math.max(0, this.shown.size() / this.columns + 1 - this.rows);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		for (int tab = 0; tab < Vault.SECTIONS; tab++) {
			if (tab != this.section) {
				this.drawSectionTab(graphics, tab);
			}
		}
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, this.panelX, this.panelY, this.panelWidth, this.panelHeight);
		this.drawSectionTab(graphics, this.section);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SEARCH, this.gridX, this.panelY + EDGE + (CONTROL_HEIGHT - SEARCH_ICON) / 2, SEARCH_ICON, SEARCH_ICON);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TEXT_FIELD, this.searchField.left(), this.searchField.top(), this.searchField.width(),
			this.searchField.height());
		for (int row = 0; row < this.rows; row++) {
			for (int column = 0; column < this.columns; column++) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, this.gridX + column * SLOT_SIZE, this.gridY + row * SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);
			}
		}
		int inventoryX = this.leftPos - ITEM_INSET;
		int inventoryY = this.topPos - ITEM_INSET;
		for (int column = 0; column < INVENTORY_COLUMNS; column++) {
			for (int row = 0; row < INVENTORY_ROWS; row++) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, inventoryX + column * SLOT_SIZE, inventoryY + row * SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);
			}
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, inventoryX + column * SLOT_SIZE, inventoryY + INVENTORY_HEIGHT - SLOT_SIZE, SLOT_SIZE,
				SLOT_SIZE);
		}
		ScreenRectangle bar = this.scrollbar();
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, INSET, bar.left(), bar.top(), bar.width(), bar.height());
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.lastScrollRow() > 0 ? SCROLLER : SCROLLER_DISABLED, bar.left() + SCROLLBAR_FRAME, this.scrollerY(),
			SCROLLER_WIDTH, SCROLLER_HEIGHT);
		if (settingsOpen) {
			ScreenRectangle settings = this.settingsArea();
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, settings.left(), settings.top(), settings.width(), settings.height());
		} else {
			SideTabs.extractSettingsTab(graphics, this.settingsTab(), this.minecraft.getWindow().getGuiScale());
		}
	}

	private float onScreenPixel(float gui) {
		return SideTabs.onScreenPixel(gui, this.minecraft.getWindow().getGuiScale());
	}

	private void drawSectionTab(GuiGraphicsExtractor graphics, int tab) {
		ScreenRectangle area = this.sectionTab(tab);
		Identifier sprite = tab == 0 ? (tab == this.section ? TAB_LEFT_FIRST_SELECTED : TAB_LEFT_FIRST) : (tab == this.section ? TAB_LEFT_SELECTED : TAB_LEFT);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, area.left(), area.top(), SideTabs.WIDTH, SideTabs.HEIGHT);
		int bodyLeft = area.left() + (tab == this.section ? 0 : SideTabs.RECESS) + SideTabs.FRAME;
		int bodyWidth = area.left() + SideTabs.WIDTH - SideTabs.UNDER - bodyLeft;
		// The numeral centred on the body, like the panel's other labels: no shadow. The font's width ends with a glyph's spacing.
		Component name = Vault.sectionName(tab);
		graphics.pose().pushMatrix();
		graphics.pose().translate(this.onScreenPixel(bodyLeft + (bodyWidth - (this.font.width(name) - GLYPH_SPACING)) / 2.0F),
			this.onScreenPixel(area.top() + SideTabs.FRAME + (SideTabs.BODY_HEIGHT - CAP_HEIGHT) / 2.0F));
		graphics.text(this.font, name, 0, 0, TEXT, false);
		graphics.pose().popMatrix();
		if (tab == ClientVault.settings().thrownSection()) {
			drawRing(graphics, bodyLeft + RING_GAP, area.top() + SideTabs.FRAME + RING_GAP);
		}
	}

	private static void drawRing(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.fill(x + 1, y, x + RING - 1, y + 1, GOLD);
		graphics.fill(x + 1, y + RING - 1, x + RING - 1, y + RING, GOLD);
		graphics.fill(x, y + 1, x + 1, y + RING - 1, GOLD);
		graphics.fill(x + RING - 1, y + 1, x + RING, y + RING - 1, GOLD);
		graphics.fill(x + 1, y + 1, x + RING - 1, y + RING - 1, PALE_GOLD);
	}

	@Override
	public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractContents(graphics, mouseX, mouseY, a);
		List<Integer> shown = this.shown();
		List<VaultEntry> entries = ClientVault.section(this.section);
		int hovered = this.cellAt(mouseX, mouseY);
		this.hoveredCell = hovered;
		for (int cell = 0; cell < this.rows * this.columns; cell++) {
			int x = this.gridX + ITEM_INSET + cell % this.columns * SLOT_SIZE;
			int y = this.gridY + ITEM_INSET + cell / this.columns * SLOT_SIZE;
			int at = this.scrollRow * this.columns + cell;
			if (cell == hovered || this.dragCells != null && this.dragCells.contains(this.cellIndexAt(cell))) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT_BACK, x - HIGHLIGHT_OUT, y - HIGHLIGHT_OUT, HIGHLIGHT_SIZE, HIGHLIGHT_SIZE);
			}
			if (at < shown.size()) {
				VaultEntry entry = entries.get(shown.get(at));
				graphics.item(entry.item(), x, y);
				graphics.itemDecorations(this.font, entry.item(), x, y, "");
				if (entry.count() > 1) {
					this.drawCount(graphics, String.valueOf(entry.count()), x, y);
				}
			}
			if (cell == hovered) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT_FRONT, x - HIGHLIGHT_OUT, y - HIGHLIGHT_OUT, HIGHLIGHT_SIZE, HIGHLIGHT_SIZE);
			}
		}
		if (settingsOpen) {
			this.extractSettings(graphics, mouseX, mouseY);
		}
	}

	// Three digits are wider than an item, so they are shrunk by whole screen pixels to a font pixel (half size at GUI scale 2, two thirds at 3, three quarters at 4).
	private void drawCount(GuiGraphicsExtractor graphics, String count, int x, int y) {
		// The font's width ends with a glyph's spacing, which isn't drawn.
		int width = this.font.width(count) - GLYPH_SPACING;
		int guiScale = this.minecraft.getWindow().getGuiScale();
		float scale = width < ITEM_SIZE ? 1.0F : Math.max(1, (ITEM_SIZE - GLYPH_SPACING) * guiScale / width) / (float) guiScale;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + COUNT_RIGHT - width * scale, y + COUNT_TOP + this.font.lineHeight * (1 - scale));
		graphics.pose().scale(scale, scale);
		graphics.text(this.font, count, 0, 0, LIGHT_TEXT, true);
		graphics.pose().popMatrix();
	}

	private void extractSettings(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		ScreenRectangle area = this.settingsArea();
		VaultSettings settings = ClientVault.settings();
		SideTabs.extractSettingsTitle(graphics, this.font, area, EDGE, CONTROL_GAP, Component.translatable("screen.fatecraft.vault.gate_of_babylon"), TEXT);
		ScreenRectangle checkbox = this.checkbox();
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, settings.thrownSection() == this.section ? CHECKBOX_SELECTED : CHECKBOX, checkbox.left(),
			checkbox.top(), CHECKBOX_SIZE, CHECKBOX_SIZE);
		graphics.text(this.font, Component.translatable("screen.fatecraft.vault.thrown_from_here"), checkbox.right() + CONTROL_GAP,
			checkbox.top() + (CHECKBOX_SIZE - this.font.lineHeight) / 2 + TEXT_BASELINE_OFFSET, TEXT, false);
		for (int picker = 0; picker < PICKERS; picker++) {
			ScreenRectangle box = this.picker(picker);
			graphics.text(this.font, pickerName(picker), area.left() + EDGE, box.top() + PICKER_TEXT_INSET, TEXT, false);
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, INSET, box.left(), box.top(), box.width(), box.height());
			if (box.containsPoint(mouseX, mouseY)) {
				graphics.fill(box.left(), box.top(), box.right(), box.bottom(), HOVER);
			}
			int textY = box.top() + PICKER_TEXT_INSET;
			graphics.text(this.font, "<", box.left() + PICKER_TEXT_INSET, textY, LIGHT_TEXT, true);
			graphics.text(this.font, ">", box.right() - PICKER_TEXT_INSET - this.font.width(">"), textY, LIGHT_TEXT, true);
			graphics.centeredText(this.font, String.valueOf(pickerValue(settings, picker)), box.left() + box.width() / 2, textY, LIGHT_TEXT);
		}
	}

	private static Component pickerName(int picker) {
		return switch (picker) {
			case GATES_PER_SECOND_PICKER -> Component.translatable("screen.fatecraft.vault.gates_per_second");
			case RETURN_PICKER -> Component.translatable("screen.fatecraft.vault.return_seconds");
			case CHAINS_PICKER -> Component.translatable("screen.fatecraft.vault.chains");
			default -> VaultSettings.tierName(picker);
		};
	}

	private static int pickerValue(VaultSettings settings, int picker) {
		return switch (picker) {
			case GATES_PER_SECOND_PICKER -> settings.gatesPerSecond();
			case RETURN_PICKER -> settings.returnSeconds();
			case CHAINS_PICKER -> settings.chains();
			default -> settings.volleys().get(picker);
		};
	}

	private static void changePicker(int picker, int by) {
		VaultSettings settings = ClientVault.settings();
		ClientVault.changeSettings(switch (picker) {
			case GATES_PER_SECOND_PICKER -> settings.withGatesPerSecond(settings.gatesPerSecond() + by);
			case RETURN_PICKER -> settings.withReturnSeconds(settings.returnSeconds() + by);
			case CHAINS_PICKER -> settings.withChains(settings.chains() + by);
			default -> settings.withVolleyChanged(picker, by);
		});
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, TEXT, false);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		if (!this.menu.getCarried().isEmpty()) {
			return;
		}
		int cell = this.cellAt(mouseX, mouseY);
		VaultEntry entry = cell < 0 ? null : this.entryAt(cell);
		if (entry != null) {
			List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(this.minecraft, entry.item()));
			lines.add(Component.translatable("screen.fatecraft.vault.in_this_slot", entry.count(), ClientVault.limit(entry)).withStyle(ChatFormatting.GRAY));
			graphics.setTooltipForNextFrame(this.font, lines, entry.item().getTooltipImage(), mouseX, mouseY);
			return;
		}
		for (int tab = 0; tab < Vault.SECTIONS; tab++) {
			if (this.sectionTab(tab).containsPoint(mouseX, mouseY)) {
				graphics.setTooltipForNextFrame(Component.translatable("screen.fatecraft.vault.section", Vault.sectionName(tab)), mouseX, mouseY);
				return;
			}
		}
		if (!settingsOpen && this.settingsTab().containsPoint(mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(this.font, List.of(Component.translatable("screen.fatecraft.vault.gate_of_babylon"),
				Component.translatable("screen.fatecraft.vault.gate_of_babylon.tooltip").withStyle(ChatFormatting.GRAY)), Optional.empty(), mouseX, mouseY);
			return;
		}
		if (settingsOpen) {
			if (this.checkbox().containsPoint(mouseX, mouseY)) {
				graphics.setTooltipForNextFrame(Component.translatable("screen.fatecraft.vault.thrown_from_here.tooltip"), mouseX, mouseY);
			}
			int picker = this.pickerAt(mouseX, mouseY);
			if (picker >= 0) {
				Component what = switch (picker) {
					case GATES_PER_SECOND_PICKER -> Component.translatable("screen.fatecraft.vault.gates_per_second.tooltip");
					case RETURN_PICKER -> Component.translatable("screen.fatecraft.vault.return_seconds.tooltip");
					case CHAINS_PICKER -> Component.translatable("screen.fatecraft.vault.chains.tooltip");
					default -> Component.translatable("screen.fatecraft.vault.volley.tooltip", pickerName(picker));
				};
				graphics.setTooltipForNextFrame(this.font, List.of(what, Component.translatable("screen.fatecraft.vault.picker.tooltip")
					.withStyle(ChatFormatting.GRAY)), Optional.empty(), mouseX, mouseY);
			}
		}
	}

	private @Nullable VaultEntry entryAt(int cell) {
		int index = this.indexAt(cell);
		return index < 0 ? null : ClientVault.section(this.section).get(index);
	}

	private int cellAt(double x, double y) {
		int column = Mth.floor((x - this.gridX) / SLOT_SIZE);
		int row = Mth.floor((y - this.gridY) / SLOT_SIZE);
		return column >= 0 && column < this.columns && row >= 0 && row < this.rows ? row * this.columns + column : -1;
	}

	private ScreenRectangle sectionTab(int tab) {
		return new ScreenRectangle(this.panelX - SideTabs.WIDTH + SideTabs.UNDER, this.panelY + tab * TAB_STEP, SideTabs.WIDTH, SideTabs.HEIGHT);
	}

	public ScreenRectangle settingsTab() {
		return SideTabs.settingsTab(this.panelX, this.panelY, this.panelWidth);
	}

	private ScreenRectangle settingsArea() {
		int height = SETTINGS_TITLE + CHECKBOX_SIZE + SETTINGS_GAP + PICKERS * SETTINGS_ROW + SETTINGS_GAP + EDGE;
		return SideTabs.settingsArea(this.panelX, this.panelY, this.panelWidth, height, this.width, this.height, MARGIN);
	}

	private ScreenRectangle settingsTitle() {
		ScreenRectangle area = this.settingsArea();
		return new ScreenRectangle(area.left(), area.top(), area.width(), SETTINGS_TITLE);
	}

	private ScreenRectangle checkbox() {
		ScreenRectangle area = this.settingsArea();
		return new ScreenRectangle(area.left() + EDGE, area.top() + SETTINGS_TITLE, CHECKBOX_SIZE, CHECKBOX_SIZE);
	}

	private ScreenRectangle picker(int picker) {
		ScreenRectangle area = this.settingsArea();
		int y = area.top() + SETTINGS_TITLE + CHECKBOX_SIZE + SETTINGS_GAP + picker * SETTINGS_ROW + (picker >= VaultSettings.TIERS ? SETTINGS_GAP : 0);
		return new ScreenRectangle(area.right() - EDGE - PICKER_WIDTH, y, PICKER_WIDTH, CONTROL_HEIGHT);
	}

	private int pickerAt(double x, double y) {
		for (int picker = 0; settingsOpen && picker < PICKERS; picker++) {
			if (this.picker(picker).containsPoint((int) x, (int) y)) {
				return picker;
			}
		}
		return -1;
	}

	private ScreenRectangle scrollbar() {
		return new ScreenRectangle(this.gridX + this.columns * SLOT_SIZE + SCROLLBAR_GAP, this.gridY, SCROLLBAR_WIDTH, this.rows * SLOT_SIZE);
	}

	private int scrollerY() {
		ScreenRectangle bar = this.scrollbar();
		int last = this.lastScrollRow();
		int travel = bar.height() - 2 * SCROLLBAR_FRAME - SCROLLER_HEIGHT;
		return bar.top() + SCROLLBAR_FRAME + (last == 0 ? 0 : travel * this.scrollRow / last);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x();
		double y = event.y();
		boolean right = event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
		if (this.search != null && right && this.search.isMouseOver(x, y)) {
			this.search.setValue("");
			return true;
		}
		for (int tab = 0; tab < Vault.SECTIONS; tab++) {
			if (this.sectionTab(tab).containsPoint((int) x, (int) y)) {
				this.openSection(tab);
				return true;
			}
		}
		if (settingsOpen ? this.settingsTitle().containsPoint((int) x, (int) y) : this.settingsTab().containsPoint((int) x, (int) y)) {
			settingsOpen = !settingsOpen;
			this.rebuildWidgets();
			return true;
		}
		if (settingsOpen && this.clickSettings((int) x, (int) y, event.hasShiftDown())) {
			return true;
		}
		if (this.scrollbar().containsPoint((int) x, (int) y)) {
			this.draggingScroller = true;
			this.scrollTo(y);
			return true;
		}
		int cell = this.cellAt(x, y);
		if (cell >= 0 && this.startDrag(event, cell, doubleClick)) {
			return true;
		}
		if (cell >= 0 && this.minecraft.options.keyPickItem.matchesMouse(event)) {
			if (this.menu.getCarried().isEmpty()) {
				this.actOnCell(cell, VaultActionPayload.Action.CLONE, 0);
			}
			this.skipRelease = true;
			return true;
		}
		if (cell >= 0 && (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT || right)) {
			this.clickCell(cell, right, event.hasShiftDown(), doubleClick);
			this.skipRelease = true;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private boolean clickSettings(int x, int y, boolean shift) {
		if (!this.settingsArea().containsPoint(x, y)) {
			return false;
		}
		if (this.checkbox().containsPoint(x, y)) {
			ClientVault.changeSettings(ClientVault.settings().withThrownSection(this.section));
		}
		int picker = this.pickerAt(x, y);
		if (picker >= 0) {
			ScreenRectangle box = this.picker(picker);
			int step = shift ? BIG_STEP : STEP;
			changePicker(picker, x < box.left() + box.width() / 2 ? -step : step);
		}
		return true;
	}

	private void clickCell(int cell, boolean right, boolean shift, boolean doubleClick) {
		ItemStack carried = this.menu.getCarried();
		if (doubleClick && !right) {
			int index = this.indexAt(cell);
			if (shift) {
				this.actOnCell(cell, VaultActionPayload.Action.TAKE_ALL_TO_INVENTORY, 0);
				return;
			}
			if (index >= 0 && ClientVault.section(this.section).get(index).holds(carried)) {
				this.actOnCell(cell, VaultActionPayload.Action.GATHER, 0);
				return;
			}
		}
		if (!carried.isEmpty()) {
			this.act(right ? VaultActionPayload.Action.PUT_ONE : VaultActionPayload.Action.PUT_ALL, this.cellIndexAt(cell), ItemStack.EMPTY);
			return;
		}
		this.actOnCell(cell, shift ? VaultActionPayload.Action.TAKE_TO_INVENTORY
			: right ? VaultActionPayload.Action.TAKE_HALF : VaultActionPayload.Action.TAKE_STACK, 0);
	}

	private boolean startDrag(MouseButtonEvent event, int cell, boolean doubleClick) {
		int index = this.cellIndexAt(cell);
		if (this.menu.getCarried().isEmpty() || doubleClick || event.hasShiftDown() || index < 0) {
			return false;
		}
		if (this.minecraft.options.keyPickItem.matchesMouse(event)) {
			if (!this.minecraft.player.hasInfiniteMaterials()) {
				return false;
			}
			this.dragKind = VaultDragPayload.Kind.CLONE;
		} else if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT || event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			this.dragKind = event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT ? VaultDragPayload.Kind.SPLIT : VaultDragPayload.Kind.ONE;
		} else {
			return false;
		}
		this.dragCells = new ArrayList<>(List.of(index));
		return true;
	}

	private void endDrag() {
		List<Integer> cells = this.dragCells;
		this.dragCells = null;
		if (cells == null || cells.isEmpty()) {
			return;
		}
		if (cells.size() > 1) {
			ClientPlayNetworking.send(new VaultDragPayload(this.menu.containerId, this.section, this.dragKind, cells));
		} else if (this.dragKind != VaultDragPayload.Kind.CLONE) {
			this.act(this.dragKind == VaultDragPayload.Kind.ONE ? VaultActionPayload.Action.PUT_ONE : VaultActionPayload.Action.PUT_ALL, cells.getFirst(),
				ItemStack.EMPTY);
		}
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		this.draggingScroller = false;
		if (this.dragCells != null) {
			this.endDrag();
			return true;
		}
		if (this.skipRelease) {
			this.skipRelease = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.draggingScroller) {
			this.scrollTo(event.y());
			return true;
		}
		if (this.dragCells != null) {
			int cell = this.cellAt(event.x(), event.y());
			int index = cell < 0 ? -1 : this.cellIndexAt(cell);
			if (index >= 0 && !this.dragCells.contains(index)) {
				this.dragCells.add(index);
			}
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	private void scrollTo(double y) {
		ScreenRectangle bar = this.scrollbar();
		double share = (y - bar.top() - SCROLLER_HEIGHT / 2.0) / (bar.height() - SCROLLER_HEIGHT);
		this.scrollRow = Mth.clamp((int) Math.round(share * this.lastScrollRow()), 0, this.lastScrollRow());
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		int picker = this.pickerAt(x, y);
		if (picker >= 0) {
			changePicker(picker, (int) Math.signum(scrollY) * (this.minecraft.hasShiftDown() ? BIG_STEP : STEP));
			return true;
		}
		ScreenRectangle grid = new ScreenRectangle(this.gridX, this.gridY, this.columns * SLOT_SIZE + SCROLLBAR_GAP + SCROLLBAR_WIDTH, this.rows * SLOT_SIZE);
		if (grid.containsPoint((int) x, (int) y)) {
			this.scrollRow = Mth.clamp(this.scrollRow - (int) Math.signum(scrollY), 0, this.lastScrollRow());
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// Typing in the search box doesn't close the screen with the inventory key.
		if (this.search != null && this.search.isFocused() && event.key() != GLFW.GLFW_KEY_ESCAPE) {
			return this.search.keyPressed(event) || this.search.canConsumeInput() || super.keyPressed(event);
		}
		if (this.hoveredCell >= 0 && this.menu.getCarried().isEmpty() && this.keyOnCell(event)) {
			return true;
		}
		return super.keyPressed(event);
	}

	private boolean keyOnCell(KeyEvent event) {
		Options options = this.minecraft.options;
		for (int slot = 0; slot < options.keyHotbarSlots.length; slot++) {
			if (options.keyHotbarSlots[slot].matches(event)) {
				return this.actOnCell(this.hoveredCell, VaultActionPayload.Action.SWAP, slot);
			}
		}
		if (options.keySwapOffhand.matches(event)) {
			return this.actOnCell(this.hoveredCell, VaultActionPayload.Action.SWAP, Inventory.SLOT_OFFHAND);
		}
		if (options.keyDrop.matches(event)) {
			return this.actOnCell(this.hoveredCell, event.hasControlDown() ? VaultActionPayload.Action.DROP_STACK : VaultActionPayload.Action.DROP_ONE, 0);
		}
		return false;
	}

	@Override
	protected boolean hasClickedOutside(double x, double y, int left, int top) {
		int px = (int) x;
		int py = (int) y;
		boolean onTabs = px >= this.panelX - SideTabs.WIDTH + SideTabs.UNDER && px < this.panelX && py >= this.panelY && py < this.panelY + Vault.SECTIONS * TAB_STEP;
		boolean onPanel = px >= this.panelX && px < this.panelX + this.panelWidth && py >= this.panelY && py < this.panelY + this.panelHeight;
		return !onTabs && !onPanel && !(settingsOpen ? this.settingsArea() : this.settingsTab()).containsPoint(px, py);
	}
}
