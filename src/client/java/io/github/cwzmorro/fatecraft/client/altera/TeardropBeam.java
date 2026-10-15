package io.github.cwzmorro.fatecraft.client.altera;

import static io.github.cwzmorro.fatecraft.client.altera.TeardropPalette.CYAN;
import static io.github.cwzmorro.fatecraft.client.altera.TeardropPalette.HALO;
import static io.github.cwzmorro.fatecraft.client.altera.TeardropPalette.RED;
import static io.github.cwzmorro.fatecraft.client.altera.TeardropPalette.RED_CORE;
import static io.github.cwzmorro.fatecraft.client.altera.TeardropPalette.RED_HALO;
import static io.github.cwzmorro.fatecraft.client.altera.TeardropPalette.SPREAD_CORE;
import static io.github.cwzmorro.fatecraft.client.altera.TeardropPalette.SPREAD_HALO;
import static io.github.cwzmorro.fatecraft.client.altera.TeardropPalette.SPREAD_HAZE;
import static io.github.cwzmorro.fatecraft.client.altera.TeardropPalette.VOLUME_CYAN;
import static io.github.cwzmorro.fatecraft.client.altera.TeardropPalette.VOLUME_WHITE;
import static io.github.cwzmorro.fatecraft.client.altera.TeardropPalette.WHITE;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.GoldenRatio;
import io.github.cwzmorro.fatecraft.altera.TeardropGeometry;
import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Teardrop's beam: the thin light down from Mars, Spread's bell or Condensed's beam, and the impact on the ground. */
final class TeardropBeam {
	private static final double MARS_BEYOND_HEIGHT = 50;
	private static final double INPUT_RADIUS = 0.42;
	private static final double INPUT_HALO_SCALE = 3;
	private static final double INPUT_FADE_DIVISOR = 3;
	private static final double CORE_SHARE = 0.86;
	private static final double HALO_SHARE = 1.15;
	private static final double SPREAD_TICKS = 6;
	private static final double GROUND_LIFT = 0.12;
	// The bell's samples and the strands' uneven golden-angle spread keep it from looking like a cage of spokes.
	private static final int PROFILE_POINTS = 33;
	private static final int STRANDS = 40;
	private static final int CURTAIN_STREAKS = 80;
	private static final double STRAND_INNER = 0.35;
	private static final double STRAND_RANGE = 0.65;
	private static final double STRAND_WIDTH = 0.012;
	private static final double EDGE_THREAD_WIDTH = 0.35;
	private static final double STRAND_GLOW_SCALE = 2;
	private static final double STRAND_GLOW_ALPHA = 0.3;
	private static final double SATELLITE_ROOT_SHARE = 0.65;
	private static final double STRAND_EDGE_SHARE = 0.9;
	private static final double WIDTH_VARIATION = 2.5;
	private static final double FLOW_SPEED = 0.045;
	private static final double FLOW_BASE = 0.28;
	private static final double FLOW_RANGE = 0.72;
	private static final double END_FADE_SHARE = 0.08;
	private static final int VOLUME_LAYERS = 8;
	private static final double VOLUME_INNER_SHARE = 0.25;
	private static final double VOLUME_RANGE = 0.75;
	private static final double VOLUME_ALPHA = 0.08;
	private static final double VOLUME_EDGE_ALPHA = 0.55;
	private static final double SHAFT_ALPHA = 0.2;
	private static final double AXIS_SHARE = 0.16;
	private static final double AXIS_ALPHA = 0.55;
	private static final double SHAFT_SHARE = 0.72;
	private static final double BURST_CORE_SCALE = 1.65;
	private static final double BURST_WASH_SCALE = 2.8;
	private static final double BURST_LIFT = 0.04;
	private static final double SIDE_LIGHT_OFFSET = 0.72;
	private static final double SIDE_LIGHT_RADIUS = 0.65;
	private static final double BURST_GLARE_SHARE = 1.65;
	private static final double BURST_GLARE_HALO = 1.8;
	private static final double BURST_HOT_SHARE = 1.0;
	private static final double CURTAIN_MIN_LENGTH = 0.18;
	private static final double CURTAIN_LENGTH_RANGE = 0.62;
	private static final double CURTAIN_WIDTH = 0.04;
	private static final double CURTAIN_HALO_SCALE = 2;
	private static final double CURTAIN_HALO_ALPHA = 0.08;
	private static final double CURTAIN_BRIGHTNESS = 0.32;
	private static final double WHITE_ZONE = 0.45;
	private static final int RIBBON_POINTS = 9;
	private static final int RADIAL_SEGMENTS = 96;
	private static final int THREADS = 16;
	private static final double THREAD_WIDTH = 0.15;
	private static final double THREAD_LENGTH = 0.3;
	private static final double THREAD_SPEED = 0.06;
	private static final double THREAD_END_ALPHA = 0.8;

	private TeardropBeam() {
	}

	static void draw(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, TeardropPhotonRayRenderer.State state, boolean added) {
		double marsReach = FateConfig.altera().teardropHeight() + MARS_BEYOND_HEIGHT;
		Vec3 far = state.sky.add(state.axis.scale(marsReach));
		Vec3 near = state.sky.add(state.axis.scale(marsReach / INPUT_FADE_DIVISOR));
		int[] body = state.spread ? CYAN : RED;
		int[] core = state.spread ? WHITE : RED_CORE;
		int[] halo = state.spread ? HALO : RED_HALO;
		int[] input = added ? core : body;
		double inputWidth = INPUT_RADIUS * (added ? CORE_SHARE : 1);
		LightStrokes.stroke(pose, buffer, eye, far, near, inputWidth, inputWidth, 0, 1, input);
		LightStrokes.stroke(pose, buffer, eye, near, state.sky, inputWidth, inputWidth, 1, 1, input);
		if (added) {
			double haloWidth = INPUT_RADIUS * INPUT_HALO_SCALE;
			LightStrokes.stroke(pose, buffer, eye, far, state.sky, haloWidth, haloWidth, 0, 1, halo);
		}
		double opening = state.spread ? Mth.smoothstep(Mth.clamp(state.firingTicks / SPREAD_TICKS, 0, 1)) : 1;
		if (state.spread) {
			spread(pose, buffer, eye, state, opening, added);
		} else {
			beam(pose, buffer, eye, state, added, opening, body, core, halo);
		}
		if (added && state.landed) {
			impact(pose, buffer, eye, state, opening, state.surface);
		}
	}

	private static void beam(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye,
		TeardropPhotonRayRenderer.State state, boolean added, double opening, int[] body, int[] core, int[] halo) {
		Vec3 origin = state.sky;
		Vec3 end = state.impact;
		double radius = state.radius;
		if (!added) {
			LightStrokes.line(pose, buffer, eye, origin, end, radius, body);
		} else {
			LightStrokes.line(pose, buffer, eye, origin, end, radius * CORE_SHARE, core);
			LightStrokes.line(pose, buffer, eye, origin, end, radius * HALO_SHARE, halo);
			facingDisc(pose, buffer, eye, origin, radius * HALO_SHARE, halo, opening);
			facingDisc(pose, buffer, eye, origin, radius * CORE_SHARE, core, opening);
			for (int i = 0; i < THREADS; i++) {
				double angle = i * Mth.TWO_PI / THREADS;
				double t = (i / (double) THREADS + state.firingTicks * THREAD_SPEED) % 1;
				Vec3 offset = LightStrokes.round(Vec3.ZERO, radius * CORE_SHARE, state.u, state.v, angle);
				Vec3 start = origin.lerp(end, Math.max(0, t - THREAD_LENGTH)).add(offset);
				Vec3 finish = origin.lerp(end, t).add(offset);
				LightStrokes.stroke(pose, buffer, eye, start, finish, THREAD_WIDTH, THREAD_WIDTH, 0, THREAD_END_ALPHA, RED_CORE);
			}
		}
	}

	private static double radiusAt(TeardropPhotonRayRenderer.State state, double t, double opening) {
		return Mth.lerp(opening, INPUT_RADIUS, TeardropGeometry.spreadRadiusAt(FateConfig.altera(), share(state, t)));
	}

	private static double flare(TeardropPhotonRayRenderer.State state, double t) {
		double start = TeardropGeometry.spreadStartRadius(FateConfig.altera());
		double full = TeardropGeometry.spreadRadius(FateConfig.altera());
		return (TeardropGeometry.spreadRadiusAt(FateConfig.altera(), share(state, t)) - start) / (full - start);
	}

	private static double share(TeardropPhotonRayRenderer.State state, double t) {
		return (state.sky.y - state.sky.lerp(state.impact, t).y) / (state.sky.y - state.surface.y);
	}

	private static void spread(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye,
		TeardropPhotonRayRenderer.State state, double opening, boolean added) {
		// Spread's sections lie flat, so its fast-widening outer flare can't turn upward.
		Vec3 u = Vec3.X_AXIS;
		Vec3 v = Vec3.Z_AXIS;
		if (!added) {
			Vec3[] centres = new Vec3[PROFILE_POINTS];
			double[] radii = new double[PROFILE_POINTS];
			double[] alpha = new double[PROFILE_POINTS];
			for (int i = 0; i < PROFILE_POINTS; i++) {
				double t = i / (double) (PROFILE_POINTS - 1);
				centres[i] = state.sky.lerp(state.impact, t);
				radii[i] = radiusAt(state, t, opening);
				alpha[i] = opening * endFade(t);
			}
			LightStrokes.tube(pose, buffer, eye, centres, radii, u, v, RADIAL_SEGMENTS, SPREAD_HAZE, alpha);
			return;
		}
		// The opening blooms just behind the seals, out to the side circles' inner rims.
		Vec3 burst = state.sky.subtract(state.axis.scale(BURST_LIFT));
		disc(pose, buffer, burst, state.u, state.v, state.sealRadius * BURST_WASH_SCALE, HALO, opening);
		disc(pose, buffer, burst, state.u, state.v, state.sealRadius * BURST_CORE_SCALE, WHITE, opening);
		Vec3 facing = eye.subtract(burst).normalize();
		Vec3 glareU = LightStrokes.perpendicular(facing);
		Vec3 glareV = facing.cross(glareU);
		disc(pose, buffer, burst, glareU, glareV, state.sealRadius * BURST_GLARE_HALO, HALO, opening);
		disc(pose, buffer, burst, glareU, glareV, state.sealRadius * BURST_GLARE_SHARE, WHITE, opening);
		disc(pose, buffer, burst, glareU, glareV, state.sealRadius * BURST_HOT_SHARE, WHITE, opening);
		for (int site = 0; site < TeardropGeometry.SEALS; site++) {
			if (site == TeardropGeometry.SIDE_SEALS) {
				continue;
			}
			Vec3 side = state.sealSites[site];
			Vec3 lit = side.add(state.sky.subtract(side).normalize().scale(state.sealRadius * SIDE_LIGHT_OFFSET))
				.subtract(state.axis.scale(BURST_LIFT));
			disc(pose, buffer, lit, state.u, state.v, state.sealRadius * SIDE_LIGHT_RADIUS, WHITE, opening);
			disc(pose, buffer, lit, glareU, glareV, state.sealRadius * SIDE_LIGHT_RADIUS, CYAN, opening);
		}
		Vec3[] centres = new Vec3[PROFILE_POINTS];
		double[] radii = new double[PROFILE_POINTS];
		double[] fillAlpha = new double[PROFILE_POINTS];
		double[] coreWidths = new double[PROFILE_POINTS];
		for (int i = 0; i < PROFILE_POINTS; i++) {
			double t = i / (double) (PROFILE_POINTS - 1);
			centres[i] = state.sky.lerp(state.impact, t);
			radii[i] = radiusAt(state, t, opening);
			coreWidths[i] = radii[i] * SHAFT_SHARE;
			fillAlpha[i] = opening * Math.min(1, (1 - t) / END_FADE_SHARE);
		}
		double[] coreAlpha = new double[PROFILE_POINTS];
		for (int i = 0; i < PROFILE_POINTS; i++) {
			coreAlpha[i] = fillAlpha[i] * SHAFT_ALPHA;
		}
		LightStrokes.strip(pose, buffer, eye, centres, coreWidths, WHITE, coreAlpha);
		for (int i = 0; i < PROFILE_POINTS; i++) {
			coreWidths[i] = radii[i] * AXIS_SHARE;
			coreAlpha[i] = fillAlpha[i] * AXIS_ALPHA;
		}
		LightStrokes.strip(pose, buffer, eye, centres, coreWidths, WHITE, coreAlpha);
		for (int layer = 0; layer < VOLUME_LAYERS; layer++) {
			double share = VOLUME_INNER_SHARE + VOLUME_RANGE * layer / (VOLUME_LAYERS - 1);
			double[] shellRadii = new double[PROFILE_POINTS];
			double[] shellAlpha = new double[PROFILE_POINTS];
			for (int i = 0; i < PROFILE_POINTS; i++) {
				shellRadii[i] = radii[i] * share;
				shellAlpha[i] = fillAlpha[i] * VOLUME_ALPHA * Mth.lerp(share, 1, VOLUME_EDGE_ALPHA);
			}
			LightStrokes.tube(pose, buffer, eye, centres, shellRadii, u, v, RADIAL_SEGMENTS,
				share < SHAFT_SHARE ? VOLUME_WHITE : VOLUME_CYAN, shellAlpha);
		}
		double full = TeardropGeometry.spreadRadius(FateConfig.altera());
		for (int strand = 0; strand < STRANDS; strand++) {
			double variation = LightStrokes.hash(strand + 1, 0);
			double radial = (STRAND_INNER + STRAND_RANGE * variation) * STRAND_EDGE_SHARE;
			double angle = strand * GoldenRatio.ANGLE;
			// Roots cover the whole burst: across the middle circle and under both side circles.
			int site = strand % TeardropGeometry.SEALS;
			double spread = Math.sqrt(LightStrokes.hash(strand + PROFILE_POINTS, 0)) * state.sealRadius * SATELLITE_ROOT_SHARE;
			Vec3 root = state.sealSites[site].subtract(state.sky)
				.add(state.u.scale(Math.cos(angle) * spread)).add(state.v.scale(Math.sin(angle) * spread));
			// Each strand sweeps straight outward from its own root, so none cross the beam.
			double rootReach = Math.hypot(root.x, root.z);
			Vec3 outward = rootReach > 0 ? new Vec3(root.x / rootReach, 0, root.z / rootReach)
				: u.scale(Math.cos(angle)).add(v.scale(Math.sin(angle)));
			// Every strand sweeps out from its root, part of the way to the full width, even from under a side circle.
			double reach = Mth.lerp(radial, rootReach, full);
			Vec3[] points = new Vec3[PROFILE_POINTS];
			double[] widths = new double[PROFILE_POINTS];
			double[] alpha = new double[PROFILE_POINTS];
			for (int i = 0; i < PROFILE_POINTS; i++) {
				double t = i / (double) (PROFILE_POINTS - 1);
				double sweep = flare(state, t);
				Vec3 offset = outward.scale(Mth.lerp(sweep, rootReach, reach)).add(0, root.y * (1 - sweep), 0);
				Vec3 point = centres[i].add(offset.scale(opening));
				// A tilted frame can lift the outer flare above a side circle's root, so a strand going down never turns
				// back up; its width is kept.
				double ceiling = i == 0 ? point.y : points[i - 1].y;
				points[i] = new Vec3(point.x, Math.min(point.y, ceiling), point.z);
				widths[i] = state.sealRadius * STRAND_WIDTH * (1 + WIDTH_VARIATION * LightStrokes.hash(strand + STRANDS, 0))
					* Mth.lerp(radial, 1, EDGE_THREAD_WIDTH);
				double flow = (t - state.firingTicks * FLOW_SPEED + variation) * Mth.TWO_PI;
				alpha[i] = fillAlpha[i] * (FLOW_BASE + FLOW_RANGE * Math.pow(Math.max(0, Math.cos(flow)), 2));
			}
			LightStrokes.strip(pose, buffer, eye, points, widths, variation < WHITE_ZONE ? WHITE : CYAN, alpha);
			for (int i = 0; i < PROFILE_POINTS; i++) {
				widths[i] *= STRAND_GLOW_SCALE;
				alpha[i] *= STRAND_GLOW_ALPHA;
			}
			LightStrokes.strip(pose, buffer, eye, points, widths, HALO, alpha);
		}
		curtain(pose, buffer, eye, state, opening, u, v);
	}

	private static void curtain(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye,
		TeardropPhotonRayRenderer.State state, double opening, Vec3 u, Vec3 v) {
		double drop = state.sky.distanceTo(state.impact);
		double skirtStart = FateConfig.altera().spreadFlareShare() * (state.sky.y - state.surface.y)
			/ (state.sky.y - state.impact.y);
		for (int lane = 0; lane < CURTAIN_STREAKS; lane++) {
			double radial = Math.sqrt(LightStrokes.hash(lane + STRANDS, 0));
			double angle = lane * GoldenRatio.ANGLE;
			double length = CURTAIN_MIN_LENGTH + CURTAIN_LENGTH_RANGE * LightStrokes.hash(lane + CURTAIN_STREAKS, 0);
			double motion = (state.firingTicks * FLOW_SPEED + LightStrokes.hash(lane + 1, 0)) % 1;
			double endT = Mth.lerp(motion, Math.min(1, skirtStart + length), 1);
			double startT = Math.max(skirtStart, endT - length);
			Vec3 start = state.sky.lerp(state.impact, startT)
				.add(LightStrokes.round(Vec3.ZERO, radiusAt(state, startT, opening) * radial * STRAND_EDGE_SHARE, u, v, angle));
			Vec3 end = state.sky.lerp(state.impact, endT)
				.add(LightStrokes.round(Vec3.ZERO, radiusAt(state, endT, opening) * radial * STRAND_EDGE_SHARE, u, v, angle));
			double width = state.radius * CURTAIN_WIDTH * (1 + WIDTH_VARIATION * LightStrokes.hash(lane + PROFILE_POINTS, 0))
				* Mth.lerp(radial, 1, EDGE_THREAD_WIDTH);
			double brightness = opening * CURTAIN_BRIGHTNESS * (1 - radial * radial)
				* (FLOW_BASE + FLOW_RANGE * LightStrokes.hash(lane + RIBBON_POINTS, 0));
			if (drop > 0 && startT < endT) {
				ribbon(pose, buffer, eye, start, end, width * CURTAIN_HALO_SCALE, brightness * CURTAIN_HALO_ALPHA, HALO);
				ribbon(pose, buffer, eye, start, end, width, brightness, radial < WHITE_ZONE ? WHITE : CYAN);
			}
		}
	}

	private static double endFade(double t) {
		return Math.min(1, Math.min(t, 1 - t) / END_FADE_SHARE);
	}

	private static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, Vec3 start, Vec3 end,
		double width, double brightness, int[] colour) {
		Vec3[] points = new Vec3[RIBBON_POINTS];
		double[] widths = new double[RIBBON_POINTS];
		double[] alpha = new double[RIBBON_POINTS];
		for (int i = 0; i < RIBBON_POINTS; i++) {
			double t = i / (double) (RIBBON_POINTS - 1);
			double taper = Math.sin(t * Math.PI);
			points[i] = start.lerp(end, t);
			widths[i] = width * taper;
			alpha[i] = brightness * taper;
		}
		LightStrokes.strip(pose, buffer, eye, points, widths, colour, alpha);
	}

	private static void impact(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye,
		TeardropPhotonRayRenderer.State state, double opening, Vec3 surface) {
		Vec3 ground = surface.add(0, GROUND_LIFT, 0);
		disc(pose, buffer, ground, Vec3.X_AXIS, Vec3.Z_AXIS, state.impactRadius, state.spread ? SPREAD_HALO : RED_HALO, opening);
		double splash = state.spread ? state.impactRadius : state.radius;
		disc(pose, buffer, ground, Vec3.X_AXIS, Vec3.Z_AXIS, splash, state.spread ? CYAN : RED, opening);
		facingDisc(pose, buffer, eye, ground, splash, state.spread ? SPREAD_CORE : RED_CORE, opening);
	}

	static void disc(PoseStack.Pose pose, VertexConsumer buffer, Vec3 centre, Vec3 u, Vec3 v,
		double radius, int[] colour, double alpha) {
		LightStrokes.disc(pose, buffer, centre, u, v, radius, colour, alpha, RADIAL_SEGMENTS);
	}

	static void facingDisc(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, Vec3 centre, double radius, int[] colour, double alpha) {
		LightStrokes.facingDisc(pose, buffer, eye, centre, radius, colour, alpha, RADIAL_SEGMENTS);
	}
}
