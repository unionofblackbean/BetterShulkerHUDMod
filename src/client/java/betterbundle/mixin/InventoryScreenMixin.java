package bettershulkerhud.mixin;

import bettershulkerhud.gui.BundlePanelRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

        betterShulkerHud$renderToggleButton(
                graphics,
                BundlePanelRenderer.toggleX(self.leftPos, self.imageWidth),
                BundlePanelRenderer.toggleY(self.topPos),
                mouseX,
                mouseY);
    }

    @Unique
    private static void betterShulkerHud$renderToggleButton(
            GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20;
        graphics.fill(x + 1, y + 1, x + 21, y + 21, 0xFF71778F);
        graphics.fill(x, y, x + 20, y + 20, 0xFFF1F2F7);
        graphics.fill(x + 1, y + 1, x + 19, y + 19,
                hovered ? 0xEAA1A7BB : 0xE6AEB3C5);
        if (BundlePanelRenderer.visible) {
            graphics.fill(x + 3, y + 17, x + 17, y + 18, 0xFFF8F8FC);
        }
        ItemStack icon = new ItemStack(Items.SHULKER_BOX);
        graphics.item(icon, x + 2, y + 2);
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
}
