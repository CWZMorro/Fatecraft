package io.github.cwzmorro.fatecraft.mixin;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.fatecraft.artoria.Avalon;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// At the press itself, so other mods' direct presses are refused too, not only the ability keys.
@Mixin(Abilities.class)
abstract class AvalonAbilitiesMixin {
	@Inject(method = "press", at = @At("HEAD"), cancellable = true, remap = false)
	private static void fatecraft$avalonNoAbility(LivingEntity entity, Identifier id, int mode, boolean sneaking, CallbackInfo ci) {
		if (Avalon.active(entity)) {
			ci.cancel();
		}
	}
}
