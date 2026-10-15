package io.github.cwzmorro.fatecraft.network;

import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.vault.VaultEntry;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → the player with the vault screen open: the entries of one section, after it changed or as the screen opens. */
public record VaultSectionPayload(int section, List<VaultEntry> entries, int stackMultiplier) implements CustomPacketPayload {
	public static final Type<VaultSectionPayload> TYPE = new Type<>(Fatecraft.id("vault_section"));
	public static final StreamCodec<RegistryFriendlyByteBuf, VaultSectionPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, VaultSectionPayload::section,
		VaultEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), VaultSectionPayload::entries,
		ByteBufCodecs.VAR_INT, VaultSectionPayload::stackMultiplier,
		VaultSectionPayload::new
	);

	public VaultSectionPayload(int section, List<VaultEntry> entries) {
		this(section, entries, FateConfig.vault().stackMultiplier());
	}

	// Its own copy of the entries: it is written later, on the network thread, while the section may change.
	public VaultSectionPayload {
		entries = List.copyOf(entries);
	}

	@Override
	public Type<VaultSectionPayload> type() {
		return TYPE;
	}
}
