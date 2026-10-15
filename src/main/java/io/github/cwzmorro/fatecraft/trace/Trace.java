package io.github.cwzmorro.fatecraft.trace;

import io.github.cwzmorro.alayacore.ability.Ability;
import io.github.cwzmorro.alayacore.ability.AbilityCast;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/** EMIYA's main-hand sword learning ability. */
public final class Trace extends Ability {
	public static final ResourceKey<Ability> KEY = ResourceKey.create(AlayaRegistries.ABILITY_KEY, Fatecraft.id("trace"));

	public Trace() {
		super(new Properties());
	}

	@Override
	public @Nullable Component cannotStart(LivingEntity entity, int mode) {
		if (Traces.divine(entity.getMainHandItem())) {
			return Component.translatable("ability.fatecraft.trace.divine_construct");
		}
		return !Traces.canTrace(entity) || !Traces.sword(entity.getMainHandItem())
			? Component.translatable("ability.fatecraft.trace.needs_sword") : null;
	}

	@Override
	public double startCost(LivingEntity entity, int mode) {
		return FateConfig.trace().traceCost();
	}

	@Override
	public void onStart(AbilityCast cast) {
		Traces.learn(cast.entity(), cast.entity().getMainHandItem());
		cast.end();
	}
}
