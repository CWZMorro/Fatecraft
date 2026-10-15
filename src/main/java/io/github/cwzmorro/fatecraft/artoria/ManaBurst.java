package io.github.cwzmorro.fatecraft.artoria;

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
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Mana Burst: a timed buff on her, not on the sword, with Speed. It multiplies her melee hits, with any weapon or none, and
 * Mana Slash ({@link #damageMultiplier}), where vanilla Strength would add a flat amount.
 */
public final class ManaBurst extends ArtoriaAbility {
	public static final ResourceKey<Ability> KEY = ResourceKey.create(AlayaRegistries.ABILITY_KEY, Fatecraft.id("mana_burst"));
	public static final Rank RANK = new Rank(RankLetter.A, 0);
	private static final Identifier MODIFIER = Fatecraft.id("mana_burst_attack");
	// A transient expiry, synced to the caster and every tracking observer, not inferred from Speed.
	private static final AttachmentType<Long> UNTIL = AttachmentRegistry.create(KEY.identifier().withSuffix("_until"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	public static boolean active(LivingEntity entity) {
		return entity.isAlive() && entity.getAttachedOrElse(UNTIL, 0L) > entity.level().getGameTime();
	}

	public static double damageMultiplier(LivingEntity entity) {
		return active(entity) ? 1 + FateConfig.artoria().attackBonus() : 1;
	}

	public ManaBurst() {
		super(ArtoriaConfig.BURST_COOLDOWN);
	}

	@Override
	public Rank rank() {
		return RANK;
	}

	@Override
	public List<Object> descriptionArguments() {
		ArtoriaConfig c = FateConfig.artoria();
		return List.of(c.burstSeconds(), Percent.fromFraction(c.attackBonus()), c.speedAmplifier() + 1);
	}

	@Override
	public double startCost(LivingEntity entity, int mode) {
		return FateConfig.artoria().burstMana();
	}

	@Override
	public void onStart(AbilityCast cast) {
		LivingEntity entity = cast.entity();
		ArtoriaConfig c = FateConfig.artoria();
		int duration = Durations.ticks(c.burstSeconds());
		entity.setAttached(UNTIL, entity.level().getGameTime() + duration);
		SkillEffects.boost(entity, Attributes.ATTACK_DAMAGE, MODIFIER, c.attackBonus());
		SkillEffects.give(entity, KEY.identifier(), new MobEffectInstance(MobEffects.SPEED, duration, c.speedAmplifier(), false, false, true));
	}

	@Override
	public void onTick(AbilityCast cast) {
		if (cast.entity().getAttachedOrElse(UNTIL, 0L) <= cast.entity().level().getGameTime()) {
			cast.end();
		}
	}

	@Override
	public void onEnd(AbilityCast cast) {
		LivingEntity entity = cast.entity();
		entity.removeAttached(UNTIL);
		SkillEffects.unboost(entity, Attributes.ATTACK_DAMAGE, MODIFIER);
		SkillEffects.take(entity, KEY.identifier(), MobEffects.SPEED);
	}
}
