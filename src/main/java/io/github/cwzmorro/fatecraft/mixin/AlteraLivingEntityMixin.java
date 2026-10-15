package io.github.cwzmorro.fatecraft.mixin;

import io.github.cwzmorro.fatecraft.altera.Altera;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Natural Body's hooks; Fabric has no effect acceptance event.
@Mixin(value = LivingEntity.class, priority = 900)
abstract class AlteraLivingEntityMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void fatecraft$naturalBody(CallbackInfo ci) {
		Altera.tickPassive((LivingEntity) (Object) this);
	}

	@Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
	private void fatecraft$refuseWeakness(MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
		if (effect.is(MobEffects.WEAKNESS) && Altera.hasNaturalBody((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
