package io.github.cwzmorro.fatecraft.chain;

import io.github.cwzmorro.fatecraft.GoldenRatio;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Where Enkidu's gates open round the target and how its chains fly out of them, worked out alike on the server and the
 * client from the bind's seed.
 */
public final class ChainLayout {
	// The gates open over OPEN_TICKS ticks before the chains come out; the chains fly CHAIN_SPEED blocks a tick.
	public static final int OPEN_TICKS = 8;
	public static final double CHAIN_SPEED = 2.4;
	public static final int RELEASE_TICKS = 16;
	private static final double CLEARANCE = 4.0;
	private static final double LOWEST_DEGREES = -15.0;
	private static final double HIGHEST_DEGREES = 60.0;
	private static final double AROUND_JITTER = 0.4;
	private static final double UP_JITTER_DEGREES = 8.0;

	private ChainLayout() {
	}

	public static double radius(AABB box) {
		return Math.max(box.getXsize(), box.getZsize()) / 2 + CLEARANCE;
	}

	public static List<Vec3> gates(Vec3 centre, double radius, long seed, int count) {
		RandomSource random = RandomSource.create(seed);
		double spin = random.nextDouble() * Mth.TWO_PI;
		List<Vec3> gates = new ArrayList<>(count);
		for (int gate = 0; gate < count; gate++) {
			// One turn round between gates, by the golden angle, so they never line up.
			double around = spin + gate * GoldenRatio.ANGLE + (random.nextDouble() - 0.5) * AROUND_JITTER * GoldenRatio.ANGLE;
			double up = Math.toRadians(Mth.lerp((gate + 0.5) / count, LOWEST_DEGREES, HIGHEST_DEGREES) + (random.nextDouble() - 0.5) * 2 * UP_JITTER_DEGREES);
			gates.add(centre.add(Math.cos(around) * Math.cos(up) * radius, Math.sin(up) * radius, Math.sin(around) * Math.cos(up) * radius));
		}
		return gates;
	}

	public static int arrivalTicks(double radius) {
		return OPEN_TICKS + Mth.ceil(radius / CHAIN_SPEED);
	}
}
