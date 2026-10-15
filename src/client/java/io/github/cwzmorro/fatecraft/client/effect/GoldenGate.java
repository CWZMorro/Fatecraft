package io.github.cwzmorro.fatecraft.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** The golden gate Ea is drawn out of, all of it added light, soft at its edges. */
public final class GoldenGate {
	// Added light hides nothing behind it, so the gate's parts, all in one plane, never hide one another.
	public static final RenderType TYPE = LightTypes.ADDED;
	private static final double GLOW_REACH = 1.7;
	private static final double[] GLOW_AT = {0.0, 0.1, 0.3, 0.6, 1.0};
	private static final int[][] GLOW = {
		{255, 250, 232, 255}, {255, 238, 175, 210}, {255, 210, 95, 120}, {255, 170, 45, 45}, {255, 140, 30, 0}};
	private static final int RIPPLES = 4;
	private static final double RIPPLE_FROM = 0.15;
	private static final double RIPPLE_TO = 1.3;
	private static final double RIPPLE_TICKS = 32.0;
	private static final double RIPPLE_NARROW = 0.04;
	private static final double RIPPLE_WIDE = 0.12;
	private static final double RIPPLE_FADE_IN = 0.15;
	private static final int[] RIPPLE = {255, 220, 120, 210};
	private static final double[] RINGS = {1.0, 0.68, 0.36};
	private static final double[] RING_WIDTHS = {0.07, 0.04, 0.03};
	private static final int[][] RING_COLOURS = {{255, 205, 80, 235}, {255, 240, 200, 210}, {255, 228, 150, 190}};
	private static final int[] SWIRLS = {3, 2, 2};
	private static final double SWIRL_SPAN = 2.0;
	private static final double SWIRL_WIDER = 2.2;
	private static final double SWIRL_CURL = 0.15;
	private static final double SWIRL_PER_TICK = 0.12;
	private static final int[] SWIRL = {255, 250, 228, 255};
	private static final double SWIRL_HEAD = 0.08;
	private static final double BREATH = 0.04;
	private static final double BREATH_PER_TICK = 0.15;
	private static final double STREAK_LENGTH = 2.4;
	private static final double STREAK_WIDTH = 0.07;
	private static final int[] STREAK = {255, 225, 150, 170};
	private static final double STREAK_CORE_LENGTH = 1.5;
	private static final double STREAK_CORE_WIDTH = 0.025;
	private static final int[] STREAK_CORE = {255, 250, 232, 230};
	private static final int STREAK_POINTS = 12;
	private static final int SEGMENTS_PER_TURN = 64;

	private GoldenGate() {
	}

	public static void draw(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, Vec3 centre, Vec3 normal, double radius, double ticks, double opacity) {
		if (radius <= 0 || opacity <= 0) {
			return;
		}
		Vec3 u = LightStrokes.perpendicular(normal);
		Vec3 v = normal.cross(u);
		double r = radius * (1 + BREATH * Math.sin(ticks * BREATH_PER_TICK));
		double[] glowRadii = new double[GLOW_AT.length];
		int[][] glow = new int[GLOW.length][];
		for (int k = 0; k < GLOW_AT.length; k++) {
			glowRadii[k] = r * GLOW_REACH * GLOW_AT[k];
			glow[k] = LightStrokes.faded(GLOW[k], opacity);
		}
		band(pose, buffer, centre, u, v, glowRadii, glow, 0.0, Mth.TWO_PI, false, 0.0);
		for (int ripple = 0; ripple < RIPPLES; ripple++) {
			double run = (ticks / RIPPLE_TICKS + ripple / (double) RIPPLES) % 1.0;
			double shown = Math.min(run / RIPPLE_FADE_IN, (1 - run) / (1 - RIPPLE_FADE_IN));
			softRing(pose, buffer, centre, u, v, r * Mth.lerp(run, RIPPLE_FROM, RIPPLE_TO), r * Mth.lerp(run, RIPPLE_NARROW, RIPPLE_WIDE),
				LightStrokes.faded(RIPPLE, opacity * shown), 0.0, Mth.TWO_PI, false, 0.0);
		}
		for (int ring = 0; ring < RINGS.length; ring++) {
			double at = r * RINGS[ring];
			double width = r * RING_WIDTHS[ring];
			softRing(pose, buffer, centre, u, v, at, width, LightStrokes.faded(RING_COLOURS[ring], opacity), 0.0, Mth.TWO_PI, false, 0.0);
			double turn = ticks * SWIRL_PER_TICK * (ring % 2 == 0 ? 1 : -1);
			for (int swirl = 0; swirl < SWIRLS[ring]; swirl++) {
				double head = turn + Mth.TWO_PI * swirl / SWIRLS[ring];
				// Trailing behind the head, whichever way the ring turns.
				double span = ring % 2 == 0 ? -SWIRL_SPAN : SWIRL_SPAN;
				softRing(pose, buffer, centre, u, v, at, width * SWIRL_WIDER, LightStrokes.faded(SWIRL, opacity), head, span, true, SWIRL_CURL * r / at);
			}
		}
		streak(pose, buffer, camera, centre, normal, r * STREAK_LENGTH, r * STREAK_WIDTH, LightStrokes.faded(STREAK, opacity));
		streak(pose, buffer, camera, centre, normal, r * STREAK_CORE_LENGTH, r * STREAK_CORE_WIDTH, LightStrokes.faded(STREAK_CORE, opacity));
	}

	private static void softRing(PoseStack.Pose pose, VertexConsumer buffer, Vec3 centre, Vec3 u, Vec3 v, double at, double halfWidth, int[] rgba,
		double from, double span, boolean tail, double curl) {
		int[] edge = LightStrokes.faded(rgba, 0.0);
		band(pose, buffer, centre, u, v, new double[]{Math.max(0.0, at - halfWidth), at, at + halfWidth}, new int[][]{edge, rgba, edge}, from, span, tail, curl);
	}

	private static void band(PoseStack.Pose pose, VertexConsumer buffer, Vec3 centre, Vec3 u, Vec3 v, double[] radii, int[][] rgba, double from,
		double span, boolean tail, double curl) {
		int segments = Math.max(1, (int) Math.ceil(SEGMENTS_PER_TURN * Math.abs(span) / Mth.TWO_PI));
		double cos0 = Math.cos(from);
		double sin0 = Math.sin(from);
		for (int s = 0; s < segments; s++) {
			double t0 = s / (double) segments;
			double t1 = (s + 1) / (double) segments;
			double cos1 = Math.cos(from + span * t1);
			double sin1 = Math.sin(from + span * t1);
			double share0 = tail ? trail(t0) : 1.0;
			double share1 = tail ? trail(t1) : 1.0;
			for (int k = 1; k < radii.length; k++) {
				LightStrokes.planeVertex(pose, buffer, centre, u, v, radii[k - 1] * (1 - curl * t0), cos0, sin0, rgba[k - 1], share0);
				LightStrokes.planeVertex(pose, buffer, centre, u, v, radii[k - 1] * (1 - curl * t1), cos1, sin1, rgba[k - 1], share1);
				LightStrokes.planeVertex(pose, buffer, centre, u, v, radii[k] * (1 - curl * t1), cos1, sin1, rgba[k], share1);
				LightStrokes.planeVertex(pose, buffer, centre, u, v, radii[k] * (1 - curl * t0), cos0, sin0, rgba[k], share0);
			}
			cos0 = cos1;
			sin0 = sin1;
		}
	}

	private static double trail(double t) {
		return Math.min(1.0, t / SWIRL_HEAD) * (1 - t) * (1 - t);
	}

	private static void streak(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, Vec3 centre, Vec3 normal, double halfLength, double halfWidth,
		int[] rgba) {
		Vec3 toCentre = centre.subtract(camera);
		Vec3 along = toCentre.cross(new Vec3(0, 1, 0));
		if (along.lengthSqr() == 0) {
			along = LightStrokes.perpendicular(normal);
		}
		along = along.normalize();
		Vec3 across = along.cross(toCentre).normalize().scale(halfWidth);
		int[] edge = LightStrokes.faded(rgba, 0.0);
		for (int i = 1; i < STREAK_POINTS; i++) {
			double t0 = (i - 1) / (double) (STREAK_POINTS - 1) * 2 - 1;
			double t1 = i / (double) (STREAK_POINTS - 1) * 2 - 1;
			Vec3 a = centre.add(along.scale(t0 * halfLength));
			Vec3 b = centre.add(along.scale(t1 * halfLength));
			int[] middleA = LightStrokes.faded(rgba, (1 - Math.abs(t0)) * (1 - Math.abs(t0)));
			int[] middleB = LightStrokes.faded(rgba, (1 - Math.abs(t1)) * (1 - Math.abs(t1)));
			LightStrokes.quad(pose, buffer, new Vec3[]{a.subtract(across), b.subtract(across), b, a}, new int[][]{edge, edge, middleB, middleA});
			LightStrokes.quad(pose, buffer, new Vec3[]{a, b, b.add(across), a.add(across)}, new int[][]{middleA, middleB, edge, edge});
		}
	}
}
