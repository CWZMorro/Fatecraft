package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;

import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.altera.CrestOfTheStar;
import io.github.cwzmorro.fatecraft.artoria.ManaBurst;
import io.github.cwzmorro.fatecraft.chain.EnkiduConfig;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.shield.RhoAiasConfig;
import io.github.cwzmorro.fatecraft.weapon.EaConfig;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

public class OwnManaCostGameTests {
	private static final BlockPos START = new BlockPos(2, 2, 2);
	private static final double CLOSE = 0.000001;

	@GameTest
	public void crestOfTheStarCostsItsOwnMana(GameTestHelper helper) {
		ServerPlayer player = FateTests.servant(helper, START, FateTests.ALTERA_MANA, FateServants.ALTERA);
		check(helper, "default cost", FateConfig.altera().crestMana() == 90.0);
		double before = AlayaMana.get(player).current();
		FateTests.tap(player, CrestOfTheStar.KEY.identifier(), 0, false);
		check(helper, "spent", Math.abs(before - AlayaMana.get(player).current() - FateConfig.altera().crestMana()) < CLOSE);
		helper.succeed();
	}

	@GameTest
	public void manaBurstCostsItsOwnMana(GameTestHelper helper) {
		ServerPlayer player = FateTests.servant(helper, START, FateTests.MANA, FateServants.ARTORIA);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FateWeapons.CALIBURN));
		check(helper, "default cost", FateConfig.artoria().burstMana() == 200.0);
		double before = AlayaMana.get(player).current();
		FateTests.tap(player, ManaBurst.KEY.identifier(), 0, false);
		check(helper, "spent", Math.abs(before - AlayaMana.get(player).current() - FateConfig.artoria().burstMana()) < CLOSE);
		helper.succeed();
	}

	@GameTest
	public void compressedAirCostsItsOwnManaPerSecond(GameTestHelper helper) {
		ServerPlayer player = FateTests.player(helper, START);
		player.setGameMode(GameType.SURVIVAL);
		ItemStack ea = new ItemStack(FateWeapons.EA);
		player.setItemInHand(InteractionHand.MAIN_HAND, ea);
		check(helper, "default cost", EaConfig.DEFAULTS.compressedAirManaPerSecond() == 100.0);
		double before = AlayaMana.getOrCreate(player).current();
		player.startUsingItem(InteractionHand.MAIN_HAND);
		ea.getItem().onUseTick(helper.getLevel(), player, ea, ea.getUseDuration(player));
		double perTick = FateConfig.ea().compressedAirManaPerSecond() / SharedConstants.TICKS_PER_SECOND;
		check(helper, "one tick spent", Math.abs(before - AlayaMana.get(player).current() - perTick) < CLOSE);
		helper.succeed();
	}

	@GameTest
	public void theOtherOwnCostsHaveTheirDefaults(GameTestHelper helper) {
		check(helper, "Enkidu bind", EnkiduConfig.DEFAULTS.bindMana() == 500.0);
		check(helper, "Enkidu tighten per second", EnkiduConfig.DEFAULTS.tightenManaPerSecond() == 50.0);
		check(helper, "Rho Aias cast, the same at every rank", RhoAiasConfig.DEFAULTS.castCost() == 700.0);
		check(helper, "the shield cast row stays for other shields", AlayaConfig.ranks().shieldCastCost(Rank.parse("B")) == 600.0);
		helper.succeed();
	}
}
