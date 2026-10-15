package io.github.cwzmorro.fatecraft.trace;

import io.github.cwzmorro.alayacore.ability.Ability;
import io.github.cwzmorro.alayacore.ability.AbilityCast;
import io.github.cwzmorro.alayacore.ability.SkillEffects;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.alayacore.rank.RankLetter;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.alayacore.util.Percent;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Registry;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * EMIYA's Sword Body: blades stand through his body for a configured time. They return a share of a melee
 * attacker's damage (thorns), take a share off everything he suffers, and keep Regeneration on him.
 */
public final class SwordBody extends Ability {
	public static final ResourceKey<Ability> KEY = ResourceKey.create(AlayaRegistries.ABILITY_KEY, Fatecraft.id("sword_body"));
	public static final Rank RANK = new Rank(RankLetter.B, 0);
	private static final AttachmentType<Long> UNTIL = AttachmentRegistry.create(KEY.identifier().withSuffix("_until"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	public SwordBody() {
		super(new Properties().cooldownSeconds(SwordBodyConfig.COOLDOWN_SECONDS));
	}

	public static boolean active(LivingEntity entity) {
		return entity.isAlive() && entity.getAttachedOrElse(UNTIL, 0L) > entity.level().getGameTime();
	}

	public static float reduced(LivingEntity entity, DamageSource source, float damage) {
		if (!active(entity) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return damage;
		}
		return damage * (float) (1.0 - Percent.toFraction(FateConfig.swordBody().reductionPercent()));
	}

	public static void register() {
		Registry.register(AlayaRegistries.ABILITY, KEY, new SwordBody());
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (blocked || !active(entity) || !(entity.level() instanceof ServerLevel level)
				|| !(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker
				|| source.is(DamageTypes.THORNS) || attacker == entity) {
				return;
			}
			// The share of what the attacker dealt, not of what got through, so the reduction does not also shrink the thorns.
			attacker.hurtServer(level, level.damageSources().thorns(entity), baseDamage * (float) Percent.toFraction(FateConfig.swordBody().thornsPercent()));
		});
	}

	@Override
	public String rankLabel() {
		return RANK.toString();
	}

	@Override
	public List<Object> descriptionArguments() {
		SwordBodyConfig c = FateConfig.swordBody();
		return List.of(c.seconds(), c.thornsPercent(), c.reductionPercent(), c.regenerationAmplifier() + 1);
	}

	@Override
	public double startCost(LivingEntity entity, int mode) {
		return AlayaConfig.ranks().skillCost(RANK);
	}

	@Override
	public void onStart(AbilityCast cast) {
		LivingEntity entity = cast.entity();
		int duration = Durations.ticks(FateConfig.swordBody().seconds());
		entity.setAttached(UNTIL, entity.level().getGameTime() + duration);
		SkillEffects.give(entity, KEY.identifier(), new MobEffectInstance(MobEffects.REGENERATION, duration, FateConfig.swordBody().regenerationAmplifier(), false, false, true));
	}

	// The blades outlast the press: only their time, in onTick, ends them.
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
		LivingEntity entity = cast.entity();
		entity.removeAttached(UNTIL);
		SkillEffects.take(entity, KEY.identifier(), MobEffects.REGENERATION);
	}
}
