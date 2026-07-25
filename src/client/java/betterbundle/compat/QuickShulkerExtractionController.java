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
import net.minecraft.world.entity.item.ItemEntity;
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
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public final class QuickShulkerExtractionController {
    private static final int OPEN_TIMEOUT_TICKS = 60;
    private static final int AUTO_PICKUP_BATCH_IDLE_TICKS = 3;
    private static final int AUTO_PICKUP_STALE_SLOT_TICKS = 8;

    private static PendingExtraction pendingExtraction;
    private static int extractionWaitTicks;
    private static int extractionMenuId = -1;
    private static int extractionCloseDelay = -1;

    private static PendingStore pendingStore;
    private static int storeWaitTicks;
    private static int storeMenuId = -1;
    private static int storeCloseDelay = -1;
    private static int storedItemCount;

    private static PendingRestore pendingRestore;
    private static int restoreWaitTicks;
    private static int restoreMenuId = -1;
    private static int restoreCloseDelay = -1;

    private static int autoPickupCooldown;
    private static int autoRestockCooldown;
    private static AutoPickupCycle autoPickupCycle;
    private static boolean autoPickupProtocolWarningShown;

    private static final List<OriginRecord> originRecords = new ArrayList<>();
    private static final ArrayDeque<OriginRecord> returnQueue = new ArrayDeque<>();
    private static OriginRecord activeReturn;
    private static int activeReturnShulkerSlot = -1;
    private static int returnWaitTicks;
    private static int returnMenuId = -1;
    private static int nextReturnDelay = -1;
    private static int returnedItemCount;
    private static ItemStack pendingLitematicaSelection = ItemStack.EMPTY;

    private QuickShulkerExtractionController() {}

    public static void onClientTick(Minecraft client) {
        tick(client);
    }

    public static void clearWorldState() {
        clearExtraction();
        clearStore();
        clearRestore();
        clearReturnProcess();
        clearAutoPickupCycle();
        autoPickupCooldown = 0;
        autoRestockCooldown = 0;
        autoPickupProtocolWarningShown = false;
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
                source.shulkerItem(), source.shulkerName(), false, false, -1, 0);
        extractionWaitTicks = 0;
        extractionMenuId = -1;
        extractionCloseDelay = -1;
        OpenShulkerPacket.sendOpenPacket(source.quickShulkerSlot());
    }

    public static void requestToHand(BundlePanelRenderer.FlatItem item) {
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

        int targetInventorySlot = inventory.getSelectedSlot();
        ItemStack targetStack = inventory.getItem(targetInventorySlot);
        if (!targetStack.isEmpty()
                && !ItemStack.isSameItemSameComponents(
                targetStack, source.expectedStack())) {
            show(client, "message.better-shulker-hud.hand_unavailable");
            return;
        }
        int capacity = source.expectedStack().getMaxStackSize() - targetStack.getCount();
        int requestedAmount = Math.min(source.expectedStack().getCount(), capacity);
        if (requestedAmount <= 0) {
            show(client, "message.better-shulker-hud.hand_unavailable");
            return;
        }

        pendingExtraction = new PendingExtraction(
                source.inventorySlot(), source.shulkerSlot(), source.expectedStack(), false,
                source.shulkerItem(), source.shulkerName(), false, false,
                targetInventorySlot, requestedAmount);
        extractionWaitTicks = 0;
        extractionMenuId = -1;
        extractionCloseDelay = -1;
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
                source.shulkerItem(), source.shulkerName(), true, false, -1, 0);
        pendingLitematicaSelection = required.copyWithCount(1);
        extractionWaitTicks = 0;
        extractionMenuId = -1;
        extractionCloseDelay = -1;
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
                prototype, target.shulkerItem(), target.shulkerName(), null);
        storeWaitTicks = 0;
        storeMenuId = -1;
        storeCloseDelay = -1;
        storedItemCount = 0;
        OpenShulkerPacket.sendOpenPacket(targetMenuSlot);
    }

    private static boolean isBusy() {
        return pendingExtraction != null || pendingStore != null || pendingRestore != null
                || autoPickupCycle != null || activeReturn != null
                || !returnQueue.isEmpty() || nextReturnDelay >= 0;
    }

    public static boolean shouldHideQuickShulkerScreen() {
        return Configs.Features.HIDE_QUICK_SHULKER_SCREEN.getBooleanValue()
                && (pendingExtraction != null || pendingStore != null
                || pendingRestore != null || activeReturn != null);
    }

    private static boolean canUseQuickShulker(Minecraft client) {
        if (ClientPlayNetworking.canSend(OpenShulkerPacket.OPEN_SHULKER_PACKET_ID)) return true;
        show(client, "message.better-shulker-hud.quickshulker_required");
        return false;
    }

    private static void tick(Minecraft client) {
        if (pendingRestore != null) {
            tickRestore(client);
            return;
        } else if (pendingStore != null) {
            tickStore(client);
            return;
        } else if (pendingExtraction != null) {
            tickExtraction(client);
            return;
        } else if (activeReturn != null || !returnQueue.isEmpty() || nextReturnDelay >= 0) {
            tickReturn(client);
            return;
        }
        if (tickAutoRestock(client)) return;
        tickAutoPickup(client);
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
                source.shulkerItem(), source.shulkerName(), false, true,
                targetInventorySlot, requestedAmount);
        extractionWaitTicks = 0;
        extractionMenuId = -1;
        extractionCloseDelay = -1;
        OpenShulkerPacket.sendOpenPacket(source.quickShulkerSlot());
        return true;
    }

    private static void tickAutoPickup(Minecraft client) {
        if (client.player == null || client.level == null || client.gameMode == null) {
            clearAutoPickupCycle();
            return;
        }
        if (client.screen != null
                || client.player.containerMenu != client.player.inventoryMenu
                || client.player.isCreative() || client.player.isSpectator()) {
            return;
        }

        if (autoPickupCycle != null) {
            tickAutoPickupCycle(client);
            return;
        }
        if (!Configs.Features.AUTO_SHULKER_PICKUP.getBooleanValue()) return;
        if (autoPickupCooldown > 0) {
            autoPickupCooldown--;
            return;
        }
        autoPickupCooldown = autoPickupScanInterval();

        Inventory inventory = client.player.getInventory();
        double range = Configs.General.AUTO_PICKUP_RANGE.getDoubleValue();
        List<ItemEntity> nearbyItems = client.level.getEntitiesOfClass(
                ItemEntity.class,
                client.player.getBoundingBox().inflate(range),
                entity -> entity.isAlive() && !entity.hasPickUpDelay()
                        && !entity.getItem().isEmpty());

        for (ItemEntity entity : nearbyItems) {
            ItemStack pickup = entity.getItem().copyWithCount(1);
            if (canInventoryAccept(inventory, pickup)) continue;

            int pickupAmount = Math.min(
                    entity.getItem().getCount(), entity.getItem().getMaxStackSize());
            if (findStoreTarget(inventory, pickup, pickupAmount) == null) continue;

            if (!ClientPlayNetworking.canSend(OpenShulkerPacket.OPEN_SHULKER_PACKET_ID)) {
                if (!autoPickupProtocolWarningShown) {
                    show(client, "message.better-shulker-hud.auto_pickup_requires_quickshulker");
                    autoPickupProtocolWarningShown = true;
                }
                autoPickupCooldown = 20;
                return;
            }

            AutoStoreCandidate candidate = findAutoStoreCandidate(inventory, pickup, false);
            if (candidate == null) continue;

            if (startAutomaticPrepare(client, candidate)) {
                return;
            }
        }
    }

    private static void tickAutoPickupCycle(Minecraft client) {
        if (client.player == null || client.gameMode == null) {
            clearAutoPickupCycle();
            return;
        }
        if (autoPickupCycle.retryDelay > 0) {
            autoPickupCycle.retryDelay--;
            return;
        }

        Inventory inventory = client.player.getInventory();
        ItemStack pickedUp = inventory.getItem(autoPickupCycle.originalInventorySlot);
        if (pickedUp.isEmpty()) {
            if (autoPickupCycle.groundSnapshot.isEmpty()) {
                List<GroundItemRecord> nearby = snapshotNearbyGroundItems(client);
                if (!nearby.isEmpty()) {
                    autoPickupCycle.groundSnapshot = nearby;
                    autoPickupCycle.waitTicks = 0;
                    return;
                }
            }
            autoPickupCycle.waitTicks++;
            if (autoPickupCycle.storedPickup
                    && autoPickupCycle.waitTicks >= AUTO_PICKUP_BATCH_IDLE_TICKS
                    && autoPickupCycle.groundSnapshot.isEmpty()) {
                finishAutoPickupBatch(client);
                return;
            }
            if (autoPickupCycle.waitTicks > pickupTrackingTimeout()) {
                finishAutoPickupBatch(client);
            }
            return;
        }

        boolean confirmedPickup = !autoPickupCycle.confirmedPickup.isEmpty()
                && ItemStack.isSameItemSameComponents(
                autoPickupCycle.confirmedPickup, pickedUp);
        if (!confirmedPickup
                && !hasPickupEvidence(client, autoPickupCycle.groundSnapshot, pickedUp)) {
            autoPickupCycle.waitTicks++;
            if (autoPickupCycle.waitTicks > AUTO_PICKUP_STALE_SLOT_TICKS) {
                finishAutoPickupBatch(client);
            }
            return;
        }

        StoreTarget target = findStoreTarget(
                inventory, pickedUp.copyWithCount(1), pickedUp.getCount());
        if (target == null) {
            autoPickupCycle.waitTicks++;
            if (autoPickupCycle.waitTicks > pickupTrackingTimeout()) {
                finishAutoPickupBatch(client);
            }
            return;
        }

        autoPickupCycle.groundSnapshot = snapshotNearbyGroundItems(client);
        autoPickupCycle.confirmedPickup = pickedUp.copyWithCount(1);
        autoPickupCycle.waitTicks = 0;
        AutoStoreCandidate candidate = new AutoStoreCandidate(
                autoPickupCycle.originalInventorySlot, pickedUp.copy(), target);
        if (!startAutomaticStore(client, candidate, AutoStorePhase.STORE_PICKUP)) {
            autoPickupCooldown = autoPickupScanInterval();
        }
    }

    private static boolean startAutomaticPrepare(
            Minecraft client, AutoStoreCandidate candidate) {
        autoPickupCycle = new AutoPickupCycle(
                candidate.inventorySlot(), candidate.stack().copy(), candidate.target(),
                snapshotNearbyGroundItems(client));
        if (startAutomaticStore(client, candidate, AutoStorePhase.PREPARE_SLOT)) return true;
        clearAutoPickupCycle();
        return false;
    }

    private static boolean startAutomaticStore(
            Minecraft client, AutoStoreCandidate candidate, AutoStorePhase phase) {
        if (client.player == null) return false;
        Inventory inventory = client.player.getInventory();
        StoreTarget target = candidate.target();
        int targetMenuSlot = resolveQuickShulkerSlot(
                client.player.containerMenu, inventory, target.inventorySlot());
        if (targetMenuSlot < 0) return false;

        ItemStack source = candidate.stack();
        pendingStore = new PendingStore(
                candidate.inventorySlot(), source.getCount(),
                target.inventorySlot(), target.shulkerSlot(),
                source.copyWithCount(1), target.shulkerItem(), target.shulkerName(), phase);
        storeWaitTicks = 0;
        storeMenuId = -1;
        storeCloseDelay = -1;
        storedItemCount = 0;
        OpenShulkerPacket.sendOpenPacket(targetMenuSlot);
        return true;
    }

    private static boolean startAutomaticRestore(Minecraft client) {
        if (client.player == null || autoPickupCycle == null) return false;
        Inventory inventory = client.player.getInventory();
        StoreTarget source = autoPickupCycle.originalTarget;
        int quickSlot = resolveQuickShulkerSlot(
                client.player.containerMenu, inventory, source.inventorySlot());
        if (quickSlot < 0) {
            clearAutoPickupCycle();
            return false;
        }

        pendingRestore = new PendingRestore(
                source.inventorySlot(), source.shulkerSlot(),
                autoPickupCycle.originalInventorySlot,
                autoPickupCycle.originalStack.copyWithCount(1),
                autoPickupCycle.originalStack.getCount(),
                source.shulkerItem(), source.shulkerName());
        restoreWaitTicks = 0;
        restoreMenuId = -1;
        restoreCloseDelay = -1;
        OpenShulkerPacket.sendOpenPacket(quickSlot);
        return true;
    }

    private static int autoPickupScanInterval() {
        return Configs.General.AUTO_PICKUP_SCAN_INTERVAL.getIntegerValue();
    }

    private static int pickupTrackingTimeout() {
        return Configs.General.PICKUP_TRACKING_TIMEOUT.getIntegerValue();
    }

    private static void finishAutoPickupBatch(Minecraft client) {
        if (!startAutomaticRestore(client)) {
            clearAutoPickupCycle();
            autoPickupCooldown = autoPickupScanInterval();
        }
    }

    private static List<GroundItemRecord> snapshotNearbyGroundItems(Minecraft client) {
        if (client.player == null || client.level == null) return List.of();
        double range = Configs.General.AUTO_PICKUP_RANGE.getDoubleValue();
        List<ItemEntity> items = client.level.getEntitiesOfClass(
                ItemEntity.class,
                client.player.getBoundingBox().inflate(range),
                entity -> entity.isAlive() && !entity.hasPickUpDelay()
                        && !entity.getItem().isEmpty());
        List<GroundItemRecord> snapshot = new ArrayList<>(items.size());
        for (ItemEntity entity : items) {
            snapshot.add(new GroundItemRecord(
                    entity.getId(), entity.getItem().copyWithCount(1),
                    entity.getItem().getCount()));
        }
        return List.copyOf(snapshot);
    }

    private static boolean hasPickupEvidence(
            Minecraft client, List<GroundItemRecord> snapshot, ItemStack pickedUp) {
        if (client.level == null || snapshot.isEmpty()) return false;
        for (GroundItemRecord record : snapshot) {
            if (!ItemStack.isSameItemSameComponents(record.prototype(), pickedUp)) continue;
            if (!(client.level.getEntity(record.entityId()) instanceof ItemEntity entity)
                    || !entity.isAlive()
                    || entity.getItem().getCount() < record.count()) {
                return true;
            }
        }
        return false;
    }

    private static AutoStoreCandidate findAutoStoreCandidate(
            Inventory inventory, ItemStack pickup, boolean matchingOnly) {
        int selectedSlot = inventory.getSelectedSlot();
        AutoStoreCandidate candidate = findAutoStoreCandidate(
                inventory, pickup, 9, 36, selectedSlot, true);
        if (candidate != null) return candidate;
        candidate = findAutoStoreCandidate(
                inventory, pickup, 0, 9, selectedSlot, true);
        if (candidate != null || matchingOnly) return candidate;

        candidate = findAutoStoreCandidate(
                inventory, pickup, 9, 36, selectedSlot, false);
        if (candidate != null) return candidate;
        return findAutoStoreCandidate(
                inventory, pickup, 0, 9, selectedSlot, false);
    }

    private static AutoStoreCandidate findAutoStoreCandidate(
            Inventory inventory, ItemStack pickup, int startSlot, int endSlot,
            int selectedSlot, boolean requireMatch) {
        for (int inventorySlot = startSlot; inventorySlot < endSlot; inventorySlot++) {
            if (inventorySlot == selectedSlot) continue;
            ItemStack stack = inventory.getItem(inventorySlot);
            if (stack.isEmpty() || ShulkerContentsHelper.isShulker(stack)
                    || stack.getMaxStackSize() <= 1) continue;

            boolean matchesPickup = ItemStack.isSameItemSameComponents(stack, pickup);
            if (requireMatch != matchesPickup) continue;
            if (!matchesPickup && isExcludedFromReturn(stack)) continue;

            StoreTarget target = findStoreTarget(
                    inventory, stack.copyWithCount(1), stack.getCount());
            if (target != null) {
                return new AutoStoreCandidate(inventorySlot, stack.copy(), target);
            }
        }
        return null;
    }

    private static boolean canInventoryAccept(Inventory inventory, ItemStack pickup) {
        if (inventory.getFreeSlot() >= 0) return true;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (!current.isEmpty()
                    && ItemStack.isSameItemSameComponents(current, pickup)
                    && current.getCount() < current.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private static void clearAutoPickupCycle() {
        autoPickupCycle = null;
    }

    private static void tickStore(Minecraft client) {
        if (client.player == null || client.gameMode == null) {
            clearStore();
            return;
        }

        if (storeCloseDelay >= 0) {
            if (storeCloseDelay-- == 0) closeAfterStore(client);
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
        moveExactAmount(client, menu, sourceMenuSlot, pendingStore.shulkerSlot(), pendingStore.amount());
        int moved = before - menu.getSlot(sourceMenuSlot).getItem().getCount();
        if (moved != pendingStore.amount()) {
            failStore(client, "message.better-shulker-hud.store_failed");
            return;
        }

        storedItemCount = moved;
        if (!pendingStore.automatic()) {
            consumeOriginRecords(pendingStore.prototype(), moved);
        }
        storeCloseDelay = 0;
    }

    private static void tickRestore(Minecraft client) {
        if (client.player == null || client.gameMode == null) {
            clearRestore();
            clearAutoPickupCycle();
            return;
        }

        if (restoreCloseDelay >= 0) {
            if (restoreCloseDelay-- == 0) closeAfterRestore(client);
            return;
        }

        if (!(client.player.containerMenu instanceof ShulkerBoxMenu menu)) {
            if (++restoreWaitTicks > OPEN_TIMEOUT_TICKS) failRestore(client);
            return;
        }
        if (restoreMenuId != menu.containerId) {
            restoreMenuId = menu.containerId;
            restoreWaitTicks = 0;
        }

        if (!isExpectedRestoreShulker(menu, client.player.getInventory(), pendingRestore)) {
            failRestore(client);
            return;
        }

        int sourceSlot = findRestoreSourceSlot(menu, pendingRestore);
        int destinationSlot = findExactPlayerDestinationSlot(
                menu, client.player.getInventory(), pendingRestore.destinationInventorySlot(),
                pendingRestore.prototype(), pendingRestore.sourceInventorySlot());
        if (sourceSlot < 0 || destinationSlot < 0) {
            failRestore(client);
            return;
        }

        int before = menu.getSlot(sourceSlot).getItem().getCount();
        moveExactAmount(client, menu, sourceSlot, destinationSlot, pendingRestore.amount());
        int moved = before - menu.getSlot(sourceSlot).getItem().getCount();
        if (moved != pendingRestore.amount()) {
            failRestore(client);
            return;
        }
        restoreCloseDelay = 0;
    }

    private static void tickExtraction(Minecraft client) {
        if (client.player == null || client.gameMode == null) {
            clearExtraction();
            return;
        }

        if (extractionCloseDelay >= 0) {
            if (extractionCloseDelay-- == 0) closeAfterExtraction(client);
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

        Slot source = menu.getSlot(pendingExtraction.shulkerSlot());
        ItemStack sourceStack = source.getItem();
        if (sourceStack.isEmpty()
                || !ItemStack.isSameItemSameComponents(sourceStack, pendingExtraction.expectedStack())) {
            failExtraction(client, "message.better-shulker-hud.source_changed");
            return;
        }

        int before = sourceStack.getCount();
        if (pendingExtraction.targetInventorySlot() >= 0) {
            int destination = findTargetedHandDestination(
                    menu, client.player.getInventory(), pendingExtraction);
            if (destination < 0) {
                failExtraction(client, "message.better-shulker-hud.hand_unavailable");
                return;
            }
            ItemStack targetStack = menu.getSlot(destination).getItem();
            int amount = Math.min(
                    pendingExtraction.requestedAmount(),
                    Math.min(sourceStack.getCount(),
                            menu.getSlot(destination).getMaxStackSize(sourceStack)
                                    - targetStack.getCount()));
            if (amount <= 0) {
                failExtraction(client, "message.better-shulker-hud.hand_unavailable");
                return;
            }
            moveExactAmount(client, menu, pendingExtraction.shulkerSlot(), destination, amount);
            int moved = before - menu.getSlot(pendingExtraction.shulkerSlot()).getItem().getCount();
            if (moved != amount) {
                failExtraction(client, "message.better-shulker-hud.hand_unavailable");
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
        if (moved > 0 && !pendingExtraction.handRestock()) {
            recordExtraction(pendingExtraction, moved);
        }
        extractionCloseDelay = 0;
    }

    private static int findTargetedHandDestination(
            ShulkerBoxMenu menu, Inventory inventory, PendingExtraction extraction) {
        if (extraction.targetInventorySlot() < 0
                || extraction.targetInventorySlot() >= 9) return -1;
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

    private static boolean isExpectedRestoreShulker(
            ShulkerBoxMenu menu, Inventory inventory, PendingRestore restore) {
        for (Slot slot : menu.slots) {
            if (slot.container != inventory
                    || slot.getContainerSlot() != restore.sourceInventorySlot()) continue;
            ItemStack shulker = slot.getItem();
            return ShulkerContentsHelper.isShulker(shulker)
                    && shulker.getItem() == restore.shulkerItem()
                    && Objects.equals(
                    shulker.get(DataComponents.CUSTOM_NAME), restore.shulkerName());
        }
        return false;
    }

    private static int findRestoreSourceSlot(
            ShulkerBoxMenu menu, PendingRestore restore) {
        int preferred = restore.preferredShulkerSlot();
        if (preferred >= 0 && preferred < ShulkerContentsHelper.SHULKER_SIZE) {
            ItemStack stack = menu.getSlot(preferred).getItem();
            if (ItemStack.isSameItemSameComponents(stack, restore.prototype())
                    && stack.getCount() >= restore.amount()) {
                return preferred;
            }
        }
        for (int slot = 0; slot < ShulkerContentsHelper.SHULKER_SIZE; slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (ItemStack.isSameItemSameComponents(stack, restore.prototype())
                    && stack.getCount() >= restore.amount()) {
                return slot;
            }
        }
        return -1;
    }

    private static int findExactPlayerDestinationSlot(
            ShulkerBoxMenu menu, Inventory inventory, int inventorySlot,
            ItemStack prototype, int sourceShulkerInventorySlot) {
        for (int menuSlot = 0; menuSlot < menu.slots.size(); menuSlot++) {
            Slot slot = menu.slots.get(menuSlot);
            if (slot.container != inventory
                    || slot.getContainerSlot() != inventorySlot
                    || slot.getContainerSlot() == sourceShulkerInventorySlot
                    || !slot.mayPlace(prototype)) continue;
            ItemStack current = slot.getItem();
            if (current.isEmpty()
                    || (ItemStack.isSameItemSameComponents(current, prototype)
                    && slot.getMaxStackSize(prototype) - current.getCount()
                    >= pendingRestore.amount())) {
                return menuSlot;
            }
        }
        return -1;
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
        return ClientUtil.getSlotId(screen.getMenu(), screen.getMenu().slots.get(menuSlot));
    }

    private static int resolveQuickShulkerSlot(
            AbstractContainerMenu menu, Inventory inventory, int inventorySlot) {
        if (menu == null || inventorySlot < 0 || inventorySlot >= 36) return -1;
        for (Slot slot : menu.slots) {
            if (slot.container == inventory && slot.getContainerSlot() == inventorySlot && slot.hasItem()) {
                return ClientUtil.getSlotId(menu, slot);
            }
        }
        return -1;
    }

    private static int resolvePlayerMenuSlot(
            AbstractContainerScreen<?> screen, Inventory inventory, int inventorySlot) {
        if (inventorySlot < 0 || inventorySlot >= 36) return -1;

        for (int menuSlot = 0; menuSlot < screen.getMenu().slots.size(); menuSlot++) {
            if (isMatchingPlayerSlot(screen, inventory, menuSlot, inventorySlot)) return menuSlot;
        }
        return -1;
    }

    private static boolean isMatchingPlayerSlot(
            AbstractContainerScreen<?> screen, Inventory inventory,
            int menuSlot, int inventorySlot) {
        if (menuSlot < 0 || menuSlot >= screen.getMenu().slots.size()) return false;
        Slot slot = screen.getMenu().slots.get(menuSlot);
        return slot.container == inventory
                && slot.getContainerSlot() == inventorySlot
                && slot.hasItem();
    }

    private static void failExtraction(Minecraft client, String messageKey) {
        boolean handRestock = pendingExtraction != null && pendingExtraction.handRestock();
        if (!handRestock) show(client, messageKey);
        if (client.player != null && client.player.containerMenu instanceof ShulkerBoxMenu) {
            closeAfterExtraction(client);
        } else {
            clearExtraction();
        }
    }

    private static void closeAfterExtraction(Minecraft client) {
        boolean litematicaRestock = pendingExtraction != null
                && pendingExtraction.litematicaRestock();
        boolean handRestock = pendingExtraction != null
                && pendingExtraction.handRestock();
        boolean background = litematicaRestock || handRestock;
        ItemStack selected = pendingLitematicaSelection;
        if (client.player != null) {
            closeContainerAndSetScreen(
                    client,
                    background ? null : new InventoryScreen(client.player),
                    !background);
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
        pendingLitematicaSelection = ItemStack.EMPTY;
    }

    private static void failStore(Minecraft client, String messageKey) {
        boolean automatic = pendingStore != null && pendingStore.automatic();
        AutoStorePhase failedPhase = automatic ? pendingStore.autoPhase() : null;
        if (!automatic) show(client, messageKey);
        if (client.player != null && client.player.containerMenu instanceof ShulkerBoxMenu) {
            closeContainerAndSetScreen(
                    client,
                    automatic ? null : new InventoryScreen(client.player),
                    !automatic);
        }
        clearStore();
        if (automatic) {
            if (failedPhase == AutoStorePhase.STORE_PICKUP
                    && autoPickupCycle != null) {
                autoPickupCycle.waitTicks = 0;
                autoPickupCycle.retryDelay = AUTO_PICKUP_STALE_SLOT_TICKS;
            } else {
                clearAutoPickupCycle();
                autoPickupCooldown = 20;
            }
        }
    }

    private static void closeAfterStore(Minecraft client) {
        PendingStore completedStore = pendingStore;
        boolean automatic = completedStore != null && completedStore.automatic();
        AutoStorePhase autoPhase = automatic ? completedStore.autoPhase() : null;
        if (client.player != null) {
            closeContainerAndSetScreen(
                    client,
                    automatic ? null : new InventoryScreen(client.player),
                    !automatic);
        }
        int completed = storedItemCount;
        clearStore();
        if (automatic) {
            autoPickupCooldown = 0;
            if (autoPickupCycle != null) {
                autoPickupCycle.waitTicks = 0;
                autoPickupCycle.retryDelay = 0;
                if (autoPhase == AutoStorePhase.STORE_PICKUP) {
                    autoPickupCycle.storedPickup = true;
                    autoPickupCycle.confirmedPickup = ItemStack.EMPTY;
                }
            }
        } else if (completed > 0) {
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
    }

    private static void failRestore(Minecraft client) {
        if (client.player != null && client.player.containerMenu instanceof ShulkerBoxMenu) {
            closeContainerAndSetScreen(client, null, false);
        }
        clearRestore();
        clearAutoPickupCycle();
        autoPickupCooldown = 20;
    }

    private static void closeAfterRestore(Minecraft client) {
        if (client.player != null) {
            closeContainerAndSetScreen(client, null, false);
        }
        clearRestore();
        clearAutoPickupCycle();
        autoPickupCooldown = 0;
    }

    private static void clearRestore() {
        pendingRestore = null;
        restoreWaitTicks = 0;
        restoreMenuId = -1;
        restoreCloseDelay = -1;
    }

    private static void clearReturnProcess() {
        returnQueue.clear();
        activeReturn = null;
        activeReturnShulkerSlot = -1;
        returnWaitTicks = 0;
        returnMenuId = -1;
        nextReturnDelay = -1;
        returnedItemCount = 0;
    }

    private static void show(Minecraft client, String key, Object... args) {
        if (client.player != null) {
            client.player.sendOverlayMessage(Component.translatable(key, args));
        }
    }

    private record PendingExtraction(
            int inventorySlot, int shulkerSlot, ItemStack expectedStack, boolean takeOne,
            Item shulkerItem, Component shulkerName, boolean litematicaRestock,
            boolean handRestock, int targetInventorySlot, int requestedAmount) {}

    private record ResolvedSource(
            int inventorySlot, int shulkerSlot, ItemStack expectedStack, int quickShulkerSlot,
            Item shulkerItem, Component shulkerName) {}

    private record PendingStore(
            int sourceInventorySlot, int amount, int targetInventorySlot, int shulkerSlot,
            ItemStack prototype, Item shulkerItem, Component shulkerName,
            AutoStorePhase autoPhase) {
        private boolean automatic() {
            return autoPhase != null;
        }
    }

    private record PendingRestore(
            int sourceInventorySlot, int preferredShulkerSlot,
            int destinationInventorySlot, ItemStack prototype, int amount,
            Item shulkerItem, Component shulkerName) {}

    private record StoreTarget(
            int inventorySlot, int shulkerSlot, Item shulkerItem, Component shulkerName) {}

    private record PlayerDestination(int menuSlot, int inventorySlot) {}

    private record AutoStoreCandidate(
            int inventorySlot, ItemStack stack, StoreTarget target) {}

    private record GroundItemRecord(
            int entityId, ItemStack prototype, int count) {}

    private enum AutoStorePhase {
        PREPARE_SLOT,
        STORE_PICKUP
    }

    private static final class AutoPickupCycle {
        private final int originalInventorySlot;
        private final ItemStack originalStack;
        private final StoreTarget originalTarget;
        private List<GroundItemRecord> groundSnapshot;
        private ItemStack confirmedPickup = ItemStack.EMPTY;
        private boolean storedPickup;
        private int retryDelay;
        private int waitTicks;

        private AutoPickupCycle(
                int originalInventorySlot, ItemStack originalStack,
                StoreTarget originalTarget, List<GroundItemRecord> groundSnapshot) {
            this.originalInventorySlot = originalInventorySlot;
            this.originalStack = originalStack;
            this.originalTarget = originalTarget;
            this.groundSnapshot = groundSnapshot;
        }
    }

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
