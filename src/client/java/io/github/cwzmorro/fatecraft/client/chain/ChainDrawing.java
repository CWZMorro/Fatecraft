package io.github.cwzmorro.fatecraft.client.chain;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Enkidu's chains: flat links like Minecraft's chain, turned round their line to face the camera, with a spike at the head. */
final class ChainDrawing {
	private static final double LINK_STEP = 0.3;
	private static final double LINK_LENGTH = 0.2;
	private static final double LINK_WIDTH = 0.1;
	private static final double BAR = 0.025;
	private static final int GLOWS_EVERY = 3;
	private static final double PULSE = 0.28;
	private static final double PULSE_APART = 0.9;
	private static final double FAINTEST = 0.45;
	private static final double TIGHTENING_GLOW = 1.8;
	private static final int[] GOLD = {245, 196, 80, 255};
	private static final int[] GOLD_EDGE = {200, 145, 45, 255};
	private static final int[] GOLD_DARK = {120, 78, 18, 255};
	private static final int[] GOLD_LIGHT = {255, 222, 120, 255};
	private static final int[] GLOW = {255, 205, 90, 170};
	private static final Vec3 LIGHT = new Vec3(0.42, 0.86, 0.29).normalize();
	private static final double GLOW_SPREAD = 2.0;
	private static final double SPIKE_LENGTH = 0.5;
	private static final double SPIKE_WIDTH = 0.1;

	private ChainDrawing() {
	}

	static void links(PoseStack.Pose pose, VertexConsumer buffer, List<Vec3> path, double length, Vec3 camera) {
		walk(path, length, (index, at, along) -> link(pose, buffer, at, along, camera, index, false, 0.0));
	}

	static void glow(PoseStack.Pose pose, VertexConsumer buffer, List<Vec3> path, double length, Vec3 camera, double ticks, boolean tightening) {
		walk(path, length, (index, at, along) -> {
			if (index % GLOWS_EVERY == 0) {
				double wave = 0.5 + 0.5 * Math.sin(ticks * PULSE + index * PULSE_APART);
				double pulse = Mth.lerp(wave, FAINTEST, 1.0) * (tightening ? TIGHTENING_GLOW : 1.0);
				link(pose, buffer, at, along, camera, index, true, pulse);
			}
		});
	}

	private interface LinkAt {
		void link(int index, Vec3 at, Vec3 along);
	}

	private static void walk(List<Vec3> path, double length, LinkAt each) {
		int index = 0;
		double walked = LINK_LENGTH;
		double segmentStart = 0;
		for (int k = 1; k < path.size(); k++) {
			Vec3 from = path.get(k - 1);
			Vec3 segment = path.get(k).subtract(from);
			double segmentLength = segment.length();
			if (segmentLength < 1.0E-6) {
				continue;
			}
			Vec3 along = segment.scale(1 / segmentLength);
			for (; walked <= segmentStart + segmentLength && walked <= length; walked += LINK_STEP) {
				each.link(index++, from.add(along.scale(walked - segmentStart)), along);
			}
			segmentStart += segmentLength;
		}
	}

	private static void link(PoseStack.Pose pose, VertexConsumer buffer, Vec3 at, Vec3 along, Vec3 camera, int index, boolean glowing, double glow) {
		Vec3 across = along.cross(camera.subtract(at));
		across = across.lengthSqr() < 1.0E-9 ? LightStrokes.perpendicular(along) : across.normalize();
		double bar = glowing ? BAR * GLOW_SPREAD : BAR;
		int[] glowColour = LightStrokes.faded(GLOW, glow);
		if (index % 2 == 1) {
			flatBar(pose, buffer, at, along, LINK_LENGTH, across, bar, glowing ? glowColour : GOLD_EDGE);
			return;
		}
		int[] colour = glowing ? glowColour : GOLD;
		for (int sign = -1; sign <= 1; sign += 2) {
			flatBar(pose, buffer, at.add(across.scale(sign * (LINK_WIDTH - BAR))), along, LINK_LENGTH, across, bar, colour);
			flatBar(pose, buffer, at.add(along.scale(sign * (LINK_LENGTH - BAR))), across, LINK_WIDTH - 2 * BAR, along, bar, colour);
		}
	}

	private static void flatBar(PoseStack.Pose pose, VertexConsumer buffer, Vec3 centre, Vec3 axis, double half, Vec3 side, double thick, int[] rgba) {
		Vec3 length = axis.scale(half);
		Vec3 width = side.scale(thick);
		LightStrokes.quad(pose, buffer, new Vec3[]{centre.subtract(length).subtract(width), centre.add(length).subtract(width), centre.add(length).add(width),
			centre.subtract(length).add(width)}, new int[][]{rgba, rgba, rgba, rgba});
	}

	private static int[] shade(double light) {
		int[] rgba = new int[4];
		for (int k = 0; k < rgba.length; k++) {
			rgba[k] = (int) Mth.lerp(light, GOLD_DARK[k], GOLD_LIGHT[k]);
		}
		return rgba;
	}

	static void spike(PoseStack.Pose pose, VertexConsumer buffer, Vec3 tip, Vec3 along) {
		Vec3 side = LightStrokes.perpendicular(along).scale(SPIKE_WIDTH);
		Vec3 up = along.cross(LightStrokes.perpendicular(along)).scale(SPIKE_WIDTH);
		Vec3 base = tip.subtract(along.scale(SPIKE_LENGTH));
		Vec3[] corners = {base.add(side), base.add(up), base.subtract(side), base.subtract(up)};
		for (int k = 0; k < corners.length; k++) {
			Vec3 a = corners[k];
			Vec3 b = corners[(k + 1) % corners.length];
			int[] rgba = shade(Math.abs(b.subtract(a).cross(tip.subtract(a)).normalize().dot(LIGHT)));
			LightStrokes.quad(pose, buffer, new Vec3[]{a, b, tip, tip}, new int[][]{rgba, rgba, rgba, rgba});
		}
	}
}
