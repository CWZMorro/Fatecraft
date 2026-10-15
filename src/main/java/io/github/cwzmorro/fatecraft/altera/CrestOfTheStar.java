package io.github.cwzmorro.fatecraft.altera;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.ability.Ability;
import io.github.cwzmorro.alayacore.ability.AbilityCast;
import io.github.cwzmorro.alayacore.ability.SkillEffects;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.alayacore.rank.RankLetter;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.alayacore.util.Percent;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Crest of the Star: EX skill, guaranteed melee critical hits, a configurable Speed buff and more attack speed. */
public final class CrestOfTheStar extends Ability {
	public static final ResourceKey<Ability> KEY = ResourceKey.create(AlayaRegistries.ABILITY_KEY, Fatecraft.id("crest_of_the_star"));
	public static final Rank RANK = new Rank(RankLetter.EX, 0);
	// Runtime-only, so the expiry cannot leak through death, dimension unload or a servant change.
	private static final AttachmentType<Long> UNTIL = AttachmentRegistry.create(KEY.identifier().withSuffix("_until"));
	private static final Identifier ATTACK_SPEED = KEY.identifier().withSuffix("_attack_speed");

	public CrestOfTheStar() {
		super(new Properties().cooldownSeconds(AlteraConfig.CREST_COOLDOWN_SECONDS));
	}

	@Override
	public String rankLabel() {
		return RANK.toString();
	}

	@Override
	public List<Object> descriptionArguments() {
		AlteraConfig config = FateConfig.altera();
		return List.of(config.crestSeconds(), config.speedAmplifier() + 1, config.crestAttackSpeedPercent());
	}

	@Override
	public double startCost(LivingEntity entity, int mode) {
		return FateConfig.altera().crestMana();
	}

	@Override
	public void onStart(AbilityCast cast) {
		LivingEntity entity = cast.entity();
		int duration = Durations.ticks(FateConfig.altera().crestSeconds());
		entity.setAttached(UNTIL, entity.level().getGameTime() + duration);
		SkillEffects.boost(entity, Attributes.ATTACK_SPEED, ATTACK_SPEED, Percent.toFraction(FateConfig.altera().crestAttackSpeedPercent()));
		SkillEffects.give(entity, KEY.identifier(), new MobEffectInstance(MobEffects.SPEED, duration, FateConfig.altera().speedAmplifier()));
	}

	// The buff outlasts the press: only its time, in onTick, ends it.
	@Override
	public void onTap(AbilityCast cast) {
	}

	@Override
	public void onReleased(AbilityCast cast) {
	}

	@Override
	public void onTick(AbilityCast cast) {
		if (!active(cast.entity())) {
			cast.end();
		}
	}

	@Override
	public void onEnd(AbilityCast cast) {
		cast.entity().removeAttached(UNTIL);
		SkillEffects.unboost(cast.entity(), Attributes.ATTACK_SPEED, ATTACK_SPEED);
		SkillEffects.take(cast.entity(), KEY.identifier(), MobEffects.SPEED);
	}

	/** Returns whether the entity's Crest is up, which makes every melee hit critical, even an uncharged or sprinting one. */
	public static boolean active(LivingEntity entity) {
		return Abilities.owns(entity, KEY.identifier()) && entity.getAttachedOrElse(UNTIL, 0L) > entity.level().getGameTime();
	}
}
