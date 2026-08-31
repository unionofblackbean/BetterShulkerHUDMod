package bettershulkerhud.mixin;

import bettershulkerhud.gui.BundlePanelRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws after the player inventory's recipe controls, which otherwise cover the HUD. */
@Mixin(InventoryScreen.class)
public abstract class PlayerInventoryScreenMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(
            GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        BundlePanelRenderer.renderOverlay(
                graphics, (AbstractContainerScreen<?>) (Object) this, mouseX, mouseY);
    }
}
