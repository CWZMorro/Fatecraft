package io.github.cwzmorro.fatecraft.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.client.screen.PresetScreen;
import io.github.cwzmorro.alayacore.thrown.ThrownWeaponEntity;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.trace.Project;
import io.github.cwzmorro.fatecraft.trace.Trace;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class TraceClientTest implements FabricClientGameTest {
	private static final int WIDTH = 1440;
	private static final int HEIGHT = 1080;
	private static final int GUI_SCALE = 3;
	private static final int FLIGHT_TICKS = 2;
	private static final int VIEW_RANGE = 20;
	private static final int PRESET_X = 156;
	private static final int PRESET_Y = 18;
	private static final int ROW_X = 60;
	private static final int LEARNED_ROW_Y = 38 + 5 * 13;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.EMIYA);
			world.getServer().runOnServer(server -> {
				var player = FateClientTests.player(server);
				FateClientTests.giveMana(player);
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
				FateTests.tap(player, Trace.KEY.identifier(), 0, false);
			});
			var learned = Project.key(BuiltInRegistries.ITEM.getKey(Items.DIAMOND_SWORD)).identifier();
			context.waitFor(client -> Abilities.owns(client.player, learned));
			context.getInput().resizeWindow(WIDTH, HEIGHT);
			FateClientTests.setGuiScale(context, GUI_SCALE);
			context.getInput().pressKey(InputConstants.KEY_B);
			context.waitForScreen(PresetScreen.class);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			context.runOnClient(client -> client.getToastManager().clear());
			double[] row = context.computeOnClient(client -> {
				Button preset = client.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
					.filter(button -> button.getMessage().getString().equals("1")).findFirst().orElseThrow();
				int scale = client.getWindow().getGuiScale();
				return new double[] {(preset.getX() - PRESET_X + ROW_X) * scale, (preset.getY() - PRESET_Y + LEARNED_ROW_Y) * scale};
			});
			context.getInput().setCursorPos(row[0], row[1]);
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitTick();
			context.takeScreenshot("trace-ability-menu");
			context.setScreen(() -> null);
			FateClientTests.clearNoon(world);
			world.getServer().runCommand("tp @p 0 150 0 0 0");
			world.getServer().runOnServer(server -> FateClientTests.player(server).setNoGravity(true));
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
			world.getServer().runCommand("execute as @p at @s run summon armor_stand ~8 ~2 ~3 {Invisible:1b,NoGravity:1b,Marker:1b,Rotation:[90f,0f]}");
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			context.runOnClient(client -> {
				client.options.hideGui = true;
				client.setCameraEntity(client.level.getEntitiesOfClass(ArmorStand.class, client.player.getBoundingBox().inflate(VIEW_RANGE)).getFirst());
			});
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			world.getServer().runCommand("tick freeze");
			FateClientTests.tap(world, Project.key(BuiltInRegistries.ITEM.getKey(FateWeapons.CALADBOLG)).identifier(), Project.TRACE_ON);
			world.getServer().runOnServer(server -> {
				var player = FateClientTests.player(server);
				var swords = server.overworld().getEntitiesOfClass(ThrownWeaponEntity.class, player.getBoundingBox().inflate(VIEW_RANGE));
				Vec3 centre = Vec3.ZERO;
				for (ThrownWeaponEntity sword : swords) {
					centre = centre.add(sword.position().add(sword.getDeltaMovement().scale(FLIGHT_TICKS)));
				}
				centre = centre.scale(1.0 / swords.size());
				var observer = server.overworld().getEntitiesOfClass(ArmorStand.class, player.getBoundingBox().inflate(VIEW_RANGE)).getFirst();
				Vec3 from = centre.add(7, 1, -4);
				Vec3 direction = centre.subtract(from);
				float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
				float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance()));
				observer.snapTo(from, yaw, pitch);
			});
			int formation = FateConfig.trace().formationTicks();
			world.getServer().runCommand("tick step " + formation / 2);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			context.runOnClient(client -> {
				if (client.level.getEntitiesOfClass(ThrownWeaponEntity.class, client.player.getBoundingBox().inflate(VIEW_RANGE))
					.stream().anyMatch(sword -> sword.formationRemaining() == 0)) {
					throw new AssertionError("Mid-formation must precede flight");
				}
			});
			context.takeScreenshot("trace-on-mid-formation");
			world.getServer().runCommand("tick step " + (formation - formation / 2 + FLIGHT_TICKS));
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			world.getServer().runOnServer(server -> {
				if (server.overworld().getEntitiesOfClass(ThrownWeaponEntity.class,
					FateClientTests.player(server).getBoundingBox().inflate(VIEW_RANGE)).size() != FateConfig.trace().volley()) {
					throw new AssertionError("Trace On must show three swords in flight");
				}
			});
			context.takeScreenshot("trace-on-volley");
			context.runOnClient(client -> {
				client.setCameraEntity(client.player);
				client.options.hideGui = false;
			});
			world.getServer().runCommand("tick unfreeze");
		}
	}
}
