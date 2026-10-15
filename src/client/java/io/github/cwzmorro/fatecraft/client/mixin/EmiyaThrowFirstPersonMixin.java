package io.github.cwzmorro.fatecraft.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.cwzmorro.fatecraft.client.weapon.ThrowPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Kanshou and Bakuya's throw in first person: the same shoulder timeline on vanilla's held or empty arm.
@Mixin(ItemInHandRenderer.class)
abstract class EmiyaThrowFirstPersonMixin {
	// The first-person shoulder the arm turns about, in the hand's view space (x mirrored for the left arm).
	@Unique
	private static final float fatecraft$shoulderX = 0.55F;
	@Unique
	private static final float fatecraft$shoulderY = -0.6F;
	@Unique
	private static final float fatecraft$shoulderZ = -0.5F;
	@Unique
	private static final float fatecraft$armScale = 0.5F;
	@Unique
	private static final float fatecraft$handX = 0.3125F;
	@Unique
	private static final float fatecraft$handY = -0.75F;

	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void fatecraft$throw(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
		float attack, ItemStack stack, float equip, PoseStack poses, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		float age = ThrowPose.age(player, partialTick);
		if (!ThrowPose.active(age)) {
			return;
		}
		poses.pushPose();
		boolean main = hand == InteractionHand.MAIN_HAND;
		HumanoidArm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
		int side = arm == HumanoidArm.RIGHT ? 1 : -1;
		poses.translate(side * fatecraft$shoulderX, fatecraft$shoulderY, fatecraft$shoulderZ);
		// The model's turns in the view's frame (its x and y axes flipped), from the arm held forward.
		poses.mulPose(new Quaternionf().rotationZYX(side * (float) Math.toRadians(ThrowPose.arm(age, main, 2, true)),
			-side * (float) Math.toRadians(ThrowPose.arm(age, main, 1, true)),
			-(float) Math.toRadians(ThrowPose.arm(age, main, 0, true) - ThrowPose.FORWARD_X)));
		poses.translate(-side * fatecraft$shoulderX, -fatecraft$shoulderY, -fatecraft$shoulderZ);
		// Vanilla draws no empty offhand, so its follow-through after Bakuya leaves it is drawn here.
		if (!main && stack.isEmpty() && !player.isInvisible()) {
			ci.cancel();
			poses.pushPose();
			poses.translate(side * fatecraft$shoulderX, fatecraft$shoulderY, fatecraft$shoulderZ);
			poses.scale(fatecraft$armScale, fatecraft$armScale, fatecraft$armScale);
			poses.translate(side * fatecraft$handX, fatecraft$handY, 0);
			AvatarRenderer<AbstractClientPlayer> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getPlayerRenderer(player);
			Identifier skin = player.getSkin().body().texturePath();
			if (arm == HumanoidArm.RIGHT) {
				renderer.renderRightHand(poses, collector, light, skin, player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
			} else {
				renderer.renderLeftHand(poses, collector, light, skin, player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
			}
			poses.popPose();
			poses.popPose();
		}
	}

	@Inject(method = "renderArmWithItem", at = @At("RETURN"))
	private void fatecraft$endThrow(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
		float attack, ItemStack stack, float equip, PoseStack poses, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		if (ThrowPose.active(ThrowPose.age(player, partialTick))) {
			poses.popPose();
		}
	}
}
