package io.github.cwzmorro.fatecraft.altera;

import net.minecraft.world.phys.Vec3;

/** Teardrop's seal layout and Spread's merged footprint, shared by its damage, digging and drawing. */
public final class TeardropGeometry {
	public static final double OUTPUT_RING_SHARE = 1.0;
	public static final int SIDE_SEALS = 1;
	public static final int SEALS = SIDE_SEALS * 2 + 1;

	private TeardropGeometry() {
	}

	public static double sealRadius(AlteraConfig config) {
		return Math.max(config.teardropRadius(), config.spreadRadius()) * config.teardropSealShare();
	}

	public static double spreadRadius(AlteraConfig config) {
		return sky(config, SIDE_SEALS).distanceTo(sky(config, 0)) + sealRadius(config);
	}

	public static double spreadStartRadius(AlteraConfig config) {
		return sealRadius(config) * OUTPUT_RING_SHARE;
	}

	/** Returns Spread's radius at {@code share} of the drop (0 at the middle seal, 1 at the ground): a bell easing into a straight skirt. */
	public static double spreadRadiusAt(AlteraConfig config, double share) {
		double flare = Math.clamp(share / config.spreadFlareShare(), 0, 1);
		double remaining = 1 - flare;
		double start = spreadStartRadius(config);
		return start + (spreadRadius(config) - start) * (1 - remaining * remaining);
	}

	public static double impactRadius(AlteraConfig config) {
		double spacing = surface(config, SIDE_SEALS).distanceTo(surface(config, 0));
		return spacing + Math.max(config.spreadImpactRadius(), spacing / 2 + config.spreadRadius());
	}

	public static Vec3 axis(AlteraConfig config) {
		return mainSeal(config).normalize();
	}

	public static Vec3 sky(AlteraConfig config, int side) {
		Vec3 axis = axis(config);
		Vec3 lateral = new Vec3(-axis.y, axis.x, 0);
		return mainSeal(config).add(lateral.scale(side * sealRadius(config) * config.teardropSideSealSpacing()));
	}

	public static Vec3 surface(AlteraConfig config, int side) {
		Vec3 origin = sky(config, side);
		double slope = config.teardropSkyOffset() / config.teardropHeight();
		return new Vec3(origin.x - origin.y * slope, 0, 0);
	}

	/** Returns the beam's end, {@code depth} blocks below {@code target} on the straight line from {@code origin} through it. */
	public static Vec3 bottom(Vec3 origin, Vec3 target, double depth) {
		Vec3 along = target.subtract(origin).normalize();
		return target.add(along.scale(depth / -along.y));
	}

	private static Vec3 mainSeal(AlteraConfig config) {
		return new Vec3(config.teardropSkyOffset(), config.teardropHeight(), 0);
	}
}
