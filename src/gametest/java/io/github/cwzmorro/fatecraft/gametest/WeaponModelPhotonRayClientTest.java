package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.altera.WeaponFlip;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import io.github.cwzmorro.fatecraft.weapon.WeaponOpening;
import io.github.cwzmorro.fatecraft.weapon.WeaponSpin;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTypeTest;

public class WeaponModelPhotonRayClientTest extends WeaponModelExcaliburClientTest {
	private static final float[] OPENINGS = {0.0F, 0.5F, 1.0F, 1.0F, 1.0F};
	private static final String[] OPENING_NAMES = {"closed", "half-open", "open", "spinning", "flipped"};

	private static final int SPINNING = 3;
	private static final int FLIPPED = 4;
	private int state;

	@Override
	protected String mainHand() {
		return "photon_ray";
	}

	@Override
	protected String screenshotPrefix() {
		return this.mainHand() + "-" + OPENING_NAMES[this.state];
	}

	@Override
	protected void stage(TestSingleplayerContext world) {
		float opening = OPENINGS[this.state];
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			Player player = FateClientTests.player(server);
			this.pose(player.getMainHandItem(), level.getGameTime(), opening);
			for (ArmorStand stand : level.getEntities(EntityTypeTest.forClass(ArmorStand.class), entity -> true)) {
				this.pose(stand.getItemBySlot(EquipmentSlot.MAINHAND), level.getGameTime(), opening);
			}
			for (ItemEntity item : level.getEntities(EntityTypeTest.forClass(ItemEntity.class), entity -> true)) {
				ItemStack stack = item.getItem().copy();
				this.pose(stack, level.getGameTime(), opening);
				item.setItem(stack);
			}
		});
	}

	private void pose(ItemStack stack, long time, float opening) {
		if (this.state == SPINNING) {
			stack.set(FateWeapons.SPIN, new WeaponSpin(time, (float) FateConfig.altera().spinDegreesPerTick()));
		} else if (this.state == FLIPPED) {
			WeaponFlip.set(stack, time - WeaponOpening.DURATION_TICKS, true);
		}
		if (opening >= 1.0F) {
			WeaponOpening.set(stack, time - WeaponOpening.DURATION_TICKS, true);
		} else if (opening > 0.0F) {
			// Started in the far future, so it stays at where it began.
			stack.set(FateWeapons.OPENING, new WeaponOpening(Long.MAX_VALUE, opening, true));
		}
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		for (this.state = 0; this.state < OPENINGS.length; this.state++) {
			super.runTest(context);
		}
	}
}
