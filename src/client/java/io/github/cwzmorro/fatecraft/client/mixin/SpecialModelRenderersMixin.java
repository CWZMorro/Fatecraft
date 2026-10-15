package io.github.cwzmorro.fatecraft.client.mixin;

import com.mojang.serialization.MapCodec;
import io.github.cwzmorro.fatecraft.client.weapon.WeaponMeshRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Adds the weapon mesh renderer to vanilla's special item model types; Fabric API has no registry for them.
@Mixin(SpecialModelRenderers.class)
abstract class SpecialModelRenderersMixin {
	@Shadow
	@Final
	private static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends SpecialModelRenderer.Unbaked<?>>> ID_MAPPER;

	@Inject(method = "bootstrap", at = @At("TAIL"))
	private static void fatecraft$registerWeaponMesh(CallbackInfo ci) {
		ID_MAPPER.put(WeaponMeshRenderer.TYPE, WeaponMeshRenderer.Unbaked.MAP_CODEC);
	}
}
