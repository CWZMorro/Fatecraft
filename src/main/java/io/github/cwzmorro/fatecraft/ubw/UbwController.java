package io.github.cwzmorro.fatecraft.ubw;

import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.api.AlayaServants;
import io.github.cwzmorro.alayacore.marble.RealityMarble;
import io.github.cwzmorro.alayacore.marble.RealityMarbles;
import io.github.cwzmorro.alayacore.util.ServerState;
import io.github.cwzmorro.fatecraft.Fatecraft;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Unlimited Blade Works' marbles: opening, upkeep, clashes, breaking and closing, with the terrain they replace. A marble
 * stays where it opened. Server thread only.
 */
public final class UbwController {
	/** {@code SHATTERING}: broken by Enuma Elish, powerless and crumbling away until it closes. */
	public enum Phase {
		ACTIVE,
		SHATTERING,
		CLOSING,
		RECOVERY_REQUIRED
	}

	/** How an opening request ended; only {@code OPENED} spent mana and built a marble. */
	public enum OpenResult {
		OPENED,
		INSUFFICIENT_MANA,
		BUSY,
		CLASH_LOST,
		RECOVERY_PENDING
	}

	public enum CloseResult {
		CLOSED,
		PENDING
	}

	public record Status(Phase phase, Optional<UUID> operation, ResourceKey<Level> dimension, BlockPos center, int radius) { }

	private static final ServerState<UbwController> INSTANCES = ServerState.create(Fatecraft.id("ubw_controller"));
	private static final double PLACEMENT_RADII = 0.5;
	private final MinecraftServer server;
	private final UbwTerrain terrain;
	private final Map<UUID, Marble> marbles = new HashMap<>();

	private final class Marble implements RealityMarble {
		final LivingEntity caster;
		final UbwConfig config;
		final ResourceKey<Level> dimension;
		final BlockPos center;
		final int minY;
		final int maxY;
		final Vec3 origin;
		long openedAt;
		final double span;
		double front = Double.POSITIVE_INFINITY;
		boolean opening;
		@Nullable UUID operation;
		UbwController.Phase phase = UbwController.Phase.ACTIVE;
		final Optional<Identifier> servant;
		int lastTick;
		@Nullable Vec3 impact;
		long shatteredAt;
		double shatterSpan;
		double fallen;

		Marble(LivingEntity caster, ServerLevel level, BlockPos center, UbwConfig config, int tick) {
			this.caster = caster;
			this.config = config;
			this.dimension = level.dimension();
			this.center = center;
			this.minY = level.getMinY();
			this.maxY = level.getMaxY();
			this.origin = caster.position();
			this.openedAt = level.getGameTime() + config.opening().chargeTicks();
			this.span = UbwOpening.span(this.origin.x - center.getX(), this.origin.z - center.getZ(), config.radius());
			this.lastTick = tick;
			this.servant = AlayaServants.servantId(caster);
		}

		Status status() {
			return new Status(this.phase, Optional.ofNullable(this.operation), this.dimension, this.center, this.config.radius());
		}

		@Override
		public LivingEntity caster() {
			return this.caster;
		}

		@Override
		public ResourceKey<Level> dimension() {
			return this.dimension;
		}

		@Override
		public Vec3 center() {
			return Vec3.atLowerCornerOf(this.center);
		}

		@Override
		public double radius() {
			return this.config.radius();
		}

		@Override
		public RealityMarble.Phase phase() {
			return switch (this.phase) {
				case ACTIVE -> this.opening ? RealityMarble.Phase.OPENING : RealityMarble.Phase.STANDING;
				case SHATTERING -> RealityMarble.Phase.BROKEN;
				case CLOSING, RECOVERY_REQUIRED -> RealityMarble.Phase.CLOSING;
			};
		}

		@Override
		public Optional<Identifier> servant() {
			return this.servant;
		}

		@Override
		public boolean shatter(Vec3 impact) {
			return UbwRuntime.shatter(UbwController.this.server, this.caster.getUUID(), impact);
		}

		@Override
		public boolean close() {
			return UbwRuntime.forceClose(UbwController.this.server, this.caster.getUUID());
		}
	}

	private UbwController(MinecraftServer server, UbwTerrain terrain) {
		this.server = server;
		this.terrain = terrain;
	}

	public static UbwController of(MinecraftServer server) throws IOException {
		if (!server.isSameThread()) {
			throw new IllegalStateException("UBW controller requires server thread");
		}
		UbwController controller = INSTANCES.get(server);
		if (controller == null) {
			controller = new UbwController(server, UbwTerrain.of(server));
			INSTANCES.set(server, controller);
		}
		return controller;
	}

	/** Returns the marble's centre: the block half a radius ahead of the caster's feet, the way it faces, its pitch ignored. */
	public static BlockPos placement(LivingEntity caster, UbwConfig config) {
		Vec3 forward = Vec3.directionFromRotation(0.0F, caster.getYRot());
		return BlockPos.containing(caster.position().add(forward.scale(config.radius() * PLACEMENT_RADII)));
	}

	/** Opens the caster's marble; only an opening that succeeds spends mana. */
	public OpenResult open(LivingEntity caster, UbwConfig config, int floorY, int floorDepth, UbwLayout.Palette palette) throws IOException {
		return this.open(caster, config, floorY, floorDepth, palette, false);
	}

	public OpenResult open(LivingEntity caster, UbwConfig config, int floorY, int floorDepth, UbwLayout.Palette palette, boolean staged)
		throws IOException {
		this.thread();
		if (!(caster.level() instanceof ServerLevel level) || level.getServer() != this.server) {
			throw new IllegalArgumentException("Foreign UBW caster");
		}
		if (this.marbles.containsKey(caster.getUUID())) {
			return OpenResult.BUSY;
		}
		if (this.marbles.values().stream().anyMatch(marble -> marble.phase == Phase.RECOVERY_REQUIRED)) {
			throw new IOException("UBW controller requires explicit terrain recovery");
		}
		Set<UUID> before = this.terrain.pending();
		if (AlayaMana.available(caster) < config.openingMana()) {
			return OpenResult.INSUFFICIENT_MANA;
		}
		BlockPos center = placement(caster, config);
		// Every overlapping marble, of any mod, is checked before any is evicted; creative's free mana doesn't count as strength.
		RealityMarbles.Overlap overlap = RealityMarbles.overlap(level, caster, Vec3.atLowerCornerOf(center), config.radius());
		if (overlap.verdict() == RealityMarbles.Verdict.BROKEN) {
			return OpenResult.BUSY;
		}
		if (overlap.verdict() == RealityMarbles.Verdict.CLOSING) {
			return OpenResult.RECOVERY_PENDING;
		}
		if (overlap.verdict() == RealityMarbles.Verdict.LOST) {
			return OpenResult.CLASH_LOST;
		}
		boolean restored = true;
		for (RealityMarble loser : overlap.losers()) {
			// Its own marbles close through this controller, so a failed restoration still surfaces as this opening's error.
			restored &= loser instanceof Marble own ? this.cancel(own.caster.getUUID()) == CloseResult.CLOSED : loser.close();
		}
		if (!restored) {
			return OpenResult.RECOVERY_PENDING;
		}
		Marble marble = new Marble(caster, level, center, config, this.server.getTickCount());
		try {
			marble.operation = this.terrain.construct(level, marble.center, config, floorY, floorDepth, palette, staged ? marble.origin : null);
			if (staged) {
				marble.opening = true;
				marble.front = 0;
			}
		} catch (IOException | RuntimeException failure) {
			// construct can fail after journalling originals and replacing some terrain, so the marble is kept to restore it.
			try {
				Set<UUID> after = this.terrain.pending();
				for (UUID operation : after) {
					if (!before.contains(operation)) {
						marble.operation = operation;
					}
				}
				if (marble.operation != null) {
					marble.phase = Phase.CLOSING;
					this.marbles.put(caster.getUUID(), marble);
				}
			} catch (IOException uncertain) {
				failure.addSuppressed(uncertain);
				marble.phase = Phase.RECOVERY_REQUIRED;
				this.marbles.put(caster.getUUID(), marble);
			}
			if (failure instanceof IOException io) {
				throw io;
			}
			throw new IOException("UBW construction failed; inspect controller status and terrain journal", failure);
		}
		this.marbles.put(caster.getUUID(), marble);
		AlayaMana.spend(caster, config.openingMana());
		return OpenResult.OPENED;
	}

	public Optional<Status> status(UUID caster) {
		this.thread();
		Marble marble = this.marbles.get(caster);
		return marble == null ? Optional.empty() : Optional.of(marble.status());
	}

	/** Charges upkeep at most once a server tick, however often it is called; a closing marble pays none. */
	public void tick(UUID caster) throws IOException {
		this.thread();
		Marble marble = this.marbles.get(caster);
		if (marble == null) {
			return;
		}
		if (marble.phase != Phase.ACTIVE && marble.phase != Phase.SHATTERING) {
			this.cancel(caster);
			return;
		}
		if (!this.server.tickRateManager().runsNormally()) {
			return;
		}
		int tick = this.server.getTickCount();
		if (marble.lastTick == tick) {
			return;
		}
		marble.lastTick = tick;
		if (marble.phase == Phase.SHATTERING) {
			this.crumble(caster, marble);
			return;
		}
		if (marble.opening) {
			this.spread(marble);
		}
		double cost = marble.config.upkeepManaPerSecond() / SharedConstants.TICKS_PER_SECOND;
		if (AlayaMana.available(marble.caster) < cost) {
			this.cancel(caster);
		} else {
			AlayaMana.spend(marble.caster, cost);
		}
	}

	private void spread(Marble marble) throws IOException {
		ServerLevel level = this.server.getLevel(marble.dimension);
		if (level == null) {
			throw new IOException("UBW opening dimension unavailable");
		}
		marble.front = UbwOpening.reach(level.getGameTime() - marble.openedAt, marble.span, marble.config.opening());
		marble.opening = !this.terrain.advance(marble.operation, marble.front);
		if (!marble.opening) {
			marble.front = Double.POSITIVE_INFINITY;
		}
	}

	/**
	 * Breaks the marble where Enuma Elish crossed its shell, {@code impact}: it stops charging upkeep, crumbles away from
	 * there, then closes. Returns false unless it stood.
	 */
	public boolean shatter(UUID caster, Vec3 impact) throws IOException {
		this.thread();
		Marble marble = this.marbles.get(caster);
		ServerLevel level = marble == null ? null : this.server.getLevel(marble.dimension);
		if (level == null || marble.phase != Phase.ACTIVE) {
			return false;
		}
		this.terrain.crumble(marble.operation, impact);
		marble.phase = Phase.SHATTERING;
		marble.opening = false;
		marble.impact = impact;
		marble.shatteredAt = level.getGameTime();
		marble.shatterSpan = UbwShatter.span(impact.subtract(Vec3.atLowerCornerOf(marble.center)), marble.config.radius());
		marble.fallen = 0;
		return true;
	}

	private void crumble(UUID caster, Marble marble) throws IOException {
		ServerLevel level = this.server.getLevel(marble.dimension);
		if (level == null) {
			throw new IOException("Broken UBW dimension unavailable");
		}
		double age = level.getGameTime() - marble.shatteredAt;
		int ticks = marble.config.shatterTicks();
		marble.fallen = UbwShatter.fall(age, ticks, marble.shatterSpan);
		this.terrain.retreat(marble.operation, marble.fallen);
		if (age >= ticks) {
			this.cancel(caster);
		}
	}

	public void finishOpening(UUID caster) throws IOException {
		this.thread();
		Marble marble = this.marbles.get(caster);
		if (marble == null || !marble.opening) {
			return;
		}
		ServerLevel level = this.server.getLevel(marble.dimension);
		if (level != null) {
			marble.openedAt = level.getGameTime() - marble.config.opening().spreadTicks();
		}
		this.spread(marble);
	}

	public CloseResult cancel(UUID caster) throws IOException {
		this.thread();
		Marble marble = this.marbles.get(caster);
		if (marble == null) {
			return CloseResult.CLOSED;
		}
		if (marble.phase == Phase.RECOVERY_REQUIRED) {
			throw new IOException("UBW controller requires explicit terrain recovery");
		}
		marble.phase = Phase.CLOSING;
		UbwRuntime.closing(this.server, caster);
		if (!this.terrain.restore(marble.operation)) {
			return CloseResult.PENDING;
		}
		this.marbles.remove(caster);
		return CloseResult.CLOSED;
	}

	/**
	 * Forgets an uncertain opening attempt once the caller has recovered the terrain journal, which then restores whatever
	 * the attempt left, now or when its chunks load.
	 */
	public void releaseRecovered(UUID caster) throws IOException {
		this.thread();
		Marble marble = this.marbles.get(caster);
		if (marble == null) {
			return;
		}
		if (marble.phase != Phase.RECOVERY_REQUIRED) {
			throw new IOException("UBW recovery is not complete");
		}
		this.terrain.pending(); // Refused while the journal is uncertain.
		this.marbles.remove(caster);
	}

	public Set<UUID> owners() {
		this.thread();
		return Set.copyOf(this.marbles.keySet());
	}

	public @Nullable UbwConfig config(UUID owner) {
		Marble marble = this.marble(owner);
		return marble == null ? null : marble.config;
	}

	public @Nullable Vec3 origin(UUID owner) {
		Marble marble = this.marble(owner);
		return marble == null ? null : marble.origin;
	}

	public long openedAt(UUID owner) {
		Marble marble = this.marble(owner);
		return marble == null ? 0 : marble.openedAt;
	}

	public double front(UUID owner) {
		Marble marble = this.marble(owner);
		return marble == null ? 0 : marble.front;
	}

	public @Nullable Vec3 impact(UUID owner) {
		Marble marble = this.marble(owner);
		return marble == null ? null : marble.impact;
	}

	public double fallen(UUID owner) {
		Marble marble = this.marble(owner);
		return marble == null ? 0 : marble.fallen;
	}

	static List<RealityMarble> marbles(MinecraftServer server) {
		UbwController controller = INSTANCES.get(server);
		return controller == null ? List.of() : List.copyOf(controller.marbles.values());
	}

	public static void stopped(MinecraftServer server) {
		INSTANCES.remove(server);
	}

	public boolean protects(ResourceKey<Level> dimension, BlockPos pos) {
		this.thread();
		Vec3 corner = Vec3.atLowerCornerOf(pos);
		for (Marble marble : this.marbles.values()) {
			if (marble.dimension.equals(dimension) && pos.getY() >= marble.minY && pos.getY() < marble.maxY && marble.contains(corner)) {
				return true;
			}
		}
		return false;
	}

	private Marble marble(UUID owner) {
		this.thread();
		return this.marbles.get(owner);
	}

	private void thread() {
		if (!this.server.isSameThread()) {
			throw new IllegalStateException("UBW controller requires server thread");
		}
	}
}
