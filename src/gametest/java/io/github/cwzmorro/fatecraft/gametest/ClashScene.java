package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.servant.ServantChoice;
import io.github.cwzmorro.alayacore.servant.ServantStorage;
import io.github.cwzmorro.fatecraft.altera.TeardropPhotonRay;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.weapon.EnumaElish;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

final class ClashScene {
	private static final double EA_SECONDS = 10.0;
	private static final double SKY_SECONDS = 3.0;

	private ClashScene() {
	}

	static void start(Husk ea, Husk altera, Vec3 groundAim) {
		ea.setNoAi(true);
		ea.setNoGravity(true);
		ea.setAttached(ServantStorage.CHOICE, ServantChoice.servant(AlayaRegistries.SERVANT.getKey(FateServants.GILGAMESH)));
		ea.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FateWeapons.EA));
		ea.setXRot(-90.0F);
		AlayaMana.getOrCreate(ea);
		double eaMana = EA_SECONDS * AlayaConfig.ranks().beamDrainPerSecond(EnumaElish.RANK);
		AlayaMana.update(ea, mana -> mana.withMax(eaMana).withCurrent(eaMana));
		altera.setNoAi(true);
		altera.setNoGravity(true);
		altera.setAttached(ServantStorage.CHOICE, ServantChoice.servant(AlayaRegistries.SERVANT.getKey(FateServants.ALTERA)));
		altera.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FateWeapons.PHOTON_RAY));
		altera.lookAt(EntityAnchorArgument.Anchor.EYES, groundAim);
		AlayaMana.getOrCreate(altera);
		double skyDrain = AlayaConfig.clash().drainPerSecond(AlayaConfig.ranks().beamDrainPerSecond(TeardropPhotonRay.RANK),
			TeardropPhotonRay.RANK, EnumaElish.RANK);
		double skyMana = SKY_SECONDS * skyDrain;
		AlayaMana.update(altera, mana -> mana.withMax(skyMana).withCurrent(skyMana));
		Abilities.press(ea, EnumaElish.KEY.identifier(), 0, false);
		Abilities.press(altera, TeardropPhotonRay.KEY.identifier(), TeardropPhotonRay.CONDENSED_MODE, false);
	}
}
