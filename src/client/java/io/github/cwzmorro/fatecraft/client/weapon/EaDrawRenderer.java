package io.github.cwzmorro.fatecraft.client.weapon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.cwzmorro.fatecraft.client.effect.GoldenGate;
import io.github.cwzmorro.fatecraft.client.effect.LightTypes;
import io.github.cwzmorro.fatecraft.weapon.EaDraw;
import io.github.cwzmorro.fatecraft.weapon.EaDrawEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

/** Ea being drawn: it rises out of a golden gate on the spot, handle up and slowly turning, and waits there to be taken. */
public final class EaDrawRenderer extends EntityRenderer<EaDrawEntity, EaDrawRenderer.State> {
	// Ea hangs upside down: its grip starts this far below the gate (the pommel just under it) and stops this high above it.
	private static final double GRIP_START = -0.5;
	private static final double GRIP_END = 1.8;
	private static final float TURN_PER_TICK = 1.5F;
	private static final double GATE_RADIUS = 1.6;
	private static final double GATE_OPENING_TICKS = 15.0;
	private static final double GATE_LIFT = 0.05;

	private final ItemModelResolver itemModels;

	public EaDrawRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModels = context.getItemModelResolver();
	}

	public static final class State extends EntityRenderState {
		final ItemStackRenderState ea = new ItemStackRenderState();
		double risen;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(EaDrawEntity entity, State state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		this.itemModels.updateForNonLiving(state.ea, entity.ea(), ItemDisplayContext.NONE, entity);
		state.risen = entity.risen(partialTick);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		double opened = Math.clamp((state.ageInTicks - EaDraw.GATE_START) / GATE_OPENING_TICKS, 0.0, 1.0);
		if (opened > 0) {
			Vec3 eye = camera.pos.subtract(state.x, state.y, state.z);
			Vec3 centre = new Vec3(0, GATE_LIFT, 0);
			LightTypes.submit(collector, poseStack, GoldenGate.TYPE,
				(pose, buffer) -> GoldenGate.draw(pose, buffer, eye, centre, Vec3.Y_AXIS, GATE_RADIUS * opened, state.ageInTicks, opened));
		}
		if (state.risen > 0 && !state.ea.isEmpty()) {
			poseStack.pushPose();
			poseStack.translate(0, GRIP_START + state.risen * (GRIP_END - GRIP_START), 0);
			poseStack.mulPose(Axis.YP.rotationDegrees(state.ageInTicks * TURN_PER_TICK));
			poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
			state.ea.submit(poseStack, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, state.outlineColor);
			poseStack.popPose();
		}
		super.submit(state, poseStack, collector, camera);
	}
}
