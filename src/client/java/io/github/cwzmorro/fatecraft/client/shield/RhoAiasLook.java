package io.github.cwzmorro.fatecraft.client.shield;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import io.github.cwzmorro.fatecraft.client.effect.LightTypes;
import java.util.Arrays;
import java.util.Random;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Rho Aias drawn in code: a shallow bowl of four or seven petals curved toward the attacks, summoned as a flare and rings,
 * then an orb the petals unfurl from. Coordinates are relative to the shield's centre.
 */
public final class RhoAiasLook {
	// Tuning numbers for the flower, in blocks, ticks or shares of its radius as the names say.
	private static final double FLARE_TICKS = 10.0;
	private static final double FLARE_RISE_TICKS = 2.0;
	private static final double RINGS_FROM = 2.0;
	private static final double RING_TICKS = 11.0;
	private static final double ORB_FROM = 6.0;
	private static final double ORB_FULL = 16.0;
	private static final double ORB_TO = 36.0;
	private static final double PETALS_FROM = 14.0;
	private static final double PETALS_SPREAD = 12.0;
	public static final double BLOOM_TICKS = 14.0;
	private static final double WIDTH_PER_PETAL = 0.08;
	private static final double WIDEST = 0.44;
	private static final double WIDEST_AT = 0.62;
	private static final double TIP_WIDTH = 0.7;
	private static final double TIP_ROUND = 0.12;
	private static final double NOTCH_DEPTH = 0.13;
	private static final double NOTCH_WIDTH = 0.3;
	private static final double NOTCH_POWER = 1.5;
	private static final double TIP_ROUND_POWER = 2.5;
	private static final double SWELL_POWER = 0.8;
	private static final double NARROW_POWER = 2;
	private static final double RAGGED = 0.025;
	private static final double RAGGED_WAVES = 9.0;
	private static final int RAGGED_EDGES = 7;
	private static final double BOWL = 0.18;
	private static final double LAYER = 0.02;
	private static final double FADE_IN = 0.25;
	private static final double FINGER_WIDTH = 0.18;
	private static final double WIDEN_FROM = 0.25;
	private static final double WIDEN_OVER = 0.6;
	private static final double TWIST = -1.1;
	private static final double TWIST_AT_BASE = 0.35;
	private static final double OPENING_WHITE = 0.8;
	private static final double OPENING_WHITE_POWER = 1.5;
	private static final int PETAL_SEGMENTS = 20;
	private static final double[] PETAL_ROWS = {0.0, 0.08, 0.2, 0.35, 0.5, 0.65, 0.8, 0.9, 0.96, 1.0};
	private static final double[] BODY_AT = {0.0, 0.35, 0.75, 0.93, 1.0};
	private static final int[][] BODY = {{255, 230, 248, 160}, {255, 140, 225, 150}, {225, 105, 235, 135}, {185, 100, 245, 110}, {170, 95, 245, 0}};
	private static final double[] LIGHT_AT = {0.0, 0.25, 0.6, 1.0};
	private static final int[][] LIGHT = {{255, 240, 240, 160}, {255, 160, 225, 70}, {240, 120, 230, 20}, {230, 110, 230, 0}};
	private static final double RIM_AT = 0.97;
	private static final double RIM_FADE = 0.3;
	private static final double RIM_HALF_WIDTH = 0.022;
	private static final int[] RIM = {255, 225, 250, 200};
	private static final double SHIMMER = 0.1;
	private static final double SHIMMER_PER_TICK = 0.12;
	private static final double CORE_REACH = 0.12;
	private static final double[] CORE_AT = {0.0, 0.3, 1.0};
	private static final int[][] CORE = {{255, 250, 240, 220}, {255, 190, 235, 130}, {255, 140, 220, 0}};
	private static final double EDGE_ON_GLOW = 0.2;
	private static final double HALO_REACH = 1.35;
	private static final double[] HALO_AT = {0.0, 0.5, 1.0};
	private static final int[][] HALO = {{255, 120, 225, 45}, {210, 100, 240, 20}, {200, 100, 240, 0}};
	private static final int SPECKS = 40;
	private static final double SPECK_REACH = 0.55;
	private static final double SPECK_SIZE = 0.025;
	private static final double SPECK_TURN_PER_TICK = 0.01;
	private static final double SPECK_TWINKLE_PER_TICK = 0.35;
	private static final int[][] SPECK = {{255, 245, 255, 230}, {255, 245, 255, 92}, {255, 245, 255, 0}};
	private static final double FLARE_ACROSS = 2.8;
	private static final double FLARE_START = 0.5;
	private static final double FLARE_UP = 1.4;
	private static final double FLARE_HALF_WIDTH = 0.03;
	private static final double FLARE_GLOW_HALF_WIDTH = 0.14;
	private static final int[] FLARE = {255, 165, 230, 255};
	private static final int[] FLARE_GLOW = {255, 70, 195, 170};
	private static final int FLARE_POINTS = 9;
	private static final double[][] RINGS = {{0.0, 0.0, 0.64, 0.0}, {-0.36, 0.28, 0.24, 1.5}, {0.44, -0.2, 0.18, 2.5}, {0.16, 0.48, 0.12, 3.5},
		{-0.52, -0.36, 0.32, 4.5}};
	private static final double RING_GROWTH = 0.25;
	private static final double RING_HALF_WIDTH = 0.03;
	private static final int[] RING = {255, 238, 255, 220};
	private static final double[][] ORBS = {{-0.5, 0.35}, {0.55, -0.3}};
	private static final double ORB_SIZE = 0.07;
	private static final double ORB_DRIFT = 0.25;
	private static final int[][] VIOLET_ORB = {{235, 215, 255, 235}, {175, 120, 255, 190}, {150, 90, 255, 0}};
	private static final double ORB_REACH = 0.22;
	private static final int[][] ORB = {{255, 252, 250, 255}, {255, 160, 225, 210}, {240, 90, 210, 0}};
	private static final double[] ORB_AT = {0.0, 0.55, 1.0};
	private static final int CRACKLES = 5;
	private static final double CRACKLE_REACH = 0.8;
	private static final int CRACKLE_TICKS = 2;
	private static final double CRACKLE_HALF_WIDTH = 0.012;
	private static final int[] CRACKLE = {255, 255, 255, 230};
	public static final double HIT_TICKS = 16.0;
	private static final double STAR_TICKS = 8.0;
	private static final double STAR_REACH = 0.9;
	private static final double STAR_LEAST = 0.35;
	private static final double STAR_HALF_WIDTH = 0.025;
	private static final double STAR_GLOW_REACH = 0.35;
	private static final int[] STAR = {255, 230, 250, 255};
	private static final int STREAKS = 10;
	private static final double STREAK_TICKS = 9.0;
	private static final double STREAK_REACH = 2.4;
	private static final double STREAK_LENGTH = 0.7;
	private static final double STREAK_HALF_WIDTH = 0.015;
	private static final double STREAK_SPREAD = 0.9;
	private static final int[] STREAK = {255, 170, 235, 230};
	private static final int SPLASHES = 8;
	private static final double SPLASH_REACH = 1.6;
	private static final double SPLASH_SIZE = 0.08;
	private static final double SPLASH_FORWARD = 0.5;
	private static final int[][] SPLASH = {{255, 130, 220, 220}, {255, 130, 220, 110}, {255, 130, 220, 0}};
	private static final int RIPPLES = 3;
	private static final double RIPPLE_STAGGER = 3.0;
	private static final double RIPPLE_REACH = 2.2;
	private static final double RIPPLE_HALF_WIDTH = 0.05;
	private static final double RIPPLE_EDGE = 0.1;
	private static final int[] RIPPLE = {255, 210, 245, 200};
	public static final double SHATTER_TICKS = 15.0;
	private static final int SHARDS_ACROSS = 4;
	private static final int SHARDS_ALONG = 3;
	private static final double SHARD_REACH = 1.8;
	private static final double SHARD_FORWARD = 0.5;
	private static final double SHARD_SPIN = 0.5;
	private static final double SHARD_LIGHT = 0.6;
	private static final int SEGMENTS_PER_TURN = 48;

	private static final Random LAYOUT = new Random(RhoAiasLook.class.getName().hashCode());
	private static final double[] RAGGED_PHASES = phases(RAGGED_EDGES);
	private static final double[][] SPECK_PLACES = places(SPECKS);
	private static final double[] SPECK_PHASES = phases(SPECKS);
	private static final double[][] STREAK_PLACES = places(STREAKS);
	private static final double[][] SPLASH_PLACES = places(SPLASHES);
	private static final double[][] SHARD_SPINS = places(SHARDS_ACROSS * SHARDS_ALONG);

	private record Plane(Vec3 facing, Vec3 across, Vec3 up) {
		static Plane of(Vec3 facing) {
			Vec3 across = LightStrokes.perpendicular(facing);
			return new Plane(facing, across, across.cross(facing));
		}

		Vec3 at(double x, double y, double back) {
			return this.across.scale(x).add(this.up.scale(y)).subtract(this.facing.scale(back));
		}
	}

	private RhoAiasLook() {
	}

	public static double summonBloom(int k, int petals, double ticks) {
		return Mth.clamp((ticks - PETALS_FROM - PETALS_SPREAD * k / petals) / BLOOM_TICKS, 0.0, 1.0);
	}

	public static void flower(PoseStack.Pose pose, VertexConsumer buffer, RenderType type, Vec3 camera, Vec3 facing, double radius, double[] bloom,
		double ticks) {
		Plane plane = Plane.of(facing);
		boolean under = type == LightTypes.BLENDED;
		double opened = 0.0;
		for (double shown : bloom) {
			opened = Math.max(opened, shown);
		}
		// The centre's glow faces the camera; seen edge-on it all but goes.
		double faceOn = camera.lengthSqr() == 0 ? 1.0 : Math.abs(camera.normalize().dot(facing));
		double glowShare = EDGE_ON_GLOW + (1 - EDGE_ON_GLOW) * faceOn;
		if (!under && opened > 0) {
			glow(pose, buffer, camera, Vec3.ZERO, radius * HALO_REACH * opened, HALO_AT, HALO, glowShare);
		}
		for (int k = 0; k < bloom.length; k++) {
			if (bloom[k] <= 0) {
				continue;
			}
			double breath = 1 - SHIMMER + SHIMMER * Math.sin(ticks * SHIMMER_PER_TICK + k);
			petal(pose, buffer, plane, camera, radius, bloom.length, k, bloom[k], under, under ? 1.0 : breath);
		}
		if (!under && opened > 0) {
			glow(pose, buffer, camera, Vec3.ZERO, radius * CORE_REACH, CORE_AT, CORE, opened * glowShare);
			specks(pose, buffer, camera, plane, radius, ticks, opened);
		}
	}

	public static void summon(PoseStack.Pose pose, VertexConsumer buffer, RenderType type, Vec3 camera, Vec3 facing, double radius, double ticks) {
		if (type != LightTypes.ADDED || ticks > ORB_TO) {
			return;
		}
		Plane plane = Plane.of(facing);
		double flare = Math.min(ticks / FLARE_RISE_TICKS, 1.0) * Math.max(0.0, 1 - ticks / FLARE_TICKS);
		if (flare > 0) {
			double reach = FLARE_START + (1 - FLARE_START) * Math.min(ticks / FLARE_RISE_TICKS, 1.0);
			flareLine(pose, buffer, camera, plane.across, radius * FLARE_ACROSS * reach, flare);
			flareLine(pose, buffer, camera, plane.up, radius * FLARE_UP * reach, flare);
		}
		for (double[] ring : RINGS) {
			double run = (ticks - RINGS_FROM - ring[3]) / RING_TICKS;
			if (run > 0 && run < 1) {
				Vec3 centre = plane.at(ring[0] * radius, ring[1] * radius, 0.0);
				double at = ring[2] * radius * (1 + RING_GROWTH * run);
				ring(pose, buffer, plane, centre, at, RING_HALF_WIDTH, LightStrokes.faded(RING, Math.sin(Math.PI * run)), Double.POSITIVE_INFINITY);
			}
		}
		double drift = (ticks - RINGS_FROM) / RING_TICKS;
		if (drift > 0 && drift < 1) {
			for (double[] orb : ORBS) {
				Vec3 at = plane.at(orb[0] * radius * (1 + ORB_DRIFT * drift), orb[1] * radius * (1 + ORB_DRIFT * drift), 0.0);
				glow(pose, buffer, camera, at, radius * ORB_SIZE, ORB_AT, VIOLET_ORB, Math.sin(Math.PI * drift));
			}
		}
		double swell = Mth.clamp((ticks - ORB_FROM) / (ORB_FULL - ORB_FROM), 0.0, 1.0);
		double shrink = Mth.clamp((ORB_TO - ticks) / (ORB_TO - ORB_FULL), 0.0, 1.0);
		double orb = swell * shrink;
		if (orb > 0) {
			glow(pose, buffer, camera, Vec3.ZERO, radius * ORB_REACH * swell, ORB_AT, ORB, orb);
			crackles(pose, buffer, camera, plane, radius * ORB_REACH * swell, ticks, orb);
		}
	}

	public static void hit(PoseStack.Pose pose, VertexConsumer buffer, RenderType type, Vec3 camera, Vec3 facing, double radius, Vec3 point, double age) {
		if (type != LightTypes.ADDED || age >= HIT_TICKS) {
			return;
		}
		Plane plane = Plane.of(facing);
		double star = 1 - age / STAR_TICKS;
		if (star > 0) {
			double reach = STAR_REACH * (STAR_LEAST + (1 - STAR_LEAST) * star);
			for (int arm = 0; arm < 2; arm++) {
				double angle = Math.PI / 4 + arm * Math.PI / 2;
				Vec3 along = plane.across.scale(Math.cos(angle)).add(plane.up.scale(Math.sin(angle)));
				taperedLine(pose, buffer, camera, point.subtract(along.scale(reach)), point.add(along.scale(reach)), STAR_HALF_WIDTH,
					LightStrokes.faded(STAR, star));
			}
			glow(pose, buffer, camera, point, STAR_GLOW_REACH, CORE_AT, CORE, star);
		}
		double burst = age / STREAK_TICKS;
		if (burst < 1) {
			double out = 1 - (1 - burst) * (1 - burst);
			for (double[] place : STREAK_PLACES) {
				Vec3 way = facing.add(plane.at(place[0] * STREAK_SPREAD, place[1] * STREAK_SPREAD, 0.0)).normalize();
				Vec3 tip = point.add(way.scale(STREAK_REACH * out + STREAK_LENGTH));
				taperedLine(pose, buffer, camera, point.add(way.scale(STREAK_REACH * out)), tip, STREAK_HALF_WIDTH,
					LightStrokes.faded(STREAK, 1 - burst));
			}
		}
		double fly = age / HIT_TICKS;
		double flown = 1 - (1 - fly) * (1 - fly);
		for (double[] place : SPLASH_PLACES) {
			Vec3 way = facing.scale(SPLASH_FORWARD).add(plane.at(place[0], place[1], 0.0)).normalize();
			Vec3 at = point.add(way.scale(SPLASH_REACH * flown));
			glow(pose, buffer, camera, at, SPLASH_SIZE * (1 - fly), ORB_AT, SPLASH, 1 - fly);
		}
		for (int k = 0; k < RIPPLES; k++) {
			double run = (age - k * RIPPLE_STAGGER) / (HIT_TICKS - (RIPPLES - 1) * RIPPLE_STAGGER);
			if (run > 0 && run < 1) {
				ring(pose, buffer, plane, point, RIPPLE_REACH * run, RIPPLE_HALF_WIDTH, LightStrokes.faded(RIPPLE, (1 - run) * (1 - run)), radius);
			}
		}
	}

	public static void shatter(PoseStack.Pose pose, VertexConsumer buffer, RenderType type, Vec3 facing, double radius, int petals, int k, double age) {
		if (age >= SHATTER_TICKS) {
			return;
		}
		Plane plane = Plane.of(facing);
		boolean under = type == LightTypes.BLENDED;
		double run = age / SHATTER_TICKS;
		double out = 1 - (1 - run) * (1 - run);
		for (int i = 0; i < SHARDS_ACROSS; i++) {
			for (int j = 0; j < SHARDS_ALONG; j++) {
				double side0 = -1 + 2.0 * i / SHARDS_ACROSS;
				double side1 = -1 + 2.0 * (i + 1) / SHARDS_ACROSS;
				double along0 = (double) j / SHARDS_ALONG;
				double along1 = (double) (j + 1) / SHARDS_ALONG;
				Vec3[] corners = {petalPoint(plane, radius, petals, k, side0, along0), petalPoint(plane, radius, petals, k, side1, along0),
					petalPoint(plane, radius, petals, k, side1, along1), petalPoint(plane, radius, petals, k, side0, along1)};
				Vec3 middle = corners[0].add(corners[1]).add(corners[2]).add(corners[3]).scale(0.25);
				double[] spin = SHARD_SPINS[i * SHARDS_ALONG + j];
				Vec3 way = middle.lengthSqr() == 0 ? facing : middle.normalize().add(facing.scale(spin[0] * SHARD_FORWARD));
				Vec3 axis = new Vec3(spin[0], spin[1], 1.0).normalize();
				double angle = SHARD_SPIN * age * (spin[1] < 0 ? -1 : 1);
				Vec3 moved = way.scale(SHARD_REACH * out);
				for (int c = 0; c < corners.length; c++) {
					corners[c] = middle.add(turn(corners[c].subtract(middle), axis, angle)).add(moved);
				}
				double along = (along0 + along1) / 2;
				int[] colour = under ? colourAt(BODY_AT, BODY, along) : colourAt(LIGHT_AT, LIGHT, along * SHARD_LIGHT);
				int[] shown = LightStrokes.faded(colour, 1 - run);
				LightStrokes.quad(pose, buffer, corners, new int[][]{shown, shown, shown, shown});
			}
		}
	}

	public static Vec3 petalMiddle(Vec3 facing, double radius, int petals, int k) {
		return petalPoint(Plane.of(facing), radius, petals, k, 0.0, 0.5);
	}

	private static void petal(PoseStack.Pose pose, VertexConsumer buffer, Plane plane, Vec3 camera, double radius, int petals, int k, double bloom,
		boolean under, double bright) {
		double grow = 1 - (1 - bloom) * (1 - bloom);
		double widen = FINGER_WIDTH + (1 - FINGER_WIDTH) * smooth((bloom - WIDEN_FROM) / WIDEN_OVER);
		double unturned = (1 - bloom) * (1 - bloom);
		double white = OPENING_WHITE * Math.pow(1 - bloom, OPENING_WHITE_POWER);
		double fadeIn = Math.min(1.0, bloom / FADE_IN);
		Vec3[][] grid = new Vec3[PETAL_SEGMENTS + 1][PETAL_ROWS.length];
		for (int a = 0; a <= PETAL_SEGMENTS; a++) {
			double side = -1 + 2.0 * a / PETAL_SEGMENTS;
			for (int r = 0; r < PETAL_ROWS.length; r++) {
				grid[a][r] = petalPoint(plane, radius * grow, petals, k, side, PETAL_ROWS[r], twist(unturned, PETAL_ROWS[r]), widen);
			}
		}
		double[] at = under ? BODY_AT : LIGHT_AT;
		int[][] colours = under ? BODY : LIGHT;
		int[][] row = new int[PETAL_ROWS.length][];
		for (int r = 0; r < PETAL_ROWS.length; r++) {
			row[r] = LightStrokes.faded(whitened(colourAt(at, colours, PETAL_ROWS[r]), white), fadeIn * bright);
		}
		for (int a = 1; a <= PETAL_SEGMENTS; a++) {
			for (int r = 1; r < PETAL_ROWS.length; r++) {
				LightStrokes.quad(pose, buffer, new Vec3[]{grid[a - 1][r - 1], grid[a][r - 1], grid[a][r], grid[a - 1][r]},
					new int[][]{row[r - 1], row[r - 1], row[r], row[r]});
			}
		}
		if (!under) {
			int sideRows = PETAL_ROWS.length - 1;
			Vec3[] rim = new Vec3[2 * sideRows + PETAL_SEGMENTS - 1];
			double[] widths = new double[rim.length];
			double[] shares = new double[rim.length];
			int i = 0;
			for (int r = 1; r < PETAL_ROWS.length; r++, i++) {
				rim[i] = petalPoint(plane, radius * grow, petals, k, -RIM_AT, PETAL_ROWS[r] * RIM_AT, twist(unturned, PETAL_ROWS[r]), widen);
				shares[i] = smooth(PETAL_ROWS[r] / RIM_FADE);
			}
			for (int a = 1; a < PETAL_SEGMENTS; a++, i++) {
				rim[i] = petalPoint(plane, radius * grow, petals, k, (-1 + 2.0 * a / PETAL_SEGMENTS) * RIM_AT, RIM_AT, twist(unturned, 1.0), widen);
				shares[i] = 1.0;
			}
			for (int r = PETAL_ROWS.length - 1; r >= 1; r--, i++) {
				rim[i] = petalPoint(plane, radius * grow, petals, k, RIM_AT, PETAL_ROWS[r] * RIM_AT, twist(unturned, PETAL_ROWS[r]), widen);
				shares[i] = smooth(PETAL_ROWS[r] / RIM_FADE);
			}
			Arrays.fill(widths, RIM_HALF_WIDTH);
			LightStrokes.strip(pose, buffer, camera, rim, widths, LightStrokes.faded(whitened(RIM, white), fadeIn * bright), shares);
		}
	}

	private static double twist(double unturned, double along) {
		return TWIST * unturned * (TWIST_AT_BASE + (1 - TWIST_AT_BASE) * along);
	}

	private static Vec3 petalPoint(Plane plane, double radius, int petals, int k, double side, double along) {
		return petalPoint(plane, radius, petals, k, side, along, 0.0, 1.0);
	}

	private static Vec3 petalPoint(Plane plane, double radius, int petals, int k, double side, double along, double twist, double widen) {
		double phase = RAGGED_PHASES[k % RAGGED_PHASES.length];
		double across = Math.min(1.0, Math.abs(side));
		double notch = NOTCH_DEPTH * smooth((widen - FINGER_WIDTH) / (1 - FINGER_WIDTH)) * Math.pow(Math.max(0.0, 1 - across / NOTCH_WIDTH), NOTCH_POWER);
		double tip = 1 - TIP_ROUND * Math.pow(across, TIP_ROUND_POWER) - notch + RAGGED * Math.sin(side * RAGGED_WAVES + phase);
		double out = along * tip;
		double wide = out < WIDEST_AT ? Math.pow(Math.sin(Math.PI / 2 * out / WIDEST_AT), SWELL_POWER)
			: 1 - (1 - TIP_WIDTH) * Math.pow((out - WIDEST_AT) / (1 - WIDEST_AT), NARROW_POWER);
		double halfWidth = Math.min(WIDEST, WIDTH_PER_PETAL * petals) * wide * widen * (1 + RAGGED * Math.sin(out * RAGGED_WAVES + phase));
		// Seven petals start at the top; four stand as an X.
		double middle = Math.PI / 2 + Mth.TWO_PI * k / petals + (petals % 2 == 0 ? Math.PI / petals : 0.0) + twist;
		double x = out * radius;
		double y = side * halfWidth * radius;
		double cos = Math.cos(middle);
		double sin = Math.sin(middle);
		double reach = Math.sqrt(x * x + y * y) / radius;
		return plane.at(cos * x - sin * y, sin * x + cos * y, BOWL * radius * reach * reach + LAYER * radius * k);
	}

	private static void ring(PoseStack.Pose pose, VertexConsumer buffer, Plane plane, Vec3 centre, double at, double halfWidth, int[] rgba,
		double within) {
		double[] radii = {Math.max(0.0, at - halfWidth), at, at + halfWidth};
		double[] alpha = {0.0, 1.0, 0.0};
		for (int s = 0; s < SEGMENTS_PER_TURN; s++) {
			double a0 = Mth.TWO_PI * s / SEGMENTS_PER_TURN;
			double a1 = Mth.TWO_PI * (s + 1) / SEGMENTS_PER_TURN;
			for (int k = 1; k < radii.length; k++) {
				Vec3[] corners = {ringPoint(plane, centre, radii[k - 1], a0), ringPoint(plane, centre, radii[k - 1], a1), ringPoint(plane, centre, radii[k], a1),
					ringPoint(plane, centre, radii[k], a0)};
				double[] shares = {alpha[k - 1], alpha[k - 1], alpha[k], alpha[k]};
				int[][] colours = new int[4][];
				for (int c = 0; c < corners.length; c++) {
					double clip = Double.isInfinite(within) ? 1.0 : Mth.clamp((within - corners[c].length()) / (within * RIPPLE_EDGE), 0.0, 1.0);
					colours[c] = LightStrokes.faded(rgba, shares[c] * clip);
				}
				LightStrokes.quad(pose, buffer, corners, colours);
			}
		}
	}

	private static Vec3 ringPoint(Plane plane, Vec3 centre, double radius, double angle) {
		return centre.add(plane.at(Math.cos(angle) * radius, Math.sin(angle) * radius, 0.0));
	}

	private static void glow(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, Vec3 centre, double reach, double[] at, int[][] rgba, double share) {
		if (reach <= 0 || share <= 0) {
			return;
		}
		Vec3 view = centre.subtract(camera);
		Vec3 normal = view.lengthSqr() == 0 ? new Vec3(0, 0, 1) : view.normalize();
		Vec3 u = LightStrokes.perpendicular(normal);
		Vec3 v = normal.cross(u);
		for (int s = 0; s < SEGMENTS_PER_TURN; s++) {
			double a0 = Mth.TWO_PI * s / SEGMENTS_PER_TURN;
			double a1 = Mth.TWO_PI * (s + 1) / SEGMENTS_PER_TURN;
			for (int k = 1; k < at.length; k++) {
				LightStrokes.planeVertex(pose, buffer, centre, u, v, reach * at[k - 1], Math.cos(a0), Math.sin(a0), rgba[k - 1], share);
				LightStrokes.planeVertex(pose, buffer, centre, u, v, reach * at[k - 1], Math.cos(a1), Math.sin(a1), rgba[k - 1], share);
				LightStrokes.planeVertex(pose, buffer, centre, u, v, reach * at[k], Math.cos(a1), Math.sin(a1), rgba[k], share);
				LightStrokes.planeVertex(pose, buffer, centre, u, v, reach * at[k], Math.cos(a0), Math.sin(a0), rgba[k], share);
			}
		}
	}

	private static void flareLine(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, Vec3 along, double reach, double share) {
		Vec3[] points = new Vec3[FLARE_POINTS];
		double[] narrow = new double[FLARE_POINTS];
		double[] wide = new double[FLARE_POINTS];
		double[] shares = new double[FLARE_POINTS];
		for (int i = 0; i < FLARE_POINTS; i++) {
			double t = -1 + 2.0 * i / (FLARE_POINTS - 1);
			points[i] = along.scale(t * reach);
			narrow[i] = FLARE_HALF_WIDTH;
			wide[i] = FLARE_GLOW_HALF_WIDTH * (1 - Math.abs(t));
			shares[i] = (1 - Math.abs(t)) * (1 - Math.abs(t)) * share;
		}
		LightStrokes.strip(pose, buffer, camera, points, wide, FLARE_GLOW, shares);
		LightStrokes.strip(pose, buffer, camera, points, narrow, FLARE, shares);
	}

	private static void taperedLine(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, Vec3 a, Vec3 b, double halfWidth, int[] rgba) {
		Vec3 middle = a.add(b).scale(0.5);
		LightStrokes.strip(pose, buffer, camera, new Vec3[]{a, middle, b}, new double[]{halfWidth, halfWidth, halfWidth}, rgba, new double[]{0.0, 1.0, 0.0});
	}

	private static void crackles(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, Plane plane, double reach, double ticks, double share) {
		Random random = new Random((long) (ticks / CRACKLE_TICKS));
		for (int i = 0; i < CRACKLES; i++) {
			double a = random.nextDouble() * Mth.TWO_PI;
			double b = a + Math.PI * (0.5 + random.nextDouble());
			double across = reach * CRACKLE_REACH;
			Vec3 from = plane.at(Math.cos(a) * across, Math.sin(a) * across, 0.0);
			Vec3 bend = plane.at((random.nextDouble() - 0.5) * reach, (random.nextDouble() - 0.5) * reach, 0.0);
			Vec3 to = plane.at(Math.cos(b) * across, Math.sin(b) * across, 0.0);
			LightStrokes.strip(pose, buffer, camera, new Vec3[]{from, bend, to}, new double[]{CRACKLE_HALF_WIDTH, CRACKLE_HALF_WIDTH, CRACKLE_HALF_WIDTH},
				LightStrokes.faded(CRACKLE, share), new double[]{0.0, 1.0, 0.0});
		}
	}

	private static void specks(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, Plane plane, double radius, double ticks, double share) {
		for (int i = 0; i < SPECKS; i++) {
			double[] place = SPECK_PLACES[i];
			double turn = ticks * SPECK_TURN_PER_TICK * place[0];
			double x = place[0] * Math.cos(turn) - place[1] * Math.sin(turn);
			double y = place[0] * Math.sin(turn) + place[1] * Math.cos(turn);
			Vec3 at = plane.at(x * radius * SPECK_REACH, y * radius * SPECK_REACH, -LAYER * radius);
			double twinkle = 0.5 + 0.5 * Math.sin(ticks * SPECK_TWINKLE_PER_TICK + SPECK_PHASES[i]);
			glow(pose, buffer, camera, at, SPECK_SIZE, ORB_AT, SPECK, share * twinkle);
		}
	}

	private static Vec3 turn(Vec3 vector, Vec3 axis, double angle) {
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		return vector.scale(cos).add(axis.cross(vector).scale(sin)).add(axis.scale(axis.dot(vector) * (1 - cos)));
	}

	private static int[] colourAt(double[] at, int[][] rgba, double t) {
		for (int k = 1; k < at.length; k++) {
			if (t <= at[k]) {
				double f = (t - at[k - 1]) / (at[k] - at[k - 1]);
				int[] a = rgba[k - 1];
				int[] b = rgba[k];
				return new int[]{(int) Mth.lerp(f, a[0], b[0]), (int) Mth.lerp(f, a[1], b[1]), (int) Mth.lerp(f, a[2], b[2]), (int) Mth.lerp(f, a[3], b[3])};
			}
		}
		return rgba[rgba.length - 1];
	}

	private static int[] whitened(int[] rgba, double share) {
		return new int[]{(int) Mth.lerp(share, rgba[0], 255), (int) Mth.lerp(share, rgba[1], 255), (int) Mth.lerp(share, rgba[2], 255), rgba[3]};
	}

	private static double smooth(double t) {
		double x = Mth.clamp(t, 0.0, 1.0);
		return x * x * (3 - 2 * x);
	}

	private static double[] phases(int count) {
		double[] phases = new double[count];
		for (int i = 0; i < count; i++) {
			phases[i] = LAYOUT.nextDouble() * Mth.TWO_PI;
		}
		return phases;
	}

	private static double[][] places(int count) {
		double[][] places = new double[count][];
		for (int i = 0; i < count; i++) {
			double angle = LAYOUT.nextDouble() * Mth.TWO_PI;
			double out = Math.sqrt(LAYOUT.nextDouble());
			places[i] = new double[]{Math.cos(angle) * out, Math.sin(angle) * out};
		}
		return places;
	}
}
