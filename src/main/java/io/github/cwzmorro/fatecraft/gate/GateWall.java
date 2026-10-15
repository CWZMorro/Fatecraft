package io.github.cwzmorro.fatecraft.gate;

import io.github.cwzmorro.alayacore.util.HeldInFront;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The wall of gates behind Gilgamesh for the AOE and targeted throws: staggered rows rising from above his shoulders,
 * placed relative to him and his size.
 */
public final class GateWall {
	// A gate with no place on the wall: single target's gates stand round the target.
	public static final int NO_SLOT = -1;
	private static final double TYPICAL_RADIUS = 1.2;
	private static final double GAP = 0.5;
	private static final double ASPECT = 2.0;
	private static final double BEHIND = 2.0;
	private static final double LOWEST = 1.6;
	private static final double OUT_OF_LINE = 0.4;
	private static final double DEPTH = 0.6;
	private static final double STAGGER = 0.5;

	// Each place at a player's normal size, in blocks: across to his right, up from his feet, back from him.
	private final List<Vec3> places;

	private GateWall(List<Vec3> places) {
		this.places = places;
	}

	public static GateWall of(int gates, RandomSource random) {
		return of(gates, 1.0, random);
	}

	public static GateWall of(int gates, double spread, RandomSource random) {
		double spacing = (2 * TYPICAL_RADIUS + GAP) * spread;
		int columns = (int) Math.ceil(Math.sqrt(gates * ASPECT));
		List<Vec3> places = new ArrayList<>(gates);
		for (int gate = 0; gate < gates; gate++) {
			int row = gate / columns;
			double stagger = row % 2 == 0 ? 0.0 : STAGGER;
			double right = (gate % columns - (columns - 1) / 2.0 + stagger + jitter(random)) * spacing;
			double up = LOWEST + (row + jitter(random)) * spacing;
			places.add(new Vec3(right, up, BEHIND + (random.nextDouble() - 0.5) * DEPTH));
		}
		return new GateWall(List.copyOf(places));
	}

	private static double jitter(RandomSource random) {
		return (random.nextDouble() - 0.5) * OUT_OF_LINE;
	}

	public int size() {
		return this.places.size();
	}

	public Vec3 at(int slot, Vec3 feet, float yaw, double scale) {
		Vec3 place = this.places.get(slot).scale(scale);
		Vec3 forward = Vec3.directionFromRotation(0.0F, yaw);
		return feet.add(HeldInFront.right(forward).scale(place.x)).add(0.0, place.y, 0.0).subtract(forward.scale(place.z));
	}
}
