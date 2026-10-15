package io.github.cwzmorro.fatecraft.servant;

import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.servant.Servant;
import io.github.cwzmorro.alayacore.servant.ServantClasses;
import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.altera.Altera;
import io.github.cwzmorro.fatecraft.altera.CrestOfTheStar;
import io.github.cwzmorro.fatecraft.altera.PhotonRay;
import io.github.cwzmorro.fatecraft.altera.TeardropPhotonRay;
import io.github.cwzmorro.fatecraft.artoria.Avalon;
import io.github.cwzmorro.fatecraft.artoria.ExcaliburNP;
import io.github.cwzmorro.fatecraft.artoria.ManaBurst;
import io.github.cwzmorro.fatecraft.artoria.ManaShot;
import io.github.cwzmorro.fatecraft.artoria.ManaSlash;
import io.github.cwzmorro.fatecraft.artoria.StrikeAir;
import io.github.cwzmorro.fatecraft.chain.Enkidu;
import io.github.cwzmorro.fatecraft.gate.GateOfBabylon;
import io.github.cwzmorro.fatecraft.shield.RhoAias;
import io.github.cwzmorro.fatecraft.trace.SwordBody;
import io.github.cwzmorro.fatecraft.trace.Trace;
import io.github.cwzmorro.fatecraft.ubw.UnlimitedBladeWorks;
import io.github.cwzmorro.fatecraft.vault.VaultAbility;
import io.github.cwzmorro.fatecraft.weapon.EnumaElish;
import java.util.List;
import net.minecraft.core.Registry;

/** Fatecraft's servants, registered into Alaya Core. */
public final class FateServants {
	public static final Servant GILGAMESH = Registry.register(AlayaRegistries.SERVANT, Fatecraft.id("gilgamesh"),
		new Servant(ServantClasses.ARCHER, List.of(VaultAbility.KEY, GateOfBabylon.KEY, Enkidu.KEY, EnumaElish.KEY)));
	public static final Servant EMIYA = Registry.register(AlayaRegistries.SERVANT, Fatecraft.id("emiya"),
		new Servant(ServantClasses.ARCHER, List.of(RhoAias.KEY, Trace.KEY, SwordBody.KEY, UnlimitedBladeWorks.KEY)));

	// Altera's melee hits ignore the hit cooldown.
	public static final Servant ALTERA = Registry.register(AlayaRegistries.SERVANT, Fatecraft.id("altera"),
		new Servant(ServantClasses.SABER, List.of(CrestOfTheStar.KEY, PhotonRay.KEY, TeardropPhotonRay.KEY, Altera.NATURAL_BODY)).meleeIgnoringHitCooldown());

	public static final Servant ARTORIA = Registry.register(AlayaRegistries.SERVANT, Fatecraft.id("artoria"),
		new Servant(ServantClasses.SABER, List.of(StrikeAir.KEY, ManaBurst.KEY, ManaSlash.KEY, ManaShot.KEY, ExcaliburNP.KEY, Avalon.KEY)));

	private FateServants() {
	}

	public static void init() {
	}
}
