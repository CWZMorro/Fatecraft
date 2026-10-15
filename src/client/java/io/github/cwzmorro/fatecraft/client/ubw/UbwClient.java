package io.github.cwzmorro.fatecraft.client.ubw;

import io.github.cwzmorro.fatecraft.ubw.Ubw;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.renderer.entity.EntityRenderers;

public final class UbwClient {
	private UbwClient() { }

	public static void init() {
		EntityRenderers.register(Ubw.ENVIRONMENT, UbwEnvironmentRenderer::new);
		EntityRenderers.register(Ubw.GROUND_SWORD, UbwGroundSwordRenderer::new);
		BlockColorRegistry.register(List.of(new UbwGroundTint()), Ubw.GROUND);
	}
}
