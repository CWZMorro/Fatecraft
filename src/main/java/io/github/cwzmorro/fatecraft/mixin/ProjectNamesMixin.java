package io.github.cwzmorro.fatecraft.mixin;

import io.github.cwzmorro.alayacore.ability.Ability;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.trace.Project;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// A Project ability's labels come from its item's translated name, so other mods' swords read right too.
@Mixin(Ability.class)
public abstract class ProjectNamesMixin {
	@Inject(method = "name", at = @At("HEAD"), cancellable = true, remap = false)
	private static void fatecraft$name(Identifier id, CallbackInfoReturnable<Component> cir) {
		Item item = Project.item(id);
		if (item != null) {
			cir.setReturnValue(Component.translatable("ability.fatecraft.project", new ItemStack(item).getHoverName()));
		}
	}

	@Inject(method = "modeName", at = @At("HEAD"), cancellable = true, remap = false)
	private static void fatecraft$mode(Identifier id, int mode, CallbackInfoReturnable<Component> cir) {
		if (Project.item(id) != null) {
			cir.setReturnValue(Component.translatable(Project.modeName(mode)));
		}
	}

	@Inject(method = "description", at = @At("HEAD"), cancellable = true, remap = false)
	private static void fatecraft$description(Identifier id, CallbackInfoReturnable<List<Component>> cir) {
		if (Project.item(id) != null) {
			cir.setReturnValue(List.of(Component.translatable("ability.fatecraft.project.description.1"),
				Component.translatable("ability.fatecraft.project.description.2", FateConfig.trace().volley())));
		}
	}
}
