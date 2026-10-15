package io.github.cwzmorro.fatecraft.client.trace;

import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The blades of Sword Body: patches of flat steel blades lying like feathers on the skin, built once in a body part's own
 * frame (right-handed, x to his left, y up, z forward); the effect places them. A patch's spot is an axial height and an
 * arc length from the middle of the part's front toward his left, in blocks.
 */
final class SwordBodyBlades {
	record Facet(Vec3[] corners, int[][] colours) { }

	enum Part {
		TORSO(new Vec3(0, VanillaHumanoid.PLAYER.bodyMiddle(), 0), Vec3.ZERO, VanillaHumanoid.PLAYER.bodyHalfWidth(),
			VanillaHumanoid.PLAYER.bodyHalfDepth(), 0, false),
		RIGHT_ARM(new Vec3(-VanillaHumanoid.PLAYER.shoulderOut(), VanillaHumanoid.PLAYER.shoulderHeight(), 0),
			new Vec3(-VanillaHumanoid.PLAYER.armMiddleOut(), -VanillaHumanoid.PLAYER.armMiddleDown(), 0), VanillaHumanoid.PLAYER.limbHalf(),
			VanillaHumanoid.PLAYER.limbHalf(), -SwordBodyBlades.ARM_SWING, true),
		LEFT_ARM(new Vec3(VanillaHumanoid.PLAYER.shoulderOut(), VanillaHumanoid.PLAYER.shoulderHeight(), 0),
			new Vec3(VanillaHumanoid.PLAYER.armMiddleOut(), -VanillaHumanoid.PLAYER.armMiddleDown(), 0), VanillaHumanoid.PLAYER.limbHalf(),
			VanillaHumanoid.PLAYER.limbHalf(), SwordBodyBlades.ARM_SWING, true),
		RIGHT_LEG(new Vec3(-VanillaHumanoid.PLAYER.hipOut(), VanillaHumanoid.PLAYER.hipHeight(), 0),
			new Vec3(0, -VanillaHumanoid.PLAYER.partHalfLength(), 0), VanillaHumanoid.PLAYER.limbHalf(), VanillaHumanoid.PLAYER.limbHalf(),
			SwordBodyBlades.LEG_SWING, false),
		LEFT_LEG(new Vec3(VanillaHumanoid.PLAYER.hipOut(), VanillaHumanoid.PLAYER.hipHeight(), 0),
			new Vec3(0, -VanillaHumanoid.PLAYER.partHalfLength(), 0), VanillaHumanoid.PLAYER.limbHalf(), VanillaHumanoid.PLAYER.limbHalf(),
			-SwordBodyBlades.LEG_SWING, false);

		private final Vec3 pivot;
		private final Vec3 middle;
		private final double halfX;
		private final double halfZ;
		private final double halfLength;
		private final double swing;
		private final boolean arm;

		Part(Vec3 pivot, Vec3 middle, double halfX, double halfZ, double swing, boolean arm) {
			this.pivot = pivot;
			this.middle = middle;
			this.halfX = halfX;
			this.halfZ = halfZ;
			this.halfLength = VanillaHumanoid.PLAYER.partHalfLength();
			this.swing = swing;
			this.arm = arm;
		}

		Vec3 pivot() {
			return this.pivot;
		}

		Vec3 middle(boolean slim) {
			return slim && this.arm ? this.middle.subtract(Math.signum(this.middle.x) * VanillaHumanoid.PLAYER.halfPixel(), 0, 0) : this.middle;
		}

		double halfX(boolean slim) {
			return slim && this.arm ? this.halfX - VanillaHumanoid.PLAYER.halfPixel() : this.halfX;
		}

		double swing() {
			return this.swing;
		}
	}

	private record Patch(Part part, double along, double around, double radiusFlow, double radiusAcross, double flow, int seed, int streaks) { }

	private static final double ARM_SWING = 1.0;
	private static final double LEG_SWING = 1.4;

	private static final List<Patch> PATCHES = List.of(
		new Patch(Part.RIGHT_ARM, -0.17, -0.05, 0.1, 0.14, 3.5, 1, 2),
		new Patch(Part.LEFT_ARM, 0.17, 0.3, 0.1, 0.085, 2.7, 2, 2),
		new Patch(Part.TORSO, -0.02, 0.6, 0.15, 0.1, 2.5, 3, 3),
		new Patch(Part.TORSO, 0.22, -0.2, 0.07, 0.055, 2.5, 4, 1),
		new Patch(Part.RIGHT_LEG, 0.12, 0.12, 0.11, 0.085, 3.3, 5, 2),
		new Patch(Part.LEFT_LEG, -0.2, 0.5, 0.065, 0.055, 3.1, 6, 1));

	// A cross-section's roundness: 2 is an oval, higher squares it toward the box a limb is drawn as.
	private static final double SQUARENESS = 5.0;
	private static final int RING_FINE_STEPS = 720;
	private static final int RING_SAMPLES = 96;
	private static final double SKIN_LIFT = 0.018;
	private static final double LAYER_LIFT = 0.0004;
	private static final double POINT_LIFT = 0.008;
	private static final double BLOOD_LIFT = 0.003;
	private static final double COLUMN_SPACING = 0.015;
	private static final double ROW_SPACING = 0.017;
	private static final double PLACE_JITTER = 0.5;
	private static final double FLOW_JITTER = 0.16;
	private static final double RIM_WOBBLE = 0.22;
	private static final double RIM_LOBES = 3.0;
	private static final double RIM_REACH = 1.3;
	// A blade is about one skin pixel long and a third of one across (a pixel is 1/16 block), lifted about 8 degrees at its point.
	private static final double LEAST_LENGTH = 0.06;
	private static final double LENGTH_RANGE = 0.025;
	private static final double HALF_WIDTH = 0.01;
	private static final double END_MARGIN = 0.02;
	private static final double[] STATION = {0.0, 0.16, 0.72, 0.9, 1.0};
	private static final double[] LEFT_EDGE = {0.5, 1.0, 1.0, 0.6, -0.35};
	private static final double[] RIGHT_EDGE = {0.5, 1.0, 1.0, 1.0, 0.35};
	private static final double OUTLINE_SHARE = 0.14;
	private static final int BANDS = 4;
	private static final double ROOT_SHADE = 0.55;
	private static final int[] OUTLINE = {50, 52, 72, 255};
	private static final int[] STEEL_DARK = {104, 106, 132, 255};
	private static final int[] STEEL_LIGHT = {182, 184, 208, 255};
	private static final double TONE_FLOOR = 0.1;
	private static final double TONE_RANGE = 0.9;
	private static final double SIDE_SPLIT = 0.25;
	private static final int[] BLOOD = {122, 14, 20, 255};
	private static final int[] BLOOD_DARK = {82, 8, 14, 255};
	private static final int STREAK_STEPS = 7;
	private static final double STREAK_LENGTH = 0.05;
	private static final double STREAK_LENGTH_RANGE = 0.04;
	private static final double STREAK_WIDTH = 0.005;
	private static final double STREAK_DRIFT = 0.45;
	private static final double STREAK_START = 0.7;
	private static final double STREAK_THINNING = 0.5;
	private static final double STREAK_LIFT_SHARE = 0.5;
	private static final double DROP_SIZE = 0.004;
	private static final double DROP_STRETCH = 1.5;
	private static final int SALT_X = 1;
	private static final int SALT_Y = 2;
	private static final int SALT_FLOW = 3;
	private static final int SALT_LENGTH = 4;
	private static final int SALT_TONE = 5;
	private static final int SALT_PHASE = 6;
	private static final int ROW_KEY = 211;
	private static final int STREAK_KEY = 977;

	private static final Map<Boolean, Map<Part, List<Facet>>> FACETS = Map.of(false, facets(false), true, facets(true));

	private SwordBodyBlades() { }

	static List<Facet> facets(Part part, boolean slim) {
		return FACETS.get(slim).get(part);
	}

	private static Map<Part, List<Facet>> facets(boolean slim) {
		Map<Part, List<Facet>> map = new EnumMap<>(Part.class);
		Map<Part, Surface> surfaces = new EnumMap<>(Part.class);
		for (Part part : Part.values()) {
			map.put(part, new ArrayList<>());
			surfaces.put(part, new Surface(part.halfX(slim), part.halfZ));
		}
		for (Patch patch : PATCHES) {
			addPatch(map.get(patch.part()), patch, surfaces.get(patch.part()));
		}
		map.replaceAll((part, list) -> List.copyOf(list));
		return map;
	}

	private static void addPatch(List<Facet> out, Patch patch, Surface surface) {
		double cosFlow = Math.cos(patch.flow());
		double sinFlow = Math.sin(patch.flow());
		int rows = (int) Math.ceil(patch.radiusAcross() * RIM_REACH / ROW_SPACING);
		int columns = (int) Math.ceil(patch.radiusFlow() * RIM_REACH / COLUMN_SPACING);
		for (int i = -rows; i <= rows; i++) {
			for (int j = -columns; j <= columns; j++) {
				int lane = patch.seed() * ROW_KEY * ROW_KEY + i * ROW_KEY + j;
				double s = (j + ((i & 1) == 0 ? 0 : 0.5) + (LightStrokes.hash(lane, SALT_X) - 0.5) * PLACE_JITTER) * COLUMN_SPACING;
				double c = (i + (LightStrokes.hash(lane, SALT_Y) - 0.5) * PLACE_JITTER) * ROW_SPACING;
				double nx = s / patch.radiusFlow();
				double ny = c / patch.radiusAcross();
				double rim = 1 + RIM_WOBBLE * Math.sin(RIM_LOBES * Math.atan2(ny, nx) + patch.seed());
				if (nx * nx + ny * ny > rim * rim) {
					continue;
				}
				double flow = patch.flow() + (LightStrokes.hash(lane, SALT_FLOW) - 0.5) * FLOW_JITTER;
				double length = LEAST_LENGTH + LENGTH_RANGE * LightStrokes.hash(lane, SALT_LENGTH);
				double t = patch.along() + s * cosFlow - c * sinFlow;
				double arc = patch.around() + s * sinFlow + c * cosFlow;
				double end = t + length * Math.cos(flow);
				double halfLength = patch.part().halfLength - END_MARGIN;
				if (Math.abs(t) > halfLength || Math.abs(end) > halfLength) {
					continue;
				}
				addBlade(out, surface, t, arc, flow, length, layerLift(patch, s), LightStrokes.hash(lane, SALT_TONE));
			}
		}
		for (int k = 0; k < patch.streaks(); k++) {
			addStreak(out, surface, patch, k);
		}
	}

	private static double layerLift(Patch patch, double s) {
		return SKIN_LIFT + LAYER_LIFT * (patch.radiusFlow() * RIM_REACH - s) / COLUMN_SPACING;
	}

	private static void addBlade(List<Facet> out, Surface surface, double t, double arc, double flow, double length, double lift, double tone) {
		int[] left = LightStrokes.mix(STEEL_DARK, STEEL_LIGHT, TONE_FLOOR + TONE_RANGE * tone);
		int[] right = LightStrokes.mix(STEEL_DARK, STEEL_LIGHT, Mth.clamp(TONE_FLOOR + TONE_RANGE * tone + SIDE_SPLIT, 0.0, 1.0));
		Vec3[][] across = new Vec3[STATION.length][];
		int[][][] colours = new int[STATION.length][][];
		for (int k = 0; k < STATION.length; k++) {
			double u = STATION[k];
			double at = t + u * length * Math.cos(flow);
			double round = arc + u * length * Math.sin(flow);
			Vec3 middle = surface.point(at, round, lift + POINT_LIFT * u);
			Vec3 side = surface.across(round, flow).scale(HALF_WIDTH);
			Vec3 leftEdge = middle.add(side.scale(-LEFT_EDGE[k]));
			Vec3 rightEdge = middle.add(side.scale(RIGHT_EDGE[k]));
			across[k] = new Vec3[]{leftEdge, leftEdge.lerp(middle, OUTLINE_SHARE), middle, rightEdge.lerp(middle, OUTLINE_SHARE), rightEdge};
			double shade = Mth.lerp(u, ROOT_SHADE, 1.0);
			colours[k] = new int[][]{dimmed(OUTLINE, shade), dimmed(left, shade), dimmed(right, shade), dimmed(OUTLINE, shade)};
		}
		// Four flat bands along the blade: outline, left face, right face, outline; only the shade runs along each.
		for (int k = 0; k + 1 < STATION.length; k++) {
			for (int band = 0; band < BANDS; band++) {
				out.add(new Facet(new Vec3[]{across[k][band], across[k][band + 1], across[k + 1][band + 1], across[k + 1][band]},
					new int[][]{colours[k][band], colours[k][band], colours[k + 1][band], colours[k + 1][band]}));
			}
		}
	}

	private static void addStreak(List<Facet> out, Surface surface, Patch patch, int index) {
		int lane = patch.seed() * STREAK_KEY + index;
		double s = (LightStrokes.hash(lane, SALT_X) - STREAK_START) * patch.radiusFlow();
		double c = (LightStrokes.hash(lane, SALT_Y) - 0.5) * patch.radiusAcross();
		double length = STREAK_LENGTH + STREAK_LENGTH_RANGE * LightStrokes.hash(lane, SALT_LENGTH);
		double drift = (LightStrokes.hash(lane, SALT_PHASE) - 0.5) * STREAK_DRIFT;
		double cosFlow = Math.cos(patch.flow());
		double sinFlow = Math.sin(patch.flow());
		Vec3[] left = new Vec3[STREAK_STEPS];
		Vec3[] right = new Vec3[STREAK_STEPS];
		Vec3 end = Vec3.ZERO;
		double endArc = 0;
		for (int k = 0; k < STREAK_STEPS; k++) {
			double share = (double) k / (STREAK_STEPS - 1);
			double along = s + share * length;
			double across = c + share * length * drift;
			double t = patch.along() + along * cosFlow - across * sinFlow;
			double arc = patch.around() + along * sinFlow + across * cosFlow;
			double lift = layerLift(patch, along) + POINT_LIFT * STREAK_LIFT_SHARE + BLOOD_LIFT;
			Vec3 middle = surface.point(t, arc, lift);
			Vec3 side = surface.across(arc, patch.flow()).scale(STREAK_WIDTH * Math.cos(Mth.HALF_PI * Math.pow(share, STREAK_THINNING)));
			left[k] = middle.subtract(side);
			right[k] = middle.add(side);
			end = middle;
			endArc = arc;
		}
		for (int k = 0; k + 1 < STREAK_STEPS; k++) {
			int[] from = LightStrokes.mix(BLOOD_DARK, BLOOD, (double) k / (STREAK_STEPS - 1));
			int[] to = LightStrokes.mix(BLOOD_DARK, BLOOD, (double) (k + 1) / (STREAK_STEPS - 1));
			out.add(new Facet(new Vec3[]{left[k], right[k], right[k + 1], left[k + 1]}, new int[][]{from, from, to, to}));
		}
		Vec3 side = surface.across(endArc, patch.flow()).scale(DROP_SIZE);
		Vec3 tip = surface.across(endArc, patch.flow() + Mth.HALF_PI).scale(DROP_SIZE * DROP_STRETCH);
		out.add(new Facet(new Vec3[]{end.subtract(tip), end.add(side), end.add(tip), end.subtract(side)}, new int[][]{BLOOD, BLOOD, BLOOD, BLOOD}));
	}

	private static int[] dimmed(int[] rgba, double shade) {
		return LightStrokes.mix(new int[]{0, 0, 0, rgba[3]}, rgba, shade);
	}

	private static final class Surface {
		private final Vec3[] points = new Vec3[RING_SAMPLES];
		private final Vec3[] normals = new Vec3[RING_SAMPLES];
		private final double perimeter;

		Surface(double halfX, double halfZ) {
			Vec3[] fine = new Vec3[RING_FINE_STEPS + 1];
			double[] length = new double[RING_FINE_STEPS + 1];
			for (int i = 0; i <= RING_FINE_STEPS; i++) {
				double angle = Mth.TWO_PI * i / RING_FINE_STEPS;
				fine[i] = new Vec3(halfX * root(Math.sin(angle)), 0, halfZ * root(Math.cos(angle)));
				length[i] = i == 0 ? 0 : length[i - 1] + fine[i].distanceTo(fine[i - 1]);
			}
			this.perimeter = length[RING_FINE_STEPS];
			int at = 0;
			for (int i = 0; i < RING_SAMPLES; i++) {
				double target = this.perimeter * i / RING_SAMPLES;
				while (length[at + 1] < target) {
					at++;
				}
				this.points[i] = fine[at].lerp(fine[at + 1], (target - length[at]) / (length[at + 1] - length[at]));
			}
			for (int i = 0; i < RING_SAMPLES; i++) {
				Vec3 tangent = this.points[(i + 1) % RING_SAMPLES].subtract(this.points[(i + RING_SAMPLES - 1) % RING_SAMPLES]).normalize();
				this.normals[i] = new Vec3(-tangent.z, 0, tangent.x);
			}
		}

		private static double root(double v) {
			return Math.signum(v) * Math.pow(Math.abs(v), 2.0 / SQUARENESS);
		}

		Vec3 point(double t, double arc, double lift) {
			return this.ring(this.points, arc).add(this.normal(arc).scale(lift)).add(0, t, 0);
		}

		Vec3 normal(double arc) {
			return this.ring(this.normals, arc).normalize();
		}

		Vec3 across(double arc, double flow) {
			Vec3 normal = this.normal(arc);
			Vec3 round = Vec3.Y_AXIS.cross(normal);
			Vec3 along = Vec3.Y_AXIS.scale(Math.cos(flow)).add(round.scale(Math.sin(flow)));
			return along.cross(normal).normalize();
		}

		private Vec3 ring(Vec3[] samples, double arc) {
			double at = Mth.frac(arc / this.perimeter) * RING_SAMPLES;
			int i = (int) at;
			return samples[i % RING_SAMPLES].lerp(samples[(i + 1) % RING_SAMPLES], at - i);
		}
	}
}
