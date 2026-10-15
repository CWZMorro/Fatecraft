package io.github.cwzmorro.fatecraft.client.weapon;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.cwzmorro.fatecraft.client.gate.TreasureDrawing;
import io.github.cwzmorro.fatecraft.weapon.BowDraw;
import io.github.cwzmorro.fatecraft.weapon.EmiyaBowItem;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

/** EMIYA's bow drawn with a sword nocked: its draw feeds the held pose; the string and nock follow the mesh's {@link BowRig}. */
public final class BowPose {
	// A nocked sword points along the arrow's way, its thin face towards the grip, even a single-edged one.
	private static final Vec3 NOCKED_FACING = new Vec3(-1, 0, 0);
	private static final Vec3 NOCKED_FACE = Vec3.Z_AXIS;

	private BowPose() {
	}

	public static void register() {
		HeldPose.register((stack, time, pose) -> {
			BowDraw draw = stack.get(EmiyaBowItem.BOW_DRAW);
			if (draw != null) {
				pose.nock(draw.fraction(time), draw.sword());
			}
		});
	}

	static void submitNocked(PoseStack poses, SubmitNodeCollector collector, TreasureDrawing.Look sword, int outline) {
		TreasureDrawing.submitFlat(poses, collector, sword, NOCKED_FACING, NOCKED_FACE, 1, outline);
	}
}
