package io.github.cwzmorro.fatecraft.weapon;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A weapon is spinning: Ea's parts turn independently, Photon Ray turns about its grip. Set on the stack by the
 * ability and removed when it ends; synced to clients, never saved.
 *
 * @param startTick       game time the spin started
 * @param degreesPerTick  how fast the parts turn; each part turns in its own direction
 */
public record WeaponSpin(long startTick, float degreesPerTick) {
	public static final double DEGREES_PER_TURN = 360.0;

	public static final StreamCodec<ByteBuf, WeaponSpin> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_LONG, WeaponSpin::startTick,
		ByteBufCodecs.FLOAT, WeaponSpin::degreesPerTick,
		WeaponSpin::new);

	/** Returns the angle at {@code time}, partial ticks included; it wraps only after multiplying, so it never jumps between frames. */
	public float degrees(double time) {
		return (float)((time - this.startTick) * this.degreesPerTick % DEGREES_PER_TURN);
	}
}
