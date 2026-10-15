package io.github.cwzmorro.fatecraft.artoria;

import io.github.cwzmorro.alayacore.ability.Ability;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.alayacore.rank.RankLetter;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;

/** Rank C sword swing: one golden crescent swept across an arc in front of her. */
public final class ManaSlash extends ArtoriaAttack {
	public static final ResourceKey<Ability> KEY = ResourceKey.create(AlayaRegistries.ABILITY_KEY, Fatecraft.id("mana_slash"));
	public static final Rank RANK = new Rank(RankLetter.C, 0);

	public ManaSlash() {
		super(KEY, ArtoriaConfig.SLASH_COOLDOWN, ArtoriaAttackEntity.Kind.GOLD);
	}

	@Override
	public Rank rank() {
		return RANK;
	}

	// A sword swing, so Mana Burst raises it as it does her melee hits.
	@Override
	public double damage(LivingEntity entity) {
		return super.damage(entity) * ManaBurst.damageMultiplier(entity);
	}

	@Override
	public List<Object> descriptionArguments() {
		return List.of(FateConfig.artoria().slashRange(), FateConfig.artoria().slashArcDegrees());
	}
}
