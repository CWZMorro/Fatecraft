package io.github.cwzmorro.fatecraft.vault;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.fatecraft.network.VaultActionPayload;
import io.github.cwzmorro.fatecraft.network.VaultDragPayload;
import io.github.cwzmorro.fatecraft.network.VaultSectionPayload;
import io.github.cwzmorro.fatecraft.network.VaultSettingsPayload;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The vault screen's menu. Only the player's inventory is made of slots, at the menu's origin (the screen places it);
 * the vault's sections are endless lists of entries, sent to the screen whole ({@link VaultSectionPayload}) and changed
 * through {@link VaultActionPayload}s with a chest's controls. Shift-clicking an inventory slot puts its stack in the open section.
 */
public final class VaultMenu extends AbstractContainerMenu {
	private int openSection;

	public VaultMenu(int containerId, Inventory inventory) {
		super(Vaults.MENU, containerId);
		this.addStandardInventorySlots(inventory, 0, 0);
	}

	public static void open(ServerPlayer player) {
		player.openMenu(new SimpleMenuProvider((containerId, inventory, opener) -> new VaultMenu(containerId, inventory),
			Component.translatable("screen.fatecraft.vault.title")));
		Vault vault = Vault.of(player);
		for (int section = 0; section < Vault.SECTIONS; section++) {
			sendSection(player, vault, section);
		}
		ServerPlayNetworking.send(player, new VaultSettingsPayload(vault.settings()));
	}

	private static void sendSection(ServerPlayer player, Vault vault, int section) {
		ServerPlayNetworking.send(player, new VaultSectionPayload(section, vault.section(section).entries()));
	}

	public static void refresh(ServerPlayer player, int section) {
		if (player.containerMenu instanceof VaultMenu) {
			sendSection(player, Vault.of(player), section);
		}
	}

	public static void act(ServerPlayer player, VaultActionPayload action) {
		if (!(player.containerMenu instanceof VaultMenu menu) || menu.containerId != action.containerId()) {
			return;
		}
		Vault vault = Vault.of(player);
		menu.openSection = Vault.sectionIndex(action.section());
		VaultSection section = vault.section(menu.openSection);
		ItemStack carried = menu.getCarried();
		int stack = action.expected().getMaxStackSize();
		switch (action.action()) {
			case VIEW -> {
				return;
			}
			case TAKE_STACK -> {
				if (carried.isEmpty()) {
					menu.setCarried(section.take(action.index(), action.expected(), stack));
				}
			}
			case TAKE_HALF -> {
				if (carried.isEmpty()) {
					int count = Math.min(section.entryAt(action.index()).count(), stack);
					menu.setCarried(section.take(action.index(), action.expected(), Mth.ceil(count / 2.0)));
				}
			}
			case TAKE_TO_INVENTORY -> {
				ItemStack taken = section.take(action.index(), action.expected(), stack);
				player.getInventory().add(taken);
				section.insert(taken);
			}
			case PUT_ALL -> menu.setCarried(section.putAt(action.index(), carried, carried.getCount()));
			case PUT_ONE -> menu.setCarried(section.putAt(action.index(), carried, 1));
			case SORT -> section.sort(vault.settings().sort());
			case CLONE -> {
				if (carried.isEmpty() && player.hasInfiniteMaterials() && section.entryAt(action.index()).holds(action.expected())) {
					menu.setCarried(action.expected().copyWithCount(stack));
				}
			}
			case SWAP -> swap(player, section, action, stack);
			case DROP_ONE -> player.drop(section.take(action.index(), action.expected(), 1), false, true);
			case DROP_STACK -> player.drop(section.take(action.index(), action.expected(), stack), false, true);
			case GATHER -> gather(menu, section, action);
			case TAKE_ALL_TO_INVENTORY -> takeAll(player, section, action, stack);
		}
		menu.broadcastChanges();
		sendSection(player, vault, menu.openSection);
	}

	public static void drag(ServerPlayer player, VaultDragPayload drag) {
		if (!(player.containerMenu instanceof VaultMenu menu) || menu.containerId != drag.containerId()) {
			return;
		}
		Vault vault = Vault.of(player);
		menu.openSection = Vault.sectionIndex(drag.section());
		VaultSection section = vault.section(menu.openSection);
		ItemStack carried = menu.getCarried();
		boolean clone = drag.kind() == VaultDragPayload.Kind.CLONE;
		if (carried.isEmpty() || clone && !player.hasInfiniteMaterials()) {
			return;
		}
		List<Integer> cells = drag.cells().stream().distinct()
			.filter(cell -> cell >= 0 && (section.emptyAt(cell) || section.entryAt(cell).holds(carried)))
			.limit(clone ? Integer.MAX_VALUE : carried.getCount()).toList();
		if (cells.isEmpty()) {
			return;
		}
		int each = switch (drag.kind()) {
			case SPLIT -> carried.getCount() / cells.size();
			case ONE -> 1;
			case CLONE -> carried.getMaxStackSize();
		};
		for (int cell : cells) {
			section.putAt(cell, clone ? carried.copyWithCount(each) : carried, each);
		}
		menu.broadcastChanges();
		sendSection(player, vault, menu.openSection);
	}

	private static void swap(ServerPlayer player, VaultSection section, VaultActionPayload action, int stack) {
		Inventory inventory = player.getInventory();
		int slot = action.slot();
		if (slot != Inventory.SLOT_OFFHAND && !Inventory.isHotbarSlot(slot)) {
			return;
		}
		ItemStack taken = section.take(action.index(), action.expected(), stack);
		if (taken.isEmpty()) {
			return;
		}
		ItemStack was = inventory.getItem(slot);
		inventory.setItem(slot, taken);
		// Into the cell it came from, if that is empty now, as a chest swaps; else anywhere.
		if (!was.isEmpty() && section.emptyAt(action.index())) {
			was = section.putAt(action.index(), was, was.getCount());
		}
		section.insert(was);
		inventory.placeItemBackInInventory(was);
	}

	private static void gather(VaultMenu menu, VaultSection section, VaultActionPayload action) {
		ItemStack carried = menu.getCarried();
		if (carried.isEmpty() || !ItemStack.isSameItemSameComponents(carried, action.expected())) {
			return;
		}
		carried.grow(section.take(action.index(), action.expected(), carried.getMaxStackSize() - carried.getCount()).getCount());
		for (Slot slot : menu.slots) {
			if (carried.getCount() >= carried.getMaxStackSize()) {
				return;
			}
			if (ItemStack.isSameItemSameComponents(slot.getItem(), carried)) {
				carried.grow(slot.remove(carried.getMaxStackSize() - carried.getCount()).getCount());
			}
		}
	}

	private static void takeAll(ServerPlayer player, VaultSection section, VaultActionPayload action, int stack) {
		while (true) {
			ItemStack taken = section.take(action.index(), action.expected(), stack);
			if (taken.isEmpty()) {
				return;
			}
			player.getInventory().add(taken);
			if (!taken.isEmpty()) {
				section.insert(taken);
				return;
			}
		}
	}

	public static void setSettings(ServerPlayer player, VaultSettingsPayload payload) {
		if (player.containerMenu instanceof VaultMenu) {
			Vault.of(player).setSettings(payload.settings());
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		Slot slot = this.slots.get(slotIndex);
		if (player instanceof ServerPlayer serverPlayer && slot.hasItem()) {
			Vault vault = Vault.of(serverPlayer);
			ItemStack moved = slot.getItem().copy();
			slot.set(ItemStack.EMPTY);
			vault.section(this.openSection).insert(moved);
			sendSection(serverPlayer, vault, this.openSection);
		}
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return player.isAlive() && Abilities.owns(player, VaultAbility.KEY.identifier());
	}
}
