package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.alayacore.progression.TrueName;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.shield.RhoAias;
import io.github.cwzmorro.fatecraft.shield.RhoAiasEntity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.zombie.Husk;

public class RhoAiasClientTest implements FabricClientGameTest {
	private static final int[] SUMMON_STAGES = {2, 7, 13, 21, 29, RhoAiasEntity.SUMMON_TICKS + 2};
	private static final int FRONT_SOUTH = 7;
	private static final int FRONT_EAST = 2;
	private static final int SIDE_EAST = 5;
	private static final int SIDE_SOUTH = 2;
	private static final int UP = 2;
	private static final int NORTH = 180;
	private static final int WEST = 90;
	private static final int LEVEL = 5;
	private static final int TURNED_YAW = 50;
	private static final int ARROWS = 3;
	private static final int ARROW_SOUTH = 9;
	private static final double ARROW_SPEED = 2.0;
	private static final int HIT_SEEN_TICKS = 3;
	private static final int HUSK_SOUTH = 6;
	private static final int SHATTER_SEEN_TICKS = 5;
	private static final int GROWING_BACK_TICKS = 7;
	private static final int GROWN_BACK_TICKS = 25;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.EMIYA);
			FateClientTests.quietNoon(context, world);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = FateClientTests.player(server);
				FateClientTests.giveMana(player);
			});
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);

			FateClientTests.press(world, RhoAias.KEY.identifier(), 0);
			int waited = 0;
			for (int stage = 0; stage < SUMMON_STAGES.length; stage++) {
				context.waitTicks(SUMMON_STAGES[stage] - waited);
				waited = SUMMON_STAGES[stage];
				context.takeScreenshot("rho-aias-0" + (stage + 1) + "-summon-" + SUMMON_STAGES[stage]);
			}
			FateClientTests.face(world, TURNED_YAW, 0);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			FateClientTests.setCamera(context, CameraType.THIRD_PERSON_BACK);
			context.takeScreenshot("rho-aias-07-held-turned");
			FateClientTests.faceSouth(world);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			FateClientTests.release(world, RhoAias.KEY.identifier());
			context.takeScreenshot("rho-aias-08-behind");
			flowerViews(context, world, "09");

			for (int arrow = 0; arrow < ARROWS; arrow++) {
				world.getServer().runCommand("execute as @p at @s run summon arrow ~" + (arrow - 1) + " ~1.6 ~" + ARROW_SOUTH
					+ " {Motion:[0.0d,0.0d," + -ARROW_SPEED + "d]}");
				context.waitTicks(1);
			}
			// From where the camera is: moving it to another view takes longer than a hit shows.
			context.waitTicks(HIT_SEEN_TICKS);
			context.takeScreenshot("rho-aias-12-hit");

			breakPetal(context, world);
			context.waitTicks(SHATTER_SEEN_TICKS);
			context.takeScreenshot("rho-aias-13-petal-shattering");
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE * 2);
			context.takeScreenshot("rho-aias-14-petal-gone");
			world.getServer().runCommand("kill @e[type=husk]");
			world.getServer().runCommand("kill @e[type=fatecraft:rho_aias]");

			// A new cast to hold (a hold repairs): break a petal, then let it grow back.
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			FateClientTests.press(world, RhoAias.KEY.identifier(), 0);
			context.waitTicks(RhoAiasEntity.SUMMON_TICKS);
			breakPetal(context, world);
			context.waitTicks(GROWING_BACK_TICKS);
			context.takeScreenshot("rho-aias-15-petal-growing-back");
			context.waitTicks(GROWN_BACK_TICKS);
			context.takeScreenshot("rho-aias-16-petal-grown-back");
			FateClientTests.release(world, RhoAias.KEY.identifier());
			world.getServer().runCommand("kill @e[type=husk]");
			world.getServer().runCommand("kill @e[type=fatecraft:rho_aias]");

			world.getServer().runOnServer(server -> TrueName.realize(FateClientTests.player(server)));
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
			FateClientTests.tap(world, RhoAias.KEY.identifier(), 0);
			context.waitTicks(RhoAiasEntity.SUMMON_TICKS / 2);
			context.takeScreenshot("rho-aias-17-seven-petals-summon");
			context.waitTicks(RhoAiasEntity.SUMMON_TICKS);
			context.takeScreenshot("rho-aias-18-seven-petals");
			flowerViews(context, world, "19");
		}
	}

	private static void flowerViews(ClientGameTestContext context, TestSingleplayerContext world, String number) {
		FateClientTests.lookFrom(context, world, "rho-aias-" + number + "a-front", FRONT_EAST, UP, FRONT_SOUTH, NORTH, LEVEL);
		FateClientTests.lookFrom(context, world, "rho-aias-" + number + "b-side", SIDE_EAST, UP, SIDE_SOUTH, WEST, LEVEL);
		world.getServer().runCommand("time set midnight");
		context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
		FateClientTests.lookFrom(context, world, "rho-aias-" + number + "c-front-night", FRONT_EAST, UP, FRONT_SOUTH, NORTH, LEVEL);
		world.getServer().runCommand("time set noon");
	}

	private static void breakPetal(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("execute as @p at @s run summon husk ~ ~ ~" + HUSK_SOUTH + " {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b}");
		context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = FateClientTests.player(server);
			RhoAiasEntity shield = RhoAiasEntity.of(player);
			ServerLevel level = player.level();
			Husk husk = level.getEntitiesOfClass(Husk.class, player.getBoundingBox().inflate(HUSK_SOUTH * 2)).getFirst();
			shield.hurtServer(level, level.damageSources().mobAttack(husk), (float) (shield.maxHp() / shield.petals() + 1));
		});
	}
}
