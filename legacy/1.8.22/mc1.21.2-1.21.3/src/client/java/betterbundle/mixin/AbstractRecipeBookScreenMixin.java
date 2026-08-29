package bettershulkerhud.mixin;

import bettershulkerhud.gui.BundleCategory;
import bettershulkerhud.gui.BundlePanelRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractRecipeBookScreenMixin {
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
