package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.servant.Servant;
import io.github.cwzmorro.alayacore.servant.ServantClasses;
import io.github.cwzmorro.fatecraft.altera.Altera;
import io.github.cwzmorro.fatecraft.altera.CrestOfTheStar;
import io.github.cwzmorro.fatecraft.artoria.Avalon;
import io.github.cwzmorro.fatecraft.shield.RhoAias;
import io.github.cwzmorro.fatecraft.trace.Trace;
import io.github.cwzmorro.fatecraft.ubw.UnlimitedBladeWorks;
import io.github.cwzmorro.fatecraft.vault.VaultAbility;
import io.github.cwzmorro.fatecraft.weapon.EnumaElish;
import java.util.List;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

// Server-only, so the client tests' selection screen is unchanged.
public final class FateTestServants implements DedicatedServerModInitializer {
	static final Identifier HEIR = FateTests.id("heir");
	static final Identifier BARE = FateTests.id("bare");

	@Override
	public void onInitializeServer() {
		Registry.register(AlayaRegistries.SERVANT, HEIR, new Servant(ServantClasses.SABER,
			List.of(VaultAbility.KEY, RhoAias.KEY, Trace.KEY, UnlimitedBladeWorks.KEY, EnumaElish.KEY, CrestOfTheStar.KEY, Altera.NATURAL_BODY, Avalon.KEY)));
		Registry.register(AlayaRegistries.SERVANT, BARE, new Servant(ServantClasses.SABER, List.of()));
	}
}
