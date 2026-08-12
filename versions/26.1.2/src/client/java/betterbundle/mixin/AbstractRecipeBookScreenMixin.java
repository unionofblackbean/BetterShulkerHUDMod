package bettershulkerhud.mixin;

import bettershulkerhud.config.Configs;
import bettershulkerhud.gui.BundleCategory;
import bettershulkerhud.gui.BundlePanelInteraction;
import bettershulkerhud.gui.BundlePanelRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractRecipeBookScreenMixin {
    @Shadow protected abstract ScreenPosition getRecipeBookButtonPosition();

    @Unique private ImageButton betterShulkerHud$recipeBookButton;

    @Inject(method = "initButton", at = @At("RETURN"))
    private void captureRecipeBookButton(CallbackInfo ci) {
        AbstractRecipeBookScreen<?> self = (AbstractRecipeBookScreen<?>) (Object) this;
        ScreenPosition expected = getRecipeBookButtonPosition();
        betterShulkerHud$recipeBookButton = null;
        for (var child : self.children()) {
            if (child instanceof ImageButton button
                    && button.getX() == expected.x()
                    && button.getY() == expected.y()
                    && button.getWidth() == 20
                    && button.getHeight() == 18) {
                betterShulkerHud$recipeBookButton = button;
                break;
            }
        }
        betterShulkerHud$syncRecipeBookButtonVisibility();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void syncRecipeBookButtonVisibility(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        betterShulkerHud$syncRecipeBookButtonVisibility();
    }

    @Unique
    private void betterShulkerHud$syncRecipeBookButtonVisibility() {
        if (betterShulkerHud$recipeBookButton == null) return;
        boolean visible = !(((Object) this) instanceof InventoryScreen)
                || !Configs.Features.HIDE_RECIPE_BOOK_BUTTON.getBooleanValue();
        betterShulkerHud$recipeBookButton.visible = visible;
        if (!visible && betterShulkerHud$recipeBookButton.isFocused()) {
            ((AbstractRecipeBookScreen<?>) (Object) this).clearFocus();
        }
    }

    @Inject(method = "lambda$initButton$0", at = @At("TAIL"))
    private void clearRecipeBookButtonFocus(CallbackInfo ci) {
        // Space is the drag-store modifier, so the recipe button must not retain keyboard focus.
        ((AbstractRecipeBookScreen<?>) (Object) this).clearFocus();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        double mouseX = event.x();
        double mouseY = event.y();

        if (self instanceof InventoryScreen) {
            if (BundlePanelRenderer.handleAdjustModeClick(
                    mouseX, mouseY, event.button(),
                    self.leftPos, self.topPos, self.imageWidth)) {
                cir.setReturnValue(true);
                return;
            }

            // Toggle button
            if (BundlePanelRenderer.handleToggleButtonClick(
                    mouseX, mouseY, event.button(),
                    self.leftPos, self.topPos, self.imageWidth)) {
                cir.setReturnValue(true);
                return;
            }
        }


        if (BundlePanelRenderer.isMinimizeButtonHovered(
                mouseX, mouseY, self.leftPos, self.topPos, self.imageHeight)) {
            BundlePanelRenderer.playButtonClick();
            BundlePanelRenderer.minimizeCurrentPreview();
            cir.setReturnValue(true);
            return;
        }

        // Category button
        if (BundlePanelRenderer.isEffectivelyVisible()) {
            BundleCategory cat = BundlePanelRenderer.getCategoryAt(mouseX, mouseY, self.leftPos, self.topPos, self.imageHeight);
            if (cat != null) {
                BundlePanelRenderer.playButtonClick();
                BundlePanelRenderer.currentCategory = cat;
                BundlePanelRenderer.searchQuery = "";
                BundlePanelRenderer.scrollToTop();
                cir.setReturnValue(true);
                return;
            }
        }

        // Search bar click
        if (BundlePanelRenderer.isEffectivelyVisible()
                && BundlePanelRenderer.isInsideSearchBar(mouseX, mouseY, self.leftPos, self.topPos, self.imageHeight)) {
            BundlePanelRenderer.searchFocused = true;
            cir.setReturnValue(true);
            return;
        }

        BundlePanelRenderer.searchFocused = false;
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void onCharTyped(CharacterEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (BundlePanelRenderer.onCharTyped(event.codepoint())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (BundlePanelRenderer.onSearchKeyPress(event.key(), event.modifiers())) {
            cir.setReturnValue(true);
        }
    }
}
