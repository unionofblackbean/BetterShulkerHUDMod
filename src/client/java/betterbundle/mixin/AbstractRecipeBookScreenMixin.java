package bettershulkerhud.mixin;

import bettershulkerhud.gui.BundleCategory;
import bettershulkerhud.gui.BundlePanelInteraction;
import bettershulkerhud.gui.BundlePanelRenderer;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractRecipeBookScreenMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        double mouseX = event.x();
        double mouseY = event.y();

        if (BundlePanelRenderer.isEnderChestButtonHovered(
                mouseX, mouseY, self.leftPos, self.topPos, self.imageWidth)) {
            bettershulkerhud.compat.QuickShulkerExtractionController
                    .requestEnderChestPreview(self);
            cir.setReturnValue(true);
            return;
        }

        // Toggle button
        int bx = BundlePanelRenderer.toggleX(self.leftPos, self.imageWidth);
        int by = BundlePanelRenderer.toggleY(self.topPos);
        if (mouseX >= bx && mouseX < bx + 20 && mouseY >= by && mouseY < by + 20) {
            BundlePanelRenderer.toggleVisible();
            cir.setReturnValue(true);
            return;
        }


        if (BundlePanelRenderer.isMinimizeButtonHovered(
                mouseX, mouseY, self.leftPos, self.topPos, self.imageHeight)) {
            BundlePanelRenderer.toggleVisible();
            cir.setReturnValue(true);
            return;
        }

        // Category button
        if (BundlePanelRenderer.isEffectivelyVisible()) {
            BundleCategory cat = BundlePanelRenderer.getCategoryAt(mouseX, mouseY, self.leftPos, self.topPos, self.imageHeight);
            if (cat != null) {
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
