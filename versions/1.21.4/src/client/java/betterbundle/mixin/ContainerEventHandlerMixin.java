package bettershulkerhud.mixin;

import bettershulkerhud.gui.BundlePanelRenderer;
import bettershulkerhud.gui.BundlePanelInteraction;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ContainerEventHandler.class)
public interface ContainerEventHandlerMixin {
    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void onCharTyped(
            char codePoint, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        Object handler = this;
        if (!(handler instanceof AbstractContainerScreen<?>)) return;
        if (BundlePanelRenderer.onCharTyped(codePoint)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void onMouseScrolled(
            double mouseX, double mouseY, double scrollX, double scrollY,
            CallbackInfoReturnable<Boolean> cir) {
        Object handler = this;
        if (!(handler instanceof AbstractContainerScreen<?> screen)
                || !BundlePanelRenderer.isEffectivelyVisible()) return;
        if (BundlePanelInteraction.handleScroll(
                mouseX, mouseY, scrollY,
                screen.leftPos, screen.topPos, screen.imageHeight)) {
            cir.setReturnValue(true);
        }
    }
}
