package io.github.cwzmorro.fatecraft.mixin;

import io.github.cwzmorro.fatecraft.trace.Projections;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// A projection wears one use per hit or mined block, even when its base item has no durability.
@Mixin(ItemStack.class)
public abstract class ProjectionWearMixin {
	@Unique
	private static final int fatecraft$useWear = 1;

	@Inject(method = "hurtEnemy", at = @At("HEAD"), cancellable = true)
	private void fatecraft$hit(LivingEntity target, LivingEntity attacker, CallbackInfoReturnable<Boolean> cir) {
		ItemStack stack = (ItemStack) (Object) this;
		if (Projections.isProjection(stack)) {
			stack.hurtAndBreak(fatecraft$useWear, attacker, EquipmentSlot.MAINHAND);
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "mineBlock", at = @At("HEAD"), cancellable = true)
	private void fatecraft$mine(Level level, BlockState state, BlockPos pos, Player player, CallbackInfo ci) {
		ItemStack stack = (ItemStack) (Object) this;
		if (Projections.isProjection(stack)) {
			// As vanilla: a block that breaks instantly does not wear the item.
			if (state.getDestroySpeed(level, pos) != 0.0F) {
				stack.hurtAndBreak(fatecraft$useWear, player, EquipmentSlot.MAINHAND);
			}
			ci.cancel();
		}
	}
}
