package io.github.cwzmorro.fatecraft.client.artoria;

import static io.github.cwzmorro.fatecraft.client.artoria.ArtoriaLook.hash;
import static io.github.cwzmorro.fatecraft.client.artoria.ArtoriaLook.star;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.fatecraft.artoria.ArtoriaAttackEntity;
import io.github.cwzmorro.fatecraft.artoria.ArtoriaConfig;
import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import net.minecraft.world.phys.Vec3;

/** Mana Shot's flare at the sword's tip, its golden beam to the target and the billows of light where it lands. */
public final class ManaShotLook {
	private static final int[] GOLD = {255, 185, 35, 200};
	private static final int[] WHITE = {255, 250, 230, 245};
	private static final int[] HALO = {255, 160, 20, 90};
	private static final int RAYS = 12, CHARGE_GLINTS = 9, TARGET_GLINTS = 7, BILLOWS = 38, DISC_SEGMENTS = 24;
	private static final int ANGLE = 21, LENGTH = 22, RADIUS = 23, HEIGHT = 24, DEPTH = 25;
	private static final int SIZE = 26, BIRTH = 27, LIFETIME = 28, ROTATION = 29;
	private static final double CHARGE_SEED = 0.12, CHARGE_GROWTH = 0.88;
	private static final double RAY_MIN = 0.35, RAY_VARIATION = 0.65, RAY_JITTER = 0.22;
	private static final double RAY_WIDTH = 0.12, CORE_RAY_WIDTH = 0.035, RAY_KNEE = 0.12;
	private static final double CORE_RADIUS = 0.14, HALO_RADIUS = 0.55;
	private static final double GLINT_ORBIT = 1.15, GLINT_SIZE = 0.08, GLINT_WIDTH = 0.035;
	private static final double GLINT_PULSE_SPEED = 0.18, GLINT_PULSE_BASE = 0.45, GLINT_PULSE_SWING = 0.55;
	private static final double TARGET_SPREAD = 0.65, TARGET_STAR_SIZE = 0.22, TARGET_STAR_WIDTH = 0.04;
	private static final double TARGET_GLINT_END = 0.32, TARGET_GLINT_STAGGER = 0.08;
	private static final double BLOOM_START = 0.12, BLOOM_STAGGER = 0.22, BLOOM_LIFE = 0.58, BLOOM_LIFE_VARIATION = 0.18;
	private static final double BLOOM_SPREAD = 0.95, BLOOM_DEPTH = 0.55, BLOOM_RISE = 0.22;
	private static final double BLOOM_SIZE_MIN = 0.13, BLOOM_SIZE_VARIATION = 0.22;
	private static final double BLOOM_GROWTH = 0.4, BLOOM_GROWTH_SHARE = 0.6, BLOOM_FADE_POWER = 1.5;
	private static final double BILLOW_HALO_SCALE = 1.5, BILLOW_CORE_SCALE = 0.62;
	private static final double GOLD_ALPHA = 0.65, CORE_ALPHA = 0.85;
	private static final double CENTERED_RANGE = 2, CENTERED_OFFSET = 1;
	private static final double BEAM_HALO_WIDTH = 1.8, BEAM_GOLD_WIDTH = 0.95, BEAM_CORE_WIDTH = 0.3, BEAM_THINNING = 0.5;
	private static final double BEAM_SHARE = 0.6, BEAM_FADE_POWER = 0.8;
	private static final int BEAM_POINTS = 16;
	private static final double BEAM_FLARE_SCALE = 1.3;
	private static final int STREAKS = 28;
	private static final double STREAK_FLOW = 0.6, STREAK_LENGTH = 5, STREAK_WIDTH = 0.05, STREAK_MIN = 0.4, STREAK_SPREAD = 2.5;
	private static final double STROKE_UNIT = 0.25;

	private ManaShotLook() {
	}

	static void draw(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, ArtoriaAttackRenderer.State s,
		ArtoriaConfig c, Vec3 right, Vec3 up) {
		// While charging the entity follows the blade's tip, so zero is the tip, not the caster's centre.
		Vec3 face = eye.normalize();
		Vec3 screenRight = LightStrokes.perpendicular(face);
		Vec3 screenUp = face.cross(screenRight).normalize();
		if (s.phase == ArtoriaAttackEntity.Phase.CHARGE) {
			double t = Math.min(1, s.phaseAge / Durations.ticks(c.chargeSeconds()));
			flare(pose, buffer, eye, Vec3.ZERO, c.stopDistance() * (CHARGE_SEED + CHARGE_GROWTH * t), t, s.phaseAge);
			return;
		}
		if (s.phase == ArtoriaAttackEntity.Phase.BLAST) {
			double t = Math.min(1, s.phaseAge / Durations.ticks(c.effectSeconds()));
			double radius = c.blastRadius();
			beam(pose, buffer, eye, s, c);
			for (int glint = 0; glint < TARGET_GLINTS; glint++) {
				double age = t - hash(glint, BIRTH) * TARGET_GLINT_STAGGER;
				if (age < 0 || age >= TARGET_GLINT_END) {
					continue;
				}
				Vec3 at = randomOffset(glint, screenRight, screenUp, face).scale(radius * TARGET_SPREAD);
				double angle = hash(glint, ROTATION) * Math.PI;
				Vec3 u = screenRight.scale(Math.cos(angle)).add(screenUp.scale(Math.sin(angle)));
				Vec3 v = screenUp.scale(Math.cos(angle)).subtract(screenRight.scale(Math.sin(angle)));
				double fade = Math.sin(age / TARGET_GLINT_END * Math.PI);
				star(pose, buffer, eye, at, u, v, radius * TARGET_STAR_SIZE, STROKE_UNIT * TARGET_STAR_WIDTH,
					LightStrokes.faded(WHITE, fade));
			}
			for (int billow = 0; billow < BILLOWS; billow++) {
				double birth = BLOOM_START + hash(billow, BIRTH) * BLOOM_STAGGER;
				double life = BLOOM_LIFE + hash(billow, LIFETIME) * BLOOM_LIFE_VARIATION;
				double age = (t - birth) / life;
				if (age <= 0 || age >= 1) {
					continue;
				}
				double expansion = Math.sqrt(age);
				Vec3 at = randomOffset(billow, right, up, s.direction).scale(radius * BLOOM_SPREAD * expansion)
					.add(up.scale(radius * BLOOM_RISE * age));
				double size = radius * (BLOOM_SIZE_MIN + hash(billow, SIZE) * BLOOM_SIZE_VARIATION)
					* (BLOOM_GROWTH + BLOOM_GROWTH_SHARE * expansion);
				double fade = Math.sin(Math.PI * Math.pow(age, BLOOM_FADE_POWER));
				LightStrokes.disc(pose, buffer, at, screenRight, screenUp, size * BILLOW_HALO_SCALE, HALO, fade, DISC_SEGMENTS);
				LightStrokes.disc(pose, buffer, at, screenRight, screenUp, size, GOLD, fade * GOLD_ALPHA, DISC_SEGMENTS);
				LightStrokes.disc(pose, buffer, at, screenRight, screenUp, size * BILLOW_CORE_SCALE, WHITE, fade * CORE_ALPHA, DISC_SEGMENTS);
			}
		}
	}

	private static void flare(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, Vec3 at, double size, double t, double age) {
		Vec3 face = eye.subtract(at).normalize();
		Vec3 screenRight = LightStrokes.perpendicular(face);
		Vec3 screenUp = face.cross(screenRight).normalize();
		LightStrokes.disc(pose, buffer, at, screenRight, screenUp, size * HALO_RADIUS, HALO, t, DISC_SEGMENTS);
		for (int ray = 0; ray < RAYS; ray++) {
			double angle = Math.TAU * ray / RAYS + centered(ray, ANGLE) * RAY_JITTER;
			Vec3 axis = screenRight.scale(Math.cos(angle)).add(screenUp.scale(Math.sin(angle)));
			double length = size * (RAY_MIN + hash(ray, LENGTH) * RAY_VARIATION);
			ray(pose, buffer, eye, at, axis, length, STROKE_UNIT * RAY_WIDTH, GOLD, t);
			ray(pose, buffer, eye, at, axis, length, STROKE_UNIT * CORE_RAY_WIDTH, WHITE, t);
		}
		LightStrokes.disc(pose, buffer, at, screenRight, screenUp, size * CORE_RADIUS, WHITE, 1, DISC_SEGMENTS);
		for (int glint = 0; glint < CHARGE_GLINTS; glint++) {
			double angle = Math.TAU * hash(glint, ANGLE);
			double orbit = size * GLINT_ORBIT * Math.sqrt(hash(glint, RADIUS));
			Vec3 glintAt = at.add(screenRight.scale(Math.cos(angle) * orbit)).add(screenUp.scale(Math.sin(angle) * orbit));
			double pulse = GLINT_PULSE_BASE + GLINT_PULSE_SWING * Math.sin(age * GLINT_PULSE_SPEED + Math.TAU * hash(glint, BIRTH));
			star(pose, buffer, eye, glintAt, screenRight, screenUp, size * GLINT_SIZE, STROKE_UNIT * GLINT_WIDTH,
				LightStrokes.faded(GOLD, t * Math.max(0, pulse)));
		}
	}

	private static void beam(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, ArtoriaAttackRenderer.State s, ArtoriaConfig c) {
		double life = s.phaseAge / (Durations.ticks(c.effectSeconds()) * BEAM_SHARE);
		if (life >= 1) {
			return;
		}
		double fade = Math.pow(1 - life, BEAM_FADE_POWER);
		double thin = 1 - BEAM_THINNING * life;
		Vec3 start = s.beamStart;
		Vec3 axis = start.length() > 0 ? start.normalize().scale(-1) : s.direction;
		beamStrip(pose, buffer, eye, start, BEAM_CORE_WIDTH * thin, WHITE, fade);
		double length = start.length();
		Vec3 sideA = LightStrokes.perpendicular(axis);
		Vec3 sideB = axis.cross(sideA).normalize();
		for (int streak = 0; streak < STREAKS; streak++) {
			double along = (hash(streak, DEPTH) + life * STREAK_FLOW) % 1;
			double angle = Math.TAU * hash(streak, ANGLE);
			double spread = BEAM_HALO_WIDTH + STREAK_SPREAD * hash(streak, RADIUS);
			Vec3 side = sideA.scale(Math.cos(angle) * spread).add(sideB.scale(Math.sin(angle) * spread));
			Vec3 head = start.add(axis.scale(along * length)).add(side);
			Vec3 tail = head.subtract(axis.scale(STREAK_LENGTH * (STREAK_MIN + hash(streak, LENGTH))));
			LightStrokes.strip(pose, buffer, eye, new Vec3[]{tail, head}, new double[]{0, STREAK_WIDTH}, WHITE, new double[]{0, fade});
		}
		flare(pose, buffer, eye, start, c.stopDistance() * BEAM_FLARE_SCALE, fade, s.phaseAge);
	}

	// The gold layers are blended rather than added: light added over a bright sky only turns it white.
	static void drawBlended(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, ArtoriaAttackRenderer.State s, ArtoriaConfig c) {
		double life = s.phaseAge / (Durations.ticks(c.effectSeconds()) * BEAM_SHARE);
		if (s.phase != ArtoriaAttackEntity.Phase.BLAST || life >= 1) {
			return;
		}
		double fade = Math.pow(1 - life, BEAM_FADE_POWER);
		double thin = 1 - BEAM_THINNING * life;
		beamStrip(pose, buffer, eye, s.beamStart, BEAM_HALO_WIDTH * thin, HALO, fade);
		beamStrip(pose, buffer, eye, s.beamStart, BEAM_GOLD_WIDTH * thin, GOLD, fade);
	}

	private static void beamStrip(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, Vec3 start, double halfWidth, int[] color, double alpha) {
		Vec3[] path = new Vec3[BEAM_POINTS];
		double[] widths = new double[BEAM_POINTS], alphas = new double[BEAM_POINTS];
		for (int i = 0; i < BEAM_POINTS; i++) {
			path[i] = start.scale(1 - i / (double) (BEAM_POINTS - 1));
			widths[i] = halfWidth;
			alphas[i] = alpha;
		}
		LightStrokes.strip(pose, buffer, eye, path, widths, color, alphas);
	}

	private static double centered(int index, int channel) {
		return hash(index, channel) * CENTERED_RANGE - CENTERED_OFFSET;
	}

	private static Vec3 randomOffset(int index, Vec3 right, Vec3 up, Vec3 depth) {
		return right.scale(centered(index, RADIUS)).add(up.scale(centered(index, HEIGHT)))
			.add(depth.scale(centered(index, DEPTH) * BLOOM_DEPTH));
	}

	private static void ray(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, Vec3 origin, Vec3 axis,
		double length, double width, int[] color, double alpha) {
		LightStrokes.strip(pose, buffer, eye,
			new Vec3[]{origin, origin.add(axis.scale(length * RAY_KNEE)), origin.add(axis.scale(length))},
			new double[]{width, width, 0}, color, new double[]{alpha, alpha, 0});
	}
}
