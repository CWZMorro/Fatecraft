package io.github.cwzmorro.fatecraft.client.mixin;

import io.github.cwzmorro.fatecraft.client.weapon.ThrowPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Kanshou and Bakuya's throw, applied after setupAnim and mirrored for left-handed players.
@Mixin(HumanoidModel.class)
abstract class EmiyaThrowArmPoseMixin {
	@Shadow public ModelPart rightArm;
	@Shadow public ModelPart leftArm;
	@Shadow public ModelPart head;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void fatecraft$throw(HumanoidRenderState state, CallbackInfo ci) {
		float age = ThrowPose.age(state);
		if (!ThrowPose.active(age)) {
			return;
		}
		boolean right = state.mainArm == HumanoidArm.RIGHT;
		this.fatecraft$pose(this.rightArm, age, right, 1);
		this.fatecraft$pose(this.leftArm, age, !right, -1);
	}

	@Unique
	private void fatecraft$pose(ModelPart arm, float age, boolean main, int side) {
		arm.xRot = (float) Math.toRadians(ThrowPose.arm(age, main, 0, false)) + this.head.xRot;
		arm.yRot = side * (float) Math.toRadians(ThrowPose.arm(age, main, 1, false));
		arm.zRot = side * (float) Math.toRadians(ThrowPose.arm(age, main, 2, false));
	}
}
