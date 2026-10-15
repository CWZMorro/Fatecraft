package io.github.cwzmorro.fatecraft.weapon;

import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Kanshou and Bakuya's both-held melee bonus and disposable throw ({@link PairedThrow}). */
public final class PairedSwordItem extends Item {
	private static final TagKey<Item> PAIRED_SWORDS = TagKey.create(Registries.ITEM, Fatecraft.id("paired_swords"));
	public static final int DURABILITY = ToolMaterial.GOLD.durability();
	private static final int MELEE_WEAR = 1;
	private final ReloadableMelee melee = new ReloadableMelee();

	public PairedSwordItem(Properties properties) {
		super(properties.durability(DURABILITY));
	}

	public static boolean paired(Item item) {
		return item.getDefaultInstance().is(PAIRED_SWORDS);
	}

	public static List<Item> pair(Item item) {
		List<Item> swords = new ArrayList<>();
		BuiltInRegistries.ITEM.getTagOrEmpty(PAIRED_SWORDS).forEach(holder -> swords.add(holder.value()));
		int index = swords.indexOf(item);
		int first = index - index % 2;
		return index < 0 || first + 1 >= swords.size() ? List.of() : List.of(swords.get(first), swords.get(first + 1));
	}

	public static boolean bothHeld(LivingEntity owner) {
		Item main = owner.getMainHandItem().getItem();
		Item off = owner.getOffhandItem().getItem();
		return main != off && pair(main).contains(off);
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		boolean paired = owner instanceof LivingEntity living && bothHeld(living)
			&& (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND);
		EmiyaWeaponsConfig config = FateConfig.emiyaWeapons();
		this.melee.apply(stack, paired ? config.pairedDamage() : config.singleDamage(), paired ? config.pairedSpeed() : config.singleSpeed());
	}

	@Override
	public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		stack.hurtAndBreak(MELEE_WEAR, attacker, EquipmentSlot.MAINHAND);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!bothHeld(player)) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer owner) {
			PairedThrow.start(owner);
		}
		return InteractionResult.SUCCESS;
	}
}
