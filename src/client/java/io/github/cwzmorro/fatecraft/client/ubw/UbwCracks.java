package io.github.cwzmorro.fatecraft.client.ubw;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.cwzmorro.fatecraft.GoldenRatio;
import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import io.github.cwzmorro.fatecraft.ubw.UbwOpening;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** The cracks Enuma Elish tears across a broken marble, seeded by the impact so every viewer sees the same ones. */
final class UbwCracks {
	private record Crack(Vec3[] points, double[] along) { }

	private interface Surface {
		Vec3 onto(Vec3 point);

		Vec3 normal(Vec3 point);

		boolean holds(Vec3 point);
	}

	private record Key(int radius, double floor, Vec3 impact) { }

	// Tuning numbers for the cracks (lengths in blocks, turns in radians) and the floor plates.
	private static final int SKY_CRACKS = 12;
	private static final int FLOOR_CRACKS = 9;
	private static final double LENGTH = 2.2;
	private static final double STEP = 1.3;
	private static final double WANDER = 0.45;
	private static final double SCATTER = 0.6;
	private static final double BRANCH_CHANCE = 0.09;
	private static final double BRANCH_TURN_LEAST = 0.4;
	private static final double BRANCH_TURN_MOST = 1.1;
	private static final double BRANCH_LENGTH = 0.45;
	private static final int BRANCH_DEPTH = 2;
	private static final double SKY_INSET = 0.2;
	private static final double FLOOR_LIFT = 0.06;
	private static final double FLOOR_REACH = 0.95;
	private static final double SKY_CLEARANCE = 0.5;
	private static final int[] GLOW = {255, 150, 70, 60};
	private static final int[] RIFT = {22, 3, 3, 240};
	private static final double GLOW_HALF_WIDTH = 0.2;
	private static final double RIFT_HALF_WIDTH = 0.07;
	private static final double TIP = 0.3;
	private static final double WIDEN = 3.0;
	private static final double WIDEN_BAND = 6.0;
	private static final int PLATES = 2400;
	private static final double PLATE_LIFETIME = 2.0;
	private static final double PLATE_RADIUS_LEAST = 0.45;
	private static final double PLATE_RADIUS_MOST = 1.2;
	private static final double PLATE_THICKNESS = 0.3;
	private static final int PLATE_SIDES = 6;
	private static final double PLATE_UNEVEN = 0.4;
	private static final double PLATE_LIFT = 0.02;
	private static final int[] MUD = {143, 106, 79};
	private static final double PLATE_VARIETY = 0.3;
	private static final double PLATE_DARKEST = 0.85;
	private static final double AMBIENT = 0.45;
	private static final Vec3 SUN = new Vec3(0.3, 0.9, 0.3).normalize();
	private static final double SIDE_SHADE = 0.75;
	private static final double CLEAR = 6.0;
	private static final double CLEAR_FADE = 6.0;
	private static final int PUFFS = 220;
	private static final double PUFF_BAND = 14.0;
	private static final double PUFF_RISE = 0.45;
	private static final double PUFF_RADIUS = 2.2;
	private static final double PUFF_GROWTH = 1.5;
	private static final int[] DUST = {176, 140, 98, 255};
	private static final double PUFF_ALPHA = 0.1;
	private static final int PUFF_SEGMENTS = 8;
	private static final int PLATE_SALT = 37;
	private static final int PUFF_SALT = 53;
	private static final int SALT_BEARING = 1;
	private static final int SALT_SIZE = 2;
	private static final int SALT_TURN = 3;
	private static final int SALT_SHADE = 4;
	private static final int SALT_CORNER = 5;
	private static final int SEED_ROTATE_Y = 21;
	private static final int SEED_ROTATE_Z = 42;
	private static Key madeFor;
	private static List<Crack> made = List.of();

	private UbwCracks() { }

	static void glow(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, int radius, double floor, UbwCrumble crumble) {
		draw(pose, buffer, eye, radius, floor, crumble, GLOW, GLOW_HALF_WIDTH);
	}

	static void rift(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, int radius, double floor, UbwCrumble crumble) {
		draw(pose, buffer, eye, radius, floor, crumble, RIFT, RIFT_HALF_WIDTH);
	}

	static void plates(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, int radius, double floor, UbwCrumble crumble) {
		double rim = UbwOpening.floorRadius(radius, floor) * FLOOR_REACH;
		for (int lane = 0; lane < PLATES; lane++) {
			Vec3 middle = floorPoint(lane, PLATE_SALT, rim, floor + PLATE_LIFT);
			// No lateness: a plate cannot stay hovering above ground the server has already restored.
			// Plates sink more slowly than sky shards, so the floor stays a broken field for longer.
			double broken = crumble.broken(middle, 0) / PLATE_LIFETIME;
			double turn = Mth.TWO_PI * LightStrokes.hash(lane, PLATE_SALT + SALT_TURN);
			Vec3 axis = new Vec3(Math.cos(turn), 0, Math.sin(turn));
			Vec3 centre = UbwCrumble.falling(middle, middle, axis, broken);
			double share = UbwCrumble.shown(broken) * clearShare(eye, centre);
			if (broken <= 0 || share <= 0) {
				continue;
			}
			double size = Mth.lerp(LightStrokes.hash(lane, PLATE_SALT + SALT_SIZE), PLATE_RADIUS_LEAST, PLATE_RADIUS_MOST) * share;
			Vec3[] top = new Vec3[PLATE_SIDES];
			Vec3[] bottom = new Vec3[PLATE_SIDES];
			for (int corner = 0; corner < PLATE_SIDES; corner++) {
				double angle = Mth.TWO_PI * corner / PLATE_SIDES;
				double uneven = 1 - PLATE_UNEVEN * LightStrokes.hash(lane * PLATE_SIDES + corner, PLATE_SALT + SALT_CORNER);
				Vec3 point = middle.add(Math.cos(angle) * size * uneven, 0, Math.sin(angle) * size * uneven);
				top[corner] = UbwCrumble.falling(point, middle, axis, broken);
				bottom[corner] = UbwCrumble.falling(point.add(0, -PLATE_THICKNESS * share, 0), middle, axis, broken);
			}
			int tint = UbwGroundTint.shade(Mth.floor(middle.x), Mth.floor(middle.z));
			double variety = Mth.lerp(LightStrokes.hash(lane, PLATE_SALT + SALT_SHADE), PLATE_DARKEST, PLATE_DARKEST + PLATE_VARIETY);
			double[] mud = {MUD[0] * ARGB.red(tint) / UbwSky.FULL * variety, MUD[1] * ARGB.green(tint) / UbwSky.FULL * variety,
				MUD[2] * ARGB.blue(tint) / UbwSky.FULL * variety};
			for (int corner = 0; corner < PLATE_SIDES; corner += 2) {
				Vec3[] fan = {centre, top[corner], top[(corner + 1) % PLATE_SIDES], top[(corner + 2) % PLATE_SIDES]};
				plateFace(pose, buffer, fan, mud, 1);
				Vec3[] under = {centre.add(0, -PLATE_THICKNESS * share, 0), bottom[corner], bottom[(corner + 1) % PLATE_SIDES], bottom[(corner + 2) % PLATE_SIDES]};
				plateFace(pose, buffer, under, mud, SIDE_SHADE);
			}
			for (int corner = 0; corner < PLATE_SIDES; corner++) {
				int next = (corner + 1) % PLATE_SIDES;
				plateFace(pose, buffer, new Vec3[]{top[corner], bottom[corner], bottom[next], top[next]}, mud, SIDE_SHADE);
			}
		}
	}

	private static void plateFace(PoseStack.Pose pose, VertexConsumer buffer, Vec3[] corners, double[] mud, double shade) {
		Vec3 normal = corners[1].subtract(corners[0]).cross(corners[2].subtract(corners[0])).normalize();
		double light = shade * (AMBIENT + (1 - AMBIENT) * Math.abs(normal.dot(SUN)));
		for (Vec3 corner : corners) {
			buffer.addVertex(pose, (float) corner.x, (float) corner.y, (float) corner.z)
				.setColor((int) (mud[0] * light), (int) (mud[1] * light), (int) (mud[2] * light), (int) UbwSky.FULL);
		}
	}

	static void dust(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, int radius, double floor, UbwCrumble crumble) {
		double rim = UbwOpening.floorRadius(radius, floor) * FLOOR_REACH;
		for (int lane = 0; lane < PUFFS; lane++) {
			Vec3 base = floorPoint(lane, PUFF_SALT, rim, floor + FLOOR_LIFT);
			double broken = crumble.broken(base, 0);
			if (broken <= 0 || broken >= PUFF_BAND) {
				continue;
			}
			double life = broken / PUFF_BAND;
			Vec3 at = base.add(0, PUFF_RISE * broken, 0);
			double alpha = PUFF_ALPHA * Math.sin(Math.PI * life) * clearShare(eye, at);
			if (alpha <= 0) {
				continue;
			}
			Vec3 look = at.subtract(eye).normalize();
			Vec3 across = LightStrokes.perpendicular(look);
			LightStrokes.disc(pose, buffer, at, across, look.cross(across), PUFF_RADIUS * (1 + PUFF_GROWTH * life), DUST, alpha, PUFF_SEGMENTS);
		}
	}

	private static Vec3 floorPoint(int lane, int salt, double rim, double y) {
		double bearing = (lane + LightStrokes.hash(lane, salt + SALT_BEARING)) * GoldenRatio.SHARE * Mth.TWO_PI;
		double distance = rim * Math.sqrt(LightStrokes.hash(lane, salt));
		return new Vec3(Math.cos(bearing) * distance, y, Math.sin(bearing) * distance);
	}

	private static double clearShare(Vec3 eye, Vec3 point) {
		return Mth.clamp((point.distanceTo(eye) - CLEAR) / CLEAR_FADE, 0, 1);
	}

	private static void draw(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, int radius, double floor, UbwCrumble crumble, int[] rgba,
		double halfWidth) {
		for (Crack crack : cracks(radius, floor, crumble.impact())) {
			int shown = 0;
			while (shown < crack.points().length && crack.along()[shown] <= crumble.cracks()) {
				shown++;
			}
			if (shown == 0) {
				continue;
			}
			boolean partial = shown < crack.points().length;
			int count = shown + (partial ? 1 : 0);
			if (count < 2) {
				continue;
			}
			Vec3[] points = new Vec3[count];
			double[] halfWidths = new double[count];
			double[] alphas = new double[count];
			double root = crack.along()[0];
			double end = crack.along()[crack.points().length - 1];
			for (int i = 0; i < count; i++) {
				Vec3 point;
				double along;
				if (i < shown) {
					point = crack.points()[i];
					along = crack.along()[i];
				} else {
					double share = (crumble.cracks() - crack.along()[i - 1]) / (crack.along()[i] - crack.along()[i - 1]);
					point = crack.points()[i - 1].lerp(crack.points()[i], share);
					along = crumble.cracks();
				}
				points[i] = point;
				double ahead = crumble.broken(point, 0);
				// A crack widens as the front nears it and the plates part, then is gone once the front has passed: the plates break off along it.
				double parting = 1 + WIDEN * Mth.clamp(1 + ahead / WIDEN_BAND, 0, 1);
				halfWidths[i] = halfWidth * parting * Mth.lerp((along - root) / Math.max(end - root, STEP), 1, TIP);
				alphas[i] = ahead < 0 ? 1 : 0;
			}
			LightStrokes.strip(pose, buffer, eye, points, halfWidths, rgba, alphas);
		}
	}

	private static List<Crack> cracks(int radius, double floor, Vec3 impact) {
		Key key = new Key(radius, floor, impact);
		if (key.equals(madeFor)) {
			return made;
		}
		long seed = Double.doubleToLongBits(impact.x)
			^ Long.rotateLeft(Double.doubleToLongBits(impact.y), SEED_ROTATE_Y)
			^ Long.rotateLeft(Double.doubleToLongBits(impact.z), SEED_ROTATE_Z);
		Random random = new Random(seed);
		List<Crack> result = new ArrayList<>();
		double sky = radius - UbwSky.BAND_INSET - SKY_INSET;
		Vec3 out = impact.lengthSqr() > 0 ? impact.normalize() : Vec3.Y_AXIS;
		Surface dome = new Surface() {
			@Override
			public Vec3 onto(Vec3 point) {
				return point.normalize().scale(sky);
			}

			@Override
			public Vec3 normal(Vec3 point) {
				return point.normalize();
			}

			@Override
			public boolean holds(Vec3 point) {
				return point.y > floor + SKY_CLEARANCE;
			}
		};
		double rim = UbwOpening.floorRadius(radius, floor) * FLOOR_REACH;
		double lift = floor + FLOOR_LIFT;
		Surface ground = new Surface() {
			@Override
			public Vec3 onto(Vec3 point) {
				return new Vec3(point.x, lift, point.z);
			}

			@Override
			public Vec3 normal(Vec3 point) {
				return Vec3.Y_AXIS;
			}

			@Override
			public boolean holds(Vec3 point) {
				return Math.hypot(point.x, point.z) < rim;
			}
		};
		Vec3 skySeed = dome.onto(out);
		if (dome.holds(skySeed)) {
			spread(random, dome, skySeed, SKY_CRACKS, LENGTH * radius, skySeed.distanceTo(impact), result);
		}
		double horizontal = Math.hypot(impact.x, impact.z);
		double inward = horizontal > rim ? rim / horizontal : 1;
		Vec3 floorSeed = new Vec3(impact.x * inward, lift, impact.z * inward);
		spread(random, ground, floorSeed, FLOOR_CRACKS, LENGTH * radius, floorSeed.distanceTo(impact), result);
		madeFor = key;
		made = List.copyOf(result);
		return made;
	}

	private static void spread(Random random, Surface surface, Vec3 seed, int count, double length, double along, List<Crack> result) {
		Vec3 normal = surface.normal(seed);
		Vec3 first = LightStrokes.perpendicular(normal);
		Vec3 second = normal.cross(first);
		for (int i = 0; i < count; i++) {
			double angle = (i + SCATTER * (random.nextDouble() - 0.5)) * Mth.TWO_PI / count;
			walk(random, surface, seed, first.scale(Math.cos(angle)).add(second.scale(Math.sin(angle))), length, along, 0, result);
		}
	}

	private static void walk(Random random, Surface surface, Vec3 start, Vec3 heading, double length, double along, int depth,
		List<Crack> result) {
		List<Vec3> points = new ArrayList<>(List.of(start));
		List<Double> alongs = new ArrayList<>(List.of(along));
		Vec3 point = start;
		Vec3 direction = heading;
		for (double travelled = 0; travelled < length; travelled += STEP) {
			direction = turn(direction, surface.normal(point), WANDER * (2 * random.nextDouble() - 1));
			Vec3 next = surface.onto(point.add(direction.scale(STEP)));
			if (!surface.holds(next)) {
				break;
			}
			Vec3 normal = surface.normal(next);
			Vec3 moved = next.subtract(point);
			direction = moved.subtract(normal.scale(moved.dot(normal))).normalize();
			along += moved.length();
			point = next;
			points.add(point);
			alongs.add(along);
			if (depth < BRANCH_DEPTH && random.nextDouble() < BRANCH_CHANCE) {
				double side = random.nextBoolean() ? 1 : -1;
				Vec3 branch = turn(direction, normal, side * Mth.lerp(random.nextDouble(), BRANCH_TURN_LEAST, BRANCH_TURN_MOST));
				walk(random, surface, point, branch, (length - travelled) * BRANCH_LENGTH, along, depth + 1, result);
			}
		}
		if (points.size() < 2) {
			return;
		}
		result.add(new Crack(points.toArray(Vec3[]::new), alongs.stream().mapToDouble(Double::doubleValue).toArray()));
	}

	private static Vec3 turn(Vec3 direction, Vec3 normal, double angle) {
		return direction.scale(Math.cos(angle)).add(normal.cross(direction).scale(Math.sin(angle)));
	}
}
