package io.github.cwzmorro.fatecraft.mixin;

import io.github.cwzmorro.fatecraft.artoria.Avalon;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Move packets set the position directly, bypassing Entity.move; only looking and letting go of Avalon get through.
@Mixin(ServerGamePacketListenerImpl.class)
abstract class AvalonPacketsMixin {
	@Shadow public ServerPlayer player;

	@Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
	private void fatecraft$avalonAnchor(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
		if (Avalon.active(this.player)) {
			Vec3 anchor = this.player.getAttachedOrElse(Avalon.ANCHOR, this.player.position());
			this.player.connection.teleport(anchor.x, anchor.y, anchor.z,
				packet.getYRot(this.player.getYRot()), packet.getXRot(this.player.getXRot()));
			ci.cancel();
		}
	}

	@Inject(method = {"handleUseItem", "handleUseItemOn", "handleInteract", "handleContainerClick", "handleContainerButtonClick"},
		at = @At("HEAD"), cancellable = true)
	private void fatecraft$avalonOnlyBlock(CallbackInfo ci) {
		if (Avalon.active(this.player)) {
			ci.cancel();
		}
	}

	@Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
	private void fatecraft$avalonNoDigOrDrop(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
		if (Avalon.active(this.player) && packet.getAction() != ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
			ci.cancel();
		}
	}
}
