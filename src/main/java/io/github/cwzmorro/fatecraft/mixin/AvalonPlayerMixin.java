package io.github.cwzmorro.fatecraft.mixin;

import io.github.cwzmorro.fatecraft.artoria.Avalon;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// At the attack itself, so other mods' direct melee calls are refused too.
@Mixin(Player.class)
abstract class AvalonPlayerMixin {
	@Inject(method = "attack", at = @At("HEAD"), cancellable = true)
	private void fatecraft$avalonNoAttack(Entity target, CallbackInfo ci) {
		if (Avalon.active((Player) (Object) this)) {
			ci.cancel();
		}
	}
}
