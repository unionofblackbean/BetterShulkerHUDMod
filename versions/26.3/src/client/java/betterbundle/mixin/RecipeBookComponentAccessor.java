package bettershulkerhud.mixin;

import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RecipeBookComponent.class)
public interface RecipeBookComponentAccessor {
    @Invoker("tryPlaceRecipe")
    boolean betterShulkerHud$tryPlaceRecipe(
            RecipeCollection collection, RecipeDisplayId recipeId, boolean max);
}
