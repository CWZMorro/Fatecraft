package io.github.cwzmorro.fatecraft.ubw;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Enuma Elish breaking the marble (as in Fate/Zero), shared by the server's terrain and the client's look. Cracks race
 * out from where the beam crossed the shell, then the pieces fall away behind a crumble front so the real world shows
 * through. Ages are ticks of the dimension's game time since the break; distances are straight-line, from the impact.
 */
public final class UbwShatter {
	// Shares of the crumble time: the cracks cover the field over CRACK_SHARE, and the first pieces fall after FALL_FROM.
	private static final double CRACK_SHARE = 0.35;
	private static final double FALL_FROM = 0.15;
	private static final double FALL_EASE = 1.6;
	private static final double SPAN_MARGIN = 1.0;

	private UbwShatter() { }

	public static double span(Vec3 impact, int radius) {
		return impact.length() + radius + SPAN_MARGIN;
	}

	public static double cracks(double age, int ticks, double span) {
		double share = Mth.clamp(age / (CRACK_SHARE * ticks), 0, 1);
		return span * (1 - (1 - share) * (1 - share));
	}

	public static double fall(double age, int ticks, double span) {
		double share = Mth.clamp((age - FALL_FROM * ticks) / ((1 - FALL_FROM) * ticks), 0, 1);
		return span * Math.pow(share, FALL_EASE);
	}
}
