package io.github.cwzmorro.fatecraft;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * A value kept with the world, in its own file of the world's data. Written with the game's registries, unlike Fabric's
 * server attachments (which can't save an item with enchantments, and then save none of their values at all), so it
 * holds items.
 */
public final class WorldData<T> extends SavedData {
	private T value;

	private WorldData(T value) {
		this.value = value;
	}

	public static <T> SavedDataType<WorldData<T>> type(String name, Codec<T> codec, Supplier<T> empty) {
		// No data fixes for a mod's own data, as Fabric's server attachments.
		return new SavedDataType<>(Fatecraft.id(name), () -> new WorldData<>(empty.get()), codec.xmap(WorldData::new, WorldData::get), null);
	}

	public static <T> WorldData<T> of(MinecraftServer server, SavedDataType<WorldData<T>> type) {
		return server.getDataStorage().computeIfAbsent(type);
	}

	public T get() {
		return this.value;
	}

	public void set(T value) {
		this.value = value;
		this.setDirty();
	}
}
