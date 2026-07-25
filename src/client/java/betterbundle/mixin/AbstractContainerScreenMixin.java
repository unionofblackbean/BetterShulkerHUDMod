package bettershulkerhud.mixin;

import bettershulkerhud.compat.QuickShulkerExtractionController;
import bettershulkerhud.gui.BundleCategory;
import bettershulkerhud.gui.BundlePanelInteraction;
import bettershulkerhud.gui.BundlePanelRenderer;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(
            MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        double mouseX = event.x();
        double mouseY = event.y();

        // Recipe-book screens receive these controls in their more specific mixin.
        if (!(((Object) this) instanceof AbstractRecipeBookScreen)) {
            int toggleX = BundlePanelRenderer.toggleX(screen.leftPos, screen.imageWidth);
            int toggleY = BundlePanelRenderer.toggleY(screen.topPos);
            if (mouseX >= toggleX && mouseX < toggleX + 20
                    && mouseY >= toggleY && mouseY < toggleY + 20) {
                BundlePanelRenderer.toggleVisible();
                cir.setReturnValue(true);
                return;
            }

            if (BundlePanelRenderer.isMinimizeButtonHovered(
                    mouseX, mouseY, screen.leftPos, screen.topPos, screen.imageHeight)) {
                BundlePanelRenderer.toggleVisible();
                cir.setReturnValue(true);
                return;
            }

            if (BundlePanelRenderer.isEffectivelyVisible()) {
                BundleCategory category = BundlePanelRenderer.getCategoryAt(
                        mouseX, mouseY, screen.leftPos, screen.topPos, screen.imageHeight);
                if (category != null) {
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

        if (!BundlePanelRenderer.isEffectivelyVisible()) return;
        if (BundlePanelInteraction.isInsidePanel(
                mouseX, mouseY, screen.leftPos, screen.topPos, screen.imageHeight)
                 && BundlePanelInteraction.handlePanelClick(
                mouseX, mouseY, event.button(), event.modifiers(),
                screen.leftPos, screen.topPos, screen.imageHeight)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void onMouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!BundlePanelRenderer.isEffectivelyVisible()) return;
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (!screen.getMenu().getCarried().isEmpty()
                && BundlePanelRenderer.isInsidePanelBounds(
                event.x(), event.y(), screen.leftPos, screen.topPos, screen.imageHeight)) {
            QuickShulkerExtractionController.requestStoreCarried(screen);
            cir.setReturnValue(true);
            return;
        }
        if (BundlePanelInteraction.isInsidePanel(
                event.x(), event.y(), screen.leftPos, screen.topPos, screen.imageHeight)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (BundlePanelRenderer.onSearchKeyPress(event.key(), event.modifiers())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void onMouseScrolled(
            double mouseX, double mouseY, double scrollX, double scrollY,
            CallbackInfoReturnable<Boolean> cir) {
        if (!BundlePanelRenderer.isEffectivelyVisible()) return;
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (BundlePanelInteraction.handleScroll(
                mouseX, mouseY, scrollY,
                screen.leftPos, screen.topPos, screen.imageHeight)) {
            cir.setReturnValue(true);
        }
    }
}
