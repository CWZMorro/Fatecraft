package io.github.cwzmorro.fatecraft.weapon;

import com.mojang.serialization.Codec;
import io.github.cwzmorro.alayacore.bow.ArcherBows;
import io.github.cwzmorro.alayacore.item.WeaponStats;
import io.github.cwzmorro.alayacore.thrown.ThrownWeaponEntity;
import io.github.cwzmorro.alayacore.util.HeldInFront;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.trace.Projections;
import io.github.cwzmorro.fatecraft.trace.Traces;
import java.util.function.Predicate;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** EMIYA's bow: it shoots swords only, projections first, and uses up even real ones. */
public final class EmiyaBowItem extends BowItem {
	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Fatecraft.id("emiya_bow"));
	private static final TagKey<Item> NOT_TRIPLE_SWORDS = TagKey.create(Registries.ITEM, Fatecraft.id("not_triple_swords"));
	private static final TagKey<Item> FIXED_BLAST_SWORDS = TagKey.create(Registries.ITEM, Fatecraft.id("fixed_blast_swords"));
	public static final DataComponentType<Boolean> BOW_TRIPLE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Fatecraft.id("bow_triple"),
		DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).ignoreSwapAnimation().build());
	public static final DataComponentType<BowDraw> BOW_DRAW = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Fatecraft.id("bow_draw"),
		DataComponentType.<BowDraw>builder().networkSynchronized(BowDraw.STREAM_CODEC).ignoreSwapAnimation().build());
	public static final double NOCK_FORWARD = 0.20;
	private static final float MIN_POWER = 0.1F;

	public EmiyaBowItem(Properties properties) {
		super(properties);
	}

	/** Returns the sword to shoot: projections before real swords, then by inventory slot; a triple shot skips those tagged not for it. */
	public static ItemStack ammo(Player player, boolean triple) {
		for (boolean projection : new boolean[] {true, false}) {
			for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
				ItemStack stack = player.getInventory().getItem(slot);
				if (Traces.sword(stack) && Projections.isProjection(stack) == projection && (!triple || !stack.is(NOT_TRIPLE_SWORDS))) {
					return stack;
				}
			}
		}
		return ItemStack.EMPTY;
	}

	public static boolean triple(ItemStack bow) {
		return bow.getOrDefault(BOW_TRIPLE, false);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack bow = player.getItemInHand(hand);
		if (player.isShiftKeyDown()) {
			if (!level.isClientSide()) {
				bow.set(BOW_TRIPLE, !triple(bow));
				player.sendOverlayMessage(Component.translatable(triple(bow) ? "item.fatecraft.emiya_bow.triple" : "item.fatecraft.emiya_bow.normal"));
			}
			return InteractionResult.SUCCESS;
		}
		ItemStack sword = ammo(player, triple(bow));
		if (sword.isEmpty()) {
			return InteractionResult.FAIL;
		}
		if (!level.isClientSide()) {
			bow.set(BOW_DRAW, new BowDraw(level.getGameTime(), (float) ArcherBows.drawTicks(ArcherBows.bar(player)), sword.copyWithCount(1)));
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public boolean releaseUsing(ItemStack bow, Level level, LivingEntity entity, int remainingTime) {
		bow.remove(BOW_DRAW);
		if (!(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel server)) {
			return false;
		}
		float power = ArcherBows.power(player, this.getUseDuration(bow, player) - remainingTime);
		if (power < MIN_POWER) {
			return false;
		}
		boolean triple = triple(bow);
		BowConfig config = FateConfig.bow();
		int limit = triple ? config.tripleCount() : 1;
		Vec3 direction = player.getLookAngle();
		Vec3 side = HeldInFront.right(direction);
		int fired = 0;
		for (int i = 0; i < limit; i++) {
			ItemStack ammo = ammo(player, triple);
			if (ammo.isEmpty()) {
				break;
			}
			ItemStack shot = ammo.split(1);
			double offset = triple ? (i - (limit - 1) / 2.0) * config.volleySpacing() : 0;
			Vec3 from = player.getEyePosition().add(direction.scale(NOCK_FORWARD)).add(side.scale(offset));
			// The projectile origin is its tail. The client measures the model and places its centre half a length ahead.
			shot.set(FateWeapons.THROWN_FROM_TAIL, true);
			Vec3 velocity = direction.scale(ArcherBows.VANILLA_SPEED * config.swordSpeedMultiplier() * power);
			shoot(server, player, shot, from, velocity, power == 1.0F);
			fired++;
		}
		return fired > 0;
	}

	public static ThrownWeaponEntity shoot(ServerLevel level, ServerPlayer owner, ItemStack sword, Vec3 from, Vec3 velocity, boolean fullCharge) {
		ThrownWeaponEntity shot = ThrownWeaponEntity.launch(level, FateWeapons.THROWN_WEAPON, owner, sword, from, velocity, DAMAGE);
		shot.fixedDamage((float) (Math.ceil(velocity.length() * FateConfig.bow().arrowDamagePerSpeed()) + ArcherBows.attack(owner)));
		shot.blast(fullCharge ? blastPower(sword) : 0.0F);
		return shot;
	}

	public static float blastPower(ItemStack sword) {
		return (float) (sword.is(FIXED_BLAST_SWORDS) ? FateConfig.bow().caladbolgBlast()
			: WeaponStats.attackDamage(sword) / FateConfig.bow().blastDivisor());
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
		if (!(entity instanceof LivingEntity living) || living.getUseItem() != stack) {
			stack.remove(BOW_DRAW);
		}
	}

	@Override
	public Predicate<ItemStack> getAllSupportedProjectiles() {
		return Traces::sword;
	}
}
