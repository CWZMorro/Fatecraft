package io.github.cwzmorro.fatecraft.mixin;

import io.github.cwzmorro.fatecraft.gate.GateReturns;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Gate of Babylon projectiles go back to the vault when their time is up; Fabric has no projectile tick event.
@Mixin(Projectile.class)
abstract class ProjectileMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void fatecraft$backWhenTimeIsUp(CallbackInfo ci) {
		GateReturns.tickThrown((Projectile) (Object) this);
	}
}
