package io.github.cwzmorro.fatecraft.client.artoria;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.cwzmorro.fatecraft.artoria.Avalon;
import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import io.github.cwzmorro.fatecraft.client.effect.LightTypes;
import io.github.cwzmorro.fatecraft.client.weapon.WeaponMesh;
import io.github.cwzmorro.fatecraft.client.weapon.WeaponMeshRenderer;
import io.github.cwzmorro.fatecraft.client.weapon.WeaponMeshes;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Avalon in use: the scabbard floats mouth-up in front of her, and light blue ribbons spiral round her. */
public final class AvalonEffect {
	// Where the scabbard floats, in blocks in front of her and above her feet.
	private static final double SCABBARD_REACH = 1.9;
	private static final double SCABBARD_HEIGHT = 1.35;
	private static final float SCABBARD_SCALE = 1F;
	private static final float MESH_CENTRE = 0.5F;
	private static final float SCABBARD_FLIP = (float) Math.PI;
	private static final int PRIMARY_SPIRALS = 4;
	private static final int SECONDARY_SPIRALS = 6;
	private static final int SPIRAL_POINTS = 161;
	private static final double BASE_RADIUS = 1.6;
	private static final double TOP_RADIUS = 0.4;
	private static final double SPIRAL_FLOOR = 0.15;
	private static final double SPIRAL_HEIGHT = 3.0;
	private static final double BAND_WIDTH = 0.16;
	private static final double BAND_FLATNESS = Math.toRadians(70);
	private static final double TURN_SPEED = 0.01;
	private static final double SECONDARY_SPEED = 1.1;
	private static final double SECONDARY_PHASE = Math.PI / SECONDARY_SPIRALS;
	private static final double END_FADE = 0.05;
	private static final int[] RIBBON = {191, 242, 250, 200};
	private static final Identifier MESH = BuiltInRegistries.ITEM.getKey(FateWeapons.AVALON);
	private static final Identifier TEXTURE = MESH.withPrefix(WeaponMeshRenderer.TEXTURE_FOLDER);

	private static List<Spiral> spirals = List.of();
	private static List<Sanctuary> sanctuaries = List.of();

	private AvalonEffect() {
	}

	public static void register() {
		LevelRenderEvents.END_EXTRACTION.register(context -> {
			List<Spiral> frame = new ArrayList<>();
			List<Sanctuary> walls = new ArrayList<>();
			float partial = context.deltaTracker().getGameTimeDeltaPartialTick(false);
			double ticks = context.level().getGameTime() + partial;
			WeaponMesh mesh = WeaponMeshes.get(MESH);
			for (Player player : context.level().players()) {
				if (!Avalon.active(player)) {
					continue;
				}
				Vec3 body = player.getPosition(partial).subtract(context.camera().position());
				float yaw = player.getViewYRot(partial);
				double heading = Math.toRadians(yaw);
				Vec3 forward = new Vec3(-Math.sin(heading), 0, Math.cos(heading));
				if (mesh != null) {
					walls.add(new Sanctuary(body.add(forward.scale(SCABBARD_REACH)).add(0, SCABBARD_HEIGHT, 0), yaw));
				}
				addSpirals(frame, body, PRIMARY_SPIRALS, ticks * TURN_SPEED);
				addSpirals(frame, body, SECONDARY_SPIRALS, ticks * TURN_SPEED * SECONDARY_SPEED + SECONDARY_PHASE);
			}
			spirals = List.copyOf(frame);
			sanctuaries = List.copyOf(walls);
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			List<Spiral> frame = spirals;
			List<Sanctuary> walls = sanctuaries;
			for (Sanctuary wall : walls) {
				context.poseStack().pushPose();
				context.poseStack().translate(wall.centre().x, wall.centre().y, wall.centre().z);
				context.poseStack().mulPose(new Quaternionf().rotationY((float) Math.toRadians(-wall.yaw()))
					.rotateZ(SCABBARD_FLIP));
				context.poseStack().scale(SCABBARD_SCALE, SCABBARD_SCALE, SCABBARD_SCALE);
				context.poseStack().translate(-MESH_CENTRE, -MESH_CENTRE, -MESH_CENTRE);
				WeaponMeshRenderer.submitFloating(MESH, TEXTURE, context.poseStack(), context.submitNodeCollector());
				context.poseStack().popPose();
			}
			LightTypes.submit(context.submitNodeCollector(), context.poseStack(), LightTypes.BLENDED_OVER, (pose, buffer) -> {
				for (Spiral spiral : frame) {
					ribbon(pose, buffer, spiral);
				}
			});
		});
	}

	private static void addSpirals(List<Spiral> frame, Vec3 body, int count, double turn) {
		for (int copy = 0; copy < count; copy++) {
			frame.add(spiral(body, turn + copy * Math.TAU / count));
		}
	}

	private static Spiral spiral(Vec3 body, double turn) {
		Vec3[] points = new Vec3[SPIRAL_POINTS];
		Vec3[] sides = new Vec3[SPIRAL_POINTS];
		double[] alpha = new double[SPIRAL_POINTS];
		for (int i = 0; i < SPIRAL_POINTS; i++) {
			double progress = (double) i / (SPIRAL_POINTS - 1);
			double angle = progress * Math.TAU + turn;
			double radius = BASE_RADIUS + (TOP_RADIUS - BASE_RADIUS) * progress;
			points[i] = body.add(Math.cos(angle) * radius, SPIRAL_FLOOR + SPIRAL_HEIGHT * progress,
				Math.sin(angle) * radius);
			sides[i] = new Vec3(Math.cos(angle) * Math.cos(BAND_FLATNESS), -Math.sin(BAND_FLATNESS),
				Math.sin(angle) * Math.cos(BAND_FLATNESS)).scale(BAND_WIDTH / 2);
			alpha[i] = Math.min(1, Math.min(progress, 1 - progress) / END_FADE);
		}
		return new Spiral(points, sides, alpha);
	}

	private static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, Spiral spiral) {
		for (int i = 1; i < spiral.points().length; i++) {
			int previous = i - 1;
			Vec3 before = spiral.points()[previous];
			Vec3 here = spiral.points()[i];
			int[] from = LightStrokes.faded(RIBBON, spiral.alpha()[previous]);
			int[] to = LightStrokes.faded(RIBBON, spiral.alpha()[i]);
			LightStrokes.quad(pose, buffer, new Vec3[]{before.subtract(spiral.sides()[previous]), before.add(spiral.sides()[previous]),
				here.add(spiral.sides()[i]), here.subtract(spiral.sides()[i])}, new int[][]{from, from, to, to});
		}
	}

	private record Spiral(Vec3[] points, Vec3[] sides, double[] alpha) {
	}

	private record Sanctuary(Vec3 centre, float yaw) {
	}
}
