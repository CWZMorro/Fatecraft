package io.github.cwzmorro.fatecraft.mixin;

import io.github.cwzmorro.fatecraft.altera.CrestOfTheStar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Crest of the Star's critical hits, even uncharged or sprinting ones; Fabric has no critical hit event.
@Mixin(Player.class)
abstract class AlteraPlayerMixin {
	@ModifyVariable(method = "attack", at = @At("STORE"), ordinal = 2)
	private boolean fatecraft$crestCritical(boolean critical) {
		return critical || CrestOfTheStar.active((Player) (Object) this);
	}
}
