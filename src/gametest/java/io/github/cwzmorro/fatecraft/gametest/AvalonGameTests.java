package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;

import com.mojang.serialization.JsonOps;
import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.api.AlayaServants;
import io.github.cwzmorro.alayacore.loot.LootTableGroups;
import io.github.cwzmorro.alayacore.mana.ManaEvents;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.WorldData;
import io.github.cwzmorro.fatecraft.artoria.Avalon;
import io.github.cwzmorro.fatecraft.artoria.AvalonLoot;
import io.github.cwzmorro.fatecraft.artoria.ManaBurst;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

public class AvalonGameTests {
	private static final BlockPos START = new BlockPos(2, 2, 2);
	private static final int BREAD_NUTRITION = 6;
	private static final float LETHAL = 1000;

	private static ServerPlayer artoria(GameTestHelper helper) {
		ServerPlayer player = FateTests.servant(helper, START, 100, FateServants.ARTORIA);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FateWeapons.AVALON));
		return player;
	}

	private static void use(GameTestHelper helper, ServerPlayer player, InteractionHand hand) {
		check(helper, "held use succeeds", FateWeapons.AVALON.use(helper.getLevel(), player, hand) == InteractionResult.CONSUME);
		check(helper, "Avalon active", Avalon.active(player));
	}

	@GameTest
	public void onlyArtoriaFreeWithoutSwordOrTrueNameBothHandsAndNoCooldown(GameTestHelper helper) {
		ServerPlayer other = FateTests.gilgamesh(helper, START, 100);
		other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FateWeapons.AVALON));
		check(helper, "other servants refused", FateWeapons.AVALON.use(helper.getLevel(), other, InteractionHand.MAIN_HAND) == InteractionResult.FAIL);
		ServerPlayer player = artoria(helper);
		AlayaMana.update(player, mana -> mana.withCurrent(0));
		for (InteractionHand hand : InteractionHand.values()) {
			player.setItemInHand(hand, new ItemStack(FateWeapons.AVALON));
			use(helper, player, hand);
			check(helper, "no mana charge", AlayaMana.available(player) == 0);
			player.releaseUsingItem();
			check(helper, "release ends defense", !Avalon.active(player));
			use(helper, player, hand);
			player.releaseUsingItem();
		}
		helper.succeed();
	}

	@GameTest
	public void allDamageTypesIncludingVoidAndModDamageExceptKill(GameTestHelper helper) {
		ServerPlayer player = artoria(helper);
		use(helper, player, InteractionHand.MAIN_HAND);
		float health = player.getHealth();
		var mob = FateTests.mob(helper, START.south());
		for (DamageSource source : List.of(player.damageSources().mobAttack(mob), player.damageSources().fall(),
			player.damageSources().onFire(), player.damageSources().fellOutOfWorld(), player.damageSources().explosion(mob, mob))) {
			check(helper, "damage rejected: " + source, !player.hurtServer(helper.getLevel(), source, LETHAL));
		}
		// Includes Fatecraft's damage types and any installed mod's registered damage type, without enumerating mods.
		helper.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).listElements().forEach(type -> {
			if (!type.is(DamageTypes.GENERIC_KILL)) {
				player.invulnerableTime = 0;
				check(helper, "all directions/type: " + type.key(), !player.hurtServer(helper.getLevel(), new DamageSource(type), LETHAL));
			}
		});
		check(helper, "health unchanged", player.getHealth() == health);
		check(helper, "only the holder is protected", mob.hurtServer(helper.getLevel(), mob.damageSources().generic(), 1));
		player.kill(helper.getLevel());
		check(helper, "/kill gets through", !player.isAlive());
		helper.succeed();
	}

	@GameTest
	public void regenerationLevelsOnlineGateAndFrozenActions(GameTestHelper helper) {
		ServerPlayer player = artoria(helper);
		ServerPlayer carrier = FateTests.gilgamesh(helper, START.east(), 100);
		carrier.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(FateWeapons.AVALON));
		Avalon.passive(List.of(carrier));
		check(helper, "no Artoria online, no passive", !carrier.hasEffect(MobEffects.REGENERATION));
		Avalon.passive(List.of(player, carrier));
		check(helper, "carrier gets Regen I", carrier.getEffect(MobEffects.REGENERATION).getAmplifier() == 0);
		Avalon.passive(List.of(carrier));
		check(helper, "Artoria leaving stops passive", !carrier.hasEffect(MobEffects.REGENERATION));
		use(helper, player, InteractionHand.MAIN_HAND);
		check(helper, "held Regen III", player.getEffect(MobEffects.REGENERATION).getAmplifier() == 2);
		Vec3 anchor = player.position();
		player.setDeltaMovement(1, 1, 1);
		player.move(MoverType.SELF, new Vec3(1, 1, 1));
		check(helper, "cannot move", player.position().equals(anchor) && player.getDeltaMovement().equals(Vec3.ZERO));
		var mob = FateTests.mob(helper, START.south());
		float health = mob.getHealth();
		player.attack(mob);
		check(helper, "cannot melee", mob.getHealth() == health);
		Abilities.press(player, ManaBurst.KEY.identifier(), 0, false);
		check(helper, "cannot use abilities", !Abilities.isCasting(player));
		check(helper, "mana channel gate", Abilities.isChanneling(player));
		AlayaMana.update(player, mana -> mana.withCurrent(10));
		AlayaMana.addCurrent(player, 10 * AlayaServants.manaRegenMultiplier(player));
		ManaEvents.onAte(player, BREAD_NUTRITION, Identifier.withDefaultNamespace("bread"));
		check(helper, "normal and food mana restoration paused", AlayaMana.available(player) == 10);
		player.releaseUsingItem();
		check(helper, "release restores action and ends the held effect, leaving the carried one it hid", !Abilities.isChanneling(player)
			&& player.getEffect(MobEffects.REGENERATION) != null
			&& player.getEffect(MobEffects.REGENERATION).getAmplifier() == FateConfig.artoria().avalonPassiveAmplifier());
		helper.succeed();
	}

	@GameTest
	public void savedWorldFlagOnceAndRealVoidResetPlusLootInEveryTarget(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		boolean previous = AvalonLoot.generated(server);
		try {
			AvalonLoot.lostToVoid(server);
			check(helper, "first world claim", AvalonLoot.claim(server));
			check(helper, "second refused", !AvalonLoot.claim(server));
			check(helper, "saved data is dirty and true", WorldData.of(server, AvalonLoot.GENERATED).isDirty() && AvalonLoot.generated(server));
			ItemEntity dropped = new ItemEntity(helper.getLevel(), 0, helper.getLevel().getMinY() - 100, 0, new ItemStack(FateWeapons.AVALON));
			dropped.tick();
			check(helper, "void destroys entity and clears flag", dropped.isRemoved() && !AvalonLoot.generated(server));
			check(helper, "can generate again", AvalonLoot.claim(server));
			var tables = LootTableGroups.tables(server.registryAccess(), AvalonLoot.GROUP);
			ResourceKey<LootTable> added = ResourceKey.create(Registries.LOOT_TABLE, FateTests.id("chests/avalon"));
			check(helper, "test datapack extends the group", tables.contains(added));
			check(helper, "all three default chests remain", tables.containsAll(List.of(BuiltInLootTables.SPAWN_BONUS_CHEST,
				BuiltInLootTables.TRIAL_CHAMBERS_REWARD, BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS)));
			for (var key : tables) {
				LootTable table = server.reloadableRegistries().getLootTable(key);
				String json = LootTable.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, server.reloadableRegistries().lookup()), table).getOrThrow().toString();
				check(helper, "modifier in " + key, json.contains("fatecraft:avalon_available") && json.contains("fatecraft:avalon"));
			}
			check(helper, "default chance is one in 10000", FateConfig.artoria().avalonLootChance() == 0.0001);
		} finally {
			WorldData.of(server, AvalonLoot.GENERATED).set(previous);
		}
		helper.succeed();
	}
}
