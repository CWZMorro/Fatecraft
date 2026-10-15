package io.github.cwzmorro.fatecraft.network;

import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.vault.VaultSettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Both ways: the player's Gate of Babylon settings: the server's as the vault screen opens, the player's changes back. */
public record VaultSettingsPayload(VaultSettings settings) implements CustomPacketPayload {
	public static final Type<VaultSettingsPayload> TYPE = new Type<>(Fatecraft.id("vault_settings"));
	public static final StreamCodec<ByteBuf, VaultSettingsPayload> STREAM_CODEC = VaultSettings.STREAM_CODEC.map(VaultSettingsPayload::new,
		VaultSettingsPayload::settings);

	@Override
	public Type<VaultSettingsPayload> type() {
		return TYPE;
	}
}
