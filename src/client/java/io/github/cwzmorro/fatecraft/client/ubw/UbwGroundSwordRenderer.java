package io.github.cwzmorro.fatecraft.client.ubw;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.cwzmorro.fatecraft.client.gate.TreasureDrawing;
import io.github.cwzmorro.fatecraft.ubw.UbwGroundSwordEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A sword standing in the field's ground, its tip sunk slightly into the floor, leaning by the sword's own lean. */
public final class UbwGroundSwordRenderer extends EntityRenderer<UbwGroundSwordEntity, UbwGroundSwordRenderer.State> {
	private static final double TIP_DEPTH = 0.12;
	private static final double CULLING_REACH = 4;
	private final ItemModelResolver models;

	public UbwGroundSwordRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.models = context.getItemModelResolver();
		this.shadowRadius = 0;
	}

	public static final class State extends EntityRenderState {
		final TreasureDrawing.Look item = new TreasureDrawing.Look();
		float scale;
		Vec3 face = Vec3.Z_AXIS;
		Vec3 point = new Vec3(0, -1, 0);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(UbwGroundSwordEntity sword, State state, float partialTick) {
		super.extractRenderState(sword, state, partialTick);
		TreasureDrawing.extract(this.models, state.item, sword.item(), sword, partialTick);
		state.scale = sword.scale();
		double yaw = Math.toRadians(sword.getYRot(partialTick));
		double lean = Math.toRadians(sword.lean(partialTick));
		state.face = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		// Down, tipped toward the side at right angles to the face, so the face stays square to the blade.
		Vec3 side = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
		state.point = side.scale(Math.sin(lean)).add(0, -Math.cos(lean), 0);
	}

	@Override
	protected AABB getBoundingBoxForCulling(UbwGroundSwordEntity sword) {
		// The entity's box is tiny, so it is culled by the longest an item can be drawn.
		return sword.getBoundingBox().inflate(CULLING_REACH * sword.scale());
	}

	@Override
	public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.item.isEmpty()) {
			poses.pushPose();
			// TreasureDrawing centres the model on its extents, so the centre moves back along the blade to sink the tip into the floor.
			Vec3 centre = state.point.scale(-state.item.length() * state.scale / 2).add(0, -TIP_DEPTH, 0);
			poses.translate(centre.x, centre.y, centre.z);
			TreasureDrawing.submit(poses, collector, state.item, state.point, state.face, state.scale, state.outlineColor, camera);
			poses.popPose();
		}
		super.submit(state, poses, collector, camera);
	}
}
