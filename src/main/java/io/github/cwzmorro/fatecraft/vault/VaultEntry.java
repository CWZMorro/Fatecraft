package io.github.cwzmorro.fatecraft.vault;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.cwzmorro.fatecraft.FateConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;

/**
 * One entry of a vault section: an item, with its data, and how many of it; or {@link #EMPTY}, an empty cell. The
 * vault saves the count itself, so it can go past vanilla's 99 ({@link #limit}); {@code item} is always a single item.
 */
public record VaultEntry(ItemStack item, int count) {
	public static final VaultEntry EMPTY = new VaultEntry(ItemStack.EMPTY, 0);
	public static final Codec<VaultEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
		ItemStack.OPTIONAL_CODEC.fieldOf("item").forGetter(VaultEntry::item),
		ExtraCodecs.NON_NEGATIVE_INT.fieldOf("count").forGetter(VaultEntry::count)
	).apply(i, VaultEntry::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, VaultEntry> STREAM_CODEC = StreamCodec.composite(
		ItemStack.OPTIONAL_STREAM_CODEC, VaultEntry::item,
		ByteBufCodecs.VAR_INT, VaultEntry::count,
		VaultEntry::new
	);

	public VaultEntry {
		item = item.copyWithCount(1);
	}

	public static int limit(ItemStack item) {
		return limit(item, FateConfig.vault().stackMultiplier());
	}

	public static int limit(ItemStack item, int multiplier) {
		return (int) Math.min(Integer.MAX_VALUE, (long) item.getMaxStackSize() * multiplier);
	}

	public int limit() {
		return limit(this.item);
	}

	public boolean isEmpty() {
		return this.item.isEmpty() || this.count == 0;
	}

	public boolean holds(ItemStack stack) {
		return !this.isEmpty() && ItemStack.isSameItemSameComponents(this.item, stack);
	}

	public VaultEntry withCount(int count) {
		return new VaultEntry(this.item, count);
	}

	public ItemStack stack(int count) {
		return this.item.copyWithCount(count);
	}
}
