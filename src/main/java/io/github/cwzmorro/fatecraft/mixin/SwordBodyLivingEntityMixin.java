package io.github.cwzmorro.fatecraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.cwzmorro.fatecraft.trace.SwordBody;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Sword Body's damage reduction, after armour and enchantments; Fabric has no event that changes the amount.
@Mixin(LivingEntity.class)
abstract class SwordBodyLivingEntityMixin {
	@ModifyReturnValue(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"))
	private float fatecraft$swordBodyReduction(float damage, DamageSource source) {
		return SwordBody.reduced((LivingEntity) (Object) this, source, damage);
	}
}
