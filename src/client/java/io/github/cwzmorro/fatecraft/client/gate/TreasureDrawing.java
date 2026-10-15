package io.github.cwzmorro.fatecraft.client.gate;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * How the Gate of Babylon's treasures look in their gates and in flight: the item's right-hand model times its thrower's
 * size, pointing from the grip to its far end, an edged weapon's edge turned down and any other's flat side to the camera.
 */
public final class TreasureDrawing {
	static final double LONGEST = 4.0;
	private static final float HELD_SCALE = 0.9375F;
	private static final double ARROW_POINT = 0.225;
	private static final double ARROW_TAIL = 0.675;
	// The frame vanilla holds an item in (its item-in-hand layer, the arm hanging down): the way the player faces, and up.
	private static final Vec3 HAND_FORWARD = new Vec3(0, 1, 0);
	private static final Vec3 HAND_UP = new Vec3(0, 0, 1);
	private static final double UPRIGHT = 0.3;
	private static final double EDGE_REACH = 1.5;
	private static final Vec3 DOWN = new Vec3(0, -1, 0);
	private static final Shape SQUARE = new Shape(new Vec3(1, 1, 0).normalize(), new Vec3(0, 0, 1), null, -Math.sqrt(0.5), Math.sqrt(0.5), List.of());
	private static final int OUTLINE_SLICES = 28;

	private record Shape(Vec3 axis, Vec3 face, @Nullable Vec3 edge, double near, double far, List<Edge> edges) {
		Vec3 middle() {
			return this.axis.scale((this.near + this.far) / 2);
		}
	}

	private record Edge(Vec3 start, Vec3 end) {
	}

	public record OutlineLine(Vec3 from, Vec3 to, double fromShare, double toShare) {
	}

	public static final class Look {
		TrackingItemStackRenderState item = new TrackingItemStackRenderState();
		Shape shape = SQUARE;
		@Nullable EntityRenderState arrow;
		boolean tailAnchored;

		public boolean isEmpty() {
			return this.arrow == null && this.item.isEmpty();
		}

		public double length() {
			return this.arrow != null ? ARROW_POINT + ARROW_TAIL : (this.shape.far() - this.shape.near()) * HELD_SCALE;
		}
	}

	private record Made(ItemStack stack, AbstractArrow arrow) {
	}

	// Both caches are made again in a new level.
	private static final List<Made> ARROWS = new ArrayList<>();
	private static final Map<Object, Shape> SHAPES = new HashMap<>();
	private static @Nullable Level madeIn;

	private TreasureDrawing() {
	}

	public static void extract(ItemModelResolver models, Look look, ItemStack stack, Entity holder, float partialTick) {
		if (holder.level() != madeIn) {
			ARROWS.clear();
			SHAPES.clear();
			madeIn = holder.level();
		}
		look.arrow = null;
		look.tailAnchored = stack.getOrDefault(FateWeapons.THROWN_FROM_TAIL, false);
		look.item = new TrackingItemStackRenderState();
		if (stack.getItem() instanceof ArrowItem arrowItem && arrow(arrowItem, stack, holder.level()) instanceof AbstractArrow arrow) {
			EntityRenderState state = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(arrow).createRenderState(arrow, partialTick);
			state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
			look.arrow = state;
			return;
		}
		models.updateForNonLiving(look.item, stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, holder);
		look.shape = SHAPES.computeIfAbsent(look.item.getModelIdentity(), identity -> measure(look.item));
	}

	public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Look look, Vec3 facing, Vec3 toCamera, double scale, int outlineColor,
		CameraRenderState camera) {
		poseStack.pushPose();
		if (look.arrow != null) {
			if (look.arrow instanceof ArrowRenderState arrow) {
				arrow.yRot = (float) (Mth.atan2(facing.x, facing.z) * Mth.RAD_TO_DEG);
				arrow.xRot = (float) (Mth.atan2(facing.y, facing.horizontalDistance()) * Mth.RAD_TO_DEG);
				arrow.shake = 0.0F;
			}
			Vec3 toPlace = facing.scale((ARROW_TAIL - ARROW_POINT) / 2 * scale);
			poseStack.translate(toPlace.x, toPlace.y, toPlace.z);
			poseStack.scale((float) scale, (float) scale, (float) scale);
			EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
			dispatcher.submit(look.arrow, camera, 0.0, 0.0, 0.0, poseStack, collector);
		} else {
			Shape shape = look.shape;
			if (look.tailAnchored) {
				Vec3 shift = facing.scale(look.length() * scale / 2);
				poseStack.translate(shift.x, shift.y, shift.z);
			}
			Vec3 down = across(facing, DOWN);
			if (shape.edge() != null && down != null) {
				poseStack.mulPose(turn(shape.axis(), shape.edge(), facing, down));
			} else {
				Vec3 face = across(facing, toCamera);
				poseStack.mulPose(turn(shape.axis(), shape.face(), facing, face != null ? face : LightStrokes.perpendicular(facing)));
			}
			float size = (float) (HELD_SCALE * scale);
			poseStack.scale(size, size, size);
			Vec3 middle = shape.middle();
			poseStack.translate(-middle.x, -middle.y, -middle.z);
			look.item.submit(poseStack, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, outlineColor);
		}
		poseStack.popPose();
	}

	public static void submitFlat(PoseStack poses, SubmitNodeCollector collector, Look look, Vec3 facing, Vec3 normal, double scale, int outline) {
		poses.pushPose();
		poses.mulPose(turn(look.shape.axis(), look.shape.face(), facing, normal));
		float size = (float) (HELD_SCALE * scale);
		poses.scale(size, size, size);
		Vec3 middle = look.shape.middle();
		poses.translate(-middle.x, -middle.y, -middle.z);
		look.item.submit(poses, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, outline);
		poses.popPose();
	}

	public static List<OutlineLine> outline(Look look, Vec3 facing, Vec3 eye, double scale) {
		Shape shape = look.shape;
		Vec3 face = across(facing, eye);
		Quaternionf rotation = turn(shape.axis(), shape.face(), facing, face == null ? LightStrokes.perpendicular(facing) : face);
		double size = HELD_SCALE * scale;
		List<OutlineLine> lines = new ArrayList<>(shape.edges().size());
		for (Edge edge : shape.edges()) {
			lines.add(new OutlineLine(rotated(edge.start().subtract(shape.middle()), rotation).scale(size),
				rotated(edge.end().subtract(shape.middle()), rotation).scale(size),
				(edge.start().dot(shape.axis()) - shape.near()) / (shape.far() - shape.near()),
				(edge.end().dot(shape.axis()) - shape.near()) / (shape.far() - shape.near())));
		}
		return lines;
	}

	private static Vec3 rotated(Vec3 point, Quaternionf rotation) {
		Vector3f result = rotation.transform(new Vector3f((float) point.x, (float) point.y, (float) point.z));
		return new Vec3(result.x, result.y, result.z);
	}

	private static @Nullable AbstractArrow arrow(ArrowItem item, ItemStack stack, Level level) {
		Player player = Minecraft.getInstance().player;
		if (player == null) {
			return null;
		}
		ItemStack key = stack.copyWithCount(1);
		for (Made made : ARROWS) {
			if (ItemStack.isSameItemSameComponents(made.stack(), key)) {
				return made.arrow();
			}
		}
		AbstractArrow arrow = item.createArrow(level, key, player, null);
		ARROWS.add(new Made(key, arrow));
		return arrow;
	}

	private static Shape measure(ItemStackRenderState item) {
		List<Vec3> points = new ArrayList<>();
		item.visitExtents(point -> points.add(new Vec3(point.x(), point.y(), point.z())));
		Vec3 tip = Vec3.ZERO;
		for (Vec3 point : points) {
			if (point.lengthSqr() > tip.lengthSqr()) {
				tip = point;
			}
		}
		if (tip.lengthSqr() < 1.0E-6) {
			return SQUARE;
		}
		Vec3 axis = tip.normalize();
		double forward = axis.dot(HAND_FORWARD);
		if (Math.abs(forward) >= UPRIGHT ? forward < 0 : axis.dot(HAND_UP) < 0) {
			axis = axis.scale(-1);
		}
		Vec3 u = LightStrokes.perpendicular(axis);
		Vec3 v = axis.cross(u);
		double near = Double.MAX_VALUE;
		double far = -Double.MAX_VALUE;
		double meanU = 0;
		double meanV = 0;
		for (Vec3 point : points) {
			double along = point.dot(axis);
			near = Math.min(near, along);
			far = Math.max(far, along);
			meanU += point.dot(u) / points.size();
			meanV += point.dot(v) / points.size();
		}
		// The spread across the line to the point: its widest way, then the thinnest at right angles to it.
		double uu = 0;
		double uv = 0;
		double vv = 0;
		for (Vec3 point : points) {
			double x = point.dot(u) - meanU;
			double y = point.dot(v) - meanV;
			uu += x * x;
			uv += x * y;
			vv += y * y;
		}
		double thinnest = 0.5 * Math.atan2(2 * uv, uu - vv) + Math.PI / 2;
		Vec3 face = u.scale(Math.cos(thinnest)).add(v.scale(Math.sin(thinnest)));
		// Across its length, in its flat side: how far each side reaches from the line of its length.
		Vec3 side = axis.cross(face);
		double reach = 0;
		double otherReach = 0;
		for (Vec3 point : points) {
			reach = Math.max(reach, point.dot(side));
			otherReach = Math.max(otherReach, -point.dot(side));
		}
		Vec3 edge = reach > EDGE_REACH * otherReach ? side : otherReach > EDGE_REACH * reach ? side.scale(-1) : null;
		return new Shape(axis, face, edge, near, far, outline(points, axis, side, near, far));
	}

	private static List<Edge> outline(List<Vec3> points, Vec3 axis, Vec3 side, double near, double far) {
		double[] low = new double[OUTLINE_SLICES];
		double[] high = new double[OUTLINE_SLICES];
		Arrays.fill(low, Double.MAX_VALUE);
		Arrays.fill(high, -Double.MAX_VALUE);
		for (Vec3 point : points) {
			int slice = Mth.clamp((int) ((point.dot(axis) - near) / (far - near) * OUTLINE_SLICES), 0, OUTLINE_SLICES - 1);
			low[slice] = Math.min(low[slice], point.dot(side));
			high[slice] = Math.max(high[slice], point.dot(side));
		}
		List<Edge> edges = new ArrayList<>();
		Edge previous = null;
		for (int slice = 0; slice < OUTLINE_SLICES; slice++) {
			if (low[slice] > high[slice]) {
				continue;
			}
			Vec3 middle = axis.scale(near + (far - near) * (slice + 0.5) / OUTLINE_SLICES);
			Edge across = new Edge(middle.add(side.scale(low[slice])), middle.add(side.scale(high[slice])));
			if (previous == null) {
				edges.add(across);
			} else {
				edges.add(new Edge(previous.start(), across.start()));
				edges.add(new Edge(previous.end(), across.end()));
			}
			previous = across;
		}
		if (previous != null) {
			edges.add(previous);
		}
		return List.copyOf(edges);
	}

	private static @Nullable Vec3 across(Vec3 facing, Vec3 direction) {
		Vec3 across = direction.subtract(facing.scale(direction.dot(facing)));
		return across.lengthSqr() < 1.0E-6 ? null : across.normalize();
	}

	private static Quaternionf turn(Vec3 axis, Vec3 face, Vec3 facing, Vec3 toward) {
		Vec3 side = axis.cross(face);
		Vec3 sideTo = facing.cross(toward);
		Matrix3f from = new Matrix3f((float) axis.x, (float) axis.y, (float) axis.z, (float) face.x, (float) face.y, (float) face.z, (float) side.x,
			(float) side.y, (float) side.z);
		Matrix3f to = new Matrix3f((float) facing.x, (float) facing.y, (float) facing.z, (float) toward.x, (float) toward.y, (float) toward.z,
			(float) sideTo.x, (float) sideTo.y, (float) sideTo.z);
		return new Quaternionf().setFromNormalized(to.mul(from.transpose()));
	}
}
