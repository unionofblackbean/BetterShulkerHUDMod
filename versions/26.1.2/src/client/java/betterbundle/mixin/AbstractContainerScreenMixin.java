package bettershulkerhud.mixin;

import bettershulkerhud.compat.QuickShulkerExtractionController;
import bettershulkerhud.event.InventoryDragStoreController;
import bettershulkerhud.gui.BundleCategory;
import bettershulkerhud.gui.BundlePanelInteraction;
import bettershulkerhud.gui.BundlePanelRenderer;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Shadow protected Slot hoveredSlot;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(
            MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) return;
        double mouseX = event.x();
        double mouseY = event.y();


        if (InventoryDragStoreController.capture(screen, hoveredSlot, event.button())) {
            cir.setReturnValue(true);
            return;
        }

        // Recipe-book screens receive these controls in their more specific mixin.
        if (!(((Object) this) instanceof AbstractRecipeBookScreen)) {
            if (BundlePanelRenderer.handleAdjustModeClick(
                    mouseX, mouseY, event.button(),
                    screen.leftPos, screen.topPos, screen.imageWidth)) {
                cir.setReturnValue(true);
                return;
            }
            if (BundlePanelRenderer.handleToggleButtonClick(
                    mouseX, mouseY, event.button(),
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

        if (!BundlePanelRenderer.isEffectivelyVisible()) return;
        if (BundlePanelInteraction.isInsidePanel(
                mouseX, mouseY, screen.leftPos, screen.topPos, screen.imageHeight)
                 && BundlePanelInteraction.handlePanelClick(
                mouseX, mouseY, event.button(), event.modifiers(),
                screen.leftPos, screen.topPos, screen.imageHeight)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void onMouseDragged(
            MouseButtonEvent event, double dragX, double dragY,
            CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (BundlePanelRenderer.handleToggleButtonDrag(
                event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
            return;
        }
        if (InventoryDragStoreController.capture(screen, hoveredSlot, event.button())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void onMouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (BundlePanelRenderer.handleToggleButtonRelease(
                event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
            return;
        }
        if (InventoryDragStoreController.finishGesture()) {
            cir.setReturnValue(true);
            return;
        }
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
