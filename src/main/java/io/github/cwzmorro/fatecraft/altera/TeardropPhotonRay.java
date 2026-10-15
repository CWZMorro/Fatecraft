package io.github.cwzmorro.fatecraft.altera;

import io.github.cwzmorro.alayacore.ability.Ability;
import io.github.cwzmorro.alayacore.ability.AbilityCast;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.beam.HeldBeam;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.alayacore.rank.RankLetter;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** Altera's held rank A++ sky strike, aimed once at press. */
public final class TeardropPhotonRay extends AlteraNoblePhantasm<TeardropPhotonRayEntity> {
	public static final ResourceKey<Ability> KEY = ResourceKey.create(AlayaRegistries.ABILITY_KEY, Fatecraft.id("teardrop_photon_ray"));
	public static final TagKey<Item> SWORDS = TagKey.create(Registries.ITEM, Fatecraft.id("teardrop_photon_ray_swords"));
	public static final Rank RANK = new Rank(RankLetter.A, 2);
	public static final int CONDENSED_MODE = 0;
	public static final int SPREAD_MODE = 1;
	public static final Identifier CONDENSED = Fatecraft.id("condensed");
	public static final Identifier SPREAD = Fatecraft.id("spread");
	static final AttachmentType<TeardropPhotonRayEntity> ACTIVE = AttachmentRegistry.create(KEY.identifier().withSuffix("_active"));

	public TeardropPhotonRay() {
		super(new Properties().modes(SPREAD_MODE + 1).noblePhantasm().cooldownSeconds(
			AlteraConfig.TEARDROP_COOLDOWN_SECONDS, AlteraConfig.TEARDROP_COOLDOWN_SECONDS), ACTIVE, SWORDS);
	}

	@Override
	public String rankLabel() {
		return RANK.toString();
	}

	@Override
	public List<Object> descriptionArguments() {
		return List.of(FateConfig.altera().teardropWindUpSeconds(), AlayaConfig.ranks().beamMaxHoldSeconds(),
			FateConfig.altera().teardropRadius(), TeardropGeometry.spreadRadius(FateConfig.altera()),
			FateConfig.altera().spreadDamageMultiplier(), TeardropGeometry.impactRadius(FateConfig.altera()));
	}

	@Override
	public void onStart(AbilityCast cast) {
		if (cast.entity().level() instanceof ServerLevel level) {
			TeardropPhotonRayEntity ray = new TeardropPhotonRayEntity(Altera.TEARDROP, level);
			ray.charge(cast.entity(), cast.mode() == SPREAD_MODE);
			cast.entity().setAttached(ACTIVE, ray);
			level.addFreshEntity(ray);
		}
	}

	@Override
	public void onHeldTick(AbilityCast cast) {
		int firing = cast.heldTicks() - Durations.ticks(FateConfig.altera().teardropWindUpSeconds());
		TeardropPhotonRayEntity ray = cast.entity().getAttached(ACTIVE);
		if (ray != null && (firing < 0 || HeldBeam.payTick(cast, RANK, firing))) {
			ray.held(cast.heldTicks(), firing >= 0);
		}
	}

	@Override
	public void onReleased(AbilityCast cast) {
		cast.end();
	}
}
