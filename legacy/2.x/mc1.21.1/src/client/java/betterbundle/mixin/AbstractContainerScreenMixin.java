package bettershulkerhud.mixin;

import bettershulkerhud.compat.QuickShulkerExtractionController;
import bettershulkerhud.gui.BundleCategory;
import bettershulkerhud.gui.BundlePanelInteraction;
import bettershulkerhud.gui.BundlePanelRenderer;
import bettershulkerhud.event.InventoryDragStoreController;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;
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
            double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) return;

        if (BundlePanelRenderer.handleScrollBarClick(
                mouseX, mouseY, button,
                screen.leftPos, screen.topPos, screen.imageHeight)) {
            cir.setReturnValue(true);
            return;
        }

        if (InventoryDragStoreController.capture(screen, hoveredSlot, button)) {
            cir.setReturnValue(true);
            return;
        }

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

        if (!BundlePanelRenderer.isEffectivelyVisible()) return;
        int modifiers = Screen.hasShiftDown() ? GLFW.GLFW_MOD_SHIFT : 0;
        if (BundlePanelInteraction.isInsidePanel(
                mouseX, mouseY, screen.leftPos, screen.topPos, screen.imageHeight)
                 && BundlePanelInteraction.handlePanelClick(
                mouseX, mouseY, button, modifiers,
                screen.leftPos, screen.topPos, screen.imageHeight)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void onMouseDragged(
            double mouseX, double mouseY, int button, double dragX, double dragY,
            CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (BundlePanelRenderer.handleScrollBarDrag(
                mouseY, button,
                screen.leftPos, screen.topPos, screen.imageHeight)) {
            cir.setReturnValue(true);
            return;
        }
        if (BundlePanelRenderer.handleToggleButtonDrag(
                mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            return;
        }
        if (InventoryDragStoreController.capture(screen, hoveredSlot, button)) {
            cir.setReturnValue(true);
            return;
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void onMouseReleased(
            double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (BundlePanelRenderer.handleScrollBarRelease(button)) {
            cir.setReturnValue(true);
            return;
        }
        if (InventoryDragStoreController.finishGesture()) {
            cir.setReturnValue(true);
            return;
        }
        if (BundlePanelRenderer.handleToggleButtonRelease(
                mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            return;
        }
        if (!BundlePanelRenderer.isEffectivelyVisible()) return;
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (!screen.getMenu().getCarried().isEmpty()
                && BundlePanelRenderer.isInsidePanelBounds(
                mouseX, mouseY, screen.leftPos, screen.topPos, screen.imageHeight)) {
            QuickShulkerExtractionController.requestStoreCarried(screen);
            cir.setReturnValue(true);
            return;
        }
        if (BundlePanelInteraction.isInsidePanel(
                mouseX, mouseY, screen.leftPos, screen.topPos, screen.imageHeight)) {
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
