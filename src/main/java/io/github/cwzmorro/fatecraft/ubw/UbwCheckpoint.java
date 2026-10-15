package io.github.cwzmorro.fatecraft.ubw;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

// The terrain journal has its own file, not SavedData, whose writes swallow errors: an original must be safely on disk
// before the terrain it describes changes.
final class UbwCheckpoint {
	private static final int FORMAT = 1;
	private static final String FILE = "data/fatecraft_ubw_terrain.nbt";

	record Original(BlockPos pos, BlockState state, Optional<CompoundTag> entity) {
		static final Codec<Original> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("pos").forGetter(Original::pos),
			BlockState.CODEC.fieldOf("state").forGetter(Original::state),
			CompoundTag.CODEC.optionalFieldOf("entity").forGetter(Original::entity)
		).apply(instance, Original::new));
	}

	// The whole sphere an operation may change, so it stays protected until recovery is done with it.
	record Volume(BlockPos center, int radius) {
		static final Codec<Volume> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("center").forGetter(Volume::center),
			Codec.intRange(1, Integer.MAX_VALUE).fieldOf("radius").forGetter(Volume::radius)
		).apply(instance, Volume::new));
	}

	record Operation(UUID owner, ResourceKey<Level> dimension, List<Original> originals, Optional<Volume> volume) {
		static final Codec<Operation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("owner").forGetter(Operation::owner),
			ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Operation::dimension),
			Original.CODEC.listOf().fieldOf("originals").forGetter(Operation::originals),
			Volume.CODEC.optionalFieldOf("volume").forGetter(Operation::volume)
		).apply(instance, Operation::new));
	}

	private record Journal(int format, int dataVersion, List<Operation> operations) {
		static final Codec<Journal> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("format").forGetter(Journal::format),
			Codec.INT.fieldOf("data_version").forGetter(Journal::dataVersion),
			Operation.CODEC.listOf().fieldOf("operations").forGetter(Journal::operations)
		).apply(instance, Journal::new));
	}

	private final MinecraftServer server;
	private final Path path;
	private final Path temporary;

	UbwCheckpoint(MinecraftServer server) {
		this.server = server;
		this.path = server.getWorldPath(LevelResource.ROOT).resolve(FILE);
		this.temporary = this.path.resolveSibling(this.path.getFileName() + ".pending");
	}

	Path path() {
		return this.path;
	}

	List<Operation> read() throws IOException {
		// An interrupted write, or a corrupt or incompatible journal, fails the read rather than passing for an empty journal.
		if (Files.exists(this.temporary)) {
			throw new IOException("UBW interrupted checkpoint: " + this.temporary);
		}
		if (Files.notExists(this.path)) {
			return List.of();
		}
		var ops = RegistryOps.create(NbtOps.INSTANCE, this.server.registryAccess());
		Journal journal;
		try {
			CompoundTag tag = NbtIo.readCompressed(this.path, NbtAccounter.unlimitedHeap());
			journal = Journal.CODEC.parse(ops, tag).getOrThrow();
			var rawOperations = tag.getListOrEmpty("operations");
			for (int operation = 0; operation < journal.operations().size(); operation++) {
				var originals = journal.operations().get(operation).originals();
				var rawOriginals = ((CompoundTag) rawOperations.get(operation)).getListOrEmpty("originals");
				for (int original = 0; original < originals.size(); original++) {
					var rawState = ((CompoundTag) rawOriginals.get(original)).get("state");
					var canonical = BlockState.CODEC.encodeStart(ops, originals.get(original).state()).getOrThrow();
					// Decoding quietly defaults missing or invalid properties and unknown ids, so the state must re-encode as saved.
					if (!canonical.equals(rawState)) {
						throw new IllegalArgumentException("Noncanonical UBW original block state");
					}
				}
			}
		} catch (RuntimeException failure) {
			throw new IOException("Invalid UBW checkpoint: " + this.path, failure);
		}
		if (journal.format() != FORMAT || journal.dataVersion() != SharedConstants.getCurrentVersion().dataVersion().version()) {
			throw new IOException("Incompatible UBW checkpoint: " + this.path);
		}
		return List.copyOf(journal.operations());
	}

	void write(List<Operation> operations) throws IOException {
		var ops = RegistryOps.create(NbtOps.INSTANCE, this.server.registryAccess());
		CompoundTag tag;
		try {
			Journal journal = new Journal(FORMAT, SharedConstants.getCurrentVersion().dataVersion().version(), operations);
			tag = (CompoundTag) Journal.CODEC.encodeStart(ops, journal).getOrThrow();
		} catch (RuntimeException failure) {
			throw new IOException("Cannot encode UBW checkpoint", failure);
		}
		Path directory = this.path.getParent();
		if (Files.notExists(directory)) {
			Files.createDirectory(directory);
			forceDirectory(directory.getParent());
		}
		// CREATE_NEW keeps an earlier interrupted write's file for inspection; without fsync or atomic rename the write fails.
		try (var output = Files.newOutputStream(this.temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
			NbtIo.writeCompressed(tag, output);
		}
		try (var channel = FileChannel.open(this.temporary, StandardOpenOption.WRITE)) {
			channel.force(true);
		}
		Files.move(this.temporary, this.path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		forceDirectory(directory);
	}

	private static void forceDirectory(Path directory) throws IOException {
		try (var channel = FileChannel.open(directory, StandardOpenOption.READ)) {
			channel.force(true);
		}
	}
}
