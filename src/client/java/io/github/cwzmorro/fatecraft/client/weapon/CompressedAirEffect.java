package io.github.cwzmorro.fatecraft.client.weapon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import io.github.cwzmorro.fatecraft.client.effect.LightTypes;
import io.github.cwzmorro.fatecraft.weapon.EnumaElishBeam;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import io.github.cwzmorro.fatecraft.weapon.WeaponSpin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The compressed wind round any player's spinning Ea: slashes of {@link WindLight#RED_LIGHT} sweeping round the wielder,
 * drawn in to Ea's tip while Enuma Elish winds up and fires.
 */
public final class CompressedAirEffect {
	// Tuning numbers for the streams and bolts round the wielder; lengths in blocks, times in ticks.
	private static final int STREAMS = 14;
	private static final int BOLTS = 6;
	private static final long MIN_STREAM_LIFE_NANOS = 300_000_000L;
	private static final long MAX_STREAM_LIFE_NANOS = 750_000_000L;
	private static final double MIN_RADIUS = 1.6;
	private static final double MIN_ARC = 1.5;
	private static final double MAX_ARC = 3.6;
	private static final double MIN_SWEEP_PER_SECOND = 3.0;
	private static final double MAX_SWEEP_PER_SECOND = 8.0;
	private static final double MIN_HALF_WIDTH = 0.12;
	private static final double MAX_HALF_WIDTH = 0.32;
	private static final double WOBBLE = 0.12;
	private static final int STREAM_POINTS = 40;
	private static final long MIN_BOLT_LIFE_NANOS = 40_000_000L;
	private static final long MAX_BOLT_LIFE_NANOS = 120_000_000L;
	private static final int BOLT_CORNERS = 9;
	private static final double BOLT_ARC_SHARE = 3.0;
	private static final double BOLT_JAG = 0.1;
	private static final double BOLT_HALF_WIDTH = 0.02;
	private static final double NANOS_PER_SECOND = 1e9;
	private static final double GATHERED_SIZE = 0.12;
	private static final double GATHERED_WIDTH = 0.5;
	private static final double GATHERED_SWEEP = 4.0;
	private static final int GATHERED_STREAMS = 10;

	private record Plane(Vec3 u, Vec3 v) {
		static Plane random() {
			Vec3 axis = direction();
			Vec3 u = LightStrokes.perpendicular(axis);
			return new Plane(u, axis.cross(u));
		}

		Vec3 onCircle(double angle, double radius) {
			return this.u.scale(Math.cos(angle) * radius).add(this.v.scale(Math.sin(angle) * radius));
		}
	}

	private record Stream(Plane plane, double radius, double startAngle, double arc, double sweepPerSecond, double halfWidth,
		double wobblePhase, long bornAt, long diesAt) {
	}

	private record Bolt(List<Vec3> corners, long diesAt) {
	}

	private record Wielder(Vec3 centre, double gathered, List<Stream> streams, List<Bolt> bolts) {
		double size() {
			return Mth.lerp(this.gathered, 1.0, GATHERED_SIZE);
		}
	}

	private static final Random RANDOM = new Random();
	private static final Map<Integer, Wielder> wielders = new HashMap<>();
	private static long now;

	private CompressedAirEffect() {
	}

	public static void register() {
		LevelRenderEvents.END_EXTRACTION.register(CompressedAirEffect::extract);
		LevelRenderEvents.COLLECT_SUBMITS.register(CompressedAirEffect::submit);
	}

	private static void extract(LevelExtractionContext context) {
		now = System.nanoTime();
		float partialTick = context.deltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.camera().position();
		Map<Integer, Wielder> seen = new HashMap<>();
		for (Player player : context.level().players()) {
			WeaponSpin spin = eaSpin(player);
			if (spin != null) {
				Vec3 body = player.getPosition(partialTick).add(0, player.getBbHeight() / 2, 0);
				Vec3 tip = EnumaElishBeam.origin(player.getEyePosition(partialTick), player.getViewVector(partialTick));
				// Compressed air is Ea held up in use; otherwise its spin is Enuma Elish, gathering over the wind-up.
				boolean enumaElish = !(player.isUsingItem() && player.getUseItem().is(FateWeapons.EA));
				double gathered = enumaElish
					? Math.clamp((context.level().getGameTime() + partialTick - spin.startTick()) / FateConfig.ea().enumaElishWindUpTicks(), 0.0, 1.0)
					: 0.0;
				Vec3 centre = body.lerp(tip, gathered * gathered).subtract(camera);
				Wielder before = wielders.get(player.getId());
				List<Stream> streams = before == null ? new ArrayList<>() : before.streams();
				List<Bolt> bolts = before == null ? new ArrayList<>() : before.bolts();
				streams.removeIf(stream -> stream.diesAt() < now);
				bolts.removeIf(bolt -> bolt.diesAt() < now);
				while (streams.size() < STREAMS + gathered * GATHERED_STREAMS) {
					streams.add(newStream());
				}
				while (bolts.size() < BOLTS) {
					bolts.add(newBolt());
				}
				seen.put(player.getId(), new Wielder(centre, gathered, streams, bolts));
			}
		}
		wielders.clear();
		wielders.putAll(seen);
	}

	private static @Nullable WeaponSpin eaSpin(Player player) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if (held.is(FateWeapons.EA) && held.has(FateWeapons.SPIN)) {
				return held.get(FateWeapons.SPIN);
			}
		}
		return null;
	}

	private static double between(double min, double max) {
		return Mth.lerp(RANDOM.nextDouble(), min, max);
	}

	private static double radius() {
		return between(MIN_RADIUS, FateConfig.ea().compressedAirRadius());
	}

	private static Vec3 direction() {
		double z = between(-1, 1);
		double angle = RANDOM.nextDouble() * Mth.TWO_PI;
		double across = Math.sqrt(1 - z * z);
		return new Vec3(across * Math.cos(angle), z, across * Math.sin(angle));
	}

	private static Stream newStream() {
		long life = (long) between(MIN_STREAM_LIFE_NANOS, MAX_STREAM_LIFE_NANOS);
		return new Stream(Plane.random(), radius(), RANDOM.nextDouble() * Mth.TWO_PI,
			between(MIN_ARC, MAX_ARC), between(MIN_SWEEP_PER_SECOND, MAX_SWEEP_PER_SECOND) * (RANDOM.nextBoolean() ? 1 : -1), between(MIN_HALF_WIDTH, MAX_HALF_WIDTH),
			RANDOM.nextDouble() * Mth.TWO_PI, now, now + life);
	}

	private static Bolt newBolt() {
		Plane plane = Plane.random();
		double radius = radius();
		double start = RANDOM.nextDouble() * Mth.TWO_PI;
		double arc = between(MIN_ARC, MAX_ARC) / BOLT_ARC_SHARE;
		List<Vec3> corners = new ArrayList<>(BOLT_CORNERS);
		for (int i = 0; i < BOLT_CORNERS; i++) {
			double angle = start + arc * i / (BOLT_CORNERS - 1);
			double r = radius * (1 + between(-BOLT_JAG, BOLT_JAG));
			corners.add(plane.onCircle(angle, r).add(direction().scale(radius * BOLT_JAG)));
		}
		return new Bolt(List.copyOf(corners), now + (long) between(MIN_BOLT_LIFE_NANOS, MAX_BOLT_LIFE_NANOS));
	}

	private static void submit(LevelRenderContext context) {
		PoseStack poseStack = context.poseStack();
		for (Wielder wielder : wielders.values()) {
			List<Stream> streams = List.copyOf(wielder.streams());
			List<Bolt> bolts = List.copyOf(wielder.bolts());
			Vec3 centre = wielder.centre();
			double size = wielder.size();
			long frame = now;
			for (LightStrokes.Layer layer : WindLight.RED_LIGHT) {
				LightTypes.submit(context.submitNodeCollector(), poseStack, layer.type(), (pose, buffer) -> {
					for (Stream stream : streams) {
						drawStream(pose, buffer, wielder, stream, frame, layer);
					}
				});
			}
			LightTypes.submit(context.submitNodeCollector(), poseStack, LightTypes.ADDED, (pose, buffer) -> {
				for (Bolt bolt : bolts) {
					for (int i = 1; i < bolt.corners().size(); i++) {
						LightStrokes.line(pose, buffer, Vec3.ZERO, centre.add(bolt.corners().get(i - 1).scale(size)), centre.add(bolt.corners().get(i).scale(size)),
							BOLT_HALF_WIDTH, WindLight.BOLT);
					}
				}
			});
		}
	}

	private static void drawStream(PoseStack.Pose pose, VertexConsumer buffer, Wielder wielder, Stream stream, long frame, LightStrokes.Layer layer) {
		double age = (frame - stream.bornAt()) / (double) (stream.diesAt() - stream.bornAt());
		double fade = Math.sin(Mth.clamp(age, 0.0, 1.0) * Math.PI);
		double sweep = stream.sweepPerSecond() * (1 + (GATHERED_SWEEP - 1) * wielder.gathered()) * (frame - stream.bornAt()) / NANOS_PER_SECOND;
		double width = stream.halfWidth() * Mth.lerp(wielder.gathered(), 1.0, GATHERED_WIDTH);
		Vec3[] points = new Vec3[STREAM_POINTS];
		double[] halfWidths = new double[STREAM_POINTS];
		for (int i = 0; i < STREAM_POINTS; i++) {
			double t = i / (double) (STREAM_POINTS - 1);
			double angle = stream.startAngle() + sweep + stream.arc() * t;
			double radius = stream.radius() * wielder.size() * (1 + WOBBLE * Math.sin(stream.wobblePhase() + t * Mth.TWO_PI));
			points[i] = wielder.centre().add(stream.plane().onCircle(angle, radius));
			halfWidths[i] = width;
		}
		WindLight.slash(pose, buffer, Vec3.ZERO, points, halfWidths, layer, fade);
	}
}
