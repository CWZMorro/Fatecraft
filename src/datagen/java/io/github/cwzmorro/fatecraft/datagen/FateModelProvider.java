package io.github.cwzmorro.fatecraft.datagen;

import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.client.weapon.BowRig;
import io.github.cwzmorro.fatecraft.client.weapon.WeaponMeshRenderer;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.properties.select.DisplayContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;

// Each weapon's mesh is a special model over a base model that gives its poses; only first-person hands get the full mesh.
final class FateModelProvider extends FabricModelProvider {
	private static final List<ItemDisplayContext> HANDS = List.of(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ItemDisplayContext.FIRST_PERSON_LEFT_HAND);
	private static final String LOW_SUFFIX = "_low";
	private static final String GLOW_SUFFIX = "_glow";
	private static final String MODEL_FOLDER = "item/";
	private static final String EA_RAISED = "ea_raised";
	// EMIYA's bow parts: the string, its centre at rest on the baked plane (a nocked sword overlaps the bow there), the fixed tips, the grip and the front.
	private static final BowRig EMIYA_BOW_RIG = new BowRig("Bow_String", new Vector3f(0.7274F, 0.5758F, 0.5F), -0.417F, 1.5686F, 0.49F, 0.405F);
	private static final WeaponMeshRenderer.EmptyHand AVALON_HAND = new WeaponMeshRenderer.EmptyHand(new Vector3f(0.12F, 0.2F, 0.72F), -90);
	private static final Look PLAIN = new Look(false, false, false, Optional.empty(), Optional.empty());
	private static final Look GLOWING = new Look(true, false, false, Optional.empty(), Optional.empty());
	private static final Look EA = new Look(true, true, false, Optional.empty(), Optional.empty());
	private static final Look PHOTON_RAY = new Look(true, false, true, Optional.empty(), Optional.empty());
	private static final Look EMIYA_BOW = new Look(false, false, false, Optional.of(EMIYA_BOW_RIG), Optional.empty());
	private static final Look AVALON_USED = new Look(false, false, false, Optional.empty(), Optional.of(AVALON_HAND));

	private record Look(boolean glows, boolean glowWhileSpinning, boolean wholeSpin, Optional<BowRig> bow,
		Optional<WeaponMeshRenderer.EmptyHand> emptyHand) {
	}

	FateModelProvider(FabricPackOutput output) {
		super(output);
	}

	@Override
	public void generateBlockStateModels(BlockModelGenerators blocks) {
	}

	@Override
	public void generateItemModels(ItemModelGenerators items) {
		items.itemModelOutput.accept(FateWeapons.EA, ItemModelUtils.conditional(ItemModelUtils.isUsingItem(), view("ea", EA_RAISED, EA), view("ea", "ea", EA)));
		items.itemModelOutput.accept(FateWeapons.EXCALIBUR, view("excalibur", "excalibur", GLOWING));
		items.itemModelOutput.accept(FateWeapons.AVALON, ItemModelUtils.conditional(ItemModelUtils.isUsingItem(),
			view("avalon", "avalon_used", AVALON_USED), view("avalon", "avalon", PLAIN)));
		items.itemModelOutput.accept(FateWeapons.KANSHOU, view("kanshou", "kanshou", PLAIN));
		items.itemModelOutput.accept(FateWeapons.BAKUYA, view("bakuya", "bakuya", PLAIN));
		items.itemModelOutput.accept(FateWeapons.EMIYA_BOW, ItemModelUtils.conditional(ItemModelUtils.isUsingItem(),
			view("emiya_bow", "emiya_bow_drawn", EMIYA_BOW), view("emiya_bow", "emiya_bow", EMIYA_BOW)));
		items.itemModelOutput.accept(FateWeapons.HRUNTING, view("hrunting", "hrunting", PLAIN));
		items.itemModelOutput.accept(FateWeapons.CALIBURN, view("caliburn", "excalibur", PLAIN));
		items.itemModelOutput.accept(FateWeapons.PHOTON_RAY, view("photon_ray", "photon_ray", PHOTON_RAY));
		items.itemModelOutput.accept(FateWeapons.BAB_ILU, view("bab_ilu", "bab_ilu", PLAIN));
		items.itemModelOutput.accept(FateWeapons.CALADBOLG, view("caladbolg", "excalibur", PLAIN));
	}

	private static ItemModel.Unbaked view(String name, String base, Look look) {
		return ItemModelUtils.select(new DisplayContext(), mesh(name + LOW_SUFFIX, name, base, look, false),
			ItemModelUtils.when(HANDS, mesh(name, name, base, look, true)));
	}

	private static ItemModel.Unbaked mesh(String mesh, String name, String base, Look look, boolean firstPerson) {
		Optional<Identifier> glow = look.glows() ? Optional.of(Fatecraft.id(WeaponMeshRenderer.TEXTURE_FOLDER + name + GLOW_SUFFIX)) : Optional.empty();
		return ItemModelUtils.specialModel(Fatecraft.id(MODEL_FOLDER + base),
			new WeaponMeshRenderer.Unbaked(Fatecraft.id(mesh), Fatecraft.id(WeaponMeshRenderer.TEXTURE_FOLDER + name), glow, firstPerson, look.glowWhileSpinning(),
				look.wholeSpin(), look.bow(), firstPerson ? look.emptyHand() : Optional.empty()));
	}
}
