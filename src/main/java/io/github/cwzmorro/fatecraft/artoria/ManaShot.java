package io.github.cwzmorro.fatecraft.artoria;

import io.github.cwzmorro.alayacore.ability.Ability;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.alayacore.rank.RankLetter;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import java.util.List;
import net.minecraft.resources.ResourceKey;

/** Rank B light gathering, thrust and golden impact blast. */
public final class ManaShot extends ArtoriaAttack {
	public static final ResourceKey<Ability> KEY = ResourceKey.create(AlayaRegistries.ABILITY_KEY, Fatecraft.id("mana_shot"));
	public static final Rank RANK = new Rank(RankLetter.B, 0);

	public ManaShot() {
		super(KEY, ArtoriaConfig.SHOT_COOLDOWN, ArtoriaAttackEntity.Kind.SHOT);
	}

	@Override
	public Rank rank() {
		return RANK;
	}

	@Override
	public List<Object> descriptionArguments() {
		return List.of(FateConfig.artoria().chargeSeconds(), FateConfig.artoria().shotRange(), FateConfig.artoria().blastRadius());
	}
}
