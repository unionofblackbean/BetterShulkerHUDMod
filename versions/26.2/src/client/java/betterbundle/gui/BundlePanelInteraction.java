package bettershulkerhud.gui;

import bettershulkerhud.compat.QuickShulkerExtractionController;
import bettershulkerhud.compat.StorageClientNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class BundlePanelInteraction {
    private static int lastExtractDragCell = -1;
    private static int lastExtractDragButton = -1;

    private BundlePanelInteraction() {}

    private static int gridX(int leftPos) {
        return BundlePanelRenderer.gridX(leftPos);
    }

    private static int gridY(int topPos, int imageHeight) {
        return BundlePanelRenderer.gridY(topPos, imageHeight);
    }

    private static int getGridCell(
            double mouseX, double mouseY,
            int leftPos, int topPos, int imageHeight) {
        int relX = (int) mouseX - gridX(leftPos);
        int relY = (int) mouseY - gridY(topPos, imageHeight);
        if (relX < 0 || relY < 0) return -1;

        int stride = BundlePanelRenderer.SLOT_SIZE + BundlePanelRenderer.SLOT_SPACING;
        int columns = BundlePanelRenderer.columnCount(leftPos);
        int rows = BundlePanelRenderer.visibleRowCount(topPos, imageHeight);
        int col = relX / stride;
        int row = relY / stride;
        if (col >= columns || row >= rows) return -1;
        if (relX % stride >= BundlePanelRenderer.SLOT_SIZE
                || relY % stride >= BundlePanelRenderer.SLOT_SIZE) return -1;
        return row * columns + col;
    }

    private static BundlePanelRenderer.FlatItem getClickedItem(
            double mouseX, double mouseY,
            int leftPos, int topPos, int imageHeight) {
        List<BundlePanelRenderer.FlatItem> items = BundlePanelRenderer.getVisibleItems();
        if (items.isEmpty()) return null;
        int cell = getGridCell(mouseX, mouseY, leftPos, topPos, imageHeight);
        if (cell < 0) return null;
        int columns = BundlePanelRenderer.columnCount(leftPos);
        int row = cell / columns;
        int col = cell % columns;
        int index = (BundlePanelRenderer.getScrollOffset() + row) * columns + col;
        return index < items.size() ? items.get(index) : null;
    }

    public static boolean handlePanelClick(
            double mouseX, double mouseY, int button, int modifiers,
            int leftPos, int topPos, int imageHeight) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT
                && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false;

        resetDrag();
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return true;
        if (!client.player.containerMenu.getCarried().isEmpty()) {
            return true;
        }

        if (BundlePanelRenderer.isReturnButtonHovered(
                mouseX, mouseY, leftPos, topPos, imageHeight)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                    && BundlePanelRenderer.getStorageView() == StorageView.SHULKERS
                    && QuickShulkerExtractionController.canOrganizeInventory()) {
                BundlePanelRenderer.playButtonClick();
                QuickShulkerExtractionController.requestReturnAll();
            }
            return true;
        }

        int cell = getGridCell(mouseX, mouseY, leftPos, topPos, imageHeight);
        if (cell < 0) return false;
        BundlePanelRenderer.FlatItem clicked = getClickedItem(
                mouseX, mouseY, leftPos, topPos, imageHeight);
        if (clicked == null) return true;

        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean handled = extract(
                clicked, !shift, button == GLFW.GLFW_MOUSE_BUTTON_RIGHT, false);
        if (handled && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            lastExtractDragCell = cell;
            lastExtractDragButton = button;
        }
        return true;
    }

    public static boolean handlePanelDrag(
            double mouseX, double mouseY, int button, int modifiers,
            int leftPos, int topPos, int imageHeight) {
        if (!BundlePanelRenderer.isEffectivelyVisible()
                || button != GLFW.GLFW_MOUSE_BUTTON_LEFT
                || !isInsideGrid(mouseX, mouseY, leftPos, topPos, imageHeight)) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !client.player.containerMenu.getCarried().isEmpty()) {
            return true;
        }
        int cell = getGridCell(mouseX, mouseY, leftPos, topPos, imageHeight);
        if (cell < 0) return true;
        if (cell == lastExtractDragCell && button == lastExtractDragButton) return true;

        lastExtractDragCell = cell;
        lastExtractDragButton = button;
        BundlePanelRenderer.FlatItem item = getClickedItem(
                mouseX, mouseY, leftPos, topPos, imageHeight);
        if (item != null) {
            boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            extract(item, !shift, false, false);
        }
        return true;
    }

    public static void resetDrag() {
        lastExtractDragCell = -1;
        lastExtractDragButton = -1;
    }

    private static boolean extract(
            BundlePanelRenderer.FlatItem item, boolean oneItem,
            boolean toCursor, boolean silentIfMissing) {
        if (item == null || item.sources().isEmpty()) return false;
        BundlePanelRenderer.ItemSource source = item.sources().getFirst();
        return switch (item.storageView()) {
            case SHULKERS -> {
                if (toCursor) {
                    QuickShulkerExtractionController.requestToCursor(item);
                } else if (silentIfMissing) {
                    QuickShulkerExtractionController.requestFromItemScroller(item, !oneItem);
                } else {
                    QuickShulkerExtractionController.request(item, oneItem);
                }
                yield true;
            }
            case ENDER_CHEST -> {
                boolean sent = toCursor
                        ? StorageClientNetwork.extractEnderToCursor(
                                source.shulkerSlot(), source.stack())
                        : StorageClientNetwork.extractEnder(
                                source.shulkerSlot(), oneItem, source.stack());
                if (!sent && !silentIfMissing) StorageClientNetwork.showServerRequired();
                yield true;
            }
            case BUNDLES -> {
                boolean sent = toCursor
                        ? StorageClientNetwork.extractBundleToCursor(
                                source.inventorySlot(), source.shulkerSlot(), source.stack())
                        : StorageClientNetwork.extractBundle(
                                source.inventorySlot(), source.shulkerSlot(),
                                oneItem, source.stack());
                if (!sent && !silentIfMissing) StorageClientNetwork.showServerRequired();
                yield true;
            }
        };
    }

    public static boolean handleStoreCarried(AbstractContainerScreen<?> screen) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || screen.getMenu().getCarried().isEmpty()) return false;
        return switch (BundlePanelRenderer.getStorageView()) {
            case SHULKERS -> {
                QuickShulkerExtractionController.requestStoreCarried(screen);
                yield true;
            }
            case ENDER_CHEST -> {
                if (!StorageClientNetwork.insertEnderCarried(
                        screen.getMenu().getCarried())) {
                    StorageClientNetwork.showServerRequired();
                }
                yield true;
            }
            case BUNDLES -> {
                int target = findBundleInsertionTarget(screen.getMenu().getCarried());
                if (target >= 0) {
                    if (!StorageClientNetwork.insertBundleCarried(
                            target, screen.getMenu().getCarried())) {
                        StorageClientNetwork.showServerRequired();
                    }
                }
                yield true;
            }
        };
    }

    private static int findBundleInsertionTarget(ItemStack carried) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || carried.isEmpty()
                || !BundleContents.canItemBeInBundle(carried)) return -1;
        Inventory inventory = client.player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack bundle = inventory.getItem(slot);
            if (!(bundle.getItem() instanceof BundleItem)) continue;
            BundleContents.Mutable mutable = new BundleContents.Mutable(
                    bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY));
            if (mutable.tryInsert(carried.copy()) > 0) return slot;
        }
        return -1;
    }

    public static boolean handleScroll(
            double mouseX, double mouseY, double scrollDelta,
            int leftPos, int topPos, int imageHeight) {
        if (!BundlePanelRenderer.isEffectivelyVisible()
                || !isInsideGrid(mouseX, mouseY, leftPos, topPos, imageHeight)) return false;
        BundlePanelRenderer.scrollBy(scrollDelta > 0 ? -1 : 1);
        return true;
    }

    public static boolean handleItemScrollerWheel(
            double mouseX, double mouseY, boolean moveStack,
            int leftPos, int topPos, int imageHeight) {
        if (!BundlePanelRenderer.isEffectivelyVisible()
                || !isInsideGrid(mouseX, mouseY, leftPos, topPos, imageHeight)) return false;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !client.player.containerMenu.getCarried().isEmpty()) {
            return true;
        }
        BundlePanelRenderer.FlatItem hovered = getClickedItem(
                mouseX, mouseY, leftPos, topPos, imageHeight);
        if (hovered == null) return false;
        extract(hovered, !moveStack, false, true);
        return true;
    }

    public static boolean handleTakeToOffhand(
            double mouseX, double mouseY,
            int leftPos, int topPos, int imageHeight) {
        if (!BundlePanelRenderer.isEffectivelyVisible()) return false;
        BundlePanelRenderer.FlatItem hovered = getClickedItem(
                mouseX, mouseY, leftPos, topPos, imageHeight);
        if (hovered == null) return false;
        if (hovered.storageView() != StorageView.SHULKERS) return true;
        QuickShulkerExtractionController.requestToOffhand(hovered);
        return true;
    }

    private static boolean isInsideGrid(
            double mouseX, double mouseY,
            int leftPos, int topPos, int imageHeight) {
        if (!BundlePanelRenderer.hasRenderablePanel()) return false;
        int panelX = BundlePanelRenderer.panelX(leftPos);
        int gx = gridX(leftPos);
        if (mouseX < gx || mouseX > panelX
                + BundlePanelRenderer.panelWidth(leftPos) - BundlePanelRenderer.PADDING) {
            return false;
        }
        int gridTop = gridY(topPos, imageHeight);
        int rows = BundlePanelRenderer.visibleRowCount(topPos, imageHeight);
        int gridHeight = rows * BundlePanelRenderer.SLOT_SIZE
                + (rows - 1) * BundlePanelRenderer.SLOT_SPACING;
        return mouseY >= gridTop && mouseY <= gridTop + gridHeight;
    }

    public static boolean isInsidePanel(
            double mouseX, double mouseY,
            int leftPos, int topPos, int imageHeight) {
        return BundlePanelRenderer.isReturnButtonHovered(
                mouseX, mouseY, leftPos, topPos, imageHeight)
                || isInsideGrid(mouseX, mouseY, leftPos, topPos, imageHeight);
    }
}
