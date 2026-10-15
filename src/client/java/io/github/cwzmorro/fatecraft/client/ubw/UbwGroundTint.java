package io.github.cwzmorro.fatecraft.client.ubw;

import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/** Tints the field's ground in darker and lighter patches by a smooth noise over the floor, rather than one flat colour. */
final class UbwGroundTint implements BlockTintSource {
	private static final double BROAD = 9.0;
	private static final double FINE = 3.5;
	private static final double FINE_SHARE = 0.35;
	private static final float[] DARK = {0.62F, 0.6F, 0.68F};
	private static final float[] LIGHT = {1.0F, 0.92F, 0.9F};
	private static final int BROAD_SALT = 41;
	private static final int FINE_SALT = 42;
	private static final int ROW = 7919;
	private static final float MIDDLE = 0.5F;

	@Override
	public int color(BlockState state) {
		return tint(MIDDLE);
	}

	@Override
	public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
		return shade(pos.getX(), pos.getZ());
	}

	static int shade(int x, int z) {
		double broad = noise(x / BROAD, z / BROAD, BROAD_SALT);
		double fine = noise(x / FINE, z / FINE, FINE_SALT);
		return tint((float) Mth.lerp(FINE_SHARE, broad, fine));
	}

	private static int tint(float share) {
		return ARGB.colorFromFloat(1,
			Mth.lerp(share, DARK[0], LIGHT[0]),
			Mth.lerp(share, DARK[1], LIGHT[1]),
			Mth.lerp(share, DARK[2], LIGHT[2]));
	}

	private static double noise(double x, double z, int salt) {
		int ix = Mth.floor(x);
		int iz = Mth.floor(z);
		double fx = Mth.smoothstep(x - ix);
		double fz = Mth.smoothstep(z - iz);
		double north = Mth.lerp(fx, corner(ix, iz, salt), corner(ix + 1, iz, salt));
		double south = Mth.lerp(fx, corner(ix, iz + 1, salt), corner(ix + 1, iz + 1, salt));
		return Mth.lerp(fz, north, south);
	}

	private static double corner(int x, int z, int salt) {
		return LightStrokes.hash(x * ROW + z, salt);
	}
}
