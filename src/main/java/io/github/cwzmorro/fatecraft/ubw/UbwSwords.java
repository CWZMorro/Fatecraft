package io.github.cwzmorro.fatecraft.ubw;

import com.mojang.serialization.Codec;
import io.github.cwzmorro.alayacore.combat.AttackTags;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.thrown.ThrownWeaponEntity;
import io.github.cwzmorro.alayacore.util.Owners;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.trace.Traces;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Unlimited Blade Works' flying sword copies: those its firing modes form and release, and the Gate of Babylon
 * counter, unique to UBW: every weapon the Gate shoots into the marble is met by the exact same weapon projected
 * flying back at it, which clashes with it and breaks its momentum; a craftable one is traced for good.
 */
public final class UbwSwords {
	private static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Fatecraft.id("unlimited_blade_works"));
	private static final AttachmentType<Boolean> COUNTERED = AttachmentRegistry.create(Fatecraft.id("ubw_countered"),
		builder -> builder.persistent(Codec.BOOL));
	private static final double COUNTER_AHEAD = 0.75;

	private record Waiting(UUID counter) implements ThrownWeaponEntity.Flight {
		@Override
		public boolean fly(ServerLevel level, ThrownWeaponEntity weapon) {
			if (level.getEntity(this.counter) != null) {
				return true;
			}
			weapon.setFlight(null);
			return false;
		}
	}

	private record Opposing(UUID original) implements ThrownWeaponEntity.Flight {
		@Override
		public boolean fly(ServerLevel level, ThrownWeaponEntity copy) {
			Entity target = level.getEntity(this.original);
			if (!(target instanceof ThrownWeaponEntity original) || !awaits(original, copy)) {
				copy.discard();
				return true;
			}
			Vec3 delta = original.position().subtract(copy.position());
			if (delta.lengthSqr() <= copy.getDeltaMovement().lengthSqr()) {
				copy.setPos(original.position());
				clash(level, copy, original);
			} else {
				copy.setPos(copy.position().add(delta.normalize().scale(copy.getDeltaMovement().length())));
			}
			return true;
		}
	}

	private UbwSwords() {
	}

	static void register() {
		ThrownWeaponEntity.INTERCEPT.register(UbwRuntime::counter);
	}

	/**
	 * Projects a copy of {@code item} for {@code owner} at {@code from}, flying at {@code velocity} once formed over
	 * {@code formation} ticks, or held until released; {@code sizeFactor} times its owner's size. It is never saved.
	 */
	public static ThrownWeaponEntity project(ServerLevel level, LivingEntity owner, ItemStack item, Vec3 from, Vec3 velocity, int formation, boolean held,
		float sizeFactor) {
		ThrownWeaponEntity sword = ThrownWeaponEntity.launch(level, FateWeapons.THROWN_WEAPON, owner, item, from, velocity, DAMAGE);
		sword.sized(sizeFactor);
		sword.form(formation);
		if (held) {
			sword.hold();
		}
		sword.unsaved();
		return sword;
	}

	public static boolean incoming(ThrownWeaponEntity weapon) {
		return weapon.is(AttackTags.COUNTERABLE_THROWN_WEAPONS) && !countered(weapon) && !(weapon.flight() instanceof Waiting) && weapon.flying();
	}

	public static boolean countered(ThrownWeaponEntity weapon) {
		return weapon.getAttachedOrElse(COUNTERED, false);
	}

	static void oppose(ServerLevel level, ThrownWeaponEntity original, LivingEntity owner, Vec3 contact) {
		Vec3 direction = original.getDeltaMovement().normalize();
		if (direction.lengthSqr() == 0) {
			direction = original.position().subtract(owner.position()).normalize();
		}
		if (direction.lengthSqr() == 0) {
			direction = Vec3.Y_AXIS;
		}
		ThrownWeaponEntity counter = project(level, owner, original.item().copyWithCount(1), contact.add(direction.scale(COUNTER_AHEAD)),
			direction.scale(-AlayaConfig.thrownWeapons().blocksPerTick()), FateConfig.trace().formationTicks(), false, 1.0F);
		// The opposing flight crosses the intercepted weapon's point next tick. It cannot damage or wear an item.
		counter.setFlight(new Opposing(original.getUUID()));
		original.setFlight(new Waiting(counter.getUUID()));
		original.setPos(contact);
	}

	private static boolean awaits(ThrownWeaponEntity original, ThrownWeaponEntity copy) {
		return original.flight() instanceof Waiting waiting && waiting.counter().equals(copy.getUUID());
	}

	private static void clash(ServerLevel level, ThrownWeaponEntity copy, ThrownWeaponEntity original) {
		if (!ItemStack.isSameItemSameComponents(copy.item(), original.item()) || !awaits(original, copy)) {
			copy.discard();
			return;
		}
		original.setFlight(null);
		original.setAttached(COUNTERED, true);
		LivingEntity counterOwner = Owners.find(level, copy.owner());
		if (counterOwner != null && UbwCraftability.craftable(level, original.item())) {
			Traces.learnCounter(counterOwner, original.item());
		}
		original.stop(level);
		copy.discard();
	}
}
