package io.github.cwzmorro.fatecraft.client.mixin;

import io.github.cwzmorro.fatecraft.client.weapon.ThrowPose;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Keeps Kanshou and Bakuya's throw age on the render state for the arm pose.
@Mixin(AvatarRenderer.class)
abstract class EmiyaThrowStateMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void fatecraft$throw(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		ThrowPose.remember(state, ThrowPose.age(entity, partialTick));
	}
}
