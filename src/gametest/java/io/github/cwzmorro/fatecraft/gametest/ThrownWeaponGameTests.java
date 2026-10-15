package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;

import io.github.cwzmorro.alayacore.thrown.ThrownWeaponEntity;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// A test-only launcher gives a thrown weapon its own damage and interceptor: a thrown weapon knows no feature.
public class ThrownWeaponGameTests {
	private static final BlockPos PLAYER = new BlockPos(3, 3, 2);
	private static final BlockPos TARGET = new BlockPos(3, 4, 5);
	private static final Vec3 START = new Vec3(3.5, 4.5, 2.5);
	private static final Vec3 FORWARD = new Vec3(0, 0, 3);
	private static final float DAMAGE = 3.0F;
	private static final int CLEARED_SKY = 40;
	private static final double LIGHTNING_NEAR = 3.0;
	private static final Item INTERCEPTED = Items.BLAZE_ROD;

	static {
		ThrownWeaponEntity.INTERCEPT.register((level, weapon, from, to, block, firstImpact) -> {
			if (!weapon.item().is(INTERCEPTED)) {
				return false;
			}
			weapon.stop(level);
			return true;
		});
	}

	@GameTest
	public void strikesAsItsLauncherSays(GameTestHelper helper) {
		ServerPlayer player = FateTests.player(helper, PLAYER);
		Husk target = FateTests.mob(helper, TARGET);
		ThrownWeaponEntity weapon = ThrownWeaponEntity.launch(helper.getLevel(), FateWeapons.THROWN_WEAPON, player, new ItemStack(Items.IRON_SWORD),
			helper.absoluteVec(START), FORWARD, DamageTypes.TRIDENT);
		weapon.fixedDamage(DAMAGE);
		weapon.tick();
		DamageSource source = target.getLastDamageSource();
		check(helper, "the target is struck", target.getHealth() < target.getMaxHealth());
		check(helper, "with the launcher's own damage type, its thrower and itself named",
			source != null && source.is(DamageTypes.TRIDENT) && source.getEntity() == player && source.getDirectEntity() == weapon);
		check(helper, "used up where it strikes", weapon.isRemoved());
		helper.succeed();
	}

	@GameTest
	public void channelingStrikesLightningOnlyInAThunderstormUnderTheSky(GameTestHelper helper) {
		ServerPlayer player = FateTests.player(helper, PLAYER);
		ServerLevel level = helper.getLevel();
		Husk target = FateTests.mob(helper, TARGET);
		for (BlockPos block : BlockPos.betweenClosed(TARGET.above(), TARGET.above(CLEARED_SKY))) {
			helper.setBlock(block, Blocks.AIR);
		}
		ItemStack trident = new ItemStack(Items.TRIDENT);
		trident.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.CHANNELING), 1);
		ThrownWeaponEntity.launch(level, FateWeapons.THROWN_WEAPON, player, trident, helper.absoluteVec(START), FORWARD, DamageTypes.TRIDENT).tick();
		check(helper, "a clear sky: no lightning", level.getEntitiesOfClass(LightningBolt.class, target.getBoundingBox().inflate(LIGHTNING_NEAR)).isEmpty());
		target.setHealth(target.getMaxHealth());
		target.invulnerableTime = 0;
		// The storm at full strength at once, as the weather would only reach it over time.
		level.setRainLevel(1.0F);
		level.setThunderLevel(1.0F);
		ThrownWeaponEntity.launch(level, FateWeapons.THROWN_WEAPON, player, trident, helper.absoluteVec(START), FORWARD, DamageTypes.TRIDENT).tick();
		check(helper, "a thunderstorm: lightning at the target",
			!level.getEntitiesOfClass(LightningBolt.class, target.getBoundingBox().inflate(LIGHTNING_NEAR)).isEmpty());
		level.setRainLevel(0.0F);
		level.setThunderLevel(0.0F);
		helper.succeed();
	}

	@GameTest
	public void anInterceptorTakesOverOnlyItsOwn(GameTestHelper helper) {
		ServerPlayer player = FateTests.player(helper, PLAYER);
		Husk target = FateTests.mob(helper, TARGET);
		ThrownWeaponEntity taken = ThrownWeaponEntity.launch(helper.getLevel(), FateWeapons.THROWN_WEAPON, player, new ItemStack(INTERCEPTED),
			helper.absoluteVec(START), FORWARD, DamageTypes.TRIDENT);
		taken.tick();
		check(helper, "the intercepted weapon strikes nothing", target.getHealth() == target.getMaxHealth());
		check(helper, "and its interceptor stopped it", taken.isRemoved());
		ThrownWeaponEntity other = ThrownWeaponEntity.launch(helper.getLevel(), FateWeapons.THROWN_WEAPON, player, new ItemStack(Items.IRON_SWORD),
			helper.absoluteVec(START), FORWARD, DamageTypes.TRIDENT);
		other.tick();
		check(helper, "any other weapon flies on and strikes", target.getHealth() < target.getMaxHealth() && other.isRemoved());
		helper.succeed();
	}
}
