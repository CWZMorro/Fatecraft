package io.github.cwzmorro.fatecraft.client.weapon;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What features add to a held weapon mesh's pose, each from its own item components, set fresh every frame by the
 * sources registered while the client starts ({@link #register}). {@link WeaponMeshRenderer} draws whatever they set and names
 * no feature.
 */
public final class HeldPose {
	private static final List<Source> SOURCES = new ArrayList<>();
	private boolean posed;
	private float tipDown;
	private boolean raised;
	private @Nullable Overlay overlay;
	private float draw;
	private @Nullable ItemStack nocked;

	HeldPose() {
	}

	@FunctionalInterface
	public interface Source {
		void pose(ItemStack stack, double time, HeldPose pose);
	}

	@FunctionalInterface
	public interface Overlay {
		void draw(SubmitNodeCollector collector, PoseStack poseStack, WeaponMesh mesh);
	}

	public static void register(Source source) {
		SOURCES.add(source);
	}

	public void tipDown(float fraction) {
		this.posed = true;
		this.tipDown = fraction;
	}

	public void raise() {
		this.posed = true;
		this.raised = true;
	}

	public void overlay(Overlay overlay) {
		this.posed = true;
		this.overlay = overlay;
	}

	public void nock(float draw, ItemStack sword) {
		this.posed = true;
		this.draw = draw;
		this.nocked = sword;
	}

	boolean apply(ItemStack stack, double time) {
		this.posed = false;
		this.tipDown = 0.0F;
		this.raised = false;
		this.overlay = null;
		this.draw = 0.0F;
		this.nocked = null;
		for (Source source : SOURCES) {
			source.pose(stack, time, this);
		}
		return this.posed;
	}

	float tipDown() {
		return this.tipDown;
	}

	boolean raised() {
		return this.raised;
	}

	@Nullable Overlay overlay() {
		return this.overlay;
	}

	float draw() {
		return this.draw;
	}

	@Nullable ItemStack nocked() {
		return this.nocked;
	}
}
