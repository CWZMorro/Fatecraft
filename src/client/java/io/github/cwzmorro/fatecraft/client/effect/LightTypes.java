package io.github.cwzmorro.fatecraft.client.effect;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.cwzmorro.fatecraft.Fatecraft;
import java.util.Optional;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * The render types every weapon effect draws with: light added, or blended to keep its colour against a bright sky. No
 * fog, no depth writes (soft overlapping layers would hide each other in specks), no culling. Vanilla's order between
 * render types is not fixed, so {@link #submit} orders them after vanilla's 0 and 1: {@link #BLENDED}, added, {@link #BLENDED_OVER}.
 */
public final class LightTypes {
	// SOLID is opaque and writes depth, so overlapping flat pieces (Sword Body's blades) hide each other by their height off the surface, not by sort order. It is drawn before every other type.
	public static final RenderType SOLID = renderType("solid", new ColorTargetState(Optional.empty(), ColorTargetState.WRITE_ALL), true);
	public static final RenderType ADDED = renderType("added", BlendFunction.LIGHTNING);
	public static final RenderType BLENDED = renderType("blended", BlendFunction.TRANSLUCENT);
	public static final RenderType BLENDED_OVER = renderType("blended_over", BlendFunction.TRANSLUCENT);
	private static final int SOLID_ORDER = 2;
	private static final int BLENDED_ORDER = 3;
	private static final int ADDED_ORDER = 4;
	private static final int BLENDED_OVER_ORDER = 5;

	private LightTypes() {
	}

	public static void submit(SubmitNodeCollector collector, PoseStack poseStack, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {
		int order = type == SOLID ? SOLID_ORDER : type == BLENDED ? BLENDED_ORDER : type == ADDED ? ADDED_ORDER : BLENDED_OVER_ORDER;
		collector.order(order).submitCustomGeometry(poseStack, type, renderer);
	}

	private static RenderType renderType(String name, BlendFunction blend) {
		return renderType(name, new ColorTargetState(blend), false);
	}

	private static RenderType renderType(String name, ColorTargetState target, boolean writeDepth) {
		RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
			.withLocation(Fatecraft.id("pipeline/light_" + name))
			.withVertexShader(Fatecraft.id("core/light"))
			.withFragmentShader(Fatecraft.id("core/light"))
			.withColorTargetState(target)
			.withCull(false)
			.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
			.withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, writeDepth))
			.build();
		return RenderType.create("fatecraft_light_" + name, RenderSetup.builder(pipeline).setOutputTarget(OutputTarget.WEATHER_TARGET).sortOnUpload()
			.createRenderSetup());
	}
}
