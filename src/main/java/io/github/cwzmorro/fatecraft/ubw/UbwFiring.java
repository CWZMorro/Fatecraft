package io.github.cwzmorro.fatecraft.ubw;

import io.github.cwzmorro.alayacore.ability.AbilityCast;
import io.github.cwzmorro.alayacore.combat.Aim;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.thrown.ThrownWeaponEntity;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.gate.GateOfBabylon;
import io.github.cwzmorro.fatecraft.gate.GateWall;
import io.github.cwzmorro.fatecraft.trace.Projections;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

/**
 * Unlimited Blade Works' firing: the Gate of Babylon's walls, aim, tiers, taps and holds, with UBW's own settings
 * ({@link UbwSettings}), but projected sword copies instead of gates and vault items.
 */
final class UbwFiring {
	private record Slot(ThrownWeaponEntity sword, int slot) { }

	private final LivingEntity owner;
	private final int mode;
	private final List<Slot> slots = new ArrayList<>();
	private GateWall wall;
	private boolean firing;
	private boolean holding;
	private double toOpen;
	private double toFire;

	UbwFiring(LivingEntity owner, int mode) {
		this.owner = owner;
		this.mode = mode;
		if (mode != UnlimitedBladeWorks.SINGLE_TARGET) {
			this.newWall();
			this.volley();
		}
	}

	private void newWall() {
		this.wall = GateWall.of(UbwSettings.volley(this.owner), UbwSettings.spread(this.owner), this.owner.level().getRandom());
	}

	private void volley() {
		List<Item> known = UbwRuntime.volleySwords(this.owner);
		for (int i = 0; i < this.wall.size(); i++) {
			this.open(i * GateOfBabylon.VOLLEY_OPENS_OVER / this.wall.size(), known);
		}
	}

	int mode() {
		return this.mode;
	}

	void hold() {
		this.firing = true;
		this.holding = true;
	}

	void again(boolean sneaking) {
		if (this.firing) {
			return;
		}
		// Shift + press changes a player's tier; a mob keeps the tier it has stored.
		if (UnlimitedBladeWorks.changesTier(this.mode, sneaking) && this.owner instanceof ServerPlayer player) {
			UbwSettings.nextTier(player);
			this.clear();
			this.newWall();
			this.volley();
		} else {
			this.firing = true;
		}
	}

	void clear() {
		this.slots.forEach(slot -> slot.sword().discard());
		this.slots.clear();
	}

	private Vec3 aim(Vec3 from) {
		return this.mode == UnlimitedBladeWorks.AOE_THROW ? this.owner.getLookAngle()
			: Aim.point(this.owner, FateConfig.ubw().aimRange()).subtract(from);
	}

	private ServerLevel level() {
		return (ServerLevel) this.owner.level();
	}

	private boolean open(int delay, List<Item> known) {
		ServerLevel level = this.level();
		if (known.isEmpty()) {
			return false;
		}
		int slot = GateWall.NO_SLOT;
		Vec3 from;
		if (this.wall == null) {
			from = GateOfBabylon.placeAround(level, Aim.point(this.owner, FateConfig.ubw().aimRange()), level.getRandom());
			if (from == null) {
				return false;
			}
		} else {
			List<Integer> free = new ArrayList<>();
			for (int i = 0; i < this.wall.size(); i++) {
				int index = i;
				if (this.slots.stream().noneMatch(existing -> existing.slot() == index)) {
					free.add(i);
				}
			}
			if (free.isEmpty()) {
				return false;
			}
			slot = free.get(level.getRandom().nextInt(free.size()));
			from = this.wall.at(slot, this.owner.position(), this.owner.getYRot(), this.owner.getScale());
		}
		if (!UbwRuntime.inside(this.owner, from)) {
			return false;
		}
		ThrownWeaponEntity sword = UbwSwords.project(level, this.owner, Projections.create(known.get(level.getRandom().nextInt(known.size()))),
			from, Vec3.ZERO, FateConfig.trace().formationTicks() + delay, true, UbwSettings.swordSize(this.owner));
		ThrownWeaponEntity.face(sword, this.aim(from));
		this.slots.add(new Slot(sword, slot));
		return true;
	}

	void tick(AbilityCast cast) {
		if (!this.level().getServer().tickRateManager().runsNormally()) {
			return;
		}
		this.slots.removeIf(slot -> slot.sword().isRemoved());
		if (this.wall != null) {
			for (Slot slot : this.slots) {
				Vec3 from = this.wall.at(slot.slot(), this.owner.position(), this.owner.getYRot(), this.owner.getScale());
				if (!UbwRuntime.inside(this.owner, from)) {
					slot.sword().discard();
					continue;
				}
				slot.sword().setPos(from);
				ThrownWeaponEntity.face(slot.sword(), this.aim(from));
			}
		}
		double perTick = UbwSettings.rate(this.owner) / (double) SharedConstants.TICKS_PER_SECOND;
		boolean opening = this.holding && cast.held();
		if (opening) {
			this.toOpen += perTick;
			List<Item> known = this.toOpen >= 1 ? UbwRuntime.volleySwords(this.owner) : List.of();
			while (this.toOpen >= 1) {
				if (!this.open(0, known)) {
					this.toOpen = 0;
					break;
				}
				this.toOpen--;
			}
		}
		if (this.firing) {
			this.toFire = Math.min(this.toFire + perTick, perTick + 1);
			for (Slot slot : List.copyOf(this.slots)) {
				if (this.toFire < 1) {
					break;
				}
				if (slot.sword().formationRemaining() > 0) {
					continue;
				}
				// Shots are free: the marble's upkeep pays for them.
				slot.sword().releaseProjection(this.aim(slot.sword().position()).normalize().scale(AlayaConfig.thrownWeapons().blocksPerTick()));
				this.slots.remove(slot);
				this.toFire--;
			}
			if (this.slots.isEmpty() && !opening) {
				cast.end();
			}
		}
	}
}
