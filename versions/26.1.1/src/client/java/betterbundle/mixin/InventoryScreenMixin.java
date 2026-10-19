package bettershulkerhud.mixin;

import bettershulkerhud.gui.BundlePanelRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class InventoryScreenMixin {

    @Inject(method = "extractContents", at = @At("TAIL"))
    private void onExtractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        if (self instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) return;
        BundlePanelRenderer.render(graphics,
                self.leftPos,
                self.topPos,
                self.imageHeight,
                mouseX, mouseY);

        // Highlight the source shulker for the hovered HUD item.
        int inventorySlot = BundlePanelRenderer.getHoveredShulkerInventorySlot();
        if (inventorySlot >= 0) {
            Slot slot = betterShulkerHud$findPlayerInventorySlot(self, inventorySlot);
            if (slot != null && slot.hasItem()) {
                int sx = self.leftPos + slot.x;
                int sy = self.topPos + slot.y;
                var pose = graphics.pose();
                pose.pushMatrix();
                pose.translate(sx + 8, sy + 8);
                float scale = 19f / 16f;
                pose.scale(scale, scale);
                pose.translate(-8, -8);
                graphics.item(slot.getItem(), 0, 0);
                pose.popMatrix();
            }
        }

        BundlePanelRenderer.renderToggleButton(
                graphics,
                BundlePanelRenderer.toggleX(self.leftPos, self.imageWidth),
                BundlePanelRenderer.toggleY(self.topPos),
                mouseX,
                mouseY);

        if (self instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
            BundlePanelRenderer.renderAdjustControls(graphics, mouseX, mouseY);
        }
    }

    @Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true)
    private void betterShulkerHud$hideCursorStagingSlot(
            GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY,
            CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        if (self instanceof InventoryScreen
                && betterShulkerHud$isCursorStagingSlot(slot)) {
            ci.cancel();
        }
    }

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void betterShulkerHud$hideCursorStagingTooltip(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        if (self instanceof InventoryScreen
                && betterShulkerHud$isCursorStagingSlot(self.hoveredSlot)) {
            ci.cancel();
        }
    }

    @Inject(method = "extractCarriedItem", at = @At("TAIL"))
    private void betterShulkerHud$renderCursorTransferPreview(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if ((Object) this instanceof InventoryScreen) {
            BundlePanelRenderer.renderCursorTransferPreview(graphics, mouseX, mouseY);
        }
    }

    @Unique
    private static Slot betterShulkerHud$findPlayerInventorySlot(
            AbstractContainerScreen<?> screen, int inventorySlot) {
        if (net.minecraft.client.Minecraft.getInstance().player == null) return null;
        var inventory = net.minecraft.client.Minecraft.getInstance().player.getInventory();
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container == inventory && slot.getContainerSlot() == inventorySlot) {
                return slot;
            }
        }
        return null;
    }

    @Unique
    private static boolean betterShulkerHud$isCursorStagingSlot(Slot slot) {
        int stagingInventorySlot = bettershulkerhud.compat
                .QuickShulkerExtractionController.getCursorStagingInventorySlot();
        if (slot == null || stagingInventorySlot < 0
                || net.minecraft.client.Minecraft.getInstance().player == null) return false;
        var inventory = net.minecraft.client.Minecraft.getInstance().player.getInventory();
        return slot.container == inventory
                && slot.getContainerSlot() == stagingInventorySlot;
    }
}
