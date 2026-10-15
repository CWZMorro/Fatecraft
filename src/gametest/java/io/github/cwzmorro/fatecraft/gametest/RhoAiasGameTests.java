package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.ability.Ability;
import io.github.cwzmorro.alayacore.ability.AbilitySetting;
import io.github.cwzmorro.alayacore.ability.AbilitySettings;
import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.api.AlayaServants;
import io.github.cwzmorro.alayacore.combat.AttackData;
import io.github.cwzmorro.alayacore.combat.Attacks;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.divinity.Divinity;
import io.github.cwzmorro.alayacore.progression.ClassGrade;
import io.github.cwzmorro.alayacore.progression.DeedKinds;
import io.github.cwzmorro.alayacore.progression.TrueName;
import io.github.cwzmorro.alayacore.progression.TrueNameDeeds;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.alayacore.servant.ServantChoice;
import io.github.cwzmorro.alayacore.servant.ServantClasses;
import io.github.cwzmorro.alayacore.servant.ServantStorage;
import io.github.cwzmorro.alayacore.util.HeldInFront;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.shield.FateShields;
import io.github.cwzmorro.fatecraft.shield.RhoAias;
import io.github.cwzmorro.fatecraft.shield.RhoAiasEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

public class RhoAiasGameTests {
	private static final BlockPos STANDING = new BlockPos(4, 1, 3);
	private static final double CLOSE = 0.001;
	private static final float HIT = 20.0F;
	private static final int COLLISION_TICKS = 60;
	private static final double NAVIGATION_SPEED = 1.0;
	private static final int WALK_LANE_RADIUS = 3;
	private static final double WALK_STEP = 0.2;
	private static final double APPROACH_DISTANCE = 2.0;
	private static final double ARROW_SPEED = 1.0;
	private static final float LOOKING_DOWN = 20.0F;
	private static final int ARENA_RADIUS = 5;
	private static final double SPRINT_STEP = 0.28;
	private static final double GRAVITY = 0.08;
	private static final double JUMP = 0.42;
	private static final double CLIMB_HEIGHT = 1.5;
	private static final int CALM = -1;
	private static final double OVERLAP = 0.1;
	private static final int PUSH_TICKS = 5;
	private static final double CHOSEN_DISTANCE = 7.0;
	private static final double CHOSEN_SIZE = 6.0;
	private static final int HELD_TICKS = 10;
	private static final int AFTER_RELEASE = 3;
	private static final int TAP_TEST_TICKS = 6002;
	private static final ChunkPos UNLOADED_CHUNK = new ChunkPos(100_000, 100_000);

	private static ServerPlayer emiya(GameTestHelper helper) {
		return FateTests.servant(helper, STANDING, FateTests.MANA, FateServants.EMIYA);
	}

	private static Ability ability() {
		return AlayaRegistries.ABILITY.getValueOrThrow(RhoAias.KEY);
	}

	private static void press(LivingEntity caster) {
		Abilities.press(caster, RhoAias.KEY.identifier(), 0, false);
	}

	private static void release(LivingEntity caster) {
		Abilities.release(caster, RhoAias.KEY.identifier());
	}

	private static void maxMana(LivingEntity caster, double mana) {
		AlayaMana.getOrCreate(caster);
		AlayaMana.update(caster, old -> old.withMax(mana).withCurrent(mana));
	}

	private static boolean kills(TrueNameDeeds.Deed deed, String type) {
		return deed.condition().kind() == DeedKinds.KILL && deed.condition().value().toString().equals(type);
	}

	private static void stage(GameTestHelper helper, ServerPlayer caster, double sync, boolean trueName, String expected, int petals, double hp,
		double cost) {
		maxMana(caster, AlayaConfig.progression().manaForSync(sync));
		if (trueName) {
			TrueName.realize(caster);
		}
		check(helper, "rank " + expected, RhoAias.rank(caster).equals(Rank.parse(expected)));
		check(helper, "start cost " + expected, Math.abs(ability().startCost(caster, 0) - cost) < CLOSE);
		// A's cost exceeds the initial pool: raise it just enough after checking A at zero sync.
		if (AlayaMana.get(caster).max() < cost) {
			maxMana(caster, Math.nextUp(cost));
		}
		AlayaMana.update(caster, mana -> mana.withCurrent(mana.max()));
		double before = AlayaMana.get(caster).current();
		press(caster);
		RhoAiasEntity shield = RhoAiasEntity.of(caster);
		check(helper, "deployed " + expected, shield != null && shield.rank().equals(Rank.parse(expected)));
		check(helper, "petals " + expected, shield.petals() == petals);
		check(helper, "cost paid " + expected, Math.abs(before - AlayaMana.get(caster).current() - cost) < CLOSE);
		check(helper, "full HP on press " + expected, shield.hp() == shield.maxHp());
		check(helper, "HP from rank " + expected, Math.abs(shield.hp() - AlayaConfig.ranks().shieldHp(shield.rank())) < CLOSE);
		check(helper, "designed HP " + expected, Math.abs(shield.hp() - hp) < CLOSE);
		release(caster);
		shield.discard();
	}

	@GameTest
	public void rankAndPetalsFollowSyncAndTrueNameAtOneCastCost(GameTestHelper helper) {
		ServerPlayer caster = emiya(helper);
		double cost = FateConfig.rhoAias().castCost();
		check(helper, "default cast cost", cost == 700.0);
		stage(helper, caster, 0.0, false, "B", 4, 306.0, cost);
		stage(helper, caster, AlayaConfig.progression().syncFor(ClassGrade.BRONZE), false, "B+", 4, 366.0, cost);
		stage(helper, caster, AlayaConfig.progression().syncFor(ClassGrade.SILVER), false, "B++", 4, 438.0, cost);
		stage(helper, caster, 0.0, true, "A", 7, 525.0, cost);
		helper.succeed();
	}

	@GameTest
	public void aMobServantCanCastAtItsSync(GameTestHelper helper) {
		Husk caster = FateTests.mob(helper, STANDING);
		maxMana(caster, FateTests.MANA);
		caster.setAttached(ServantStorage.CHOICE, ServantChoice.servant(Fatecraft.id("emiya")));
		press(caster);
		check(helper, "mob servant at full sync", RhoAiasEntity.of(caster).rank().equals(Rank.parse("B++")));
		release(caster);
		helper.succeed();
	}

	@GameTest
	public void notEnoughManaRefusesThePressWithoutDeploying(GameTestHelper helper) {
		ServerPlayer caster = emiya(helper);
		AlayaMana.update(caster, mana -> mana.withCurrent(ability().startCost(caster, 0) - 1));
		double before = AlayaMana.get(caster).current();
		press(caster);
		check(helper, "refused", !Abilities.isActive(caster, RhoAias.KEY.identifier()) && RhoAiasEntity.of(caster) == null);
		check(helper, "no cost paid", AlayaMana.get(caster).current() == before);
		check(helper, "no cooldown", Abilities.cooldownSecondsLeft(caster, RhoAias.KEY.identifier(), 0) == 0);
		helper.succeed();
	}

	@GameTest
	public void pressingAgainDismissesWithoutCostAndAllowsTheNext(GameTestHelper helper) {
		ServerPlayer caster = emiya(helper);
		press(caster);
		RhoAiasEntity shield = RhoAiasEntity.of(caster);
		release(caster);
		double before = AlayaMana.get(caster).current();
		AlayaMana.update(caster, mana -> mana.withCurrent(0));
		press(caster);
		check(helper, "dismissed without mana", shield.isRemoved() && RhoAiasEntity.of(caster) == null && !Abilities.isActive(caster, RhoAias.KEY.identifier()));
		check(helper, "no dismissal cost", AlayaMana.get(caster).current() == 0);
		AlayaMana.update(caster, mana -> mana.withCurrent(before));
		press(caster);
		check(helper, "next projection", RhoAiasEntity.of(caster) != null && RhoAiasEntity.of(caster) != shield);
		release(caster);
		helper.succeed();
	}

	@GameTest
	public void pressingAgainWhileHeldDismisses(GameTestHelper helper) {
		ServerPlayer caster = emiya(helper);
		press(caster);
		RhoAiasEntity shield = RhoAiasEntity.of(caster);
		for (int tick = 0; tick < AlayaConfig.abilities().holdThresholdTicks(); tick++) {
			Abilities.tick(caster);
		}
		double before = AlayaMana.get(caster).current();
		press(caster);
		check(helper, "held shield dismissed", shield.isRemoved() && !Abilities.isActive(caster, RhoAias.KEY.identifier()));
		check(helper, "no second cast cost", AlayaMana.get(caster).current() == before);
		helper.succeed();
	}

	// Runs alone: the largest shield, 16 blocks out, reaches into the structures of the tests beside it.
	@GameTest(environment = "fatecraft-gametest:isolated/rho_aias_full_size")
	public void fullSizeAndPlacementRangesAreAvailable(GameTestHelper helper) {
		ServerPlayer caster = emiya(helper);
		AbilitySetting distance = ability().settings().get(0);
		AbilitySetting size = ability().settings().get(1);
		check(helper, "distance range", distance.min() == 2.0 && distance.max() == 16.0);
		check(helper, "size range", size.min() == 5.0 && size.max() == 15.0);
		check(helper, "default settings unchanged", FateConfig.rhoAias().defaultDistance() == 2.5 && FateConfig.rhoAias().defaultSize() == 5.5);
		AbilitySettings.set(caster, RhoAias.KEY.identifier(), distance.id(), distance.max());
		AbilitySettings.set(caster, RhoAias.KEY.identifier(), size.id(), size.max());
		Vec3 spot = HeldInFront.at(caster.getEyePosition(), caster.getLookAngle(), distance.max());
		helper.getLevel().getChunkAt(BlockPos.containing(spot));
		press(caster);
		RhoAiasEntity shield = RhoAiasEntity.of(caster);
		check(helper, "maximum diameter", Math.abs(shield.radius() * 2 - size.max()) < CLOSE);
		check(helper, "maximum placement", shield.position().distanceTo(spot) < CLOSE);
		release(caster);
		shield.discard();
		helper.succeed();
	}

	@GameTest
	public void aCastUsesTheCastersChosenDistanceAndSizeWithinTheBounds(GameTestHelper helper) {
		ServerPlayer caster = emiya(helper);
		AbilitySetting distance = ability().settings().get(0);
		AbilitySetting size = ability().settings().get(1);
		AbilitySettings.set(caster, RhoAias.KEY.identifier(), distance.id(), CHOSEN_DISTANCE);
		AbilitySettings.set(caster, RhoAias.KEY.identifier(), size.id(), CHOSEN_SIZE);
		// The chosen distance can cross the empty structure's loaded chunk boundary.
		// Force-load tickets take effect later; load the destination before synchronous entity lookup.
		helper.getLevel().getChunkAt(BlockPos.containing(HeldInFront.at(caster.getEyePosition(), caster.getLookAngle(), CHOSEN_DISTANCE)));
		press(caster);
		RhoAiasEntity shield = RhoAiasEntity.of(caster);
		check(helper, "chosen size across", Math.abs(shield.radius() - CHOSEN_SIZE / 2) < CLOSE);
		check(helper, "chosen distance",
			shield.position().distanceTo(HeldInFront.at(caster.getEyePosition(), caster.getLookAngle(), CHOSEN_DISTANCE)) < CLOSE);
		release(caster);
		shield.discard();

		AbilitySettings.set(caster, RhoAias.KEY.identifier(), size.id(), size.max() + 1);
		press(caster);
		check(helper, "size clamped to the maximum", Math.abs(RhoAiasEntity.of(caster).radius() - size.max() / 2) < CLOSE);
		release(caster);
		helper.succeed();
	}

	@GameTest(maxTicks = HELD_TICKS + AFTER_RELEASE + 3)
	public void itFollowsTheHeadWhilePressedAndHeldButNotAfterRelease(GameTestHelper helper) {
		ServerPlayer caster = emiya(helper);
		press(caster);
		RhoAiasEntity shield = RhoAiasEntity.of(caster);
		check(helper, "cast game time", shield.castTime() == helper.getLevel().getGameTime());
		check(helper, "full immediately", shield.hp() == shield.maxHp());
		caster.setYRot(90.0F);
		caster.setXRot(-30.0F);
		helper.runAfterDelay(2, () -> {
			check(helper, "follows before hold", shield.getLookAngle().distanceTo(caster.getLookAngle()) < CLOSE);
			check(helper, "held in front", shield.position().distanceTo(HeldInFront.at(caster.getEyePosition(), caster.getLookAngle(),
				FateConfig.rhoAias().defaultDistance())) < CLOSE);
			caster.setYRot(-45.0F);
		});
		Vec3[] at = {null}, facing = {null};
		helper.runAfterDelay(HELD_TICKS, () -> {
			check(helper, "follows while held", shield.getLookAngle().distanceTo(caster.getLookAngle()) < CLOSE);
			release(caster);
			at[0] = shield.position();
			facing[0] = shield.getLookAngle();
			caster.setYRot(180.0F);
			caster.setPos(caster.position().add(1, 0, 0));
		});
		helper.runAfterDelay(HELD_TICKS + AFTER_RELEASE, () -> {
			check(helper, "stays placed", shield.position().equals(at[0]) && shield.getLookAngle().equals(facing[0]));
			check(helper, "tap time since last hold", shield.ticksLeft() >= AlayaConfig.shields().tapTicks() - AFTER_RELEASE - 1);
			helper.succeed();
		});
	}

	@GameTest
	public void holdingPaysUpkeepAndRepairsAtTheRankRate(GameTestHelper helper) {
		ServerPlayer caster = emiya(helper);
		press(caster);
		RhoAiasEntity shield = RhoAiasEntity.of(caster);
		Husk attacker = FateTests.mob(helper, STANDING.south(4));
		shield.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), HIT);
		double before = AlayaMana.get(caster).current();
		for (int tick = 0; tick < AlayaConfig.abilities().holdThresholdTicks(); tick++) {
			Abilities.tick(caster);
		}
		double expected = AlayaConfig.shields().holdUpkeepPerTick() + AlayaConfig.ranks().shieldRepairCost(shield.rank(), HIT);
		check(helper, "repaired", shield.hp() == shield.maxHp());
		check(helper, "upkeep plus repair", Math.abs(before - AlayaMana.get(caster).current() - expected) < CLOSE);
		double full = AlayaMana.get(caster).current();
		Abilities.tick(caster);
		check(helper, "full pays only upkeep", Math.abs(full - AlayaMana.get(caster).current() - AlayaConfig.shields().holdUpkeepPerTick()) < CLOSE);
		release(caster);
		helper.succeed();
	}

	@GameTest
	public void holdingWithTooLittleManaRepairsWhatItCanAndEndsWithoutRemovingTheShield(GameTestHelper helper) {
		ServerPlayer caster = emiya(helper);
		press(caster);
		RhoAiasEntity shield = RhoAiasEntity.of(caster);
		Husk attacker = FateTests.mob(helper, STANDING.south(4));
		shield.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), HIT);
		double before = shield.hp();
		double repairMana = 1.0;
		AlayaMana.update(caster, mana -> mana.withCurrent(AlayaConfig.shields().holdUpkeepPerTick() + repairMana));
		for (int tick = 0; tick < AlayaConfig.abilities().holdThresholdTicks(); tick++) {
			Abilities.tick(caster);
		}
		check(helper, "cast ended", !Abilities.isActive(caster, RhoAias.KEY.identifier()));
		check(helper, "shield remains", shield.isAlive());
		check(helper, "mana spent", AlayaMana.get(caster).current() == 0);
		check(helper, "partial repair", Math.abs(shield.hp() - before - repairMana / AlayaConfig.ranks().repairRate(shield.rank())) < CLOSE);
		helper.succeed();
	}

	@GameTest
	public void tappingLeavesADamagedShieldForTheTapTime(GameTestHelper helper) {
		ServerPlayer caster = emiya(helper);
		press(caster);
		RhoAiasEntity shield = RhoAiasEntity.of(caster);
		release(caster);
		check(helper, "tap time", shield.ticksLeft() == AlayaConfig.shields().tapTicks());
		Husk attacker = FateTests.mob(helper, STANDING.south(4));
		shield.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), HIT);
		double hp = shield.hp();
		double mana = AlayaMana.get(caster).current();
		helper.runAfterDelay(AFTER_RELEASE, () -> {
			check(helper, "time counts down", shield.ticksLeft() == AlayaConfig.shields().tapTicks() - AFTER_RELEASE);
			check(helper, "no repair or upkeep", shield.hp() == hp && AlayaMana.get(caster).current() >= mana);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = TAP_TEST_TICKS)
	public void aTappedShieldExpiresOnlyAtTheEndOfTheTapTime(GameTestHelper helper) {
		ServerPlayer caster = emiya(helper);
		press(caster);
		RhoAiasEntity shield = RhoAiasEntity.of(caster);
		release(caster);
		int life = AlayaConfig.shields().tapTicks();
		helper.runAfterDelay(life - 1, () -> check(helper, "alive until expiry", shield.isAlive()));
		helper.runAfterDelay(life + 1, () -> {
			check(helper, "expired", shield.isRemoved());
			check(helper, "next projection allowed", ability().cannotStart(caster, 0) == null);
			helper.succeed();
		});
	}

	@GameTest
	public void anUnloadedShieldCanBeDismissedAndStaysDismissedOnReload(GameTestHelper helper) {
		ServerPlayer caster = emiya(helper);
		press(caster);
		RhoAiasEntity shield = RhoAiasEntity.of(caster);
		release(caster);
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
		check(helper, "saved before unload", shield.save(output));
		shield.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
		FateShields.Projection projection = caster.getAttached(FateShields.PROJECTION);
		// As if its chunk had unloaded with it: the test's own chunk stays loaded.
		caster.setAttached(FateShields.PROJECTION, new FateShields.Projection(projection.shield(), projection.expiresAt(), projection.dimension(),
			UNLOADED_CHUNK));
		check(helper, "unloaded", RhoAiasEntity.of(caster) == null);
		double before = AlayaMana.get(caster).current();
		press(caster);
		check(helper, "dismissed reference, no projection and no cost", caster.getAttached(FateShields.PROJECTION) == null
			&& !Abilities.isActive(caster, RhoAias.KEY.identifier()) && AlayaMana.get(caster).current() == before);
		press(caster);
		RhoAiasEntity replacement = RhoAiasEntity.of(caster);
		release(caster);
		RhoAiasEntity loaded = new RhoAiasEntity(FateShields.RHO_AIAS, helper.getLevel());
		loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult()));
		helper.getLevel().addFreshEntity(loaded);
		check(helper, "dismissed shield removed when its chunk reloads", loaded.isRemoved());
		check(helper, "replacement remains", RhoAiasEntity.of(caster) == replacement && replacement.isAlive());
		helper.succeed();
	}

	@GameTest
	public void aShieldGoneWhileItsCasterWasAwayDoesNotBlockTheNext(GameTestHelper helper) {
		ServerPlayer caster = emiya(helper);
		press(caster);
		RhoAiasEntity shield = RhoAiasEntity.of(caster);
		release(caster);
		FateShields.Projection projection = caster.getAttached(FateShields.PROJECTION);
		shield.discard();
		// As if the caster had been offline when it broke: nothing cleared the projection.
		caster.setAttached(FateShields.PROJECTION, projection);
		check(helper, "its chunk is loaded and it is gone", ability().cannotStart(caster, 0) == null);
		press(caster);
		check(helper, "next projection", RhoAiasEntity.of(caster) != null && RhoAiasEntity.of(caster) != shield);
		release(caster);
		helper.succeed();
	}

	private static RhoAiasEntity collisionShield(GameTestHelper helper, ServerPlayer caster) {
		Vec3 spot = HeldInFront.at(caster.getEyePosition(), caster.getLookAngle(), FateConfig.rhoAias().defaultDistance());
		int reach = (int) Math.ceil(FateConfig.rhoAias().defaultSize() + APPROACH_DISTANCE);
		for (BlockPos block : BlockPos.betweenClosed(BlockPos.containing(spot).offset(-reach, -reach, -reach),
			BlockPos.containing(spot).offset(reach, reach, reach))) {
			helper.getLevel().setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
		}
		helper.getLevel().getChunkAt(BlockPos.containing(spot));
		press(caster);
		RhoAiasEntity shield = RhoAiasEntity.of(caster);
		release(caster);
		return shield;
	}

	@GameTest(maxTicks = COLLISION_TICKS + 10)
	public void walkingMobAndDroppedItemCannotCrossTheDisc(GameTestHelper helper) {
		ServerPlayer caster = emiya(helper);
		RhoAiasEntity shield = collisionShield(helper, caster);
		Vec3 normal = shield.getLookAngle();
		Husk walker = FateTests.mob(helper, STANDING);
		walker.setNoAi(false);
		walker.setNoGravity(false);
		Vec3 start = shield.position().add(normal.scale(APPROACH_DISTANCE));
		Vec3 feet = new Vec3(start.x, caster.getY(), start.z);
		floor(helper, shield, feet.y, WALK_LANE_RADIUS);
		walker.snapTo(feet);
		Vec3 goal = feet.subtract(normal.scale(APPROACH_DISTANCE * 2));
		ItemEntity item = new ItemEntity(helper.getLevel(), start.x + 1, start.y, start.z, new ItemStack(Items.STONE));
		item.setNoGravity(true);
		helper.getLevel().addFreshEntity(item);
		helper.onEachTick(() -> {
			walker.setTarget(null);
			walker.getNavigation().moveTo(goal.x, goal.y, goal.z, NAVIGATION_SPEED);
			item.setDeltaMovement(normal.scale(-WALK_STEP));
		});
		helper.runAfterDelay(COLLISION_TICKS, () -> {
			double side = walker.getBoundingBox().getCenter().subtract(shield.position()).dot(normal);
			check(helper, "walking mob reached the barrier", side < APPROACH_DISTANCE / 2);
			check(helper, "walking mob stayed in front", side > 0);
			check(helper, "item stayed in front", item.position().subtract(shield.position()).dot(normal) > 0);
			check(helper, "body contact does not damage the shield", shield.hp() == shield.maxHp());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = COLLISION_TICKS + 10)
	public void ownMovementStopsAtTheDiscOnTheGround(GameTestHelper helper) {
		ServerPlayer caster = emiya(helper);
		caster.setXRot(LOOKING_DOWN);
		RhoAiasEntity shield = collisionShield(helper, caster);
		Vec3 normal = shield.getLookAngle();
		Vec3 ahead = new Vec3(normal.x, 0, normal.z).normalize();
		Vec3 side = ahead.cross(Vec3.Y_AXIS);
		double ground = caster.getY();
		floor(helper, shield, ground, ARENA_RADIUS);
		Vec3 front = new Vec3(shield.getX(), ground, shield.getZ()).add(ahead.scale(APPROACH_DISTANCE));
		ServerPlayer runner = walker(helper, front);
		ServerPlayer jumper = walker(helper, front.add(side.scale(APPROACH_DISTANCE)));
		ServerPlayer behind = walker(helper, caster.position().add(side.scale(APPROACH_DISTANCE)));
		Creeper creeper = helper.spawn(EntityType.CREEPER, STANDING);
		creeper.snapTo(front.subtract(side.scale(APPROACH_DISTANCE)));
		Vec3 goal = creeper.position().subtract(ahead.scale(APPROACH_DISTANCE * 2));
		double[] rise = {0.0};
		double[] highest = {ground};
		helper.onEachTick(() -> {
			runner.move(MoverType.SELF, ahead.scale(-SPRINT_STEP).add(0, -GRAVITY, 0));
			behind.move(MoverType.SELF, ahead.scale(SPRINT_STEP).add(0, -GRAVITY, 0));
			rise[0] = jumper.onGround() ? JUMP : rise[0] - GRAVITY;
			jumper.move(MoverType.SELF, ahead.scale(-SPRINT_STEP).add(0, rise[0], 0));
			highest[0] = Math.max(highest[0], jumper.getY());
			creeper.setTarget(null);
			creeper.setSwellDir(CALM);
			creeper.getNavigation().moveTo(goal.x, goal.y, goal.z, NAVIGATION_SPEED);
		});
		helper.runAfterDelay(COLLISION_TICKS, () -> {
			for (Entity body : new Entity[]{runner, jumper, creeper}) {
				String name = body == runner ? "sprinting player" : body == jumper ? "jumping player" : "creeper";
				double inFront = body.getBoundingBox().getCenter().subtract(shield.position()).dot(normal);
				check(helper, name + " reached the disc", inFront < APPROACH_DISTANCE / 2);
				check(helper, name + " stayed in front", inFront > 0);
			}
			check(helper, "player behind stayed behind", behind.getBoundingBox().getCenter().subtract(shield.position()).dot(normal) < 0);
			check(helper, "players walking into it stay on the ground", Math.abs(runner.getY() - ground) < CLOSE
				&& Math.abs(behind.getY() - ground) < CLOSE);
			check(helper, "creeper neither sinks nor rises", Math.abs(creeper.getY() - ground) < CLOSE && creeper.onGround());
			check(helper, "jumping does not climb it", highest[0] < ground + CLIMB_HEIGHT);
			check(helper, "body contact does not damage the shield", shield.hp() == shield.maxHp());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = PUSH_TICKS + 10)
	public void aDiscCastOntoAMobPushesItOutAlongTheGround(GameTestHelper helper) {
		ServerPlayer caster = emiya(helper);
		caster.setXRot(LOOKING_DOWN);
		RhoAiasEntity shield = collisionShield(helper, caster);
		Vec3 normal = shield.getLookAngle();
		double ground = caster.getY();
		floor(helper, shield, ground, ARENA_RADIUS);
		Husk mob = FateTests.mob(helper, STANDING);
		mob.snapTo(new Vec3(shield.getX(), ground, shield.getZ()).add(new Vec3(normal.x, 0, normal.z).normalize().scale(OVERLAP)));
		double before = mob.getBoundingBox().getCenter().subtract(shield.position()).dot(normal);
		check(helper, "the mob starts inside the disc", Math.abs(before) < mob.getBbWidth() / 2);
		helper.runAfterDelay(PUSH_TICKS, () -> {
			double after = mob.getBoundingBox().getCenter().subtract(shield.position()).dot(normal);
			check(helper, "pushed out to the side it was on", after * Math.signum(before) > mob.getBbWidth() / 2);
			check(helper, "neither lifted nor pushed into the ground", Math.abs(mob.getY() - ground) < CLOSE);
			helper.succeed();
		});
	}

	private static ServerPlayer walker(GameTestHelper helper, Vec3 feet) {
		ServerPlayer walker = FateTests.human(helper, STANDING);
		walker.snapTo(feet);
		return walker;
	}

	private static void floor(GameTestHelper helper, RhoAiasEntity shield, double feetY, int radius) {
		BlockPos floor = BlockPos.containing(new Vec3(shield.getX(), feetY, shield.getZ())).below();
		for (BlockPos block : BlockPos.betweenClosed(floor.offset(-radius, 0, -radius), floor.offset(radius, 0, radius))) {
			helper.getLevel().setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
		}
	}

	@GameTest(maxTicks = COLLISION_TICKS + 10)
	public void incomingArrowStopsButOwnerCanMoveAndShootFromBehind(GameTestHelper helper) {
		ServerPlayer caster = emiya(helper);
		RhoAiasEntity shield = collisionShield(helper, caster);
		Vec3 normal = shield.getLookAngle();
		Arrow incoming = helper.spawn(EntityType.ARROW, STANDING);
		incoming.snapTo(shield.position().add(normal.scale(APPROACH_DISTANCE)));
		incoming.setNoGravity(true);
		incoming.setDeltaMovement(normal.scale(-ARROW_SPEED));
		incoming.setAttached(Attacks.ATTACK, new AttackData(caster.getUUID(), shield.rank(), false, false));
		Arrow outgoing = helper.spawn(EntityType.ARROW, STANDING);
		outgoing.setOwner(caster);
		outgoing.setNoGravity(true);
		outgoing.snapTo(shield.position().subtract(normal.scale(APPROACH_DISTANCE)).add(WALK_STEP * 2, 0, 0));
		outgoing.setDeltaMovement(normal.scale(ARROW_SPEED));
		// Even when the owner's body overlaps the disc, collision must not shove the caster.
		Vec3 ownerAt = shield.position().subtract(normal.scale(WALK_STEP));
		caster.setPos(ownerAt);
		helper.runAfterDelay(COLLISION_TICKS, () -> {
			check(helper, "incoming arrow stopped", incoming.isRemoved());
			check(helper, "incoming arrow used shield HP hit logic", shield.hp() < shield.maxHp());
			check(helper, "outgoing arrow crossed freely", outgoing.isAlive()
				&& outgoing.position().subtract(shield.position()).dot(normal) > APPROACH_DISTANCE);
			check(helper, "owner not pushed", caster.position().distanceTo(ownerAt) < CLOSE);
			caster.move(MoverType.SELF, normal.scale(-WALK_STEP));
			check(helper, "owner can move behind it", caster.position().distanceTo(ownerAt.subtract(normal.scale(WALK_STEP))) < CLOSE);
			helper.succeed();
		});
	}

	@GameTest
	public void ordinaryProjectilesDealHalfButNoblePhantasmProjectilesDealFullDamage(GameTestHelper helper) {
		ServerPlayer caster = emiya(helper);
		press(caster);
		RhoAiasEntity shield = RhoAiasEntity.of(caster);
		release(caster);
		Arrow arrow = helper.spawn(EntityType.ARROW, STANDING.south(4));
		// It stays where it is over the ticks below, never reaching the shield itself.
		arrow.setNoGravity(true);
		arrow.setAttached(Attacks.ATTACK, new AttackData(caster.getUUID(), shield.rank(), false, false));
		// A shield takes each attack once a tick: the same arrow hits again on the next ticks.
		helper.startSequence().thenExecute(() -> {
			shield.hurtServer(helper.getLevel(), helper.getLevel().damageSources().arrow(arrow, caster), HIT);
			check(helper, "ranked non-NP half", Math.abs(shield.maxHp() - shield.hp() - HIT / 2) < CLOSE);
		}).thenIdle(1).thenExecute(() -> {
			arrow.setAttached(Attacks.ATTACK, new AttackData(caster.getUUID(), shield.rank(), false, true));
			shield.hurtServer(helper.getLevel(), helper.getLevel().damageSources().arrow(arrow, caster), HIT);
			check(helper, "NP full", Math.abs(shield.maxHp() - shield.hp() - HIT / 2 - HIT) < CLOSE);
		}).thenIdle(1).thenExecute(() -> {
			arrow.removeAttached(Attacks.ATTACK);
			shield.hurtServer(helper.getLevel(), helper.getLevel().damageSources().arrow(arrow, caster), HIT);
			check(helper, "vanilla projectile half", Math.abs(shield.maxHp() - shield.hp() - HIT * 2) < CLOSE);
		}).thenSucceed();
	}

	@GameTest
	public void emiyaCanBeChosenAndHasRhoAiasAndTheGilgameshDeeds(GameTestHelper helper) {
		ServerPlayer player = FateTests.player(helper, STANDING);
		FateTests.become(helper, player, FateServants.EMIYA);
		check(helper, "chosen", AlayaServants.servant(player).orElseThrow() == FateServants.EMIYA);
		check(helper, "Archer, no divinity", FateServants.EMIYA.servantClass().equals(ServantClasses.ARCHER) && Divinity.of(player) == 0);
		check(helper, "Rho Aias starts available without true name", Abilities.owned(player).contains(RhoAias.KEY) && !ability().noblePhantasm());
		TrueNameDeeds deeds = helper.getLevel().registryAccess().lookupOrThrow(TrueName.DEEDS_KEY).getValueOrThrow(
			ResourceKey.create(TrueName.DEEDS_KEY, Fatecraft.id("emiya")));
		check(helper, "Warden first", kills(deeds.deeds().get(0), "minecraft:warden"));
		check(helper, "raid second", deeds.deeds().get(1).condition().kind() == DeedKinds.WIN_RAID);
		check(helper, "Elder Guardian third", kills(deeds.deeds().get(2), "minecraft:elder_guardian"));
		check(helper, "awakening data", deeds.awakening().equals(Fatecraft.id("emiya/true_name")));
		press(player);
		check(helper, "usable from start", RhoAiasEntity.of(player) != null);
		release(player);
		helper.succeed();
	}

	@GameTest
	public void savedShieldKeepsHpPetalsAndOriginalCastTime(GameTestHelper helper) {
		ServerPlayer caster = emiya(helper);
		TrueName.realize(caster);
		press(caster);
		RhoAiasEntity shield = RhoAiasEntity.of(caster);
		release(caster);
		shield.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(FateTests.mob(helper, STANDING.south(4))), HIT);
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
		check(helper, "saved like base", shield.save(output));
		RhoAiasEntity loaded = new RhoAiasEntity(FateShields.RHO_AIAS, helper.getLevel());
		loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult()));
		check(helper, "HP and rank", loaded.hp() == shield.hp() && loaded.maxHp() == shield.maxHp() && loaded.rank().equals(shield.rank()));
		check(helper, "petals, size, expiry and timestamp", loaded.petals() == 7 && loaded.radius() == shield.radius()
			&& loaded.ticksLeft() == shield.ticksLeft() && loaded.castTime() == shield.castTime());
		helper.succeed();
	}
}
