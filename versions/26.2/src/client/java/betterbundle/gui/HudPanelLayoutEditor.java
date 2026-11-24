package bettershulkerhud.gui;

import bettershulkerhud.compat.QuickShulkerExtractionController;
import bettershulkerhud.compat.RecipePreparationController;
import bettershulkerhud.config.Configs;
import bettershulkerhud.core.HudPanelLayout;
import bettershulkerhud.event.InventoryDragStoreController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** A position-only transaction: no storage requests, fake items, or inventory actions. */
public final class HudPanelLayoutEditor {
    private static AbstractContainerScreen<?> owner;
    private static int x = HudPanelLayout.AUTO;
    private static int y = HudPanelLayout.AUTO;
    private static boolean dragging;
    private static double grabX;
    private static double grabY;
    private static Component entryFailure = Component.empty();

    private HudPanelLayoutEditor() {}

    public static boolean start() {
        Minecraft client = Minecraft.getInstance();
        if (isActive()) return true;
        if (client.player == null || client.level == null) {
            return reject("message.better-shulker-hud.layout_no_world");
        }
        if (!client.player.containerMenu.getCarried().isEmpty()
                || QuickShulkerExtractionController.hasActiveOperation()
                || QuickShulkerExtractionController.isCursorTransferInProgress()
                || RecipePreparationController.isRecipeSupplyPending()
                || InventoryDragStoreController.peekNextValid(client) != null) {
            return reject("message.better-shulker-hud.layout_busy");
        }
        // Activate only after setScreen accepts the inventory screen.
        if (!(client.gui.screen() instanceof InventoryScreen)) client.gui.setScreen(new InventoryScreen(client.player));
        if (!(client.gui.screen() instanceof InventoryScreen screen)) {
            return reject("message.better-shulker-hud.layout_busy");
        }
        BundlePanelRenderer.resetToggleButtonInteraction();
        owner = screen;
        x = Configs.General.HUD_PANEL_X.getIntegerValue();
        y = Configs.General.HUD_PANEL_Y.getIntegerValue();
        dragging = false;
        entryFailure = Component.empty();
        return true;
    }

    private static boolean reject(String key) {
        entryFailure = Component.translatable(key);
        Minecraft.getInstance().gui.hud.setOverlayMessage(entryFailure, false);
        return false;
    }

    public static Component entryFailure() { return entryFailure; }
    public static boolean isActive() { return owner != null; }
    public static HudPanelLayout.Rect controlsBounds() {
        return isActive() ? new HudPanelLayout.Rect(0, 0, owner.width, 40)
                : new HudPanelLayout.Rect(0, 0, 0, 0);
    }
    public static int draftX() { return x; }
    public static int draftY() { return y; }

    public static void onScreenChanging(Screen next) {
        if (owner != null && next != owner) cancel();
    }

    public static void cancel() { owner = null; dragging = false; }

    public static void resetDraft() {
        if (!isActive()) return;
        x = HudPanelLayout.AUTO;
        y = HudPanelLayout.AUTO;
        dragging = false;
    }

    public static void commit() {
        if (!isActive()) return;
        Configs.General.HUD_PANEL_X.setIntegerValue(x);
        Configs.General.HUD_PANEL_Y.setIntegerValue(y);
        cancel();
        Configs.saveToFile();
    }

    private static HudPanelLayout.Rect bounds() {
        return BundlePanelRenderer.panelBounds(owner.leftPos, owner.topPos, owner.imageHeight);
    }

    private static int buttonWidth() { return Math.min(90, Math.max(36, (owner.width - 16) / 3)); }
    private static int buttonX(int index) { return (owner.width - (buttonWidth() * 3 + 8)) / 2 + index * (buttonWidth() + 4); }
    private static boolean overButton(double mx, double my, int index) {
        return mx >= buttonX(index) && mx < buttonX(index) + buttonWidth() && my >= 18 && my < 36;
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isActive()) return false;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        for (int i = 0; i < 3; i++) {
            if (!overButton(mouseX, mouseY, i)) continue;
            if (i == 0) commit();
            else if (i == 1) cancel();
            else resetDraft();
            return true;
        }
        HudPanelLayout.Rect bounds = bounds();
        if (bounds.contains(mouseX, mouseY)) {
            grabX = mouseX - bounds.x();
            grabY = mouseY - bounds.y();
            dragging = true;
        }
        return true;
    }

    public static boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (!isActive()) return false;
        if (dragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            var bounds = bounds();
            int px = HudPanelLayout.snap(mouseX - grabX, owner.width, bounds.width());
            int py = HudPanelLayout.snap(mouseY - grabY, owner.height, bounds.height());
            x = HudPanelLayout.normalize(px, owner.width, bounds.width());
            y = HudPanelLayout.normalize(py, owner.height, bounds.height());
        }
        return true;
    }

    public static boolean mouseReleased(int button) {
        if (!isActive()) return false;
        dragging = false;
        return true;
    }

    public static boolean keyPressed(int key) {
        if (!isActive()) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) cancel();
        else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) commit();
        return true;
    }

    public static void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!isActive()) return;
        if (Minecraft.getInstance().gui.screen() != owner) { cancel(); return; }
        var bounds = bounds();
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + 1, 0xFF60C8FF);
        graphics.fill(bounds.x(), bounds.y() + bounds.height() - 1, bounds.x() + bounds.width(), bounds.y() + bounds.height(), 0xFF60C8FF);
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + 1, bounds.y() + bounds.height(), 0xFF60C8FF);
        graphics.fill(bounds.x() + bounds.width() - 1, bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), 0xFF60C8FF);
        var font = Minecraft.getInstance().font;
        String title = Component.translatable("message.better-shulker-hud.layout_hint").getString();
        graphics.fill(0, 0, owner.width, 40, 0xD0182028);
        graphics.text(font, font.plainSubstrByWidth(title, owner.width - 8), 4, 5, 0xFFFFFFFF, false);
        String[] keys = {"gui.done", "gui.cancel", "message.better-shulker-hud.layout_reset"};
        for (int i = 0; i < keys.length; i++) {
            int bx = buttonX(i);
            graphics.fill(bx, 18, bx + buttonWidth(), 36,
                    overButton(mouseX, mouseY, i) ? 0xFF637B8A : 0xFF344550);
            String text = font.plainSubstrByWidth(Component.translatable(keys[i]).getString(), buttonWidth() - 4);
            graphics.text(font, text, bx + (buttonWidth() - font.width(text)) / 2, 23, 0xFFFFFFFF, false);
        }
    }
}
