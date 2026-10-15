package io.github.cwzmorro.fatecraft.network;

import io.github.cwzmorro.fatecraft.Fatecraft;
import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.ByIdMap;
import net.minecraft.world.item.ItemStack;

/**
 * Client → server, from the vault screen: something done to section {@code section}, which becomes the open one;
 * {@code index} and {@code expected} name the entry clicked (the item it showed), where the action takes from one;
 * {@code slot} is the inventory slot a swap is with.
 */
public record VaultActionPayload(int containerId, int section, Action action, int index, ItemStack expected, int slot) implements CustomPacketPayload {
	public static final Type<VaultActionPayload> TYPE = new Type<>(Fatecraft.id("vault_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, VaultActionPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, VaultActionPayload::containerId,
		ByteBufCodecs.VAR_INT, VaultActionPayload::section,
		Action.STREAM_CODEC, VaultActionPayload::action,
		ByteBufCodecs.VAR_INT, VaultActionPayload::index,
		ItemStack.OPTIONAL_STREAM_CODEC, VaultActionPayload::expected,
		ByteBufCodecs.VAR_INT, VaultActionPayload::slot,
		VaultActionPayload::new
	);

	public VaultActionPayload(int containerId, int section, Action action, int index, ItemStack expected) {
		this(containerId, section, action, index, expected, 0);
	}

	public static VaultActionPayload view(int containerId, int section) {
		return new VaultActionPayload(containerId, section, Action.VIEW, 0, ItemStack.EMPTY);
	}

	@Override
	public Type<VaultActionPayload> type() {
		return TYPE;
	}

	public enum Action {
		VIEW,
		TAKE_STACK,
		TAKE_HALF,
		TAKE_TO_INVENTORY,
		PUT_ALL,
		PUT_ONE,
		SORT,
		CLONE,
		SWAP,
		DROP_ONE,
		DROP_STACK,
		GATHER,
		TAKE_ALL_TO_INVENTORY;

		private static final IntFunction<Action> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
		public static final StreamCodec<ByteBuf, Action> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Enum::ordinal);
	}
}
