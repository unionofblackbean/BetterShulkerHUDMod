package bettershulkerhud.event;

import bettershulkerhud.compat.QuickShulkerExtractionController;
import bettershulkerhud.config.Configs;
import bettershulkerhud.config.Hotkeys;
import bettershulkerhud.util.ShulkerContentsHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class InventoryDragStoreController {
    private static final ArrayDeque<QueuedStoreRequest> QUEUE = new ArrayDeque<>();
    private static final ArrayDeque<QueuedStoreRequest> PROGRAMMATIC_QUEUE =
            new ArrayDeque<>();
    private static final Set<Integer> VISITED_SLOTS = new HashSet<>();
    private static boolean gestureActive;
    private static int programmaticReopenDelayTicks;

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
        if (stack.isEmpty() || ShulkerContentsHelper.isShulker(stack)) return false;

        if (!gestureActive) {
            gestureActive = true;
            VISITED_SLOTS.clear();
        }
        if (VISITED_SLOTS.add(inventorySlot)) {
            QUEUE.addLast(new QueuedStoreRequest(
                    inventorySlot,
                    mouseButton == GLFW.GLFW_MOUSE_BUTTON_RIGHT,
                    stack.copyWithCount(1),
                    false));
        }
        return true;
    }

    public static boolean finishGesture() {
        boolean handled = gestureActive;
        gestureActive = false;
        VISITED_SLOTS.clear();
        return handled;
    }

    public static void onClientTick(Minecraft client) {
        if (!Configs.Features.INVENTORY_DRAG_STORE.getBooleanValue()) {
            clearManualQueue();
        }
        if (gestureActive || QUEUE.isEmpty() && PROGRAMMATIC_QUEUE.isEmpty()
                || client.player == null || client.gameMode == null
                || QuickShulkerExtractionController.hasActiveOperation()) {
            return;
        }
        if (!PROGRAMMATIC_QUEUE.isEmpty() && programmaticReopenDelayTicks > 0) {
            programmaticReopenDelayTicks--;
            return;
        }
        if (!(client.screen instanceof InventoryScreen screen)) {
            QUEUE.clear();
            return;
        }
        if (!screen.getMenu().getCarried().isEmpty()) return;

        QueuedStoreRequest request = peekNextValid(client);
        if (request == null) return;
        if (!request.programmatic()) consume(request);
        ItemStack current = client.player.getInventory().getItem(request.inventorySlot());
        if (request.programmatic()) {
            QuickShulkerExtractionController.requestProgrammaticStoreInventorySlot(
                    screen, request.inventorySlot(), request.oneItem());
        } else {
            QuickShulkerExtractionController.requestStoreInventorySlot(
                    screen, request.inventorySlot(), request.oneItem());
        }
    }

    public static int enqueueProgrammatic(
            Minecraft client, List<Integer> inventorySlots) {
        if (client.player == null || inventorySlots == null) return 0;
        int queued = 0;
        Set<Integer> seen = new HashSet<>();
        for (Integer inventorySlot : inventorySlots) {
            if (inventorySlot == null || inventorySlot < 0 || inventorySlot >= 36
                    || !seen.add(inventorySlot)) continue;
            ItemStack stack = client.player.getInventory().getItem(inventorySlot);
            if (stack.isEmpty() || ShulkerContentsHelper.isShulker(stack)) continue;
            PROGRAMMATIC_QUEUE.addLast(new QueuedStoreRequest(
                    inventorySlot, false, stack.copyWithCount(1), true));
            queued++;
        }
        return queued;
    }

    public static void clearProgrammaticQueue() {
        PROGRAMMATIC_QUEUE.clear();
        programmaticReopenDelayTicks = 0;
    }

    public static QueuedStoreRequest peekNextProgrammatic(Minecraft client) {
        if (client.player == null) return null;
        return peekNextValid(client, PROGRAMMATIC_QUEUE);
    }

    public static void deferProgrammaticQueue(int ticks) {
        programmaticReopenDelayTicks = Math.max(
                programmaticReopenDelayTicks, Math.max(0, ticks));
    }

    public static void clear() {
        clearManualQueue();
        PROGRAMMATIC_QUEUE.clear();
        programmaticReopenDelayTicks = 0;
    }

    private static void clearManualQueue() {
        QUEUE.clear();
        VISITED_SLOTS.clear();
        gestureActive = false;
    }

    public static QueuedStoreRequest peekNextValid(Minecraft client) {
        if (client.player == null) return null;
        QueuedStoreRequest programmatic = peekNextValid(client, PROGRAMMATIC_QUEUE);
        return programmatic != null ? programmatic : peekNextValid(client, QUEUE);
    }

    private static QueuedStoreRequest peekNextValid(
            Minecraft client, ArrayDeque<QueuedStoreRequest> queue) {
        while (!queue.isEmpty()) {
            QueuedStoreRequest request = queue.peekFirst();
            ItemStack current = client.player.getInventory().getItem(request.inventorySlot());
            if (!current.isEmpty()
                    && ItemStack.isSameItemSameComponents(current, request.prototype())) {
                return request;
            }
            queue.removeFirst();
        }
        return null;
    }

    public static void consume(QueuedStoreRequest request) {
        if (PROGRAMMATIC_QUEUE.peekFirst() == request) {
            PROGRAMMATIC_QUEUE.removeFirst();
        } else if (QUEUE.peekFirst() == request) {
            QUEUE.removeFirst();
        }
    }

    public record QueuedStoreRequest(
            int inventorySlot, boolean oneItem, ItemStack prototype,
            boolean programmatic) {}
}
