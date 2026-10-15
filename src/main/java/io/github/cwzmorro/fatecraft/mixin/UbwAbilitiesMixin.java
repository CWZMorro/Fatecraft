package io.github.cwzmorro.fatecraft.mixin;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.fatecraft.ubw.UnlimitedBladeWorks;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A repeated press keeps the running cast's mode, so pressing another firing mode of Unlimited Blade Works ends that cast first.
@Mixin(Abilities.class)
public abstract class UbwAbilitiesMixin {
	@Inject(method = "press", at = @At("HEAD"), remap = false)
	private static void fatecraft$ubwMode(LivingEntity entity, Identifier abilityId, int mode, boolean sneaking, CallbackInfo ci) {
		if (!UnlimitedBladeWorks.KEY.identifier().equals(abilityId) || !Abilities.owns(entity, abilityId)
			|| !UnlimitedBladeWorks.canSwitch(entity, mode)) {
			return;
		}
		int active = UnlimitedBladeWorks.firingMode(entity);
		if (active >= 0 && active != mode) {
			Abilities.end(entity, abilityId);
		}
	}
}
