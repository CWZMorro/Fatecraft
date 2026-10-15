package io.github.cwzmorro.fatecraft.client.ubw;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A marble that Enuma Elish broke: how far its cracks have run and its pieces have fallen away
 * ({@link io.github.cwzmorro.fatecraft.ubw.UbwShatter}). A shred breaks off as the crumble front passes it, late by its
 * own hash so the front is ragged, then drops, shrinks and fades. Points and impact are relative to the sphere's center.
 */
record UbwCrumble(Vec3 impact, double cracks, double fallen) {
	// A broken shred drops away over FALL_BAND blocks behind the crumble front and starts fading half way.
	private static final double FALL_BAND = 7.0;
	private static final double FADE_FROM = 0.5;
	private static final double DROP = 10.0;
	private static final double SHRINK = 0.3;
	private static final double TUMBLE = 1.2;
	private static final double JAG = 3.0;

	double broken(Vec3 point, double lateness) {
		return this.fallen - point.distanceTo(this.impact) - JAG * lateness;
	}

	static double shown(double broken) {
		return 1 - Mth.clamp((broken / FALL_BAND - FADE_FROM) / (1 - FADE_FROM), 0, 1);
	}

	static double drop(double broken) {
		double share = Mth.clamp(broken / FALL_BAND, 0, 1);
		return DROP * share * share;
	}

	static Vec3 falling(Vec3 point, Vec3 centre, Vec3 axis, double broken) {
		if (broken <= 0) {
			return point;
		}
		double angle = TUMBLE * Mth.clamp(broken / FALL_BAND, 0, 1);
		Vec3 offset = point.subtract(centre).scale(size(broken));
		Vec3 rotated = offset.scale(Math.cos(angle)).add(axis.cross(offset).scale(Math.sin(angle)))
			.add(axis.scale(axis.dot(offset) * (1 - Math.cos(angle))));
		return centre.add(rotated).add(0, -drop(broken), 0);
	}

	static double size(double broken) {
		return 1 - SHRINK * Mth.clamp(broken / FALL_BAND, 0, 1);
	}
}
