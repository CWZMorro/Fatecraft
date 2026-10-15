package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.beam.HeldBeam;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.artoria.ExcaliburEntity;
import io.github.cwzmorro.fatecraft.artoria.ExcaliburNP;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.weapon.EnumaElish;
import io.github.cwzmorro.fatecraft.weapon.EnumaElishBeam;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public class HeldBeamMovementClientTest implements FabricClientGameTest {
	private static final int SETTLE = 5;
	private static final double EPSILON = 0.00001;
	private static final int FLOOR_LENGTH = 40;
	private static final int FLOOR_CLEARANCE = 10;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		for (boolean excalibur : new boolean[]{false, true}) {
			try (TestSingleplayerContext world = context.worldBuilder().create()) {
				FateClientTests.becomeServant(context, world, excalibur ? FateServants.ARTORIA : FateServants.GILGAMESH);
				world.getServer().runCommand("gamemode creative @p");
				world.getServer().runCommand("gamerule alayacore:weapon_block_damage false");
				FateClientTests.flatFloor(world, FLOOR_LENGTH, FLOOR_CLEARANCE);
				FateClientTests.hold(world, excalibur ? FateWeapons.EXCALIBUR : FateWeapons.EA);
				world.getServer().runCommand("tp @p 0.5 101 0.5 0 -90");
				context.waitTicks(SETTLE);
				context.getInput().holdKey(options -> options.keyUp);
				context.waitTicks(SETTLE);
				float baseline = context.computeOnClient(client -> client.player.zza);
				if (baseline <= 0) {
					throw new AssertionError("No forward input baseline");
				}
				Identifier ability = excalibur ? ExcaliburNP.KEY.identifier() : EnumaElish.KEY.identifier();
				FateClientTests.press(world, ability, 0);
				world.getServer().runOnServer(server -> {
					var player = FateClientTests.player(server);
					if (!Abilities.isActive(player, ability) || !HeldBeam.restrictsMovement(player)) {
						throw new AssertionError("Server activation missing: " + ability + ", owns="
							+ Abilities.owns(player, ability) + ", hand=" + player.getMainHandItem()
							+ ", creative=" + player.isCreative() + ", restricted=" + HeldBeam.restrictsMovement(player));
					}
				});
				context.waitFor(client -> HeldBeam.restrictsMovement(client.player));
				context.waitTicks(SETTLE);
				assertMovement(context, baseline);
				int charge = excalibur ? Durations.ticks(FateConfig.artoria().excaliburWindUpSeconds()) : FateConfig.ea().enumaElishWindUpTicks();
				context.waitTicks(charge);
				context.waitFor(client -> excalibur
					? client.level.getEntitiesOfClass(ExcaliburEntity.class, client.player.getBoundingBox().inflate(FateConfig.artoria().excaliburBeamRange()))
						.stream().anyMatch(light -> light.phase() == ExcaliburEntity.Phase.HELD)
					: !client.level.getEntitiesOfClass(EnumaElishBeam.class, client.player.getBoundingBox().inflate(FateConfig.artoria().excaliburBeamRange())).isEmpty());
				assertMovement(context, baseline);
				FateClientTests.release(world, ability);
				context.waitFor(client -> !HeldBeam.restrictsMovement(client.player));
				context.waitTicks(SETTLE);
				context.runOnClient(client -> {
					if (HeldBeam.restrictsMovement(client.player) || Math.abs(client.player.zza - baseline) > EPSILON) {
						throw new AssertionError("Input not restored after " + ability);
					}
					client.player.setSprinting(true);
					if (!client.player.isSprinting()) {
						throw new AssertionError("Sprint not restored");
					}
				});
				context.getInput().releaseKey(options -> options.keyUp);
			}
		}
	}

	private static void assertMovement(ClientGameTestContext context, float baseline) {
		context.runOnClient(client -> {
			if (!HeldBeam.restrictsMovement(client.player)
				|| Math.abs(client.player.zza / baseline - AlayaConfig.heldBeams().heldBeamMovementMultiplier()) > EPSILON) {
				throw new AssertionError("Vanilla input not slowed: " + client.player.zza + " / " + baseline
					+ ", restricted=" + HeldBeam.restrictsMovement(client.player) + ", multiplier=" + HeldBeam.movementMultiplier(client.player));
			}
			client.player.setSprinting(true);
			if (client.player.isSprinting()) {
				throw new AssertionError("Sprint accepted during held beam");
			}
			client.player.setDeltaMovement(Vec3.ZERO);
			client.player.jumpFromGround();
			if (client.player.getDeltaMovement().y <= 0) {
				throw new AssertionError("Jump prevented during held beam");
			}
		});
	}
}
