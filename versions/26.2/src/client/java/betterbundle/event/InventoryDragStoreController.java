package bettershulkerhud.event;

import bettershulkerhud.compat.QuickShulkerExtractionController;
import bettershulkerhud.compat.StorageClientNetwork;
import bettershulkerhud.config.Configs;
import bettershulkerhud.config.Hotkeys;
import bettershulkerhud.gui.BundlePanelRenderer;
import bettershulkerhud.gui.StorageView;
import bettershulkerhud.util.ShulkerContentsHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public final class InventoryDragStoreController {
    private static final ArrayDeque<QueuedStoreRequest> QUEUE = new ArrayDeque<>();
    private static final Set<Integer> VISITED_SLOTS = new HashSet<>();
    private static boolean gestureActive;

    private InventoryDragStoreController() {}

    public static boolean capture(
            AbstractContainerScreen<?> screen, Slot hoveredSlot, int mouseButton) {
        Minecraft client = Minecraft.getInstance();
        if (!Configs.Features.INVENTORY_DRAG_STORE.getBooleanValue()
                || !(screen instanceof InventoryScreen)
                || !Hotkeys.INVENTORY_DRAG_STORE_MODIFIER.getKeybind().isKeybindHeld()
                || client.player == null
                || hoveredSlot == null
                || hoveredSlot.container != client.player.getInventory()
                || !screen.getMenu().getCarried().isEmpty()
                || mouseButton != GLFW.GLFW_MOUSE_BUTTON_LEFT
                && mouseButton != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return false;
        }

        int inventorySlot = hoveredSlot.getContainerSlot();
        if (inventorySlot < 0 || inventorySlot >= 36) return false;
        ItemStack stack = hoveredSlot.getItem();
        if (stack.isEmpty()) return false;

        StorageView storageView = BundlePanelRenderer.getStorageView();
        if (storageView == StorageView.SHULKERS
                && ShulkerContentsHelper.isShulker(stack)) return false;
        if (storageView == StorageView.BUNDLES
                && !BundleContents.canItemBeInBundle(stack)) return false;

        if (!gestureActive) {
            gestureActive = true;
            VISITED_SLOTS.clear();
        }
        if (VISITED_SLOTS.add(inventorySlot)) {
            boolean oneItem = mouseButton == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
            if (trySmoothStore(
                    client, storageView, inventorySlot, stack, oneItem)) {
                return true;
            }
            if (storageView != StorageView.SHULKERS) {
                StorageClientNetwork.showServerRequired();
                return true;
            }
            QUEUE.addLast(new QueuedStoreRequest(
                    inventorySlot,
                    oneItem,
                    stack.copyWithCount(1)));
        }
        return true;
    }

    private static boolean trySmoothStore(
            Minecraft client, StorageView storageView, int inventorySlot,
            ItemStack stack, boolean oneItem) {
        if (!StorageClientNetwork.hasStorageServer()
                || QuickShulkerExtractionController.hasActiveOperation()) {
            return false;
        }
        return switch (storageView) {
            case ENDER_CHEST -> StorageClientNetwork.storeEnderInventorySlot(
                    inventorySlot, oneItem, stack);
            case BUNDLES -> StorageClientNetwork.storeBundleInventorySlot(
                    inventorySlot, oneItem, stack);
            case SHULKERS -> {
                // The selected hand slot and items with remembered origins
                // remain on the existing transaction path. That path owns
                // hand-restock suppression and source-aware returns.
                boolean safe = inventorySlot
                        != client.player.getInventory().getSelectedSlot()
                        && !QuickShulkerExtractionController.hasOriginFor(stack);
                yield safe && StorageClientNetwork.storeShulkerInventorySlot(
                        inventorySlot, oneItem, stack);
            }
        };
    }

    public static boolean finishGesture() {
        boolean handled = gestureActive;
        gestureActive = false;
        VISITED_SLOTS.clear();
        return handled;
    }

    public static void onClientTick(Minecraft client) {
        if (!Configs.Features.INVENTORY_DRAG_STORE.getBooleanValue()) {
            clear();
            return;
        }
        if (gestureActive || QUEUE.isEmpty()
                || client.player == null || client.gameMode == null
                || QuickShulkerExtractionController.hasActiveOperation()) {
            return;
        }
        if (!(client.gui.screen() instanceof InventoryScreen screen)) {
            QUEUE.clear();
            return;
        }
        if (!screen.getMenu().getCarried().isEmpty()) return;

        QueuedStoreRequest request = QUEUE.removeFirst();
        ItemStack current = client.player.getInventory().getItem(request.inventorySlot());
        if (current.isEmpty()
                || !ItemStack.isSameItemSameComponents(current, request.prototype())) {
            return;
        }
        QuickShulkerExtractionController.requestStoreInventorySlot(
                screen, request.inventorySlot(), request.oneItem());
    }

    public static void clear() {
        QUEUE.clear();
        VISITED_SLOTS.clear();
        gestureActive = false;
    }

    public static QueuedStoreRequest peekNextValid(Minecraft client) {
        if (client.player == null) return null;
        while (!QUEUE.isEmpty()) {
            QueuedStoreRequest request = QUEUE.peekFirst();
            ItemStack current = client.player.getInventory().getItem(request.inventorySlot());
            if (!current.isEmpty()
                    && ItemStack.isSameItemSameComponents(current, request.prototype())) {
                return request;
            }
            QUEUE.removeFirst();
        }
        return null;
    }

    public static void consume(QueuedStoreRequest request) {
        if (QUEUE.peekFirst() == request) QUEUE.removeFirst();
    }

    public record QueuedStoreRequest(
            int inventorySlot, boolean oneItem, ItemStack prototype) {}
}
