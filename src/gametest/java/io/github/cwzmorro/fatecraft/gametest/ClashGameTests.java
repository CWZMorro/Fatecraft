package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;
import static io.github.cwzmorro.fatecraft.gametest.FateTests.mob;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.fatecraft.altera.TeardropPhotonRay;
import io.github.cwzmorro.fatecraft.altera.TeardropPhotonRayEntity;
import io.github.cwzmorro.fatecraft.weapon.EnumaElish;
import io.github.cwzmorro.fatecraft.weapon.EnumaElishBeam;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class ClashGameTests {
	private static final BlockPos GROUND = new BlockPos(3, 0, 3);
	private static final BlockPos EA = GROUND.above(5);
	// Both casters stay inside the ticking fixture; farther out, a position could land in an unticked chunk.
	private static final BlockPos ALTERA = GROUND.above().east(4).south(4);
	private static final int FIRST_FRAME = FateTests.EA_WIND_UP_TICKS + 5;
	private static final int SLIDE_TICKS = 20;
	private static final int END_TICKS = FateTests.EA_WIND_UP_TICKS + 80;
	private static final int TIMEOUT = END_TICKS + 5;
	private static final double EPSILON = 0.01;

	@GameTest(maxTicks = TIMEOUT)
	public void skyClashSlidesAndEaContinuesWithoutHittingAlteraOrGround(GameTestHelper helper) {
		helper.setBlock(GROUND, Blocks.STONE);
		Husk ea = mob(helper, EA);
		Husk altera = mob(helper, ALTERA);
		ClashScene.start(ea, altera, helper.absoluteVec(Vec3.atCenterOf(GROUND).add(0, 0.5, 0)));
		double[] firstReach = {0};
		helper.runAfterDelay(FIRST_FRAME, () -> {
			EnumaElishBeam beam = beam(helper, ea);
			TeardropPhotonRayEntity sky = sky(helper, altera);
			check(helper, "EX and A++ hold, not instant victory: sky firing=" + sky.firing() + ", held=" + sky.heldTicks() + ", active="
				+ Abilities.isActive(altera, TeardropPhotonRay.KEY.identifier()) + ", ea live=" + beam.beamLive(), sky.firing() && beam.beamLive());
			check(helper, "both clipped", beam.reach() < EnumaElishBeam.length() && sky.reach() < sky.beamLength() - EPSILON);
			firstReach[0] = beam.reach();
			check(helper, "ground not excavated", sky.depth() == 0);
			helper.assertBlockPresent(Blocks.STONE, GROUND);
		});
		helper.runAfterDelay(FIRST_FRAME + SLIDE_TICKS, () -> {
			check(helper, "contact moves up toward losing sky origin", beam(helper, ea).reach() > firstReach[0] + EPSILON);
			helper.assertBlockPresent(Blocks.STONE, GROUND);
		});
		helper.runAfterDelay(END_TICKS, () -> {
			check(helper, "Teardrop ran out", !Abilities.isActive(altera, TeardropPhotonRay.KEY.identifier()));
			check(helper, "Ea still firing along its upward path", beam(helper, ea).reach() == EnumaElishBeam.length());
			check(helper, "Altera on ground outside Ea path unharmed", altera.getHealth() == altera.getMaxHealth());
			check(helper, "losing caster mana exhausted", AlayaMana.available(altera) < beam(helper, ea).beamDrainPerSecond());
			helper.assertBlockPresent(Blocks.STONE, GROUND);
			Abilities.release(ea, EnumaElish.KEY.identifier());
			helper.succeed();
		});
	}

	private static EnumaElishBeam beam(GameTestHelper helper, Husk ea) {
		return helper.getLevel().getEntitiesOfClass(EnumaElishBeam.class, ea.getBoundingBox().inflate(EnumaElishBeam.length()),
			beam -> beam.beamOwner() == ea).getFirst();
	}

	private static TeardropPhotonRayEntity sky(GameTestHelper helper, Husk altera) {
		return helper.getLevel().getEntitiesOfClass(TeardropPhotonRayEntity.class, altera.getBoundingBox().inflate(EnumaElishBeam.length()),
			ray -> ray.owner() == altera).getFirst();
	}
}
