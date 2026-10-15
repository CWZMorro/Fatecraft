package io.github.cwzmorro.fatecraft.client.weapon;

import io.github.cwzmorro.fatecraft.weapon.PairedThrow;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Kanshou and Bakuya's throw pose: shoulder keyframes in degrees on {@link PairedThrow}'s timeline, shared by the
 * third-person and first-person arms.
 */
public final class ThrowPose {
	private static final float NOT_THROWING = -1;
	// Keyframe ages in ticks: arms up and wide two ticks before the main sword leaves, both releases, the arms crossed, then rest.
	private static final int WIND_UP = PairedThrow.MAIN_RELEASE - 2;
	private static final int FOLLOW_THROUGH = 12;
	private static final float[] TICKS = {0, WIND_UP, PairedThrow.MAIN_RELEASE, PairedThrow.OFF_RELEASE, FOLLOW_THROUGH, PairedThrow.DURATION};
	// Each arm's shoulder (x, y, z) at each keyframe, as a player model's arm turns: x -90 points it forward, y below 0 turns it in across the chest, z above 0 raises it out to its side.
	private static final float[][] MAIN = {{0, 0, 0}, {20, 0, 130}, {-90, -30, 0}, {-85, -60, 0}, {-80, -55, 0}, {0, 0, 0}};
	private static final float[][] OFF = {{0, 0, 0}, {20, 0, 130}, {-60, 0, 50}, {-90, -30, 0}, {-80, -55, 0}, {0, 0, 0}};
	public static final float FORWARD_X = -90;
	private static final float[] HELD_FORWARD = {FORWARD_X, 0, 0};
	private static final Map<HumanoidRenderState, Float> AGES = new WeakHashMap<>();

	private ThrowPose() { }

	public static float age(LivingEntity thrower, float partialTick) {
		Long start = PairedThrow.startTick(thrower);
		return start == null ? NOT_THROWING : thrower.level().getGameTime() - start + partialTick;
	}

	public static void remember(HumanoidRenderState state, float age) {
		AGES.put(state, age);
	}

	public static float age(HumanoidRenderState state) {
		return AGES.getOrDefault(state, NOT_THROWING);
	}

	public static boolean active(float age) {
		return age >= 0 && age < TICKS[TICKS.length - 1];
	}

	public static float arm(float age, boolean main, int axis, boolean firstPerson) {
		float[][] frames = main ? MAIN : OFF;
		float[] values = new float[TICKS.length];
		for (int i = 0; i < values.length; i++) {
			boolean rest = i == 0 || i == values.length - 1;
			values[i] = firstPerson && rest ? HELD_FORWARD[axis] : frames[i][axis];
		}
		return sample(age, values);
	}

	private static float sample(float age, float[] values) {
		for (int i = 1; i < TICKS.length; i++) {
			if (age <= TICKS[i]) {
				return Mth.lerp(Mth.clamp((age - TICKS[i - 1]) / (TICKS[i] - TICKS[i - 1]), 0, 1), values[i - 1], values[i]);
			}
		}
		return values[values.length - 1];
	}
}
