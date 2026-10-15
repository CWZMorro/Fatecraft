package io.github.cwzmorro.fatecraft.altera;

import io.github.cwzmorro.fatecraft.weapon.WeaponOpening;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/** Synced, unsaved turn about the held grip toward a tip-down pose. */
public record WeaponFlip(long startTick, float fromFlip, boolean flipped) {
	public static final StreamCodec<ByteBuf, WeaponFlip> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_LONG, WeaponFlip::startTick,
		ByteBufCodecs.FLOAT, WeaponFlip::fromFlip,
		ByteBufCodecs.BOOL, WeaponFlip::flipped,
		WeaponFlip::new);

	public float flip(double time) {
		return WeaponOpening.transition(time, this.startTick, this.fromFlip, this.flipped);
	}

	public static void set(ItemStack stack, long time, boolean flipped) {
		WeaponFlip current = stack.get(Altera.FLIP);
		stack.set(Altera.FLIP, new WeaponFlip(time, current == null ? 0.0F : current.flip(time), flipped));
	}
}
