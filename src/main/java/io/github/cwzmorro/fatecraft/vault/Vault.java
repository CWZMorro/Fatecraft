package io.github.cwzmorro.fatecraft.vault;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Gilgamesh's vault: five sections of its own storage, not the inventory or the ender chest, and his Gate of
 * Babylon settings. Saved on the player and kept through death; it stays with the player when they stop being Gilgamesh,
 * unusable until they are Gilgamesh again (only his Vault ability opens it).
 */
public final class Vault {
	public static final int SECTIONS = 5;
	private static final List<String> SECTION_NAMES = List.of("screen.fatecraft.vault.section.1", "screen.fatecraft.vault.section.2",
		"screen.fatecraft.vault.section.3", "screen.fatecraft.vault.section.4", "screen.fatecraft.vault.section.5");
	public static final Codec<Vault> CODEC = RecordCodecBuilder.create(i -> i.group(
		VaultSection.CODEC.listOf(SECTIONS, SECTIONS).fieldOf("sections").forGetter(vault -> vault.sections),
		VaultSettings.CODEC.optionalFieldOf("settings").xmap(value -> value.orElseGet(VaultSettings::defaults), Optional::of).forGetter(Vault::settings)
	).apply(i, Vault::new));

	private final List<VaultSection> sections;
	private VaultSettings settings;

	public Vault() {
		this(Stream.generate(VaultSection::new).limit(SECTIONS).toList(), VaultSettings.defaults());
	}

	private Vault(List<VaultSection> sections, VaultSettings settings) {
		this.sections = List.copyOf(sections);
		this.settings = settings;
	}

	public static Vault of(Player player) {
		return player.getAttachedOrCreate(Vaults.STORAGE);
	}

	/** Returns {@code index} clamped to a section's index, 0 to {@link #SECTIONS} − 1, since a client may send anything. */
	public static int sectionIndex(int index) {
		return Mth.clamp(index, 0, SECTIONS - 1);
	}

	public static Component sectionName(int index) {
		return Component.translatable(SECTION_NAMES.get(index));
	}

	public VaultSection section(int index) {
		return this.sections.get(index);
	}

	public VaultSettings settings() {
		return this.settings;
	}

	public void setSettings(VaultSettings settings) {
		this.settings = settings.clamped();
	}
}
