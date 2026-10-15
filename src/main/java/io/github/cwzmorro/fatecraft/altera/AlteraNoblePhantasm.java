package io.github.cwzmorro.fatecraft.altera;

import io.github.cwzmorro.alayacore.ability.Ability;
import io.github.cwzmorro.alayacore.ability.AbilityCast;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/** Shared main-hand sword gate and unsaved entity lifetime for Altera's two NPs; each NP names its own swords. */
abstract class AlteraNoblePhantasm<E extends Entity> extends Ability {
	private static final String NEEDS_SWORD = "ability.fatecraft.photon_ray.needs_sword";
	private final AttachmentType<E> active;
	private final TagKey<Item> swords;

	AlteraNoblePhantasm(Properties properties, AttachmentType<E> active, TagKey<Item> swords) {
		super(properties);
		this.active = active;
		this.swords = swords;
	}

	@Override
	public @Nullable Component cannotStart(LivingEntity entity, int mode) {
		return !entity.getMainHandItem().is(this.swords) ? Component.translatable(NEEDS_SWORD) : null;
	}

	@Override
	public void onTick(AbilityCast cast) {
		E ray = cast.entity().getAttached(this.active);
		if (ray == null || ray.isRemoved() || !cast.entity().getMainHandItem().is(this.swords)) {
			cast.end();
		}
	}

	@Override
	public void onEnd(AbilityCast cast) {
		E ray = cast.entity().removeAttached(this.active);
		if (ray != null) {
			ray.discard();
		}
	}
}
