package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.UbwClientTest.check;
import static io.github.cwzmorro.fatecraft.gametest.UbwClientTest.shot;
import static io.github.cwzmorro.fatecraft.gametest.UbwClientTest.step;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.progression.TrueName;
import io.github.cwzmorro.alayacore.servant.ServantChoice;
import io.github.cwzmorro.alayacore.servant.ServantStorage;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.ubw.Ubw;
import io.github.cwzmorro.fatecraft.ubw.UbwConfig;
import io.github.cwzmorro.fatecraft.ubw.UbwController;
import io.github.cwzmorro.fatecraft.ubw.UbwEnvironmentEntity;
import io.github.cwzmorro.fatecraft.ubw.UbwLayout;
import io.github.cwzmorro.fatecraft.ubw.UbwRuntime;
import io.github.cwzmorro.fatecraft.ubw.UnlimitedBladeWorks;
import io.github.cwzmorro.fatecraft.weapon.EnumaElish;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class UbwShatterClientTest implements FabricClientGameTest {
	private static final int[][] PILLARS = {{6, -6}, {-8, 4}, {10, 10}, {-3, -15}, {14, -2}};
	private static final int PILLAR_HEIGHT = 7;
	private static final double CASTER_OUTSIDE = 10;
	private static final double CASTER_ASIDE = 10;
	private static final double CASTER_HEIGHT = 8;
	private static final int CRACK_START = 6;
	private static final int CRACKS_SPREAD = 18;
	private static final int MID_CRUMBLE = 33;
	private static final int LATE_CRUMBLE = 45;
	private static final int AFTER_CLOSE = 3;
	private static final int CAMERA_SETTLE_TICKS = 2;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.EMIYA);
			UbwClientTest.stage(context, world);
			world.getServer().runCommand("tp @p 0 150 0 0 0");
			BlockPos[] center = {BlockPos.ZERO};
			Map<BlockPos, BlockState> originals = new HashMap<>();
			world.getServer().runOnServer(server -> {
				ServerPlayer player = FateClientTests.player(server);
				player.setNoGravity(true);
				TrueName.realize(player);
				FateClientTests.giveMana(player);
				center[0] = UbwController.placement(player, FateConfig.ubw());
				ServerLevel level = player.level();
				for (int[] pillar : PILLARS) {
					for (int y = 0; y < PILLAR_HEIGHT; y++) {
						level.setBlock(center[0].offset(pillar[0], y, pillar[1]), Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
					}
				}
				originals.putAll(UbwTests.originals(level, UbwLayout.sphere(level, center[0], FateConfig.ubw(), 149, 1, Ubw.palette())));
			});
			Vec3 centre = Vec3.atLowerCornerOf(center[0]);
			Vec3 inside = centre.add(-6, 4, 16);
			Vec3 towardImpact = centre.add(8, 9, -24);
			Vec3 overlook = centre.add(-14, 14, 12);
			Vec3 floor = centre.add(4, 0, -6);
			AABB bounds = new AABB(centre.subtract(30, 30, 30), centre.add(30, 30, 30));
			world.getServer().runCommand("tick freeze");
			world.getServer().runOnServer(server -> {
				ServerPlayer player = FateClientTests.player(server);
				FateTests.tap(player, UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.MARBLE, false);
				UbwClientTest.finishOpening(player);
			});
			context.waitFor(client -> !client.level.getEntitiesOfClass(UbwEnvironmentEntity.class, bounds).isEmpty());
			step(context, world, UbwConfig.DEFAULTS.opening().dyingTicks());
			shot(context, inside, towardImpact, "ubw-shatter-01-standing");
			Husk[] caster = {null};
			world.getServer().runOnServer(server -> {
				Husk husk = EntityType.HUSK.create(server.overworld(), EntitySpawnReason.TRIGGERED);
				check(husk != null, "Husk fixture must exist");
				husk.setNoAi(true);
				husk.setNoGravity(true);
				husk.setInvulnerable(true);
				husk.setAttached(ServantStorage.CHOICE, ServantChoice.servant(AlayaRegistries.SERVANT.getKey(FateServants.GILGAMESH)));
				AlayaMana.getOrCreate(husk);
				husk.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FateWeapons.EA));
				Vec3 at = centre.add(CASTER_ASIDE, 149 + 1 + CASTER_HEIGHT - centre.y, -FateConfig.ubw().radius() - CASTER_OUTSIDE);
				husk.snapTo(at.x, at.y, at.z, 0, 0);
				husk.setYHeadRot(0);
				husk.setYBodyRot(0);
				server.overworld().addFreshEntity(husk);
				caster[0] = husk;
				Abilities.press(husk, EnumaElish.KEY.identifier(), 0, false);
			});
			step(context, world, FateConfig.ea().enumaElishWindUpTicks() + 3);
			long[] brokeAt = {0};
			world.getServer().runOnServer(server -> {
				ServerPlayer player = FateClientTests.player(server);
				check(UbwRuntime.shattering(player), "Enuma Elish must break the marble");
				brokeAt[0] = server.overworld().getEntitiesOfClass(UbwEnvironmentEntity.class, bounds).getFirst().shatteredAt();
			});
			long[] now = {0};
			Runnable clock = () -> world.getServer().runOnServer(server -> now[0] = server.overworld().getGameTime());
			clock.run();
			step(context, world, (int) (brokeAt[0] + CRACK_START - now[0]));
			shot(context, inside, towardImpact, "ubw-shatter-02-crack-start");
			world.getServer().runOnServer(server -> Abilities.end(caster[0], EnumaElish.KEY.identifier()));
			step(context, world, CRACKS_SPREAD - CRACK_START);
			shot(context, inside, towardImpact, "ubw-shatter-03-cracks-spread");
			step(context, world, MID_CRUMBLE - CRACKS_SPREAD);
			shot(context, inside, towardImpact, "ubw-shatter-04-mid-crumble");
			shot(context, overlook, floor, "ubw-shatter-04b-mid-crumble-floor");
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
			context.runOnClient(client -> client.setCameraEntity(client.player));
			context.waitTicks(CAMERA_SETTLE_TICKS);
			context.takeScreenshot("ubw-shatter-04c-mid-first-person");
			step(context, world, LATE_CRUMBLE - MID_CRUMBLE);
			shot(context, inside, towardImpact, "ubw-shatter-05-late-crumble");
			step(context, world, FateConfig.ubw().shatterTicks() - LATE_CRUMBLE + AFTER_CLOSE);
			world.getServer().runOnServer(server -> {
				check(!UbwRuntime.hasMarble(FateClientTests.player(server)), "The broken marble must close after its crumble");
				for (Map.Entry<BlockPos, BlockState> entry : originals.entrySet()) {
					check(server.overworld().getBlockState(entry.getKey()).equals(entry.getValue()), "Original block not restored at " + entry.getKey());
				}
			});
			context.waitFor(client -> client.level.getEntitiesOfClass(UbwEnvironmentEntity.class, bounds).isEmpty());
			shot(context, inside, towardImpact, "ubw-shatter-06-closed");
			world.getServer().runOnServer(server -> caster[0].discard());
			world.getServer().runCommand("tick unfreeze");
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
			context.runOnClient(client -> client.options.hideGui = false);
		}
	}
}
