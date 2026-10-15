package io.github.cwzmorro.fatecraft.client.vault;

import io.github.cwzmorro.fatecraft.network.VaultSectionPayload;
import io.github.cwzmorro.fatecraft.network.VaultSettingsPayload;
import io.github.cwzmorro.fatecraft.vault.Vault;
import io.github.cwzmorro.fatecraft.vault.VaultConfig;
import io.github.cwzmorro.fatecraft.vault.VaultEntry;
import io.github.cwzmorro.fatecraft.vault.VaultSettings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** The vault as the server last sent it, for the vault screen; the player's changes of settings go back from here. */
public final class ClientVault {
	private static final List<List<VaultEntry>> sections = new ArrayList<>(Collections.nCopies(Vault.SECTIONS, List.of()));
	private static VaultSettings settings = VaultSettings.DEFAULTS;
	private static int stackMultiplier = VaultConfig.DEFAULTS.stackMultiplier();
	private static int version;

	private ClientVault() {
	}

	public static void register() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			Collections.fill(sections, List.of());
			settings = VaultSettings.DEFAULTS;
			stackMultiplier = VaultConfig.DEFAULTS.stackMultiplier();
			version++;
		});
		ClientPlayNetworking.registerGlobalReceiver(VaultSectionPayload.TYPE, (payload, context) -> {
			if (payload.section() >= 0 && payload.section() < Vault.SECTIONS) {
				sections.set(payload.section(), List.copyOf(payload.entries()));
				stackMultiplier = payload.stackMultiplier();
				version++;
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(VaultSettingsPayload.TYPE, (payload, context) -> {
			settings = payload.settings();
			version++;
		});
	}

	public static List<VaultEntry> section(int index) {
		return sections.get(index);
	}

	public static int limit(VaultEntry entry) {
		return VaultEntry.limit(entry.item(), stackMultiplier);
	}

	public static VaultSettings settings() {
		return settings;
	}

	public static void changeSettings(VaultSettings changed) {
		settings = changed.clamped();
		version++;
		ClientPlayNetworking.send(new VaultSettingsPayload(settings));
	}

	public static int version() {
		return version;
	}
}
