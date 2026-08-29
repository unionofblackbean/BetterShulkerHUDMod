package bettershulkerhud.mixin;

import bettershulkerhud.gui.BundlePanelRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class InventoryScreenMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(
            GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        if (self instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) return;
        if (self instanceof AbstractRecipeBookScreen) return;
        BundlePanelRenderer.renderOverlay(graphics, self, mouseX, mouseY);
    }

}
