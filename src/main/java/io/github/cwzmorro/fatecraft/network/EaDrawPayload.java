package io.github.cwzmorro.fatecraft.network;

import io.github.cwzmorro.fatecraft.Fatecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server → every client in the dimension: Ea is being drawn by the entity {@code drawer}, facing {@code yaw},
 * at the spot {@code at}; show the vault opening wherever the player is.
 */
public record EaDrawPayload(Vec3 at, float yaw, int drawer) implements CustomPacketPayload {
	public static final Type<EaDrawPayload> TYPE = new Type<>(Fatecraft.id("ea_draw"));
	public static final StreamCodec<RegistryFriendlyByteBuf, EaDrawPayload> STREAM_CODEC = StreamCodec.composite(
		Vec3.STREAM_CODEC, EaDrawPayload::at,
		ByteBufCodecs.FLOAT, EaDrawPayload::yaw,
		ByteBufCodecs.VAR_INT, EaDrawPayload::drawer,
		EaDrawPayload::new
	);

	@Override
	public Type<EaDrawPayload> type() {
		return TYPE;
	}
}
