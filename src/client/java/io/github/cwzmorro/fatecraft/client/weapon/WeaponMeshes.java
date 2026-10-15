package io.github.cwzmorro.fatecraft.client.weapon;

import io.github.cwzmorro.fatecraft.Fatecraft;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.Nullable;

/** Loads every weapon mesh ({@code models/weapon/*.json}) with the client's resources, off the render thread. */
public final class WeaponMeshes extends SimplePreparableReloadListener<Map<Identifier, WeaponMesh>> {
	public static final Identifier ID = Fatecraft.id("weapon_meshes");
	private static final FileToIdConverter FILES = FileToIdConverter.json("models/weapon");

	private static Map<Identifier, WeaponMesh> meshes = Map.of();

	public static @Nullable WeaponMesh get(Identifier id) {
		return meshes.get(id);
	}

	@Override
	protected Map<Identifier, WeaponMesh> prepare(ResourceManager manager, ProfilerFiller profiler) {
		Map<Identifier, WeaponMesh> loaded = new HashMap<>();
		for (Map.Entry<Identifier, Resource> entry : FILES.listMatchingResources(manager).entrySet()) {
			Identifier id = FILES.fileToId(entry.getKey());
			try (Reader reader = entry.getValue().openAsReader()) {
				loaded.put(id, WeaponMesh.parse(GsonHelper.parse(reader)));
			} catch (IOException | RuntimeException e) {
				Fatecraft.LOGGER.error("Couldn't load weapon mesh {}", entry.getKey(), e);
			}
		}
		return Map.copyOf(loaded);
	}

	@Override
	protected void apply(Map<Identifier, WeaponMesh> loaded, ResourceManager manager, ProfilerFiller profiler) {
		meshes = loaded;
	}
}
