package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.ability.SkillEffects;
import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.servant.ServantChoice;
import io.github.cwzmorro.alayacore.servant.ServantStorage;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.alayacore.util.Percent;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.altera.Altera;
import io.github.cwzmorro.fatecraft.altera.AlteraConfig;
import io.github.cwzmorro.fatecraft.altera.CrestOfTheStar;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Husk;

public class AlteraSkillGameTests {
	private static final BlockPos ORIGIN = new BlockPos(3, 8, 3);
	// A literal for the annotation's timeout; the test checks it against the config's default.
	private static final int CREST_TICKS = 500;
	private static final Identifier SAME_SPEED = FateTests.id("same_speed");
	private static final int PASSIVE_CHECK_TICKS = 60;
	private static final float TARGET_HP = 1_000.0F;
	private static final float CRITICAL_MULTIPLIER = 1.5F;
	private static final double EPSILON = 0.01;

	@GameTest(maxTicks = CREST_TICKS + 5)
	public void crestGivesTheConfiguredSpeedAndExpiresWhenItsTimeIsUp(GameTestHelper helper) {
		check(helper, "the timeout matches the default duration", CREST_TICKS == Durations.ticks(AlteraConfig.DEFAULTS.crestSeconds()));
		ServerPlayer player = FateTests.altera(helper, ORIGIN, FateTests.ALTERA_MANA);
		// A mock player's effects don't count down by themselves, as a real player's do.
		helper.onEachTick(player::baseTick);
		double before = AlayaMana.get(player).current();
		double attackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
		FateTests.tap(player, CrestOfTheStar.KEY.identifier(), 0, false);
		double cost = AlayaRegistries.ABILITY.getValueOrThrow(CrestOfTheStar.KEY).startCost(player, 0);
		check(helper, "the skill costs its start cost without true name", Math.abs(before - AlayaMana.get(player).current() - cost) < EPSILON);
		check(helper, "Speed lasts the configured time at the configured level", player.getEffect(MobEffects.SPEED).getDuration() == CREST_TICKS
			&& player.getEffect(MobEffects.SPEED).getAmplifier() == AlteraConfig.DEFAULTS.speedAmplifier());
		check(helper, "Speed II, 25 seconds, +30% attack speed, 90 mana and a 60 second cooldown by default", AlteraConfig.DEFAULTS.speedAmplifier() == 1
			&& AlteraConfig.DEFAULTS.crestSeconds() == 25 && AlteraConfig.DEFAULTS.crestAttackSpeedPercent() == 30
			&& AlteraConfig.DEFAULTS.crestMana() == 90 && AlteraConfig.CREST_COOLDOWN_SECONDS == 60);
		check(helper, "total attack speed raised by the configured share", Math.abs(player.getAttributeValue(Attributes.ATTACK_SPEED)
			- attackSpeed * (1 + Percent.toFraction(FateConfig.altera().crestAttackSpeedPercent()))) < EPSILON);
		helper.runAfterDelay(CREST_TICKS - 1, () -> check(helper, "still active before expiry", CrestOfTheStar.active(player)));
		helper.runAfterDelay(CREST_TICKS + 1, () -> {
			check(helper, "critical buff expired", !CrestOfTheStar.active(player));
			check(helper, "speed cleaned", !player.hasEffect(MobEffects.SPEED));
			check(helper, "attack speed restored", Math.abs(player.getAttributeValue(Attributes.ATTACK_SPEED) - attackSpeed) < EPSILON);
			check(helper, "its cooldown", Math.abs(Abilities.cooldownSecondsLeft(player, CrestOfTheStar.KEY.identifier(), 0)
				- AlayaConfig.abilities().cooldownSeconds(CrestOfTheStar.KEY.identifier(),
					AlayaRegistries.ABILITY.getValue(CrestOfTheStar.KEY.identifier()), 0)) < 1.0);
			helper.succeed();
		});
	}

	@GameTest
	public void crestMakesGroundedAndUnchargedMeleeCritical(GameTestHelper helper) {
		ServerPlayer player = FateTests.altera(helper, ORIGIN, FateTests.ALTERA_MANA);
		Husk target = FateTests.mob(helper, ORIGIN.above(2));
		target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(TARGET_HP);
		target.setHealth(TARGET_HP);
		target.getAttribute(Attributes.ARMOR).setBaseValue(0);
		player.setOnGround(true);
		player.setSprinting(true);
		// Crest also raises attack speed, which changes a hit's charge and class attack share, so the normal hit gets the same speed.
		SkillEffects.boost(player, Attributes.ATTACK_SPEED, SAME_SPEED, Percent.toFraction(FateConfig.altera().crestAttackSpeedPercent()));
		player.resetAttackStrengthTicker();
		player.attack(target);
		SkillEffects.unboost(player, Attributes.ATTACK_SPEED, SAME_SPEED);
		float normal = TARGET_HP - target.getHealth();
		target.setHealth(TARGET_HP);
		target.invulnerableTime = 0;
		player.resetAttackStrengthTicker();
		FateTests.tap(player, CrestOfTheStar.KEY.identifier(), 0, false);
		player.attack(target);
		float critical = TARGET_HP - target.getHealth();
		check(helper, "vanilla damage gains the critical multiplier at zero charge: normal=" + normal + ", critical=" + critical, normal > 0
			&& Math.abs(critical - normal * CRITICAL_MULTIPLIER) < EPSILON);
		helper.succeed();
	}

	@GameTest(maxTicks = PASSIVE_CHECK_TICKS + 5)
	public void naturalBodyHealsAndRejectsWeaknessAndEndsWithAltera(GameTestHelper helper) {
		Cow caster = helper.spawn(EntityType.COW, ORIGIN);
		caster.setNoAi(true);
		caster.setNoGravity(true);
		caster.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, PASSIVE_CHECK_TICKS));
		caster.setAttached(ServantStorage.CHOICE, ServantChoice.servant(AlayaRegistries.SERVANT.getKey(FateServants.ALTERA)));
		caster.setHealth(caster.getMaxHealth() / 2);
		float health = caster.getHealth();
		Altera.tickPassive(caster);
		check(helper, "weakness removed", !caster.hasEffect(MobEffects.WEAKNESS));
		check(helper, "weakness refused", !caster.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, PASSIVE_CHECK_TICKS)));
		helper.runAfterDelay(PASSIVE_CHECK_TICKS, () -> {
			check(helper, "regeneration heals, not just a constantly reset effect: " + health + " -> " + caster.getHealth()
				+ ", regeneration=" + caster.getEffect(MobEffects.REGENERATION) + ", ticked " + caster.tickCount + ", alive=" + caster.isAlive(),
				caster.getHealth() > health);
			check(helper, "Regeneration and Strength at the configured level", caster.getEffect(MobEffects.REGENERATION).getAmplifier() == FateConfig.altera().passiveAmplifier()
				&& caster.getEffect(MobEffects.STRENGTH).getAmplifier() == FateConfig.altera().passiveAmplifier());
			caster.setAttached(ServantStorage.CHOICE, ServantChoice.human());
			Altera.tickPassive(caster);
			check(helper, "passive removed after leaving", !caster.hasEffect(MobEffects.STRENGTH) && !caster.hasEffect(MobEffects.REGENERATION));
			helper.succeed();
		});
	}

	@GameTest
	public void naturalBodyIsRegenerationOneAndStrengthOneByDefault(GameTestHelper helper) {
		check(helper, "both level I", AlteraConfig.DEFAULTS.passiveAmplifier() == 0);
		helper.succeed();
	}

	@GameTest
	public void photonRayIsNotGivenOnSelectionOrJoin(GameTestHelper helper) {
		ServerPlayer player = FateTests.player(helper, ORIGIN);
		FateTests.become(helper, player, FateServants.ALTERA);
		check(helper, "selection gives no sword", player.getInventory().countItem(FateWeapons.PHOTON_RAY) == 0);
		ServerPlayerEvents.JOIN.invoker().onJoin(player);
		check(helper, "join gives no sword", player.getInventory().countItem(FateWeapons.PHOTON_RAY) == 0);
		ServerPlayer returning = FateTests.servant(helper, ORIGIN, FateTests.ALTERA_MANA, FateServants.ALTERA);
		ServerPlayerEvents.JOIN.invoker().onJoin(returning);
		check(helper, "existing Altera receives no sword on join", returning.getInventory().countItem(FateWeapons.PHOTON_RAY) == 0);
		helper.succeed();
	}
}
