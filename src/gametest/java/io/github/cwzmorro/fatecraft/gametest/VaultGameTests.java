package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;

import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.servant.ServantChoice;
import io.github.cwzmorro.alayacore.servant.ServantStorage;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.network.VaultActionPayload;
import io.github.cwzmorro.fatecraft.network.VaultDragPayload;
import io.github.cwzmorro.fatecraft.network.VaultSectionPayload;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.vault.Vault;
import io.github.cwzmorro.fatecraft.vault.VaultAbility;
import io.github.cwzmorro.fatecraft.vault.VaultEntry;
import io.github.cwzmorro.fatecraft.vault.VaultGrid;
import io.github.cwzmorro.fatecraft.vault.VaultMenu;
import io.github.cwzmorro.fatecraft.vault.VaultSection;
import io.github.cwzmorro.fatecraft.vault.VaultSettings;
import io.github.cwzmorro.fatecraft.vault.VaultSort;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

public class VaultGameTests {
	private static final BlockPos STANDING = new BlockPos(1, 1, 1);
	private static final int ARROWS = 1000;
	private static final int HOTBAR_SLOT = 2;
	private static final double DROPPED_NEAR = 4.0;
	private static final int EMPTY_CELL = 3;
	private static final int GRID_COLUMNS = 12;
	private static final int GRID_ROWS = 3;
	private static final int HALF_GRID = GRID_COLUMNS * GRID_ROWS / 2;
	private static final int NARROW_COLUMNS = 9;

	private static List<Integer> counts(VaultSection section) {
		return section.entries().stream().map(VaultEntry::count).toList();
	}

	@GameTest
	public void halfOccupiedGridUnlocksTheRowBelow(GameTestHelper helper) {
		VaultSection section = new VaultSection();
		for (int cell = 0; cell < HALF_GRID - 1; cell++) {
			section.putAt(cell, new ItemStack(Items.ARROW, Items.ARROW.getDefaultMaxStackSize()), Items.ARROW.getDefaultMaxStackSize());
		}
		check(helper, "full stacks below half capacity do not unlock a row",
			VaultGrid.lastScrollRow(section.entries(), GRID_COLUMNS, GRID_ROWS) == 0);
		section.putAt(HALF_GRID - 1, new ItemStack(Items.STICK), 1);
		check(helper, "half occupied unlocks the next row before the original grid is full",
			VaultGrid.lastScrollRow(section.entries(), GRID_COLUMNS, GRID_ROWS) == 1);
		int newCell = GRID_COLUMNS * GRID_ROWS;
		ItemStack held = section.putAt(newCell, new ItemStack(Items.BOW), 1);
		check(helper, "the newly accessible row accepts items without moving existing cells",
			held.isEmpty() && section.entryAt(newCell).item().is(Items.BOW) && section.entryAt(0).item().is(Items.ARROW));
		for (int cell = 0; cell < HALF_GRID - 1; cell++) {
			section.take(cell, new ItemStack(Items.ARROW), Items.ARROW.getDefaultMaxStackSize());
		}
		check(helper, "sparse placements remain reachable after occupancy drops",
			VaultGrid.lastScrollRow(section.entries(), GRID_COLUMNS, GRID_ROWS) == 1);
		section.take(newCell, new ItemStack(Items.BOW), 1);
		check(helper, "empty holes do not count as occupied",
			VaultGrid.lastScrollRow(section.entries(), GRID_COLUMNS, GRID_ROWS) == 0);

		VaultSection narrow = new VaultSection();
		int narrowHalf = (NARROW_COLUMNS * GRID_ROWS + 1) / 2;
		for (int cell = 0; cell < narrowHalf - 1; cell++) {
			narrow.putAt(cell, new ItemStack(Items.STICK), 1);
		}
		check(helper, "odd capacity rounds the half threshold up",
			VaultGrid.lastScrollRow(narrow.entries(), NARROW_COLUMNS, GRID_ROWS) == 0);
		narrow.putAt(narrowHalf - 1, new ItemStack(Items.STICK), 1);
		check(helper, "narrow windows also unlock at half occupancy",
			VaultGrid.lastScrollRow(narrow.entries(), NARROW_COLUMNS, GRID_ROWS) == 1);
		for (int cell = narrowHalf; cell < NARROW_COLUMNS * GRID_ROWS; cell++) {
			narrow.putAt(cell, new ItemStack(Items.STICK), 1);
		}
		check(helper, "expansion continues as more cells fill",
			VaultGrid.lastScrollRow(narrow.entries(), NARROW_COLUMNS, GRID_ROWS) > 1);
		helper.succeed();
	}

	@GameTest
	public void anEntryHoldsTenStacksThenANewOneStarts(GameTestHelper helper) {
		VaultSection section = new VaultSection();
		for (int left = ARROWS; left > 0; left -= Items.ARROW.getDefaultMaxStackSize()) {
			section.insert(new ItemStack(Items.ARROW, Math.min(left, Items.ARROW.getDefaultMaxStackSize())));
		}
		check(helper, "640 then 360", counts(section).equals(List.of(640, ARROWS - 640)));
		section.insert(new ItemStack(Items.DIAMOND_SWORD));
		section.insert(new ItemStack(Items.DIAMOND_SWORD));
		check(helper, "swords stack to 10 in one entry", counts(section).equals(List.of(640, ARROWS - 640, 2)));
		helper.succeed();
	}

	@GameTest
	public void everyWeaponAndThrowableStacksTenfold(GameTestHelper helper) {
		for (Item item : FateTests.WEAPONS_AND_THROWABLES) {
			VaultSection section = new VaultSection();
			for (int stack = 0; stack <= FateConfig.vault().stackMultiplier(); stack++) {
				section.insert(new ItemStack(item, item.getDefaultMaxStackSize()));
			}
			int limit = item.getDefaultMaxStackSize() * FateConfig.vault().stackMultiplier();
			check(helper, item + ": ten stacks in one entry, the eleventh in the next",
				counts(section).equals(List.of(limit, item.getDefaultMaxStackSize())));
		}
		helper.succeed();
	}

	@GameTest
	public void differentDataIsADifferentEntry(GameTestHelper helper) {
		VaultSection section = new VaultSection();
		ItemStack named = new ItemStack(Items.IRON_SWORD);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("Merodach"));
		section.insert(new ItemStack(Items.IRON_SWORD));
		section.insert(named);
		check(helper, "two entries", section.entries().size() == 2);
		helper.succeed();
	}

	@GameTest
	public void takingGivesAtMostAStackAndChecksTheItem(GameTestHelper helper) {
		VaultSection section = new VaultSection();
		section.insert(new ItemStack(Items.ARROW, 64));
		section.insert(new ItemStack(Items.ARROW, 36));
		check(helper, "not something else", section.take(0, new ItemStack(Items.STICK), 1).isEmpty());
		ItemStack taken = section.take(0, new ItemStack(Items.ARROW), 1000);
		check(helper, "one stack", taken.is(Items.ARROW) && taken.getCount() == 64);
		section.take(0, new ItemStack(Items.ARROW), 64);
		check(helper, "an emptied entry at the end goes", section.entries().isEmpty());
		helper.succeed();
	}

	@GameTest
	public void cellsStayWhereTheyArePutAsInAChest(GameTestHelper helper) {
		VaultSection section = new VaultSection();
		section.insert(new ItemStack(Items.ARROW, 10));
		section.insert(new ItemStack(Items.BOW));
		section.take(0, new ItemStack(Items.ARROW), 10);
		check(helper, "an emptied cell stays empty; nothing moves up", section.entries().getFirst().isEmpty() && section.entries().get(1).item().is(Items.BOW));
		ItemStack held = section.putAt(EMPTY_CELL, new ItemStack(Items.STICK, 5), 5);
		check(helper, "put into the cell clicked, past the end", held.isEmpty() && section.entries().get(EMPTY_CELL).item().is(Items.STICK)
			&& section.entries().get(2).isEmpty());
		held = section.putAt(1, new ItemStack(Items.SNOWBALL, 4), 4);
		check(helper, "a different item swaps", held.is(Items.BOW) && section.entries().get(1).item().is(Items.SNOWBALL));
		section.insert(new ItemStack(Items.FEATHER));
		check(helper, "put in anywhere: the first empty cell", section.entries().getFirst().item().is(Items.FEATHER));
		section.sort(VaultSort.NAME);
		check(helper, "sorting closes the gaps", section.entries().stream().noneMatch(VaultEntry::isEmpty));
		helper.succeed();
	}

	@GameTest
	public void emptyAndOversizedPutsDoNotLoseOrCreateItems(GameTestHelper helper) {
		VaultSection section = new VaultSection();
		section.insert(new ItemStack(Items.BOW));
		section.putAt(0, ItemStack.EMPTY, 1);
		check(helper, "an empty cursor cannot erase an occupied cell", section.entries().getFirst().item().is(Items.BOW));
		section.putAt(EMPTY_CELL, ItemStack.EMPTY, 1);
		check(helper, "an empty cursor cannot grow the section", section.entries().size() == 1);
		ItemStack held = new ItemStack(Items.ARROW);
		section.putAt(EMPTY_CELL, held, ARROWS);
		check(helper, "only the held count moves", held.isEmpty() && section.entries().get(EMPTY_CELL).count() == 1);
		ItemStack untouched = new ItemStack(Items.STICK);
		section.putAt(EMPTY_CELL, untouched, 0);
		check(helper, "a zero amount cannot swap items", untouched.is(Items.STICK) && section.entries().get(EMPTY_CELL).item().is(Items.ARROW));
		helper.succeed();
	}

	@GameTest
	public void negativePickupIndexesAreIgnored(GameTestHelper helper) {
		ServerPlayer player = FateTests.player(helper, STANDING);
		player.setGameMode(GameType.CREATIVE);
		VaultSection section = Vault.of(player).section(0);
		section.insert(new ItemStack(Items.ARROW));
		VaultMenu.open(player);
		for (VaultActionPayload.Action action : List.of(VaultActionPayload.Action.TAKE_HALF, VaultActionPayload.Action.CLONE)) {
			VaultMenu.act(player, new VaultActionPayload(player.containerMenu.containerId, 0, action, -1, new ItemStack(Items.ARROW)));
			check(helper, "negative index leaves the cursor empty", player.containerMenu.getCarried().isEmpty());
			check(helper, "negative index leaves storage intact", section.entries().getFirst().count() == 1);
		}
		helper.succeed();
	}

	@GameTest
	public void theVaultAbilityIsFree(GameTestHelper helper) {
		ServerPlayer player = FateTests.gilgamesh(helper, STANDING, 0.0);
		AlayaMana.spend(player, AlayaMana.available(player));
		FateTests.tap(player, VaultAbility.KEY.identifier(), 0, false);
		check(helper, "the screen opened", player.containerMenu instanceof VaultMenu);
		check(helper, "and cost nothing", AlayaMana.available(player) == 0.0);
		helper.succeed();
	}

	@GameTest
	public void anOpenVaultClosesOnceTheServantChanges(GameTestHelper helper) {
		ServerPlayer player = FateTests.gilgamesh(helper, STANDING, FateTests.MANA);
		VaultMenu.open(player);
		check(helper, "open for Gilgamesh", player.containerMenu instanceof VaultMenu && player.containerMenu.stillValid(player));
		player.setAttached(ServantStorage.CHOICE, ServantChoice.servant(AlayaRegistries.SERVANT.getKey(FateServants.ALTERA)));
		check(helper, "invalid once the servant has no Vault", !player.containerMenu.stillValid(player));
		player.setAttached(ServantStorage.CHOICE, ServantChoice.servant(AlayaRegistries.SERVANT.getKey(FateServants.GILGAMESH)));
		check(helper, "valid again as Gilgamesh", player.containerMenu.stillValid(player));
		helper.succeed();
	}

	@GameTest
	public void volleySettingsKeepTheirOwnCopy(GameTestHelper helper) {
		List<Integer> volleys = new ArrayList<>(VaultSettings.DEFAULTS.volleys());
		VaultSettings settings = new VaultSettings(0, volleys, 0, VaultSettings.DEFAULTS.gatesPerSecond(),
			VaultSettings.DEFAULTS.returnSeconds(), VaultSettings.DEFAULTS.chains(), VaultSort.NAME);
		volleys.clear();
		check(helper, "settings do not retain a caller-owned list", settings.volleys().equals(VaultSettings.DEFAULTS.volleys()));
		helper.succeed();
	}

	@GameTest
	public void sortingByNameModAndCount(GameTestHelper helper) {
		VaultSection section = new VaultSection();
		section.insert(new ItemStack(Items.STICK, 3));
		section.insert(new ItemStack(Items.ARROW, 10));
		section.insert(new ItemStack(Items.BOW));
		section.sort(VaultSort.COUNT);
		check(helper, "by count", counts(section).equals(List.of(10, 3, 1)));
		section.sort(VaultSort.NAME);
		check(helper, "by name", section.entries().getFirst().item().is(Items.ARROW));
		helper.succeed();
	}

	@GameTest
	public void sortingStacksTheSection(GameTestHelper helper) {
		VaultSection section = new VaultSection();
		section.insert(new ItemStack(Items.ARROW, 640));
		section.insert(new ItemStack(Items.STICK, 5));
		section.insert(new ItemStack(Items.ARROW, 640));
		section.take(0, new ItemStack(Items.ARROW), 64);
		check(helper, "a gap in the first arrows", counts(section).equals(List.of(576, 5, 640)));
		section.sort(VaultSort.COUNT);
		check(helper, "arrows merged, the full entry first", counts(section).equals(List.of(640, 576, 5)));
		helper.succeed();
	}

	@GameTest
	public void theVaultSavesCountsPast99(GameTestHelper helper) {
		Vault vault = new Vault();
		vault.section(2).insert(new ItemStack(Items.ARROW, 64));
		vault.section(2).insert(new ItemStack(Items.ARROW, 64));
		vault.setSettings(new VaultSettings(2, List.of(1, 2, 3, 4), 3, 7, 90, 5, VaultSort.MOD));
		RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		Vault loaded = Vault.CODEC.parse(ops, Vault.CODEC.encodeStart(ops, vault).getOrThrow()).getOrThrow();
		check(helper, "128 arrows", counts(loaded.section(2)).equals(List.of(128)));
		check(helper, "settings", loaded.settings().equals(vault.settings()));
		helper.succeed();
	}

	@GameTest
	public void settingsAreKeptInBounds(GameTestHelper helper) {
		Vault vault = new Vault();
		vault.setSettings(new VaultSettings(9, List.of(0, 5), 7, -3, 0, 99, VaultSort.NAME));
		VaultSettings settings = vault.settings();
		check(helper, "section", settings.thrownSection() == Vault.SECTIONS - 1);
		check(helper, "volleys", settings.volleys().equals(List.of(1, 5, VaultSettings.DEFAULTS.volleys().get(2), VaultSettings.DEFAULTS.volleys().get(3))));
		check(helper, "tier", settings.tier() == VaultSettings.TIERS - 1);
		check(helper, "speed", settings.gatesPerSecond() == 1);
		check(helper, "return time", settings.returnSeconds() == 1);
		check(helper, "chains", settings.chains() == VaultSettings.MAX_CHAINS);
		helper.succeed();
	}

	@GameTest
	public void theScreenTakesAndPutsBack(GameTestHelper helper) {
		ServerPlayer player = FateTests.player(helper, STANDING);
		Vault.of(player).section(1).insert(new ItemStack(Items.ARROW, 100));
		VaultMenu.open(player);
		int menu = player.containerMenu.containerId;
		VaultMenu.act(player, new VaultActionPayload(menu, 1, VaultActionPayload.Action.TAKE_STACK, 0, new ItemStack(Items.ARROW)));
		check(helper, "a stack on the cursor", player.containerMenu.getCarried().getCount() == 64);
		check(helper, "36 left", counts(Vault.of(player).section(1)).equals(List.of(36)));
		VaultMenu.act(player, new VaultActionPayload(menu, 1, VaultActionPayload.Action.PUT_ONE, 0, ItemStack.EMPTY));
		check(helper, "one back", counts(Vault.of(player).section(1)).equals(List.of(37)));
		VaultMenu.act(player, new VaultActionPayload(menu, 1, VaultActionPayload.Action.PUT_ALL, 0, ItemStack.EMPTY));
		check(helper, "all back", counts(Vault.of(player).section(1)).equals(List.of(100)) && player.containerMenu.getCarried().isEmpty());
		VaultMenu.act(player, new VaultActionPayload(menu, 1, VaultActionPayload.Action.TAKE_TO_INVENTORY, 0, new ItemStack(Items.ARROW)));
		check(helper, "a stack into the inventory", player.getInventory().countItem(Items.ARROW) == 64);
		helper.succeed();
	}

	@GameTest
	public void theScreenHasAChestsKeysAndClicks(GameTestHelper helper) {
		ServerPlayer player = FateTests.player(helper, STANDING);
		player.setGameMode(GameType.SURVIVAL);
		VaultSection section = Vault.of(player).section(1);
		section.insert(new ItemStack(Items.ARROW, 100));
		section.insert(new ItemStack(Items.IRON_SWORD));
		VaultMenu.open(player);
		int menu = player.containerMenu.containerId;
		ItemStack arrow = new ItemStack(Items.ARROW);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		VaultMenu.act(player, new VaultActionPayload(menu, 1, VaultActionPayload.Action.CLONE, 0, arrow));
		check(helper, "no clone outside creative", player.containerMenu.getCarried().isEmpty());
		player.setGameMode(GameType.CREATIVE);
		VaultMenu.act(player, new VaultActionPayload(menu, 1, VaultActionPayload.Action.CLONE, 0, arrow));
		check(helper, "a full stack cloned in creative", player.containerMenu.getCarried().getCount() == 64 && counts(section).equals(List.of(100, 1)));
		player.containerMenu.setCarried(ItemStack.EMPTY);
		player.setGameMode(GameType.SURVIVAL);

		player.getInventory().setItem(HOTBAR_SLOT, new ItemStack(Items.BOW));
		VaultMenu.act(player, new VaultActionPayload(menu, 1, VaultActionPayload.Action.SWAP, 1, sword, HOTBAR_SLOT));
		check(helper, "the sword to the hotbar key's slot", player.getInventory().getItem(HOTBAR_SLOT).is(Items.IRON_SWORD));
		check(helper, "the bow into the vault", section.entries().get(1).item().is(Items.BOW));
		VaultMenu.act(player, new VaultActionPayload(menu, 1, VaultActionPayload.Action.SWAP, 0, arrow, Inventory.SLOT_OFFHAND));
		check(helper, "arrows to the offhand", player.getOffhandItem().is(Items.ARROW) && player.getOffhandItem().getCount() == 64);

		VaultMenu.act(player, new VaultActionPayload(menu, 1, VaultActionPayload.Action.DROP_ONE, 0, arrow));
		check(helper, "one dropped", counts(section).getFirst() == 35);
		VaultMenu.act(player, new VaultActionPayload(menu, 1, VaultActionPayload.Action.DROP_STACK, 0, arrow));
		check(helper, "the rest, up to a stack, dropped, its cell left empty", section.entries().getFirst().isEmpty());
		check(helper, "dropped as items", helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(DROPPED_NEAR)).size() == 2);

		section.insert(new ItemStack(Items.ARROW, 100));
		player.containerMenu.setCarried(new ItemStack(Items.ARROW, 10));
		VaultMenu.act(player, new VaultActionPayload(menu, 1, VaultActionPayload.Action.GATHER, 0, arrow));
		check(helper, "double-click fills the held stack", player.containerMenu.getCarried().getCount() == 64 && counts(section).getFirst() == 46);
		player.containerMenu.setCarried(ItemStack.EMPTY);
		player.getInventory().clearContent();
		VaultMenu.act(player, new VaultActionPayload(menu, 1, VaultActionPayload.Action.TAKE_ALL_TO_INVENTORY, 0, arrow));
		check(helper, "shift and double-click: all of it into the inventory", player.getInventory().countItem(Items.ARROW) == 46
			&& section.entries().getFirst().isEmpty());
		helper.succeed();
	}

	@GameTest
	public void draggingAHeldStackAcrossCellsSpreadsItAsInAChest(GameTestHelper helper) {
		ServerPlayer player = FateTests.player(helper, STANDING);
		player.setGameMode(GameType.SURVIVAL);
		VaultSection section = Vault.of(player).section(1);
		section.insert(new ItemStack(Items.BOW));
		VaultMenu.open(player);
		int menu = player.containerMenu.containerId;
		player.containerMenu.setCarried(new ItemStack(Items.ARROW, 64));
		VaultMenu.drag(player, new VaultDragPayload(menu, 1, VaultDragPayload.Kind.SPLIT, List.of(0, 1, 2, 3, 4)));
		check(helper, "split evenly over the four free cells; the bow's cell left alone",
			counts(section).equals(List.of(1, 16, 16, 16, 16)) && player.containerMenu.getCarried().isEmpty());
		player.containerMenu.setCarried(new ItemStack(Items.ARROW, 10));
		VaultMenu.drag(player, new VaultDragPayload(menu, 1, VaultDragPayload.Kind.ONE, List.of(1, 2)));
		check(helper, "one in each", counts(section).equals(List.of(1, 17, 17, 16, 16)) && player.containerMenu.getCarried().getCount() == 8);
		VaultMenu.drag(player, new VaultDragPayload(menu, 1, VaultDragPayload.Kind.CLONE, List.of(EMPTY_CELL + 3, EMPTY_CELL + 4)));
		check(helper, "no clones outside creative", section.entries().size() == 5);
		player.setGameMode(GameType.CREATIVE);
		VaultMenu.drag(player, new VaultDragPayload(menu, 1, VaultDragPayload.Kind.CLONE, List.of(EMPTY_CELL + 3, EMPTY_CELL + 4)));
		check(helper, "a full stack in each in creative, the held one kept",
			counts(section).subList(6, 8).equals(List.of(64, 64)) && player.containerMenu.getCarried().getCount() == 8);
		helper.succeed();
	}

	@GameTest
	public void aSectionSentKeepsItsOwnCopy(GameTestHelper helper) {
		VaultSection section = new VaultSection();
		section.insert(new ItemStack(Items.ARROW, 10));
		VaultSectionPayload sent = new VaultSectionPayload(0, section.entries());
		section.insert(new ItemStack(Items.DIAMOND_SWORD));
		check(helper, "the payload doesn't change with the section", sent.entries().size() == 1);
		helper.succeed();
	}

	@GameTest
	public void shiftClickingTheInventoryFillsTheOpenSection(GameTestHelper helper) {
		ServerPlayer player = FateTests.player(helper, STANDING);
		player.getInventory().setItem(0, new ItemStack(Items.BOW));
		VaultMenu.open(player);
		VaultMenu.act(player, VaultActionPayload.view(player.containerMenu.containerId, 3));
		int hotbarFirst = player.containerMenu.slots.size() - 9;
		player.containerMenu.quickMoveStack(player, hotbarFirst);
		check(helper, "in section IV", Vault.of(player).section(3).entries().size() == 1);
		check(helper, "out of the hotbar", player.getInventory().getItem(0).isEmpty());
		helper.succeed();
	}
}
