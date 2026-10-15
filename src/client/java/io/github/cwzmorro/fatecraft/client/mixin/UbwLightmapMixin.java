package io.github.cwzmorro.fatecraft.client.mixin;

import io.github.cwzmorro.fatecraft.client.ubw.UbwAtmosphere;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Lights a marble by its own sky while the camera is inside it.
@Mixin(LightmapRenderStateExtractor.class)
abstract class UbwLightmapMixin {
	@Inject(method = "extract", at = @At("TAIL"))
	private void fatecraft$marbleLight(LightmapRenderState state, float partialTick, CallbackInfo ci) {
		UbwAtmosphere.light(state, partialTick);
	}
}
