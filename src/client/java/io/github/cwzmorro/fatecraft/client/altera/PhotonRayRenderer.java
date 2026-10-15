package io.github.cwzmorro.fatecraft.client.altera;

import static io.github.cwzmorro.fatecraft.client.altera.PhotonRayPalette.HIGHLIGHT;
import static io.github.cwzmorro.fatecraft.client.altera.PhotonRayPalette.INNER_BAND;
import static io.github.cwzmorro.fatecraft.client.altera.PhotonRayPalette.MOTE_HALO;
import static io.github.cwzmorro.fatecraft.client.altera.PhotonRayPalette.OUTER_BAND;
import static io.github.cwzmorro.fatecraft.client.altera.PhotonRayPalette.WHITE;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.altera.PhotonRayEntity;
import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import io.github.cwzmorro.fatecraft.client.effect.LightTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Photon Ray's cone: narrow and faint while charging, it widens and brightens, then flashes into full form when release can fire. */
public final class PhotonRayRenderer extends EntityRenderer<PhotonRayEntity, PhotonRayRenderer.State> {
	private static final double NOSE_RADIUS = 0.0;
	private static final double TIP_TAPER_SHARE = 0.25;
	private static final int POINTS = 64;
	private static final int SPIRALS = 18;
	private static final double TURNS = 0.85;
	// Each strand leaves fading copies over the arc it swept in the last share of a tick, so the turn reads as a whirl rather than a strobe.
	private static final int SMEAR_COPIES = 3;
	private static final double SMEAR_TICK_SHARE = 1.0 / 3;
	// The strands are uneven in brightness and spacing, so a fast turn can't alias into a pattern standing still or running backwards.
	private static final double STRAND_LEAST_GLOW = 0.35;
	private static final double STRAND_JITTER = 0.8;
	private static final double HASH_MIDDLE = 0.5;
	private static final int GLOW_SALT = 0;
	private static final int JITTER_SALT = 1;
	private static final double CHARGE_LEAST_FORM = 0.45;
	private static final double CHARGE_MOST_FORM = 0.8;
	private static final double CHARGE_LEAST_GLOW = 0.3;
	private static final double CHARGE_MOST_GLOW = 0.6;
	private static final float FLASH_TICKS = 8;
	private static final double FLASH_SWELL = 0.35;
	private static final double FLASH_CORE = 2.0;
	private static final double FLASH_RING_WIDTH = 0.25;
	private static final int RING_POINTS = 48;
	// Strands fade out nearer the camera than this (blocks), so her own first-person view isn't washed white.
	private static final double NEAR_FADE = 2.0;
	private static final double NEAR_HIDDEN = 0.6;
	private static final double STROKE_WIDTH = 0.18;
	private static final double HALO_WIDTH = 3.0;
	private static final double CORE_WIDTH = 0.3;
	private static final double TAIL_FADE = 0.18;
	private static final double INNER_SPIRAL = 0.65;
	private static final double HIGHLIGHT_LEAST_GLOW = 0.7;
	private static final double CORE_FROM_CHEST = 0.5;
	private static final double CORE_LINE = 0.09;
	private static final double CORE_GLOW_WIDTH = 4.0;
	private static final double CORE_FULL_FROM = 0.25;
	private static final double CORE_FULL_TO = 0.75;
	private static final int SPEED_LINES = 22;
	private static final int SPARKLES = 14;
	private static final double SPEED_LENGTH = 0.6;
	private static final double SPEED_WIDTH = 0.07;
	private static final double STROKE_MIDDLE = 0.5;
	private static final double MOTE_SIZE = 0.11;
	private static final double MOTE_DRIFT = 0.017;
	private static final int MOTE_STRAND_STEP = 5;
	private static final int MOTE_SALT = 2;
	private static final double MOTE_BRIGHT_SHARE = 0.75;
	private static final double MOTE_BRIGHT_SCALE = 2.0;
	private static final double MOTE_DIM = 0.55;
	private static final double MOTE_CORE_SHARE = 0.35;
	private static final int MOTE_SEGMENTS = 16;
	private static final double ROTATION_SPEED = 0.03;
	private static final double BAND_EDGE_SHARE = 0.35;

	public PhotonRayRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	public static final class State extends EntityRenderState {
		Vec3 direction = Vec3.ZERO;
		double form;
		double glow;
		double flash;
		double spin;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(PhotonRayEntity ray, State state, float partialTick) {
		super.extractRenderState(ray, state, partialTick);
		LivingEntity owner = ray.owner();
		state.direction = direction(ray, owner, partialTick);
		if (owner != null) {
			Vec3 at = tip(owner.getPosition(partialTick), state.direction);
			state.x = at.x;
			state.y = at.y;
			state.z = at.z;
		}
		boolean full = ray.dashing() || ray.charged();
		double progress = ray.chargeProgress(partialTick);
		state.form = full ? 1 : Mth.lerp(progress, CHARGE_LEAST_FORM, CHARGE_MOST_FORM);
		state.glow = full ? 1 : Mth.lerp(progress, CHARGE_LEAST_GLOW, CHARGE_MOST_GLOW);
		float sinceLock = ray.ticksSinceLocked(partialTick);
		state.flash = ray.dashing() || sinceLock < 0 || sinceLock >= FLASH_TICKS ? 0 : 1 - sinceLock / FLASH_TICKS;
		state.spin = Math.toRadians(FateConfig.altera().spinDegreesPerTick());
	}

	// Culled where it is drawn, round its owner, not where the lagging synced entity is, or it vanishes mid-dash.
	@Override
	protected AABB getBoundingBoxForCulling(PhotonRayEntity ray) {
		LivingEntity owner = ray.owner();
		if (owner == null) {
			return super.getBoundingBoxForCulling(ray);
		}
		Vec3 direction = direction(ray, owner, 1);
		Vec3 tip = tip(owner.position(), direction);
		return new AABB(tip, tip.subtract(direction.scale(PhotonRayEntity.coneLength()))).inflate(PhotonRayEntity.coneRadius() * (1 + FLASH_SWELL));
	}

	private static Vec3 direction(PhotonRayEntity ray, @Nullable LivingEntity owner, float partialTick) {
		if (owner == null) {
			return ray.getLookAngle();
		}
		// Moving, she runs level along where she faces, as her own side drives it: the cone turns with her view, never a tick behind.
		return ray.dashing() ? Vec3.directionFromRotation(0, owner.getViewYRot(partialTick)) : owner.getViewVector(partialTick);
	}

	private static Vec3 tip(Vec3 feet, Vec3 direction) {
		return feet.add(0, PhotonRayEntity.CHEST_HEIGHT, 0).add(direction.scale(PhotonRayEntity.SWORD_AHEAD));
	}

	@Override
	public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
		Vec3 eye = camera.pos.subtract(state.x, state.y, state.z);
		LightTypes.submit(collector, poses, LightTypes.BLENDED, (pose, buffer) -> bands(pose, buffer, eye, state));
		LightTypes.submit(collector, poses, LightTypes.ADDED, (pose, buffer) -> light(pose, buffer, eye, state));
		super.submit(state, poses, collector, camera);
	}

	private static void strand(State state, Vec3 eye, Vec3 u, Vec3 v, int spiral, int copy, Vec3[] points, double[] alpha) {
		double glow = strandGlow(spiral);
		double fade = 1 - copy / (SMEAR_COPIES + 1.0);
		for (int i = 0; i < POINTS; i++) {
			double t = i / (double) (POINTS - 1);
			points[i] = strandPoint(state, u, v, spiral, t, copy);
			alpha[i] = Math.min(1, t / TAIL_FADE) * t * state.glow * glow * near(points[i], eye) * fade;
		}
	}

	private static Vec3 strandPoint(State state, Vec3 u, Vec3 v, int spiral, double t, int copy) {
		double skirt = PhotonRayEntity.coneRadius() * state.form;
		double start = (spiral + (LightStrokes.hash(spiral, JITTER_SALT) - HASH_MIDDLE) * STRAND_JITTER) * Mth.TWO_PI / SPIRALS;
		double smear = state.spin * SMEAR_TICK_SHARE / SMEAR_COPIES;
		double radius = Mth.lerp(t, skirt, NOSE_RADIUS) * (inner(spiral) ? INNER_SPIRAL : 1);
		double angle = start + t * TURNS * Mth.TWO_PI - state.ageInTicks * state.spin + copy * smear;
		return state.direction.scale((t - 1) * PhotonRayEntity.coneLength())
			.add(u.scale(Math.cos(angle) * radius)).add(v.scale(Math.sin(angle) * radius));
	}

	private static double tipWidth(int point) {
		return Math.min(1, (1 - point / (double) (POINTS - 1)) / TIP_TAPER_SHARE);
	}

	private static boolean inner(int spiral) {
		return spiral % 2 != 0;
	}

	private static double strandGlow(int spiral) {
		return Mth.lerp(LightStrokes.hash(spiral, GLOW_SALT), STRAND_LEAST_GLOW, 1);
	}

	private static double near(Vec3 point, Vec3 eye) {
		return Mth.clamp((point.distanceTo(eye) - NEAR_HIDDEN) / (NEAR_FADE - NEAR_HIDDEN), 0, 1);
	}

	private static void bands(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, State state) {
		Vec3 direction = state.direction;
		Vec3 u = LightStrokes.perpendicular(direction);
		Vec3 v = direction.cross(u);
		Vec3[] points = new Vec3[POINTS];
		double[] widths = new double[POINTS];
		double[] alpha = new double[POINTS];
		double[] edge = new double[POINTS];
		for (int spiral = 0; spiral < SPIRALS; spiral++) {
			int[] colour = inner(spiral) ? INNER_BAND : OUTER_BAND;
			for (int copy = SMEAR_COPIES; copy >= 0; copy--) {
				strand(state, eye, u, v, spiral, copy, points, alpha);
				for (int i = 0; i < POINTS; i++) {
					widths[i] = STROKE_WIDTH * HALO_WIDTH * tipWidth(i);
					edge[i] = alpha[i] * BAND_EDGE_SHARE;
				}
				LightStrokes.strip(pose, buffer, eye, points, widths, colour, edge);
				for (int i = 0; i < POINTS; i++) {
					widths[i] = STROKE_WIDTH * tipWidth(i);
				}
				LightStrokes.strip(pose, buffer, eye, points, widths, colour, alpha);
			}
		}
		double skirt = PhotonRayEntity.coneRadius() * state.form;
		int[] blue = LightStrokes.faded(INNER_BAND, state.glow);
		for (int i = 0; i < SPEED_LINES; i++) {
			double angle = Mth.TWO_PI * i / SPEED_LINES - state.ageInTicks * ROTATION_SPEED;
			Vec3 back = direction.scale(-PhotonRayEntity.coneLength())
				.add(u.scale(Math.cos(angle) * skirt)).add(v.scale(Math.sin(angle) * skirt));
			Vec3 front = back.scale(1 - SPEED_LENGTH);
			LightStrokes.strip(pose, buffer, eye, new Vec3[]{back, back.lerp(front, STROKE_MIDDLE), front},
				new double[]{0, SPEED_WIDTH, 0}, blue, new double[]{0, 1, 0});
		}
	}

	private static void light(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, State state) {
		Vec3 direction = state.direction;
		Vec3 u = LightStrokes.perpendicular(direction);
		Vec3 v = direction.cross(u);
		float age = state.ageInTicks;
		Vec3[] points = new Vec3[POINTS];
		double[] widths = new double[POINTS];
		double[] alpha = new double[POINTS];
		for (int spiral = 0; spiral < SPIRALS; spiral++) {
			if (strandGlow(spiral) < HIGHLIGHT_LEAST_GLOW) {
				continue;
			}
			strand(state, eye, u, v, spiral, 0, points, alpha);
			for (int i = 0; i < POINTS; i++) {
				widths[i] = STROKE_WIDTH * CORE_WIDTH * (1 + FLASH_CORE * state.flash) * tipWidth(i);
			}
			LightStrokes.strip(pose, buffer, eye, points, widths, HIGHLIGHT, alpha);
		}
		Vec3 coreStart = direction.scale(CORE_FROM_CHEST - PhotonRayEntity.SWORD_AHEAD);
		Vec3[] core = {coreStart, coreStart.scale(1 - CORE_FULL_FROM), coreStart.scale(1 - CORE_FULL_TO), Vec3.ZERO};
		double width = CORE_LINE * (1 + FLASH_CORE * state.flash);
		double[] coreAlpha = new double[core.length];
		for (int i = 0; i < core.length; i++) {
			boolean end = i == 0 || i == core.length - 1;
			coreAlpha[i] = end ? 0 : state.glow * near(core[i], eye);
		}
		LightStrokes.strip(pose, buffer, eye, core, new double[]{0, width * CORE_GLOW_WIDTH, width * CORE_GLOW_WIDTH, 0},
			HIGHLIGHT, coreAlpha);
		LightStrokes.strip(pose, buffer, eye, core, new double[]{0, width, width, 0}, WHITE, coreAlpha);
		if (state.flash > 0) {
			double ringRadius = PhotonRayEntity.coneRadius() * (1 + FLASH_SWELL * (1 - state.flash));
			Vec3 skirtCentre = direction.scale(-PhotonRayEntity.coneLength());
			Vec3[] ring = new Vec3[RING_POINTS + 1];
			double[] ringWidths = new double[RING_POINTS + 1];
			for (int i = 0; i <= RING_POINTS; i++) {
				ring[i] = LightStrokes.round(skirtCentre, ringRadius, u, v, Mth.TWO_PI * i / RING_POINTS);
				ringWidths[i] = FLASH_RING_WIDTH;
			}
			LightStrokes.strip(pose, buffer, eye, ring, ringWidths, LightStrokes.faded(WHITE, state.flash));
		}
		for (int i = 0; i < SPARKLES; i++) {
			double t = (i / (double) SPARKLES + age * MOTE_DRIFT) % 1;
			Vec3 at = strandPoint(state, u, v, i * MOTE_STRAND_STEP % SPIRALS, t, 0);
			boolean bright = LightStrokes.hash(i, MOTE_SALT) > MOTE_BRIGHT_SHARE;
			// Shrinks and fades, with the strands, close to the camera: small in her own first-person view.
			double closeness = near(at, eye);
			double size = MOTE_SIZE * (bright ? MOTE_BRIGHT_SCALE : 1) * closeness;
			double moteAlpha = state.glow * Math.min(1, t / TAIL_FADE) * closeness * (bright ? 1 : MOTE_DIM);
			LightStrokes.facingDisc(pose, buffer, eye, at, size, MOTE_HALO, moteAlpha, MOTE_SEGMENTS);
			LightStrokes.facingDisc(pose, buffer, eye, at, size * MOTE_CORE_SHARE, HIGHLIGHT, moteAlpha, MOTE_SEGMENTS);
		}
	}
}
