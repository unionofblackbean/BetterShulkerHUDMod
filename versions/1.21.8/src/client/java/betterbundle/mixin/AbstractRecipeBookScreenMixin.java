package bettershulkerhud.mixin;

import bettershulkerhud.config.Configs;
import bettershulkerhud.gui.BundleCategory;
import bettershulkerhud.gui.BundlePanelRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    @Inject(method = "render", at = @At("HEAD"))
    private void syncRecipeBookButtonVisibility(
            GuiGraphics graphics, int mouseX, int mouseY,
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

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(
            GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        BundlePanelRenderer.renderOverlay(
                graphics, (AbstractContainerScreen<?>) (Object) this, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(
            double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;

        if (BundlePanelRenderer.handleToggleButtonClick(
                mouseX, mouseY, button,
                screen.leftPos, screen.topPos, screen.imageWidth)) {
            cir.setReturnValue(true);
            return;
        }

        if (BundlePanelRenderer.isMinimizeButtonHovered(
                mouseX, mouseY, screen.leftPos, screen.topPos, screen.imageHeight)) {
            BundlePanelRenderer.playButtonClick();
            BundlePanelRenderer.minimizeCurrentPreview();
            cir.setReturnValue(true);
            return;
        }

        if (BundlePanelRenderer.isEffectivelyVisible()) {
            BundleCategory category = BundlePanelRenderer.getCategoryAt(
                    mouseX, mouseY, screen.leftPos, screen.topPos, screen.imageHeight);
            if (category != null) {
                BundlePanelRenderer.playButtonClick();
                BundlePanelRenderer.currentCategory = category;
                BundlePanelRenderer.searchQuery = "";
                BundlePanelRenderer.scrollToTop();
                cir.setReturnValue(true);
                return;
            }
        }

        if (BundlePanelRenderer.isEffectivelyVisible()
                && BundlePanelRenderer.isInsideSearchBar(
                mouseX, mouseY, screen.leftPos, screen.topPos, screen.imageHeight)) {
            BundlePanelRenderer.searchFocused = true;
            cir.setReturnValue(true);
            return;
        }
        BundlePanelRenderer.searchFocused = false;
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void onCharTyped(
            char codePoint, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (BundlePanelRenderer.onCharTyped(codePoint)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(
            int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        if (BundlePanelRenderer.onSearchKeyPress(keyCode, modifiers)) {
            cir.setReturnValue(true);
        }
    }
}
