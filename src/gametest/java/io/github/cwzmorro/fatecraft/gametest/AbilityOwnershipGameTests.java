package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.fatecraft.altera.Altera;
import io.github.cwzmorro.fatecraft.altera.CrestOfTheStar;
import io.github.cwzmorro.fatecraft.artoria.Avalon;
import io.github.cwzmorro.fatecraft.shield.RhoAias;
import io.github.cwzmorro.fatecraft.shield.RhoAiasEntity;
import io.github.cwzmorro.fatecraft.trace.ExcaliburStrain;
import io.github.cwzmorro.fatecraft.trace.Project;
import io.github.cwzmorro.fatecraft.trace.Traces;
import io.github.cwzmorro.fatecraft.ubw.UnlimitedBladeWorks;
import io.github.cwzmorro.fatecraft.vault.VaultMenu;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

public class AbilityOwnershipGameTests {
	private static final BlockPos START = new BlockPos(1, 1, 1);
	private static final int WEAKNESS_TICKS = 100;

	// Creative, so true-name rules let it cast.
	private static ServerPlayer servant(GameTestHelper helper, Identifier id) {
		ServerPlayer player = FateTests.servant(helper, START, FateTests.MANA, AlayaRegistries.SERVANT.getOptional(id).orElseThrow());
		player.setGameMode(GameType.CREATIVE);
		return player;
	}

	private static boolean usesKey(GameTestHelper helper, ServerPlayer player) {
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FateWeapons.BAB_ILU));
		return player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).consumesAction();
	}

	@GameTest
	public void abilitiesFollowOwnershipNotServant(GameTestHelper helper) {
		ServerPlayer heir = servant(helper, FateTestServants.HEIR);
		ServerPlayer bare = servant(helper, FateTestServants.BARE);
		Identifier sword = BuiltInRegistries.ITEM.getKey(Items.DIAMOND_SWORD);
		Traces.learn(heir, new ItemStack(Items.DIAMOND_SWORD));
		Traces.learn(bare, new ItemStack(Items.DIAMOND_SWORD));
		check(helper, "Trace: the owner learns and projects", Traces.canTrace(heir) && Traces.known(heir).contains(sword)
			&& Abilities.owns(heir, Project.key(sword).identifier()));
		check(helper, "Trace: no one else", !Traces.canTrace(bare) && !Traces.known(bare).contains(sword)
			&& !Abilities.owns(bare, Project.key(sword).identifier()));
		ExcaliburStrain.begin(heir);
		ExcaliburStrain.begin(bare);
		ExcaliburStrain.tick(heir);
		ExcaliburStrain.tick(bare);
		check(helper, "the strain stays only on a servant with Trace", ExcaliburStrain.active(heir) && !ExcaliburStrain.active(bare));
		check(helper, "Unlimited Blade Works", UnlimitedBladeWorks.eligible(heir) && !UnlimitedBladeWorks.eligible(bare));
		check(helper, "the key: Enuma Elish's owner draws Ea", usesKey(helper, heir) && !usesKey(helper, bare));
		FateTests.tap(heir, CrestOfTheStar.KEY.identifier(), 0, false);
		FateTests.tap(bare, CrestOfTheStar.KEY.identifier(), 0, false);
		check(helper, "Crest of the Star", CrestOfTheStar.active(heir) && !CrestOfTheStar.active(bare));
		VaultMenu.open(heir);
		VaultMenu.open(bare);
		check(helper, "the vault: only its owner's screen stays valid", heir.containerMenu.stillValid(heir) && !bare.containerMenu.stillValid(bare));
		Abilities.press(heir, RhoAias.KEY.identifier(), 0, false);
		Abilities.press(bare, RhoAias.KEY.identifier(), 0, false);
		check(helper, "Rho Aias: only its owner projects it", RhoAiasEntity.of(heir) != null && RhoAiasEntity.of(bare) == null);
		helper.succeed();
	}

	@GameTest
	public void passivesFollowOwnershipAndAreNeverPressed(GameTestHelper helper) {
		ServerPlayer heir = servant(helper, FateTestServants.HEIR);
		ServerPlayer bare = servant(helper, FateTestServants.BARE);
		Altera.tickPassive(heir);
		Altera.tickPassive(bare);
		check(helper, "Natural Body: the owner gets Strength", heir.hasEffect(MobEffects.STRENGTH) && !bare.hasEffect(MobEffects.STRENGTH));
		check(helper, "Natural Body: the owner refuses Weakness", !heir.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAKNESS_TICKS))
			&& bare.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAKNESS_TICKS)));
		check(helper, "Avalon: the owner uses it", usesAvalon(helper, heir) && !usesAvalon(helper, bare));
		heir.releaseUsingItem();
		for (Identifier passive : List.of(Altera.NATURAL_BODY.identifier(), Avalon.KEY.identifier())) {
			Abilities.press(heir, passive, 0, false);
			check(helper, "a press on " + passive + " does nothing", !Abilities.isActive(heir, passive) && !Abilities.isCasting(heir));
		}
		helper.succeed();
	}

	private static boolean usesAvalon(GameTestHelper helper, ServerPlayer player) {
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FateWeapons.AVALON));
		return FateWeapons.AVALON.use(helper.getLevel(), player, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME && Avalon.active(player);
	}
}
