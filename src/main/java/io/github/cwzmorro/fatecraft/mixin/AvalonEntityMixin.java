package io.github.cwzmorro.fatecraft.mixin;

import io.github.cwzmorro.fatecraft.artoria.Avalon;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class AvalonEntityMixin {
	@Inject(method = "move", at = @At("HEAD"), cancellable = true)
	private void fatecraft$avalonStill(MoverType type, Vec3 movement, CallbackInfo ci) {
		if ((Object) this instanceof LivingEntity living && Avalon.active(living)) {
			living.setDeltaMovement(Vec3.ZERO);
			ci.cancel();
		}
	}
}
