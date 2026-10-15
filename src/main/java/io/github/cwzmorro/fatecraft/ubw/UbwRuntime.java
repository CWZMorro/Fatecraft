package io.github.cwzmorro.fatecraft.ubw;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.marble.RealityMarble;
import io.github.cwzmorro.alayacore.marble.RealityMarbleSource;
import io.github.cwzmorro.alayacore.marble.RealityMarbles;
import io.github.cwzmorro.alayacore.thrown.ThrownWeaponEntity;
import io.github.cwzmorro.alayacore.util.ServerState;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.trace.Projections;
import io.github.cwzmorro.fatecraft.trace.Traces;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Runs Unlimited Blade Works on a live server, wired to its lifecycle events by {@link #register}; recovery only touches loaded chunks. */
public final class UbwRuntime {
	private static final Logger LOGGER = LoggerFactory.getLogger("fatecraft/ubw");
	private static final ServerState<UbwRuntime> INSTANCES = ServerState.create(Fatecraft.id("ubw_runtime"));
	private static final int RETRY_TICKS = 20;
	// Ground swords keep this far (squared, in blocks) from the caster's feet, so none stands where he stands.
	private static final double CASTER_CLEARANCE_SQR = 4;
	private static final int DEGREES_PER_TURN = 360;
	private static final int LEAN_STEPS = 1_000;
	private static final int LEAN_BITS = 20;
	private static final int FLOOR_DEPTH = 1;
	// Guards a division by a zero-length segment when a projectile's path is turned into a fraction, and the slack when comparing those fractions.
	private static final double MIN_SEGMENT = 1e-9;
	private static final double FRACTION_TOLERANCE = 1e-7;
	// The 64-bit mixing step that spreads a ground sword's position over the whole hash: its two multipliers and three shifts.
	private static final long MIX_FIRST = 0xbf58476d1ce4e5b9L;
	private static final long MIX_SECOND = 0x94d049bb133111ebL;
	private static final int MIX_SHIFT_FIRST = 30;
	private static final int MIX_SHIFT_SECOND = 27;
	private static final int MIX_SHIFT_LAST = 31;
	private final MinecraftServer server;
	private UbwTerrain terrain;
	private UbwController controller;
	private final Map<UUID, Field> fields = new HashMap<>();
	private final Set<UUID> closing = new HashSet<>();
	private final Set<UUID> due = new HashSet<>();
	private boolean ready;
	private int retryAt;
	private int lastTick = Integer.MIN_VALUE;
	private String lastError;

	private static final class Field {
		final LivingEntity owner;
		final UbwController.Status status;
		final UbwConfig config;
		final UbwEnvironmentEntity environment;
		final List<UbwGroundSwordEntity> swords = new ArrayList<>();
		final ArrayDeque<Slot> unreached = new ArrayDeque<>();
		boolean broken;

		Field(LivingEntity owner, UbwController.Status status, UbwConfig config, UbwEnvironmentEntity environment) {
			this.owner = owner;
			this.status = status;
			this.config = config;
			this.environment = environment;
		}
	}

	private record Slot(Vec3 point, ItemStack sword, float yaw, float lean, double distance) { }

	private UbwRuntime(MinecraftServer server) {
		this.server = server;
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(UbwRuntime::started);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			if (success) {
				reloaded(server);
			}
		});
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> loaded(level, chunk));
		ServerTickEvents.END_SERVER_TICK.register(UbwRuntime::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(UbwRuntime::stopping);
		ServerLifecycleEvents.SERVER_STOPPED.register(UbwRuntime::stopped);
		RealityMarbles.register(new RealityMarbleSource() {
			@Override
			public Collection<? extends RealityMarble> marbles(MinecraftServer server) {
				return UbwController.marbles(server);
			}

			@Override
			public boolean protects(ServerLevel level, BlockPos pos) {
				return UbwRuntime.protects(level, pos);
			}
		});
	}

	private static UbwRuntime of(MinecraftServer server) {
		if (!server.isSameThread()) {
			throw new IllegalStateException("UBW runtime requires server thread");
		}
		UbwRuntime runtime = INSTANCES.get(server);
		if (runtime == null) {
			runtime = new UbwRuntime(server);
			INSTANCES.set(server, runtime);
			runtime.recover();
		}
		return runtime;
	}

	/** Recovers the terrain journal as the server starts; a failure is logged and kept, never read as an empty journal. */
	public static void started(MinecraftServer server) {
		of(server);
	}

	/** Closes every marble after a data reload, then recovers the journal again. */
	public static void reloaded(MinecraftServer server) {
		UbwRuntime runtime = of(server);
		for (UUID owner : List.copyOf(runtime.fields.keySet())) {
			runtime.close(owner);
		}
		runtime.ready = false;
		runtime.recover();
	}

	/**
	 * Schedules the restorations waiting on a chunk that loaded, without reading the journal; nothing may restore or discard
	 * entities while the chunk is still listing them.
	 */
	public static void loaded(ServerLevel level, LevelChunk chunk) {
		UbwRuntime runtime = INSTANCES.get(level.getServer());
		if (runtime != null && runtime.terrain != null) {
			runtime.due.addAll(runtime.terrain.waiting(level.dimension(), chunk.getPos()));
		}
	}

	private void recover() {
		this.retryAt = this.server.getTickCount() + RETRY_TICKS;
		this.due.clear();
		try {
			this.terrain = UbwTerrain.reloadForRecovery(this.server);
			// What waits on unloaded chunks is restored when they load; it holds back nothing else.
			this.terrain.recover();
			this.controller = UbwController.of(this.server);
			for (UUID owner : this.controller.owners()) {
				if (this.controller.status(owner).orElseThrow().phase() == UbwController.Phase.RECOVERY_REQUIRED) {
					this.controller.releaseRecovered(owner);
				} else {
					this.controller.cancel(owner);
				}
			}
			// A closing that still waits on unloaded chunks stays closing.
			this.closing.retainAll(this.controller.owners());
			this.ready = true;
			this.lastError = null;
		} catch (IOException | RuntimeException failure) {
			this.ready = false;
			this.error("recovery", failure);
		}
	}

	/** Runs once a server tick, however often it is called; missed ticks aren't caught up. */
	public static void tick(MinecraftServer server) {
		UbwRuntime runtime = INSTANCES.get(server);
		if (runtime == null || runtime.lastTick == server.getTickCount()) {
			return;
		}
		runtime.lastTick = server.getTickCount();
		if (!runtime.ready) {
			if (server.getTickCount() >= runtime.retryAt) {
				runtime.recover();
			}
			return;
		}
		runtime.restoreLoaded();
		boolean retry = server.getTickCount() >= runtime.retryAt;
		for (UUID owner : List.copyOf(runtime.closing)) {
			if (runtime.fields.containsKey(owner) || retry) {
				runtime.close(owner);
			}
		}
		if (retry) {
			runtime.retryAt = server.getTickCount() + RETRY_TICKS;
		}
		for (Field field : List.copyOf(runtime.fields.values())) {
			LivingEntity owner = field.owner;
			// Restoring and closing run while the game is frozen; only a normal or stepped tick advances the marbles.
			if (!server.tickRateManager().runsNormally()) {
				continue;
			}
			try {
				runtime.controller.tick(owner.getUUID());
			} catch (IOException failure) {
				runtime.close(owner.getUUID());
				runtime.error("upkeep/close", failure);
				continue;
			}
			if (runtime.fields.get(owner.getUUID()) != field) {
				continue;
			}
			if (field.broken) {
				runtime.crumble(field);
			} else {
				runtime.reveal(field);
			}
		}
	}

	private void restoreLoaded() {
		if (this.due.isEmpty()) {
			return;
		}
		Map<UUID, UUID> closers = new HashMap<>();
		for (UUID owner : this.controller.owners()) {
			this.controller.status(owner).orElseThrow().operation().ifPresent(operation -> closers.put(operation, owner));
		}
		for (UUID operation : List.copyOf(this.due)) {
			this.due.remove(operation);
			UUID owner = closers.get(operation);
			if (owner != null) {
				this.close(owner);
			} else {
				try {
					this.terrain.restore(operation);
				} catch (IOException | RuntimeException failure) {
					this.failClosed();
					this.error("recovery", failure);
				}
			}
			if (!this.ready) {
				return;
			}
		}
	}

	static boolean shatter(MinecraftServer server, UUID owner, Vec3 impact) {
		UbwRuntime runtime = INSTANCES.get(server);
		Field field = runtime == null || !runtime.ready ? null : runtime.fields.get(owner);
		if (field == null || !runtime.intact(field)) {
			return false;
		}
		try {
			if (!runtime.controller.shatter(owner, impact)) {
				return false;
			}
		} catch (IOException | RuntimeException failure) {
			runtime.close(owner);
			runtime.error("shatter", failure);
			return false;
		}
		field.broken = true;
		field.unreached.clear();
		field.environment.shatter(impact, field.environment.level().getGameTime(), field.config.shatterTicks());
		Abilities.end(field.owner, UnlimitedBladeWorks.KEY.identifier());
		return true;
	}

	private void crumble(Field field) {
		Vec3 impact = this.controller.impact(field.owner.getUUID());
		double fallen = this.controller.fallen(field.owner.getUUID());
		field.swords.removeIf(sword -> {
			if (sword.isRemoved()) {
				return true;
			}
			if (sword.position().distanceTo(impact) > fallen) {
				return false;
			}
			sword.discard();
			return true;
		});
	}

	private boolean intact(Field field) {
		return !field.broken && !this.closing.contains(field.owner.getUUID());
	}

	public static boolean shattering(LivingEntity owner) {
		if (!(owner.level() instanceof ServerLevel level)) {
			return false;
		}
		UbwRuntime runtime = INSTANCES.get(level.getServer());
		Field field = runtime == null ? null : runtime.fields.get(owner.getUUID());
		return field != null && field.broken;
	}

	/** Returns whether recovery has run, so casting is allowed; restorations waiting on unloaded chunks hold back only their own terrain. */
	public static boolean ready(MinecraftServer server) {
		return of(server).ready;
	}

	/**
	 * Opens the owner's marble, a player's or a mob's. A failed opening keeps the journal, and closes every marble if the
	 * journal is left uncertain.
	 */
	public static UbwController.OpenResult open(LivingEntity owner) throws IOException {
		ServerLevel level = (ServerLevel) owner.level();
		UbwRuntime runtime = of(level.getServer());
		if (!runtime.ready) {
			throw new IOException("UBW recovery pending; casting refused (journal retained)");
		}
		if (!UnlimitedBladeWorks.eligible(owner)) {
			throw new IOException("UBW requires EMIYA's realized true name");
		}
		UbwConfig config = FateConfig.ubw();
		int floor = (int) Math.floor(owner.getY()) - 1;
		UbwController.OpenResult result;
		try {
			result = runtime.controller.open(owner, config, floor, FLOOR_DEPTH, Ubw.palette(), true);
		} catch (IOException | RuntimeException failure) {
			runtime.error("opening", failure);
			if (runtime.controller.status(owner.getUUID()).isPresent()) {
				runtime.close(owner.getUUID());
			}
			// A losing marble can fail to close before this one has any status. A plain refusal changes nothing, but an
			// uncertain journal closes every marble.
			if (runtime.ready) {
				try {
					runtime.terrain.pending();
				} catch (IOException uncertain) {
					failure.addSuppressed(uncertain);
					runtime.failClosed();
				}
			}
			if (failure instanceof IOException io) {
				throw io;
			}
			throw new IOException("UBW opening failed; journal retained", failure);
		}
		if (result != UbwController.OpenResult.OPENED) {
			return result;
		}
		UbwController.Status status = runtime.controller.status(owner.getUUID()).orElseThrow();
		UUID id = owner.getUUID();
		UbwEnvironmentEntity environment = UbwEnvironmentEntity.create(level, id, status.center(), status.radius(), floor,
			runtime.controller.origin(id), runtime.controller.openedAt(id), config.opening());
		Field field = new Field(owner, status, config, environment);
		runtime.fields.put(id, field);
		runtime.populate(field);
		runtime.reveal(field);
		return result;
	}

	public static void finishOpening(LivingEntity owner) throws IOException {
		UbwRuntime runtime = of(((ServerLevel) owner.level()).getServer());
		Field field = runtime.fields.get(owner.getUUID());
		if (field == null) {
			return;
		}
		runtime.controller.finishOpening(owner.getUUID());
		field.environment.openedAt(runtime.controller.openedAt(owner.getUUID()));
		runtime.reveal(field);
	}

	public static UbwController.CloseResult cancel(LivingEntity entity) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return UbwController.CloseResult.CLOSED;
		}
		return of(level.getServer()).close(entity.getUUID());
	}

	private UbwController.CloseResult close(UUID owner) {
		if (this.controller == null) {
			return UbwController.CloseResult.PENDING;
		}
		try {
			UbwController.CloseResult result = this.controller.cancel(owner);
			if (result == UbwController.CloseResult.CLOSED) {
				this.closing.remove(owner);
			}
			return result;
		} catch (IOException | RuntimeException failure) {
			closing(this.server, owner);
			this.failClosed();
			this.error("close", failure);
			return UbwController.CloseResult.PENDING;
		}
	}

	private void failClosed() {
		for (UUID other : List.copyOf(this.fields.keySet())) {
			closing(this.server, other);
		}
		this.ready = false;
		this.retryAt = this.server.getTickCount() + RETRY_TICKS;
	}

	// The controller calls this before restoration, so no pickup, free projection or firing survives a pending close.
	static void closing(MinecraftServer server, UUID owner) {
		UbwRuntime runtime = INSTANCES.get(server);
		if (runtime == null) {
			return;
		}
		runtime.closing.add(owner);
		Field field = runtime.fields.remove(owner);
		if (field != null) {
			field.environment.closing();
			field.environment.discard();
			field.swords.forEach(UbwGroundSwordEntity::discard);
			Abilities.end(field.owner, UnlimitedBladeWorks.KEY.identifier());
		}
	}

	static boolean forceClose(MinecraftServer server, UUID owner) {
		UbwRuntime runtime = INSTANCES.get(server);
		return runtime != null && runtime.close(owner) == UbwController.CloseResult.CLOSED;
	}

	/** Restores what is loaded as the server stops; the rest stays journalled for the next start. */
	public static void stopping(MinecraftServer server) {
		UbwRuntime runtime = INSTANCES.get(server);
		if (runtime == null) {
			return;
		}
		for (UUID owner : List.copyOf(runtime.fields.keySet())) {
			runtime.close(owner);
		}
		for (UUID owner : List.copyOf(runtime.closing)) {
			runtime.close(owner);
		}
		if (!runtime.ready) {
			runtime.recover();
		}
	}

	/** Forgets the server's state once it has stopped, even after a journal failure. */
	public static void stopped(MinecraftServer server) {
		INSTANCES.remove(server);
		UbwController.stopped(server);
		UbwTerrain.stopped(server);
	}

	public static boolean inside(LivingEntity owner, Vec3 point) {
		if (!(owner.level() instanceof ServerLevel level)) {
			return false;
		}
		UbwRuntime runtime = INSTANCES.get(level.getServer());
		Field field = runtime == null ? null : runtime.fields.get(owner.getUUID());
		return field != null && runtime.intact(field) && field.status.dimension().equals(level.dimension())
			&& UbwMembership.contains(field.status.center(), field.status.radius(), point);
	}

	public static boolean hasMarble(LivingEntity owner) {
		if (!(owner.level() instanceof ServerLevel level)) {
			return false;
		}
		UbwRuntime runtime = INSTANCES.get(level.getServer());
		return runtime != null && runtime.controller != null && runtime.controller.status(owner.getUUID()).isPresent();
	}

	static boolean ownsGround(MinecraftServer server, UUID owner, UUID operation) {
		UbwRuntime runtime = INSTANCES.get(server);
		Field field = runtime == null ? null : runtime.fields.get(owner);
		return field != null && !runtime.closing.contains(owner) && field.status.operation().orElseThrow().equals(operation);
	}

	static boolean pullable(MinecraftServer server, UUID owner, UUID operation) {
		UbwRuntime runtime = INSTANCES.get(server);
		Field field = runtime == null ? null : runtime.fields.get(owner);
		return field != null && !field.broken && ownsGround(server, owner, operation);
	}

	public static boolean protects(Level level, BlockPos pos) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return false;
		}
		UbwRuntime runtime = INSTANCES.get(serverLevel.getServer());
		return runtime != null && ((runtime.controller != null && runtime.controller.protects(level.dimension(), pos))
			|| (runtime.terrain != null && runtime.terrain.protects(level.dimension(), pos)));
	}

	/** Returns his arsenal: the swords he has traced, never a divine construct or Excalibur (projected only at its strain). */
	public static List<Item> swords(LivingEntity owner) {
		return Traces.known(owner).stream()
			.map(id -> BuiltInRegistries.ITEM.getOptional(id).orElse(null))
			.filter(item -> item != null && Traces.sword(item.getDefaultInstance()) && !Traces.divine(item.getDefaultInstance())
				&& !Traces.strained(item))
			.toList();
	}

	/**
	 * Returns how many swords stand in his field: {@link UbwConfig#groundSwordsPerTrace} for each sword of his arsenal,
	 * times his own amount setting, at most {@link UbwConfig#maxGroundSwords}.
	 */
	public static int groundSwords(LivingEntity owner, UbwConfig config) {
		long count = Math.round(config.groundSwordsPerTrace() * swords(owner).size() * UbwSettings.groundAmount(owner));
		return (int) Math.min(config.maxGroundSwords(), count);
	}

	public static List<Item> volleySwords(LivingEntity owner) {
		return swords(owner).stream().filter(Traces::volleyable).toList();
	}

	private void populate(Field field) {
		ServerLevel level = (ServerLevel) field.owner.level();
		List<Item> known = swords(field.owner);
		if (known.isEmpty()) {
			return;
		}
		BlockPos center = field.status.center();
		int radius = field.status.radius();
		int floor = field.environment.floorY();
		Vec3 origin = this.controller.origin(field.owner.getUUID());
		double reach = UbwSettings.groundSpread(field.owner) * field.environment.span();
		double tilt = UbwSettings.groundTilt(field.owner);
		Map<BlockPos, BlockState> standing = UbwLayout.sphere(level, center, field.config, floor, FLOOR_DEPTH, Ubw.palette());
		Function<BlockPos, BlockState> state = pos -> standing.getOrDefault(pos, level.getBlockState(pos));
		List<BlockPos> positions = new ArrayList<>();
		for (int x = -radius + 1; x < radius; x++) {
			for (int z = -radius + 1; z < radius; z++) {
				BlockPos ground = new BlockPos(center.getX() + x, floor, center.getZ() + z);
				if (!state.apply(ground).is(Ubw.GROUND) || !state.apply(ground.above()).isAir() || !state.apply(ground.above(2)).isAir()) {
					continue;
				}
				Vec3 point = standingPoint(ground, floor);
				if (point.distanceToSqr(field.owner.position()) > CASTER_CLEARANCE_SQR
					&& UbwMembership.contains(center, radius - 1, point.add(0, 1, 0))
					&& Math.hypot(point.x - origin.x, point.z - origin.z) <= reach) {
					positions.add(ground);
				}
			}
		}
		// A hash of each position spreads them over the field, not in rows or clumps; the item types take turns.
		long operationSalt = field.status.operation().orElseThrow().getLeastSignificantBits();
		positions.sort(Comparator.comparingLong(pos -> spread(pos.asLong() ^ operationSalt)));
		int count = Math.min(groundSwords(field.owner, field.config), positions.size());
		List<Slot> slots = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			BlockPos ground = positions.get(i);
			Vec3 point = standingPoint(ground, floor);
			long hash = spread(ground.asLong());
			float lean = (float) (tilt * Math.floorMod(hash >>> LEAN_BITS, LEAN_STEPS) / LEAN_STEPS);
			float yaw = (float) Math.floorMod(hash, DEGREES_PER_TURN);
			double distance = Math.hypot(point.x - origin.x, point.z - origin.z);
			slots.add(new Slot(point, Projections.create(known.get(i % known.size())), yaw, lean, distance));
		}
		slots.sort(Comparator.comparingDouble(Slot::distance));
		field.unreached.addAll(slots);
	}

	private static Vec3 standingPoint(BlockPos ground, int floor) {
		return new Vec3(ground.getX() + 0.5, floor + 1, ground.getZ() + 0.5);
	}

	private void reveal(Field field) {
		double front = this.controller.front(field.owner.getUUID());
		while (!field.unreached.isEmpty() && field.unreached.peek().distance() <= front) {
			Slot slot = field.unreached.poll();
			field.swords.add(UbwGroundSwordEntity.create((ServerLevel) field.owner.level(), field.owner.getUUID(), field.status.operation().orElseThrow(),
				slot.sword(), slot.point(), slot.yaw(), slot.lean()));
		}
	}

	private static long spread(long value) {
		long mixed = (value ^ (value >>> MIX_SHIFT_FIRST)) * MIX_FIRST;
		mixed = (mixed ^ (mixed >>> MIX_SHIFT_SECOND)) * MIX_SECOND;
		return mixed ^ (mixed >>> MIX_SHIFT_LAST);
	}

	static boolean counter(ServerLevel level, ThrownWeaponEntity original, Vec3 from, Vec3 to, BlockHitResult block, Vec3 firstImpact) {
		UbwRuntime runtime = INSTANCES.get(level.getServer());
		// A divine construct (Ea) entering the marble is not copied: it passes through uncountered.
		if (runtime == null || !UbwSwords.incoming(original) || Traces.divine(original.item())) {
			return false;
		}
		Field selected = null;
		double earliest = Double.POSITIVE_INFINITY;
		double length = Math.max(MIN_SEGMENT, from.distanceTo(to));
		double limit = from.distanceTo(firstImpact) / length;
		boolean blockHit = block.getType() != HitResult.Type.MISS;
		for (Field field : runtime.fields.values()) {
			if (!runtime.intact(field) || !field.status.dimension().equals(level.dimension()) || field.owner.getUUID().equals(original.owner())) {
				continue;
			}
			double entry = UbwMembership.entry(field.status.center(), field.status.radius(), from, to);
			// The shell's blocks stand just inside the sphere's radius, so a weapon hitting one is met there too.
			boolean shell = blockHit && level.getBlockState(block.getBlockPos()).is(Ubw.SHELL)
				&& UbwMembership.contains(field.status.center(), field.status.radius(), Vec3.atLowerCornerOf(block.getBlockPos()));
			double hit = blockHit ? from.distanceTo(block.getLocation()) / length : Double.POSITIVE_INFINITY;
			if (Double.isNaN(entry) && shell) {
				entry = hit;
			}
			if (Double.isNaN(entry) || entry > hit + FRACTION_TOLERANCE && !shell) {
				continue;
			}
			entry = shell ? Math.min(entry, hit) : entry;
			if (entry > limit + FRACTION_TOLERANCE) {
				continue;
			}
			if (entry < earliest) {
				earliest = entry;
				selected = field;
			}
		}
		if (selected == null) {
			return false;
		}
		UbwSwords.oppose(level, original, selected.owner, from.lerp(to, earliest));
		return true;
	}

	private void error(String action, Throwable failure) {
		String signature = action + ": " + failure;
		if (!signature.equals(this.lastError)) {
			LOGGER.error("UBW {} failed; ownership/journal retained; inspect checkpoint before casting", action, failure);
		}
		this.lastError = signature;
	}
}
