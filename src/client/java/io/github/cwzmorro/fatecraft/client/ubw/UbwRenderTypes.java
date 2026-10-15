package io.github.cwzmorro.fatecraft.client.ubw;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.cwzmorro.fatecraft.Fatecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Render types for the field's scenery. They write depth, unlike additive effects, so the world and entities in front
 * stay visible, and blend so the scenery fades in behind the opening's front. The cloud veil in front of the gears
 * writes no depth and submits after them ({@link #VEIL_ORDER}), because vanilla's draw order between types is not fixed.
 */
final class UbwRenderTypes {
	static final RenderType SKY = type("sky", "ubw_sky", "ubw_sky", true);
	static final RenderType GEARS = type("gears", "light", "light", true);
	static final RenderType VEIL = type("veil", "ubw_sky", "ubw_veil", false);
	// The veil is submitted after the sky and gears (the default order, 0) and before the light types (2 on).
	static final int VEIL_ORDER = 1;

	private UbwRenderTypes() { }

	private static RenderType type(String name, String vertex, String fragment, boolean writesDepth) {
		RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
			.withLocation(Fatecraft.id("pipeline/ubw_" + name))
			.withVertexShader(Fatecraft.id("core/" + vertex))
			.withFragmentShader(Fatecraft.id("core/" + fragment))
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withCull(false)
			.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
			.withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, writesDepth))
			.build();
		return RenderType.create("fatecraft_ubw_" + name,
			RenderSetup.builder(pipeline).setOutputTarget(OutputTarget.MAIN_TARGET).createRenderSetup());
	}
}
