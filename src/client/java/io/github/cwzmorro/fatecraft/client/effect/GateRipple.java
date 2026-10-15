package io.github.cwzmorro.fatecraft.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * A gate of the Gate of Babylon, for its weapons and for Enkidu, laid out from the gate's own seed. Drawn twice: blended in
 * deep gold ({@link #UNDER_TYPE}) to stay golden against a bright sky, then light added over it ({@link #TYPE}).
 */
public final class GateRipple {
	public static final RenderType UNDER_TYPE = LightTypes.BLENDED;
	public static final RenderType TYPE = LightTypes.ADDED;
	// Tuning numbers for the crests and sheen, as shares of the gate's radius unless noted.
	private static final int CRESTS = 7;
	private static final double CREST_FROM = 0.08;
	private static final double CREST_TO = 1.15;
	private static final double CREST_TICKS = 40.0;
	private static final double CREST_SPEED_SPREAD = 0.3;
	private static final double CREST_FADE_IN = 0.2;
	private static final double WOBBLE = 0.07;
	private static final double OFF_CENTRE = 0.06;
	private static final double CREST_NARROW = 0.015;
	private static final double CREST_WIDE = 0.06;
	private static final double WIDTH_VARIES = 0.6;
	private static final double BREAK_DEPTH = 0.9;
	private static final int[] CREST_LIGHT = {255, 225, 140, 205};
	private static final int[] CREST_GOLD = {220, 150, 25, 215};
	private static final double SHEEN_REACH = 1.25;
	private static final double[] SHEEN_AT = {0.0, 0.45, 0.8, 1.0};
	private static final int[][] SHEEN_GOLD = {{240, 180, 50, 110}, {230, 165, 40, 75}, {220, 150, 30, 25}, {210, 140, 25, 0}};
	private static final double HEART_REACH = 0.7;
	private static final double[] HEART_AT = {0.0, 0.55, 0.8, 1.0};
	private static final int[][] HEART_GOLD = {{255, 236, 175, 250}, {252, 222, 140, 235}, {245, 195, 85, 120}, {235, 170, 50, 0}};
	private static final double GLOW_REACH = 1.45;
	private static final double[] GLOW_AT = {0.0, 0.3, 0.65, 1.0};
	private static final int[][] GLOW_LIGHT = {{255, 240, 190, 165}, {255, 215, 125, 105}, {255, 185, 65, 40}, {255, 165, 40, 0}};
	private static final double CORE_REACH = 0.5;
	private static final double[] CORE_AT = {0.0, 0.25, 0.6, 1.0};
	private static final int[][] CORE_LIGHT = {{255, 252, 235, 255}, {255, 240, 185, 200}, {255, 215, 110, 80}, {255, 190, 60, 0}};
	private static final int RAYS = 5;
	private static final double RAY_SHORTEST = 0.5;
	private static final double RAY_LONGEST = 1.1;
	private static final double RAY_WIDTH = 0.035;
	private static final double RAY_SIDE = 0.5;
	private static final double RAY_TURN = 0.01;
	private static final double RAY_PULSE = 0.1;
	private static final int[] RAY_LIGHT = {255, 238, 170, 120};
	private static final int MOTES = 10;
	private static final double MOTE_TICKS = 50.0;
	private static final double MOTE_OUT = 1.4;
	private static final double MOTE_FORWARD = 0.6;
	private static final double MOTE_SIZE = 0.035;
	private static final int[] MOTE_LIGHT = {255, 236, 150, 255};
	private static final double WOBBLE_DRIFT = 0.02;
	private static final int[] WOBBLE_WAVES = {2, 3, 5};
	private static final int[] BREAK_WAVES = {2, 3};
	private static final int MOST_SEGMENTS = 48;
	private static final int FEWEST_SEGMENTS = 20;
	private static final double FULL_DETAIL_SIZE = 0.4;

	private GateRipple() {
	}

	public static int segments(double radius, double distance) {
		return Mth.clamp((int) Math.ceil(MOST_SEGMENTS * radius / Math.max(distance, radius) / FULL_DETAIL_SIZE), FEWEST_SEGMENTS, MOST_SEGMENTS);
	}

	public static double opened(double age, int opensAt, int openTicks, int closesAt, int closeTicks) {
		return closesAt >= 0 ? 1 - Math.clamp((age - closesAt) / closeTicks, 0.0, 1.0) : Math.clamp((age - opensAt) / openTicks, 0.0, 1.0);
	}

	public static void drawUnder(PoseStack.Pose pose, VertexConsumer buffer, Vec3 normal, double radius, double ticks, double opacity, long seed, int segments) {
		Plane plane = new Plane(normal);
		RandomSource random = RandomSource.create(seed);
		disc(pose, buffer, plane, radius * SHEEN_REACH, SHEEN_AT, SHEEN_GOLD, opacity, random.nextDouble() * Mth.TWO_PI, segments);
		crests(pose, buffer, plane, radius, ticks, opacity, random, segments, CREST_GOLD);
		disc(pose, buffer, plane, radius * HEART_REACH, HEART_AT, HEART_GOLD, opacity, 0.0, segments);
	}

	public static void draw(PoseStack.Pose pose, VertexConsumer buffer, Vec3 toCamera, Vec3 normal, double radius, double ticks, double opacity, long seed,
		int segments) {
		Plane plane = new Plane(normal);
		RandomSource random = RandomSource.create(seed);
		// The same draws as drawUnder, so the crests lie where their gold does.
		random.nextDouble();
		disc(pose, buffer, plane, radius * GLOW_REACH, GLOW_AT, GLOW_LIGHT, opacity, 0.0, segments);
		crests(pose, buffer, plane, radius, ticks, opacity, random, segments, CREST_LIGHT);
		disc(pose, buffer, plane, radius * CORE_REACH, CORE_AT, CORE_LIGHT, opacity, 0.0, segments);
		rays(pose, buffer, plane, radius, ticks, opacity, random);
		motes(pose, buffer, plane, toCamera, radius, ticks, opacity, random);
	}

	private record Plane(Vec3 normal, Vec3 u, Vec3 v) {
		Plane(Vec3 normal) {
			this(normal, LightStrokes.perpendicular(normal));
		}

		private Plane(Vec3 normal, Vec3 u) {
			this(normal, u, normal.cross(u));
		}
	}

	private static void disc(PoseStack.Pose pose, VertexConsumer buffer, Plane plane, double reach, double[] at, int[][] rgba, double opacity, double phase,
		int segments) {
		for (int s = 0; s < segments; s++) {
			double a0 = Mth.TWO_PI * s / segments;
			double a1 = Mth.TWO_PI * (s + 1) / segments;
			double edge0 = 1 + WOBBLE * Math.sin(WOBBLE_WAVES[1] * a0 + phase);
			double edge1 = 1 + WOBBLE * Math.sin(WOBBLE_WAVES[1] * a1 + phase);
			for (int k = 1; k < at.length; k++) {
				quad(pose, buffer, plane, Vec3.ZERO, a0, a1, reach * at[k - 1] * edge0, reach * at[k - 1] * edge1, reach * at[k] * edge1, reach * at[k] * edge0,
					rgba[k - 1], rgba[k], opacity, opacity);
			}
		}
	}

	private static void crests(PoseStack.Pose pose, VertexConsumer buffer, Plane plane, double radius, double ticks, double opacity, RandomSource random,
		int segments, int[] rgba) {
		int[] edge = LightStrokes.faded(rgba, 0.0);
		for (int crest = 0; crest < CRESTS; crest++) {
			double start = random.nextDouble();
			double speed = 1 + (random.nextDouble() - 0.5) * 2 * CREST_SPEED_SPREAD;
			double offAngle = random.nextDouble() * Mth.TWO_PI;
			double[] wobble = phases(random, WOBBLE_WAVES.length);
			double[] breaks = phases(random, BREAK_WAVES.length + 1);
			double run = (ticks * speed / CREST_TICKS + start) % 1.0;
			double shown = opacity * Math.min(run / CREST_FADE_IN, (1 - run) / (1 - CREST_FADE_IN));
			if (shown <= 0) {
				continue;
			}
			double at = radius * Mth.lerp(run, CREST_FROM, CREST_TO);
			double half = radius * Mth.lerp(run, CREST_NARROW, CREST_WIDE);
			Vec3 centre = plane.u().scale(Math.cos(offAngle)).add(plane.v().scale(Math.sin(offAngle))).scale(radius * OFF_CENTRE * run);
			for (int s = 0; s < segments; s++) {
				double a0 = Mth.TWO_PI * s / segments;
				double a1 = Mth.TWO_PI * (s + 1) / segments;
				double r0 = at * (1 + wobble(a0, wobble, ticks));
				double r1 = at * (1 + wobble(a1, wobble, ticks));
				double w0 = half * (1 + WIDTH_VARIES * Math.sin(BREAK_WAVES[1] * a0 + breaks[BREAK_WAVES.length]));
				double w1 = half * (1 + WIDTH_VARIES * Math.sin(BREAK_WAVES[1] * a1 + breaks[BREAK_WAVES.length]));
				double b0 = shown * brightness(a0, breaks);
				double b1 = shown * brightness(a1, breaks);
				quad(pose, buffer, plane, centre, a0, a1, Math.max(0, r0 - w0), Math.max(0, r1 - w1), r1, r0, edge, rgba, b0, b1);
				quad(pose, buffer, plane, centre, a0, a1, r0, r1, r1 + w1, r0 + w0, rgba, edge, b0, b1);
			}
		}
	}

	private static double[] phases(RandomSource random, int count) {
		double[] phases = new double[count];
		for (int k = 0; k < count; k++) {
			phases[k] = random.nextDouble() * Mth.TWO_PI;
		}
		return phases;
	}

	private static double wobble(double angle, double[] phases, double ticks) {
		double sum = 0;
		for (int k = 0; k < WOBBLE_WAVES.length; k++) {
			sum += Math.sin(WOBBLE_WAVES[k] * angle + phases[k] + ticks * WOBBLE_DRIFT * (k + 1)) / (k + 1);
		}
		return WOBBLE * sum;
	}

	private static double brightness(double angle, double[] phases) {
		double dip = 1.0;
		for (int k = 0; k < BREAK_WAVES.length; k++) {
			dip *= 0.5 + 0.5 * Math.sin(BREAK_WAVES[k] * angle + phases[k]);
		}
		return 1 - BREAK_DEPTH * dip;
	}

	private static void rays(PoseStack.Pose pose, VertexConsumer buffer, Plane plane, double radius, double ticks, double opacity, RandomSource random) {
		int[] edge = LightStrokes.faded(RAY_LIGHT, 0.0);
		for (int ray = 0; ray < RAYS; ray++) {
			double angle = random.nextDouble() * Mth.TWO_PI + ticks * RAY_TURN * (ray % 2 == 0 ? 1 : -1);
			double length = radius * Mth.lerp(random.nextDouble(), RAY_SHORTEST, RAY_LONGEST);
			double pulse = opacity * (0.5 + 0.5 * Math.sin(ticks * RAY_PULSE + random.nextDouble() * Mth.TWO_PI));
			double side = radius * CORE_REACH * RAY_SIDE;
			quad(pose, buffer, plane, Vec3.ZERO, angle - RAY_WIDTH, angle, 0.0, 0.0, length, side, RAY_LIGHT, edge, pulse, pulse);
			quad(pose, buffer, plane, Vec3.ZERO, angle, angle + RAY_WIDTH, 0.0, 0.0, side, length, RAY_LIGHT, edge, pulse, pulse);
		}
	}

	private static void motes(PoseStack.Pose pose, VertexConsumer buffer, Plane plane, Vec3 toCamera, double radius, double ticks, double opacity,
		RandomSource random) {
		Vec3 view = toCamera.lengthSqr() == 0 ? plane.normal() : toCamera.normalize();
		Vec3 side = LightStrokes.perpendicular(view);
		Vec3 up = view.cross(side);
		double size = radius * MOTE_SIZE;
		for (int mote = 0; mote < MOTES; mote++) {
			double angle = random.nextDouble() * Mth.TWO_PI;
			double life = (ticks / MOTE_TICKS + random.nextDouble()) % 1.0;
			double fade = opacity * Math.sin(life * Math.PI);
			Vec3 at = plane.u().scale(Math.cos(angle)).add(plane.v().scale(Math.sin(angle))).scale(radius * MOTE_OUT * life)
				.add(plane.normal().scale(radius * MOTE_FORWARD * life));
			int[] colour = LightStrokes.faded(MOTE_LIGHT, fade);
			int[] clear = LightStrokes.faded(MOTE_LIGHT, 0.0);
			Vec3[] points = {at.add(side.scale(size)), at.add(up.scale(size)), at.subtract(side.scale(size)), at.subtract(up.scale(size))};
			for (int k = 0; k < points.length; k++) {
				Vec3 next = points[(k + 1) % points.length];
				LightStrokes.quad(pose, buffer, new Vec3[]{at, points[k], next, at}, new int[][]{colour, clear, clear, colour});
			}
		}
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer buffer, Plane plane, Vec3 centre, double a0, double a1, double in0, double in1, double out1,
		double out0, int[] inner, int[] outer, double share0, double share1) {
		double cos0 = Math.cos(a0);
		double sin0 = Math.sin(a0);
		double cos1 = Math.cos(a1);
		double sin1 = Math.sin(a1);
		LightStrokes.planeVertex(pose, buffer, centre, plane.u(), plane.v(), in0, cos0, sin0, inner, share0);
		LightStrokes.planeVertex(pose, buffer, centre, plane.u(), plane.v(), in1, cos1, sin1, inner, share1);
		LightStrokes.planeVertex(pose, buffer, centre, plane.u(), plane.v(), out1, cos1, sin1, outer, share1);
		LightStrokes.planeVertex(pose, buffer, centre, plane.u(), plane.v(), out0, cos0, sin0, outer, share0);
	}
}
