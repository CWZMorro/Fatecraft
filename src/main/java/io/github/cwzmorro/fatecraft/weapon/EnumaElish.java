package io.github.cwzmorro.fatecraft.weapon;

import io.github.cwzmorro.alayacore.ability.Ability;
import io.github.cwzmorro.alayacore.ability.AbilityCast;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.beam.HeldBeam;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.alayacore.rank.RankLetter;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Enuma Elish: Gilgamesh's noble phantasm with Ea in the main hand. Held: Ea winds up
 * ({@link EaConfig#enumaElishWindUpTicks}), then the beam ({@link EnumaElishBeam}) fires and lasts while held, up to the
 * held beam limit, draining mana every tick. Letting go ends it; the cooldown follows.
 */
public final class EnumaElish extends Ability {
	public static final ResourceKey<Ability> KEY = ResourceKey.create(AlayaRegistries.ABILITY_KEY, Fatecraft.id("enuma_elish"));
	public static final Rank RANK = new Rank(RankLetter.EX, 0);
	private static final double COOLDOWN_SECONDS = 60.0;

	public EnumaElish() {
		super(new Ability.Properties().noblePhantasm().cooldownSeconds(COOLDOWN_SECONDS));
	}

	@Override
	public String rankLabel() {
		return RANK.toString();
	}

	@Override
	public List<Object> descriptionArguments() {
		return List.of(FateConfig.ea().compressedAirRadius());
	}

	@Override
	public @Nullable Component cannotStart(LivingEntity entity, int mode) {
		return entity.getMainHandItem().is(FateWeapons.EA) ? null : Component.translatable("ability.fatecraft.enuma_elish.needs_ea");
	}

	@Override
	public void onStart(AbilityCast cast) {
		HeldBeam.startMovement(cast.entity());
	}

	@Override
	public void onEnd(AbilityCast cast) {
		HeldBeam.endMovement(cast.entity());
	}

	@Override
	public void onHeldTick(AbilityCast cast) {
		LivingEntity caster = cast.entity();
		if (!caster.getMainHandItem().is(FateWeapons.EA)) {
			cast.end();
			return;
		}
		int firing = cast.heldTicks() - FateConfig.ea().enumaElishWindUpTicks();
		if (firing < 0) {
			return;
		}
		if (!HeldBeam.payTick(cast, RANK, firing)) {
			return;
		}
		if (firing == 0 && caster.level() instanceof ServerLevel level) {
			EnumaElishBeam.fire(level, caster);
		}
	}
}
