package io.github.cwzmorro.fatecraft.client.ubw;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import java.util.function.ToDoubleFunction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The great bronze gears turning in the marble's clouded sky, shaded so their depth reads from below. */
final class UbwGears {
	private record Gear(double bearing, double elevation, double size, int teeth, int spokes, double turn, int way) { }

	private static final Gear[] GEARS = {
		new Gear(75, 32, 0.32, 32, 5, 17, 1),
		new Gear(150, 63, 0.38, 36, 6, 34, -1),
		new Gear(230, 28, 0.27, 28, 4, 9, 1),
		new Gear(310, 48, 0.34, 32, 5, 29, -1),
		new Gear(10, 72, 0.23, 24, 4, 42, 1)
	};
	// Opening ticks over which the gears fade in, only where the sky behind them has been revealed.
	private static final double FADE_FROM = 8;
	private static final double FADE_FULL = 32;
	private static final double TURN_PER_TICK = 0.0015;
	private static final double SKY_GAP = 2.0;
	private static final double VEIL_GAP = 1.0;
	private static final double VEIL_REACH = 1.4;
	private static final double VEIL_SOFT = 0.4;
	private static final int VEIL_RINGS = 10;
	private static final int VEIL_SEGMENTS = 40;
	private static final double TIP = 1.0;
	private static final double ROOT = 0.89;
	private static final double RIM_INSIDE = 0.72;
	private static final double HUB = 0.2;
	private static final double HOLE = 0.08;
	private static final double SPOKE_FROM = 0.17;
	private static final double SPOKE_TO = 0.75;
	private static final double SPOKE_HALF_WIDTH = 0.065;
	private static final double RIM_DEPTH = 0.15;
	private static final double SPOKE_DEPTH = 0.08;
	private static final double HUB_DEPTH = 0.24;
	private static final double BEVEL = 0.03;
	private static final int TOOTH_CORNERS = 4;
	private static final int HUB_SEGMENTS = 48;
	// Bronze lit by the sky round it: ambient light, a warm key light from the brightest clouds that never reaches faces toward the field, and a rim light on edge-on surfaces.
	private static final int[] BRONZE = {108, 68, 46, 255};
	private static final double AMBIENT = 0.32;
	private static final double SKYWARD = 0.45;
	private static final double KEY = 0.55;
	private static final double GLANCE = 0.6;
	private static final Vec3 KEY_LIGHT = new Vec3(0.6, 0.2, 0.77).normalize();
	private static final double HALF = 0.5;

	private record Frame(Vec3 eye, Vec3 centre, Vec3 xAxis, Vec3 yAxis, Vec3 out, double size, ToDoubleFunction<Vec3> share, double fade,
		double broken) {
		Vec3 at(double x, double y, double z) {
			return this.centre.add(this.xAxis.scale(x * this.size)).add(this.yAxis.scale(y * this.size)).add(this.out.scale(z * this.size));
		}

		Vec3 direction(double x, double y, double z) {
			return this.xAxis.scale(x).add(this.yAxis.scale(y)).add(this.out.scale(z)).normalize();
		}
	}

	private UbwGears() { }

	static void draw(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, int radius, UbwReveal reveal, @Nullable UbwCrumble crumble, double age) {
		double fade = fade(age);
		if (fade == 0) {
			return;
		}
		for (Gear gear : GEARS) {
			Vec3 out = out(gear);
			double size = radius * gear.size();
			Vec3 hung = out.scale(distance(radius, size));
			double broken = broken(crumble, hung);
			double turn = Math.toRadians(gear.turn()) + gear.way() * age * TURN_PER_TICK;
			Vec3 first = LightStrokes.perpendicular(out);
			Vec3 second = out.cross(first);
			Vec3 xAxis = first.scale(Math.cos(turn)).add(second.scale(Math.sin(turn)));
			Vec3 yAxis = second.scale(Math.cos(turn)).subtract(first.scale(Math.sin(turn)));
			Frame frame = new Frame(eye, hung, xAxis, yAxis, out, size, share(reveal, broken), fade, broken);
			ring(pose, buffer, frame, teeth(gear.teeth()), circle(gear.teeth() * TOOTH_CORNERS, RIM_INSIDE), RIM_DEPTH);
			ring(pose, buffer, frame, circle(HUB_SEGMENTS, HUB), circle(HUB_SEGMENTS, HOLE), HUB_DEPTH);
			for (int spoke = 0; spoke < gear.spokes(); spoke++) {
				spoke(pose, buffer, frame, spoke * Mth.TWO_PI / gear.spokes());
			}
		}
	}

	static void veil(PoseStack.Pose pose, VertexConsumer buffer, int radius, UbwReveal reveal, @Nullable UbwCrumble crumble, double age) {
		double fade = fade(age);
		if (fade == 0) {
			return;
		}
		for (Gear gear : GEARS) {
			Vec3 out = out(gear);
			double size = radius * gear.size();
			ToDoubleFunction<Vec3> share = share(reveal, broken(crumble, out.scale(distance(radius, size))));
			Vec3 centre = out.scale(distance(radius, size) - size * HUB_DEPTH * HALF - VEIL_GAP);
			Vec3 first = LightStrokes.perpendicular(out);
			Vec3 second = out.cross(first);
			double reach = size * VEIL_REACH;
			for (int ring = 0; ring < VEIL_RINGS; ring++) {
				for (int segment = 0; segment < VEIL_SEGMENTS; segment++) {
					int[][] corners = {{ring, segment}, {ring + 1, segment}, {ring + 1, segment + 1}, {ring, segment + 1}};
					for (int[] corner : corners) {
						double outward = (double) corner[0] / VEIL_RINGS;
						Vec3 point = LightStrokes.round(centre, reach * outward, first, second, corner[1] * Mth.TWO_PI / VEIL_SEGMENTS);
						double soft = Mth.smoothstep(Mth.clamp((1 - outward) / VEIL_SOFT, 0, 1));
						UbwSky.vertex(pose, buffer, point, fade * soft * share.applyAsDouble(point));
					}
				}
			}
		}
	}

	private static double fade(double age) {
		return Mth.clamp((age - FADE_FROM) / (FADE_FULL - FADE_FROM), 0, 1);
	}

	private static double broken(@Nullable UbwCrumble crumble, Vec3 centre) {
		return crumble == null ? 0 : crumble.broken(centre, 0);
	}

	private static ToDoubleFunction<Vec3> share(UbwReveal reveal, double broken) {
		double left = UbwCrumble.shown(broken);
		return point -> reveal.share(point) * left;
	}

	private static Vec3 out(Gear gear) {
		double bearing = Math.toRadians(gear.bearing());
		double elevation = Math.toRadians(gear.elevation());
		return new Vec3(Math.cos(bearing) * Math.cos(elevation), Math.sin(elevation), Math.sin(bearing) * Math.cos(elevation));
	}

	private static double distance(int radius, double size) {
		double sky = radius - UbwSky.BAND_INSET - SKY_GAP;
		return Math.sqrt(sky * sky - size * size) - size * RIM_DEPTH * HALF;
	}

	private static Vec2[] teeth(int teeth) {
		int corners = teeth * TOOTH_CORNERS;
		Vec2[] points = new Vec2[corners];
		for (int i = 0; i < corners; i++) {
			double angle = i * Mth.TWO_PI / corners;
			double reach = i % TOOTH_CORNERS < TOOTH_CORNERS / 2 ? TIP : ROOT;
			points[i] = new Vec2((float) (Math.cos(angle) * reach), (float) (Math.sin(angle) * reach));
		}
		return points;
	}

	private static Vec2[] circle(int segments, double radius) {
		Vec2[] points = new Vec2[segments];
		for (int i = 0; i < segments; i++) {
			double angle = i * Mth.TWO_PI / segments;
			points[i] = new Vec2((float) (Math.cos(angle) * radius), (float) (Math.sin(angle) * radius));
		}
		return points;
	}

	private static void ring(PoseStack.Pose pose, VertexConsumer buffer, Frame frame, Vec2[] outside, Vec2[] inside, double depth) {
		double half = depth * HALF;
		double flat = half - BEVEL;
		for (int i = 0; i < outside.length; i++) {
			int next = (i + 1) % outside.length;
			Vec2 outer0 = outside[i];
			Vec2 outer1 = outside[next];
			Vec2 inner0 = inside[i];
			Vec2 inner1 = inside[next];
			Vec2 outerBevel0 = toward(outer0, BEVEL);
			Vec2 outerBevel1 = toward(outer1, BEVEL);
			Vec2 innerBevel0 = toward(inner0, -BEVEL);
			Vec2 innerBevel1 = toward(inner1, -BEVEL);
			// Each wall faces out of the ring: away from the centre outside, toward it inside.
			Vec2 away = edgeNormal(outer0, outer1);
			Vec2 inward = edgeNormal(inner0, inner1).negated();
			for (int side = -1; side <= 1; side += 2) {
				double z = side * half;
				face(pose, buffer, frame, new double[][]{
					{innerBevel0.x, innerBevel0.y, z}, {outerBevel0.x, outerBevel0.y, z},
					{outerBevel1.x, outerBevel1.y, z}, {innerBevel1.x, innerBevel1.y, z}},
					0, 0, side);
				face(pose, buffer, frame, new double[][]{
					{outerBevel0.x, outerBevel0.y, z}, {outer0.x, outer0.y, side * flat},
					{outer1.x, outer1.y, side * flat}, {outerBevel1.x, outerBevel1.y, z}},
					away.x, away.y, side);
				face(pose, buffer, frame, new double[][]{
					{innerBevel0.x, innerBevel0.y, z}, {inner0.x, inner0.y, side * flat},
					{inner1.x, inner1.y, side * flat}, {innerBevel1.x, innerBevel1.y, z}},
					inward.x, inward.y, side);
			}
			face(pose, buffer, frame, new double[][]{
				{outer0.x, outer0.y, -flat}, {outer1.x, outer1.y, -flat}, {outer1.x, outer1.y, flat}, {outer0.x, outer0.y, flat}},
				away.x, away.y, 0);
			face(pose, buffer, frame, new double[][]{
				{inner0.x, inner0.y, -flat}, {inner1.x, inner1.y, -flat}, {inner1.x, inner1.y, flat}, {inner0.x, inner0.y, flat}},
				inward.x, inward.y, 0);
		}
	}

	private static Vec2 toward(Vec2 point, double by) {
		double length = point.length();
		return point.scale((float) ((length - by) / length));
	}

	private static Vec2 edgeNormal(Vec2 a, Vec2 b) {
		return new Vec2(b.y - a.y, a.x - b.x).normalized();
	}

	private static void spoke(PoseStack.Pose pose, VertexConsumer buffer, Frame frame, double angle) {
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		double half = SPOKE_DEPTH * HALF;
		for (int side = -1; side <= 1; side += 2) {
			double across = side * SPOKE_HALF_WIDTH;
			double[] sideFrom = {cos * SPOKE_FROM - sin * across, sin * SPOKE_FROM + cos * across};
			double[] sideTo = {cos * SPOKE_TO - sin * across, sin * SPOKE_TO + cos * across};
			face(pose, buffer, frame, new double[][]{
				{sideFrom[0], sideFrom[1], -half}, {sideTo[0], sideTo[1], -half},
				{sideTo[0], sideTo[1], half}, {sideFrom[0], sideFrom[1], half}},
				-sin * side, cos * side, 0);
			double z = side * half;
			double[] fromRight = {cos * SPOKE_FROM + sin * SPOKE_HALF_WIDTH, sin * SPOKE_FROM - cos * SPOKE_HALF_WIDTH};
			double[] toRight = {cos * SPOKE_TO + sin * SPOKE_HALF_WIDTH, sin * SPOKE_TO - cos * SPOKE_HALF_WIDTH};
			double[] toLeft = {cos * SPOKE_TO - sin * SPOKE_HALF_WIDTH, sin * SPOKE_TO + cos * SPOKE_HALF_WIDTH};
			double[] fromLeft = {cos * SPOKE_FROM - sin * SPOKE_HALF_WIDTH, sin * SPOKE_FROM + cos * SPOKE_HALF_WIDTH};
			face(pose, buffer, frame, new double[][]{
				{fromRight[0], fromRight[1], z}, {toRight[0], toRight[1], z}, {toLeft[0], toLeft[1], z}, {fromLeft[0], fromLeft[1], z}},
				0, 0, side);
		}
	}

	private static void face(PoseStack.Pose pose, VertexConsumer buffer, Frame frame, double[][] local, double nx, double ny, double nz) {
		Vec3 normal = frame.direction(nx, ny, nz);
		double skyward = normal.dot(frame.out());
		Vec3[] world = new Vec3[local.length];
		for (int i = 0; i < local.length; i++) {
			world[i] = frame.at(local[i][0], local[i][1], local[i][2]);
		}
		Vec3 toEye = frame.eye().subtract(world[0]).normalize();
		double edgeOn = 1 - Math.abs(normal.dot(toEye));
		double light = AMBIENT + SKYWARD * HALF * (1 + skyward) + KEY * Math.max(0, normal.dot(KEY_LIGHT)) * (1 - Math.max(0, -skyward))
			+ GLANCE * edgeOn * edgeOn;
		int[] colour = {lit(BRONZE[0], light), lit(BRONZE[1], light), lit(BRONZE[2], light), BRONZE[3]};
		int[][] colours = new int[local.length][];
		for (int i = 0; i < local.length; i++) {
			colours[i] = LightStrokes.faded(colour, frame.fade() * frame.share().applyAsDouble(world[i]));
		}
		for (int i = 0; i < world.length; i++) {
			world[i] = UbwCrumble.falling(world[i], frame.centre(), frame.xAxis(), frame.broken());
		}
		LightStrokes.quad(pose, buffer, world, colours);
	}

	private static int lit(int channel, double light) {
		return (int) Math.min(UbwSky.FULL, channel * light);
	}
}
