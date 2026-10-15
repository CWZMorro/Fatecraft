package io.github.cwzmorro.fatecraft.weapon;

import io.github.cwzmorro.alayacore.combat.Aim;
import io.github.cwzmorro.alayacore.thrown.ThrownWeaponEntity;
import io.github.cwzmorro.alayacore.util.HeldInFront;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Kanshou and Bakuya's throw: a server-clock timeline, shared with every tracking client, including the thrower. */
public final class PairedThrow {
	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Fatecraft.id("paired_throw"));
	// Ticks after the press at which the main-hand and off-hand swords leave, and the whole throw's length (the pose included).
	public static final int MAIN_RELEASE = 5;
	public static final int OFF_RELEASE = 7;
	public static final int DURATION = 20;
	public static final AttachmentType<Long> START = AttachmentRegistry.create(Fatecraft.id("paired_throw_start"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));
	public static final DataComponentType<Long> PAIRED_THROW_START = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Fatecraft.id("paired_throw_start"),
		DataComponentType.<Long>builder().networkSynchronized(ByteBufCodecs.VAR_LONG).ignoreSwapAnimation().build());
	private static final double HAND_OFFSET = 0.2;
	private record Pending(ItemStack main, ItemStack off) { }
	private static final AttachmentType<Pending> PENDING = AttachmentRegistry.create(Fatecraft.id("paired_throw_pending"));

	private PairedThrow() { }

	/** Registers the throw on both sides before Fabric negotiates synced attachments, and its releases on the server tick. */
	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> server.getPlayerList().getPlayers().forEach(PairedThrow::tick));
	}

	public static @Nullable Long startTick(LivingEntity player) {
		Long start = player.getMainHandItem().get(PAIRED_THROW_START);
		if (start == null) {
			start = player.getOffhandItem().get(PAIRED_THROW_START);
		}
		return start == null ? player.getAttached(START) : start;
	}

	public static void start(ServerPlayer player) {
		Long start = player.getAttached(START);
		long now = player.level().getGameTime();
		if (start != null && now - start < DURATION) {
			return;
		}
		player.setAttached(PENDING, new Pending(player.getMainHandItem(), player.getOffhandItem()));
		player.getMainHandItem().set(PAIRED_THROW_START, now);
		player.getOffhandItem().set(PAIRED_THROW_START, now);
		player.setAttached(START, now);
	}

	/** Releases each sword once its time has come; safe to call more than once a tick. Swapping a sword away cancels its release. */
	public static void tick(ServerPlayer player) {
		Pending pending = player.getAttached(PENDING);
		Long start = player.getAttached(START);
		if (pending == null || start == null) {
			return;
		}
		if (!player.isAlive()) {
			pending.main().remove(PAIRED_THROW_START);
			pending.off().remove(PAIRED_THROW_START);
			player.removeAttached(PENDING);
			return;
		}
		long age = player.level().getGameTime() - start;
		if (age >= MAIN_RELEASE) {
			release(player, InteractionHand.MAIN_HAND, pending.main());
		}
		if (age >= OFF_RELEASE) {
			release(player, InteractionHand.OFF_HAND, pending.off());
			player.removeAttached(PENDING);
		}
	}

	private static void release(ServerPlayer player, InteractionHand hand, ItemStack expected) {
		expected.remove(PAIRED_THROW_START);
		if (expected.isEmpty() || player.getItemInHand(hand) != expected) {
			return;
		}
		EmiyaWeaponsConfig config = FateConfig.emiyaWeapons();
		new ReloadableMelee().apply(expected, config.pairedDamage(), config.pairedSpeed());
		Vec3 forward = player.getLookAngle();
		int side = (player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1) * (hand == InteractionHand.MAIN_HAND ? 1 : -1);
		Vec3 from = player.getEyePosition().add(HeldInFront.right(forward).scale(HAND_OFFSET * player.getScale() * side));
		launch(player.level(), player, expected, from, forward.scale(config.pairedThrowSpeed()), side, Aim.target(player, config.pairedTargetRange()));
		player.setItemInHand(hand, ItemStack.EMPTY);
	}

	private static void launch(ServerLevel level, ServerPlayer owner, ItemStack sword, Vec3 from, Vec3 velocity, int side, @Nullable LivingEntity target) {
		if (!PairedSwordItem.paired(sword.getItem())) {
			throw new IllegalArgumentException("Only paired swords may be hand-thrown");
		}
		PairedFlight flight = target == null ? null : PairedFlight.toward(from, target, side, velocity.length());
		ThrownWeaponEntity thrown = ThrownWeaponEntity.launch(level, FateWeapons.THROWN_WEAPON, owner, sword, from,
			flight == null ? velocity : flight.leaving().scale(velocity.length()), DAMAGE);
		thrown.setFlight(flight);
		thrown.spin(side);
	}
}
