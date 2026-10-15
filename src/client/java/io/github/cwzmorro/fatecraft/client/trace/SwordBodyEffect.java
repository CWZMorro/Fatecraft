package io.github.cwzmorro.fatecraft.client.trace;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import io.github.cwzmorro.fatecraft.client.effect.LightTypes;
import io.github.cwzmorro.fatecraft.client.trace.SwordBodyBlades.Facet;
import io.github.cwzmorro.fatecraft.client.trace.SwordBodyBlades.Part;
import io.github.cwzmorro.fatecraft.trace.SwordBody;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.phys.Vec3;

/**
 * Sword Body: patches of steel blades on parts of the body ({@link SwordBodyBlades}) that follow the body's yaw and the
 * limbs' swing and lower while sneaking. In first person the empty arm vanilla draws carries its own patch
 * ({@code SwordBodyFirstPersonMixin}) and the world-space body is not drawn for the local player.
 */
public final class SwordBodyEffect {
	private record Placement(Part part, boolean slim, Vec3 origin, float yaw, float swing) { }

	private static List<Placement> placements = List.of();

	private SwordBodyEffect() { }

	public static void register() {
		LevelRenderEvents.END_EXTRACTION.register(context -> {
			List<Placement> frame = new ArrayList<>();
			float partial = context.deltaTracker().getGameTimeDeltaPartialTick(false);
			Vec3 camera = context.camera().position();
			for (AbstractClientPlayer player : context.level().players()) {
				if (!SwordBody.active(player) || (context.camera().entity() == player && !context.camera().isDetached())) {
					continue;
				}
				addPlayer(frame, player, partial, camera);
			}
			placements = List.copyOf(frame);
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			PoseStack poseStack = context.poseStack();
			for (Placement placement : placements) {
				poseStack.pushPose();
				poseStack.translate(placement.origin().x, placement.origin().y, placement.origin().z);
				poseStack.mulPose(Axis.YP.rotation(-placement.yaw()));
				place(poseStack, placement.part(), placement.slim(), placement.swing());
				submit(poseStack, context.submitNodeCollector(), placement.part(), placement.slim());
				poseStack.popPose();
			}
		});
	}

	public static void submitFirstPersonArm(PoseStack poseStack, SubmitNodeCollector collector, HumanoidArm side) {
		Part arm = side == HumanoidArm.RIGHT ? Part.RIGHT_ARM : Part.LEFT_ARM;
		AbstractClientPlayer player = Minecraft.getInstance().player;
		if (player == null || !SwordBody.active(player)) {
			return;
		}
		poseStack.pushPose();
		// Vanilla's model frame is the body's turned half a turn about x (y down, z back), measured from the top of the model.
		poseStack.translate(0, VanillaHumanoid.PLAYER.height(), 0);
		poseStack.mulPose(Axis.XP.rotation(Mth.PI));
		place(poseStack, arm, slim(player), 0);
		submit(poseStack, collector, arm, slim(player));
		poseStack.popPose();
	}

	private static boolean slim(AbstractClientPlayer player) {
		return player.getSkin().model() == PlayerModelType.SLIM;
	}

	private static void addPlayer(List<Placement> frame, AbstractClientPlayer player, float partial, Vec3 camera) {
		float yaw = (float) Math.toRadians(Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot));
		Vec3 origin = player.getPosition(partial).subtract(camera).add(0, player.isCrouching() ? -VanillaHumanoid.PLAYER.sneakDrop() : 0, 0);
		float swing = (float) (Math.cos(player.walkAnimation.position(partial) * VanillaHumanoid.PLAYER.limbPhase()) * player.walkAnimation.speed(partial));
		for (Part part : Part.values()) {
			frame.add(new Placement(part, slim(player), origin, yaw, swing));
		}
	}

	private static void place(PoseStack poseStack, Part part, boolean slim, float swing) {
		poseStack.translate(part.pivot().x, part.pivot().y, part.pivot().z);
		poseStack.mulPose(Axis.XP.rotation((float) (part.swing() * swing)));
		Vec3 middle = part.middle(slim);
		poseStack.translate(middle.x, middle.y, middle.z);
	}

	private static void submit(PoseStack poseStack, SubmitNodeCollector collector, Part part, boolean slim) {
		LightTypes.submit(collector, poseStack, LightTypes.SOLID, (pose, buffer) -> {
			for (Facet facet : SwordBodyBlades.facets(part, slim)) {
				LightStrokes.quad(pose, buffer, facet.corners(), facet.colours());
			}
		});
	}
}
