package io.github.cwzmorro.fatecraft.client.gate;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.cwzmorro.alayacore.thrown.ThrownWeaponEntity;
import io.github.cwzmorro.alayacore.util.HeldInFront;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** A thrown weapon: the item itself, point first along its flight, as it was in its gate ({@link TreasureDrawing}). */
public final class TreasureRenderer extends EntityRenderer<ThrownWeaponEntity, TreasureRenderer.State> {
	// A hand-thrown weapon spins flat in its blade's plane like a disc; a pair turns opposite ways, each banked to its own side.
	private static final float SPIN_DEGREES_PER_TICK = 63;
	private static final float BANK_DEGREES = 15;
	private static final FormingLook WHOLE = new FormingLook() {
		@Override
		public float fill(float progress) {
			return 1;
		}

		@Override
		public void submit(PoseStack poses, SubmitNodeCollector collector, TreasureDrawing.Look look, Vec3 facing, Vec3 eye, double scale, float progress) {
		}
	};
	private static FormingLook formingLook = WHOLE;
	private final ItemModelResolver itemModels;

	public interface FormingLook {
		float fill(float progress);

		void submit(PoseStack poses, SubmitNodeCollector collector, TreasureDrawing.Look look, Vec3 facing, Vec3 eye, double scale, float progress);
	}

	public TreasureRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModels = context.getItemModelResolver();
	}

	public static void forming(FormingLook look) {
		formingLook = look;
	}

	public static final class State extends EntityRenderState {
		final TreasureDrawing.Look item = new TreasureDrawing.Look();
		Vec3 facing = Vec3.ZERO;
		float scale;
		float formation;
		boolean forming;
		int throwSide;
		float throwAge;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ThrownWeaponEntity weapon, State state, float partialTick) {
		super.extractRenderState(weapon, state, partialTick);
		TreasureDrawing.extract(this.itemModels, state.item, weapon.item(), weapon, partialTick);
		state.scale = weapon.scale();
		state.throwSide = weapon.throwSide();
		state.throwAge = weapon.throwAge(partialTick);
		state.forming = weapon.formationRemaining() > 0;
		state.formation = weapon.formation(partialTick);
		state.facing = Vec3.directionFromRotation(weapon.getXRot(partialTick), weapon.getYRot(partialTick));
	}

	@Override
	protected AABB getBoundingBoxForCulling(ThrownWeaponEntity weapon) {
		return weapon.getBoundingBox().inflate(TreasureDrawing.LONGEST * weapon.scale());
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.item.isEmpty()) {
			poseStack.pushPose();
			if (state.throwSide != 0) {
				Vec3 side = HeldInFront.right(state.facing);
				Vec3 flat = side.cross(state.facing);
				poseStack.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(state.throwSide * BANK_DEGREES),
					(float) state.facing.x, (float) state.facing.y, (float) state.facing.z));
				poseStack.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(state.throwSide * state.throwAge * SPIN_DEGREES_PER_TICK),
					(float) flat.x, (float) flat.y, (float) flat.z));
				TreasureDrawing.submitFlat(poseStack, collector, state.item, state.facing, flat, state.scale, state.outlineColor);
			} else {
				Vec3 eye = camera.pos.subtract(state.x, state.y, state.z);
				if (state.forming) {
					formingLook.submit(poseStack, collector, state.item, state.facing, eye, state.scale, state.formation);
				}
				float fill = state.forming ? formingLook.fill(state.formation) : 1;
				if (fill > 0) {
					poseStack.pushPose();
					Vec3 shift = state.facing.scale(state.item.length() * state.scale * (fill - 1) / 2);
					poseStack.translate(shift.x, shift.y, shift.z);
					Quaternionf along = new Quaternionf().rotationTo(1, 0, 0,
						(float) state.facing.x, (float) state.facing.y, (float) state.facing.z);
					poseStack.mulPose(along);
					poseStack.scale(fill, 1, 1);
					poseStack.mulPose(new Quaternionf(along).conjugate());
					TreasureDrawing.submit(poseStack, collector, state.item, state.facing, eye, state.scale, state.outlineColor, camera);
					poseStack.popPose();
				}
			}
			poseStack.popPose();
		}
		super.submit(state, poseStack, collector, camera);
	}
}
