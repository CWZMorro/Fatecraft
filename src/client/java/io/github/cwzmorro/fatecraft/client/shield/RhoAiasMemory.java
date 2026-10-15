package io.github.cwzmorro.fatecraft.client.shield;

import io.github.cwzmorro.fatecraft.shield.RhoAiasEntity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.phys.Vec3;

/**
 * What the client remembers of each Rho Aias it draws: the hits it took and when each petal shattered or started growing
 * back. The petals gauge its HP: each time HP drops past another share (1/4 or 1/7), the petal nearest the last hit
 * shatters, and a petal grows back as HP climbs past the same mark. Kept per shield until the client forgets it.
 */
public final class RhoAiasMemory {
	private static final long HIT_BREAKS_WITHIN = 2;
	// Slack, as a share of a petal, so HP exactly on a petal's mark doesn't count as part of the next one.
	private static final double MARK_SLACK = 1e-6;
	private static final Map<RhoAiasEntity, RhoAiasMemory> MEMORIES = new WeakHashMap<>();

	public record Hit(Vec3 point, long time) {
	}

	private final List<Hit> hits = new ArrayList<>();
	private long[] brokenAt = new long[0];
	private long[] regrownAt = new long[0];

	private RhoAiasMemory() {
	}

	public static RhoAiasMemory of(RhoAiasEntity shield, long now) {
		RhoAiasMemory memory = MEMORIES.computeIfAbsent(shield, key -> new RhoAiasMemory());
		memory.update(shield, now);
		return memory;
	}

	public static void hit(RhoAiasEntity shield, Vec3 point) {
		RhoAiasMemory memory = MEMORIES.computeIfAbsent(shield, key -> new RhoAiasMemory());
		memory.hits.add(new Hit(point.subtract(shield.position()), shield.level().getGameTime()));
	}

	public List<Hit> hits() {
		return this.hits;
	}

	public long brokenAt(int k) {
		return this.brokenAt[k];
	}

	public long regrownAt(int k) {
		return this.regrownAt[k];
	}

	private void update(RhoAiasEntity shield, long now) {
		int petals = shield.petals();
		if (this.brokenAt.length != petals) {
			this.brokenAt = new long[petals];
			this.regrownAt = new long[petals];
			Arrays.fill(this.brokenAt, Long.MIN_VALUE);
			Arrays.fill(this.regrownAt, Long.MIN_VALUE);
		}
		this.hits.removeIf(hit -> now - hit.time() >= RhoAiasLook.HIT_TICKS);
		int whole = shield.maxHp() <= 0 ? petals : (int) Math.ceil(shield.hp() / shield.maxHp() * petals - MARK_SLACK);
		int standing = 0;
		for (long broken : this.brokenAt) {
			standing += broken == Long.MIN_VALUE ? 1 : 0;
		}
		for (; standing > whole; standing--) {
			this.brokenAt[this.toBreak(shield, now)] = now;
		}
		for (; standing < whole; standing++) {
			int latest = this.latestBroken();
			this.brokenAt[latest] = Long.MIN_VALUE;
			this.regrownAt[latest] = now;
		}
	}

	private int toBreak(RhoAiasEntity shield, long now) {
		Hit latest = this.hits.isEmpty() ? null : this.hits.getLast();
		boolean hitJustLanded = latest != null && now - latest.time() <= HIT_BREAKS_WITHIN;
		int chosen = -1;
		double nearest = Double.POSITIVE_INFINITY;
		for (int k = 0; k < this.brokenAt.length; k++) {
			if (this.brokenAt[k] != Long.MIN_VALUE) {
				continue;
			}
			// Without a hit, the later petals rank nearer, so the last whole one breaks.
			double distance = !hitJustLanded ? -k
				: latest.point().distanceToSqr(RhoAiasLook.petalMiddle(shield.getLookAngle(), shield.radius(), this.brokenAt.length, k));
			if (distance < nearest) {
				nearest = distance;
				chosen = k;
			}
		}
		return chosen;
	}

	private int latestBroken() {
		int latest = -1;
		for (int k = 0; k < this.brokenAt.length; k++) {
			if (this.brokenAt[k] != Long.MIN_VALUE && (latest < 0 || this.brokenAt[k] > this.brokenAt[latest])) {
				latest = k;
			}
		}
		return latest;
	}
}
