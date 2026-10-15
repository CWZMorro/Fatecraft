package io.github.cwzmorro.fatecraft.chain;

import io.github.cwzmorro.alayacore.ability.Ability;
import io.github.cwzmorro.alayacore.ability.AbilityCast;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.combat.Aim;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.vault.Vault;
import io.github.cwzmorro.fatecraft.vault.VaultSettings;
import java.util.Comparator;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Gilgamesh's Enkidu: chains out of the Gate of Babylon bind one target. A tap binds what is under the crosshair (else
 * the nearest living thing in reach) or, while bound, lets go. Held, it tightens the bound target and hurts it (binding
 * first if nothing is bound); letting go keeps the bind.
 */
public final class Enkidu extends Ability {
	public static final ResourceKey<Ability> KEY = ResourceKey.create(AlayaRegistries.ABILITY_KEY, Fatecraft.id("enkidu"));
	public static final int BIND = 0;
	public static final int TIGHTEN = 1;
	private static final int MODES = 2;

	public Enkidu() {
		super(new Ability.Properties().modes(MODES));
	}

	@Override
	public String rankLabel() {
		return UNKNOWN_RANK;
	}

	@Override
	public void onStart(AbilityCast cast) {
		LivingEntity caster = cast.entity();
		ChainBind bind = ChainBind.of(caster);
		if (cast.mode() == BIND) {
			if (bind != null) {
				bind.release();
			} else {
				startBind(cast);
			}
			cast.end();
		} else if (bind == null && !startBind(cast)) {
			cast.end();
		}
	}

	private static boolean startBind(AbilityCast cast) {
		LivingEntity caster = cast.entity();
		if (!(caster.level() instanceof ServerLevel level)) {
			return false;
		}
		LivingEntity target = target(caster);
		if (target == null) {
			if (caster instanceof ServerPlayer player) {
				player.sendOverlayMessage(Component.translatable("ability.fatecraft.enkidu.nothing_to_bind"));
			}
			return false;
		}
		if (!cast.pay(FateConfig.enkidu().bindMana())) {
			return false;
		}
		ChainBind.start(level, caster, target, chains(caster));
		return true;
	}

	private static @Nullable LivingEntity target(LivingEntity caster) {
		EnkiduConfig config = FateConfig.enkidu();
		LivingEntity aimed = Aim.target(caster, config.aimRange());
		if (aimed != null) {
			return aimed;
		}
		return caster.level().getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(config.nearestRange()),
				living -> living != caster && living.isAlive() && !living.isSpectator() && !living.isAlliedTo(caster)
					&& living.distanceTo(caster) <= config.nearestRange())
			.stream().min(Comparator.comparingDouble(living -> living.distanceToSqr(caster))).orElse(null);
	}

	private static int chains(LivingEntity caster) {
		return caster instanceof Player player ? Vault.of(player).settings().chains() : VaultSettings.defaults().chains();
	}

	@Override
	public void onHeldTick(AbilityCast cast) {
		ChainBind bind = ChainBind.of(cast.entity());
		if (cast.mode() != TIGHTEN || bind == null) {
			cast.end();
			return;
		}
		if (bind.bound() && cast.entity().level() instanceof ServerLevel level && cast.pay(FateConfig.enkidu().tightenManaPerTick())) {
			bind.tighten(level, cast.entity());
		}
	}

	@Override
	public void onEnd(AbilityCast cast) {
		ChainBind bind = ChainBind.of(cast.entity());
		if (bind != null) {
			bind.stopTightening();
		}
	}
}
