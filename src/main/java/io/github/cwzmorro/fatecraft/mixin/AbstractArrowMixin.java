package io.github.cwzmorro.fatecraft.mixin;

import io.github.cwzmorro.fatecraft.gate.GateReturns;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Gate of Babylon arrows go back to the vault when picked up by their owner or after landing; Fabric has no event for either.
@Mixin(AbstractArrow.class)
abstract class AbstractArrowMixin {
	@Shadow
	protected abstract boolean isInGround();

	@Inject(method = "tryPickup", at = @At("HEAD"), cancellable = true)
	private void fatecraft$backToTheVault(Player player, CallbackInfoReturnable<Boolean> cir) {
		if (GateReturns.ownerPicksUp((AbstractArrow) (Object) this, player)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "tryPickup", at = @At("RETURN"))
	private void fatecraft$takenBySomeoneElse(Player player, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			GateReturns.pickedUp((AbstractArrow) (Object) this);
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void fatecraft$returnWhenLanded(CallbackInfo ci) {
		GateReturns.tickArrow((AbstractArrow) (Object) this, this.isInGround());
	}
}
