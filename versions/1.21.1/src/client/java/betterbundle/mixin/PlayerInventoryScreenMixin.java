package bettershulkerhud.mixin;

import bettershulkerhud.config.Configs;
import bettershulkerhud.gui.BundlePanelRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws after the player inventory's recipe controls, which otherwise cover the HUD. */
@Mixin(InventoryScreen.class)
public abstract class PlayerInventoryScreenMixin {
    @Unique private ImageButton betterShulkerHud$recipeBookButton;

    @Inject(method = "init", at = @At("RETURN"))
    private void captureRecipeBookButton(CallbackInfo ci) {
        InventoryScreen self = (InventoryScreen) (Object) this;
        int expectedX = self.leftPos + 104;
        int expectedY = self.height / 2 - 22;
        betterShulkerHud$recipeBookButton = null;
        for (var child : self.children()) {
            if (child instanceof ImageButton button
                    && button.getX() == expectedX
                    && button.getY() == expectedY
                    && button.getWidth() == 20
                    && button.getHeight() == 18) {
                betterShulkerHud$recipeBookButton = button;
                break;
            }
        }
        betterShulkerHud$syncRecipeBookButtonVisibility();
    }

    @Unique
    private void betterShulkerHud$syncRecipeBookButtonVisibility() {
        if (betterShulkerHud$recipeBookButton == null) return;
        boolean visible = !Configs.Features.HIDE_RECIPE_BOOK_BUTTON.getBooleanValue();
        betterShulkerHud$recipeBookButton.visible = visible;
        if (!visible && betterShulkerHud$recipeBookButton.isFocused()) {
            ((InventoryScreen) (Object) this).clearFocus();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(
            GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        betterShulkerHud$syncRecipeBookButtonVisibility();
        BundlePanelRenderer.renderOverlay(
                graphics, (AbstractContainerScreen<?>) (Object) this, mouseX, mouseY);
    }
}
