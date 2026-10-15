package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.alayacore.util.Percent;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.trace.SwordBody;
import io.github.cwzmorro.fatecraft.trace.SwordBodyConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.block.Blocks;

public class SwordBodyGameTests {
	private static final BlockPos START = new BlockPos(2, 2, 2);
	private static final BlockPos ATTACKER = new BlockPos(2, 2, 4);
	private static final float HIT = 8.0F;
	private static final double EPSILON = 0.01;
	private static final int SETTLE_TICKS = 2;
	private static final int HIT_TICKS = 80;
	private static final int CANCEL_TICKS = 40;
	private static final int EXPIRY_TICKS = (int) (SwordBodyConfig.DEFAULT_SECONDS * SharedConstants.TICKS_PER_SECOND) + 60;

	@GameTest(maxTicks = HIT_TICKS)
	public void reducesDamageAndThrowsMeleeAttackersBackAndRegenerates(GameTestHelper helper) {
		ServerPlayer player = emiya(helper);
		FateTests.tap(player, SwordBody.KEY.identifier(), 0, false);
		SwordBodyConfig config = FateConfig.swordBody();
		check(helper, "ability active", Abilities.isActive(player, SwordBody.KEY.identifier()) && SwordBody.active(player));
		check(helper, "regeneration at the configured level for the configured time", player.getEffect(MobEffects.REGENERATION) != null
			&& player.getEffect(MobEffects.REGENERATION).getAmplifier() == config.regenerationAmplifier()
			&& player.getEffect(MobEffects.REGENERATION).getDuration() == Durations.ticks(config.seconds()));
		Husk attacker = attacker(helper);
		double full = lostBy(helper, emiya(helper), mobAttack(helper, attacker));
		double lost = lostBy(helper, player, mobAttack(helper, attacker));
		double expectedLoss = full * (1 - Percent.toFraction(config.reductionPercent()));
		check(helper, "reduction: lost " + lost + ", expected " + expectedLoss, Math.abs(lost - expectedLoss) < EPSILON);
		double expectedThorns = HIT * Percent.toFraction(config.thornsPercent());
		check(helper, "thorns: attacker lost " + (attacker.getMaxHealth() - attacker.getHealth()) + ", expected " + expectedThorns,
			Math.abs(attacker.getMaxHealth() - attacker.getHealth() - expectedThorns) < EPSILON);
		helper.succeed();
	}

	@GameTest(maxTicks = HIT_TICKS)
	public void withoutTheBladesNothingIsReducedReturnedOrHealed(GameTestHelper helper) {
		ServerPlayer player = emiya(helper);
		Husk attacker = attacker(helper);
		check(helper, "damage is taken", lostBy(helper, player, mobAttack(helper, attacker)) > 0);
		check(helper, "attacker unhurt", attacker.getHealth() == attacker.getMaxHealth());
		check(helper, "no regeneration", !player.hasEffect(MobEffects.REGENERATION));
		helper.succeed();
	}

	@GameTest(maxTicks = HIT_TICKS)
	public void projectilesAreReducedButNotReturned(GameTestHelper helper) {
		ServerPlayer player = emiya(helper);
		FateTests.tap(player, SwordBody.KEY.identifier(), 0, false);
		Husk shooter = attacker(helper);
		DamageSource shot = helper.getLevel().damageSources().arrow(helper.spawn(EntityType.ARROW, ATTACKER), shooter);
		double full = lostBy(helper, emiya(helper), shot);
		double lost = lostBy(helper, player, shot);
		double expectedLoss = full * (1 - Percent.toFraction(FateConfig.swordBody().reductionPercent()));
		check(helper, "arrow reduced: lost " + lost + ", expected " + expectedLoss, Math.abs(lost - expectedLoss) < EPSILON);
		check(helper, "shooter unhurt", shooter.getHealth() == shooter.getMaxHealth());
		helper.succeed();
	}

	@GameTest(maxTicks = EXPIRY_TICKS)
	public void endsAfterItsDuration(GameTestHelper helper) {
		ServerPlayer player = emiya(helper);
		// Made up front: a servant registers its ability tick, which a scheduled callback must not do.
		ServerPlayer unprotected = emiya(helper);
		// A mock player's effects don't count down by themselves, as a real player's do.
		helper.onEachTick(player::baseTick);
		FateTests.tap(player, SwordBody.KEY.identifier(), 0, false);
		helper.runAfterDelay(Durations.ticks(FateConfig.swordBody().seconds()) + SETTLE_TICKS, () -> {
			check(helper, "ability ended", !Abilities.isActive(player, SwordBody.KEY.identifier()) && !SwordBody.active(player));
			check(helper, "regeneration gone", !player.hasEffect(MobEffects.REGENERATION));
			Husk attacker = attacker(helper);
			double full = lostBy(helper, unprotected, mobAttack(helper, attacker));
			double lost = lostBy(helper, player, mobAttack(helper, attacker));
			check(helper, "damage not reduced after the end: lost " + lost + ", unprotected " + full, Math.abs(lost - full) < EPSILON);
			check(helper, "no thorns", attacker.getHealth() == attacker.getMaxHealth());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = CANCEL_TICKS)
	public void cancellingTakesBackItsOwnRegenerationOnly(GameTestHelper helper) {
		ServerPlayer player = emiya(helper);
		FateTests.tap(player, SwordBody.KEY.identifier(), 0, false);
		Abilities.end(player, SwordBody.KEY.identifier());
		check(helper, "regeneration gone", !player.hasEffect(MobEffects.REGENERATION));
		helper.succeed();
	}

	private static DamageSource mobAttack(GameTestHelper helper, Husk attacker) {
		return helper.getLevel().damageSources().mobAttack(attacker);
	}

	private static double lostBy(GameTestHelper helper, ServerPlayer player, DamageSource source) {
		float before = player.getHealth();
		player.hurtServer(helper.getLevel(), source, HIT);
		return before - player.getHealth();
	}

	private static ServerPlayer emiya(GameTestHelper helper) {
		for (BlockPos pos : BlockPos.betweenClosed(START.offset(-2, -1, -2), START.offset(4, 4, 4))) {
			helper.setBlock(pos, Blocks.AIR);
		}
		return FateTests.servant(helper, START, FateTests.MANA, FateServants.EMIYA);
	}

	private static Husk attacker(GameTestHelper helper) {
		Husk attacker = FateTests.mob(helper, ATTACKER);
		attacker.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
		attacker.getAttribute(Attributes.ARMOR).setBaseValue(0);
		attacker.setHealth(attacker.getMaxHealth());
		return attacker;
	}
}
