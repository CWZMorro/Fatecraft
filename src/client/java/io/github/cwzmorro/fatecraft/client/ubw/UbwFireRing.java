package io.github.cwzmorro.fatecraft.client.ubw;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import io.github.cwzmorro.fatecraft.ubw.UbwOpening;
import java.util.Arrays;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The opening's ring of fire, timed by the dimension time synced at the cast so late viewers never replay it. It spreads
 * from the caster's feet with the server's front ({@link UbwOpening#reach}) and dies at the boundary.
 */
public final class UbwFireRing {
	private static final int SEGMENTS = 160;
	private static final int SPOKES = 8;
	private static final int[] WHITE_HOT = {255, 246, 208, 255};
	private static final int[] ORANGE = {255, 138, 30, 255};
	private static final int[] CRIMSON = {192, 32, 16, 255};
	// Tuning numbers for the ring: heights and widths in blocks, alphas and shares as the names say.
	private static final double FLOOR_LIFT = 0.08;
	private static final double IGNITION_HALF_WIDTH = 0.25;
	private static final double SPOKE_HALF_WIDTH = 0.15;
	private static final double SPOKE_START = 0.2;
	private static final double SETTLE_TICKS = 4;
	private static final double[] BAND_OFFSET = {-0.9, -0.45, 0.0};
	private static final double[] BAND_HALF_WIDTH = {0.35, 0.4, 0.45};
	private static final int[][] BAND_COLOUR = {WHITE_HOT, ORANGE, CRIMSON};
	private static final int[] FLAME = {255, 84, 16, 255};
	private static final int[] HOT_BASE = {255, 236, 150, 255};
	private static final int[] DARK_RED = {130, 14, 6, 255};
	private static final int TONGUE_SLOTS = 240;
	private static final double TONGUE_LOWEST = 3.0;
	private static final double TONGUE_TALLEST = 10.0;
	private static final double TONGUE_NARROWEST = 0.45;
	private static final double TONGUE_WIDEST = 1.2;
	private static final double TONGUE_SWAY = 0.4;
	private static final double LICK_WAVES = 1.5;
	private static final double LICK_SPEED = 0.5;
	private static final double TONGUE_PULSE = 0.25;
	private static final double PULSE_SPEED = 0.6;
	private static final double[] TONGUE_RISE = {0.0, 0.3, 0.6, 0.85, 1.0};
	private static final double[] BODY_ALPHA = {0.9, 0.8, 0.55, 0.25, 0.0};
	private static final double[] EDGE_ALPHA = {0.0, 0.5, 0.45, 0.25, 0.0};
	private static final double CORE_HEIGHT = 0.4;
	private static final double CORE_WIDTH = 0.6;
	private static final double EDGE_FROM = 0.45;
	private static final double EDGE_WIDTH = 1.25;
	private static final double TAPER = 0.6;
	private static final double TIP_HALF_WIDTH = 0.05;
	private static final double BASE_HEIGHT = 1.5;
	private static final double BASE_ALPHA = 0.8;
	private static final int HEIGHT_SALT = 1;
	private static final int WIDTH_SALT = 2;
	private static final int ANGLE_SALT = 3;
	private static final int LICK_SALT = 4;
	private static final int PULSE_SALT = 5;
	private static final double FAR_ALPHA = 0.6;
	private static final double RIM_SOFT = 1.5;
	private static final double RIM_ALPHA = 0.55;
	private static final double RIM_HALF_WIDTH = 0.3;
	private static final double RIM_FLICKER = 0.25;
	private static final int RIM_FLICKERS = 31;
	private static final double RIM_FLICKER_SPEED = 1.3;

	record Ring(Vec3 eye, double originX, double originZ, double floor, double rim, double age, double reach, double span,
		UbwOpening.Timing timing) { }

	private UbwFireRing() { }

	public static double progress(double age, UbwOpening.Timing timing) {
		return Mth.clamp(age / timing.dyingTicks(), 0, 1);
	}

	static void wall(PoseStack.Pose pose, VertexConsumer buffer, Ring ring) {
		if (!walled(ring.age(), ring.timing())) {
			return;
		}
		for (int slot = 0; slot < TONGUE_SLOTS; slot++) {
			tongue(pose, buffer, ring, slot, EDGE_FROM, 1, EDGE_WIDTH, DARK_RED, EDGE_ALPHA);
		}
	}

	static void glow(PoseStack.Pose pose, VertexConsumer buffer, Ring ring) {
		double age = ring.age();
		if (walled(age, ring.timing())) {
			base(pose, buffer, ring);
			for (int slot = 0; slot < TONGUE_SLOTS; slot++) {
				tongue(pose, buffer, ring, slot, 0, 1, 1, FLAME, BODY_ALPHA);
				tongue(pose, buffer, ring, slot, 0, CORE_HEIGHT, CORE_WIDTH, HOT_BASE, BODY_ALPHA);
			}
		}
		UbwOpening.Timing timing = ring.timing();
		if (age < timing.spreadTicks()) {
			double igniting = 1 - settled(age, timing);
			if (igniting > 0) {
				Vec3[] circle = circle(ring, ring.originX(), ring.originZ(), ring.reach());
				strip(pose, buffer, ring, circle, IGNITION_HALF_WIDTH, WHITE_HOT, clip(ring, circle), igniting);
				Vec3 origin = new Vec3(ring.originX(), ring.floor() + FLOOR_LIFT, ring.originZ());
				for (int spoke = 0; spoke < SPOKES; spoke++) {
					double angle = spoke * Mth.TWO_PI / SPOKES;
					Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
					Vec3[] spokePoints = {origin.add(out.scale(ring.reach() * SPOKE_START)), origin.add(out.scale(ring.reach()))};
					LightStrokes.strip(pose, buffer, ring.eye(), spokePoints, new double[]{SPOKE_HALF_WIDTH, SPOKE_HALF_WIDTH}, ORANGE,
						new double[]{0, igniting});
				}
			}
			if (age >= timing.ignitionTicks()) {
				double strength = strength(ring) * settled(age, timing);
				for (int band = 0; band < BAND_OFFSET.length; band++) {
					Vec3[] circle = circle(ring, ring.originX(), ring.originZ(), Math.max(0, ring.reach() + BAND_OFFSET[band]));
					strip(pose, buffer, ring, circle, BAND_HALF_WIDTH[band], BAND_COLOUR[band], clip(ring, circle), strength);
				}
			}
		}
		double dying = age < timing.spreadTicks()
			? 1
			: 1 - (age - timing.spreadTicks()) / (timing.dyingTicks() - timing.spreadTicks());
		if (dying <= 0) {
			return;
		}
		Vec3[] boundary = circle(ring, 0, 0, ring.rim());
		double[] alpha = new double[boundary.length];
		for (int i = 0; i < boundary.length; i++) {
			double distance = Math.hypot(boundary[i].x - ring.originX(), boundary[i].z - ring.originZ());
			double lit = Mth.clamp((ring.reach() - distance) / RIM_SOFT, 0, 1);
			double phase = RIM_FLICKERS * i * Mth.TWO_PI / SEGMENTS + age * RIM_FLICKER_SPEED;
			double flicker = 1 - RIM_FLICKER + RIM_FLICKER * Math.sin(phase);
			alpha[i] = RIM_ALPHA * lit * dying * flicker;
		}
		strip(pose, buffer, ring, boundary, RIM_HALF_WIDTH * dying, CRIMSON, alpha, 1);
	}

	private static double settled(double age, UbwOpening.Timing timing) {
		return Mth.clamp((age - timing.ignitionTicks()) / SETTLE_TICKS, 0, 1);
	}

	private static double strength(Ring ring) {
		return Mth.lerp(Mth.clamp(ring.reach() / ring.span(), 0, 1), 1, FAR_ALPHA);
	}

	private static boolean walled(double age, UbwOpening.Timing timing) {
		return age >= timing.ignitionTicks() && age < timing.spreadTicks();
	}

	private static void base(PoseStack.Pose pose, VertexConsumer buffer, Ring ring) {
		double strength = strength(ring) * settled(ring.age(), ring.timing());
		Vec3[] base = circle(ring, ring.originX(), ring.originZ(), ring.reach());
		double[] clip = clip(ring, base);
		for (int i = 0; i < SEGMENTS; i++) {
			if (clip[i] == 0 && clip[i + 1] == 0) {
				continue;
			}
			Vec3[] corners = {base[i], base[i + 1], base[i + 1].add(0, BASE_HEIGHT, 0), base[i].add(0, BASE_HEIGHT, 0)};
			int[][] colours = {
				LightStrokes.faded(HOT_BASE, BASE_ALPHA * clip[i] * strength),
				LightStrokes.faded(HOT_BASE, BASE_ALPHA * clip[i + 1] * strength),
				LightStrokes.faded(HOT_BASE, 0),
				LightStrokes.faded(HOT_BASE, 0)};
			LightStrokes.quad(pose, buffer, corners, colours);
		}
	}

	private static void tongue(PoseStack.Pose pose, VertexConsumer buffer, Ring ring, int slot, double from, double to, double widthShare,
		int[] rgba, double[] alphas) {
		double angle = (slot + LightStrokes.hash(slot, ANGLE_SALT)) * Mth.TWO_PI / TONGUE_SLOTS;
		Vec3 foot = new Vec3(ring.originX() + Math.cos(angle) * ring.reach(), ring.floor(), ring.originZ() + Math.sin(angle) * ring.reach());
		double clip = Mth.clamp((ring.rim() - Math.hypot(foot.x, foot.z)) / RIM_SOFT, 0, 1);
		if (clip == 0) {
			return;
		}
		double age = ring.age();
		double pulse = Math.sin(age * PULSE_SPEED + Mth.TWO_PI * LightStrokes.hash(slot, PULSE_SALT));
		double height = Mth.lerp(LightStrokes.hash(slot, HEIGHT_SALT), TONGUE_LOWEST, TONGUE_TALLEST) * settled(age, ring.timing())
			* (1 - TONGUE_PULSE + TONGUE_PULSE * pulse);
		double width = Mth.lerp(LightStrokes.hash(slot, WIDTH_SALT), TONGUE_NARROWEST, TONGUE_WIDEST);
		Vec3 along = new Vec3(-Math.sin(angle), 0, Math.cos(angle));
		double lick = Mth.TWO_PI * LightStrokes.hash(slot, LICK_SALT);
		Vec3[] points = new Vec3[TONGUE_RISE.length];
		double[] halfWidths = new double[points.length];
		double[] shares = new double[points.length];
		for (int point = 0; point < points.length; point++) {
			double up = Mth.lerp(TONGUE_RISE[point], from, to);
			double sway = TONGUE_SWAY * width * up * Math.sin(LICK_WAVES * Mth.TWO_PI * up - LICK_SPEED * age + lick);
			points[point] = foot.add(along.scale(sway)).add(0, height * up, 0);
			halfWidths[point] = Math.max(TIP_HALF_WIDTH, widthShare * width * Math.pow(1 - up, TAPER));
			shares[point] = alphas[point] * clip * strength(ring);
		}
		LightStrokes.strip(pose, buffer, ring.eye(), points, halfWidths, rgba, shares);
	}

	private static Vec3[] circle(Ring ring, double x, double z, double radius) {
		Vec3[] points = new Vec3[SEGMENTS + 1];
		for (int i = 0; i < points.length; i++) {
			double angle = i * Mth.TWO_PI / SEGMENTS;
			points[i] = new Vec3(x + Math.cos(angle) * radius, ring.floor() + FLOOR_LIFT, z + Math.sin(angle) * radius);
		}
		return points;
	}

	private static double[] clip(Ring ring, Vec3[] points) {
		double[] shares = new double[points.length];
		for (int i = 0; i < points.length; i++) {
			shares[i] = Mth.clamp((ring.rim() - Math.hypot(points[i].x, points[i].z)) / RIM_SOFT, 0, 1);
		}
		return shares;
	}

	private static void strip(PoseStack.Pose pose, VertexConsumer buffer, Ring ring, Vec3[] points, double halfWidth, int[] rgba,
		double[] shares, double alpha) {
		double[] widths = new double[points.length];
		Arrays.fill(widths, halfWidth);
		double[] alphas = new double[points.length];
		for (int i = 0; i < points.length; i++) {
			alphas[i] = shares[i] * alpha;
		}
		LightStrokes.strip(pose, buffer, ring.eye(), points, widths, rgba, alphas);
	}
}
