package io.github.cwzmorro.fatecraft.gametest;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;

// The test shield draws nothing, but every entity type needs a renderer on the client.
public final class FateTestShieldsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRenderers.register(FateTestShields.DISC, NoopRenderer::new);
	}
}
