package bettershulkerhud.gui;

import bettershulkerhud.compat.QuickShulkerExtractionController;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class BundlePanelInteraction {
    private BundlePanelInteraction() {}

    private static int gridX(int leftPos) {
        return BundlePanelRenderer.gridX(leftPos);
    }

    private static int gridY(int topPos, int imageHeight) {
        return BundlePanelRenderer.gridY(topPos, imageHeight);
    }

    private static BundlePanelRenderer.FlatItem getClickedItem(
            double mouseX, double mouseY, int leftPos, int topPos, int imageHeight) {
        List<BundlePanelRenderer.FlatItem> items = BundlePanelRenderer.getVisibleItems();
        if (items.isEmpty()) return null;

        int relX = (int) mouseX - gridX(leftPos);
        int relY = (int) mouseY - gridY(topPos, imageHeight);
        if (relX < 0 || relY < 0) return null;

        int stride = BundlePanelRenderer.SLOT_SIZE + BundlePanelRenderer.SLOT_SPACING;
        int columns = BundlePanelRenderer.columnCount(leftPos);
        int rows = BundlePanelRenderer.visibleRowCount(topPos, imageHeight);
        int col = relX / stride;
        int row = relY / stride;
        if (col >= columns || row >= rows) return null;
        if (relX % stride >= BundlePanelRenderer.SLOT_SIZE
                || relY % stride >= BundlePanelRenderer.SLOT_SIZE) return null;

        int index = (BundlePanelRenderer.getScrollOffset() + row)
                * columns + col;
        return index < items.size() ? items.get(index) : null;
    }

    public static boolean handlePanelClick(
            double mouseX, double mouseY, int button, int modifiers,
            int leftPos, int topPos, int imageHeight) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT
                && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return true;
        if (!client.player.containerMenu.getCarried().isEmpty()) {
            // The release handler owns carried-stack storage. Do not also extract
            // an item from the HUD during the same click.
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && BundlePanelRenderer.isReturnButtonHovered(
                mouseX, mouseY, leftPos, topPos, imageHeight)) {
            if (QuickShulkerExtractionController.canOrganizeInventory()) {
                BundlePanelRenderer.playButtonClick();
                QuickShulkerExtractionController.requestReturnAll();
            }
            return true;
        }

        BundlePanelRenderer.FlatItem clicked = getClickedItem(
                mouseX, mouseY, leftPos, topPos, imageHeight);
        if (clicked == null) return false;

        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            QuickShulkerExtractionController.requestToCursor(clicked);
        } else {
            QuickShulkerExtractionController.request(clicked, !shift);
        }
        return true;
    }

    public static boolean handleScroll(
            double mouseX, double mouseY, double scrollDelta,
            int leftPos, int topPos, int imageHeight) {
        if (scrollDelta == 0.0
                || !BundlePanelRenderer.isMouseOverScrollBar(
                mouseX, mouseY, leftPos, topPos, imageHeight)) return false;
        BundlePanelRenderer.scrollBy(scrollDelta > 0 ? -1 : 1);
        return true;
    }

    public static boolean handleTakeToOffhand(
            double mouseX, double mouseY, int leftPos, int topPos, int imageHeight) {
        if (!BundlePanelRenderer.isEffectivelyVisible()) return false;
        BundlePanelRenderer.FlatItem hovered = getClickedItem(
                mouseX, mouseY, leftPos, topPos, imageHeight);
        if (hovered == null) return false;
        QuickShulkerExtractionController.requestToOffhand(hovered);
        return true;
    }

    public static boolean isInsidePanel(
            double mouseX, double mouseY, int leftPos, int topPos, int imageHeight) {
        int panelX = BundlePanelRenderer.panelX(leftPos);
        int gx = gridX(leftPos);
        if (BundlePanelRenderer.isReturnButtonHovered(
                mouseX, mouseY, leftPos, topPos, imageHeight)) return true;
        if (mouseX < gx
                || mouseX > panelX + BundlePanelRenderer.panelWidth(leftPos) - BundlePanelRenderer.PADDING) {
            return false;
        }

        int gridTop = gridY(topPos, imageHeight);
        int rows = BundlePanelRenderer.visibleRowCount(topPos, imageHeight);
        int gridHeight = rows * BundlePanelRenderer.SLOT_SIZE
                + (rows - 1) * BundlePanelRenderer.SLOT_SPACING;
        return mouseY >= gridTop && mouseY <= gridTop + gridHeight;
    }
}
