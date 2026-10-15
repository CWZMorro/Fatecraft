package io.github.cwzmorro.fatecraft.client.ubw;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.cwzmorro.fatecraft.GoldenRatio;
import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import java.util.Arrays;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * EMIYA's mana gathering before the ring of fire ignites: a glowing pool at his feet with wisps and faint lightning, a
 * cyan-white surge in the last ticks and a flash for cameras inside the sphere. Ages are the opening's (negative while
 * the mana gathers); coordinates are relative to the sphere's centre.
 */
final class UbwMana {
	// Tuning numbers for the mana effect: radii and heights in blocks, speeds in radians per tick, ticks for the timings.
	private static final int[] POOL_HEART = {225, 250, 255, 255};
	private static final int[] POOL_BODY = {60, 190, 255, 255};
	private static final int[] POOL_HALO = {40, 120, 255, 255};
	private static final double HEART_RADIUS = 1.0;
	private static final double BODY_RADIUS = 1.9;
	private static final double HALO_RADIUS = 3.4;
	private static final double HEART_ALPHA = 1.0;
	private static final double BODY_ALPHA = 0.7;
	private static final double BODY_GLOW_ALPHA = 0.8;
	private static final double HALO_ALPHA = 0.35;
	private static final double PLATEAU = 0.45;
	private static final double POOL_START = 0.35;
	private static final int DISC_SEGMENTS = 96;
	private static final double FLOOR_LIFT = 0.06;
	private static final int[] RIM_WAVES = {5, 9, 17};
	private static final double[] RIM_WAVE_DEPTH = {0.08, 0.06, 0.04};
	private static final double[] RIM_WAVE_SPEED = {0.25, -0.45, 0.7};
	private static final int WISPS = 34;
	private static final double WISP_LIFE = 14.0;
	private static final double WISP_RISE = 0.35;
	private static final double WISP_LENGTH = 0.45;
	private static final double WISP_HALF_WIDTH = 0.08;
	private static final double WISP_OUT = 0.25;
	private static final double WISP_SWAY = 0.08;
	private static final double WISP_SWAY_WAVES = 1.5;
	private static final double WISP_SWAY_SPEED = 0.4;
	private static final int[] WISP_LIGHT = {90, 180, 255, 255};
	private static final int[] WISP_SMOKE = {70, 140, 235, 255};
	private static final double WISP_LIGHT_ALPHA = 0.6;
	private static final double WISP_SMOKE_ALPHA = 0.3;
	private static final double[] WISP_ALONG = {0.0, 0.25, 0.5, 0.75, 1.0};
	private static final int SPECKS = 30;
	private static final double SPECK_RISE = 1.8;
	private static final double SPECK_LIFE = 26.0;
	private static final double SPECK_LENGTH = 0.08;
	private static final double SPECK_HALF_WIDTH = 0.03;
	private static final int[] SPECK = {210, 245, 255, 255};
	private static final int BOLT_LANES = 12;
	private static final int BOLT_PERIOD_LEAST = 7;
	private static final int BOLT_PERIOD_SPREAD = 8;
	private static final int BOLT_LIFE = 3;
	private static final double FLICKER_LEAST = 0.3;
	private static final double BOLT_NEAREST = 9.0;
	private static final double BOLT_FARTHEST = 20.0;
	private static final double BOLT_TOP_LEAST = 6.0;
	private static final double BOLT_TOP_SPREAD = 6.0;
	private static final double BOLT_FOOT_HIGHEST = 3.0;
	private static final double BOLT_DRIFT = 5.0;
	private static final int BOLT_POINTS = 32;
	private static final double[] BOLT_WAVES = {1.3, 3.1};
	private static final double[] BOLT_WAVE_DEPTH = {0.05, 0.025};
	private static final double BOLT_JITTER = 0.008;
	private static final int BRANCHES = 3;
	private static final double BRANCH_FROM = 0.15;
	private static final double BRANCH_SPREAD = 0.6;
	private static final double BRANCH_LEAST = 0.1;
	private static final double BRANCH_LONGER = 0.15;
	private static final double BRANCH_LEAN = 0.7;
	private static final int BRANCH_POINTS = 10;
	private static final double BRANCH_ALPHA = 0.55;
	private static final double BOLT_CORE_HALF_WIDTH = 0.03;
	private static final double BOLT_GLOW_HALF_WIDTH = 0.2;
	private static final int[] BOLT_CORE = {185, 220, 255, 255};
	private static final int[] BOLT_GLOW = {60, 130, 255, 255};
	private static final double BOLT_CORE_ALPHA = 0.85;
	private static final double BOLT_GLOW_ALPHA = 0.3;
	private static final int SURGE_TICKS = 8;
	private static final double SURGE_RADIUS = 14.0;
	private static final int PUFFS = 150;
	private static final double PUFF_LEAST = 0.5;
	private static final double PUFF_LARGER = 1.2;
	private static final double PUFF_LOWEST = 0.2;
	private static final double PUFF_RISE = 2.2;
	private static final double PUFF_GROWN = 0.5;
	private static final int[] PUFF_PALE = {200, 240, 255, 255};
	private static final int[] PUFF_SHADE = {30, 120, 210, 255};
	private static final int[] PUFF_GLOW = {150, 220, 255, 255};
	private static final double PUFF_ALPHA = 0.4;
	private static final double SHADE_ALPHA = 0.5;
	private static final double PUFF_GLOW_ALPHA = 0.18;
	private static final double SHADE_OFFSET = 0.35;
	private static final double SHADE_SIZE = 0.7;
	private static final double PUFF_OUTWARD = 0.6;
	private static final double PUFF_NEAREST = 0.8;
	private static final int PUFF_SEGMENTS = 16;
	private static final int FLUNG = 40;
	private static final double FLUNG_HIGHEST = 1.8;
	private static final double FLUNG_LENGTH = 0.18;
	private static final double FLUNG_HALF_WIDTH = 0.035;
	private static final int[] GRIT = {35, 45, 60, 255};
	private static final double GRIT_ALPHA = 0.8;
	private static final double FADE_TICKS = 6.0;
	private static final double FLASH_RISE_TICKS = 3.0;
	private static final double FLASH_FADE_TICKS = 8.0;
	private static final int[] FLASH = {240, 246, 255, 255};
	private static final double FLASH_REACH = 0.3;
	private static final int ANGLE_SALT = 21;
	private static final int OUT_SALT = 22;
	private static final int PHASE_SALT = 23;
	private static final int HEIGHT_SALT = 24;
	private static final int SHOW_SALT = 25;
	private static final int END_SALT = 26;
	private static final int RAGGED_SALT = 27;
	private static final int PERIOD_SALT = 28;
	private static final int FLICKER_SALT = 29;
	private static final int SIZE_SALT = 30;
	private static final double MIDDLE = 0.5;
	private static final int STRIKE_LANES = 1000;
	private static final int QUAD_CORNERS = 4;
	private static final int BOX_AXES = 3;

	record Charge(Vec3 eye, Vec3 feet, double age, int chargeTicks) { }

	private UbwMana() { }

	static boolean shows(double age, int chargeTicks) {
		return age >= -chargeTicks && age < Math.max(FADE_TICKS, FLASH_FADE_TICKS);
	}

	private static double strength(double age, int chargeTicks) {
		if (age >= 0) {
			return 1 - Mth.clamp(age / FADE_TICKS, 0, 1);
		}
		return Mth.smoothstep(Mth.clamp((age + chargeTicks) / chargeTicks, 0, 1));
	}

	private static double surge(double age) {
		return Mth.smoothstep(Mth.clamp((age + SURGE_TICKS) / SURGE_TICKS, 0, 1));
	}

	private static double grown(double age, int chargeTicks) {
		return Mth.lerp(strength(Math.min(age, 0), chargeTicks), POOL_START, 1);
	}

	static double flash(double age) {
		if (age < 0) {
			return Mth.smoothstep(Mth.clamp((age + FLASH_RISE_TICKS) / FLASH_RISE_TICKS, 0, 1));
		}
		return 1 - Mth.smoothstep(Mth.clamp(age / FLASH_FADE_TICKS, 0, 1));
	}

	static void body(PoseStack.Pose pose, VertexConsumer buffer, Charge charge) {
		double strength = strength(charge.age(), charge.chargeTicks());
		if (strength <= 0) {
			return;
		}
		double surge = surge(charge.age());
		pool(pose, buffer, charge, BODY_RADIUS * grown(charge.age(), charge.chargeTicks()), POOL_BODY, BODY_ALPHA * strength);
		for (int wisp = 0; wisp < WISPS; wisp++) {
			wisp(pose, buffer, charge, wisp, WISP_SMOKE, WISP_SMOKE_ALPHA * strength);
		}
		if (surge <= 0) {
			return;
		}
		for (int puff = 0; puff < PUFFS; puff++) {
			puff(pose, buffer, charge, puff, surge, strength, false);
		}
		for (int bit = 0; bit < FLUNG; bit++) {
			flung(pose, buffer, charge, bit, surge, GRIT, GRIT_ALPHA * strength);
		}
	}

	static void glow(PoseStack.Pose pose, VertexConsumer buffer, Charge charge) {
		double strength = strength(charge.age(), charge.chargeTicks());
		if (strength <= 0) {
			return;
		}
		double age = charge.age();
		double grown = grown(age, charge.chargeTicks());
		double surge = surge(age);
		pool(pose, buffer, charge, HALO_RADIUS * grown, POOL_HALO, HALO_ALPHA * strength);
		pool(pose, buffer, charge, BODY_RADIUS * grown, POOL_BODY, BODY_GLOW_ALPHA * strength);
		pool(pose, buffer, charge, HEART_RADIUS * grown, POOL_HEART, HEART_ALPHA * strength);
		for (int wisp = 0; wisp < WISPS; wisp++) {
			wisp(pose, buffer, charge, wisp, WISP_LIGHT, WISP_LIGHT_ALPHA * strength);
		}
		for (int speck = 0; speck < SPECKS; speck++) {
			speck(pose, buffer, charge, speck, strength);
		}
		if (surge > 0) {
			for (int puff = 0; puff < PUFFS; puff++) {
				puff(pose, buffer, charge, puff, surge, strength, true);
			}
			// Lanes past the grit's, so the bright specks fly apart from it.
			for (int bit = 0; bit < FLUNG; bit++) {
				flung(pose, buffer, charge, bit + FLUNG, surge, SPECK, strength);
			}
		}
		for (int lane = 0; lane < BOLT_LANES; lane++) {
			bolt(pose, buffer, charge, lane, strength);
		}
	}

	static void flash(PoseStack.Pose pose, VertexConsumer buffer, Charge charge) {
		double flash = flash(charge.age());
		if (flash <= 0) {
			return;
		}
		int[][] colours = new int[QUAD_CORNERS][];
		Arrays.fill(colours, LightStrokes.faded(FLASH, flash));
		Vec3 eye = charge.eye();
		// A small box round the eye: nearer than anything it covers, so the whole view goes white.
		for (int axis = 0; axis < BOX_AXES; axis++) {
			for (int sign = -1; sign <= 1; sign += 2) {
				Vec3 out = (axis == 0 ? Vec3.X_AXIS : axis == 1 ? Vec3.Y_AXIS : Vec3.Z_AXIS).scale(sign);
				Vec3 across = axis == 0 ? Vec3.Y_AXIS : Vec3.X_AXIS;
				Vec3 up = out.cross(across);
				Vec3 face = eye.add(out.scale(FLASH_REACH));
				LightStrokes.quad(pose, buffer, new Vec3[]{
					face.add(across.scale(-FLASH_REACH)).add(up.scale(-FLASH_REACH)),
					face.add(across.scale(FLASH_REACH)).add(up.scale(-FLASH_REACH)),
					face.add(across.scale(FLASH_REACH)).add(up.scale(FLASH_REACH)),
					face.add(across.scale(-FLASH_REACH)).add(up.scale(FLASH_REACH))}, colours);
			}
		}
	}

	private static void pool(PoseStack.Pose pose, VertexConsumer buffer, Charge charge, double radius, int[] rgba, double alpha) {
		if (alpha <= 0) {
			return;
		}
		Vec3 feet = charge.feet().add(0, FLOOR_LIFT, 0);
		double age = charge.age();
		for (int i = 0; i < DISC_SEGMENTS; i++) {
			double start = i * Mth.TWO_PI / DISC_SEGMENTS;
			double end = (i + 1) * Mth.TWO_PI / DISC_SEGMENTS;
			double innerStart = radius * PLATEAU * ragged(start, -age);
			double innerEnd = radius * PLATEAU * ragged(end, -age);
			LightStrokes.planeVertex(pose, buffer, feet, Vec3.X_AXIS, Vec3.Z_AXIS, 0, 0, 0, rgba, alpha);
			LightStrokes.planeVertex(pose, buffer, feet, Vec3.X_AXIS, Vec3.Z_AXIS, innerStart, Math.cos(start), Math.sin(start), rgba, alpha);
			LightStrokes.planeVertex(pose, buffer, feet, Vec3.X_AXIS, Vec3.Z_AXIS, innerEnd, Math.cos(end), Math.sin(end), rgba, alpha);
			LightStrokes.planeVertex(pose, buffer, feet, Vec3.X_AXIS, Vec3.Z_AXIS, 0, 0, 0, rgba, alpha);
			LightStrokes.planeVertex(pose, buffer, feet, Vec3.X_AXIS, Vec3.Z_AXIS, innerStart, Math.cos(start), Math.sin(start), rgba, alpha);
			LightStrokes.planeVertex(pose, buffer, feet, Vec3.X_AXIS, Vec3.Z_AXIS, radius * ragged(start, age), Math.cos(start), Math.sin(start), rgba, 0);
			LightStrokes.planeVertex(pose, buffer, feet, Vec3.X_AXIS, Vec3.Z_AXIS, radius * ragged(end, age), Math.cos(end), Math.sin(end), rgba, 0);
			LightStrokes.planeVertex(pose, buffer, feet, Vec3.X_AXIS, Vec3.Z_AXIS, innerEnd, Math.cos(end), Math.sin(end), rgba, alpha);
		}
	}

	private static double ragged(double angle, double age) {
		double ragged = 1;
		for (int wave = 0; wave < RIM_WAVES.length; wave++) {
			ragged += RIM_WAVE_DEPTH[wave] * Math.sin(RIM_WAVES[wave] * angle + RIM_WAVE_SPEED[wave] * age);
		}
		return ragged;
	}

	private static void wisp(PoseStack.Pose pose, VertexConsumer buffer, Charge charge, int wisp, int[] rgba, double alpha) {
		double age = charge.age() + charge.chargeTicks();
		double life = Mth.frac(age / WISP_LIFE + LightStrokes.hash(wisp, PHASE_SALT));
		double angle = (wisp + LightStrokes.hash(wisp, ANGLE_SALT)) * Mth.TWO_PI / WISPS;
		double out = BODY_RADIUS * grown(charge.age(), charge.chargeTicks()) * LightStrokes.hash(wisp, OUT_SALT);
		Vec3 round = new Vec3(Math.cos(angle), 0, Math.sin(angle));
		Vec3 across = new Vec3(-Math.sin(angle), 0, Math.cos(angle));
		double phase = Mth.TWO_PI * LightStrokes.hash(wisp, HEIGHT_SALT);
		double fade = Math.sin(Math.PI * life);
		Vec3[] points = new Vec3[WISP_ALONG.length];
		double[] widths = new double[points.length];
		double[] alphas = new double[points.length];
		for (int point = 0; point < points.length; point++) {
			double along = WISP_ALONG[point];
			double sway = WISP_SWAY * along * Math.sin(WISP_SWAY_WAVES * Mth.TWO_PI * along - WISP_SWAY_SPEED * age + phase);
			points[point] = charge.feet().add(round.scale(out + along * WISP_OUT)).add(across.scale(sway))
				.add(0, life * WISP_RISE + along * WISP_LENGTH, 0);
			widths[point] = WISP_HALF_WIDTH * (1 - along / 2);
			alphas[point] = alpha * fade * Math.sin(Math.PI * along);
		}
		LightStrokes.strip(pose, buffer, charge.eye(), points, widths, rgba, alphas);
	}

	private static void speck(PoseStack.Pose pose, VertexConsumer buffer, Charge charge, int speck, double strength) {
		double life = Mth.frac((charge.age() + charge.chargeTicks()) / SPECK_LIFE + LightStrokes.hash(speck, PHASE_SALT));
		double angle = LightStrokes.hash(speck, ANGLE_SALT) * Mth.TWO_PI;
		double out = BODY_RADIUS * LightStrokes.hash(speck, OUT_SALT);
		Vec3 at = charge.feet().add(Math.cos(angle) * out, life * SPECK_RISE, Math.sin(angle) * out);
		double alpha = strength * Math.sin(Math.PI * life);
		Vec3[] points = {at, at.add(0, SPECK_LENGTH, 0)};
		LightStrokes.strip(pose, buffer, charge.eye(), points, new double[]{SPECK_HALF_WIDTH, SPECK_HALF_WIDTH}, SPECK,
			new double[]{alpha, alpha});
	}

	private static void puff(PoseStack.Pose pose, VertexConsumer buffer, Charge charge, int puff, double surge, double strength,
		boolean glow) {
		double angle = Mth.frac(puff * GoldenRatio.SHARE + LightStrokes.hash(puff, ANGLE_SALT) / PUFFS) * Mth.TWO_PI;
		double out = Math.pow(LightStrokes.hash(puff, OUT_SALT), PUFF_OUTWARD) * SURGE_RADIUS * surge;
		double height = (PUFF_LOWEST + LightStrokes.hash(puff, HEIGHT_SALT) * PUFF_RISE) * surge;
		double radius = (PUFF_LEAST + LightStrokes.hash(puff, SIZE_SALT) * PUFF_LARGER) * Mth.lerp(surge, PUFF_GROWN, 1);
		Vec3 centre = charge.feet().add(Math.cos(angle) * out, height, Math.sin(angle) * out);
		Vec3 toEye = charge.eye().subtract(centre);
		if (toEye.length() < PUFF_NEAREST) {
			return;
		}
		Vec3 facing = toEye.normalize();
		Vec3 first = LightStrokes.perpendicular(facing);
		Vec3 second = facing.cross(first);
		double alpha = surge * strength;
		if (glow) {
			LightStrokes.disc(pose, buffer, centre, first, second, radius, PUFF_GLOW, PUFF_GLOW_ALPHA * alpha, PUFF_SEGMENTS);
			return;
		}
		Vec3 aside = new Vec3(-Math.sin(angle), 0, Math.cos(angle)).scale((LightStrokes.hash(puff, PHASE_SALT) - MIDDLE) * 2);
		Vec3 shade = centre.add(aside.add(0, -1, 0).scale(radius * SHADE_OFFSET));
		LightStrokes.disc(pose, buffer, shade, first, second, radius * SHADE_SIZE, PUFF_SHADE, SHADE_ALPHA * alpha, PUFF_SEGMENTS);
		LightStrokes.disc(pose, buffer, centre, first, second, radius, PUFF_PALE, PUFF_ALPHA * alpha, PUFF_SEGMENTS);
	}

	private static void flung(PoseStack.Pose pose, VertexConsumer buffer, Charge charge, int bit, double surge, int[] rgba, double alpha) {
		double angle = LightStrokes.hash(bit, ANGLE_SALT) * Mth.TWO_PI;
		Vec3 outward = new Vec3(Math.cos(angle), 0, Math.sin(angle));
		double out = LightStrokes.hash(bit, OUT_SALT) * SURGE_RADIUS * surge;
		Vec3 at = charge.feet().add(outward.scale(out)).add(0, LightStrokes.hash(bit, HEIGHT_SALT) * FLUNG_HIGHEST * surge, 0);
		double shown = alpha * surge;
		Vec3[] points = {at, at.add(outward.scale(FLUNG_LENGTH))};
		LightStrokes.strip(pose, buffer, charge.eye(), points, new double[]{FLUNG_HALF_WIDTH, FLUNG_HALF_WIDTH}, rgba,
			new double[]{shown, shown});
	}

	private static void bolt(PoseStack.Pose pose, VertexConsumer buffer, Charge charge, int lane, double strength) {
		int period = BOLT_PERIOD_LEAST + Mth.floor(LightStrokes.hash(lane, PERIOD_SALT) * BOLT_PERIOD_SPREAD);
		int tick = Mth.floor(charge.age()) + charge.chargeTicks() + Mth.floor(LightStrokes.hash(lane, END_SALT) * period);
		int into = Math.floorMod(tick, period);
		if (into >= BOLT_LIFE) {
			return;
		}
		int strike = lane * STRIKE_LANES + Math.floorDiv(tick, period);
		if (LightStrokes.hash(strike, SHOW_SALT) > strength) {
			return;
		}
		double flicker = into == 0 ? 1 : Mth.lerp(LightStrokes.hash(strike * BOLT_LIFE + into, FLICKER_SALT), FLICKER_LEAST, 1);
		double alpha = flicker * strength;
		double bearing = LightStrokes.hash(strike, ANGLE_SALT) * Mth.TWO_PI;
		double out = Mth.lerp(LightStrokes.hash(strike, OUT_SALT), BOLT_NEAREST, BOLT_FARTHEST);
		double topHeight = BOLT_TOP_LEAST + LightStrokes.hash(strike, HEIGHT_SALT) * BOLT_TOP_SPREAD;
		Vec3 top = charge.feet().add(Math.cos(bearing) * out, topHeight, Math.sin(bearing) * out);
		double footBearing = bearing + (LightStrokes.hash(strike, END_SALT) - MIDDLE) * BOLT_DRIFT / out;
		double footOut = out + (LightStrokes.hash(strike, PHASE_SALT) - MIDDLE) * BOLT_DRIFT;
		double footHeight = LightStrokes.hash(strike, RAGGED_SALT) * BOLT_FOOT_HIGHEST;
		Vec3 foot = charge.feet().add(Math.cos(footBearing) * footOut, footHeight, Math.sin(footBearing) * footOut);
		Vec3[] points = wavy(top, foot, BOLT_POINTS, strike);
		strike(pose, buffer, charge, points, alpha, true);
		Vec3 path = foot.subtract(top);
		Vec3 side = LightStrokes.perpendicular(path.normalize());
		for (int branch = 0; branch < BRANCHES; branch++) {
			int seed = strike * BRANCHES + branch + STRIKE_LANES / 2;
			double leaves = Mth.lerp(LightStrokes.hash(seed, OUT_SALT), BRANCH_FROM, BRANCH_FROM + BRANCH_SPREAD);
			int at = Mth.floor(leaves * (points.length - 1));
			double lean = BRANCH_LEAN * (LightStrokes.hash(seed, ANGLE_SALT) * 2 - 1);
			double length = path.length() * (BRANCH_LEAST + LightStrokes.hash(seed, SIZE_SALT) * BRANCH_LONGER);
			Vec3 reach = path.normalize().add(side.scale(lean)).normalize().scale(length);
			Vec3[] branchPoints = wavy(points[at], points[at].add(reach), BRANCH_POINTS, seed);
			strike(pose, buffer, charge, branchPoints, alpha * BRANCH_ALPHA, false);
		}
	}

	private static Vec3[] wavy(Vec3 from, Vec3 to, int count, int lane) {
		Vec3 path = to.subtract(from);
		double length = path.length();
		Vec3 side = LightStrokes.perpendicular(path.normalize());
		Vec3 other = path.normalize().cross(side);
		Vec3[] points = new Vec3[count];
		for (int point = 0; point < count; point++) {
			double along = (double) point / (count - 1);
			double sideways = BOLT_JITTER * (LightStrokes.hash(lane * count + point, RAGGED_SALT) * 2 - 1);
			double otherways = BOLT_JITTER * (LightStrokes.hash(lane * count + point, RAGGED_SALT + 1) * 2 - 1);
			for (int wave = 0; wave < BOLT_WAVES.length; wave++) {
				double phase = Mth.TWO_PI * LightStrokes.hash(lane * BOLT_WAVES.length + wave, PHASE_SALT);
				sideways += BOLT_WAVE_DEPTH[wave] * Math.sin(Mth.TWO_PI * BOLT_WAVES[wave] * along + phase);
				otherways += BOLT_WAVE_DEPTH[wave] * Math.cos(Mth.TWO_PI * BOLT_WAVES[wave] * along + phase);
			}
			points[point] = from.add(path.scale(along)).add(side.scale(sideways * length)).add(other.scale(otherways * length));
		}
		return points;
	}

	private static void strike(PoseStack.Pose pose, VertexConsumer buffer, Charge charge, Vec3[] points, double alpha, boolean bothEnds) {
		double[] glow = new double[points.length];
		double[] core = new double[points.length];
		double[] glowAlphas = new double[points.length];
		double[] coreAlphas = new double[points.length];
		Arrays.fill(glow, BOLT_GLOW_HALF_WIDTH);
		Arrays.fill(core, BOLT_CORE_HALF_WIDTH);
		for (int point = 0; point < points.length; point++) {
			double along = (double) point / (points.length - 1);
			double taper = bothEnds ? Math.sqrt(Math.sin(Math.PI * along)) : 1 - along;
			glowAlphas[point] = BOLT_GLOW_ALPHA * alpha * taper;
			coreAlphas[point] = BOLT_CORE_ALPHA * alpha * taper;
		}
		LightStrokes.strip(pose, buffer, charge.eye(), points, glow, BOLT_GLOW, glowAlphas);
		LightStrokes.strip(pose, buffer, charge.eye(), points, core, BOLT_CORE, coreAlphas);
	}
}
