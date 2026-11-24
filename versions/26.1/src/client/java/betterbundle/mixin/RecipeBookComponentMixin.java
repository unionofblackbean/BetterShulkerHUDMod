package bettershulkerhud.mixin;

import bettershulkerhud.compat.RecipePreparationController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes boxed ingredients visible to 2x2 and 3x3 recipe books, then supplies them safely. */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
    @Shadow @Final protected RecipeBookMenu menu;
    @Shadow protected Minecraft minecraft;

    @Inject(method = "tryPlaceRecipe", at = @At("HEAD"), cancellable = true)
    private void supplyBeforePlacement(
            RecipeCollection collection, RecipeDisplayId recipeId, boolean max,
            CallbackInfoReturnable<Boolean> cir) {
        if (max || minecraft.player == null || minecraft.player.containerMenu != menu
                || !(minecraft.screen instanceof InventoryScreen
                || minecraft.screen instanceof CraftingScreen)
                || !(menu instanceof InventoryMenu || menu instanceof CraftingMenu)) return;
        for (RecipeDisplayEntry entry : collection.getRecipes()) {
            if (!entry.id().equals(recipeId) || entry.craftingRequirements().isEmpty()) continue;
            if (RecipePreparationController.begin(
                    recipeId, entry.craftingRequirements().get())) {
                cir.setReturnValue(true);
            }
            return;
        }
    }
}
