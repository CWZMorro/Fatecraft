package io.github.cwzmorro.fatecraft.altera;

import io.github.cwzmorro.alayacore.ability.Ability;
import io.github.cwzmorro.alayacore.ability.AbilityCast;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.combat.ForcedAdvance;
import io.github.cwzmorro.alayacore.combat.ServantBonus;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.alayacore.rank.RankLetter;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;

/** Altera's rank A held Noble Phantasm: charge, then move forward while held. */
public final class PhotonRay extends AlteraNoblePhantasm<PhotonRayEntity> {
	public static final ResourceKey<Ability> KEY = ResourceKey.create(AlayaRegistries.ABILITY_KEY, Fatecraft.id("photon_ray"));
	public static final TagKey<Item> SWORDS = TagKey.create(Registries.ITEM, Fatecraft.id("photon_ray_swords"));
	public static final Rank RANK = new Rank(RankLetter.A, 0);
	public static final int DASH_MODE = 0;
	static final AttachmentType<PhotonRayEntity> ACTIVE = AttachmentRegistry.create(KEY.identifier().withSuffix("_active"));

	public PhotonRay() {
		super(new Properties().noblePhantasm().cooldownSeconds(AlteraConfig.DASH_COOLDOWN_SECONDS), ACTIVE, SWORDS);
	}

	@Override
	public String rankLabel() {
		return RANK.toString();
	}

	@Override
	public List<Object> descriptionArguments() {
		return List.of(FateConfig.altera().chargeSeconds(), FateConfig.altera().dashBlocksPerSecond(),
			AlayaConfig.ranks().beamDrainPerSecond(RANK), FateConfig.altera().knockbackDistance());
	}

	@Override
	public void onStart(AbilityCast cast) {
		if (cast.entity().level() instanceof ServerLevel level) {
			PhotonRayEntity ray = new PhotonRayEntity(Altera.PHOTON_RAY, level);
			ray.charge(cast.entity(), chargeTicks());
			cast.entity().setAttached(ACTIVE, ray);
			level.addFreshEntity(ray);
		}
	}

	@Override
	public void onTick(AbilityCast cast) {
		super.onTick(cast);
		PhotonRayEntity ray = cast.entity().getAttached(ACTIVE);
		if (cast.ended() || ray == null) {
			return;
		}
		if (ray.dashing()) {
			cast.pay(AlayaConfig.ranks().beamDrainPerSecond(RANK) / SharedConstants.TICKS_PER_SECOND);
			return;
		}
		ray.charging(cast.heldTicks());
		if (cast.heldTicks() >= chargeTicks()) {
			ray.launch();
		}
	}

	@Override
	public void onEnd(AbilityCast cast) {
		ForcedAdvance.stop(cast.entity());
		super.onEnd(cast);
	}

	public static int chargeTicks() {
		return Durations.ticks(FateConfig.altera().chargeSeconds());
	}

	/** Returns the pierce damage: the rank's base plus the servant's class attack, not the sword's or Strength's. */
	public static double damage(LivingEntity caster) {
		return AlayaConfig.ranks().npDamage(RANK, ServantBonus.classAttack(caster));
	}
}
