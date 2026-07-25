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
        graphics.fill(x, y, x + 20, y + 20, hovered ? 0xFFD6D6D6 : 0xFFC6C6C6);
        graphics.fill(x, y, x + 19, y + 1, 0xFFFFFFFF);
        graphics.fill(x, y, x + 1, y + 19, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 19, x + 20, y + 20, 0xFF373737);
        graphics.fill(x + 19, y + 1, x + 20, y + 20, 0xFF373737);
        if (BundlePanelRenderer.visible) {
            graphics.fill(x + 3, y + 17, x + 17, y + 18, 0xFF404040);
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
