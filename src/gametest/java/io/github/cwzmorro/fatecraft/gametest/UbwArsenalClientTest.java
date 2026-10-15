package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.alayacore.progression.TrueName;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.trace.Traces;
import io.github.cwzmorro.fatecraft.ubw.UbwController;
import io.github.cwzmorro.fatecraft.ubw.UbwEnvironmentEntity;
import io.github.cwzmorro.fatecraft.ubw.UbwGroundSwordEntity;
import io.github.cwzmorro.fatecraft.ubw.UbwRuntime;
import io.github.cwzmorro.fatecraft.ubw.UnlimitedBladeWorks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class UbwArsenalClientTest implements FabricClientGameTest {
	private static final Item[] MORE_SWORDS = {
		Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};
	private static final double FIELD_REACH = 30;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.EMIYA);
			UbwClientTest.stage(context, world);
			world.getServer().runCommand("gamemode creative @p");
			field(context, world, "small", false);
			field(context, world, "large", true);
		}
	}

	private static void field(ClientGameTestContext context, TestSingleplayerContext world, String name, boolean more) {
		world.getServer().runCommand("tp @p 0 150 0 0 0");
		BlockPos[] center = {BlockPos.ZERO};
		int[] count = {0};
		world.getServer().runOnServer(server -> {
			var player = FateClientTests.player(server);
			player.setNoGravity(true);
			TrueName.realize(player);
			FateClientTests.giveMana(player);
			if (more) {
				for (Item sword : MORE_SWORDS) {
					Traces.learn(player, new ItemStack(sword));
				}
			}
			center[0] = UbwController.placement(player, FateConfig.ubw());
			count[0] = UbwRuntime.groundSwords(player, FateConfig.ubw());
		});
		Vec3 centre = Vec3.atLowerCornerOf(center[0]);
		AABB bounds = new AABB(centre, centre).inflate(FIELD_REACH);
		try {
			world.getServer().runOnServer(server ->
				FateTests.tap(FateClientTests.player(server), UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.MARBLE, false));
			context.waitFor(client -> !client.level.getEntitiesOfClass(UbwEnvironmentEntity.class, bounds).isEmpty());
			world.getServer().runOnServer(server -> UbwClientTest.finishOpening(FateClientTests.player(server)));
			context.waitFor(client -> client.level.getEntitiesOfClass(UbwGroundSwordEntity.class, bounds).size() == count[0]);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			UbwClientTest.shot(context, centre.add(UbwClientTest.OVERLOOK), centre.add(UbwClientTest.FIELD),
				"ubw-arsenal-" + name + "-" + count[0] + "-overlook");
			UbwClientTest.shot(context, centre.add(UbwClientTest.EYE_LEVEL), centre.add(UbwClientTest.EYE_LEVEL_LOOK),
				"ubw-arsenal-" + name + "-" + count[0] + "-eye-level");
		} finally {
			world.getServer().runOnServer(server -> UbwRuntime.cancel(FateClientTests.player(server)));
			context.waitFor(client -> client.level.getEntitiesOfClass(UbwGroundSwordEntity.class, bounds).isEmpty());
		}
	}
}
