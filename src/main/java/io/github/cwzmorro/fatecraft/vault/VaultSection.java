package io.github.cwzmorro.fatecraft.vault;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * One of the vault's sections: an endless row of cells, each empty or holding one entry. Entries stay in their cell,
 * and an emptied cell stays empty, until the section is sorted. A stack put in anywhere fills entries holding the same
 * item and data up to their limit, then the first empty cells, then new ones at the end.
 */
public final class VaultSection {
	public static final Codec<VaultSection> CODEC = VaultEntry.CODEC.listOf().xmap(VaultSection::new, VaultSection::entries);

	private final List<VaultEntry> entries;

	public VaultSection() {
		this(List.of());
	}

	public VaultSection(List<VaultEntry> entries) {
		this.entries = new ArrayList<>(entries);
	}

	public List<VaultEntry> entries() {
		return Collections.unmodifiableList(this.entries);
	}

	public void insert(ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		for (int i = 0; i < this.entries.size() && !stack.isEmpty(); i++) {
			VaultEntry entry = this.entries.get(i);
			if (entry.holds(stack) && entry.count() < entry.limit()) {
				int moved = Math.min(stack.getCount(), entry.limit() - entry.count());
				this.entries.set(i, entry.withCount(entry.count() + moved));
				stack.shrink(moved);
			}
		}
		for (int i = 0; i < this.entries.size() && !stack.isEmpty(); i++) {
			if (this.entries.get(i).isEmpty()) {
				int moved = Math.min(stack.getCount(), VaultEntry.limit(stack));
				this.entries.set(i, new VaultEntry(stack, moved));
				stack.shrink(moved);
			}
		}
		while (!stack.isEmpty()) {
			int moved = Math.min(stack.getCount(), VaultEntry.limit(stack));
			this.entries.add(new VaultEntry(stack, moved));
			stack.shrink(moved);
		}
	}

	public ItemStack take(int index, ItemStack expected, int amount) {
		if (amount <= 0 || !this.entryAt(index).holds(expected)) {
			return ItemStack.EMPTY;
		}
		VaultEntry entry = this.entries.get(index);
		int taken = Math.min(Math.min(amount, entry.count()), entry.item().getMaxStackSize());
		this.entries.set(index, taken == entry.count() ? VaultEntry.EMPTY : entry.withCount(entry.count() - taken));
		this.trim();
		return entry.stack(taken);
	}

	/**
	 * Puts up to {@code amount} of {@code held} (shrinking it) into the cell at {@code index}: into an empty cell or one
	 * holding the same item, up to the entry's limit; a different item swaps with the held stack if the cell holds no more
	 * than one stack of it. Anywhere ({@link #insert}) if {@code index} is negative. Returns what is held after.
	 */
	public ItemStack putAt(int index, ItemStack held, int amount) {
		if (held.isEmpty() || amount <= 0) {
			return held;
		}
		amount = Math.min(amount, held.getCount());
		if (index < 0) {
			this.insert(held.split(amount));
			return held;
		}
		while (this.entries.size() <= index) {
			this.entries.add(VaultEntry.EMPTY);
		}
		VaultEntry entry = this.entries.get(index);
		if (entry.isEmpty() || entry.holds(held)) {
			int moved = Math.max(0, Math.min(amount, VaultEntry.limit(held) - entry.count()));
			this.entries.set(index, new VaultEntry(held, entry.count() + moved));
			held.shrink(moved);
		} else if (entry.count() <= entry.item().getMaxStackSize()) {
			this.entries.set(index, new VaultEntry(held, held.getCount()));
			held = entry.stack(entry.count());
		}
		this.trim();
		return held;
	}

	public VaultEntry entryAt(int index) {
		return index >= 0 && index < this.entries.size() ? this.entries.get(index) : VaultEntry.EMPTY;
	}

	public boolean emptyAt(int index) {
		return index >= this.entries.size() || this.entries.get(index).isEmpty();
	}

	private void trim() {
		while (!this.entries.isEmpty() && this.entries.getLast().isEmpty()) {
			this.entries.removeLast();
		}
	}

	public void sort(VaultSort sort) {
		List<VaultEntry> unsorted = List.copyOf(this.entries);
		this.entries.clear();
		for (VaultEntry entry : unsorted) {
			if (!entry.isEmpty()) {
				this.insert(entry.stack(entry.count()));
			}
		}
		this.entries.sort(sort.order());
	}
}
