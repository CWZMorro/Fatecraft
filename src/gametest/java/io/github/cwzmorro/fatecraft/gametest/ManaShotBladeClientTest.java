package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.artoria.Artoria;
import io.github.cwzmorro.fatecraft.artoria.ExcaliburNP;
import io.github.cwzmorro.fatecraft.artoria.ManaShot;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.item.ItemStack;

public final class ManaShotBladeClientTest implements FabricClientGameTest {
	private static final int SETTLE_TICKS = FateClientTests.TICKS_TO_SETTLE;
	private static final int FLOOR_LENGTH = 60, FLOOR_CLEARANCE = 8;
	private static final int CHARGE_TICKS = 12;
	private static final int FOV = 70;
	private static final int MODE = 0;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.ARTORIA);
			world.getServer().runCommand("gamemode creative @p");
			FateClientTests.flatFloor(world, FLOOR_LENGTH, FLOOR_CLEARANCE);
			world.getServer().runCommand("weather clear");
			context.runOnClient(client -> {
				client.options.hideGui = true;
				client.options.fov().set(FOV);
			});
			for (String time : FateClientTests.TIMES_OF_DAY) {
				world.getServer().runCommand("time set " + time);
				FateClientTests.hold(world, FateWeapons.CALIBURN);
				world.getServer().runCommand("tp @p 0 101 0 0 0");
				camera(context, CameraType.FIRST_PERSON);
				world.getServer().runCommand("tick freeze");
				FateClientTests.tap(world, ManaShot.KEY.identifier(), MODE);
				world.getServer().runCommand("tick step " + CHARGE_TICKS);
				context.waitTicks(SETTLE_TICKS);
				checkLit(context, true, "Mana Shot's charge must light the blade");
				context.takeScreenshot("mana-shot-blade-caliburn-first-person-" + time);
				camera(context, CameraType.THIRD_PERSON_BACK);
				context.takeScreenshot("mana-shot-blade-caliburn-third-person-back-" + time);
				camera(context, CameraType.THIRD_PERSON_FRONT);
				context.takeScreenshot("mana-shot-blade-caliburn-third-person-front-" + time);
				world.getServer().runOnServer(server -> Abilities.endAll(FateClientTests.player(server)));
				world.getServer().runCommand("tick step 1");
				context.waitTicks(SETTLE_TICKS);
				checkLit(context, false, "Cancelling Mana Shot must unlight the blade");
				world.getServer().runCommand("tick unfreeze");
				context.waitTicks(SETTLE_TICKS);
				FateClientTests.hold(world, FateWeapons.EXCALIBUR);
				world.getServer().runCommand("tp @p 0 101 0 0 0");
				camera(context, CameraType.FIRST_PERSON);
				world.getServer().runCommand("tick freeze");
				FateClientTests.press(world, ExcaliburNP.KEY.identifier(), ExcaliburNP.HELD);
				world.getServer().runCommand("tick step " + (Durations.ticks(FateConfig.artoria().excaliburWindUpSeconds()) - SETTLE_TICKS));
				context.waitTicks(SETTLE_TICKS);
				context.takeScreenshot("mana-shot-blade-excalibur-control-first-person-" + time);
				camera(context, CameraType.THIRD_PERSON_BACK);
				context.takeScreenshot("mana-shot-blade-excalibur-control-third-person-back-" + time);
				world.getServer().runOnServer(server -> Abilities.endAll(FateClientTests.player(server)));
				world.getServer().runCommand("tick unfreeze");
				context.waitTicks(Durations.ticks(FateConfig.artoria().excaliburWindUpSeconds()));
			}
		}
	}

	private static void camera(ClientGameTestContext context, CameraType camera) {
		context.runOnClient(client -> client.options.hideGui = camera != CameraType.FIRST_PERSON);
		FateClientTests.setCamera(context, camera);
	}

	private static void checkLit(ClientGameTestContext context, boolean lit, String message) {
		context.runOnClient(client -> {
			ItemStack held = client.player.getMainHandItem();
			if (Boolean.TRUE.equals(held.get(Artoria.MANA_SHOT_CHARGE)) != lit
				|| held.has(Artoria.EXCALIBUR_CHARGE)) {
				throw new AssertionError(message);
			}
		});
	}
}
