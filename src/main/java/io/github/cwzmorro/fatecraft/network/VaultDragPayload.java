package io.github.cwzmorro.fatecraft.network;

import io.github.cwzmorro.fatecraft.Fatecraft;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.function.IntFunction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.ByIdMap;

/**
 * Client → server, from the vault screen: the held stack dragged across cells {@code cells} of section
 * {@code section}, as across a chest's slots.
 */
public record VaultDragPayload(int containerId, int section, Kind kind, List<Integer> cells) implements CustomPacketPayload {
	public static final Type<VaultDragPayload> TYPE = new Type<>(Fatecraft.id("vault_drag"));
	public static final StreamCodec<RegistryFriendlyByteBuf, VaultDragPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, VaultDragPayload::containerId,
		ByteBufCodecs.VAR_INT, VaultDragPayload::section,
		Kind.STREAM_CODEC, VaultDragPayload::kind,
		ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), VaultDragPayload::cells,
		VaultDragPayload::new
	);

	public VaultDragPayload {
		cells = List.copyOf(cells);
	}

	@Override
	public Type<VaultDragPayload> type() {
		return TYPE;
	}

	public enum Kind {
		SPLIT,
		ONE,
		CLONE;

		private static final IntFunction<Kind> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
		public static final StreamCodec<ByteBuf, Kind> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Enum::ordinal);
	}
}
