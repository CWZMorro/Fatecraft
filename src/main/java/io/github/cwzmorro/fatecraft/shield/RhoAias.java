package io.github.cwzmorro.fatecraft.shield;

import io.github.cwzmorro.alayacore.ability.Ability;
import io.github.cwzmorro.alayacore.ability.AbilityCast;
import io.github.cwzmorro.alayacore.ability.AbilitySetting;
import io.github.cwzmorro.alayacore.ability.AbilitySettings;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.progression.Progression;
import io.github.cwzmorro.alayacore.progression.TrueName;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** EMIYA's projected shield: full on the press, follows the head while pressed, repairs once held. */
public final class RhoAias extends Ability {
	public static final ResourceKey<Ability> KEY = ResourceKey.create(AlayaRegistries.ABILITY_KEY, Fatecraft.id("rho_aias"));
	private static final String DISTANCE = "distance";
	private static final String SIZE = "size";

	public RhoAias() {
		super(new Ability.Properties());
	}

	private static AbilitySetting distanceSetting() {
		RhoAiasConfig config = FateConfig.rhoAias();
		return new AbilitySetting(DISTANCE, config.minDistance(), config.maxDistance(), RhoAiasConfig.STEP, config.defaultDistance());
	}

	private static AbilitySetting sizeSetting() {
		RhoAiasConfig config = FateConfig.rhoAias();
		return new AbilitySetting(SIZE, config.minSize(), config.maxSize(), RhoAiasConfig.STEP, config.defaultSize());
	}

	@Override
	public List<AbilitySetting> settings() {
		return List.of(distanceSetting(), sizeSetting());
	}

	static double distance(LivingEntity caster) {
		return AbilitySettings.value(caster, KEY.identifier(), distanceSetting());
	}

	static double size(LivingEntity caster) {
		return AbilitySettings.value(caster, KEY.identifier(), sizeSetting());
	}

	/** Returns the rank from the caster's current class grade, or A once its true name is realized; earned grades don't count. */
	public static Rank rank(LivingEntity caster) {
		RhoAiasConfig config = FateConfig.rhoAias();
		if (caster instanceof ServerPlayer player && TrueName.isRealized(player)) {
			return config.trueNameRank();
		}
		return switch (Progression.grade(caster)) {
			case BLACK -> config.blackRank();
			case BRONZE -> config.bronzeRank();
			case SILVER, GOLD, GRAND -> config.silverRank();
		};
	}

	@Override
	public String rankLabel() {
		return Rank.range(FateConfig.rhoAias().blackRank(), FateConfig.rhoAias().trueNameRank());
	}

	@Override
	public List<Object> descriptionArguments() {
		return List.of(Durations.text(AlayaConfig.shields().tapSeconds()));
	}

	@Override
	public double startCost(LivingEntity entity, int mode) {
		return RhoAiasEntity.isUp(entity) ? 0.0 : FateConfig.rhoAias().castCost();
	}

	@Override
	public void onStart(AbilityCast cast) {
		if (RhoAiasEntity.isUp(cast.entity())) {
			RhoAiasEntity.dismiss(cast.entity());
			cast.end();
		} else if (cast.entity().level() instanceof ServerLevel level) {
			RhoAiasEntity.project(level, cast.entity(), rank(cast.entity()));
		}
	}

	@Override
	public void onTick(AbilityCast cast) {
		RhoAiasEntity shield = RhoAiasEntity.of(cast.entity());
		if (shield == null) {
			cast.end();
		} else {
			shield.follow(cast.entity());
		}
	}

	@Override
	public void onPressedAgain(AbilityCast cast) {
		RhoAiasEntity.dismiss(cast.entity());
		cast.end();
	}

	@Override
	public void onEnd(AbilityCast cast) {
		RhoAiasEntity shield = RhoAiasEntity.of(cast.entity());
		if (shield != null) {
			shield.stopFollowing();
		}
	}

	@Override
	public void onHeldTick(AbilityCast cast) {
		RhoAiasEntity shield = RhoAiasEntity.of(cast.entity());
		if (shield == null) {
			cast.end();
		} else if (!shield.hold(cast.entity())) {
			cast.endOutOfMana();
		}
	}
}
