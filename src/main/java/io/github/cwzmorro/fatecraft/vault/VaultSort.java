package io.github.cwzmorro.fatecraft.vault;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Comparator;
import java.util.function.IntFunction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

/** How the sort button orders a section, once stacked: by name, by mod, or by count. */
public enum VaultSort implements StringRepresentable {
	NAME("name", "screen.fatecraft.vault.sort.name", Comparator.comparing(VaultSort::displayName).thenComparing(VaultSort::itemId)),
	MOD("mod", "screen.fatecraft.vault.sort.mod",
		Comparator.comparing((VaultEntry entry) -> itemId(entry).getNamespace()).thenComparing(VaultSort::displayName).thenComparing(VaultSort::itemId)),
	COUNT("count", "screen.fatecraft.vault.sort.count",
		Comparator.comparingInt(VaultEntry::count).reversed().thenComparing(VaultSort::displayName).thenComparing(VaultSort::itemId));

	public static final Codec<VaultSort> CODEC = StringRepresentable.fromEnum(VaultSort::values);
	private static final IntFunction<VaultSort> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.WRAP);
	public static final StreamCodec<ByteBuf, VaultSort> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Enum::ordinal);

	private final String serializedName;
	private final String labelKey;
	private final Comparator<VaultEntry> order;

	VaultSort(String serializedName, String labelKey, Comparator<VaultEntry> order) {
		this.serializedName = serializedName;
		this.labelKey = labelKey;
		this.order = order;
	}

	public Comparator<VaultEntry> order() {
		return this.order;
	}

	public VaultSort next() {
		return BY_ID.apply(this.ordinal() + 1);
	}

	public Component label() {
		return Component.translatable(this.labelKey);
	}

	@Override
	public String getSerializedName() {
		return this.serializedName;
	}

	private static String displayName(VaultEntry entry) {
		return entry.item().getHoverName().getString();
	}

	private static Identifier itemId(VaultEntry entry) {
		return BuiltInRegistries.ITEM.getKey(entry.item().getItem());
	}
}
