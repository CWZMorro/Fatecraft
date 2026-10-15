package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.servant.ServantChoice;
import io.github.cwzmorro.alayacore.servant.ServantStorage;
import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.shield.RhoAias;
import io.github.cwzmorro.fatecraft.shield.RhoAiasEntity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class RhoAiasSolidClientTest implements FabricClientGameTest {
	private static final int FLOOR = FateClientTests.FLOOR_Y;
	private static final int CASTER_SOUTH = 8;
	private static final int NORTH = 180;
	private static final int RUN_TICKS = 40;
	private static final int JUMP_TICKS = 30;
	private static final double AGREE = 0.1;
	private static final double REACHED = 1.0;
	private static final double CLOSE = 0.001;
	private static final double CLIMB_HEIGHT = 1.5;
	private static final int SIDE_EAST = 6;
	private static final int SIDE_UP = 1;
	private static final int SIDE_FOV = 70;
	private static final double SEARCH = 20.0;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.GILGAMESH);
			FateClientTests.flatFloor(world);
			FateClientTests.clearNoon(world);
			context.runOnClient(client -> client.options.hideGui = true);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = FateClientTests.player(server);
				Husk caster = EntityType.HUSK.create(player.level(), EntitySpawnReason.COMMAND);
				caster.setNoAi(true);
				caster.snapTo(0.5, FLOOR + 1, CASTER_SOUTH + 0.5, NORTH, 0.0F);
				caster.setYHeadRot(NORTH);
				player.level().addFreshEntity(caster);
				AlayaMana.getOrCreate(caster);
				AlayaMana.update(caster, mana -> mana.withMax(FateTests.MANA).withCurrent(FateTests.MANA));
				caster.setAttached(ServantStorage.CHOICE, ServantChoice.servant(Fatecraft.id("emiya")));
				Abilities.press(caster, RhoAias.KEY.identifier(), 0, false);
				Abilities.release(caster, RhoAias.KEY.identifier());
			});
			context.waitTicks(RhoAiasEntity.SUMMON_TICKS + FateClientTests.TICKS_TO_SETTLE);
			FateClientTests.setCamera(context, CameraType.THIRD_PERSON_BACK);
			context.getInput().holdKey(options -> options.keyUp);
			context.getInput().holdKey(options -> options.keySprint);
			double highest;
			try {
				context.waitTicks(RUN_TICKS);
				checkStopped(context, world, "running");
				context.takeScreenshot("rho-solid-1-ran-into-it");
				context.getInput().holdKey(options -> options.keyJump);
				highest = FLOOR + 1;
				for (int tick = 0; tick < JUMP_TICKS; tick++) {
					context.waitTick();
					highest = Math.max(highest, context.computeOnClient(client -> client.player.getY()));
				}
			} finally {
				context.getInput().releaseKey(options -> options.keyJump);
				context.getInput().releaseKey(options -> options.keySprint);
				context.getInput().releaseKey(options -> options.keyUp);
			}
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			checkStopped(context, world, "jumping");
			if (highest >= FLOOR + 1 + CLIMB_HEIGHT) {
				throw new AssertionError("Jumping must not climb the shield");
			}
			if (context.computeOnClient(client -> Math.abs(client.player.getY() - (FLOOR + 1)) > CLOSE || !client.player.onGround())) {
				throw new AssertionError("The player must end on the ground");
			}
			Vec3 player = context.computeOnClient(client -> client.player.position());
			FateClientTests.view(context, player.add(SIDE_EAST, SIDE_UP, 0), player.add(0, SIDE_UP, 0), SIDE_FOV);
			context.takeScreenshot("rho-solid-2-side");
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
		}
	}

	private static void checkStopped(ClientGameTestContext context, TestSingleplayerContext world, String moving) {
		Vec3 client = context.computeOnClient(minecraft -> {
			RhoAiasEntity shield = minecraft.level.getEntitiesOfClass(RhoAiasEntity.class, new AABB(minecraft.player.blockPosition()).inflate(SEARCH)).getFirst();
			double front = minecraft.player.getBoundingBox().getCenter().subtract(shield.position()).dot(shield.getLookAngle());
			if (front <= 0) {
				throw new AssertionError("A " + moving + " player must stay in front of the shield, not pass through it");
			}
			if (front > REACHED) {
				throw new AssertionError("A " + moving + " player must actually reach the shield");
			}
			return minecraft.player.position();
		});
		Vec3 server = world.getServer().computeOnServer(minecraft -> FateClientTests.player(minecraft).position());
		if (client.distanceTo(server) > AGREE) {
			throw new AssertionError("The server must agree where the shield stopped the " + moving + " player");
		}
	}
}
