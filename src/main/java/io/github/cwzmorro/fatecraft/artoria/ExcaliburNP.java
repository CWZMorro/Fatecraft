package io.github.cwzmorro.fatecraft.artoria;

import io.github.cwzmorro.alayacore.ability.Ability;
import io.github.cwzmorro.alayacore.ability.AbilityCast;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.beam.HeldBeam;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.alayacore.rank.RankLetter;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.alayacore.util.Percent;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/** Excalibur, Artoria's Noble Phantasm: Beam fires at full charge while held, Burst on release. */
public final class ExcaliburNP extends Ability {
	public static final ResourceKey<Ability> KEY = ResourceKey.create(AlayaRegistries.ABILITY_KEY, Fatecraft.id("excalibur"));
	public static final TagKey<Item> SWORDS = TagKey.create(Registries.ITEM, Fatecraft.id("excalibur_swords"));
	public static final Rank RANK = new Rank(RankLetter.A, 2);
	public static final int HELD = 0;
	public static final int BURST = 1;
	static final AttachmentType<ExcaliburEntity> ACTIVE = AttachmentRegistry.create(KEY.identifier().withSuffix("_active"));

	public ExcaliburNP() {
		super(new Properties().modes(2).noblePhantasm().cooldownSeconds(
			FateConfig.artoria().excaliburCooldownSeconds(), FateConfig.artoria().excaliburCooldownSeconds()));
	}

	@Override
	public String rankLabel() {
		return RANK.toString();
	}

	@Override
	public List<Object> descriptionArguments() {
		ArtoriaConfig c = FateConfig.artoria();
		return List.of(c.excaliburWindUpSeconds(), c.excaliburBeamRange(), c.excaliburStartRadius() * 2,
			c.excaliburEndRadius() * 2, c.excaliburBurstRange(), c.excaliburBurstRadius(), c.excaliburBurstHeight(),
			AlayaConfig.ranks().npBaseDamage(RANK), Percent.fromFraction(c.excaliburEdgeDamage()), c.excaliburCooldownSeconds(),
			AlayaConfig.ranks().beamMaxHoldSeconds(), AlayaConfig.ranks().beamDamagePerSecond(RANK),
			AlayaConfig.ranks().beamDrainPerSecond(RANK));
	}

	@Override
	public @Nullable Component cannotStart(LivingEntity entity, int mode) {
		return entity.getMainHandItem().is(SWORDS) ? null : Component.translatable("ability.fatecraft.excalibur.needs_sword");
	}

	@Override
	public double startCost(LivingEntity entity, int mode) {
		return 0;
	}

	@Override
	public void onStart(AbilityCast cast) {
		if (cast.mode() == HELD) {
			HeldBeam.startMovement(cast.entity());
		}
		if (cast.entity().level() instanceof ServerLevel level) {
			ExcaliburEntity light = new ExcaliburEntity(Artoria.EXCALIBUR, level);
			light.charge(cast.entity(), cast.mode());
			cast.entity().setAttached(ACTIVE, light);
			cast.entity().getMainHandItem().set(Artoria.EXCALIBUR_CHARGE, true);
			level.addFreshEntity(light);
		}
	}

	@Override
	public void onTick(AbilityCast cast) {
		ExcaliburEntity light = cast.entity().getAttached(ACTIVE);
		if (light == null || light.isRemoved() || !cast.entity().getMainHandItem().is(SWORDS)) {
			cast.end();
			return;
		}
		// Beam fires by itself once fully charged, along her look at that moment; Burst waits for the release.
		if (cast.mode() == HELD && light.phase() == ExcaliburEntity.Phase.CHARGE
			&& cast.heldTicks() >= Durations.ticks(FateConfig.artoria().excaliburWindUpSeconds())) {
			light.release(cast.entity());
		}
		if (light.phase() == ExcaliburEntity.Phase.HELD) {
			HeldBeam.payTick(cast, RANK, light.phaseAge());
		}
	}

	@Override
	public void onTap(AbilityCast cast) {
		cast.end();
	}

	@Override
	public void onReleased(AbilityCast cast) {
		if (cast.mode() == HELD) {
			cast.end();
			return;
		}
		ExcaliburEntity light = cast.entity().getAttached(ACTIVE);
		if (light == null || cast.heldTicks() < Durations.ticks(FateConfig.artoria().excaliburWindUpSeconds())) {
			cast.end();
			return;
		}
		if (light.phase() != ExcaliburEntity.Phase.CHARGE) {
			return;
		}
		if (cast.pay(AlayaConfig.ranks().burstCost(RANK))) {
			light.release(cast.entity());
		}
	}

	@Override
	public void onEnd(AbilityCast cast) {
		if (cast.mode() == HELD) {
			HeldBeam.endMovement(cast.entity());
		}
		cast.entity().getMainHandItem().remove(Artoria.EXCALIBUR_CHARGE);
		// The light clears the charging sword too, in case she swapped swords during the charge.
		ExcaliburEntity light = cast.entity().removeAttached(ACTIVE);
		if (light != null) {
			light.clearCharge();
			light.discard();
		}
	}
}
