package io.github.cwzmorro.fatecraft.artoria;

import io.github.cwzmorro.alayacore.ability.Ability;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.alayacore.rank.RankLetter;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import java.util.List;
import net.minecraft.resources.ResourceKey;

/** Instant rank C wind cone; the caster never moves. */
public final class StrikeAir extends ArtoriaAttack {
	public static final ResourceKey<Ability> KEY = ResourceKey.create(AlayaRegistries.ABILITY_KEY, Fatecraft.id("strike_air"));
	public static final Rank RANK = new Rank(RankLetter.C, 0);

	public StrikeAir() {
		super(KEY, ArtoriaConfig.STRIKE_COOLDOWN, ArtoriaAttackEntity.Kind.WIND);
	}

	@Override
	public boolean deflects() {
		return true;
	}

	@Override
	public Rank rank() {
		return RANK;
	}

	@Override
	public List<Object> descriptionArguments() {
		return List.of(FateConfig.artoria().strikeConeLength(), FateConfig.artoria().strikeConeDegrees());
	}
}
