package io.github.cwzmorro.fatecraft.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.cwzmorro.fatecraft.client.trace.SwordBodyEffect;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Sword Body's blades on the empty first-person arm, drawn right after vanilla draws the arm, in its frame.
@Mixin(ItemInHandRenderer.class)
abstract class SwordBodyFirstPersonMixin {
	@Inject(method = "renderPlayerArm", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
		target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V"))
	private void fatecraft$rightArm(PoseStack poses, SubmitNodeCollector collector, int light, float inverseArmHeight, float attack, HumanoidArm arm,
		CallbackInfo ci) {
		SwordBodyEffect.submitFirstPersonArm(poses, collector, HumanoidArm.RIGHT);
	}

	@Inject(method = "renderPlayerArm", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
		target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V"))
	private void fatecraft$leftArm(PoseStack poses, SubmitNodeCollector collector, int light, float inverseArmHeight, float attack, HumanoidArm arm,
		CallbackInfo ci) {
		SwordBodyEffect.submitFirstPersonArm(poses, collector, HumanoidArm.LEFT);
	}
}
