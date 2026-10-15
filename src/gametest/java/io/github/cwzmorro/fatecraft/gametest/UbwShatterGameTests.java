package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;
import static io.github.cwzmorro.fatecraft.gametest.UbwFeatureGameTests.emiya;
import static io.github.cwzmorro.fatecraft.gametest.UbwFeatureGameTests.ground;
import static io.github.cwzmorro.fatecraft.gametest.UbwFeatureGameTests.mana;
import static io.github.cwzmorro.fatecraft.gametest.UbwFeatureGameTests.matches;
import static io.github.cwzmorro.fatecraft.gametest.UbwFeatureGameTests.open;
import static io.github.cwzmorro.fatecraft.gametest.UbwFeatureGameTests.shots;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.marble.MarbleSphere;
import io.github.cwzmorro.alayacore.servant.ServantChoice;
import io.github.cwzmorro.alayacore.servant.ServantStorage;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.gate.GateOfBabylon;
import io.github.cwzmorro.fatecraft.gate.TreasureEntity;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.ubw.Ubw;
import io.github.cwzmorro.fatecraft.ubw.UbwController;
import io.github.cwzmorro.fatecraft.ubw.UbwEnvironmentEntity;
import io.github.cwzmorro.fatecraft.ubw.UbwGroundSwordEntity;
import io.github.cwzmorro.fatecraft.ubw.UbwLayout;
import io.github.cwzmorro.fatecraft.ubw.UbwMembership;
import io.github.cwzmorro.fatecraft.ubw.UbwRuntime;
import io.github.cwzmorro.fatecraft.ubw.UbwSwords;
import io.github.cwzmorro.fatecraft.ubw.UnlimitedBladeWorks;
import io.github.cwzmorro.fatecraft.vault.Vault;
import io.github.cwzmorro.fatecraft.weapon.EnumaElish;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class UbwShatterGameTests {
	private static final double CASTER_OUTSIDE = 10;
	private static final double CASTER_ASIDE = 10;
	private static final double CASTER_HEIGHT = 6;
	private static final int UPKEEP_TICKS = 10;
	private static final double MID_SHARE = 0.6;
	private static final int CLOSE_MARGIN = 3;
	private static final float BLAST_POWER = 4;
	private static final int AFTER_BLAST = 5;

	private static boolean refusedAs(Component reason, String key) {
		return reason != null && reason.getContents() instanceof TranslatableContents text && text.getKey().equals(key);
	}

	private static Husk enumaElishCaster(GameTestHelper helper, Vec3 at, float yaw) {
		Husk caster = FateTests.mob(helper, UbwFeatureGameTests.START);
		caster.setAttached(ServantStorage.CHOICE, ServantChoice.servant(AlayaRegistries.SERVANT.getKey(FateServants.GILGAMESH)));
		AlayaMana.getOrCreate(caster);
		caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FateWeapons.EA));
		caster.snapTo(at.x, at.y, at.z, yaw, 0);
		caster.setYHeadRot(yaw);
		caster.setYBodyRot(yaw);
		return caster;
	}

	@GameTest(environment = "fatecraft-gametest:isolated/feature_shatter", setupTicks = 2, padding = 64, maxTicks = 400)
	public void enumaElishBreaksTheMarbleWhichCrumblesThenCloses(GameTestHelper helper) throws IOException {
		ServerPlayer owner = emiya(helper);
		ServerLevel level = helper.getLevel();
		int floor = (int) Math.floor(owner.getY()) - 1;
		BlockPos placed = UbwController.placement(owner, FateConfig.ubw());
		Map<BlockPos, BlockState> standing = UbwLayout.sphere(level, placed, FateConfig.ubw(), floor, 1, Ubw.palette());
		Map<BlockPos, BlockState> originals = UbwTests.originals(level, standing);
		UbwController.Status status = open(helper, owner);
		Vec3 center = Vec3.atLowerCornerOf(status.center());
		int ticks = FateConfig.ubw().shatterTicks();
		// GoB's shot comes from the east; Enuma Elish crosses the field from the north, wide of EMIYA and Gilgamesh.
		ServerPlayer gil = FateTests.gilgamesh(helper, UbwFeatureGameTests.START, 50_000);
		gil.setPos(center.add(status.radius() + 12, 2, 0));
		gil.setYRot(90);
		gil.setXRot(0);
		Vault vault = Vault.of(gil);
		vault.setSettings(vault.settings().withVolleyChanged(0, 1 - vault.settings().volley()));
		vault.section(0).insert(new ItemStack(Items.DIAMOND_AXE));
		Vec3 casterAt = center.add(CASTER_ASIDE, floor + CASTER_HEIGHT - center.y, -status.radius() - CASTER_OUTSIDE);
		Husk caster = enumaElishCaster(helper, casterAt, 0);
		ServerPlayer anyone = FateTests.player(helper, UbwFeatureGameTests.START);
		check(helper, "the marble stands with its powers before the beam", UbwMembership.freeProjection(owner) && !UbwRuntime.shattering(owner));
		Abilities.press(caster, EnumaElish.KEY.identifier(), 0, false);
		long[] brokeAt = {-1};
		double[] manaAtBreak = {0};
		TreasureEntity[] gob = {null};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (brokeAt[0] < 0) {
				if (!UbwRuntime.shattering(owner)) {
					return;
				}
				brokeAt[0] = tick;
				manaAtBreak[0] = mana(owner);
				Abilities.end(caster, EnumaElish.KEY.identifier());
				check(helper, "broken at once: no free projection, no firing inside it", !UbwMembership.freeProjection(owner)
					&& !UbwRuntime.inside(owner, owner.position()) && !UbwRuntime.inside(owner, center)
					&& !UnlimitedBladeWorks.canSwitch(owner, UnlimitedBladeWorks.AOE_THROW));
				check(helper, "the owner can neither cancel nor reopen it while it crumbles",
					refusedAs(new UnlimitedBladeWorks().cannotStart(owner, UnlimitedBladeWorks.MARBLE), UnlimitedBladeWorks.SHATTERING));
				List<UbwGroundSwordEntity> swords = ground(helper, status);
				check(helper, "no ground sword can be pulled", !swords.isEmpty()
					&& swords.getFirst().interact(anyone, InteractionHand.MAIN_HAND, swords.getFirst().position()) == InteractionResult.PASS);
				Abilities.press(gil, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW, false);
				return;
			}
			if (gob[0] == null) {
				List<TreasureEntity> shot = level.getEntitiesOfClass(TreasureEntity.class, gil.getBoundingBox().inflate(32),
					candidate -> gil.getUUID().equals(candidate.owner()));
				if (!shot.isEmpty()) {
					Abilities.release(gil, GateOfBabylon.KEY.identifier());
					gob[0] = shot.getFirst();
					check(helper, "an actual incoming GoB weapon", UbwSwords.incoming(gob[0]));
					// Uncountered, it strikes the broken shell like any wall.
					gob[0].setPos(center.add(status.radius() + 2, 2, 0));
					gob[0].setDeltaMovement(-3, 0, 0);
					gob[0].tick();
					gob[0].tick();
					check(helper, "a GoB weapon crossing the broken shell is not countered: countered=" + UbwSwords.countered(gob[0])
						+ " counters=" + shots(helper, owner, status).size(), !UbwSwords.countered(gob[0]) && shots(helper, owner, status).isEmpty());
				}
			}
			if (tick == brokeAt[0] + UPKEEP_TICKS) {
				check(helper, "no upkeep while it crumbles", mana(owner) >= manaAtBreak[0]);
			}
			if (tick == brokeAt[0] + (long) (MID_SHARE * ticks)) {
				boolean given = false;
				boolean left = false;
				for (BlockPos pos : standing.keySet()) {
					if (standing.get(pos).equals(originals.get(pos))) {
						continue;
					}
					BlockState now = level.getBlockState(pos);
					given |= now.equals(originals.get(pos));
					left |= now.equals(standing.get(pos));
				}
				check(helper, "mid-crumble the real world shows through the gaps while the rest still stands",
					given && left && UbwRuntime.hasMarble(owner));
			}
			if (tick == brokeAt[0] + ticks + CLOSE_MARGIN) {
				check(helper, "a GoB weapon was shot at the broken marble", gob[0] != null);
				check(helper, "closed after the crumble with every original block back",
					!UbwRuntime.hasMarble(owner) && matches(level, originals, originals.keySet()));
				check(helper, "no ground swords or scenery left", ground(helper, status).isEmpty()
					&& level.getEntitiesOfClass(UbwEnvironmentEntity.class, caster.getBoundingBox().inflate(status.radius() * 3)).isEmpty());
				helper.succeed();
			}
		});
	}

	@GameTest(environment = "fatecraft-gametest:isolated/feature_unbroken", setupTicks = 2, padding = 64, maxTicks = 100)
	public void otherAttacksLeaveTheMarbleStanding(GameTestHelper helper) throws IOException {
		ServerPlayer owner = emiya(helper);
		UbwController.Status status = open(helper, owner);
		Vec3 center = Vec3.atLowerCornerOf(status.center());
		Husk bystander = FateTests.mob(helper, UbwFeatureGameTests.START);
		helper.getLevel().explode(bystander, center.x, owner.getY() + 1, center.z, BLAST_POWER, Level.ExplosionInteraction.TNT);
		Vec3 inside = MarbleSphere.impact(center, status.radius(), center, center.add(0, 0, 2.0 * status.radius()));
		check(helper, "a beam from inside breaks the shell where it leaves",
			inside != null && Math.abs(inside.distanceTo(center) - status.radius()) < 1e-6);
		helper.runAfterDelay(AFTER_BLAST, () -> {
			check(helper, "a blast is not Enuma Elish: the marble stands with its powers", !UbwRuntime.shattering(owner)
				&& UbwMembership.freeProjection(owner) && UbwRuntime.inside(owner, owner.position()));
			check(helper, "close restores", UbwRuntime.cancel(owner) == UbwController.CloseResult.CLOSED);
			helper.succeed();
		});
	}
}
