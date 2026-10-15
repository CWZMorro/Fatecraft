package io.github.cwzmorro.fatecraft.trace;

import com.mojang.serialization.Codec;
import io.github.cwzmorro.alayacore.item.WeaponStats;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.weapon.PairedSwordItem;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Projected items: clean base items with a marker, which EMIYA's bow shoots before real swords. */
public final class Projections {
	public static final DataComponentType<Boolean> MARKER = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Fatecraft.id("projection"),
		DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());
	private static final TagKey<Item> FIXED_COST_PROJECTIONS = TagKey.create(Registries.ITEM, Fatecraft.id("fixed_cost_projections"));
	private static final List<Predicate<LivingEntity>> FREE = new ArrayList<>();

	private Projections() {
	}

	public static void init() {
	}

	public static void waiveCostWhen(Predicate<LivingEntity> free) {
		FREE.add(free);
	}

	public static boolean free(LivingEntity entity) {
		for (Predicate<LivingEntity> rule : FREE) {
			if (rule.test(entity)) {
				return true;
			}
		}
		return false;
	}

	/** Returns a fresh projection of {@code item}: its base attack only, never a source stack's components or enchantments. */
	public static ItemStack create(Item item) {
		ItemStack stack = new ItemStack(item);
		stack.remove(DataComponents.ENCHANTMENTS);
		stack.remove(DataComponents.UNBREAKABLE);
		stack.set(DataComponents.MAX_STACK_SIZE, 1);
		stack.set(DataComponents.MAX_DAMAGE, PairedSwordItem.paired(item) ? PairedSwordItem.DURABILITY : FateConfig.trace().uses());
		stack.set(DataComponents.DAMAGE, 0);
		stack.set(MARKER, true);
		return stack;
	}

	public static boolean isProjection(ItemStack stack) {
		return stack.getOrDefault(MARKER, false);
	}

	/**
	 * Returns the mana to project {@code item}: the paired or fixed cost for those swords, otherwise the multiplier times its
	 * total base main-hand attack, the player's own included.
	 */
	public static double cost(Item item) {
		TraceConfig config = FateConfig.trace();
		if (PairedSwordItem.paired(item)) {
			return config.pairedCost();
		}
		ItemStack stack = new ItemStack(item);
		return stack.is(FIXED_COST_PROJECTIONS) ? config.caladbolgCost() : config.attackMultiplier() * WeaponStats.attackDamage(stack);
	}

	public static int emptyHands(LivingEntity entity) {
		return (entity.getMainHandItem().isEmpty() ? 1 : 0) + (entity.getOffhandItem().isEmpty() ? 1 : 0);
	}

	public static void fillPair(LivingEntity entity, Item item) {
		List<Item> pair = PairedSwordItem.pair(item);
		if (pair.isEmpty()) {
			return;
		}
		if (entity.getMainHandItem().isEmpty()) {
			entity.setItemInHand(InteractionHand.MAIN_HAND, create(pair.getFirst()));
		}
		if (entity.getOffhandItem().isEmpty()) {
			entity.setItemInHand(InteractionHand.OFF_HAND, create(pair.getLast()));
		}
	}
}
