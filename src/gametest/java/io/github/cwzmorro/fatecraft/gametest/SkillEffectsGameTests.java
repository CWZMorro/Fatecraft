package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.mixin.MobEffectInstanceAccessor;
import io.github.cwzmorro.alayacore.servant.ServantChoice;
import io.github.cwzmorro.alayacore.servant.ServantStorage;
import io.github.cwzmorro.fatecraft.altera.Altera;
import io.github.cwzmorro.fatecraft.altera.CrestOfTheStar;
import io.github.cwzmorro.fatecraft.artoria.Avalon;
import io.github.cwzmorro.fatecraft.artoria.ManaBurst;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.trace.SwordBody;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public class SkillEffectsGameTests {
	private static final BlockPos START = new BlockPos(2, 2, 2);
	private static final int AFTER_TICK = 3;
	private static final int CANCEL_TICK = 10;
	private static final int TIMEOUT_TICKS = CANCEL_TICK + 5;
	// The effect's countdown and the test's clock are read at different points of a tick.
	private static final int TOLERANCE_TICKS = 2;
	private static final int LONGER_FACTOR = 2;
	private static final int SHORTER_DIVISOR = 2;

	private record Skill(Holder<MobEffect> effect, Function<GameTestHelper, ServerPlayer> make, Consumer<ServerPlayer> start, Consumer<ServerPlayer> end) { }

	private record Scenario(boolean before, int strength, boolean longer) {
		@Override
		public String toString() {
			return (this.before ? "before" : "after") + (this.strength < 0 ? " weaker" : this.strength > 0 ? " stronger" : " same") + (this.longer ? " longer" : " shorter");
		}
	}

	@GameTest(maxTicks = TIMEOUT_TICKS)
	public void swordBodyKeepsEveryPotion(GameTestHelper helper) {
		run(helper, swordBody(MobEffects.REGENERATION));
	}

	@GameTest(maxTicks = TIMEOUT_TICKS)
	public void manaBurstKeepsEveryPotion(GameTestHelper helper) {
		run(helper, new Skill(MobEffects.SPEED, SkillEffectsGameTests::artoria, p -> FateTests.tap(p, ManaBurst.KEY.identifier(), 0, false),
			p -> Abilities.end(p, ManaBurst.KEY.identifier())));
	}

	@GameTest(maxTicks = TIMEOUT_TICKS)
	public void crestOfTheStarKeepsEveryPotion(GameTestHelper helper) {
		run(helper, new Skill(MobEffects.SPEED, SkillEffectsGameTests::altera, p -> FateTests.tap(p, CrestOfTheStar.KEY.identifier(), 0, false),
			p -> Abilities.end(p, CrestOfTheStar.KEY.identifier())));
	}

	@GameTest(maxTicks = TIMEOUT_TICKS)
	public void naturalBodyRegenerationKeepsEveryPotion(GameTestHelper helper) {
		run(helper, naturalBody(MobEffects.REGENERATION));
	}

	@GameTest(maxTicks = TIMEOUT_TICKS)
	public void naturalBodyStrengthKeepsEveryPotion(GameTestHelper helper) {
		run(helper, naturalBody(MobEffects.STRENGTH));
	}

	@GameTest(maxTicks = TIMEOUT_TICKS)
	public void heldAvalonKeepsEveryPotion(GameTestHelper helper) {
		run(helper, new Skill(MobEffects.REGENERATION, SkillEffectsGameTests::artoria, Avalon::begin, Avalon::end));
	}

	@GameTest(maxTicks = TIMEOUT_TICKS)
	public void carriedAvalonKeepsEveryPotion(GameTestHelper helper) {
		run(helper, new Skill(MobEffects.REGENERATION, SkillEffectsGameTests::artoria, p -> {
			p.getInventory().add(new ItemStack(FateWeapons.AVALON));
			Avalon.passive(List.of(p));
		}, p -> {
			p.getInventory().clearContent();
			Avalon.passive(List.of(p));
		}));
	}

	private static void run(GameTestHelper helper, Skill skill) {
		MobEffectInstance pilot = pilot(helper, skill);
		int amplifier = pilot.getAmplifier();
		int ticks = pilot.getDuration();
		List<Scenario> scenarios = new ArrayList<>();
		for (boolean before : new boolean[] {true, false}) {
			for (int strength = -1; strength <= 1; strength++) {
				for (boolean longer : new boolean[] {true, false}) {
					// A weaker potion needs a level below the skill's; vanilla ignores a later one that is no stronger and shorter.
					boolean ignored = !before && strength <= 0 && !longer;
					if (amplifier + strength >= 0 && !ignored) {
						scenarios.add(new Scenario(before, strength, longer));
					}
				}
			}
		}
		List<ServerPlayer> players = new ArrayList<>();
		for (Scenario scenario : scenarios) {
			ServerPlayer player = skill.make().apply(helper);
			players.add(player);
			if (scenario.before()) {
				player.addEffect(potion(skill, scenario, amplifier, ticks));
			}
			skill.start().accept(player);
		}
		helper.onEachTick(() -> players.forEach(ServerPlayer::baseTick));
		helper.runAfterDelay(AFTER_TICK, () -> {
			for (int i = 0; i < scenarios.size(); i++) {
				if (!scenarios.get(i).before()) {
					players.get(i).addEffect(potion(skill, scenarios.get(i), amplifier, ticks));
				}
			}
		});
		helper.runAfterDelay(CANCEL_TICK, () -> {
			for (int i = 0; i < scenarios.size(); i++) {
				Scenario scenario = scenarios.get(i);
				ServerPlayer player = players.get(i);
				skill.end().accept(player);
				int length = scenario.longer() ? ticks * LONGER_FACTOR : ticks / SHORTER_DIVISOR;
				int expected = length - (scenario.before() ? CANCEL_TICK : CANCEL_TICK - AFTER_TICK);
				List<MobEffectInstance> chain = chain(player, skill.effect());
				check(helper, scenario + ": only the potion is left, " + chain, chain.size() == 1 && chain.get(0).getAmplifier() == amplifier + scenario.strength()
					&& Math.abs(chain.get(0).getDuration() - expected) <= TOLERANCE_TICKS);
			}
			helper.succeed();
		});
	}

	private static MobEffectInstance pilot(GameTestHelper helper, Skill skill) {
		ServerPlayer player = skill.make().apply(helper);
		skill.start().accept(player);
		MobEffectInstance given = new MobEffectInstance(player.getEffect(skill.effect()));
		skill.end().accept(player);
		return given;
	}

	private static MobEffectInstance potion(Skill skill, Scenario scenario, int amplifier, int ticks) {
		return new MobEffectInstance(skill.effect(), scenario.longer() ? ticks * LONGER_FACTOR : ticks / SHORTER_DIVISOR, amplifier + scenario.strength());
	}

	private static List<MobEffectInstance> chain(ServerPlayer player, Holder<MobEffect> effect) {
		List<MobEffectInstance> chain = new ArrayList<>();
		for (MobEffectInstance node = player.getEffect(effect); node != null; node = ((MobEffectInstanceAccessor) node).alayacore$hiddenEffect()) {
			chain.add(node);
		}
		return chain;
	}

	private static Skill swordBody(Holder<MobEffect> effect) {
		return new Skill(effect, helper -> {
			clear(helper);
			return FateTests.servant(helper, START, FateTests.MANA, FateServants.EMIYA);
		}, p -> FateTests.tap(p, SwordBody.KEY.identifier(), 0, false), p -> Abilities.end(p, SwordBody.KEY.identifier()));
	}

	private static Skill naturalBody(Holder<MobEffect> effect) {
		return new Skill(effect, SkillEffectsGameTests::altera, Altera::tickPassive, p -> {
			p.setAttached(ServantStorage.CHOICE, ServantChoice.human());
			Altera.tickPassive(p);
		});
	}

	private static ServerPlayer artoria(GameTestHelper helper) {
		clear(helper);
		ServerPlayer player = FateTests.servant(helper, START, FateTests.MANA, FateServants.ARTORIA);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FateWeapons.CALIBURN));
		return player;
	}

	private static ServerPlayer altera(GameTestHelper helper) {
		clear(helper);
		return FateTests.servant(helper, START, FateTests.ALTERA_MANA, FateServants.ALTERA);
	}

	private static void clear(GameTestHelper helper) {
		for (BlockPos pos : BlockPos.betweenClosed(START.offset(-2, -1, -2), START.offset(4, 4, 4))) {
			helper.setBlock(pos, Blocks.AIR);
		}
	}
}
