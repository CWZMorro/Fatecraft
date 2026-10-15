package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;
import static io.github.cwzmorro.fatecraft.gametest.FateTests.mob;

import io.github.cwzmorro.alayacore.combat.AttackData;
import io.github.cwzmorro.alayacore.combat.AttackDefence;
import io.github.cwzmorro.alayacore.combat.AttackSource;
import io.github.cwzmorro.alayacore.combat.Attacks;
import io.github.cwzmorro.alayacore.combat.ServantBonus;
import io.github.cwzmorro.alayacore.combat.WeaponHits;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.chain.Chains;
import io.github.cwzmorro.fatecraft.weapon.CompressedAir;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;

public class DamageRulesGameTests {
	private static final BlockPos WIELDER = new BlockPos(7, 2, 7);
	private static final int NEAR = 3;
	private static final double TARGET_HEALTH = 1000.0;
	private static final int PROTECTION_LEVEL = 4;
	private static final float TIGHTEN = 30.0F;
	private static final int CHUNKS_LOADED_TICKS = 5;
	private static final double EPS = 1e-2;
	private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
	private static final Item[] ARMOR = {Items.NETHERITE_BOOTS, Items.NETHERITE_LEGGINGS, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_HELMET};

	private static Husk armored(GameTestHelper helper, BlockPos pos) {
		Husk target = mob(helper, pos);
		target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(TARGET_HEALTH);
		target.setHealth((float) TARGET_HEALTH);
		var protection = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
		for (int i = 0; i < ARMOR.length; i++) {
			ItemStack stack = new ItemStack(ARMOR[i]);
			stack.enchant(protection, PROTECTION_LEVEL);
			target.setItemSlot(ARMOR_SLOTS[i], stack);
			// The armor attributes would only apply on the husk's next tick.
			stack.get(DataComponents.ATTRIBUTE_MODIFIERS).forEach(ARMOR_SLOTS[i],
				(attribute, modifier) -> target.getAttribute(attribute).addTransientModifier(modifier));
		}
		return target;
	}

	private static DamageSource source(GameTestHelper helper, ResourceKey<DamageType> type, LivingEntity attacker) {
		return new DamageSource(Attacks.damageType(helper.getLevel(), type), attacker);
	}

	// Enkidu's tightening hit, worked out by hand: 90% of what armor takes off ignored, Protection capped at 20 then halved.
	@GameTest
	public void enkidusTighteningIgnoresMostDefence(GameTestHelper helper) {
		ServerPlayer gilgamesh = FateTests.gilgamesh(helper, WIELDER, FateTests.MANA);
		Husk target = armored(helper, WIELDER.east(NEAR));
		DamageSource source = source(helper, Chains.DAMAGE, gilgamesh);
		float afterArmor = CombatRules.getDamageAfterAbsorb(target, TIGHTEN, source, target.getArmorValue(), (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
		float reduced = TIGHTEN - 0.1F * (TIGHTEN - afterArmor);
		float protection = Math.min(20.0F, EnchantmentHelper.getDamageProtection(helper.getLevel(), target, source));
		double expected = CombatRules.getDamageAfterMagicAbsorb(reduced, protection * 0.5F);
		float before = target.getHealth();
		target.hurtServer(helper.getLevel(), source, TIGHTEN);
		check(helper, "tightening damage " + (before - target.getHealth()) + " vs " + expected, Math.abs(expected - (before - target.getHealth())) < EPS);
		check(helper, "it wears no armor", !AttackDefence.wearsArmor(source));
		helper.succeed();
	}

	@GameTest
	public void onlyEnkiduIsInTheIgnoresDefenceTagAndNothingBypassesByName(GameTestHelper helper) {
		check(helper, "Enkidu ignores defence", Attacks.damageType(helper.getLevel(), Chains.DAMAGE).is(AttackDefence.IGNORES_DEFENCE));
		for (String name : new String[]{"enuma_elish", "excalibur", "photon_ray", "compressed_air", "gate_of_babylon", "artoria_skill"}) {
			check(helper, name + " does not ignore defence by name", !Attacks.damageType(helper.getLevel(), ResourceKey.create(Registries.DAMAGE_TYPE,
				Fatecraft.id(name))).is(AttackDefence.IGNORES_DEFENCE));
		}
		for (String name : new String[]{"enuma_elish", "excalibur", "enkidu", "photon_ray"}) {
			Holder<DamageType> type = Attacks.damageType(helper.getLevel(), ResourceKey.create(Registries.DAMAGE_TYPE, Fatecraft.id(name)));
			check(helper, name + " is not in the vanilla bypass tags", !type.is(DamageTypeTags.BYPASSES_ARMOR) && !type.is(DamageTypeTags.BYPASSES_ENCHANTMENTS));
		}
		helper.succeed();
	}

	@GameTest
	public void compressedAirIsAnExSkillThatAddsTheWieldersClassAttack(GameTestHelper helper) {
		ServerPlayer player = FateTests.gilgamesh(helper, WIELDER, FateTests.MANA);
		player.setGameMode(GameType.SURVIVAL);
		ItemStack ea = new ItemStack(FateWeapons.EA);
		player.setItemInHand(InteractionHand.MAIN_HAND, ea);
		Mob plain = mob(helper, WIELDER.west(NEAR));
		plain.getAttribute(Attributes.MAX_HEALTH).setBaseValue(TARGET_HEALTH);
		plain.setHealth((float) TARGET_HEALTH);
		plain.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		Husk target = armored(helper, WIELDER.east(NEAR));
		Rank rank = Rank.parse("EX");
		double perSecond = AlayaConfig.ranks().skillBaseDamage(rank) + ServantBonus.classAttack(player);
		float hit = WeaponHits.damage(perSecond);
		DamageSource source = new AttackSource(Attacks.damageType(helper.getLevel(), CompressedAir.DAMAGE), player, player,
			new AttackData(player.getUUID(), rank, false, false));
		// An EX skill meets armor as an A noble phantasm: half the armor, three quarters of the Protection.
		float afterArmor = CombatRules.getDamageAfterAbsorb(target, hit, source, target.getArmorValue() * 0.5F,
			(float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
		double expected = CombatRules.getDamageAfterMagicAbsorb(afterArmor, EnchantmentHelper.getDamageProtection(helper.getLevel(), target, source) * 0.25F);

		// Entities only count as around the wielder once their chunks have loaded, which the forceload of a mock player takes a tick to do.
		helper.runAfterDelay(CHUNKS_LOADED_TICKS, () -> {
			player.startUsingItem(InteractionHand.MAIN_HAND);
			ea.getItem().onUseTick(helper.getLevel(), player, ea, ea.getUseDuration(player));
			check(helper, "an unarmored target takes the whole hit: " + (TARGET_HEALTH - plain.getHealth()) + " vs " + hit,
				Math.abs(TARGET_HEALTH - plain.getHealth() - hit) < EPS);
			check(helper, "an armored target takes it as an A noble phantasm: " + (TARGET_HEALTH - target.getHealth()) + " vs " + expected,
				Math.abs(TARGET_HEALTH - target.getHealth() - expected) < EPS);
			helper.succeed();
		});
	}

	@GameTest
	public void aCompressedAirHitCarriesItsSkillRankForShields(GameTestHelper helper) {
		ServerPlayer player = FateTests.gilgamesh(helper, WIELDER, FateTests.MANA);
		AttackData data = new AttackData(player.getUUID(), Rank.parse("EX"), false, false);
		DamageSource source = new AttackSource(Attacks.damageType(helper.getLevel(), CompressedAir.DAMAGE), player, player, data);
		check(helper, "shields and clashes read the rank from the source", data.equals(Attacks.of(source)));
		check(helper, "a plain source carries none", Attacks.of(source(helper, CompressedAir.DAMAGE, player)) == null);
		helper.succeed();
	}
}
