package io.github.cwzmorro.fatecraft.ubw;

import io.github.cwzmorro.alayacore.util.ServerState;
import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.ubw.UbwCheckpoint.Operation;
import io.github.cwzmorro.fatecraft.ubw.UbwCheckpoint.Original;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntToDoubleFunction;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The terrain Unlimited Blade Works replaces, journalled before it changes so it can always be put back: opened, staged,
 * crumbled and restored by {@link UbwController}, recovered by {@link UbwRuntime}. Server thread only. Recovery overwrites
 * other changes to the replaced blocks; entities and scheduled ticks are not rolled back.
 */
public final class UbwTerrain {
	private static final ServerState<UbwTerrain> INSTANCES = ServerState.create(Fatecraft.id("ubw_terrain"));
	private static final int TICKET_RADIUS = 0;
	private final MinecraftServer server;
	private final UbwCheckpoint checkpoint;
	private List<Operation> operations = List.of();
	// The staging and crumbling maps live in memory only, so recovery restores every original.
	private final Map<UUID, Staging> staging = new HashMap<>();
	private final Map<UUID, Crumble> crumbling = new HashMap<>();
	private final Map<WaitingChunk, Set<UUID>> waiting = new HashMap<>();
	private boolean uncertain = true;

	private static final class Staging {
		final ServerLevel level;
		final int[] order;
		final double[] distance;
		final BlockState[] target;
		int placed;

		Staging(ServerLevel level, int[] order, double[] distance, BlockState[] target) {
			this.level = level;
			this.order = order;
			this.distance = distance;
			this.target = target;
		}
	}

	private static final class Crumble {
		final ServerLevel level;
		final int[] order;
		final double[] distance;
		int restored;

		Crumble(ServerLevel level, int[] order, double[] distance) {
			this.level = level;
			this.order = order;
			this.distance = distance;
		}
	}

	private record WaitingChunk(ResourceKey<Level> dimension, ChunkPos chunk) { }

	private UbwTerrain(MinecraftServer server) {
		this.server = server;
		this.checkpoint = new UbwCheckpoint(server);
	}

	public static UbwTerrain of(MinecraftServer server) throws IOException {
		if (!server.isSameThread()) {
			throw new IllegalStateException("UBW terrain requires server thread");
		}
		UbwTerrain terrain = INSTANCES.get(server);
		if (terrain == null) {
			terrain = new UbwTerrain(server);
			INSTANCES.set(server, terrain);
			terrain.reload();
		}
		terrain.ready();
		return terrain;
	}

	/** Returns the server's terrain after reading the journal again, even when an earlier read failed. */
	public static UbwTerrain reloadForRecovery(MinecraftServer server) throws IOException {
		if (!server.isSameThread()) {
			throw new IllegalStateException("UBW terrain requires server thread");
		}
		UbwTerrain terrain = INSTANCES.getOrCreate(server, UbwTerrain::new);
		terrain.reload();
		return terrain;
	}

	public Path checkpointPath() {
		return this.checkpoint.path();
	}

	public static void stopped(MinecraftServer server) {
		INSTANCES.remove(server);
	}

	public boolean protects(ResourceKey<Level> dimension, BlockPos pos) {
		this.thread();
		for (Operation operation : this.operations) {
			if (!operation.dimension().equals(dimension)) {
				continue;
			}
			if (operation.volume().isPresent()) {
				UbwCheckpoint.Volume volume = operation.volume().get();
				if (UbwMembership.contains(volume.center(), volume.radius(), Vec3.atLowerCornerOf(pos))) {
					return true;
				}
			}
			for (Original original : operation.originals()) {
				if (original.pos().equals(pos)) {
					return true;
				}
			}
		}
		return false;
	}

	public void reload() throws IOException {
		this.thread();
		this.uncertain = true;
		this.staging.clear();
		this.crumbling.clear();
		for (WaitingChunk chunk : this.waiting.keySet()) {
			this.setTicket(chunk, false);
		}
		this.waiting.clear();
		List<Operation> loaded = this.checkpoint.read();
		Set<UUID> owners = new HashSet<>();
		Map<Object, Set<BlockPos>> positions = new HashMap<>();
		for (Operation operation : loaded) {
			if (!owners.add(operation.owner()) || operation.originals().isEmpty()) {
				throw new IOException("Invalid UBW operation ownership");
			}
			Set<BlockPos> owned = positions.computeIfAbsent(operation.dimension(), key -> new HashSet<>());
			for (Original original : operation.originals()) {
				if (!owned.add(original.pos())) {
					throw new IOException("Conflicting UBW checkpoint positions");
				}
				this.entity(original); // Each block entity must load and save back unchanged.
			}
		}
		this.operations = loaded;
		this.uncertain = false;
	}

	public Set<UUID> pending() throws IOException {
		this.ready();
		Set<UUID> result = new HashSet<>();
		for (Operation operation : this.operations) {
			result.add(operation.owner());
		}
		return Set.copyOf(result);
	}

	public Set<UUID> waiting(ResourceKey<Level> dimension, ChunkPos chunk) {
		this.thread();
		return Set.copyOf(this.waiting.getOrDefault(new WaitingChunk(dimension, chunk), Set.of()));
	}

	public UUID construct(ServerLevel level, BlockPos center, UbwConfig config, int floorY, int floorDepth, UbwLayout.Palette palette)
		throws IOException {
		return this.construct(level, center, config, floorY, floorDepth, palette, null);
	}

	/**
	 * Journals and replaces the sphere's terrain, returning the operation's id. With a {@code front} origin nothing is placed
	 * yet: {@link #advance} places each block as the opening's front reaches it.
	 */
	public UUID construct(ServerLevel level, BlockPos center, UbwConfig config, int floorY, int floorDepth, UbwLayout.Palette palette,
		@Nullable Vec3 front) throws IOException {
		this.ready();
		if (level.getServer() != this.server) {
			throw new IllegalArgumentException("Foreign UBW server");
		}
		Map<BlockPos, BlockState> layout = UbwLayout.sphere(level, center, config, floorY, floorDepth, palette);
		if (layout.isEmpty()) {
			throw new IOException("Empty UBW layout");
		}
		for (Operation operation : this.operations) {
			if (!operation.dimension().equals(level.dimension())) {
				continue;
			}
			for (Original original : operation.originals()) {
				if (layout.containsKey(original.pos())) {
					throw new IOException("UBW ownership overlap: " + operation.owner());
				}
			}
		}
		List<Original> originals = new ArrayList<>();
		for (BlockPos pos : layout.keySet()) {
			LevelChunk chunk = this.loaded(level, pos);
			if (chunk == null) {
				throw new IOException("UBW chunk unavailable at " + pos);
			}
			originals.add(this.capture(chunk, pos));
		}
		UUID owner = UUID.randomUUID();
		List<Operation> next = new ArrayList<>(this.operations);
		UbwCheckpoint.Volume volume = new UbwCheckpoint.Volume(center.immutable(), config.radius());
		next.add(new Operation(owner, level.dimension(), List.copyOf(originals), Optional.of(volume)));
		this.commit(next);
		if (front != null) {
			// Originals and layout share the layout's iteration order, so one index addresses both.
			BlockState[] target = layout.values().toArray(BlockState[]::new);
			IntToDoubleFunction reach = i -> Math.hypot(originals.get(i).pos().getX() + 0.5 - front.x, originals.get(i).pos().getZ() + 0.5 - front.z);
			int[] order = nearestFirst(IntStream.range(0, target.length), reach);
			this.staging.put(owner, new Staging(level, order, distances(order, reach), target));
			return owner;
		}
		// A failed placement leaves the entire operation owned and recoverable.
		for (Map.Entry<BlockPos, BlockState> entry : layout.entrySet()) {
			this.place(level, entry.getKey(), entry.getValue(), null);
		}
		return owner;
	}

	/**
	 * Places every staged block within {@code reach} of the front's origin, journalling again any the world changed since the
	 * cast; returns true once the whole layout stands.
	 */
	public boolean advance(UUID owner, double reach) throws IOException {
		this.ready();
		Staging staged = this.staging.get(owner);
		if (staged == null) {
			return true;
		}
		int end = staged.placed;
		while (end < staged.order.length && staged.distance[end] <= reach) {
			end++;
		}
		if (end > staged.placed) {
			List<Original> originals = this.recapture(owner, staged, end);
			for (int i = staged.placed; i < end; i++) {
				int index = staged.order[i];
				// Counted before the write: a failed placement is restored with the rest.
				staged.placed = i + 1;
				this.place(staged.level, originals.get(index).pos(), staged.target[index], null);
			}
		}
		if (staged.placed < staged.order.length) {
			return false;
		}
		this.staging.remove(owner);
		return true;
	}

	private List<Original> recapture(UUID owner, Staging staged, int end) throws IOException {
		Operation operation = this.operation(owner);
		if (operation == null) {
			throw new IOException("Staged UBW operation missing");
		}
		List<Original> changed = null;
		for (int i = staged.placed; i < end; i++) {
			int index = staged.order[i];
			Original original = operation.originals().get(index);
			LevelChunk chunk = this.loaded(staged.level, original.pos());
			if (chunk == null) {
				throw new IOException("UBW position became unavailable: " + original.pos());
			}
			Original now = this.capture(chunk, original.pos());
			if (now.equals(original)) {
				continue;
			}
			if (changed == null) {
				changed = new ArrayList<>(operation.originals());
			}
			changed.set(index, now);
		}
		if (changed == null) {
			return operation.originals();
		}
		List<Operation> next = new ArrayList<>(this.operations);
		next.set(next.indexOf(operation), new Operation(owner, operation.dimension(), List.copyOf(changed), operation.volume()));
		this.commit(next);
		return changed;
	}

	private Original capture(LevelChunk chunk, BlockPos pos) throws IOException {
		BlockEntity entity = chunk.getBlockEntity(pos);
		Optional<CompoundTag> saved = Optional.ofNullable(entity == null ? null : entity.saveWithFullMetadata(this.server.registryAccess()).copy());
		Original original = new Original(pos, chunk.getBlockState(pos), saved);
		this.entity(original);
		return original;
	}

	private @Nullable Operation operation(UUID owner) {
		return this.operations.stream().filter(operation -> operation.owner().equals(owner)).findFirst().orElse(null);
	}

	private static int[] nearestFirst(IntStream indices, IntToDoubleFunction distance) {
		return indices.boxed().sorted(Comparator.comparingDouble(distance::applyAsDouble)).mapToInt(Integer::intValue).toArray();
	}

	private static double[] distances(int[] order, IntToDoubleFunction distance) {
		return IntStream.of(order).mapToDouble(distance).toArray();
	}

	private int[] touched(UUID owner, Operation operation) {
		Staging staged = this.staging.get(owner);
		return staged == null ? IntStream.range(0, operation.originals().size()).toArray() : Arrays.copyOf(staged.order, staged.placed);
	}

	/**
	 * Starts a broken marble's crumble from {@code impact}, which {@link #retreat} then gives back to the world bit by bit.
	 * Blocks with block entities wait for {@link #restore}, so nothing taken from one is restored twice.
	 */
	public void crumble(UUID owner, Vec3 impact) throws IOException {
		this.ready();
		Operation operation = this.operation(owner);
		if (operation == null) {
			throw new IOException("Broken UBW operation missing");
		}
		ServerLevel level = this.server.getLevel(operation.dimension());
		if (level == null) {
			throw new IOException("Broken UBW dimension unavailable");
		}
		List<Original> originals = operation.originals();
		IntToDoubleFunction fromImpact = i -> Vec3.atCenterOf(originals.get(i).pos()).distanceTo(impact);
		IntStream plain = IntStream.of(this.touched(owner, operation)).filter(i -> originals.get(i).entity().isEmpty());
		int[] order = nearestFirst(plain, fromImpact);
		this.crumbling.put(owner, new Crumble(level, order, distances(order, fromImpact)));
	}

	public void retreat(UUID owner, double reach) throws IOException {
		this.ready();
		Crumble crumble = this.crumbling.get(owner);
		Operation operation = this.operation(owner);
		if (crumble == null || operation == null) {
			return;
		}
		AABB fallen = null;
		while (crumble.restored < crumble.order.length && crumble.distance[crumble.restored] <= reach) {
			Original original = operation.originals().get(crumble.order[crumble.restored]);
			// Counted before the write: a failed one is restored again with the rest.
			crumble.restored++;
			this.place(crumble.level, original.pos(), original.state(), null);
			fallen = fallen == null ? new AABB(original.pos()) : fallen.minmax(new AABB(original.pos()));
		}
		if (fallen != null) {
			UbwReturn.occupants(crumble.level, fallen, operation.volume().map(UbwCheckpoint.Volume::radius).orElse(0), false);
		}
	}

	/**
	 * Restores every replaced block whose chunk is loaded, returning true once the whole operation is retired. Blocks in
	 * unloaded chunks stay journalled and protected until those chunks load ({@link #waiting}).
	 */
	public boolean restore(UUID owner) throws IOException {
		this.ready();
		Operation operation = this.operation(owner);
		if (operation == null) {
			return true;
		}
		ServerLevel level = this.server.getLevel(operation.dimension());
		if (level == null) {
			return false;
		}
		// An opening closed before its front crossed the field restores only what the front replaced.
		List<Original> available = new ArrayList<>();
		List<Original> unloaded = new ArrayList<>();
		Map<ChunkPos, LevelChunk> chunks = new HashMap<>();
		for (int index : this.touched(owner, operation)) {
			Original original = operation.originals().get(index);
			if (level.isOutsideBuildHeight(original.pos())) {
				throw new IOException("UBW checkpoint outside current build limits");
			}
			LevelChunk chunk = this.loaded(level, original.pos());
			if (chunk == null) {
				unloaded.add(original);
			} else {
				available.add(original);
				chunks.put(chunk.getPos(), chunk);
			}
		}
		Set<WaitingChunk> waitingOn = new HashSet<>();
		for (Original original : unloaded) {
			waitingOn.add(new WaitingChunk(operation.dimension(), ChunkPos.containing(original.pos())));
		}
		this.waitOn(owner, waitingOn);
		if (available.isEmpty() && !unloaded.isEmpty()) {
			return false;
		}
		// What a crumble already gave back is only persisted with the rest, never placed again.
		Set<BlockPos> crumbled = new HashSet<>();
		Crumble crumble = this.crumbling.get(owner);
		if (crumble != null) {
			for (int i = 0; i < crumble.restored; i++) {
				crumbled.add(operation.originals().get(crumble.order[i]).pos());
			}
		}
		// Only what is restored now can trap anyone.
		Operation restored = unloaded.isEmpty() ? operation : new Operation(owner, operation.dimension(), available, operation.volume());
		var storage = level.getChunkSource().chunkMap;
		try {
			// ChunkMap.save queues its async write before it returns on this thread, so queued writes are drained first and
			// none can land after this restore.
			storage.synchronize(true).join();
			for (Original original : available) {
				if (!crumbled.contains(original.pos())) {
					this.place(level, original.pos(), original.state(), this.entity(original));
				}
			}
			UbwReturn.occupants(level, restored);
			for (Map.Entry<ChunkPos, LevelChunk> entry : chunks.entrySet()) {
				storage.write(entry.getKey(), SerializableChunkData.copyOf(level, entry.getValue()).write()).join();
			}
			storage.synchronize(true).join();
		} catch (RuntimeException failure) {
			throw new IOException("UBW restore/chunk persistence failed; journal retained", failure);
		}
		List<Operation> next = new ArrayList<>(this.operations);
		if (unloaded.isEmpty()) {
			next.remove(operation);
		} else {
			// What stays journalled keeps the operation's owner and volume, so it stays protected until its chunks load.
			next.set(next.indexOf(operation), new Operation(owner, operation.dimension(), List.copyOf(unloaded), operation.volume()));
		}
		this.commit(next); // If this fails, either journal on disk replays safely.
		// The remaining originals are all replaced positions, indexed afresh: no staging or crumble order applies to them, so a
		// crumbled one among them is placed again when its chunk loads, as recovery would.
		this.staging.remove(owner);
		this.crumbling.remove(owner);
		return unloaded.isEmpty();
	}

	// Each chunk someone waits on holds a TERRAIN_TICKET, so it loads without any player near; the ticket goes once nothing waits on it.
	private void waitOn(UUID owner, Set<WaitingChunk> chunks) {
		this.waiting.entrySet().removeIf(entry -> {
			if (chunks.contains(entry.getKey()) || !entry.getValue().remove(owner) || !entry.getValue().isEmpty()) {
				return false;
			}
			this.setTicket(entry.getKey(), false);
			return true;
		});
		for (WaitingChunk chunk : chunks) {
			Set<UUID> owners = this.waiting.get(chunk);
			if (owners == null) {
				owners = new HashSet<>();
				this.waiting.put(chunk, owners);
				this.setTicket(chunk, true);
			}
			owners.add(owner);
		}
	}

	private void setTicket(WaitingChunk chunk, boolean held) {
		ServerLevel level = this.server.getLevel(chunk.dimension());
		if (level != null) {
			if (held) {
				level.getChunkSource().addTicketWithRadius(Ubw.TERRAIN_TICKET, chunk.chunk(), TICKET_RADIUS);
			} else {
				level.getChunkSource().removeTicketWithRadius(Ubw.TERRAIN_TICKET, chunk.chunk(), TICKET_RADIUS);
			}
		}
	}

	/**
	 * Restores every interrupted operation whose chunks are loaded, never loading or generating others, and returns the
	 * operations still pending.
	 */
	public Set<UUID> recover() throws IOException {
		this.ready();
		for (UUID owner : this.pending()) {
			this.restore(owner);
		}
		return this.pending();
	}

	private void commit(List<Operation> next) throws IOException {
		this.uncertain = true;
		this.checkpoint.write(next);
		this.operations = List.copyOf(next);
		this.uncertain = false;
	}

	private @Nullable BlockEntity entity(Original original) throws IOException {
		if (original.entity().isEmpty()) {
			if (original.state().hasBlockEntity()) {
				throw new IOException("Missing UBW original block entity");
			}
			return null;
		}
		CompoundTag tag = original.entity().get();
		BlockEntity entity = BlockEntity.loadStatic(original.pos(), original.state(), tag.copy(), this.server.registryAccess());
		if (entity == null || !entity.getType().isValid(original.state())
			|| !tag.equals(entity.saveWithFullMetadata(this.server.registryAccess()))) {
			throw new IOException("Invalid or lossy UBW original block entity at " + original.pos());
		}
		return entity;
	}

	private void place(ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity entity) throws IOException {
		LevelChunk chunk = this.loaded(level, pos);
		if (chunk == null) {
			throw new IOException("UBW position became unavailable: " + pos);
		}
		chunk.removeBlockEntity(pos);
		level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
		if (!chunk.getBlockState(pos).equals(state)) {
			throw new IOException("UBW block write failed: " + pos);
		}
		if (entity != null) {
			// setBlock registered a default block entity, which goes before the saved one is put in.
			chunk.removeBlockEntity(pos);
			chunk.addAndRegisterBlockEntity(entity);
			entity.setChanged();
			level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
		}
	}

	private @Nullable LevelChunk loaded(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
	}

	private void thread() {
		if (!this.server.isSameThread()) {
			throw new IllegalStateException("UBW terrain requires server thread");
		}
	}

	private void ready() throws IOException {
		this.thread();
		if (this.uncertain) {
			throw new IOException("UBW checkpoint uncertain; explicit reload required");
		}
	}
}
