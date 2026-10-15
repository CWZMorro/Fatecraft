package io.github.cwzmorro.fatecraft.vault;

import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.network.VaultActionPayload;
import io.github.cwzmorro.fatecraft.network.VaultDragPayload;
import io.github.cwzmorro.fatecraft.network.VaultSectionPayload;
import io.github.cwzmorro.fatecraft.network.VaultSettingsPayload;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

/** Registers the vault: its storage, the Vault ability, the screen's menu and its payloads. */
public final class Vaults {
	public static final AttachmentType<Vault> STORAGE = AttachmentRegistry.create(Fatecraft.id("vault"), builder -> builder
		.initializer(Vault::new)
		.persistent(Vault.CODEC)
		.copyOnDeath());
	public static final MenuType<VaultMenu> MENU = Registry.register(BuiltInRegistries.MENU, Fatecraft.id("vault"),
		new MenuType<>(VaultMenu::new, FeatureFlags.VANILLA_SET));

	private Vaults() {
	}

	public static void init() {
		Registry.register(AlayaRegistries.ABILITY, VaultAbility.KEY, new VaultAbility());
		PayloadTypeRegistry.clientboundPlay().register(VaultSectionPayload.TYPE, VaultSectionPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(VaultSettingsPayload.TYPE, VaultSettingsPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(VaultSettingsPayload.TYPE, VaultSettingsPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(VaultActionPayload.TYPE, VaultActionPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(VaultDragPayload.TYPE, VaultDragPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(VaultActionPayload.TYPE, (payload, context) -> VaultMenu.act(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(VaultDragPayload.TYPE, (payload, context) -> VaultMenu.drag(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(VaultSettingsPayload.TYPE, (payload, context) -> VaultMenu.setSettings(context.player(), payload));
	}
}
