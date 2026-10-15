package io.github.cwzmorro.fatecraft.client.gate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Passes everything submitted through it on to another collector, but cuts items and custom geometry off at a plane, so a
 * weapon half out of a gate shows nothing of its other half from any side. Item quads are cut and drawn as usual; custom
 * geometry, models and model parts are cut as they are written; everything else goes on uncut.
 */
final class PlaneClip implements SubmitNodeCollector {
	private static final int MOST_CORNERS = 5;

	private final SubmitNodeCollector root;
	private final OrderedSubmitNodeCollector target;
	private final Vector3f point;
	private final Vector3f normal;

	PlaneClip(SubmitNodeCollector root, PoseStack.Pose pose, Vec3 facing) {
		this(root, root, pose.pose().transformPosition(0, 0, 0, new Vector3f()),
			pose.transformNormal((float) facing.x, (float) facing.y, (float) facing.z, new Vector3f()));
	}

	private PlaneClip(SubmitNodeCollector root, OrderedSubmitNodeCollector target, Vector3f point, Vector3f normal) {
		this.root = root;
		this.target = target;
		this.point = point;
		this.normal = normal;
	}

	@Override
	public OrderedSubmitNodeCollector order(int order) {
		return new PlaneClip(this.root, this.root.order(order), this.point, this.normal);
	}

	@Override
	public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
		this.target.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
			Clipper clipper = new Clipper(buffer, this.point, this.normal);
			customGeometryRenderer.render(pose, clipper);
			clipper.flush();
		});
	}

	@Override
	public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords, int overlayCoords, int outlineColor, int[] tintLayers,
		List<BakedQuad> quads, ItemStackRenderState.FoilType foilType) {
		Matrix4f pose = poseStack.last().pose();
		Vector3f point = pose.invert(new Matrix4f()).transformPosition(this.point, new Vector3f());
		Vector3f normal = new Matrix3f(pose).transpose().transform(this.normal, new Vector3f());
		Cut cut = new Cut();
		float[] side = new float[BakedQuad.VERTEX_COUNT];
		List<BakedQuad> kept = new ArrayList<>(quads.size());
		for (BakedQuad quad : quads) {
			for (int corner = 0; corner < BakedQuad.VERTEX_COUNT; corner++) {
				side[corner] = quad.position(corner).sub(point, new Vector3f()).dot(normal);
			}
			if (Cut.whole(side)) {
				kept.add(quad);
			} else if (cut.of(side)) {
				for (int[] corners : cut.quads()) {
					kept.add(cut.quad(quad, corners));
				}
			}
		}
		this.target.submitItem(poseStack, displayContext, lightCoords, overlayCoords, outlineColor, tintLayers, kept, foilType);
	}

	@Override
	public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
		this.target.submitShadow(poseStack, radius, pieces);
	}

	@Override
	public void submitNameTag(PoseStack poseStack, @Nullable Vec3 nameTagAttachment, int offset, Component name, boolean seeThrough, int lightCoords,
		double distanceToCameraSq, CameraRenderState camera) {
		this.target.submitNameTag(poseStack, nameTagAttachment, offset, name, seeThrough, lightCoords, distanceToCameraSq, camera);
	}

	@Override
	public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence string, boolean dropShadow, Font.DisplayMode displayMode,
		int lightCoords, int color, int backgroundColor, int outlineColor) {
		this.target.submitText(poseStack, x, y, string, dropShadow, displayMode, lightCoords, color, backgroundColor, outlineColor);
	}

	@Override
	public void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
		this.target.submitFlame(poseStack, renderState, rotation);
	}

	@Override
	public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
		this.target.submitLeash(poseStack, leashState);
	}

	@Override
	public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords,
		int tintedColor, @Nullable TextureAtlasSprite sprite, int outlineColor, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
		this.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
			model.setupAnim(state);
			model.renderToBuffer(at(pose), sprite == null ? buffer : sprite.wrap(buffer), lightCoords, overlayCoords, tintedColor);
		});
	}

	@Override
	public void submitModelPart(ModelPart modelPart, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords,
		@Nullable TextureAtlasSprite sprite, boolean sheeted, boolean hasFoil, int tintedColor, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay,
		int outlineColor) {
		SubmitNodeCollector.CustomGeometryRenderer part = (pose, buffer) -> modelPart.render(at(pose), sprite == null ? buffer : sprite.wrap(buffer),
			lightCoords, overlayCoords, tintedColor);
		this.submitCustomGeometry(poseStack, renderType, part);
		if (hasFoil) {
			this.submitCustomGeometry(poseStack, ItemFeatureRenderer.getFoilRenderType(renderType, sheeted), part);
		}
	}

	private static PoseStack at(PoseStack.Pose pose) {
		PoseStack poseStack = new PoseStack();
		poseStack.last().set(pose);
		return poseStack;
	}

	@Override
	public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState) {
		this.target.submitMovingBlock(poseStack, movingBlockRenderState);
	}

	@Override
	public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts, int[] tintLayers, int lightCoords,
		int overlayCoords, int outlineColor) {
		this.target.submitBlockModel(poseStack, renderType, parts, tintLayers, lightCoords, overlayCoords, outlineColor);
	}

	@Override
	public void submitBreakingBlockModel(PoseStack poseStack, BlockStateModel model, long seed, int progress) {
		this.target.submitBreakingBlockModel(poseStack, model, seed, progress);
	}

	@Override
	public void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer particleGroupRenderer) {
		this.target.submitParticleGroup(particleGroupRenderer);
	}

	private static final class Clipper implements VertexConsumer {
		private final VertexConsumer buffer;
		private final Vector3f point;
		private final Vector3f normal;
		private final float[][] positions = new float[BakedQuad.VERTEX_COUNT][3];
		private final float[][] uvs = new float[BakedQuad.VERTEX_COUNT][2];
		private final float[][] normals = new float[BakedQuad.VERTEX_COUNT][3];
		private final int[] colors = new int[BakedQuad.VERTEX_COUNT];
		private final int[] overlays = new int[BakedQuad.VERTEX_COUNT];
		private final int[] lights = new int[BakedQuad.VERTEX_COUNT];
		private int corners;
		private final Cut cut = new Cut();
		private final float[] side = new float[BakedQuad.VERTEX_COUNT];

		Clipper(VertexConsumer buffer, Vector3f point, Vector3f normal) {
			this.buffer = buffer;
			this.point = point;
			this.normal = normal;
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			if (this.corners == BakedQuad.VERTEX_COUNT) {
				this.flush();
			}
			int corner = this.corners++;
			this.positions[corner][0] = x;
			this.positions[corner][1] = y;
			this.positions[corner][2] = z;
			this.colors[corner] = -1;
			this.uvs[corner][0] = 0;
			this.uvs[corner][1] = 0;
			this.overlays[corner] = 0;
			this.lights[corner] = 0;
			this.normals[corner][0] = 0;
			this.normals[corner][1] = 1;
			this.normals[corner][2] = 0;
			return this;
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) {
			return this.setColor(ARGB.color(a, r, g, b));
		}

		@Override
		public VertexConsumer setColor(int color) {
			this.colors[this.corners - 1] = color;
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			this.uvs[this.corners - 1][0] = u;
			this.uvs[this.corners - 1][1] = v;
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			this.overlays[this.corners - 1] = u & 0xFFFF | v << 16;
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			this.lights[this.corners - 1] = u & 0xFFFF | v << 16;
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			this.normals[this.corners - 1][0] = x;
			this.normals[this.corners - 1][1] = y;
			this.normals[this.corners - 1][2] = z;
			return this;
		}

		@Override
		public VertexConsumer setLineWidth(float width) {
			return this;
		}

		void flush() {
			int taken = this.corners;
			this.corners = 0;
			if (taken < BakedQuad.VERTEX_COUNT) {
				return;
			}
			float[] side = this.side;
			for (int corner = 0; corner < BakedQuad.VERTEX_COUNT; corner++) {
				float[] at = this.positions[corner];
				side[corner] = (at[0] - this.point.x) * this.normal.x + (at[1] - this.point.y) * this.normal.y + (at[2] - this.point.z) * this.normal.z;
			}
			if (this.cut.of(side)) {
				for (int[] corners : this.cut.quads()) {
					this.put(corners);
				}
			}
		}

		private void put(int[] corners) {
			for (int k : corners) {
				int a = this.cut.from[k];
				int b = this.cut.to[k];
				float t = this.cut.share[k];
				this.buffer.addVertex(Mth.lerp(t, this.positions[a][0], this.positions[b][0]), Mth.lerp(t, this.positions[a][1], this.positions[b][1]),
					Mth.lerp(t, this.positions[a][2], this.positions[b][2]), ARGB.srgbLerp(t, this.colors[a], this.colors[b]),
					Mth.lerp(t, this.uvs[a][0], this.uvs[b][0]), Mth.lerp(t, this.uvs[a][1], this.uvs[b][1]), this.overlays[a], this.lights[a],
					Mth.lerp(t, this.normals[a][0], this.normals[b][0]), Mth.lerp(t, this.normals[a][1], this.normals[b][1]),
					Mth.lerp(t, this.normals[a][2], this.normals[b][2]));
			}
		}
	}

	// Sutherland-Hodgman against the one plane.
	private static final class Cut {
		private static final int[][] TRIANGLE = {{0, 1, 2, 2}};
		private static final int[][] QUAD = {{0, 1, 2, 3}};
		private static final int[][] PENTAGON = {{0, 1, 2, 3}, {0, 3, 4, 4}};

		final int[] from = new int[MOST_CORNERS];
		final int[] to = new int[MOST_CORNERS];
		final float[] share = new float[MOST_CORNERS];
		private int count;

		static boolean whole(float[] side) {
			for (float corner : side) {
				if (corner < 0) {
					return false;
				}
			}
			return true;
		}

		boolean of(float[] side) {
			this.count = 0;
			for (int corner = 0; corner < BakedQuad.VERTEX_COUNT; corner++) {
				int next = (corner + 1) % BakedQuad.VERTEX_COUNT;
				if (side[corner] >= 0) {
					this.add(corner, next, 0);
				}
				if (side[corner] >= 0 != side[next] >= 0) {
					this.add(corner, next, side[corner] / (side[corner] - side[next]));
				}
			}
			return this.count > 0;
		}

		private void add(int from, int to, float share) {
			this.from[this.count] = from;
			this.to[this.count] = to;
			this.share[this.count] = share;
			this.count++;
		}

		int[][] quads() {
			return this.count == 3 ? TRIANGLE : this.count == 4 ? QUAD : PENTAGON;
		}

		BakedQuad quad(BakedQuad quad, int[] corners) {
			Vector3fc[] positions = new Vector3fc[BakedQuad.VERTEX_COUNT];
			long[] uvs = new long[BakedQuad.VERTEX_COUNT];
			for (int k = 0; k < BakedQuad.VERTEX_COUNT; k++) {
				int a = this.from[corners[k]];
				int b = this.to[corners[k]];
				float t = this.share[corners[k]];
				positions[k] = quad.position(a).lerp(quad.position(b), t, new Vector3f());
				long uvA = quad.packedUV(a);
				long uvB = quad.packedUV(b);
				uvs[k] = UVPair.pack(Mth.lerp(t, UVPair.unpackU(uvA), UVPair.unpackU(uvB)), Mth.lerp(t, UVPair.unpackV(uvA), UVPair.unpackV(uvB)));
			}
			return new BakedQuad(positions[0], positions[1], positions[2], positions[3], uvs[0], uvs[1], uvs[2], uvs[3], quad.direction(), quad.materialInfo());
		}
	}
}
