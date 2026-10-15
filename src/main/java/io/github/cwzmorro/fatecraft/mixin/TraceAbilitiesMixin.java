package io.github.cwzmorro.fatecraft.mixin;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.ability.Ability;
import io.github.cwzmorro.fatecraft.trace.Traces;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Traced swords add their Project abilities to those the servant owns.
@Mixin(Abilities.class)
public abstract class TraceAbilitiesMixin {
	@Inject(method = "owned", at = @At("RETURN"), cancellable = true, remap = false)
	private static void fatecraft$traces(LivingEntity entity, CallbackInfoReturnable<List<ResourceKey<Ability>>> cir) {
		cir.setReturnValue(Traces.owned(entity, cir.getReturnValue()));
	}
}
