package io.github.cwzmorro.fatecraft.altera;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.ability.Ability;
import io.github.cwzmorro.alayacore.ability.PassiveAbility;
import io.github.cwzmorro.alayacore.ability.SkillEffects;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.api.AlayaServants;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;

/** Altera's feature registrations and Natural Body passive. */
public final class Altera {
	// Natural Body lasts two Regeneration I periods and is renewed below one, so its timer never resets every tick.
	private static final int PASSIVE_TICKS = 2 * SkillEffects.REGENERATION_PERIOD_TICKS;
	private static final float ENTITY_SIZE = 1.0F;
	private static final int DASH_TRACKING_CHUNKS = 8;
	private static final int SKY_TRACKING_CHUNKS = 16;
	private static final int UPDATE_INTERVAL_TICKS = 1;
	public static final ResourceKey<Ability> NATURAL_BODY = ResourceKey.create(AlayaRegistries.ABILITY_KEY, Fatecraft.id("natural_body"));
	private static final AttachmentType<Boolean> PASSIVE_APPLIED = AttachmentRegistry.create(Fatecraft.id("natural_body_applied"));
	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Fatecraft.id("photon_ray"));
	public static final EntityType<PhotonRayEntity> PHOTON_RAY = Fatecraft.entity("photon_ray",
		EntityType.Builder.<PhotonRayEntity>of(PhotonRayEntity::new, MobCategory.MISC).sized(ENTITY_SIZE, ENTITY_SIZE)
			.noSave().clientTrackingRange(DASH_TRACKING_CHUNKS).updateInterval(UPDATE_INTERVAL_TICKS));
	public static final EntityType<TeardropPhotonRayEntity> TEARDROP = Fatecraft.entity("teardrop_photon_ray",
		EntityType.Builder.<TeardropPhotonRayEntity>of(TeardropPhotonRayEntity::new, MobCategory.MISC).sized(ENTITY_SIZE, ENTITY_SIZE)
			.noSave().clientTrackingRange(SKY_TRACKING_CHUNKS).updateInterval(UPDATE_INTERVAL_TICKS));
	public static final DataComponentType<WeaponFlip> FLIP = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Fatecraft.id("weapon_flip"),
		DataComponentType.<WeaponFlip>builder().networkSynchronized(WeaponFlip.STREAM_CODEC).ignoreSwapAnimation().build());

	private Altera() {
	}

	public static void init() {
		Registry.register(AlayaRegistries.ABILITY, CrestOfTheStar.KEY, new CrestOfTheStar());
		Registry.register(AlayaRegistries.ABILITY, PhotonRay.KEY, new PhotonRay());
		Registry.register(AlayaRegistries.ABILITY, TeardropPhotonRay.KEY, new TeardropPhotonRay());
		Registry.register(AlayaRegistries.ABILITY, NATURAL_BODY, new PassiveAbility());
		AlayaServants.BECAME_SERVANT.register((player, servant) -> tickPassive(player));
	}

	public static boolean hasNaturalBody(LivingEntity entity) {
		return Abilities.owns(entity, NATURAL_BODY.identifier());
	}

	/** Keeps Natural Body's Regeneration and Strength on the entity and removes Weakness if it has Natural Body; server side only. */
	public static void tickPassive(LivingEntity entity) {
		if (entity.level().isClientSide()) {
			return;
		}
		if (entity.isAlive() && hasNaturalBody(entity)) {
			int amplifier = FateConfig.altera().passiveAmplifier();
			refreshEffect(entity, MobEffects.REGENERATION, amplifier);
			refreshEffect(entity, MobEffects.STRENGTH, amplifier);
			entity.setAttached(PASSIVE_APPLIED, true);
			entity.removeEffect(MobEffects.WEAKNESS);
		} else if (entity.removeAttached(PASSIVE_APPLIED) != null) {
			SkillEffects.take(entity, NATURAL_BODY.identifier(), MobEffects.REGENERATION);
			SkillEffects.take(entity, NATURAL_BODY.identifier(), MobEffects.STRENGTH);
		}
	}

	private static void refreshEffect(LivingEntity entity, Holder<MobEffect> effect, int amplifier) {
		MobEffectInstance current = entity.getEffect(effect);
		if (current == null || current.getAmplifier() < amplifier || current.getDuration() < SkillEffects.REGENERATION_PERIOD_TICKS) {
			SkillEffects.give(entity, NATURAL_BODY.identifier(), new MobEffectInstance(effect, PASSIVE_TICKS, amplifier, true, false, true));
		}
	}
}
