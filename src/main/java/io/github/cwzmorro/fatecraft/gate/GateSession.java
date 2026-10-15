package io.github.cwzmorro.fatecraft.gate;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/** One cast of the Gate of Babylon: its waiting gates in the order they opened, its wall, and what it throws, fixed when it starts. */
final class GateSession {
	private final long id;
	private final int section;
	private final boolean copy;
	private @Nullable GateWall wall;
	private final List<GateEntity> gates = new ArrayList<>();
	boolean firing;
	boolean holding;
	double toFire;
	double toOpen;

	GateSession(long id, int section, boolean copy, @Nullable GateWall wall) {
		this.id = id;
		this.section = section;
		this.copy = copy;
		this.wall = wall;
	}

	long id() {
		return this.id;
	}

	int section() {
		return this.section;
	}

	boolean copy() {
		return this.copy;
	}

	@Nullable GateWall wall() {
		return this.wall;
	}

	void setWall(GateWall wall) {
		this.wall = wall;
	}

	List<GateEntity> gates() {
		return this.gates;
	}

	void forgetGone() {
		this.gates.removeIf(gate -> gate.gone() || gate.isRemoved());
	}

	@Nullable GateEntity firstReady() {
		for (GateEntity gate : this.gates) {
			if (gate.ready()) {
				return gate;
			}
		}
		return null;
	}

	int freeSlot(RandomSource random) {
		if (this.wall == null) {
			return GateWall.NO_SLOT;
		}
		boolean[] taken = new boolean[this.wall.size()];
		for (GateEntity gate : this.gates) {
			if (gate.slot() >= 0 && gate.slot() < taken.length) {
				taken[gate.slot()] = true;
			}
		}
		int free = 0;
		for (boolean occupied : taken) {
			if (!occupied) {
				free++;
			}
		}
		if (free == 0) {
			return GateWall.NO_SLOT;
		}
		int pick = random.nextInt(free);
		for (int slot = 0; slot < taken.length; slot++) {
			if (!taken[slot] && pick-- == 0) {
				return slot;
			}
		}
		return GateWall.NO_SLOT;
	}
}
