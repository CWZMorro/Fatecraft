package io.github.cwzmorro.fatecraft.artoria;

import io.github.cwzmorro.alayacore.ability.Ability;
import io.github.cwzmorro.alayacore.ability.AbilityCast;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;

/** An Artoria skill that fires a light ({@link ArtoriaAttackEntity}) living as long as its cast. */
public abstract class ArtoriaAttack extends ArtoriaAbility {
	private final AttachmentType<ArtoriaAttackEntity> active;
	private final ArtoriaAttackEntity.Kind kind;

	protected ArtoriaAttack(ResourceKey<Ability> key, double cooldown, ArtoriaAttackEntity.Kind kind) {
		super(cooldown);
		this.active = AttachmentRegistry.create(key.identifier().withSuffix("_active"));
		this.kind = kind;
	}

	@Override
	public void onStart(AbilityCast cast) {
		if (cast.entity().level() instanceof ServerLevel level) {
			ArtoriaAttackEntity attack = new ArtoriaAttackEntity(Artoria.ATTACK, level);
			attack.start(cast.entity(), this.kind, cast.abilityId(), this.rank(), this.damage(cast.entity()));
			cast.entity().setAttached(this.active, attack);
			level.addFreshEntity(attack);
		}
	}

	@Override
	public void onTick(AbilityCast cast) {
		ArtoriaAttackEntity attack = cast.entity().getAttached(this.active);
		if (!sword(cast.entity()) || attack == null || attack.isRemoved()) {
			cast.end();
		}
	}

	@Override
	public void onEnd(AbilityCast cast) {
		ArtoriaAttackEntity attack = cast.entity().removeAttached(this.active);
		if (attack != null) {
			attack.discard();
		}
	}
}
