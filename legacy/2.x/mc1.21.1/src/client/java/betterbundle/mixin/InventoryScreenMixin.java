package bettershulkerhud.mixin;

import bettershulkerhud.gui.BundlePanelRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
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

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(
            GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        if (self instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) return;
        if (self instanceof InventoryScreen) return;
        BundlePanelRenderer.renderOverlay(graphics, self, mouseX, mouseY);
    }

    @Unique
    private static void betterShulkerHud$renderToggleButton(
            GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + BundlePanelRenderer.TOGGLE_WIDTH
                && mouseY >= y && mouseY < y + BundlePanelRenderer.TOGGLE_HEIGHT;
        graphics.blitSprite(
                hovered ? BundlePanelRenderer.RECIPE_BUTTON_HIGHLIGHTED_SPRITE
                        : BundlePanelRenderer.RECIPE_BUTTON_SPRITE,
                x, y, BundlePanelRenderer.TOGGLE_WIDTH, BundlePanelRenderer.TOGGLE_HEIGHT);
        ItemStack icon = new ItemStack(Items.SHULKER_BOX);
        graphics.renderItem(icon, x + 2, y + 1);
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
