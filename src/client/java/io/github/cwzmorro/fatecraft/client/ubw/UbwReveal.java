package io.github.cwzmorro.fatecraft.client.ubw;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * How much of the field's scenery shows at a point during the opening: none ahead of the front of fire, where the real
 * world still stands, all of it a few blocks behind, fading between. Points and origin are relative to the sphere's center.
 */
record UbwReveal(double originX, double originZ, double reach) {
	static final UbwReveal WHOLE = new UbwReveal(0, 0, Double.POSITIVE_INFINITY);
	private static final double SOFT = 4.0;

	double share(Vec3 point) {
		if (this.reach == Double.POSITIVE_INFINITY) {
			return 1;
		}
		return Mth.clamp((this.reach - Math.hypot(point.x - this.originX, point.z - this.originZ)) / SOFT, 0, 1);
	}
}
