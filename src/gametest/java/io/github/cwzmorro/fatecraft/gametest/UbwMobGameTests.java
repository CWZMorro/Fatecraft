package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;
import static io.github.cwzmorro.fatecraft.gametest.UbwFeatureGameTests.shots;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.thrown.ThrownWeaponEntity;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.trace.Project;
import io.github.cwzmorro.fatecraft.trace.Projections;
import io.github.cwzmorro.fatecraft.ubw.Ubw;
import io.github.cwzmorro.fatecraft.ubw.UbwController;
import io.github.cwzmorro.fatecraft.ubw.UbwEnvironmentEntity;
import io.github.cwzmorro.fatecraft.ubw.UbwRuntime;
import io.github.cwzmorro.fatecraft.ubw.UbwSettings;
import io.github.cwzmorro.fatecraft.ubw.UbwSwords;
import io.github.cwzmorro.fatecraft.ubw.UnlimitedBladeWorks;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import java.io.IOException;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class UbwMobGameTests {
	private static final Identifier UBW = UnlimitedBladeWorks.KEY.identifier();
	private static final int FIRED_TICKS = 30;
	private static final int CLOSED_TICKS = 2;
	private static final int TARGET_BLOCKS = 4;
	// Inside the structure's volume: its barrier shell (the ceiling at START's height, the wall south of its end) stops the sword.
	private static final BlockPos SWORD_MOB = new BlockPos(4, 4, 1);

	private static Husk emiya(GameTestHelper helper) {
		Husk mob = FateTests.mobServant(helper, UbwFeatureGameTests.START, 50_000, FateServants.EMIYA);
		UbwFeatureGameTests.field(helper, mob);
		return mob;
	}

	private static void tap(LivingEntity caster, int mode) {
		Abilities.press(caster, UBW, mode, false);
		Abilities.release(caster, UBW);
	}

	private static BlockPos floor(Husk mob) {
		BlockPos center = UbwController.placement(mob, FateConfig.ubw());
		return new BlockPos(center.getX(), Mth.floor(mob.getY()) - 1, center.getZ());
	}

	private static UbwController.Status open(GameTestHelper helper, Husk mob) throws IOException {
		double before = AlayaMana.available(mob);
		tap(mob, UnlimitedBladeWorks.MARBLE);
		check(helper, "the mob opens its marble and pays for it, with no true name asked of a mob",
			UbwRuntime.hasMarble(mob) && AlayaMana.available(mob) == before - FateConfig.ubw().openingMana());
		UbwRuntime.finishOpening(mob);
		return UbwController.of(helper.getLevel().getServer()).status(mob.getUUID()).orElseThrow();
	}

	private static void closed(GameTestHelper helper, Husk mob, UbwController.Status status, BlockPos floor, BlockState original) {
		check(helper, "the marble is gone, its terrain restored and unprotected", !UbwRuntime.hasMarble(mob)
			&& !UbwRuntime.protects(helper.getLevel(), status.center()) && helper.getLevel().getBlockState(floor).equals(original)
			&& helper.getLevel().getEntitiesOfClass(UbwEnvironmentEntity.class, UbwFeatureGameTests.bounds(status)).isEmpty()
			&& UbwFeatureGameTests.ground(helper, status).isEmpty());
	}

	@GameTest(environment = "fatecraft-gametest:isolated/mob_firing", setupTicks = 2, padding = 64, maxTicks = 100)
	public void aMobOpensFiresAndClosesItsMarble(GameTestHelper helper) throws IOException {
		Husk mob = emiya(helper);
		BlockPos floor = floor(mob);
		BlockState original = helper.getLevel().getBlockState(floor);
		UbwController.Status status = open(helper, mob);
		check(helper, "its ground and ground swords stand", helper.getLevel().getBlockState(floor).is(Ubw.GROUND)
			&& !UbwFeatureGameTests.ground(helper, status).isEmpty());
		check(helper, "projection is free inside, Trace On too",
			new Project(FateWeapons.CALADBOLG).startCost(mob, Project.TRACE_ON) == 0);
		Abilities.press(mob, UBW, UnlimitedBladeWorks.AOE_THROW, false);
		check(helper, "its stored tier's volley forms, held", shots(helper, mob, status).size() == UbwSettings.volley(mob)
			&& shots(helper, mob, status).stream().allMatch(ThrownWeaponEntity::heldProjection));
		helper.runAfterDelay(FIRED_TICKS, () -> {
			check(helper, "held, the volley really launches", shots(helper, mob, status).stream().anyMatch(shot -> !shot.heldProjection()));
			Abilities.release(mob, UBW);
			tap(mob, UnlimitedBladeWorks.MARBLE);
			closed(helper, mob, status, floor, original);
			mob.discard();
			helper.succeed();
		});
	}

	@GameTest(environment = "fatecraft-gametest:isolated/mob_tier", setupTicks = 2, padding = 64, maxTicks = 100)
	public void aMobNeverCyclesItsVolleyTier(GameTestHelper helper) throws IOException {
		Husk mob = emiya(helper);
		BlockPos floor = floor(mob);
		BlockState original = helper.getLevel().getBlockState(floor);
		UbwController.Status status = open(helper, mob);
		int tier = UbwSettings.tier(mob);
		Abilities.press(mob, UBW, UnlimitedBladeWorks.AOE_THROW, true);
		check(helper, "a sneaking mob's press neither changes its tier nor skips its volley", UbwSettings.tier(mob) == tier
			&& UnlimitedBladeWorks.firingMode(mob) == UnlimitedBladeWorks.AOE_THROW && shots(helper, mob, status).size() == UbwSettings.volley(mob));
		Abilities.release(mob, UBW);
		Abilities.end(mob, UBW);
		tap(mob, UnlimitedBladeWorks.MARBLE);
		closed(helper, mob, status, floor, original);
		mob.discard();
		helper.succeed();
	}

	@GameTest(environment = "fatecraft-gametest:isolated/mob_death", setupTicks = 2, padding = 64, maxTicks = 100)
	public void aMobsDeathClosesAndRestoresItsMarble(GameTestHelper helper) throws IOException {
		Husk mob = emiya(helper);
		BlockPos floor = floor(mob);
		BlockState original = helper.getLevel().getBlockState(floor);
		UbwController.Status status = open(helper, mob);
		mob.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
		helper.runAfterDelay(CLOSED_TICKS, () -> {
			closed(helper, mob, status, floor, original);
			helper.succeed();
		});
	}

	@GameTest(environment = "fatecraft-gametest:isolated/mob_removed", setupTicks = 2, padding = 64, maxTicks = 100)
	public void aMobsRemovalClosesAndRestoresItsMarble(GameTestHelper helper) throws IOException {
		Husk mob = emiya(helper);
		BlockPos floor = floor(mob);
		BlockState original = helper.getLevel().getBlockState(floor);
		UbwController.Status status = open(helper, mob);
		mob.discard();
		helper.runAfterDelay(CLOSED_TICKS, () -> {
			closed(helper, mob, status, floor, original);
			helper.succeed();
		});
	}

	@GameTest(environment = "fatecraft-gametest:isolated/mob_attacker", setupTicks = 2, padding = 64, maxTicks = 100)
	public void aMobsSwordNamesTheMobAsAttacker(GameTestHelper helper) {
		Husk mob = FateTests.mobServant(helper, SWORD_MOB, 0, FateServants.EMIYA);
		Husk target = FateTests.mob(helper, SWORD_MOB.south(TARGET_BLOCKS));
		Vec3 from = mob.getEyePosition();
		Vec3 velocity = target.getBoundingBox().getCenter().subtract(from).normalize().scale(AlayaConfig.thrownWeapons().blocksPerTick());
		UbwSwords.project(helper.getLevel(), mob, Projections.create(FateWeapons.CALADBOLG), from, velocity, 0, false, 1.0F);
		helper.succeedWhen(() -> {
			check(helper, "the target was hit by the mob", target.getLastHurtByMob() == mob);
			mob.discard();
			target.discard();
		});
	}
}
