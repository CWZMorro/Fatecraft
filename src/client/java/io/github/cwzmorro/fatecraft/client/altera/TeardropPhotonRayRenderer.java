package io.github.cwzmorro.fatecraft.client.altera;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.altera.AlteraConfig;
import io.github.cwzmorro.fatecraft.altera.PhotonRayEntity;
import io.github.cwzmorro.fatecraft.altera.TeardropGeometry;
import io.github.cwzmorro.fatecraft.altera.TeardropPhotonRayEntity;
import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import io.github.cwzmorro.fatecraft.client.effect.LightTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Teardrop's circles assembling one after another, the link from her sword to the sky, and the strike. */
public final class TeardropPhotonRayRenderer extends EntityRenderer<TeardropPhotonRayEntity, TeardropPhotonRayRenderer.State> {
	// Five planes along the axis; the two full-size side circles share the main seal's plane.
	private static final int CIRCLES = 5;
	private static final double PLANE_SPACING = 1;
	private static final double ROTATION = 0.004;
	private static final double SMALL_RING_ALPHA = 0.35;
	private static final double BACKGROUND_RADIUS = 3;
	private static final double BACKGROUND_PLANE = 1.8;
	private static final double BACKGROUND_ALPHA = 0.18;
	private static final double REACH_EPSILON = 0.01;
	private static final TeardropSeals.Artwork[] ARTWORK = {
		TeardropSeals.GREEN_RIM, TeardropSeals.MAIN, TeardropSeals.BLUE_DISC,
		TeardropSeals.OCTAGRAM, TeardropSeals.SMALL_RING
	};
	private static final double[] RADII = {0.88, 0.8, TeardropGeometry.OUTPUT_RING_SHARE, 0.85, 0.32};
	private static final double[] PLANES = {0.5, 0, -0.12, 1.15, -0.6};
	private static final double LINK_ARRIVES = 0.12;
	private static final double CIRCLES_START = 0.28;
	private static final double CIRCLES_READY = 0.8;
	private static final double LINK_FADE_START = 0.32;
	private static final double LINK_FADE_END = 0.7;
	private static final double LINK_WIDTH = 0.16;
	private static final double LINK_GLOW = 0.65;
	private static final double LINK_GLOW_ALPHA = 0.4;
	// In first person the link is very thin at her hand; further off it keeps its normal width.
	private static final double HAND_LINK_WIDTH = 0.006;
	private static final double HAND_LINK_GLOW = 0.025;
	private static final int MAGENTA_ORBS = 24;
	private static final int GOLD_ORBS = 12;
	private static final double ORB_ORBIT = 1.12;
	private static final double ORB_SIZE = 0.035;
	private static final double ORB_HEIGHT = 0.22;
	private static final double ORBS_START = 2;

	public TeardropPhotonRayRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	public static final class State extends EntityRenderState {
		Vec3 swordAt = Vec3.ZERO;
		boolean firstPerson;
		float progress;
		float depth;
		boolean firing;
		boolean spread;
		double firingTicks;
		double sealRadius;
		double radius;
		double impactRadius;
		final Vec3[] sealSites = new Vec3[TeardropGeometry.SEALS];
		Vec3 surface = Vec3.ZERO;
		boolean landed;
		Vec3 sky = Vec3.ZERO;
		Vec3 impact = Vec3.ZERO;
		Vec3 axis = Vec3.ZERO;
		Vec3 u = Vec3.ZERO;
		Vec3 v = Vec3.ZERO;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public boolean shouldRender(TeardropPhotonRayEntity ray, Frustum frustum, double x, double y, double z) {
		// The tiny entity box at the impact doesn't cover the sky circles or the raised sword.
		return true;
	}

	@Override
	public void extractRenderState(TeardropPhotonRayEntity ray, State state, float partialTick) {
		super.extractRenderState(ray, state, partialTick);
		AlteraConfig config = FateConfig.altera();
		int windUp = Durations.ticks(config.teardropWindUpSeconds());
		state.progress = (ray.heldTicks() + partialTick) / windUp;
		state.firing = ray.firing();
		state.depth = ray.depth();
		state.spread = ray.spread();
		state.firingTicks = Math.max(0, ray.heldTicks() + partialTick - windUp);
		state.radius = ray.beamRadius();
		state.impactRadius = state.spread ? TeardropGeometry.impactRadius(config) : state.radius;
		state.sealRadius = TeardropGeometry.sealRadius(config);
		state.sky = TeardropGeometry.sky(config, 0);
		state.axis = TeardropGeometry.axis(config);
		for (int side = -TeardropGeometry.SIDE_SEALS; side <= TeardropGeometry.SIDE_SEALS; side++) {
			state.sealSites[side + TeardropGeometry.SIDE_SEALS] = TeardropGeometry.sky(config, side);
		}
		// The circles stay put; only Condensed's beam end turns after her look.
		state.surface = ray.targetOffset(partialTick);
		Vec3 bottom = TeardropGeometry.bottom(state.sky, state.surface, state.depth);
		double length = state.sky.distanceTo(bottom);
		state.impact = state.sky.lerp(bottom, Math.min(length, ray.reach()) / length);
		// The reach was set before this tick's dig, so it is tested against the surface, not the new bottom.
		state.landed = state.impact.y <= state.surface.y + REACH_EPSILON;
		state.u = LightStrokes.perpendicular(state.axis);
		state.v = state.axis.cross(state.u);
		LivingEntity owner = ray.owner();
		if (owner != null) {
			Minecraft client = Minecraft.getInstance();
			state.firstPerson = owner == client.player && client.getCameraEntity() == owner && client.options.getCameraType().isFirstPerson();
			state.swordAt = (state.firstPerson
				? AlteraSwordOrigin.firstPerson(client, partialTick)
				: owner.getPosition(partialTick).add(0, PhotonRayEntity.CHEST_HEIGHT, 0).add(owner.getViewVector(partialTick)))
				.subtract(state.x, state.y, state.z);
		}
	}

	@Override
	public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
		Vec3 eye = camera.pos.subtract(state.x, state.y, state.z);
		LightTypes.submit(collector, poses, LightTypes.BLENDED, (pose, buffer) -> {
			draw(pose, buffer, eye, state, false);
		});
		LightTypes.submit(collector, poses, LightTypes.ADDED, (pose, buffer) -> {
			draw(pose, buffer, eye, state, true);
			circles(pose, buffer, eye, state, true);
		});
		// Blended over the beam's added glow, so the seals' red, pink and blue stay readable; terrain still hides them.
		LightTypes.submit(collector, poses, LightTypes.BLENDED_OVER,
			(pose, buffer) -> circles(pose, buffer, eye, state, false));
		super.submit(state, poses, collector, camera);
	}

	private static void circles(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, State state, boolean added) {
		double assembly = (state.progress - CIRCLES_START) / (CIRCLES_READY - CIRCLES_START);
		double background = Mth.smoothstep(Mth.clamp(assembly, 0, 1));
		TeardropSeals.draw(TeardropSeals.BACKGROUND, pose, buffer, eye,
			state.sky.add(state.axis.scale(state.sealRadius * BACKGROUND_PLANE)), state.u, state.v,
			state.sealRadius * BACKGROUND_RADIUS, -state.ageInTicks * ROTATION, background * BACKGROUND_ALPHA, added);
		for (int i = 0; i < CIRCLES; i++) {
			double growth = Mth.smoothstep(Mth.clamp(assembly * CIRCLES - i, 0.0, 1.0));
			if (growth <= 0) {
				continue;
			}
			Vec3 centre = circleCentre(state, i);
			TeardropSeals.draw(ARTWORK[i], pose, buffer, eye, centre, state.u, state.v,
				state.sealRadius * RADII[i] * growth, state.ageInTicks * ROTATION,
				growth * (i == CIRCLES - 1 ? SMALL_RING_ALPHA : 1), added,
				state.spread && state.firing && (ARTWORK[i] == TeardropSeals.MAIN || ARTWORK[i] == TeardropSeals.BLUE_DISC));
		}
		if (added) {
			double lights = Mth.smoothstep(Mth.clamp(assembly * CIRCLES - ORBS_START, 0, 1));
			orbs(pose, buffer, eye, state, state.sky, MAGENTA_ORBS, TeardropPalette.MAGENTA, lights);
		}
		double satellites = Mth.smoothstep(Mth.clamp(assembly * CIRCLES - (CIRCLES - 1), 0.0, 1.0));
		if (satellites > 0) {
			for (int side = -TeardropGeometry.SIDE_SEALS; side <= TeardropGeometry.SIDE_SEALS; side++) {
				if (side == 0) {
					continue;
				}
				Vec3 at = state.sealSites[side + TeardropGeometry.SIDE_SEALS];
				TeardropSeals.draw(TeardropSeals.SATELLITE, pose, buffer, eye, at, state.u, state.v,
					state.sealRadius * satellites, -state.ageInTicks * ROTATION, satellites, added);
				if (added) {
					orbs(pose, buffer, eye, state, at, GOLD_ORBS, TeardropPalette.GOLD, satellites);
				}
			}
		}
	}

	private static void orbs(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, State state,
		Vec3 centre, int count, int[] colour, double alpha) {
		for (int i = 0; i < count; i++) {
			double angle = i * Mth.TWO_PI / count + state.ageInTicks * ROTATION;
			Vec3 at = centre.add(state.u.scale(Math.cos(angle) * state.sealRadius * ORB_ORBIT))
				.add(state.v.scale(Math.sin(angle) * state.sealRadius * ORB_ORBIT))
				.add(state.axis.scale(Math.sin(i) * state.sealRadius * ORB_HEIGHT));
			TeardropBeam.facingDisc(pose, buffer, eye, at, state.sealRadius * ORB_SIZE, colour, alpha);
		}
	}

	static Vec3 circleCentre(State state, int index) {
		return state.sky.add(state.axis.scale(PLANES[index] * state.sealRadius * PLANE_SPACING));
	}

	private static void draw(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, State state, boolean added) {
		Vec3 tip = state.swordAt;
		double linkAlpha = state.firing ? 0 : 1 - Mth.smoothstep(Mth.clamp(
			(state.progress - LINK_FADE_START) / (LINK_FADE_END - LINK_FADE_START), 0, 1));
		if (linkAlpha > 0) {
			Vec3 end = tip.lerp(state.sky, Mth.clamp(state.progress / LINK_ARRIVES, 0, 1));
			// Unlike a distant effect's strokes, the link is drawn right up to the held sword.
			double width = added ? LINK_GLOW : LINK_WIDTH;
			double nearWidth = state.firstPerson ? (added ? HAND_LINK_GLOW : HAND_LINK_WIDTH) : width;
			LightStrokes.stroke(pose, buffer, eye, tip, end, nearWidth, width,
				linkAlpha * (added ? LINK_GLOW_ALPHA : 1), linkAlpha * (added ? LINK_GLOW_ALPHA : 1), TeardropPalette.LINK_RED);
		}
		if (state.firing) {
			TeardropBeam.draw(pose, buffer, eye, state, added);
		}
	}
}
