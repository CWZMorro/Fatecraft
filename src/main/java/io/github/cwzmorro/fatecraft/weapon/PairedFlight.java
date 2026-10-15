package io.github.cwzmorro.fatecraft.weapon;

import io.github.cwzmorro.alayacore.thrown.ThrownWeaponEntity;
import io.github.cwzmorro.alayacore.util.HeldInFront;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A thrown Kanshou or Bakuya's path at the creature under the crosshair: a curve that swings out to its own hand's
 * side and comes back in onto the target, so the pair cross in an X where they strike it. Its end follows the target as
 * it moves; the sword strikes as any thrown one does.
 */
public final class PairedFlight implements ThrownWeaponEntity.Flight {
	private static final double SWING = 0.5;
	private final UUID target;
	private final Vec3 start;
	private final Vec3 swing;
	private final int ticks;
	private int age;

	private PairedFlight(UUID target, Vec3 start, Vec3 swing, int ticks) {
		this.target = target;
		this.start = start;
		this.swing = swing;
		this.ticks = ticks;
	}

	public static PairedFlight toward(Vec3 from, LivingEntity target, int side, double speed) {
		Vec3 end = target.getBoundingBox().getCenter();
		Vec3 across = end.subtract(from);
		Vec3 swing = from.add(across.scale(0.5)).add(HeldInFront.right(across.normalize()).scale(side * SWING * across.length()));
		// The curve's length lies between its chord and the polygon through its middle point; their mean is near enough.
		double length = (across.length() + swing.distanceTo(from) + end.distanceTo(swing)) / 2;
		return new PairedFlight(target.getUUID(), from, swing, Math.max(1, (int) Math.ceil(length / speed)));
	}

	public Vec3 leaving() {
		return this.swing.subtract(this.start).normalize();
	}

	/**
	 * This tick's move from {@code position} onto the curve's next point, its end on the target where it is now; null once the
	 * curve is flown or the target is gone, after which it flies straight on.
	 */
	public @Nullable Vec3 step(ServerLevel level, Vec3 position) {
		Entity entity = level.getEntity(this.target);
		if (entity == null || !entity.isAlive() || this.age >= this.ticks) {
			return null;
		}
		this.age++;
		double t = (double) this.age / this.ticks;
		Vec3 end = entity.getBoundingBox().getCenter();
		Vec3 point = this.start.scale((1 - t) * (1 - t)).add(this.swing.scale(2 * (1 - t) * t)).add(end.scale(t * t));
		return point.subtract(position);
	}

	@Override
	public boolean fly(ServerLevel level, ThrownWeaponEntity sword) {
		Vec3 move = this.step(level, sword.position());
		if (move == null) {
			sword.setFlight(null);
		} else {
			sword.setDeltaMovement(move);
		}
		return false;
	}
}
