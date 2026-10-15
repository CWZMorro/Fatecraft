package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.combat.ServantBonus;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.chain.ChainBind;
import io.github.cwzmorro.fatecraft.chain.ChainLink;
import io.github.cwzmorro.fatecraft.chain.Chains;
import io.github.cwzmorro.fatecraft.chain.Enkidu;
import io.github.cwzmorro.fatecraft.chain.EnkiduConfig;
import io.github.cwzmorro.fatecraft.vault.VaultSettings;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

// Game-test players don't tick, so each test ticks its Gilgamesh's abilities itself.
public class EnkiduGameTests {
	private static final BlockPos STANDING = new BlockPos(4, 1, 1);
	private static final BlockPos TARGET = new BlockPos(4, 1, 6);
	private static final double LOW_SYNC_MANA = 700.0;
	private static final int BOUND_TICKS = 30;
	private static final int FIRST_HIT_TICKS = 3;
	private static final int NEXT_HIT_TICKS = 22;
	private static final double CLOSE = 0.01;
	private static final Vec3 SHIELD = new Vec3(4.5, 2.5, 3.5);
	private static final float FACING_HIM = 180.0F;
	private static final Rank SHIELD_RANK = Rank.parse("B");

	private static ServerPlayer gilgamesh(GameTestHelper helper, LivingEntity target) {
		return gilgamesh(helper, target, FateTests.MANA);
	}

	private static ServerPlayer gilgamesh(GameTestHelper helper, LivingEntity target, double mana) {
		ServerPlayer player = FateTests.gilgamesh(helper, STANDING, mana);
		player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
		return player;
	}

	private static Mob golem(GameTestHelper helper) {
		Mob golem = helper.spawn(EntityType.IRON_GOLEM, TARGET);
		golem.setNoAi(true);
		return golem;
	}

	@GameTest
	public void defaultsMatchTheDesignedValues(GameTestHelper helper) {
		EnkiduConfig config = EnkiduConfig.DEFAULTS;
		check(helper, "chain 100 at 0% sync", Math.abs(config.chainHp(0.0, 0.0) - 100.0) < CLOSE);
		check(helper, "chain 500 at full sync", Math.abs(config.chainHp(1.0, 0.0) - 500.0) < CLOSE);
		check(helper, "x1.4 a point of divinity", Math.abs(config.chainHp(1.0, 1.0) - 700.0) < CLOSE);
		check(helper, "first hit 19", Math.abs(config.tighten(true, 0.0) - 19.0) < CLOSE);
		check(helper, "then 14", Math.abs(config.tighten(false, 0.0) - 14.0) < CLOSE);
		check(helper, "about 57 at divinity 10", Math.abs(config.tighten(false, 10.0) - 56.6) < 0.1);
		check(helper, "keeps 65% of its attack", Math.abs(config.attackKept(0.0) - 0.65) < CLOSE);
		helper.succeed();
	}

	@GameTest(maxTicks = BOUND_TICKS * 2 + 2)
	public void aBindHoldsTheTargetUntilASecondTap(GameTestHelper helper) {
		Mob target = golem(helper);
		ServerPlayer player = gilgamesh(helper, target);
		double before = AlayaMana.get(player).current();
		FateTests.tap(player, Enkidu.KEY.identifier(), Enkidu.BIND, false);
		check(helper, "the bind's mana", Math.abs(before - AlayaMana.get(player).current() - FateConfig.enkidu().bindMana()) < CLOSE);
		helper.runAfterDelay(BOUND_TICKS, () -> {
			check(helper, "bound", target.hasEffect(Chains.BOUND));
			Vec3 at = target.position();
			target.teleportTo(at.x + 2, at.y, at.z);
		});
		helper.runAfterDelay(BOUND_TICKS + 2, () -> {
			check(helper, "held where it was", target.position().distanceTo(helper.absoluteVec(Vec3.atBottomCenterOf(TARGET))) < 0.1);
			FateTests.tap(player, Enkidu.KEY.identifier(), Enkidu.BIND, false);
		});
		helper.runAfterDelay(BOUND_TICKS + 4, () -> {
			check(helper, "let go", !target.hasEffect(Chains.BOUND) && ChainBind.of(player) == null);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = BOUND_TICKS + NEXT_HIT_TICKS * 2)
	public void tighteningHitsHardFirstThenLessEachSecond(GameTestHelper helper) {
		Mob target = golem(helper);
		ServerPlayer player = gilgamesh(helper, target);
		// Each second's hit adds the servant's class attack on top.
		double first = FateConfig.enkidu().tighten(true, 0.0) + ServantBonus.classAttack(player);
		double next = FateConfig.enkidu().tighten(false, 0.0) + ServantBonus.classAttack(player);
		// Held: the chains bind first, then tighten from the tick they reach it.
		Abilities.press(player, Enkidu.KEY.identifier(), Enkidu.TIGHTEN, false);
		int bound = ChainBind.of(player).boundAt();
		helper.runAfterDelay(bound + FIRST_HIT_TICKS, () -> check(helper, "the first hit, lost " + (target.getMaxHealth() - target.getHealth()),
			Math.abs(target.getMaxHealth() - target.getHealth() - first) < CLOSE));
		helper.runAfterDelay(bound + FIRST_HIT_TICKS + NEXT_HIT_TICKS, () -> {
			check(helper, "then the next, lost " + (target.getMaxHealth() - target.getHealth()),
				Math.abs(target.getMaxHealth() - target.getHealth() - first - next) < CLOSE);
			Abilities.release(player, Enkidu.KEY.identifier());
			check(helper, "still bound after letting go", ChainBind.of(player) != null);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = BOUND_TICKS + FIRST_HIT_TICKS + 2)
	public void aShieldBetweenThemTakesTheTighteningInsteadOfTheTarget(GameTestHelper helper) {
		Mob target = golem(helper);
		ServerPlayer player = gilgamesh(helper, target);
		FateTestShields.Disc shield = helper.spawn(FateTestShields.DISC, SHIELD);
		shield.setYRot(FACING_HIM);
		shield.deploy(SHIELD_RANK);
		Abilities.press(player, Enkidu.KEY.identifier(), Enkidu.TIGHTEN, false);
		int bound = ChainBind.of(player).boundAt();
		helper.runAfterDelay(bound + FIRST_HIT_TICKS, () -> {
			check(helper, "the target is unhurt, lost " + (target.getMaxHealth() - target.getHealth()), target.getHealth() == target.getMaxHealth());
			check(helper, "the shield took the hit", shield.hp() < shield.maxHp() && shield.hitTimes.size() == 1);
			Abilities.release(player, Enkidu.KEY.identifier());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = BOUND_TICKS)
	public void aBoundTargetsAttackIsCut(GameTestHelper helper) {
		Mob target = golem(helper);
		double attack = target.getAttributeValue(Attributes.ATTACK_DAMAGE);
		ServerPlayer player = gilgamesh(helper, target);
		FateTests.tap(player, Enkidu.KEY.identifier(), Enkidu.BIND, false);
		helper.runAfterDelay(BOUND_TICKS - 1, () -> {
			check(helper, "its attack cut", Math.abs(target.getAttributeValue(Attributes.ATTACK_DAMAGE) - attack * FateConfig.enkidu().attackKept(0.0)) < CLOSE);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 300)
	public void aBoundMobWearsTheChainDown(GameTestHelper helper) {
		Mob target = golem(helper);
		ServerPlayer player = gilgamesh(helper, target, LOW_SYNC_MANA);
		FateTests.tap(player, Enkidu.KEY.identifier(), Enkidu.BIND, false);
		helper.runAfterDelay(BOUND_TICKS, () -> check(helper, "bound", ChainBind.of(player) != null));
		// A golem's 15 attack a second against about 190 HP at 23% sync: free within 11 seconds.
		helper.startSequence().thenIdle(BOUND_TICKS + 1)
			.thenWaitUntil(() -> check(helper, "worn through", ChainBind.of(player) == null)).thenSucceed();
	}

	@GameTest(maxTicks = BOUND_TICKS + 4)
	public void onlyOthersBreakTheChainByStrikingIt(GameTestHelper helper) {
		Mob target = golem(helper);
		ServerPlayer player = gilgamesh(helper, target);
		ServerPlayer other = FateTests.player(helper, TARGET.east(2));
		FateTests.tap(player, Enkidu.KEY.identifier(), Enkidu.BIND, false);
		helper.runAfterDelay(BOUND_TICKS, () -> {
			List<ChainLink> links = helper.getLevel().getEntitiesOfClass(ChainLink.class, target.getBoundingBox().inflate(4.0));
			check(helper, "a link a chain", links.size() == VaultSettings.DEFAULTS.chains());
			ChainLink link = links.getFirst();
			check(helper, "the caster can't", !link.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1000.0F));
			check(helper, "still bound", ChainBind.of(player) != null);
			check(helper, "anyone else can", link.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(other), 1000.0F));
			check(helper, "broken", ChainBind.of(player) == null);
			helper.succeed();
		});
	}
}
