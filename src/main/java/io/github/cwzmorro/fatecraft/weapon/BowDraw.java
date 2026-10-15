package io.github.cwzmorro.fatecraft.weapon;

import io.github.cwzmorro.alayacore.bow.ArcherBows;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** Server-authored bow pose and nocked sword, synced with equipment but never saved. */
public record BowDraw(long startTick, float duration, ItemStack sword) {
	public static final StreamCodec<RegistryFriendlyByteBuf, BowDraw> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_LONG, BowDraw::startTick,
		ByteBufCodecs.FLOAT, BowDraw::duration,
		ItemStack.STREAM_CODEC, BowDraw::sword,
		BowDraw::new);

	public float fraction(double time) {
		float fraction = Mth.clamp((float) ((time - this.startTick) / this.duration), 0.0F, 1.0F);
		return ArcherBows.powerFraction(fraction);
	}
}
