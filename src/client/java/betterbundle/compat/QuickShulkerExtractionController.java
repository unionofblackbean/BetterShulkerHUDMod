package bettershulkerhud.compat;

import bettershulkerhud.config.Configs;
import bettershulkerhud.gui.BundlePanelRenderer;
import bettershulkerhud.util.ShulkerContentsHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kyrptonaught.quickshulker.client.ClientUtil;
import net.kyrptonaught.quickshulker.network.OpenShulkerPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class QuickShulkerExtractionController {
    private static final int OPEN_TIMEOUT_TICKS = 60;
    private static long containerSyncVersion;
    private static final Map<Integer, Long> containerSyncVersions = new HashMap<>();

    private static PendingExtraction pendingExtraction;
    private static int extractionWaitTicks;
    private static int extractionMenuId = -1;
    private static int extractionCloseDelay = -1;
    private static int extractionMovedItemCount;
    private static long extractionOpenSyncVersion;
    private static long extractionMoveSyncVersion;
    private static int extractionExpectedSourceCount = -1;

    private static PendingCursorPickup pendingCursorPickup;
    private static int cursorPickupWaitTicks;

    private static PendingStore pendingStore;
    private static int storeWaitTicks;
    private static int storeMenuId = -1;
    private static int storeCloseDelay = -1;
    private static int storedItemCount;
    private static long storeOpenSyncVersion;
    private static long storeMoveSyncVersion;
    private static int storeExpectedTargetCount = -1;

    private static int autoRestockCooldown;

    private static final List<OriginRecord> originRecords = new ArrayList<>();
    private static final ArrayDeque<OriginRecord> returnQueue = new ArrayDeque<>();
    private static OriginRecord activeReturn;
    private static int activeReturnShulkerSlot = -1;
    private static int returnWaitTicks;
    private static int returnMenuId = -1;
    private static int nextReturnDelay = -1;
    private static int returnedItemCount;
    private static long returnOpenSyncVersion;
    private static ItemStack pendingLitematicaSelection = ItemStack.EMPTY;

    private QuickShulkerExtractionController() {}

    public static void onClientTick(Minecraft client) {
        tick(client);
    }

    public static void onContainerSync(int containerId) {
        containerSyncVersions.put(containerId, ++containerSyncVersion);
        BundlePanelRenderer.invalidateCache();
    }

    public static void clearWorldState() {
        clearExtraction();
        clearCursorPickup();
        clearStore();
        clearReturnProcess();
        autoRestockCooldown = 0;
        containerSyncVersion = 0;
        containerSyncVersions.clear();
        originRecords.clear();
        pendingLitematicaSelection = ItemStack.EMPTY;
        BundlePanelRenderer.invalidateCache();
    }

    public static boolean hasReturnableHistory() {
        return Configs.Features.RETURN_HISTORY.getBooleanValue()
                && originRecords.stream().anyMatch(record -> record.remaining > 0);
    }

    public static void request(BundlePanelRenderer.FlatItem item, boolean takeOne) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !(client.screen instanceof AbstractContainerScreen<?> screen)) return;

        if (isBusy()) {
            show(client, "message.better-shulker-hud.busy");
            return;
        }
        if (!canUseQuickShulker(client)) return;

        ResolvedSource source = findValidatedSource(
                screen, client.player.getInventory(), item);
        if (source == null) {
            show(client, "message.better-shulker-hud.source_missing");
            return;
        }

        pendingExtraction = new PendingExtraction(
                source.inventorySlot(), source.shulkerSlot(), source.expectedStack(), takeOne,
                source.shulkerItem(), source.shulkerName(), false, false, false,
                -1, 0, 0);
        extractionWaitTicks = 0;
        extractionMenuId = -1;
        extractionCloseDelay = -1;
        extractionMovedItemCount = 0;
        extractionExpectedSourceCount = -1;
        extractionOpenSyncVersion = containerSyncVersion;
        OpenShulkerPacket.sendOpenPacket(source.quickShulkerSlot());
    }

    public static void requestToCursor(BundlePanelRenderer.FlatItem item) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null
                || !(client.screen instanceof AbstractContainerScreen<?> screen)) return;
        if (!screen.getMenu().getCarried().isEmpty()) return;

        if (isBusy()) {
            show(client, "message.better-shulker-hud.busy");
            return;
        }
        if (!canUseQuickShulker(client)) return;

        Inventory inventory = client.player.getInventory();
        ResolvedSource source = findValidatedSource(screen, inventory, item);
        if (source == null) {
            show(client, "message.better-shulker-hud.source_missing");
            return;
        }

        CursorStaging staging = findCursorStagingDestination(
                screen, inventory, source.expectedStack(), source.inventorySlot());
        if (staging == null) {
            show(client, "message.better-shulker-hud.inventory_full");
            return;
        }
        int requestedAmount = Math.min(
                source.expectedStack().getCount(), staging.capacity());
        if (requestedAmount <= 0) {
            show(client, "message.better-shulker-hud.inventory_full");
            return;
        }

        pendingExtraction = new PendingExtraction(
                source.inventorySlot(), source.shulkerSlot(), source.expectedStack(), false,
                source.shulkerItem(), source.shulkerName(), false, false, true,
                staging.inventorySlot(), requestedAmount, staging.baselineCount());
        extractionWaitTicks = 0;
        extractionMenuId = -1;
        extractionCloseDelay = -1;
        extractionMovedItemCount = 0;
        extractionExpectedSourceCount = -1;
        extractionOpenSyncVersion = containerSyncVersion;
        OpenShulkerPacket.sendOpenPacket(source.quickShulkerSlot());
    }

    public static void requestLitematicaRestock(ItemStack required) {
        Minecraft client = Minecraft.getInstance();
        if (!Configs.Features.LITEMATICA_RESTOCK.getBooleanValue()
                || required == null || required.isEmpty() || client.player == null
                || client.gameMode == null || isBusy()
                || hasMatchingPlayerItem(client.player.getInventory(), required)
                || !ClientPlayNetworking.canSend(OpenShulkerPacket.OPEN_SHULKER_PACKET_ID)) {
            return;
        }

        ResolvedSource source = findRestockSource(
                client.player.containerMenu, client.player.getInventory(), required);
        if (source == null) return;

        pendingExtraction = new PendingExtraction(
                source.inventorySlot(), source.shulkerSlot(), source.expectedStack(), false,
                source.shulkerItem(), source.shulkerName(), true, false, false,
                -1, 0, 0);
        pendingLitematicaSelection = required.copyWithCount(1);
        extractionWaitTicks = 0;
        extractionMenuId = -1;
        extractionCloseDelay = -1;
        extractionExpectedSourceCount = -1;
        extractionOpenSyncVersion = containerSyncVersion;
        OpenShulkerPacket.sendOpenPacket(source.quickShulkerSlot());
    }

    public static void requestReturnAll() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !(client.screen instanceof AbstractContainerScreen<?>)) return;
        if (!Configs.Features.RETURN_HISTORY.getBooleanValue()) return;

        if (isBusy()) {
            show(client, "message.better-shulker-hud.busy");
            return;
        }
        if (!hasReturnableHistory()) {
            show(client, "message.better-shulker-hud.nothing_to_return");
            return;
        }
        if (!canUseQuickShulker(client)) return;

        returnQueue.clear();
        originRecords.stream()
                .filter(record -> record.remaining > 0)
                .forEach(returnQueue::addLast);
        returnedItemCount = 0;
        startNextReturn(client);
    }

    public static void requestStoreCarried(AbstractContainerScreen<?> screen) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null) return;

        ItemStack carried = screen.getMenu().getCarried();
        if (carried.isEmpty()) return;
        if (isBusy()) {
            show(client, "message.better-shulker-hud.busy");
            return;
        }
        if (ShulkerContentsHelper.isShulker(carried)) {
            show(client, "message.better-shulker-hud.cannot_nest_shulker");
            return;
        }
        if (!canUseQuickShulker(client)) return;

        ItemStack prototype = carried.copyWithCount(1);
        StoreTarget target = findStoreTarget(client.player.getInventory(), prototype, carried.getCount());
        if (target == null) {
            show(client, "message.better-shulker-hud.no_shulker_space");
            return;
        }

        PlayerDestination temporary = findTemporaryDestination(
                screen, client.player.getInventory(), carried, target.inventorySlot());
        if (temporary == null) {
            show(client, "message.better-shulker-hud.no_temporary_space");
            return;
        }

        int amount = carried.getCount();
        client.gameMode.handleContainerInput(
                screen.getMenu().containerId, temporary.menuSlot(), 0,
                ContainerInput.PICKUP, client.player);
        if (!screen.getMenu().getCarried().isEmpty()) {
            show(client, "message.better-shulker-hud.no_temporary_space");
            return;
        }

        int targetMenuSlot = resolveQuickShulkerSlot(
                screen, client.player.getInventory(), target.inventorySlot());
        if (targetMenuSlot < 0) {
            show(client, "message.better-shulker-hud.store_target_changed");
            return;
        }

        pendingStore = new PendingStore(
                temporary.inventorySlot(), amount, target.inventorySlot(), target.shulkerSlot(),
                prototype, target.shulkerItem(), target.shulkerName());
        storeWaitTicks = 0;
        storeMenuId = -1;
        storeCloseDelay = -1;
        storedItemCount = 0;
        storeExpectedTargetCount = -1;
        storeOpenSyncVersion = containerSyncVersion;
        OpenShulkerPacket.sendOpenPacket(targetMenuSlot);
    }

    private static boolean isBusy() {
        return pendingExtraction != null || pendingStore != null
                || pendingCursorPickup != null
                || activeReturn != null
                || !returnQueue.isEmpty() || nextReturnDelay >= 0;
    }

    public static boolean shouldHideQuickShulkerScreen() {
        return Configs.Features.HIDE_QUICK_SHULKER_SCREEN.getBooleanValue()
                && (pendingExtraction != null || pendingStore != null
                || activeReturn != null);
    }

    private static boolean canUseQuickShulker(Minecraft client) {
        if (ClientPlayNetworking.canSend(OpenShulkerPacket.OPEN_SHULKER_PACKET_ID)) return true;
        show(client, "message.better-shulker-hud.quickshulker_required");
        return false;
    }

    private static void tick(Minecraft client) {
        if (pendingStore != null) {
            tickStore(client);
            return;
        } else if (pendingExtraction != null) {
            tickExtraction(client);
            return;
        } else if (pendingCursorPickup != null) {
            tickCursorPickup(client);
            return;
        } else if (activeReturn != null || !returnQueue.isEmpty() || nextReturnDelay >= 0) {
            tickReturn(client);
            return;
        }
        tickAutoRestock(client);
    }

    private static boolean tickAutoRestock(Minecraft client) {
        if (client.player == null || client.gameMode == null
                || !Configs.Features.AUTO_RESTOCK.getBooleanValue()
                || client.screen != null
                || client.player.containerMenu != client.player.inventoryMenu
                || client.player.isCreative() || client.player.isSpectator()) {
            return false;
        }
        if (autoRestockCooldown > 0) {
            autoRestockCooldown--;
            return false;
        }
        autoRestockCooldown = Configs.General.AUTO_RESTOCK_SCAN_INTERVAL.getIntegerValue();

        Inventory inventory = client.player.getInventory();
        int targetInventorySlot = inventory.getSelectedSlot();
        ItemStack targetStack = inventory.getItem(targetInventorySlot);
        if (targetStack.isEmpty() || ShulkerContentsHelper.isShulker(targetStack)
                || targetStack.getMaxStackSize() <= 1
                || targetStack.getCount()
                > Configs.General.AUTO_RESTOCK_THRESHOLD.getIntegerValue()
                || targetStack.getCount() >= targetStack.getMaxStackSize()) {
            return false;
        }
        if (!ClientPlayNetworking.canSend(OpenShulkerPacket.OPEN_SHULKER_PACKET_ID)) {
            return false;
        }

        ItemStack required = targetStack.copyWithCount(1);
        ResolvedSource source = findRestockSource(
                client.player.containerMenu, inventory, required);
        if (source == null) return false;

        int requestedAmount = Math.min(
                Configs.General.AUTO_RESTOCK_AMOUNT.getIntegerValue(),
                targetStack.getMaxStackSize() - targetStack.getCount());
        if (requestedAmount <= 0) return false;

        pendingExtraction = new PendingExtraction(
                source.inventorySlot(), source.shulkerSlot(), source.expectedStack(), false,
                source.shulkerItem(), source.shulkerName(), false, true, false,
                targetInventorySlot, requestedAmount, targetStack.getCount());
        extractionWaitTicks = 0;
        extractionMenuId = -1;
        extractionCloseDelay = -1;
        extractionMovedItemCount = 0;
        extractionExpectedSourceCount = -1;
        extractionOpenSyncVersion = containerSyncVersion;
        OpenShulkerPacket.sendOpenPacket(source.quickShulkerSlot());
        return true;
    }

    private static void tickStore(Minecraft client) {
        if (client.player == null || client.gameMode == null) {
            clearStore();
            return;
        }

        if (storeCloseDelay >= 0) {
            if (!hasContainerSyncAfter(storeMenuId, storeMoveSyncVersion)) {
                if (++storeWaitTicks > OPEN_TIMEOUT_TICKS) {
                    failStore(client, "message.better-shulker-hud.store_failed");
                }
                return;
            }
            if (!(client.player.containerMenu instanceof ShulkerBoxMenu menu)
                    || !isConfirmedStoreTarget(menu)) {
                failStore(client, "message.better-shulker-hud.store_failed");
                return;
            }
            closeAfterStore(client);
            return;
        }

        if (!(client.player.containerMenu instanceof ShulkerBoxMenu menu)) {
            if (++storeWaitTicks > OPEN_TIMEOUT_TICKS) {
                failStore(client, "message.better-shulker-hud.open_failed");
            }
            return;
        }
        if (storeMenuId != menu.containerId) {
            storeMenuId = menu.containerId;
            storeWaitTicks = 0;
        }
        if (!hasContainerSyncAfter(menu.containerId, storeOpenSyncVersion)) {
            if (++storeWaitTicks > OPEN_TIMEOUT_TICKS) {
                failStore(client, "message.better-shulker-hud.open_failed");
            }
            return;
        }

        if (!isExpectedStoreShulker(menu, client.player.getInventory(), pendingStore)) {
            failStore(client, "message.better-shulker-hud.store_target_changed");
            return;
        }

        Slot target = menu.getSlot(pendingStore.shulkerSlot());
        ItemStack targetStack = target.getItem();
        if (!targetStack.isEmpty()
                && !ItemStack.isSameItemSameComponents(targetStack, pendingStore.prototype())) {
            failStore(client, "message.better-shulker-hud.store_target_changed");
            return;
        }
        int capacity = target.getMaxStackSize(pendingStore.prototype()) - targetStack.getCount();
        int sourceMenuSlot = findExactPlayerItemSlot(
                menu, client.player.getInventory(), pendingStore.sourceInventorySlot(),
                pendingStore.prototype(), pendingStore.targetInventorySlot());
        if (capacity < pendingStore.amount() || sourceMenuSlot < 0) {
            failStore(client, "message.better-shulker-hud.store_target_changed");
            return;
        }

        int before = menu.getSlot(sourceMenuSlot).getItem().getCount();
        storeMoveSyncVersion = containerSyncVersion;
        storeExpectedTargetCount = targetStack.getCount() + pendingStore.amount();
        moveExactAmount(client, menu, sourceMenuSlot, pendingStore.shulkerSlot(), pendingStore.amount());
        int moved = before - menu.getSlot(sourceMenuSlot).getItem().getCount();
        if (moved != pendingStore.amount()) {
            failStore(client, "message.better-shulker-hud.store_failed");
            return;
        }

        storedItemCount = moved;
        consumeOriginRecords(pendingStore.prototype(), moved);
        storeCloseDelay = 0;
        storeWaitTicks = 0;
    }

    private static boolean isConfirmedStoreTarget(ShulkerBoxMenu menu) {
        if (pendingStore == null || pendingStore.shulkerSlot() < 0
                || pendingStore.shulkerSlot() >= ShulkerContentsHelper.SHULKER_SIZE
                || storeExpectedTargetCount < 0) return false;
        ItemStack target = menu.getSlot(pendingStore.shulkerSlot()).getItem();
        return ItemStack.isSameItemSameComponents(target, pendingStore.prototype())
                && target.getCount() >= storeExpectedTargetCount;
    }

    private static void tickExtraction(Minecraft client) {
        if (client.player == null || client.gameMode == null) {
            clearExtraction();
            return;
        }

        if (extractionCloseDelay >= 0) {
            if (!hasContainerSyncAfter(extractionMenuId, extractionMoveSyncVersion)) {
                if (++extractionWaitTicks > OPEN_TIMEOUT_TICKS) {
                    failExtraction(client, "message.better-shulker-hud.open_failed");
                }
                return;
            }
            if (!(client.player.containerMenu instanceof ShulkerBoxMenu menu)
                    || !isConfirmedExtractionSource(menu)) {
                failExtraction(client, "message.better-shulker-hud.source_changed");
                return;
            }
            closeAfterExtraction(client);
            return;
        }

        if (!(client.player.containerMenu instanceof ShulkerBoxMenu menu)) {
            if (++extractionWaitTicks > OPEN_TIMEOUT_TICKS) {
                failExtraction(client, "message.better-shulker-hud.open_failed");
            }
            return;
        }

        if (extractionMenuId != menu.containerId) {
            extractionMenuId = menu.containerId;
            extractionWaitTicks = 0;
        }
        if (!hasContainerSyncAfter(menu.containerId, extractionOpenSyncVersion)) {
            if (++extractionWaitTicks > OPEN_TIMEOUT_TICKS) {
                failExtraction(client, "message.better-shulker-hud.open_failed");
            }
            return;
        }

        Slot source = menu.getSlot(pendingExtraction.shulkerSlot());
        ItemStack sourceStack = source.getItem();
        if (sourceStack.isEmpty()
                || !ItemStack.isSameItemSameComponents(sourceStack, pendingExtraction.expectedStack())) {
            failExtraction(client, "message.better-shulker-hud.source_changed");
            return;
        }

        int before = sourceStack.getCount();
        extractionMoveSyncVersion = containerSyncVersion;
        if (pendingExtraction.targetInventorySlot() >= 0) {
            String targetError = pendingExtraction.cursorPickup()
                    ? "message.better-shulker-hud.cursor_pickup_failed"
                    : "message.better-shulker-hud.hand_unavailable";
            int destination = findTargetedPlayerDestination(
                    menu, client.player.getInventory(), pendingExtraction);
            if (destination < 0) {
                failExtraction(client, targetError);
                return;
            }
            ItemStack targetStack = menu.getSlot(destination).getItem();
            int amount = Math.min(
                    pendingExtraction.requestedAmount(),
                    Math.min(sourceStack.getCount(),
                            menu.getSlot(destination).getMaxStackSize(sourceStack)
                                    - targetStack.getCount()));
            if (amount <= 0) {
                failExtraction(client, targetError);
                return;
            }
            moveExactAmount(client, menu, pendingExtraction.shulkerSlot(), destination, amount);
            int moved = before - menu.getSlot(pendingExtraction.shulkerSlot()).getItem().getCount();
            if (moved != amount) {
                failExtraction(client, targetError);
                return;
            }
        } else if (pendingExtraction.takeOne()) {
            int destination = findDestinationSlot(menu, client.player.getInventory(), sourceStack,
                    pendingExtraction.inventorySlot());
            if (destination < 0) {
                failExtraction(client, "message.better-shulker-hud.inventory_full");
                return;
            }
            takeOne(client, menu, pendingExtraction.shulkerSlot(), destination);
        } else {
            client.gameMode.handleContainerInput(
                    menu.containerId, pendingExtraction.shulkerSlot(), 0,
                    ContainerInput.QUICK_MOVE, client.player);
        }

        int moved = before - menu.getSlot(pendingExtraction.shulkerSlot()).getItem().getCount();
        extractionExpectedSourceCount = before - moved;
        if (pendingExtraction.cursorPickup()) extractionMovedItemCount = moved;
        if (moved > 0 && !pendingExtraction.handRestock()) {
            recordExtraction(pendingExtraction, moved);
        }
        extractionCloseDelay = 0;
        extractionWaitTicks = 0;
    }

    private static boolean isConfirmedExtractionSource(ShulkerBoxMenu menu) {
        if (pendingExtraction == null || extractionExpectedSourceCount < 0) return false;
        ItemStack source = menu.getSlot(pendingExtraction.shulkerSlot()).getItem();
        if (extractionExpectedSourceCount == 0) return source.isEmpty();
        return ItemStack.isSameItemSameComponents(source, pendingExtraction.expectedStack())
                && source.getCount() == extractionExpectedSourceCount;
    }

    private static int findTargetedPlayerDestination(
            ShulkerBoxMenu menu, Inventory inventory, PendingExtraction extraction) {
        if (extraction.targetInventorySlot() < 0
                || extraction.targetInventorySlot() >= 36) return -1;
        for (int menuSlot = 0; menuSlot < menu.slots.size(); menuSlot++) {
            Slot slot = menu.slots.get(menuSlot);
            if (slot.container != inventory
                    || slot.getContainerSlot() != extraction.targetInventorySlot()) continue;
            ItemStack current = slot.getItem();
            if (!slot.mayPlace(extraction.expectedStack())
                    || (!current.isEmpty()
                    && !ItemStack.isSameItemSameComponents(
                    current, extraction.expectedStack()))
                    || current.getCount()
                    >= slot.getMaxStackSize(extraction.expectedStack())) {
                return -1;
            }
            return menuSlot;
        }
        return -1;
    }

    private static void tickCursorPickup(Minecraft client) {
        if (client.player == null || client.gameMode == null) {
            clearCursorPickup();
            return;
        }
        if (!(client.screen instanceof InventoryScreen)
                || client.player.containerMenu != client.player.inventoryMenu) {
            if (++cursorPickupWaitTicks > 20) {
                show(client, "message.better-shulker-hud.cursor_pickup_failed");
                clearCursorPickup();
            }
            return;
        }

        AbstractContainerMenu menu = client.player.inventoryMenu;
        if (!menu.getCarried().isEmpty()) {
            show(client, "message.better-shulker-hud.cursor_pickup_failed");
            clearCursorPickup();
            return;
        }
        int menuSlot = findPlayerInventoryMenuSlot(
                menu, client.player.getInventory(), pendingCursorPickup.inventorySlot());
        if (menuSlot < 0) {
            show(client, "message.better-shulker-hud.cursor_pickup_failed");
            clearCursorPickup();
            return;
        }

        ItemStack staged = menu.getSlot(menuSlot).getItem();
        int expectedCount = pendingCursorPickup.baselineCount()
                + pendingCursorPickup.extractedAmount();
        if (!ItemStack.isSameItemSameComponents(
                staged, pendingCursorPickup.prototype())
                || staged.getCount() != expectedCount) {
            if (++cursorPickupWaitTicks > 20) {
                show(client, "message.better-shulker-hud.cursor_pickup_failed");
                clearCursorPickup();
            }
            return;
        }

        client.gameMode.handleContainerInput(
                menu.containerId, menuSlot, 0, ContainerInput.PICKUP, client.player);
        for (int i = 0; i < pendingCursorPickup.baselineCount(); i++) {
            client.gameMode.handleContainerInput(
                    menu.containerId, menuSlot, 1, ContainerInput.PICKUP, client.player);
        }

        ItemStack carried = menu.getCarried();
        ItemStack restored = menu.getSlot(menuSlot).getItem();
        boolean restoredBaseline = pendingCursorPickup.baselineCount() == 0
                ? restored.isEmpty()
                : ItemStack.isSameItemSameComponents(
                restored, pendingCursorPickup.prototype())
                && restored.getCount() == pendingCursorPickup.baselineCount();
        if (ItemStack.isSameItemSameComponents(
                carried, pendingCursorPickup.prototype())
                && carried.getCount() == pendingCursorPickup.extractedAmount()
                && restoredBaseline) {
            clearCursorPickup();
            return;
        }

        if (!menu.getCarried().isEmpty()) {
            client.gameMode.handleContainerInput(
                    menu.containerId, menuSlot, 0, ContainerInput.PICKUP, client.player);
        }
        show(client, "message.better-shulker-hud.cursor_pickup_failed");
        clearCursorPickup();
    }

    private static void tickReturn(Minecraft client) {
        if (client.player == null || client.gameMode == null) {
            clearReturnProcess();
            return;
        }

        if (nextReturnDelay >= 0) {
            if (nextReturnDelay-- == 0) startNextReturn(client);
            return;
        }
        if (activeReturn == null) {
            startNextReturn(client);
            return;
        }

        if (!(client.player.containerMenu instanceof ShulkerBoxMenu menu)) {
            if (++returnWaitTicks > OPEN_TIMEOUT_TICKS) finishCurrentReturn(client);
            return;
        }
        if (returnMenuId != menu.containerId) {
            returnMenuId = menu.containerId;
            returnWaitTicks = 0;
        }
        if (!hasContainerSyncAfter(menu.containerId, returnOpenSyncVersion)) {
            if (++returnWaitTicks > OPEN_TIMEOUT_TICKS) finishCurrentReturn(client);
            return;
        }

        int targetSlot = findReturnTargetSlot(menu, activeReturn.shulkerSlot, activeReturn.prototype);
        if (targetSlot < 0) {
            finishCurrentReturn(client);
            return;
        }
        Slot target = menu.getSlot(targetSlot);
        ItemStack targetStack = target.getItem();

        int capacity = target.getMaxStackSize(activeReturn.prototype) - targetStack.getCount();
        int sourceMenuSlot = findMatchingPlayerItemSlot(
                menu, client.player.getInventory(), activeReturn.prototype, activeReturnShulkerSlot);
        if (capacity <= 0 || activeReturn.remaining <= 0 || sourceMenuSlot < 0) {
            finishCurrentReturn(client);
            return;
        }

        ItemStack sourceStack = menu.getSlot(sourceMenuSlot).getItem();
        int amount = Math.min(Math.min(sourceStack.getCount(), capacity), activeReturn.remaining);
        int before = sourceStack.getCount();
        moveExactAmount(client, menu, sourceMenuSlot, targetSlot, amount);
        int after = menu.getSlot(sourceMenuSlot).getItem().getCount();
        int moved = Math.max(0, before - after);
        if (moved <= 0) {
            finishCurrentReturn(client);
            return;
        }
        activeReturn.remaining -= moved;
        returnedItemCount += moved;
        if (activeReturn.remaining <= 0) originRecords.remove(activeReturn);
    }

    private static void startNextReturn(Minecraft client) {
        if (client.player == null) {
            clearReturnProcess();
            return;
        }
        if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
            client.setScreen(new InventoryScreen(client.player));
            nextReturnDelay = 0;
            return;
        }

        while (!returnQueue.isEmpty()) {
            OriginRecord record = returnQueue.removeFirst();
            if (record.remaining <= 0 || !hasMatchingPlayerItem(client.player.getInventory(), record.prototype)) {
                continue;
            }

            int shulkerInventorySlot = findOriginShulker(client.player.getInventory(), record);
            if (shulkerInventorySlot < 0) continue;
            int menuSlot = resolveQuickShulkerSlot(
                    screen, client.player.getInventory(), shulkerInventorySlot);
            if (menuSlot < 0) continue;

            activeReturn = record;
            activeReturnShulkerSlot = shulkerInventorySlot;
            returnWaitTicks = 0;
            returnMenuId = -1;
            nextReturnDelay = -1;
            returnOpenSyncVersion = containerSyncVersion;
            OpenShulkerPacket.sendOpenPacket(menuSlot);
            return;
        }

        int completed = returnedItemCount;
        clearReturnProcess();
        if (completed > 0) {
            show(client, "message.better-shulker-hud.return_complete", completed);
        } else {
            show(client, "message.better-shulker-hud.nothing_returned");
        }
    }

    private static void finishCurrentReturn(Minecraft client) {
        if (client.player != null && client.player.containerMenu instanceof ShulkerBoxMenu) {
            closeContainerAndSetScreen(
                    client, new InventoryScreen(client.player), true);
        }
        activeReturn = null;
        activeReturnShulkerSlot = -1;
        returnWaitTicks = 0;
        returnMenuId = -1;
        nextReturnDelay = 0;
    }

    private static StoreTarget findStoreTarget(
            Inventory inventory, ItemStack prototype, int amount) {
        StoreTarget emptyTarget = null;
        for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
            ItemStack shulker = inventory.getItem(inventorySlot);
            if (!ShulkerContentsHelper.isShulker(shulker)) continue;

            List<ItemStack> contents = ShulkerContentsHelper.getStacks(shulker);
            for (int shulkerSlot = 0; shulkerSlot < contents.size(); shulkerSlot++) {
                ItemStack current = contents.get(shulkerSlot);
                if (!current.isEmpty()
                        && ItemStack.isSameItemSameComponents(current, prototype)
                        && current.getMaxStackSize() - current.getCount() >= amount) {
                    return new StoreTarget(
                            inventorySlot, shulkerSlot, shulker.getItem(),
                            shulker.get(DataComponents.CUSTOM_NAME));
                }
                if (current.isEmpty() && emptyTarget == null
                        && prototype.getMaxStackSize() >= amount) {
                    emptyTarget = new StoreTarget(
                            inventorySlot, shulkerSlot, shulker.getItem(),
                            shulker.get(DataComponents.CUSTOM_NAME));
                }
            }
        }
        return emptyTarget;
    }

    private static PlayerDestination findTemporaryDestination(
            AbstractContainerScreen<?> screen, Inventory inventory,
            ItemStack carried, int targetShulkerInventorySlot) {
        PlayerDestination emptyDestination = null;
        for (int menuSlot = 0; menuSlot < screen.getMenu().slots.size(); menuSlot++) {
            Slot slot = screen.getMenu().slots.get(menuSlot);
            if (slot.container != inventory
                    || slot.getContainerSlot() == targetShulkerInventorySlot
                    || !slot.mayPlace(carried)) continue;

            ItemStack current = slot.getItem();
            if (!current.isEmpty()
                    && ItemStack.isSameItemSameComponents(current, carried)
                    && slot.getMaxStackSize(carried) - current.getCount() >= carried.getCount()) {
                return new PlayerDestination(menuSlot, slot.getContainerSlot());
            }
            if (current.isEmpty() && emptyDestination == null
                    && slot.getMaxStackSize(carried) >= carried.getCount()) {
                emptyDestination = new PlayerDestination(menuSlot, slot.getContainerSlot());
            }
        }
        return emptyDestination;
    }

    private static CursorStaging findCursorStagingDestination(
            AbstractContainerScreen<?> screen, Inventory inventory,
            ItemStack source, int sourceShulkerInventorySlot) {
        CursorStaging matching = null;
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container != inventory
                    || slot.getContainerSlot() < 0
                    || slot.getContainerSlot() >= 36
                    || slot.getContainerSlot() == sourceShulkerInventorySlot
                    || !slot.mayPlace(source)) continue;

            ItemStack current = slot.getItem();
            int capacity = slot.getMaxStackSize(source) - current.getCount();
            if (capacity <= 0) continue;
            if (current.isEmpty()) {
                return new CursorStaging(slot.getContainerSlot(), 0, capacity);
            }
            if (matching == null
                    && ItemStack.isSameItemSameComponents(current, source)) {
                matching = new CursorStaging(
                        slot.getContainerSlot(), current.getCount(), capacity);
            }
        }
        return matching;
    }

    private static int findExactPlayerItemSlot(
            ShulkerBoxMenu menu, Inventory inventory, int inventorySlot,
            ItemStack prototype, int targetShulkerInventorySlot) {
        for (int menuSlot = 0; menuSlot < menu.slots.size(); menuSlot++) {
            Slot slot = menu.slots.get(menuSlot);
            if (slot.container == inventory
                    && slot.getContainerSlot() == inventorySlot
                    && slot.getContainerSlot() != targetShulkerInventorySlot
                    && ItemStack.isSameItemSameComponents(slot.getItem(), prototype)) {
                return menuSlot;
            }
        }
        return -1;
    }

    private static boolean isExpectedStoreShulker(
            ShulkerBoxMenu menu, Inventory inventory, PendingStore store) {
        for (Slot slot : menu.slots) {
            if (slot.container != inventory
                    || slot.getContainerSlot() != store.targetInventorySlot()) continue;
            ItemStack shulker = slot.getItem();
            return ShulkerContentsHelper.isShulker(shulker)
                    && shulker.getItem() == store.shulkerItem()
                    && Objects.equals(shulker.get(DataComponents.CUSTOM_NAME), store.shulkerName());
        }
        return false;
    }

    private static void consumeOriginRecords(ItemStack prototype, int amount) {
        Iterator<OriginRecord> iterator = originRecords.iterator();
        while (iterator.hasNext() && amount > 0) {
            OriginRecord record = iterator.next();
            if (record.remaining <= 0
                    || !ItemStack.isSameItemSameComponents(record.prototype, prototype)) continue;
            int consumed = Math.min(record.remaining, amount);
            record.remaining -= consumed;
            amount -= consumed;
            if (record.remaining <= 0) iterator.remove();
        }
    }

    private static void recordExtraction(PendingExtraction extraction, int moved) {
        if (!Configs.Features.RETURN_HISTORY.getBooleanValue()
                || isExcludedFromReturn(extraction.expectedStack())) return;
        ItemStack prototype = extraction.expectedStack().copyWithCount(1);
        for (OriginRecord record : originRecords) {
            if (record.inventorySlot == extraction.inventorySlot()
                    && record.shulkerSlot == extraction.shulkerSlot()
                    && record.shulkerItem == extraction.shulkerItem()
                    && Objects.equals(record.shulkerName, extraction.shulkerName())
                    && ItemStack.isSameItemSameComponents(record.prototype, prototype)) {
                record.remaining += moved;
                return;
            }
        }
        originRecords.add(new OriginRecord(
                extraction.inventorySlot(), extraction.shulkerSlot(), prototype,
                extraction.shulkerItem(), extraction.shulkerName(), moved));
    }

    private static boolean isExcludedFromReturn(ItemStack stack) {
        return stack.has(DataComponents.TOOL)
                || stack.has(DataComponents.WEAPON)
                || stack.has(DataComponents.EQUIPPABLE)
                || stack.has(DataComponents.BLOCKS_ATTACKS)
                || stack.has(DataComponents.PIERCING_WEAPON)
                || stack.has(DataComponents.KINETIC_WEAPON)
                || stack.has(DataComponents.FOOD);
    }

    private static int findOriginShulker(Inventory inventory, OriginRecord record) {
        ItemStack originalSlotStack = inventory.getItem(record.inventorySlot);
        if (isOriginShulkerIdentity(originalSlotStack, record)) {
            return isMatchingOriginShulker(originalSlotStack, record)
                    ? record.inventorySlot : -1;
        }
        for (int slot = 0; slot < 36; slot++) {
            if (slot != record.inventorySlot && isMatchingOriginShulker(inventory.getItem(slot), record)) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean isMatchingOriginShulker(ItemStack shulker, OriginRecord record) {
        if (!isOriginShulkerIdentity(shulker, record)) return false;
        List<ItemStack> contents = ShulkerContentsHelper.getStacks(shulker);
        return findReturnTargetSlot(contents, record.shulkerSlot, record.prototype) >= 0;
    }

    private static boolean isOriginShulkerIdentity(ItemStack shulker, OriginRecord record) {
        return ShulkerContentsHelper.isShulker(shulker)
                && shulker.getItem() == record.shulkerItem
                && Objects.equals(shulker.get(DataComponents.CUSTOM_NAME), record.shulkerName);
    }

    private static int findReturnTargetSlot(
            ShulkerBoxMenu menu, int preferredSlot, ItemStack prototype) {
        if (preferredSlot >= 0 && preferredSlot < ShulkerContentsHelper.SHULKER_SIZE) {
            Slot preferred = menu.getSlot(preferredSlot);
            ItemStack stack = preferred.getItem();
            if ((stack.isEmpty() || ItemStack.isSameItemSameComponents(stack, prototype))
                    && preferred.getMaxStackSize(prototype) - stack.getCount() > 0) {
                return preferredSlot;
            }
        }

        int emptySlot = -1;
        for (int slotIndex = 0; slotIndex < ShulkerContentsHelper.SHULKER_SIZE; slotIndex++) {
            ItemStack stack = menu.getSlot(slotIndex).getItem();
            if (!stack.isEmpty()
                    && ItemStack.isSameItemSameComponents(stack, prototype)
                    && menu.getSlot(slotIndex).getMaxStackSize(prototype) - stack.getCount() > 0) {
                return slotIndex;
            }
            if (stack.isEmpty() && emptySlot < 0) emptySlot = slotIndex;
        }
        return emptySlot;
    }

    private static int findReturnTargetSlot(
            List<ItemStack> contents, int preferredSlot, ItemStack prototype) {
        if (preferredSlot >= 0 && preferredSlot < contents.size()) {
            ItemStack preferred = contents.get(preferredSlot);
            if ((preferred.isEmpty() || ItemStack.isSameItemSameComponents(preferred, prototype))
                    && prototype.getMaxStackSize() - preferred.getCount() > 0) {
                return preferredSlot;
            }
        }

        int emptySlot = -1;
        for (int slotIndex = 0; slotIndex < contents.size(); slotIndex++) {
            ItemStack stack = contents.get(slotIndex);
            if (!stack.isEmpty()
                    && ItemStack.isSameItemSameComponents(stack, prototype)
                    && prototype.getMaxStackSize() - stack.getCount() > 0) {
                return slotIndex;
            }
            if (stack.isEmpty() && emptySlot < 0) emptySlot = slotIndex;
        }
        return emptySlot;
    }

    private static boolean hasMatchingPlayerItem(Inventory inventory, ItemStack prototype) {
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (ItemStack.isSameItemSameComponents(stack, prototype)) return true;
        }
        return false;
    }

    private static int findMatchingPlayerItemSlot(
            ShulkerBoxMenu menu, Inventory inventory, ItemStack prototype, int shulkerInventorySlot) {
        for (int menuSlot = 0; menuSlot < menu.slots.size(); menuSlot++) {
            Slot slot = menu.slots.get(menuSlot);
            if (slot.container == inventory
                    && slot.getContainerSlot() != shulkerInventorySlot
                    && ItemStack.isSameItemSameComponents(slot.getItem(), prototype)) {
                return menuSlot;
            }
        }
        return -1;
    }

    private static void moveExactAmount(
            Minecraft client, ShulkerBoxMenu menu, int sourceSlot, int targetSlot, int amount) {
        ItemStack source = menu.getSlot(sourceSlot).getItem();
        int sourceCount = source.getCount();
        client.gameMode.handleContainerInput(
                menu.containerId, sourceSlot, 0, ContainerInput.PICKUP, client.player);
        if (amount == sourceCount) {
            client.gameMode.handleContainerInput(
                    menu.containerId, targetSlot, 0, ContainerInput.PICKUP, client.player);
        } else {
            for (int i = 0; i < amount; i++) {
                client.gameMode.handleContainerInput(
                        menu.containerId, targetSlot, 1, ContainerInput.PICKUP, client.player);
            }
        }
        if (!menu.getCarried().isEmpty()) {
            client.gameMode.handleContainerInput(
                    menu.containerId, sourceSlot, 0, ContainerInput.PICKUP, client.player);
        }
    }

    private static void takeOne(Minecraft client, ShulkerBoxMenu menu, int sourceSlot, int destinationSlot) {
        client.gameMode.handleContainerInput(
                menu.containerId, sourceSlot, 0, ContainerInput.PICKUP, client.player);
        client.gameMode.handleContainerInput(
                menu.containerId, destinationSlot, 1, ContainerInput.PICKUP, client.player);
        if (!menu.getCarried().isEmpty()) {
            client.gameMode.handleContainerInput(
                    menu.containerId, sourceSlot, 0, ContainerInput.PICKUP, client.player);
        }
    }

    private static int findDestinationSlot(
            ShulkerBoxMenu menu, Inventory inventory, ItemStack source, int shulkerInventorySlot) {
        int emptySlot = -1;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container != inventory || slot.getContainerSlot() == shulkerInventorySlot
                    || !slot.mayPlace(source)) continue;

            ItemStack current = slot.getItem();
            if (!current.isEmpty()
                    && ItemStack.isSameItemSameComponents(current, source)
                    && current.getCount() < slot.getMaxStackSize(source)) {
                return i;
            }
            if (current.isEmpty() && emptySlot < 0) emptySlot = i;
        }
        return emptySlot;
    }

    private static ResolvedSource findValidatedSource(
            AbstractContainerScreen<?> screen, Inventory inventory,
            BundlePanelRenderer.FlatItem item) {
        for (BundlePanelRenderer.ItemSource source : item.sources()) {
            if (source.inventorySlot() < 0 || source.inventorySlot() >= 36) continue;
            ItemStack shulker = inventory.getItem(source.inventorySlot());
            if (!ShulkerContentsHelper.isShulker(shulker)) continue;

            List<ItemStack> contents = ShulkerContentsHelper.getStacks(shulker);
            if (source.shulkerSlot() < 0 || source.shulkerSlot() >= contents.size()) continue;
            ItemStack current = contents.get(source.shulkerSlot());
            if (current.isEmpty()
                    || !ItemStack.isSameItemSameComponents(current, source.stack())) continue;

            int quickSlot = resolveQuickShulkerSlot(
                    screen, inventory, source.inventorySlot());
            if (quickSlot < 0) continue;
            return new ResolvedSource(
                    source.inventorySlot(), source.shulkerSlot(), current.copy(), quickSlot,
                    shulker.getItem(), shulker.get(DataComponents.CUSTOM_NAME));
        }

        ItemStack requested = item.stack().copyWithCount(1);
        for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
            ItemStack shulker = inventory.getItem(inventorySlot);
            if (!ShulkerContentsHelper.isShulker(shulker)) continue;
            List<ItemStack> contents = ShulkerContentsHelper.getStacks(shulker);
            for (int shulkerSlot = 0; shulkerSlot < contents.size(); shulkerSlot++) {
                ItemStack current = contents.get(shulkerSlot);
                if (current.isEmpty()
                        || !ItemStack.isSameItemSameComponents(current, requested)) continue;
                int quickSlot = resolveQuickShulkerSlot(screen, inventory, inventorySlot);
                if (quickSlot < 0) continue;
                return new ResolvedSource(
                        inventorySlot, shulkerSlot, current.copy(), quickSlot,
                        shulker.getItem(), shulker.get(DataComponents.CUSTOM_NAME));
            }
        }
        return null;
    }

    private static ResolvedSource findRestockSource(
            AbstractContainerMenu menu, Inventory inventory, ItemStack required) {
        for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
            ItemStack shulker = inventory.getItem(inventorySlot);
            if (!ShulkerContentsHelper.isShulker(shulker)) continue;
            List<ItemStack> contents = ShulkerContentsHelper.getStacks(shulker);
            for (int shulkerSlot = 0; shulkerSlot < contents.size(); shulkerSlot++) {
                ItemStack current = contents.get(shulkerSlot);
                if (current.isEmpty()
                        || !ItemStack.isSameItemSameComponents(current, required)) continue;
                int quickSlot = resolveQuickShulkerSlot(menu, inventory, inventorySlot);
                if (quickSlot < 0) continue;
                return new ResolvedSource(
                        inventorySlot, shulkerSlot, current.copy(), quickSlot,
                        shulker.getItem(), shulker.get(DataComponents.CUSTOM_NAME));
            }
        }
        return null;
    }

    private static int resolveQuickShulkerSlot(
            AbstractContainerScreen<?> screen, Inventory inventory, int inventorySlot) {
        int menuSlot = resolvePlayerMenuSlot(screen, inventory, inventorySlot);
        if (menuSlot < 0) return -1;
        return toQuickShulkerSlot(screen.getMenu(), menuSlot);
    }

    private static int resolveQuickShulkerSlot(
            AbstractContainerMenu menu, Inventory inventory, int inventorySlot) {
        if (menu == null || inventorySlot < 0 || inventorySlot >= 36) return -1;
        for (int menuSlot = 0; menuSlot < menu.slots.size(); menuSlot++) {
            Slot slot = menu.slots.get(menuSlot);
            if (slot.container == inventory && slot.getContainerSlot() == inventorySlot) {
                return toQuickShulkerSlot(menu, menuSlot);
            }
        }
        return -1;
    }

    private static int toQuickShulkerSlot(
            AbstractContainerMenu menu, int menuSlot) {
        if (menuSlot < 0 || menuSlot >= menu.slots.size()) return -1;
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && ClientUtil.isCreativeScreen(client.player)) {
            return ClientUtil.getSlotId(menu, menu.slots.get(menuSlot));
        }
        // QuickShulker indexes player.containerMenu.slots on the server. The list
        // position is stable for hotbar slots; Slot.index is not guaranteed to be.
        return menuSlot;
    }

    private static int resolvePlayerMenuSlot(
            AbstractContainerScreen<?> screen, Inventory inventory, int inventorySlot) {
        if (inventorySlot < 0 || inventorySlot >= 36) return -1;

        for (int menuSlot = 0; menuSlot < screen.getMenu().slots.size(); menuSlot++) {
            if (isMatchingPlayerSlot(screen, inventory, menuSlot, inventorySlot)) return menuSlot;
        }
        return -1;
    }

    private static int findPlayerInventoryMenuSlot(
            AbstractContainerMenu menu, Inventory inventory, int inventorySlot) {
        if (menu == null || inventorySlot < 0 || inventorySlot >= 36) return -1;
        for (int menuSlot = 0; menuSlot < menu.slots.size(); menuSlot++) {
            Slot slot = menu.slots.get(menuSlot);
            if (slot.container == inventory
                    && slot.getContainerSlot() == inventorySlot) return menuSlot;
        }
        return -1;
    }

    private static boolean isMatchingPlayerSlot(
            AbstractContainerScreen<?> screen, Inventory inventory,
            int menuSlot, int inventorySlot) {
        if (menuSlot < 0 || menuSlot >= screen.getMenu().slots.size()) return false;
        Slot slot = screen.getMenu().slots.get(menuSlot);
        return slot.container == inventory
                && slot.getContainerSlot() == inventorySlot;
    }

    private static boolean hasContainerSyncAfter(int containerId, long baseline) {
        return containerSyncVersions.getOrDefault(containerId, Long.MIN_VALUE) > baseline;
    }

    private static void failExtraction(Minecraft client, String messageKey) {
        boolean handRestock = pendingExtraction != null && pendingExtraction.handRestock();
        boolean background = pendingExtraction != null
                && (pendingExtraction.litematicaRestock() || handRestock);
        if (!handRestock) show(client, messageKey);
        if (client.player != null && client.player.containerMenu instanceof ShulkerBoxMenu) {
            closeContainerAndSetScreen(
                    client,
                    background ? null : new InventoryScreen(client.player),
                    !background);
        }
        clearExtraction();
    }

    private static void closeAfterExtraction(Minecraft client) {
        PendingExtraction completedExtraction = pendingExtraction;
        boolean litematicaRestock = completedExtraction != null
                && completedExtraction.litematicaRestock();
        boolean handRestock = completedExtraction != null
                && completedExtraction.handRestock();
        boolean cursorPickup = completedExtraction != null
                && completedExtraction.cursorPickup()
                && extractionMovedItemCount > 0;
        boolean background = litematicaRestock || handRestock;
        ItemStack selected = pendingLitematicaSelection;
        if (client.player != null) {
            closeContainerAndSetScreen(
                    client,
                    background ? null : new InventoryScreen(client.player),
                    !background);
        }
        if (cursorPickup) {
            pendingCursorPickup = new PendingCursorPickup(
                    completedExtraction.targetInventorySlot(),
                    completedExtraction.expectedStack().copyWithCount(1),
                    completedExtraction.targetBaselineCount(),
                    extractionMovedItemCount);
            cursorPickupWaitTicks = 0;
        }
        clearExtraction();
        if (litematicaRestock) selectLitematicaItem(client, selected);
    }

    private static void selectLitematicaItem(Minecraft client, ItemStack selected) {
        if (selected.isEmpty()) return;
        try {
            Class<?> inventoryUtils = Class.forName("fi.dy.masa.litematica.util.InventoryUtils");
            inventoryUtils.getMethod("setPickedItemToHand", ItemStack.class, Minecraft.class)
                    .invoke(null, selected, client);
        } catch (ReflectiveOperationException ignored) {
            // Litematica is optional and may be removed while the client is stopped.
        }
    }

    private static void clearExtraction() {
        pendingExtraction = null;
        extractionWaitTicks = 0;
        extractionMenuId = -1;
        extractionCloseDelay = -1;
        extractionMovedItemCount = 0;
        extractionOpenSyncVersion = 0;
        extractionMoveSyncVersion = 0;
        extractionExpectedSourceCount = -1;
        pendingLitematicaSelection = ItemStack.EMPTY;
    }

    private static void clearCursorPickup() {
        pendingCursorPickup = null;
        cursorPickupWaitTicks = 0;
    }

    private static void failStore(Minecraft client, String messageKey) {
        show(client, messageKey);
        if (client.player != null && client.player.containerMenu instanceof ShulkerBoxMenu) {
            closeContainerAndSetScreen(
                    client, new InventoryScreen(client.player), true);
        }
        clearStore();
    }

    private static void closeAfterStore(Minecraft client) {
        if (client.player != null) {
            closeContainerAndSetScreen(
                    client, new InventoryScreen(client.player), true);
        }
        int completed = storedItemCount;
        clearStore();
        if (completed > 0) {
            show(client, "message.better-shulker-hud.store_complete", completed);
        }
    }

    private static void closeContainerAndSetScreen(
            Minecraft client, Screen nextScreen, boolean preserveCursor) {
        long window = client.getWindow().handle();
        double[] cursorX = new double[1];
        double[] cursorY = new double[1];
        if (preserveCursor) {
            GLFW.glfwGetCursorPos(window, cursorX, cursorY);
        }

        if (client.player != null) client.player.closeContainer();
        client.setScreen(nextScreen);

        if (preserveCursor && nextScreen != null) {
            GLFW.glfwSetCursorPos(window, cursorX[0], cursorY[0]);
        }
    }

    private static void clearStore() {
        pendingStore = null;
        storeWaitTicks = 0;
        storeMenuId = -1;
        storeCloseDelay = -1;
        storedItemCount = 0;
        storeOpenSyncVersion = 0;
        storeMoveSyncVersion = 0;
        storeExpectedTargetCount = -1;
    }

    private static void clearReturnProcess() {
        returnQueue.clear();
        activeReturn = null;
        activeReturnShulkerSlot = -1;
        returnWaitTicks = 0;
        returnMenuId = -1;
        nextReturnDelay = -1;
        returnedItemCount = 0;
        returnOpenSyncVersion = 0;
    }

    private static void show(Minecraft client, String key, Object... args) {
        if (client.player != null) {
            client.player.sendOverlayMessage(Component.translatable(key, args));
        }
    }

    private record PendingExtraction(
            int inventorySlot, int shulkerSlot, ItemStack expectedStack, boolean takeOne,
            Item shulkerItem, Component shulkerName, boolean litematicaRestock,
            boolean handRestock, boolean cursorPickup, int targetInventorySlot,
            int requestedAmount, int targetBaselineCount) {}

    private record PendingCursorPickup(
            int inventorySlot, ItemStack prototype,
            int baselineCount, int extractedAmount) {}

    private record ResolvedSource(
            int inventorySlot, int shulkerSlot, ItemStack expectedStack, int quickShulkerSlot,
            Item shulkerItem, Component shulkerName) {}

    private record PendingStore(
            int sourceInventorySlot, int amount, int targetInventorySlot, int shulkerSlot,
            ItemStack prototype, Item shulkerItem, Component shulkerName) {}

    private record StoreTarget(
            int inventorySlot, int shulkerSlot, Item shulkerItem, Component shulkerName) {}

    private record PlayerDestination(int menuSlot, int inventorySlot) {}

    private record CursorStaging(
            int inventorySlot, int baselineCount, int capacity) {}

    private static final class OriginRecord {
        private final int inventorySlot;
        private final int shulkerSlot;
        private final ItemStack prototype;
        private final Item shulkerItem;
        private final Component shulkerName;
        private int remaining;

        private OriginRecord(
                int inventorySlot, int shulkerSlot, ItemStack prototype,
                Item shulkerItem, Component shulkerName, int remaining) {
            this.inventorySlot = inventorySlot;
            this.shulkerSlot = shulkerSlot;
            this.prototype = prototype;
            this.shulkerItem = shulkerItem;
            this.shulkerName = shulkerName;
            this.remaining = remaining;
        }
    }
}
