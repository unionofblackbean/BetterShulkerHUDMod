package bettershulkerhud.gui;

import bettershulkerhud.compat.QuickShulkerExtractionController;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class BundlePanelInteraction {
    private BundlePanelInteraction() {}

    private static int gridX(int leftPos) {
        int panelX = BundlePanelRenderer.panelX(leftPos);
        return panelX + BundlePanelRenderer.PADDING
                + BundlePanelRenderer.CAT_BAR_WIDTH + 2
                + BundlePanelRenderer.SCROLL_BAR_WIDTH + 2;
    }

    private static int gridY(int topPos) {
        return topPos + BundlePanelRenderer.SEARCH_BAR_HEIGHT + 3
                + BundlePanelRenderer.PADDING;
    }

    private static BundlePanelRenderer.FlatItem getClickedItem(
            double mouseX, double mouseY, int leftPos, int topPos, int imageHeight) {
        List<BundlePanelRenderer.ShulkerSlotEntry> shulkers = BundlePanelRenderer.getShulkers();
        List<BundlePanelRenderer.FlatItem> items = BundlePanelRenderer.filterItems(
                BundlePanelRenderer.buildFlatItemList(shulkers),
                BundlePanelRenderer.searchQuery);
        if (items.isEmpty()) return null;

        int relX = (int) mouseX - gridX(leftPos);
        int relY = (int) mouseY - gridY(topPos);
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

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && BundlePanelRenderer.isReturnButtonHovered(
                mouseX, mouseY, leftPos, topPos, imageHeight)) {
            QuickShulkerExtractionController.requestReturnAll();
            return true;
        }

        BundlePanelRenderer.FlatItem clicked = getClickedItem(
                mouseX, mouseY, leftPos, topPos, imageHeight);
        if (clicked == null) return false;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return true;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean takeOne = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && !shift;
        QuickShulkerExtractionController.request(clicked, takeOne);
        return true;
    }

    public static boolean handleScroll(
            double mouseX, double mouseY, double scrollDelta,
            int leftPos, int topPos, int imageHeight) {
        if (!BundlePanelRenderer.isEffectivelyVisible()
                || !isInsidePanel(mouseX, mouseY, leftPos, topPos, imageHeight)) return false;
        BundlePanelRenderer.scrollBy(scrollDelta > 0 ? -1 : 1);
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

        int gridTop = gridY(topPos);
        int rows = BundlePanelRenderer.visibleRowCount(topPos, imageHeight);
        int gridHeight = rows * BundlePanelRenderer.SLOT_SIZE
                + (rows - 1) * BundlePanelRenderer.SLOT_SPACING;
        return mouseY >= gridTop && mouseY <= gridTop + gridHeight;
    }
}
