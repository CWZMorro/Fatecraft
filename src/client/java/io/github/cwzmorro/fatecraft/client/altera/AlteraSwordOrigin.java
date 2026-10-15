package io.github.cwzmorro.fatecraft.client.altera;

import io.github.cwzmorro.fatecraft.altera.Altera;
import io.github.cwzmorro.fatecraft.altera.WeaponFlip;
import io.github.cwzmorro.fatecraft.client.weapon.HeldPose;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

/** Where Altera's held sword is drawn (not the server's chest-height attack origin), and its tip-down turn. */
public final class AlteraSwordOrigin {
	// First-person grip positions are measured in the fixed 70-degree hand projection, before and after the raised flip.
	private static final double HAND_FOV = 70;
	private static final double FORWARD = 0.8;
	private static final double SIDE = 0.37;
	private static final double LOWERED = -0.52;
	private static final double RAISED = -0.13;

	private AlteraSwordOrigin() {
	}

	public static void register() {
		HeldPose.register((stack, time, pose) -> {
			WeaponFlip flip = stack.get(Altera.FLIP);
			if (flip != null) {
				pose.tipDown(flip.flip(time));
			}
		});
	}

	static Vec3 firstPerson(Minecraft client, float partialTick) {
		WeaponFlip pose = client.player.getMainHandItem().get(Altera.FLIP);
		double time = client.level.getGameTime() + partialTick;
		double flip = pose == null ? 0 : pose.flip(time);
		Vec3 forward = client.player.getViewVector(partialTick);
		Vec3 right = Vec3.directionFromRotation(0, client.player.getViewYRot(partialTick) + 90);
		Vec3 up = right.cross(forward);
		double projection = Math.tan(Math.toRadians(client.options.fov().get()) / 2)
			/ Math.tan(Math.toRadians(HAND_FOV) / 2);
		int hand = client.player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
		return client.player.getEyePosition(partialTick).add(forward.scale(FORWARD))
			.add(right.scale(SIDE * hand * projection))
			.add(up.scale(Mth.lerp(flip, LOWERED, RAISED) * projection));
	}
}
