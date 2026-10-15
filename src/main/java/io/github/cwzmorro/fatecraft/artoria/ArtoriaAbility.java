package io.github.cwzmorro.fatecraft.artoria;

import io.github.cwzmorro.alayacore.ability.Ability;
import io.github.cwzmorro.alayacore.ability.AbilityCast;
import io.github.cwzmorro.alayacore.combat.ServantBonus;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.fatecraft.Fatecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/** An Artoria skill: it needs one of her swords in the main hand, not her true name, and its cost and damage follow its rank. */
public abstract class ArtoriaAbility extends Ability {
	public static final TagKey<Item> SWORDS = TagKey.create(Registries.ITEM, Fatecraft.id("artoria_swords"));

	protected ArtoriaAbility(double cooldown) {
		super(new Properties().cooldownSeconds(cooldown));
	}

	public abstract Rank rank();

	@Override
	public String rankLabel() {
		return this.rank().toString();
	}

	public static boolean sword(LivingEntity entity) {
		return entity.getMainHandItem().is(SWORDS);
	}

	@Override
	public @Nullable Component cannotStart(LivingEntity entity, int mode) {
		return sword(entity) ? null : Component.translatable("ability.fatecraft.artoria.needs_sword");
	}

	public double damage(LivingEntity entity) {
		return AlayaConfig.ranks().skillDamage(this.rank(), ServantBonus.classAttack(entity));
	}

	@Override
	public double startCost(LivingEntity entity, int mode) {
		return AlayaConfig.ranks().skillCost(this.rank());
	}

	@Override
	public void onTap(AbilityCast cast) {
		// Tap skills finish their own animations, not on key release.
	}

	@Override
	public void onReleased(AbilityCast cast) {
	}
}
