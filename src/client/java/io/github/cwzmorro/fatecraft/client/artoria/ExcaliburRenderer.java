package io.github.cwzmorro.fatecraft.client.artoria;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.GoldenRatio;
import io.github.cwzmorro.fatecraft.artoria.ArtoriaConfig;
import io.github.cwzmorro.fatecraft.artoria.ExcaliburEntity;
import io.github.cwzmorro.fatecraft.artoria.ExcaliburNP;
import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import io.github.cwzmorro.fatecraft.client.effect.LightTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Excalibur's light, in which every edge fades out and nothing ends in a solid cap. */
public final class ExcaliburRenderer extends EntityRenderer<ExcaliburEntity, ExcaliburRenderer.State> {
	private static final int[] GOLD = {255, 170, 24, 180};
	private static final int[] CORE = {255, 250, 210, 240};
	private static final int RINGS = 65;
	private static final int SIDES = 48;
	private static final double GOLDEN = GoldenRatio.SHARE;
	private static final double SILVER = 0.4142135624;
	private static final int PHASE = 1;
	private static final int ANGLE = 2;
	private static final int RADIUS = 3;
	private static final int HEIGHT = 4;
	private static final int SIZE = 5;
	// The core pass is drawn added, so its glow, streaks and sparkles are thinner than the blended gold under it.
	private static final double GOLD_WIDEN = 2.5;
	private static final double GOLD_DIM = 0.5;

	private static final int[] SPARK = {255, 200, 60, 255};
	private static final int[] ORANGE = {255, 120, 20, 200};
	private static final int SPARKLES = 34;
	private static final double SPARKLE_TICKS = 140;
	private static final double SPARKLE_CLIMB = 3.5;
	private static final double SPARKLE_NEAR = 1.5;
	private static final double SPARKLE_FAR = 4.5;
	private static final double SPARKLE_FLOOR = -2.5;
	private static final double SPARKLE_UNIT = 0.05;
	private static final double BEAM_SPARKLE_UNIT = 0.03;
	private static final double SPARKLE_DISTANCE = 4;
	private static final double SPARKLE_VARIATION = 0.3;
	private static final double FLARE_LENGTH = 4;
	private static final double FLARE_WIDTH = 0.18;
	private static final int TRAIL_DOTS = 4;
	private static final double TRAIL_SPACING = 2.4;
	private static final double TRAIL_DOT = 0.4;
	private static final double UNDER_WIDEN = 1.8;
	private static final int MOTES = 30;
	private static final double MOTE_SHARE = 0.5;
	private static final double MOTE_DRIFT = 0.5;
	private static final int DOT_SEGMENTS = 8;

	private static final double BEAM_HALO = 1.35;
	private static final double BEAM_HALO_ALPHA = 0.5;
	private static final double[] BEAM_SHELLS = {0.4, 0.6, 0.8, 1.0};
	private static final double BEAM_SHELL_ALPHA = 0.3;
	private static final double BEAM_CORE = 0.5;
	private static final double BEAM_CORE_ALPHA = 0.6;
	private static final double BEAM_AXIS = 0.2;
	private static final double BEAM_FADE_IN = 3;
	private static final double BEAM_GLARE = 2.5;
	private static final double BEAM_FADE_OUT_RINGS = 4.0;
	private static final int BEAM_STREAKS = 18;
	private static final double STREAK_LENGTH = 16;
	private static final double STREAK_SPEED = 1.4;
	private static final double STREAK_WIDTH = 0.045;
	private static final double STREAK_INNER = 0.1;
	private static final double STREAK_OUTER = 0.85;

	private static final double BLADE_TRAIL = 8;
	private static final double BLADE_TAIL_HEIGHT = 0.5;
	private static final int BLADE_SAMPLES = 13;
	private static final int BLADE_ROWS = 12;
	private static final double BLADE_ALPHA = 0.9;
	private static final double BLADE_BACK_FADE = 0.8;
	private static final double BLADE_TOP_FADE = 0.3;
	private static final double BLADE_CORE_BACK = 2.5;
	private static final double BLADE_CORE_UP = 1.2;
	private static final double BLADE_EDGE_WIDTH = 1.0;
	private static final double BLADE_CORE_SHARE = 0.4;
	private static final double BLADE_LIFT = 0.1;
	private static final double BLADE_GLARE = 4;
	private static final double BLADE_GLARE_ALPHA = 0.5;
	private static final int TONGUES = 24;
	private static final double TONGUE_RISE = 0.8;
	private static final double TONGUE_RISE_RANGE = 1;
	private static final double TONGUE_BACK = 4;
	private static final double TONGUE_BACK_RANGE = 2;
	private static final double TONGUE_WIDTH = 0.45;
	private static final double TONGUE_FLICKER = 0.2;
	private static final double TONGUE_FLICKER_RATE = 0.9;
	private static final double TONGUE_ROOT = 0.5;
	private static final double TONGUE_BACK_SHARE = 0.6;
	private static final int SPRAY = 36;
	private static final double SPRAY_TICKS = 8;
	private static final double SPRAY_RISE = 2.5;
	private static final double SPRAY_OUT = 3.5;
	private static final double SPRAY_OUT_LEAST = 0.5;
	private static final double SPRAY_SIDE_SPLIT = 0.5;
	private static final double SPRAY_BACK = 1.5;
	private static final double SPRAY_STEP = 0.12;
	private static final double SPRAY_WIDTH = 0.1;
	private static final double SPRAY_FOOT = 2;
	private static final double SURFACE_MARGIN = 1;

	private static final double PILLAR_WIDTH = 1.0;
	private static final double PILLAR_FOOT_FLARE = 0.25;
	private static final double PILLAR_FOOT_FALL = 6;
	private static final double PILLAR_FADE = 0.4;
	private static final double PILLAR_FOOT_ALPHA = 0.5;
	private static final double PILLAR_FOOT_RISE = 40;
	private static final double[] PILLAR_SHELLS = {0.4, 0.6, 0.8, 1.0};
	private static final double PILLAR_SHELL_ALPHA = 0.3;
	private static final double PILLAR_CORE = 0.6;
	private static final double PILLAR_CORE_ALPHA = 0.4;
	private static final double PILLAR_AXIS = 0.18;
	private static final int STREAK_POINTS = 17;
	private static final int FLAMES = 140;
	private static final double FLAME_LENGTH = 0.3;
	private static final double FLAME_LENGTH_RANGE = 0.5;
	private static final double FLAME_RISE_TICKS = 50;
	private static final double FLAME_WIDTH = 0.07;
	private static final double FLAME_WIDTH_RANGE = 2;
	private static final double FLAME_SWAY = 0.5;
	private static final double FLAME_WAVE = 0.35;
	private static final double HOT_SHARE = 0.7;
	private static final double ORANGE_SHARE = 0.75;
	// Each ribbon: start angle and sweep (radians), start and end height and radius (shares of the column's), width (blocks), orange (1) or gold (0).
	private static final double[][] RIBBONS = {
		{0.5, 3.0, 0.02, 0.35, 1.2, 1.35, 7.0, 0},
		{3.3, 2.4, 0.2, 0.55, 1.45, 1.8, 8.0, 1},
		{5.8, 3.4, 0.4, 0.8, 1.9, 2.5, 9.0, 0},
		{2.0, 1.8, 0.6, 0.95, 2.1, 2.6, 8.0, 1}};
	private static final double RIBBON_TICKS = 40;
	private static final double RIBBON_ALPHA = 0.5;
	private static final double RIBBON_TAPER = 1.0;
	private static final double RIBBON_SOFT = 1.6;
	private static final double RIBBON_FACING = 0.5;
	private static final double RIBBON_SOFT_ALPHA = 0.4;
	private static final double CORE_FADE = 0.15;
	private static final double AXIS_FADE = 0.12;
	private static final double SHELL_FADE_STEP = 0.3;
	private static final double[][] EDGES = {{0.95, 0.3, 0.5}, {0.6, 0.3, 0.3}};
	private static final double FLAME_CEILING = 0.8;
	private static final double FLAME_OVERSHOOT = 0.2;
	private static final double FLAME_THIN = 0.6;
	private static final double FLAME_FADE = 0.3;
	private static final double SPINE = 0.25;
	private static final double RIBBON_FADE_IN = 1.5;
	private static final double SPARK_START = 0.3;
	private static final double SPARK_START_RANGE = 0.6;
	private static final double SPARK_RISE = 10;
	private static final double SPARK_DROP = 0.5;
	private static final double SPLASH_GLOW = 1.9;
	private static final double SPLASH_HEART = 0.6;
	private static final double SPLASH_GLARE = 0.9;
	private static final int SPLASH_RAYS = 14;
	private static final double SPLASH_RAY_INNER = 0.3;
	private static final double SPLASH_RAY_OUTER = 0.8;
	private static final double SPLASH_RAY_RANGE = 0.4;
	private static final double SPLASH_RAY_WIDTH = 0.1;
	private static final double SPLASH_RAY_ALPHA = 0.3;
	private static final double[][] FLOOD = {{1.0, 0.9}, {0.65, 1.0}, {0.35, 1.0}};
	private static final double SPLASH_LIFT = 0.12;
	private static final int BANKS = 12;
	private static final int BANK_POINTS = 7;
	private static final double BANK_OUT = 1.1;
	private static final double BANK_LENGTH = 1.8;
	private static final double BANK_HEIGHT = 0.5;
	private static final double BANK_END_HEIGHT = 0.5;
	private static final double BANK_HALF = 0.5;
	private static final double BANK_WIDTH = 0.75;
	private static final double BANK_ALPHA = 0.9;
	private static final int RING_ARCS = 5;
	private static final int RING_POINTS = 33;
	private static final int RING_SWEEP_SEED = 6;
	private static final int RING_ANGLE_SEED = 13;
	private static final double RING_RADIUS = 2.2;
	private static final double RING_RADIUS_RANGE = 0.5;
	private static final double RING_TRAVEL = 2.0;
	private static final double RING_HEIGHT = 0.52;
	private static final double RING_HEIGHT_RANGE = 0.08;
	private static final double RING_SWEEP = 0.8;
	private static final double RING_SWEEP_RANGE = 1.2;
	private static final double RING_TURN = 0.35;
	private static final double RING_WIDTH = 0.5;
	private static final double RING_WIDTH_RANGE = 0.25;
	private static final double RING_TAIL_POWER = 1.0;
	private static final double RING_HEAD_FADE = 0.16;
	private static final double RING_ALPHA = 0.95;
	private static final double RING_FACING = 0.35;
	private static final double RING_FACING_FLOOR = 0.05;
	private static final double RING_FADE_IN = 0.08;
	private static final double RING_FADE_OUT = 0.25;
	private static final int SPARKS = 60;
	private static final double SPARK_SPEED = 0.5;
	private static final double SPARK_TICKS = 24;
	private static final double SPARK_LENGTH = 1.2;
	private static final double SPARK_WIDTH = 0.08;
	private static final double GROW_HEAD_START = 0.1;
	private static final double GROW_SPEED = 4;
	private static final int DISC_SEGMENTS = 64;
	private static final double RAY_FADE = 0.15;
	private static final double STREAK_MIDDLE = 0.5;
	private static final double STREAK_FULLEST = 0.9;
	private static final double STREAK_MIDDLE_WIDTH = 0.6;
	private static final double STREAK_MIDDLE_ALPHA = 0.5;

	public ExcaliburRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	public static final class State extends EntityRenderState {
		ExcaliburEntity.Phase phase = ExcaliburEntity.Phase.CHARGE;
		Vec3 direction = Vec3.Z_AXIS;
		double reach;
		double waveDistance;
		float age;
		int mode;
		final double[] bladeAlong = new double[BLADE_SAMPLES];
		final double[] bladeGround = new double[BLADE_SAMPLES];
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public boolean shouldRender(ExcaliburEntity entity, Frustum frustum, double x, double y, double z) {
		// Its point-sized server box does not encompass the beam or the tall pillar.
		return entity.shouldRender(x, y, z);
	}

	@Override
	public void extractRenderState(ExcaliburEntity entity, State state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.phase = entity.phase();
		state.age = entity.phaseAge() + partialTick;
		state.mode = entity.mode();
		state.direction = entity.getLookAngle();
		state.reach = entity.reach();
		state.waveDistance = entity.waveDistance();
		if (state.phase == ExcaliburEntity.Phase.WAVE) {
			state.reach = Math.min(state.waveDistance, state.reach + ExcaliburEntity.waveTravel(partialTick));
			// The blade's foot runs on the ground's surface under the line, not down in the furrow it tears; off the ground it stays on the line.
			double tail = Math.max(0, state.reach - BLADE_TRAIL);
			Vec3 side = LightStrokes.perpendicular(state.direction).scale(ExcaliburEntity.furrowReach() + SURFACE_MARGIN);
			for (int i = 0; i < BLADE_SAMPLES; i++) {
				double along = Mth.lerp(i / (double) (BLADE_SAMPLES - 1), tail, state.reach);
				Vec3 line = entity.position().add(state.direction.scale(along));
				state.bladeAlong[i] = along;
				state.bladeGround[i] = surface(entity, line, side) - entity.getY();
			}
		}
	}

	private static double surface(ExcaliburEntity entity, Vec3 line, Vec3 beside) {
		double highest = Double.NEGATIVE_INFINITY;
		for (Vec3 probe : new Vec3[]{line, line.add(beside), line.subtract(beside)}) {
			BlockHitResult ground = ExcaliburEntity.ground(entity.level(), probe, entity);
			if (ground != null) {
				highest = Math.max(highest, ground.getLocation().y);
			}
		}
		return highest == Double.NEGATIVE_INFINITY ? line.y : highest;
	}

	@Override
	public void submit(State s, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
		Vec3 eye = camera.pos.subtract(s.x, s.y, s.z);
		// Blended gold stays readable against daytime sky; additive white burns out the core at night.
		LightTypes.submit(collector, poses, LightTypes.BLENDED, (pose, buffer) -> draw(pose, buffer, eye, s, GOLD, false));
		LightTypes.submit(collector, poses, LightTypes.ADDED, (pose, buffer) -> draw(pose, buffer, eye, s, CORE, true));
		super.submit(s, poses, collector, camera);
	}

	private static void draw(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, State s, int[] color, boolean core) {
		ArtoriaConfig c = FateConfig.artoria();
		switch (s.phase) {
			case CHARGE -> {
				// Beam's charge lights are left in the world instead, by ExcaliburChargeTrail.
				if (s.mode == ExcaliburNP.BURST) {
					charge(pose, buffer, eye, s, c, core);
				}
			}
			case HELD -> held(pose, buffer, eye, s, c, color, core);
			case WAVE -> advancingLight(pose, buffer, eye, s, c, color, core);
			case BURST -> burst(pose, buffer, eye, s, c, color, core);
		}
	}

	private static void charge(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, State s, ArtoriaConfig c, boolean core) {
		double fill = Math.min(1, s.age / Durations.ticks(c.excaliburWindUpSeconds()));
		// The caster stands behind the light's point of origin, which is held in front of her.
		Vec3 caster = s.direction.scale(-c.excaliburStartRadius());
		for (int lane = 0; lane < SPARKLES + MOTES; lane++) {
			double t = (s.age / SPARKLE_TICKS + LightStrokes.hash(lane, PHASE)) % 1;
			Vec3 at = caster.add(chargeOffset(lane, t * chargeClimb(lane)));
			chargeGlint(pose, buffer, eye, at, lane, SPARKLE_UNIT * eye.distanceTo(at) / SPARKLE_DISTANCE, fill * Math.sin(t * Math.PI), core);
		}
	}

	private static Vec3 chargeOffset(int lane, double climbed) {
		// Phase, angle, radius, starting height and size each come from their own hash, so nothing lines up from any view.
		double angle = Mth.TWO_PI * LightStrokes.hash(lane, ANGLE);
		double radius = SPARKLE_NEAR + (SPARKLE_FAR - SPARKLE_NEAR) * Math.sqrt(LightStrokes.hash(lane, RADIUS));
		return new Vec3(Math.cos(angle) * radius, SPARKLE_FLOOR * LightStrokes.hash(lane, HEIGHT) + climbed, Math.sin(angle) * radius);
	}

	private static double chargeClimb(int lane) {
		return SPARKLE_CLIMB * (lane >= SPARKLES ? MOTE_DRIFT : 1);
	}

	private static void chargeGlint(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, Vec3 at, int lane, double size, double glow, boolean core) {
		double unit = size * (1 + SPARKLE_VARIATION * (2 * LightStrokes.hash(lane, SIZE) - 1));
		if (lane >= SPARKLES) {
			LightStrokes.facingDisc(pose, buffer, eye, at, unit * MOTE_SHARE * (core ? 1 : UNDER_WIDEN), ORANGE, glow, DOT_SEGMENTS);
		} else {
			sparkle(pose, buffer, eye, at, unit * (core ? 1 : UNDER_WIDEN), core ? SPARK : GOLD, glow);
		}
	}

	record ChargeLight(Vec3 origin, double born, int lane, double chargeAge) {
	}

	static int chargeLanes() {
		return SPARKLES + MOTES;
	}

	static double chargeLifetime() {
		return SPARKLE_TICKS;
	}

	static double chargePhase(int lane) {
		return LightStrokes.hash(lane, PHASE);
	}

	static ChargeLight chargeLight(Vec3 caster, double born, int lane, double chargeAge) {
		return new ChargeLight(caster.add(chargeOffset(lane, 0)), born, lane, chargeAge);
	}

	// A Beam charge light stays where it was born; its size is fixed in the world, so a trail left behind her shrinks with distance.
	static void drawChargeLight(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, ChargeLight light, double clock, boolean core) {
		double age = clock - light.born();
		double t = Mth.clamp(age / SPARKLE_TICKS, 0, 1);
		Vec3 at = light.origin().add(0, t * chargeClimb(light.lane()), 0).subtract(camera);
		double fill = Math.min(1, (light.chargeAge() + age) / Durations.ticks(FateConfig.artoria().excaliburWindUpSeconds()));
		chargeGlint(pose, buffer, Vec3.ZERO, at, light.lane(), BEAM_SPARKLE_UNIT, fill * Math.sin(t * Math.PI), core);
	}

	private static void sparkle(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, Vec3 at, double unit, int[] color, double glow) {
		LightStrokes.facingDisc(pose, buffer, eye, at, unit, color, glow, DOT_SEGMENTS);
		Vec3 flare = eye.subtract(at).cross(Vec3.Y_AXIS).normalize().scale(unit * FLARE_LENGTH);
		ray(pose, buffer, eye, at.subtract(flare), at.add(flare), unit * FLARE_WIDTH, LightStrokes.faded(color, glow));
		for (int dot = 1; dot <= TRAIL_DOTS; dot++) {
			LightStrokes.facingDisc(pose, buffer, eye, at.add(0, -dot * unit * TRAIL_SPACING, 0), unit * TRAIL_DOT * (1 - dot / (TRAIL_DOTS + 1.0)), color, glow,
				DOT_SEGMENTS);
		}
	}

	private static void held(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, State s, ArtoriaConfig c, int[] color, boolean core) {
		Vec3[] centres = new Vec3[RINGS]; double[] radii = new double[RINGS]; double[] alpha = new double[RINGS];
		for (int i = 0; i < RINGS; i++) {
			double t = i / (double) (RINGS - 1);
			centres[i] = s.direction.scale(t * s.reach);
			radii[i] = ExcaliburEntity.radiusAt(t * s.reach);
			alpha[i] = Math.min(1, Math.min(t * s.reach / BEAM_FADE_IN, (1 - t) * RINGS / BEAM_FADE_OUT_RINGS));
		}
		Vec3 u = LightStrokes.perpendicular(s.direction); Vec3 v = s.direction.cross(u).normalize();
		if (core) {
			strip(pose, buffer, eye, centres, radii, BEAM_CORE, alpha, BEAM_CORE_ALPHA, color);
			strip(pose, buffer, eye, centres, radii, BEAM_AXIS, alpha, 1, color);
			for (double share : BEAM_SHELLS) {
				LightStrokes.tube(pose, buffer, eye, centres, scaled(radii, share * BEAM_CORE), u, v, SIDES, color, scaled(alpha, BEAM_SHELL_ALPHA));
			}
		} else {
			strip(pose, buffer, eye, centres, radii, BEAM_HALO, alpha, BEAM_HALO_ALPHA, color);
			for (double share : BEAM_SHELLS) {
				LightStrokes.tube(pose, buffer, eye, centres, scaled(radii, share), u, v, SIDES, color, scaled(alpha, BEAM_SHELL_ALPHA));
			}
		}
		for (int lane = 0; lane < BEAM_STREAKS; lane++) {
			double along = (s.age * STREAK_SPEED + Mth.frac(lane * GOLDEN) * s.reach) % s.reach;
			double end = Math.min(s.reach, along + STREAK_LENGTH);
			double angle = lane * GoldenRatio.ANGLE;
			double share = STREAK_INNER + (STREAK_OUTER - STREAK_INNER) * Mth.frac(lane * SILVER);
			Vec3 offset = u.scale(Math.cos(angle)).add(v.scale(Math.sin(angle)));
			Vec3 head = s.direction.scale(end).add(offset.scale(share * ExcaliburEntity.radiusAt(end)));
			Vec3 tail = s.direction.scale(along).add(offset.scale(share * ExcaliburEntity.radiusAt(along)));
			streak(pose, buffer, eye, tail, head, STREAK_WIDTH * ExcaliburEntity.radiusAt(end) * (core ? 1 : GOLD_WIDEN),
				LightStrokes.faded(color, core ? 1 : GOLD_DIM));
		}
		LightStrokes.facingDisc(pose, buffer, eye, Vec3.ZERO, c.excaliburStartRadius() * BEAM_GLARE, color, core ? 1 : GOLD_DIM, DISC_SEGMENTS);
	}

	private static void advancingLight(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, State s, ArtoriaConfig c, int[] color, boolean core) {
		if (s.reach <= 0) {
			return;
		}
		double height = c.excaliburPathHeight();
		Vec3 flat = new Vec3(s.direction.x, 0, s.direction.z);
		Vec3 forward = flat.lengthSqr() > 0 ? flat.normalize() : LightStrokes.perpendicular(s.direction);
		Vec3 side = new Vec3(-forward.z, 0, forward.x);
		Vec3[] foot = new Vec3[BLADE_SAMPLES];
		for (int i = 0; i < BLADE_SAMPLES; i++) {
			foot[i] = new Vec3(s.direction.x * s.bladeAlong[i], s.bladeGround[i] + BLADE_LIFT, s.direction.z * s.bladeAlong[i]);
		}
		Vec3 front = foot[BLADE_SAMPLES - 1];
		sheet(pose, buffer, foot, height, core);
		// The blazing leading edge: a vertical strip facing the viewer, so the blade also shows from straight behind it.
		Vec3[] edge = new Vec3[BLADE_ROWS + 1];
		double[] edgeWidth = new double[BLADE_ROWS + 1], edgeAlpha = new double[BLADE_ROWS + 1];
		for (int j = 0; j <= BLADE_ROWS; j++) {
			double up = j / (double) BLADE_ROWS;
			edge[j] = front.add(0, height * up, 0);
			edgeWidth[j] = BLADE_EDGE_WIDTH * (core ? BLADE_CORE_SHARE : GOLD_WIDEN) * Mth.lerp(up, 1, BLADE_CORE_SHARE);
			edgeAlpha[j] = Mth.smoothstep(Mth.clamp((1 - up) / BLADE_TOP_FADE, 0, 1)) * (core ? 1 : GOLD_DIM);
		}
		LightStrokes.strip(pose, buffer, eye, edge, edgeWidth, color, edgeAlpha);
		for (int lane = 0; lane < TONGUES; lane++) {
			double u = Mth.lerp(LightStrokes.hash(lane, ANGLE), 1 - TONGUE_BACK_SHARE, 1);
			Vec3 root = foot[(int) Math.round(u * (BLADE_SAMPLES - 1))].add(0, height * sheetHeight(u), 0);
			double rise = (TONGUE_RISE + TONGUE_RISE_RANGE * LightStrokes.hash(lane, HEIGHT))
				* (1 + TONGUE_FLICKER * Math.sin(s.age * TONGUE_FLICKER_RATE + lane * GoldenRatio.ANGLE));
			double back = TONGUE_BACK + TONGUE_BACK_RANGE * LightStrokes.hash(lane, RADIUS);
			Vec3[] path = new Vec3[STREAK_POINTS];
			double[] widths = new double[STREAK_POINTS], alpha = new double[STREAK_POINTS];
			for (int i = 0; i < STREAK_POINTS; i++) {
				double v = i / (double) (STREAK_POINTS - 1);
				path[i] = root.add(0, rise * v * (1 - v / 2), 0).subtract(forward.scale(back * v));
				widths[i] = TONGUE_WIDTH * (1 + LightStrokes.hash(lane, SIZE)) * Math.cos(v * Math.PI / 2) * (core ? BLADE_CORE_SHARE : 1);
				alpha[i] = Math.sin(Math.min(1, v / TONGUE_ROOT) * Math.PI / 2) * u;
			}
			LightStrokes.strip(pose, buffer, eye, path, widths, core ? CORE : GOLD, alpha);
		}
		Vec3 facing = eye.subtract(front).normalize();
		Vec3 right = facing.cross(Vec3.Y_AXIS).normalize();
		LightStrokes.disc(pose, buffer, front, right, right.cross(facing).normalize(), BLADE_GLARE, color,
			BLADE_GLARE_ALPHA * (core ? 1 : GOLD_DIM), DISC_SEGMENTS);
		for (int lane = 0; lane < SPRAY; lane++) {
			double life = (s.age / SPRAY_TICKS + LightStrokes.hash(lane, PHASE)) % 1;
			double out = LightStrokes.hash(lane, ANGLE) < SPRAY_SIDE_SPLIT ? -1 : 1;
			Vec3 start = front.subtract(forward.scale(SPRAY_FOOT * LightStrokes.hash(lane, SIZE)));
			double rise = SPRAY_RISE * (1 + LightStrokes.hash(lane, HEIGHT));
			double reach = SPRAY_OUT * (SPRAY_OUT_LEAST + LightStrokes.hash(lane, RADIUS));
			streak(pose, buffer, eye, shard(start, forward, side, out * reach, rise, Math.max(0, life - SPRAY_STEP)),
				shard(start, forward, side, out * reach, rise, life), SPRAY_WIDTH * (core ? 1 : UNDER_WIDEN),
				LightStrokes.faded(core ? SPARK : GOLD, Math.sin(life * Math.PI)));
		}
	}

	private static double sheetHeight(double u) {
		return Mth.lerp(Mth.smoothstep(u), BLADE_TAIL_HEIGHT, 1);
	}

	private static void sheet(PoseStack.Pose pose, VertexConsumer buffer, Vec3[] foot, double height, boolean core) {
		Vec3[][] at = new Vec3[BLADE_SAMPLES][BLADE_ROWS + 1];
		int[][][] tint = new int[BLADE_SAMPLES][BLADE_ROWS + 1][];
		for (int i = 0; i < BLADE_SAMPLES; i++) {
			double u = i / (double) (BLADE_SAMPLES - 1);
			for (int j = 0; j <= BLADE_ROWS; j++) {
				double up = j / (double) BLADE_ROWS;
				at[i][j] = foot[i].add(0, height * sheetHeight(u) * up, 0);
				double shown = BLADE_ALPHA * Math.pow(u, BLADE_BACK_FADE) * Mth.smoothstep(Mth.clamp((1 - up) / BLADE_TOP_FADE, 0, 1));
				tint[i][j] = core
					? LightStrokes.faded(CORE, shown * Math.pow(u, BLADE_CORE_BACK) * Math.pow(1 - up, BLADE_CORE_UP))
					: LightStrokes.faded(LightStrokes.mix(GOLD, ORANGE, Math.max(1 - u, up)), shown * GOLD_DIM);
			}
		}
		mesh(pose, buffer, at, tint);
	}

	private static Vec3 shard(Vec3 start, Vec3 forward, Vec3 side, double out, double rise, double life) {
		return start.add(0, arc(life) * rise, 0).subtract(forward.scale(SPRAY_BACK * life)).add(side.scale(out * life));
	}

	private static double arc(double life) {
		return 4 * life * (1 - life);
	}

	private static void mesh(PoseStack.Pose pose, VertexConsumer buffer, Vec3[][] at, int[][][] tint) {
		for (int i = 0; i + 1 < at.length; i++) {
			for (int j = 0; j + 1 < at[i].length; j++) {
				LightStrokes.quad(pose, buffer, new Vec3[]{at[i][j], at[i + 1][j], at[i + 1][j + 1], at[i][j + 1]},
					new int[][]{tint[i][j], tint[i + 1][j], tint[i + 1][j + 1], tint[i][j + 1]});
			}
		}
	}

	private static void burst(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, State s, ArtoriaConfig c, int[] color, boolean core) {
		double life = s.age / Durations.ticks(c.excaliburLightSeconds());
		double fade = Math.sin(Math.min(1, life) * Math.PI);
		double height = c.excaliburBurstHeight() * Math.min(1, (life + GROW_HEAD_START) * GROW_SPEED);
		double base = c.excaliburBurstVisualRadius();
		Vec3[] centres = new Vec3[RINGS]; double[] radii = new double[RINGS]; double[] foot = new double[RINGS];
		for (int i = 0; i < RINGS; i++) {
			double t = i / (double) (RINGS - 1);
			centres[i] = new Vec3(0, t * height, 0);
			radii[i] = pillarRadius(base, t);
			foot[i] = fade * Math.min(1, t * PILLAR_FOOT_RISE + PILLAR_FOOT_ALPHA);
		}
		// No cap: every layer fades to nothing over its own top part, the inner ones first and narrowing as they go, so the top is ragged.
		if (core) {
			strip(pose, buffer, eye, centres, radii, PILLAR_CORE, thinning(foot, CORE_FADE), PILLAR_CORE_ALPHA, color);
			strip(pose, buffer, eye, centres, radii, PILLAR_AXIS, thinning(foot, AXIS_FADE), 1, color);
		}
		for (double share : PILLAR_SHELLS) {
			int[] shell = !core && share == PILLAR_SHELLS[PILLAR_SHELLS.length - 1] ? ORANGE : color;
			LightStrokes.tube(pose, buffer, eye, centres, scaled(radii, share * (core ? PILLAR_CORE : 1)), Vec3.X_AXIS, Vec3.Z_AXIS, SIDES, shell,
				scaled(thinning(foot, PILLAR_FADE + (1 - share) * SHELL_FADE_STEP), PILLAR_SHELL_ALPHA));
		}
		if (!core) {
			Vec3 across = eye.cross(Vec3.Y_AXIS).normalize();
			for (int edge = -1; edge <= 1; edge += 2) {
				for (int band = 0; band < EDGES.length; band++) {
					Vec3[] path = new Vec3[RINGS];
					for (int i = 0; i < RINGS; i++) {
						path[i] = centres[i].add(across.scale(edge * radii[i] * EDGES[band][0]));
					}
					LightStrokes.strip(pose, buffer, eye, path, scaled(radii, EDGES[band][1]), band == 0 ? ORANGE : GOLD,
						scaled(thinning(foot, PILLAR_FADE), EDGES[band][2]));
				}
			}
		}
		for (int lane = 0; lane < FLAMES; lane++) {
			double share = Math.sqrt(Mth.frac(lane * SILVER));
			if (core && share > HOT_SHARE) {
				continue;
			}
			int[] tint = core ? color : share > ORANGE_SHARE ? ORANGE : color;
			double length = height * (FLAME_LENGTH + FLAME_LENGTH_RANGE * Mth.frac(lane * GOLDEN));
			double bottom = (s.age / FLAME_RISE_TICKS + Mth.frac(lane * GOLDEN * GOLDEN)) % 1 * (height + length) - length;
			double ceiling = height * (FLAME_CEILING + (1 + FLAME_OVERSHOOT - FLAME_CEILING) * Mth.frac(lane * SILVER * SILVER));
			double angle = lane * GoldenRatio.ANGLE;
			Vec3 outward = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			Vec3 sideways = new Vec3(-outward.z, 0, outward.x);
			Vec3[] path = new Vec3[STREAK_POINTS]; double[] widths = new double[STREAK_POINTS]; double[] strength = new double[STREAK_POINTS];
			for (int i = 0; i < STREAK_POINTS; i++) {
				double along = i / (double) (STREAK_POINTS - 1);
				double y = Mth.clamp(bottom + along * length, 0, ceiling);
				double t = height == 0 ? 0 : y / height;
				double wave = Math.sin(y * FLAME_WAVE + lane);
				path[i] = outward.scale(pillarRadius(base, t) * share).add(sideways.scale(FLAME_SWAY * wave)).add(0, y, 0);
				widths[i] = base * FLAME_WIDTH * (1 + FLAME_WIDTH_RANGE * Mth.frac(lane * GOLDEN)) * Math.sqrt(Math.sin(along * Math.PI))
					* (1 + FLAME_WAVE * Math.sin(y * FLAME_WAVE * 2 + lane)) * (core ? 1 : GOLD_WIDEN) * (1 - FLAME_THIN * y / ceiling);
				strength[i] = fade * Math.sin(along * Math.PI) * Mth.smoothstep(Mth.clamp((ceiling - y) / (height * FLAME_FADE), 0, 1)) * (core ? 1 : GOLD_DIM);
			}
			LightStrokes.strip(pose, buffer, eye, path, widths, tint, strength);
		}
		ribbons(pose, buffer, eye, height, base, s.age, core, fade);
		ring(pose, buffer, eye, base, height, life, core);
		if (core) {
			sparks(pose, buffer, eye, base, s.age, fade);
		} else {
			banks(pose, buffer, eye, base, fade);
		}
		splash(pose, buffer, eye, base, color, core, fade);
	}

	private static double pillarRadius(double base, double t) {
		return base * PILLAR_WIDTH * (1 + PILLAR_FOOT_FLARE * Math.exp(-t * PILLAR_FOOT_FALL));
	}

	private static double[] thinning(double[] foot, double span) {
		double[] out = new double[foot.length];
		for (int i = 0; i < foot.length; i++) {
			out[i] = foot[i] * Mth.smoothstep(Mth.clamp((1 - i / (double) (foot.length - 1)) / span, 0, 1));
		}
		return out;
	}

	private static void splash(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, double base, int[] color, boolean core, double fade) {
		Vec3 ground = new Vec3(0, SPLASH_LIFT, 0);
		for (double[] layer : FLOOD) {
			LightStrokes.disc(pose, buffer, ground, Vec3.X_AXIS, Vec3.Z_AXIS, base * SPLASH_GLOW * layer[0] * (core ? SPLASH_HEART : 1), color, fade * layer[1],
				DISC_SEGMENTS);
		}
		LightStrokes.facingDisc(pose, buffer, eye, ground, base * SPLASH_GLARE, color, fade, DISC_SEGMENTS);
		for (int lane = 0; lane < SPLASH_RAYS; lane++) {
			double angle = lane * GoldenRatio.ANGLE;
			Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			double outer = SPLASH_RAY_OUTER + SPLASH_RAY_RANGE * Mth.frac(lane * SILVER);
			ray(pose, buffer, eye, ground.add(out.scale(base * SPLASH_RAY_INNER)), ground.add(out.scale(base * outer)),
				base * SPLASH_RAY_WIDTH * (core ? 1 : GOLD_WIDEN), LightStrokes.faded(color, fade * SPLASH_RAY_ALPHA));
		}
	}

	private static void ribbons(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, double height, double base, double age, boolean core, double fade) {
		for (double[] ribbon : RIBBONS) {
			Vec3[] path = new Vec3[STREAK_POINTS]; double[] widths = new double[STREAK_POINTS]; double[] alpha = new double[STREAK_POINTS];
			for (int i = 0; i < STREAK_POINTS; i++) {
				double along = i / (double) (STREAK_POINTS - 1);
				double angle = ribbon[0] + ribbon[1] * along + age / RIBBON_TICKS;
				double out = base * Mth.lerp(along, ribbon[4], ribbon[5]);
				path[i] = new Vec3(Math.cos(angle) * out, Mth.lerp(along, ribbon[2], ribbon[3]) * height, Math.sin(angle) * out);
				double taper = Math.sin(along * Math.PI);
				widths[i] = ribbon[6] * Math.pow(taper, RIBBON_TAPER) * (core ? SPINE : 1);
				alpha[i] = fade * RIBBON_ALPHA * Mth.smoothstep(Mth.clamp(taper * RIBBON_FADE_IN, 0, 1));
			}
			// A band running towards or away from the camera is edge-on: it fades out there, so no straight cut edge shows from any angle.
			for (int i = 0; i < STREAK_POINTS; i++) {
				alpha[i] *= Mth.smoothstep(Mth.clamp(facing(path, i, eye) / RIBBON_FACING, 0, 1));
			}
			int[] tint = ribbon[7] == 0 ? GOLD : ORANGE;
			if (!core) {
				LightStrokes.strip(pose, buffer, eye, path, scaled(widths, RIBBON_SOFT), tint, scaled(alpha, RIBBON_SOFT_ALPHA));
			}
			LightStrokes.strip(pose, buffer, eye, path, widths, core ? CORE : tint, alpha);
		}
	}

	private static void banks(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, double base, double fade) {
		for (int bank = 0; bank < BANKS; bank++) {
			double angle = bank * Mth.TWO_PI / BANKS + bank * GOLDEN;
			Vec3 centre = new Vec3(Math.cos(angle), 0, Math.sin(angle)).scale(base * BANK_OUT);
			Vec3 along = new Vec3(-Math.sin(angle), 0, Math.cos(angle)).scale(base * BANK_LENGTH);
			Vec3[] path = new Vec3[BANK_POINTS]; double[] widths = new double[BANK_POINTS]; double[] alpha = new double[BANK_POINTS];
			for (int i = 0; i < BANK_POINTS; i++) {
				double f = i / (double) (BANK_POINTS - 1);
				double taper = Math.sin(f * Math.PI);
				path[i] = centre.add(along.scale(f - BANK_HALF)).add(0, base * BANK_HEIGHT * (BANK_END_HEIGHT + taper), 0);
				widths[i] = base * BANK_WIDTH * taper;
				alpha[i] = fade * BANK_ALPHA * taper;
			}
			LightStrokes.strip(pose, buffer, eye, path, widths, bank % 2 == 0 ? GOLD : ORANGE, alpha);
		}
	}

	private static void ring(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, double base, double height, double life, boolean core) {
		double fade = Mth.smoothstep(Mth.clamp(life / RING_FADE_IN, 0, 1))
			* Mth.smoothstep(Mth.clamp((1 - life) / RING_FADE_OUT, 0, 1));
		for (int lane = 0; lane < RING_ARCS; lane++) {
			double start = Mth.TWO_PI * LightStrokes.hash(lane, RING_ANGLE_SEED) + life * RING_TURN;
			double sweep = RING_SWEEP + RING_SWEEP_RANGE * LightStrokes.hash(lane, RING_SWEEP_SEED);
			double radius = base * (RING_RADIUS + RING_RADIUS_RANGE * LightStrokes.hash(lane, RADIUS) + life * RING_TRAVEL);
			double y = height * (RING_HEIGHT + RING_HEIGHT_RANGE * LightStrokes.hash(lane, HEIGHT));
			double width = base * (RING_WIDTH + RING_WIDTH_RANGE * LightStrokes.hash(lane, SIZE));
			Vec3[] path = new Vec3[RING_POINTS]; double[] widths = new double[RING_POINTS]; double[] alpha = new double[RING_POINTS];
			for (int i = 0; i < RING_POINTS; i++) {
				double along = i / (double) (RING_POINTS - 1);
				double angle = start + along * sweep;
				double taper = Math.pow(along, RING_TAIL_POWER)
					* Mth.smoothstep(Mth.clamp((1 - along) / RING_HEAD_FADE, 0, 1));
				path[i] = new Vec3(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
				widths[i] = width * taper * (core ? SPINE : 1);
				alpha[i] = fade * RING_ALPHA * Math.sqrt(taper);
			}
			// Only the stretches seen nearly edge-on are softened; no side of an arc is ever hidden.
			for (int i = 0; i < RING_POINTS; i++) {
				alpha[i] *= Mth.lerp(Mth.smoothstep(Mth.clamp(facing(path, i, eye) / RING_FACING, 0, 1)), RING_FACING_FLOOR, 1);
			}
			int[] tint = LightStrokes.hash(lane, PHASE) < HOT_SHARE ? GOLD : ORANGE;
			if (!core) {
				LightStrokes.strip(pose, buffer, eye, path, scaled(widths, RIBBON_SOFT), tint, scaled(alpha, RIBBON_SOFT_ALPHA));
			}
			LightStrokes.strip(pose, buffer, eye, path, widths, core ? CORE : tint, alpha);
		}
	}

	private static double facing(Vec3[] path, int i, Vec3 eye) {
		Vec3 along = path[Math.min(i + 1, path.length - 1)].subtract(path[Math.max(i - 1, 0)]);
		Vec3 view = path[i].subtract(eye);
		return along.cross(view).length() / (along.length() * view.length());
	}

	private static void sparks(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, double base, double age, double fade) {
		for (int lane = 0; lane < SPARKS; lane++) {
			double life = (age / SPARK_TICKS + Mth.frac(lane * GOLDEN)) % 1;
			double angle = lane * GoldenRatio.ANGLE;
			Vec3 outward = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			double start = base * (SPARK_START + SPARK_START_RANGE * Mth.frac(lane * SILVER));
			double rise = SPARK_RISE * (1 + Mth.frac(lane * SILVER * GOLDEN));
			Vec3 head = outward.scale(start + life * SPARK_SPEED * SPARK_TICKS).add(0, arc(life) * rise, 0);
			Vec3 tail = head.subtract(outward.scale(SPARK_LENGTH)).subtract(0, (1 - 2 * life) * SPARK_LENGTH * SPARK_DROP, 0);
			streak(pose, buffer, eye, tail, head, SPARK_WIDTH, LightStrokes.faded(SPARK, fade * Math.sin(life * Math.PI)));
		}
	}

	private static double[] scaled(double[] values, double share) {
		double[] out = new double[values.length];
		for (int i = 0; i < values.length; i++) {
			out[i] = values[i] * share;
		}
		return out;
	}

	private static void strip(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, Vec3[] centres, double[] radii, double widthShare, double[] alpha,
		double alphaShare, int[] color) {
		LightStrokes.strip(pose, buffer, eye, centres, scaled(radii, widthShare), color, scaled(alpha, alphaShare));
	}

	private static void ray(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, Vec3 a, Vec3 b, double width, int[] color) {
		Vec3[] path = new Vec3[]{a, a.lerp(b, RAY_FADE), a.lerp(b, 1 - RAY_FADE), b};
		LightStrokes.strip(pose, buffer, eye, path, new double[]{0, width, width, 0}, color, new double[]{0, 1, 1, 0});
	}

	private static void streak(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, Vec3 tail, Vec3 head, double width, int[] color) {
		Vec3[] path = new Vec3[]{tail, tail.lerp(head, STREAK_MIDDLE), tail.lerp(head, STREAK_FULLEST), head};
		LightStrokes.strip(pose, buffer, eye, path, new double[]{0, width * STREAK_MIDDLE_WIDTH, width, 0}, color,
			new double[]{0, STREAK_MIDDLE_ALPHA, 1, 0});
	}
}
