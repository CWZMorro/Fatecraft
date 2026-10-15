package io.github.cwzmorro.fatecraft.vault;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

/** A player's Gate of Babylon, Enkidu and sorting settings, set in the vault screen; Shift and the ability change the tier. */
public record VaultSettings(int thrownSection, List<Integer> volleys, int tier, int gatesPerSecond, int returnSeconds, int chains, VaultSort sort) {
	public static final int TIERS = 4;
	private static final List<String> TIER_NAMES = List.of("screen.fatecraft.vault.volley.1", "screen.fatecraft.vault.volley.2", "screen.fatecraft.vault.volley.3",
		"screen.fatecraft.vault.volley.4");
	public static final int MIN = 1;
	public static final int MAX_VOLLEY = 9999;
	public static final int MAX_GATES_PER_SECOND = 999;
	public static final int MAX_RETURN_SECONDS = 3600;
	public static final int MAX_CHAINS = 32;
	public static final VaultSettings DEFAULTS = defaults(VaultConfig.DEFAULTS);
	public static final Codec<VaultSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.intRange(0, Vault.SECTIONS - 1).fieldOf("thrown_section").forGetter(VaultSettings::thrownSection),
		Codec.intRange(MIN, MAX_VOLLEY).listOf(TIERS, TIERS).fieldOf("volleys").forGetter(VaultSettings::volleys),
		Codec.intRange(0, TIERS - 1).optionalFieldOf("tier", DEFAULTS.tier).forGetter(VaultSettings::tier),
		Codec.intRange(MIN, MAX_GATES_PER_SECOND).fieldOf("gates_per_second").forGetter(VaultSettings::gatesPerSecond),
		Codec.intRange(MIN, MAX_RETURN_SECONDS).optionalFieldOf("return_seconds")
			.xmap(value -> value.orElseGet(() -> defaults().returnSeconds()), Optional::of).forGetter(VaultSettings::returnSeconds),
		Codec.intRange(MIN, MAX_CHAINS).optionalFieldOf("chains", DEFAULTS.chains).forGetter(VaultSettings::chains),
		VaultSort.CODEC.fieldOf("sort").forGetter(VaultSettings::sort)
	).apply(i, VaultSettings::new));
	public static final StreamCodec<ByteBuf, VaultSettings> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, VaultSettings::thrownSection,
		ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(TIERS)), VaultSettings::volleys,
		ByteBufCodecs.VAR_INT, VaultSettings::tier,
		ByteBufCodecs.VAR_INT, VaultSettings::gatesPerSecond,
		ByteBufCodecs.VAR_INT, VaultSettings::returnSeconds,
		ByteBufCodecs.VAR_INT, VaultSettings::chains,
		VaultSort.STREAM_CODEC, VaultSettings::sort,
		VaultSettings::new
	);

	public VaultSettings {
		volleys = List.copyOf(volleys);
	}

	public static VaultSettings defaults() {
		return defaults(FateConfig.vault());
	}

	private static VaultSettings defaults(VaultConfig config) {
		return new VaultSettings(0, config.volleys(), 0, config.gatesPerSecond(), config.returnSeconds(), config.chains(), VaultSort.NAME);
	}

	public VaultSettings withThrownSection(int section) {
		return new VaultSettings(section, this.volleys, this.tier, this.gatesPerSecond, this.returnSeconds, this.chains, this.sort);
	}

	public VaultSettings withVolleyChanged(int tier, int by) {
		List<Integer> volleys = new ArrayList<>(this.volleys);
		volleys.set(tier, volleys.get(tier) + by);
		return new VaultSettings(this.thrownSection, volleys, this.tier, this.gatesPerSecond, this.returnSeconds, this.chains, this.sort);
	}

	public VaultSettings withNextTier() {
		return new VaultSettings(this.thrownSection, this.volleys, (this.tier + 1) % TIERS, this.gatesPerSecond, this.returnSeconds, this.chains, this.sort);
	}

	public VaultSettings withGatesPerSecond(int gatesPerSecond) {
		return new VaultSettings(this.thrownSection, this.volleys, this.tier, gatesPerSecond, this.returnSeconds, this.chains, this.sort);
	}

	public VaultSettings withReturnSeconds(int returnSeconds) {
		return new VaultSettings(this.thrownSection, this.volleys, this.tier, this.gatesPerSecond, returnSeconds, this.chains, this.sort);
	}

	public VaultSettings withChains(int chains) {
		return new VaultSettings(this.thrownSection, this.volleys, this.tier, this.gatesPerSecond, this.returnSeconds, chains, this.sort);
	}

	public VaultSettings withSort(VaultSort sort) {
		return new VaultSettings(this.thrownSection, this.volleys, this.tier, this.gatesPerSecond, this.returnSeconds, this.chains, sort);
	}

	public static Component tierName(int tier) {
		return Component.translatable(TIER_NAMES.get(tier));
	}

	public int volley() {
		return this.volleys.get(this.tier);
	}

	public int returnTicks() {
		return this.returnSeconds * SharedConstants.TICKS_PER_SECOND;
	}

	public VaultSettings clamped() {
		List<Integer> volleys = new ArrayList<>(TIERS);
		for (int tier = 0; tier < TIERS; tier++) {
			volleys.add(Mth.clamp(tier < this.volleys.size() ? this.volleys.get(tier) : defaults().volleys.get(tier), MIN, MAX_VOLLEY));
		}
		return new VaultSettings(Vault.sectionIndex(this.thrownSection), List.copyOf(volleys), Mth.clamp(this.tier, 0, TIERS - 1),
			Mth.clamp(this.gatesPerSecond, MIN, MAX_GATES_PER_SECOND), Mth.clamp(this.returnSeconds, MIN, MAX_RETURN_SECONDS), Mth.clamp(this.chains, MIN, MAX_CHAINS),
			this.sort);
	}
}
