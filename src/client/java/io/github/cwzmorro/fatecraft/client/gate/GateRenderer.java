package io.github.cwzmorro.fatecraft.client.gate;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.cwzmorro.fatecraft.client.effect.GateRipple;
import io.github.cwzmorro.fatecraft.client.effect.LightTypes;
import io.github.cwzmorro.fatecraft.gate.GateEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A gate of the Gate of Babylon, facing where it throws and sized to its item, which comes out point first until half is
 * out ({@link PlaneClip} cuts off what is still behind it).
 */
public final class GateRenderer extends EntityRenderer<GateEntity, GateRenderer.State> {
	private static final double LIGHT_REACH = 1.6;
	private static final double RADIUS_PER_LENGTH = 1.45 / 1.35;

	// A gate's radius never looks smaller than this share of its distance (about 0.02 rad, some 15 pixels at 1080p), so far gates keep a visible glow.
	private static final double MIN_APPARENT_RADIUS = 0.02;

	private final ItemModelResolver itemModels;

	public GateRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModels = context.getItemModelResolver();
	}

	public static final class State extends EntityRenderState {
		final TreasureDrawing.Look item = new TreasureDrawing.Look();
		Vec3 facing = Vec3.ZERO;
		float scale;
		double length;
		int opensAt;
		int closesAt;
		long seed;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(GateEntity gate, State state, float partialTick) {
		super.extractRenderState(gate, state, partialTick);
		TreasureDrawing.extract(this.itemModels, state.item, gate.item(), gate, partialTick);
		state.facing = gate.getViewVector(partialTick);
		state.scale = gate.scale();
		if (!state.item.isEmpty()) {
			gate.setDrawnLength(state.item.length() * state.scale);
		}
		state.length = gate.drawnLength();
		state.opensAt = gate.opensAt();
		state.closesAt = gate.closesAt();
		state.seed = gate.getUUID().getLeastSignificantBits();
	}

	@Override
	protected AABB getBoundingBoxForCulling(GateEntity gate) {
		double near = TreasureDrawing.LONGEST * gate.scale() * (RADIUS_PER_LENGTH * LIGHT_REACH + 1);
		double far = MIN_APPARENT_RADIUS * gate.renderRange() * LIGHT_REACH;
		return gate.getBoundingBox().inflate(Math.max(near, far));
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		double open = state.ageInTicks - state.opensAt;
		if (open <= 0) {
			return;
		}
		double opened = GateRipple.opened(state.ageInTicks, state.opensAt, GateEntity.OPEN_TICKS, state.closesAt, GateEntity.CLOSE_TICKS);
		Vec3 toCamera = camera.pos.subtract(state.x, state.y, state.z);
		if (opened > 0) {
			double distance = toCamera.length();
			double radius = Math.max(RADIUS_PER_LENGTH * state.length, MIN_APPARENT_RADIUS * distance) * opened;
			int segments = GateRipple.segments(radius, distance);
			LightTypes.submit(collector, poseStack, GateRipple.UNDER_TYPE,
				(pose, buffer) -> GateRipple.drawUnder(pose, buffer, state.facing, radius, state.ageInTicks, opened, state.seed, segments));
			LightTypes.submit(collector, poseStack, GateRipple.TYPE,
				(pose, buffer) -> GateRipple.draw(pose, buffer, toCamera, state.facing, radius, state.ageInTicks, opened, state.seed, segments));
		}
		if (!state.item.isEmpty()) {
			// Out point first until half of it is out; nothing of it shows behind the gate.
			double out = Math.clamp(open / GateEntity.EMERGE_TICKS, 0.0, 1.0);
			PlaneClip clip = new PlaneClip(collector, poseStack.last(), state.facing);
			poseStack.pushPose();
			Vec3 centre = state.facing.scale(state.length / 2 * (out - 1));
			poseStack.translate(centre.x, centre.y, centre.z);
			TreasureDrawing.submit(poseStack, clip, state.item, state.facing, toCamera.subtract(centre), state.scale, state.outlineColor, camera);
			poseStack.popPose();
		}
		super.submit(state, poseStack, collector, camera);
	}
}
