package io.github.cwzmorro.fatecraft.client.mixin;

import io.github.cwzmorro.fatecraft.client.ubw.UbwAtmosphere;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Hazes the distance with the field's dust while the camera is inside a marble.
@Mixin(FogRenderer.class)
abstract class UbwFogMixin {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void fatecraft$marbleDust(Camera camera, int renderDistance, DeltaTracker deltaTracker, float darkening, ClientLevel level,
		CallbackInfoReturnable<FogData> cir) {
		UbwAtmosphere.haze(cir.getReturnValue(), deltaTracker.getGameTimeDeltaPartialTick(false));
	}
}
