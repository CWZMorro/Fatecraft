package io.github.cwzmorro.fatecraft.weapon;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * A weapon's hinged parts are moving to their open pose or back (Photon Ray's claws, guard and leaves). Set on the
 * stack by the ability; synced to clients, never saved. A stack without it is closed.
 *
 * @param startTick   game time the change started
 * @param fromOpening how open the weapon was then, 0 (closed) to 1 (open), so a change halfway continues from there
 * @param open        the state it is moving to
 */
public record WeaponOpening(long startTick, float fromOpening, boolean open) {
	public static final int DURATION_TICKS = 10;

	public static final StreamCodec<ByteBuf, WeaponOpening> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_LONG, WeaponOpening::startTick,
		ByteBufCodecs.FLOAT, WeaponOpening::fromOpening,
		ByteBufCodecs.BOOL, WeaponOpening::open,
		WeaponOpening::new);

	public float opening(double time) {
		return transition(time, this.startTick, this.fromOpening, this.open);
	}

	public static float transition(double time, long startTick, float from, boolean active) {
		float progress = Mth.clamp((float) ((time - startTick) / DURATION_TICKS), 0.0F, 1.0F);
		return Mth.lerp((float) Mth.smoothstep(progress), from, active ? 1.0F : 0.0F);
	}

	public static void set(ItemStack stack, long time, boolean open) {
		WeaponOpening current = stack.get(FateWeapons.OPENING);
		float from = current == null ? 0.0F : current.opening(time);
		stack.set(FateWeapons.OPENING, new WeaponOpening(time, from, open));
	}
}
