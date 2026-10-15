package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.network.OpenServantSelectionPayload;
import io.github.cwzmorro.alayacore.progression.ClassGrade;
import io.github.cwzmorro.alayacore.progression.TrueName;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.alayacore.servant.ServantSelection;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.gate.GateOfBabylon;
import io.github.cwzmorro.fatecraft.network.VaultSectionPayload;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.shield.RhoAias;
import io.github.cwzmorro.fatecraft.shield.RhoAiasConfig;
import io.github.cwzmorro.fatecraft.shield.RhoAiasEntity;
import io.github.cwzmorro.fatecraft.ubw.UbwController;
import io.github.cwzmorro.fatecraft.ubw.UbwEnvironmentEntity;
import io.github.cwzmorro.fatecraft.ubw.UbwOpening;
import io.github.cwzmorro.fatecraft.ubw.UbwRuntime;
import io.github.cwzmorro.fatecraft.vault.Vault;
import io.github.cwzmorro.fatecraft.vault.VaultSettings;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class ContentConfigGameTests {
	private static final double EPSILON = 0.001;

	@GameTest(environment = "fatecraft-gametest:isolated/ubw_opening_config", setupTicks = 2)
	public void ubwCapturesConfiguredOpeningTiming(GameTestHelper helper) throws IOException {
		Path file = Files.createTempFile("fatecraft-opening-test-", ".toml");
		ServerPlayer caster = null;
		try {
			Files.writeString(file, """
				[fatecraft.unlimited_blade_works]
				radius = 3
				ground_swords_per_trace = 0
				max_ground_swords = 0
				opening_charge_ticks = 7
				opening_ignition_ticks = 2
				opening_spread_ticks = 8
				opening_dying_ticks = 12
				""");
			check(helper, "valid opening config", FateTests.configProblems(file).isEmpty());
			UbwOpening.Timing timing = new UbwOpening.Timing(7, 2, 8, 12);
			check(helper, "configured timing", FateConfig.ubw().opening().equals(timing));
			check(helper, "configured ignition", UbwOpening.reach(2, 26, timing) == UbwOpening.IGNITION_REACH);
			check(helper, "configured spread", UbwOpening.reach(8, 26, timing) == 26);
			caster = FateTests.servant(helper, new BlockPos(3, 1, 3), FateTests.MANA, FateServants.EMIYA);
			TrueName.realize(caster);
			long castAt = helper.getLevel().getGameTime();
			check(helper, "staged opening", UbwRuntime.open(caster) == UbwController.OpenResult.OPENED);
			UbwController controller = UbwController.of(helper.getLevel().getServer());
			UbwEnvironmentEntity environment = helper.getLevel().getEntitiesOfClass(UbwEnvironmentEntity.class,
				caster.getBoundingBox().inflate(FateConfig.ubw().radius() * 2)).getFirst();
			check(helper, "configured charge", controller.openedAt(caster.getUUID()) == castAt + timing.chargeTicks());
			check(helper, "replicated captured timing", environment.openingTiming().equals(timing));
			ByteBuf buffer = Unpooled.buffer();
			try {
				UbwOpening.Timing.STREAM_CODEC.encode(buffer, timing);
				check(helper, "opening timing crosses the network", UbwOpening.Timing.STREAM_CODEC.decode(buffer).equals(timing));
			} finally {
				buffer.release();
			}
			AlayaConfig.load(helper.getLevel().registryAccess());
			check(helper, "reload preserves active opening", controller.config(caster.getUUID()).opening().equals(timing));
			UbwRuntime.finishOpening(caster);
			check(helper, "finish uses captured spread", environment.openedAt() == helper.getLevel().getGameTime() - timing.spreadTicks());
			check(helper, "timing stays replicated after reload", environment.openingTiming().equals(timing));
		} finally {
			try {
				if (caster != null) {
					UbwRuntime.cancel(caster);
				}
			} finally {
				AlayaConfig.load(helper.getLevel().registryAccess());
				Files.deleteIfExists(file);
			}
		}
		helper.succeed();
	}

	@GameTest(environment = "fatecraft-gametest:isolated/rho_aias_config")
	public void rhoAiasUsesConfiguredRanksAndPetals(GameTestHelper helper) throws IOException {
		Path file = Files.createTempFile("fatecraft-rank-test-", ".toml");
		try {
			Files.writeString(file, """
				[fatecraft.rho_aias]
				black_rank = "E"
				bronze_rank = "D+"
				silver_rank = "C++"
				true_name_rank = "A+"
				lower_petals = 3
				true_name_petals = 6
				""");
			check(helper, "valid rank config", FateTests.configProblems(file).isEmpty());
			ServerPlayer caster = FateTests.servant(helper, new BlockPos(3, 1, 3), FateTests.MANA, FateServants.EMIYA);
			AlayaMana.update(caster, mana -> mana.withMax(AlayaConfig.progression().manaForSync(0)));
			check(helper, "Black rank", RhoAias.rank(caster).equals(Rank.parse("E")));
			Abilities.press(caster, RhoAias.KEY.identifier(), 0, false);
			RhoAiasEntity shield = RhoAiasEntity.of(caster);
			check(helper, "configured lower petals", shield != null && shield.petals() == 3);
			Abilities.release(caster, RhoAias.KEY.identifier());
			shield.discard();
			AlayaMana.update(caster, mana -> mana.withMax(AlayaConfig.progression().manaForSync(AlayaConfig.progression().syncFor(ClassGrade.BRONZE))));
			check(helper, "Bronze rank", RhoAias.rank(caster).equals(Rank.parse("D+")));
			AlayaMana.update(caster, mana -> mana.withMax(AlayaConfig.progression().manaForSync(AlayaConfig.progression().syncFor(ClassGrade.SILVER))));
			check(helper, "Silver rank", RhoAias.rank(caster).equals(Rank.parse("C++")));
			TrueName.realize(caster);
			check(helper, "true-name rank", RhoAias.rank(caster).equals(Rank.parse("A+")));
			AlayaMana.update(caster, mana -> mana.withCurrent(mana.max()));
			Abilities.press(caster, RhoAias.KEY.identifier(), 0, false);
			shield = RhoAiasEntity.of(caster);
			check(helper, "configured true-name petals", shield != null && shield.petals() == 6);
			Abilities.release(caster, RhoAias.KEY.identifier());
			shield.discard();
			check(helper, "server panel labels", "E–A+".equals(ServantSelection.rankLabels().get(RhoAias.KEY.identifier())));
			ByteBuf buffer = Unpooled.buffer();
			try {
				OpenServantSelectionPayload payload = new OpenServantSelectionPayload(false, true, true, AlayaConfig.progression().demi(), Map.of());
				OpenServantSelectionPayload.STREAM_CODEC.encode(buffer, payload);
				check(helper, "server ranks cross the network", "E–A+".equals(OpenServantSelectionPayload.STREAM_CODEC.decode(buffer)
					.rankLabels().get(RhoAias.KEY.identifier())));
			} finally {
				buffer.release();
			}
			Files.writeString(file, "[fatecraft.rho_aias]\nblack_rank = \"not a rank\"\n");
			check(helper, "invalid rank warns once", FateTests.configProblems(file).size() == 1);
			check(helper, "invalid rank falls back", FateConfig.rhoAias().blackRank().equals(RhoAiasConfig.DEFAULTS.blackRank()));
		} finally {
			AlayaConfig.load(helper.getLevel().registryAccess());
			Files.deleteIfExists(file);
		}
		helper.succeed();
	}

	@GameTest(environment = "fatecraft-gametest:isolated/gate_vault_config")
	public void gateAndVaultUseConfiguredNumbers(GameTestHelper helper) throws IOException {
		VaultSettings saved = new Vault().settings();
		Path file = Files.createTempFile("fatecraft-config-test-", ".toml");
		try {
			Files.writeString(file, """
				[fatecraft.vault]
				stack_multiplier = 2
				small_volley = 3
				medium_volley = 7
				large_volley = 11
				huge_volley = 17
				gates_per_second = 13
				return_seconds = 9
				chains = 4
				[fatecraft.gate_of_babylon]
				single_target_min_degrees = 45
				single_target_max_degrees = 45
				""");
			check(helper, "config is valid", FateTests.configProblems(file).isEmpty());
			Vault vault = new Vault();
			check(helper, "configured tiers", vault.settings().volleys().equals(List.of(3, 7, 11, 17)));
			check(helper, "configured rate and return", vault.settings().gatesPerSecond() == 13 && vault.settings().returnSeconds() == 9);
			check(helper, "configured Enkidu chains", vault.settings().chains() == 4);
			check(helper, "old choices unchanged", saved.volleys().equals(List.of(8, 25, 54, 200)) && saved.gatesPerSecond() == 25 && saved.returnSeconds() == 60
				&& saved.chains() == 12);
			vault.section(0).insert(new ItemStack(Items.ARROW, 129));
			check(helper, "configured cell capacity", vault.section(0).entries().stream().map(entry -> entry.count()).toList().equals(List.of(128, 1)));
			check(helper, "server capacity sent to client", new VaultSectionPayload(0, vault.section(0).entries()).stackMultiplier() == 2);
			Vec3 target = helper.absoluteVec(new Vec3(4, 40, 4));
			Vec3 at = GateOfBabylon.placeAround(helper.getLevel(), target, helper.getLevel().getRandom());
			check(helper, "gate placed", at != null);
			Vec3 delta = at.subtract(target);
			check(helper, "configured elevation", Math.abs(Math.toDegrees(Math.asin(delta.y / delta.length())) - 45.0) < EPSILON);
			check(helper, "server config owns both angle bounds", FateConfig.gate().singleTargetMinDegrees() == 45.0
				&& FateConfig.gate().singleTargetMaxDegrees() == 45.0);
		} finally {
			AlayaConfig.load(helper.getLevel().registryAccess());
			Files.deleteIfExists(file);
		}
		helper.succeed();
	}
}
