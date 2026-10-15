package io.github.cwzmorro.fatecraft.client.weapon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import io.github.cwzmorro.fatecraft.client.effect.LightTypes;
import io.github.cwzmorro.fatecraft.weapon.EnumaElishBeam;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Enuma Elish's beam: slashes of {@link WindLight#RED_LIGHT} whirling round the firing line, with lightning and dark
 * shards. Drawn by level render events, not as an entity, because the game culls an entity whose own box (here only the
 * beam's start) or chunk section it doesn't see.
 */
public final class EnumaElishEffect {
	// Tuning numbers for the beam's wind, lightning and shards; shares are of the beam's length or radius.
	private static final double GROWTH_PER_TICK = 25.0;
	private static final double FADE_IN_TICKS = 4.0;
	private static final double SWELL = 0.06;
	private static final double SWELL_PER_BLOCK = 0.11;
	private static final double SWELL_PER_TICK = 0.3;
	private static final int SLASHES = 110;
	private static final double SLASH_SHORTEST = 0.04;
	private static final double SLASH_LONGEST = 0.16;
	private static final double SLASH_FEWEST_TURNS = 0.3;
	private static final double SLASH_MOST_TURNS = 1.2;
	private static final double SLASH_NEAREST = 0.1;
	private static final double SLASH_FARTHEST = 1.05;
	private static final double SLASH_THINNEST = 0.12;
	private static final double SLASH_WIDEST = 0.35;
	private static final double SLASH_SLOWEST = 0.004;
	private static final double SLASH_FASTEST = 0.012;
	private static final double SLASH_LEAST_SPIN = 0.5;
	private static final double SLASH_MOST_SPIN = 1.0;
	private static final int SLASH_POINTS = 20;
	private static final int BOLTS = 14;
	private static final int BOLT_LIFE_TICKS = 3;
	private static final double BOLT_SHORTEST = 0.05;
	private static final double BOLT_LONGEST = 0.15;
	private static final int BOLT_CORNERS = 6;
	private static final double BOLT_JUMP = 1.2;
	private static final double BOLT_HALF_WIDTH = 0.1;
	private static final double FORK_CHANCE = 0.35;
	private static final int[] GOLD_BOLT = {255, 220, 120, 185};
	private static final int[] RED_BOLT = {255, 60, 60, 185};
	private static final int SHARDS = 40;
	private static final double SHARD_DRIFT = 0.004;
	private static final double SHARD_SPIN = 0.05;
	private static final double SHARD_NEAREST = 1.15;
	private static final double SHARD_FARTHEST = 1.7;
	private static final double SHARD_SMALLEST = 0.3;
	private static final double SHARD_LARGEST = 1.0;
	private static final int[] SHARD = {30, 3, 5, 110};

	private record Slash(double start, double length, double drift, double angle, double turns, double spin, double out, double width) {
	}

	private static final List<Slash> SLASH_PATHS = slashes(new Random(EnumaElishEffect.class.getName().hashCode() + 1));

	private static List<Slash> slashes(Random random) {
		List<Slash> slashes = new ArrayList<>(SLASHES);
		for (int i = 0; i < SLASHES; i++) {
			slashes.add(new Slash(random.nextDouble(), Mth.lerp(random.nextDouble(), SLASH_SHORTEST, SLASH_LONGEST),
				Mth.lerp(random.nextDouble(), SLASH_SLOWEST, SLASH_FASTEST), random.nextDouble() * Mth.TWO_PI,
				Mth.lerp(random.nextDouble(), SLASH_FEWEST_TURNS, SLASH_MOST_TURNS), Mth.lerp(random.nextDouble(), SLASH_LEAST_SPIN, SLASH_MOST_SPIN),
				Mth.lerp(random.nextDouble(), SLASH_NEAREST, SLASH_FARTHEST), Mth.lerp(random.nextDouble(), SLASH_THINNEST, SLASH_WIDEST)));
		}
		return List.copyOf(slashes);
	}

	private record Shard(double start, double drift, double angle, double spin, double out, double size) {
	}

	private static final List<Shard> SHARD_PATHS = shards(new Random(EnumaElishEffect.class.getName().hashCode()));

	private static List<Shard> shards(Random random) {
		List<Shard> shards = new ArrayList<>(SHARDS);
		for (int i = 0; i < SHARDS; i++) {
			shards.add(new Shard(random.nextDouble(), random.nextDouble() * SHARD_DRIFT, random.nextDouble() * Mth.TWO_PI,
				(random.nextDouble() * 2 - 1) * SHARD_SPIN, Mth.lerp(random.nextDouble(), SHARD_NEAREST, SHARD_FARTHEST),
				Mth.lerp(random.nextDouble(), SHARD_SMALLEST, SHARD_LARGEST)));
		}
		return List.copyOf(shards);
	}

	private record Beam(Vec3 start, Vec3 direction, Vec3 u, Vec3 v, double reach, float age) {
		Vec3 along(double distance) {
			return this.start.add(this.direction.scale(distance));
		}

		Vec3 at(double distance, double angle, double radius) {
			return this.along(distance).add(this.u.scale(Math.cos(angle) * radius)).add(this.v.scale(Math.sin(angle) * radius));
		}

		double radius(double distance) {
			return EnumaElishBeam.radiusAt(distance) * (1 + SWELL * Math.sin(distance * SWELL_PER_BLOCK - this.age * SWELL_PER_TICK));
		}

		double fade() {
			return Math.clamp(this.age / FADE_IN_TICKS, 0.0, 1.0);
		}
	}

	private static final List<Beam> beams = new ArrayList<>();

	private EnumaElishEffect() {
	}

	public static void register() {
		LevelRenderEvents.END_EXTRACTION.register(EnumaElishEffect::extract);
		LevelRenderEvents.COLLECT_SUBMITS.register(EnumaElishEffect::submit);
	}

	private static void extract(LevelExtractionContext context) {
		beams.clear();
		float partialTick = context.deltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.camera().position();
		for (Entity entity : context.level().entitiesForRendering()) {
			if (entity instanceof EnumaElishBeam beam) {
				Vec3 direction = Vec3.directionFromRotation(beam.getXRot(partialTick), beam.getYRot(partialTick));
				Vec3 u = LightStrokes.perpendicular(direction);
				float age = beam.tickCount + partialTick;
				beams.add(new Beam(beam.getPosition(partialTick).subtract(camera), direction, u, direction.cross(u),
					Math.min(beam.reach(), age * GROWTH_PER_TICK), age));
			}
		}
	}

	private static void submit(LevelRenderContext context) {
		PoseStack poseStack = context.poseStack();
		for (Beam beam : List.copyOf(beams)) {
			LightTypes.submit(context.submitNodeCollector(), poseStack, LightTypes.BLENDED_OVER, (pose, buffer) -> drawShards(pose, buffer, beam));
			for (LightStrokes.Layer layer : WindLight.RED_LIGHT) {
				LightTypes.submit(context.submitNodeCollector(), poseStack, layer.type(), (pose, buffer) -> drawSlashes(pose, buffer, beam, layer));
			}
			LightTypes.submit(context.submitNodeCollector(), poseStack, LightTypes.ADDED, (pose, buffer) -> drawBolts(pose, buffer, beam));
		}
	}

	private static void drawSlashes(PoseStack.Pose pose, VertexConsumer buffer, Beam beam, LightStrokes.Layer layer) {
		for (Slash slash : SLASH_PATHS) {
			double from = beam.reach() * ((slash.start() + beam.age() * slash.drift()) % 1.0);
			double length = Math.min(beam.reach() * slash.length(), beam.reach() - from);
			if (length <= 0) {
				continue;
			}
			Vec3[] line = new Vec3[SLASH_POINTS];
			double[] halfWidths = new double[SLASH_POINTS];
			for (int i = 0; i < SLASH_POINTS; i++) {
				double t = i / (double) (SLASH_POINTS - 1);
				double distance = from + length * t;
				double radius = beam.radius(distance);
				double angle = slash.angle() + beam.age() * slash.spin() + slash.turns() * Mth.TWO_PI * t;
				line[i] = beam.at(distance, angle, radius * slash.out());
				halfWidths[i] = radius * slash.width();
			}
			WindLight.slash(pose, buffer, Vec3.ZERO, line, halfWidths, layer, beam.fade());
		}
	}

	private static void drawBolts(PoseStack.Pose pose, VertexConsumer buffer, Beam beam) {
		long turn = (long) (beam.age() / BOLT_LIFE_TICKS);
		for (int bolt = 0; bolt < BOLTS; bolt++) {
			Random random = new Random(turn * BOLTS + bolt);
			double start = random.nextDouble() * (1 - BOLT_LONGEST);
			double span = Mth.lerp(random.nextDouble(), BOLT_SHORTEST, BOLT_LONGEST);
			double angle = random.nextDouble() * Mth.TWO_PI;
			int[] rgba = LightStrokes.faded(random.nextBoolean() ? GOLD_BOLT : RED_BOLT, beam.fade());
			int fork = random.nextDouble() < FORK_CHANCE ? 1 + random.nextInt(BOLT_CORNERS - 1) : -1;
			Vec3 previous = null;
			for (int corner = 0; corner <= BOLT_CORNERS; corner++) {
				double distance = beam.reach() * (start + span * corner / BOLT_CORNERS);
				angle += (random.nextDouble() * 2 - 1) * BOLT_JUMP;
				Vec3 point = beam.at(distance, angle, beam.radius(distance));
				if (previous != null) {
					LightStrokes.line(pose, buffer, Vec3.ZERO, previous, point, BOLT_HALF_WIDTH, rgba);
				}
				if (corner == fork) {
					double forkAngle = angle + (random.nextBoolean() ? BOLT_JUMP : -BOLT_JUMP);
					double forkDistance = distance + beam.reach() * span / BOLT_CORNERS;
					LightStrokes.line(pose, buffer, Vec3.ZERO, point, beam.at(forkDistance, forkAngle, beam.radius(forkDistance)), BOLT_HALF_WIDTH, rgba);
				}
				previous = point;
			}
		}
	}

	private static void drawShards(PoseStack.Pose pose, VertexConsumer buffer, Beam beam) {
		int[] rgba = LightStrokes.faded(SHARD, beam.fade());
		for (Shard shard : SHARD_PATHS) {
			double distance = beam.reach() * ((shard.start() + beam.age() * shard.drift()) % 1.0);
			double angle = shard.angle() + beam.age() * shard.spin();
			Vec3 centre = beam.at(distance, angle, beam.radius(distance) * shard.out());
			Vec3 half = beam.direction().scale(shard.size());
			LightStrokes.line(pose, buffer, Vec3.ZERO, centre.subtract(half), centre.add(half), shard.size() / 2, rgba);
		}
	}
}
