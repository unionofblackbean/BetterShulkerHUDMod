package bettershulkerhud.compat;

import bettershulkerhud.BetterBundleMod;
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
import net.minecraft.core.registries.BuiltInRegistries;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class QuickShulkerExtractionController {
    private static final int OPEN_TIMEOUT_TICKS = 60;
    // Keep the interaction sequence server-authoritative, but finish a normal
    // stack in a few ticks instead of spreading it over eight-click batches.
    private static final int MAX_STORE_CLICKS_PER_TICK = 32;
    private static final String DIAGNOSTIC_PREFIX = "[Better Shulker HUD Diagnostics]";
    private static long containerSyncVersion;
    private static final Map<Integer, Long> containerSyncVersions = new HashMap<>();
    private static long diagnosticOperationSequence;
    private static long extractionOperationId;
    private static long cursorPickupOperationId;
    private static long storeOperationId;

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
    private static StoreTransfer storeTransfer;
    private static long storeOpenSyncVersion;
    private static long storeMoveSyncVersion;
    private static int storeExpectedTargetCount = -1;
    private static boolean storeContinueAfterMove;

    private static DeferredExtraction deferredExtraction;
    private static int deferredExtractionDelay = -1;
    private static int deferredExtractionMenuId = -1;

    private static boolean organizeActive;
    private static int organizeDelay = -1;
    private static int organizedItemCount;
    private static int organizeRetryCount;
    private static long organizeAvailabilityFingerprint = Long.MIN_VALUE;
    private static boolean cachedOrganizeAvailability;

    private static int autoRestockCooldown;
    private static boolean preserveInventoryScreenDuringContainerClose;

    private static final List<OriginRecord> originRecords = new ArrayList<>();
    private static final ArrayDeque<OriginRecord> returnQueue = new ArrayDeque<>();
    private static OriginRecord activeReturn;
    private static int activeReturnShulkerSlot = -1;
    private static int returnWaitTicks;
    private static int returnMenuId = -1;
    private static int nextReturnDelay = -1;
    private static int returnedItemCount;
    private static long returnOpenSyncVersion;
    private static long returnMoveSyncVersion;
    private static boolean returnAwaitingSync;
    private static int returnExpectedTargetSlot = -1;
    private static int returnExpectedTargetCount = -1;
    private static int returnPendingMoved;
    private static ItemStack pendingLitematicaSelection = ItemStack.EMPTY;

    private QuickShulkerExtractionController() {}

    public static void onClientTick(Minecraft client) {
        tick(client);
    }

    public static void onContainerSync(int containerId) {
        containerSyncVersions.put(containerId, ++containerSyncVersion);
        long operationId = activeDiagnosticOperationId();
        if (operationId != 0) {
            diagnostic(operationId,
                    "container-sync menu=%d version=%d extractionOpenBaseline=%d extractionMoveBaseline=%d storeOpenBaseline=%d storeMoveBaseline=%d",
                    containerId, containerSyncVersion, extractionOpenSyncVersion,
                    extractionMoveSyncVersion, storeOpenSyncVersion, storeMoveSyncVersion);
        }
        organizeAvailabilityFingerprint = Long.MIN_VALUE;
        BundlePanelRenderer.invalidateContentsCache();
    }

    public static void clearWorldState() {
        diagnostic(activeDiagnosticOperationId(), "world-state-cleared");
        clearExtraction();
        clearCursorPickup();
        clearStore();
        clearDeferredExtraction();
        clearReturnProcess();
        clearOrganizeProcess();
        autoRestockCooldown = 0;
        containerSyncVersion = 0;
        containerSyncVersions.clear();
        organizeAvailabilityFingerprint = Long.MIN_VALUE;
        cachedOrganizeAvailability = false;
        originRecords.clear();
        pendingLitematicaSelection = ItemStack.EMPTY;
        extractionOperationId = 0;
        cursorPickupOperationId = 0;
        storeOperationId = 0;
        BundlePanelRenderer.invalidateCache();
    }

    public static boolean hasReturnableHistory() {
        return Configs.Features.RETURN_HISTORY.getBooleanValue()
                && originRecords.stream().anyMatch(record -> record.remaining > 0);
    }

    public static boolean canOrganizeInventory() {
        Minecraft client = Minecraft.getInstance();
        if (hasReturnableHistory()) return true;
        if (client.player == null) return false;
        Inventory inventory = client.player.getInventory();
        long fingerprint = inventoryFingerprint(inventory);
        if (fingerprint != organizeAvailabilityFingerprint) {
            organizeAvailabilityFingerprint = fingerprint;
            cachedOrganizeAvailability = findNextMatchingStore(inventory) != null;
        }
        return cachedOrganizeAvailability;
    }

    private static long inventoryFingerprint(Inventory inventory) {
        long fingerprint = 1L;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            fingerprint = 31L * fingerprint + ItemStack.hashItemAndComponents(stack);
            fingerprint = 31L * fingerprint + stack.getCount();
        }
        return fingerprint;
    }

    public static void request(BundlePanelRenderer.FlatItem item, boolean takeOne) {
        Minecraft client = Minecraft.getInstance();
        long operationId = nextDiagnosticOperationId();
        diagnostic(operationId, "request type=%s target=%s displayedCount=%d",
                takeOne ? "left-one" : "shift-left-stack",
                itemId(item.stack()), item.stack().getCount());
        if (client.player == null || !(client.screen instanceof AbstractContainerScreen<?> screen)) {
            diagnostic(operationId, "request-rejected reason=no-player-or-container-screen");
            return;
        }

        if (isBusy()) {
            diagnostic(operationId, "request-rejected reason=controller-busy activeOperation=%d",
                    activeDiagnosticOperationId());
            show(client, "message.better-shulker-hud.busy");
            return;
        }
        if (!canUseQuickShulker(client, operationId)) return;

        startInventoryExtraction(client, screen, item, takeOne, true, operationId);
    }

    private static void startInventoryExtraction(
            Minecraft client, AbstractContainerScreen<?> screen,
            BundlePanelRenderer.FlatItem item, boolean takeOne, boolean allowClear,
            long operationId) {
        if (client.player == null) {
            diagnostic(operationId, "extraction-rejected reason=player-missing");
            return;
        }

        ResolvedSource source = findValidatedSource(
                screen, client.player.getInventory(), item);
        if (source == null) {
            diagnostic(operationId, "extraction-rejected reason=source-not-resolved target=%s",
                    itemId(item.stack()));
            show(client, "message.better-shulker-hud.source_missing");
            return;
        }
        diagnostic(operationId,
                "source-resolved item=%s count=%d inventorySlot=%d shulkerSlot=%d quickShulkerSlot=%d",
                itemId(source.expectedStack()), source.expectedStack().getCount(),
                source.inventorySlot(), source.shulkerSlot(), source.quickShulkerSlot());

        int capacity = inventoryCapacity(
                screen, client.player.getInventory(), source.expectedStack(), source.inventorySlot());
        int requiredCapacity = takeOne ? 1 : source.expectedStack().getCount();
        if (capacity < requiredCapacity) {
            diagnostic(operationId,
                    "inventory-capacity-insufficient capacity=%d required=%d allowClear=%s",
                    capacity, requiredCapacity, allowClear);
            if (allowClear && beginExtractionClearance(
                    client, screen, item, takeOne, false, operationId)) return;
            show(client, allowClear
                    ? "message.better-shulker-hud.cannot_free_slot"
                    : "message.better-shulker-hud.inventory_full");
            return;
        }

        pendingExtraction = new PendingExtraction(
                source.inventorySlot(), source.shulkerSlot(), source.expectedStack(), takeOne,
                source.shulkerItem(), source.shulkerName(), false, false, false,
                -1, 0, 0);
        extractionOperationId = operationId;
        extractionWaitTicks = 0;
        extractionMenuId = -1;
        extractionCloseDelay = -1;
        extractionMovedItemCount = 0;
        extractionExpectedSourceCount = -1;
        extractionOpenSyncVersion = containerSyncVersion;
        diagnostic(operationId,
                "packet-send open-shulker quickShulkerSlot=%d canSend=%s openSyncBaseline=%d target=inventory requested=%d",
                source.quickShulkerSlot(), canSendQuickShulker(), extractionOpenSyncVersion,
                takeOne ? 1 : source.expectedStack().getCount());
        OpenShulkerPacket.sendOpenPacket(source.quickShulkerSlot());
        diagnostic(operationId, "packet-sent open-shulker");
    }

    public static void requestToCursor(BundlePanelRenderer.FlatItem item) {
        Minecraft client = Minecraft.getInstance();
        long operationId = nextDiagnosticOperationId();
        diagnostic(operationId, "request type=right-click-cursor target=%s displayedCount=%d",
                itemId(item.stack()), item.stack().getCount());
        if (client.player == null
                || !(client.screen instanceof AbstractContainerScreen<?> screen)) {
            diagnostic(operationId, "request-rejected reason=no-player-or-container-screen");
            return;
        }
        if (!screen.getMenu().getCarried().isEmpty()) {
            diagnostic(operationId, "request-rejected reason=cursor-not-empty item=%s count=%d",
                    itemId(screen.getMenu().getCarried()), screen.getMenu().getCarried().getCount());
            return;
        }

        if (isBusy()) {
            diagnostic(operationId, "request-rejected reason=controller-busy activeOperation=%d",
                    activeDiagnosticOperationId());
            show(client, "message.better-shulker-hud.busy");
            return;
        }
        if (!canUseQuickShulker(client, operationId)) return;

        startCursorExtraction(client, screen, item, true, operationId);
    }

    private static void startCursorExtraction(
            Minecraft client, AbstractContainerScreen<?> screen,
            BundlePanelRenderer.FlatItem item, boolean allowClear, long operationId) {
        if (client.player == null || !screen.getMenu().getCarried().isEmpty()) {
            diagnostic(operationId, "cursor-extraction-rejected reason=player-missing-or-cursor-not-empty");
            return;
        }

        Inventory inventory = client.player.getInventory();
        ResolvedSource source = findValidatedSource(screen, inventory, item);
        if (source == null) {
            diagnostic(operationId, "cursor-extraction-rejected reason=source-not-resolved target=%s",
                    itemId(item.stack()));
            show(client, "message.better-shulker-hud.source_missing");
            return;
        }
        diagnostic(operationId,
                "source-resolved item=%s count=%d inventorySlot=%d shulkerSlot=%d quickShulkerSlot=%d",
                itemId(source.expectedStack()), source.expectedStack().getCount(),
                source.inventorySlot(), source.shulkerSlot(), source.quickShulkerSlot());

        CursorStaging staging = findCursorStagingDestination(
                screen, inventory, source.expectedStack(), source.inventorySlot());
        if (staging == null) {
            diagnostic(operationId, "cursor-staging-unavailable allowClear=%s", allowClear);
            if (allowClear && beginExtractionClearance(
                    client, screen, item, false, true, operationId)) return;
            show(client, allowClear
                    ? "message.better-shulker-hud.cannot_free_slot"
                    : "message.better-shulker-hud.inventory_full");
            return;
        }
        int requestedAmount = Math.min(
                source.expectedStack().getCount(), staging.capacity());
        if (requestedAmount <= 0) {
            diagnostic(operationId, "cursor-extraction-rejected reason=zero-staging-capacity");
            show(client, "message.better-shulker-hud.inventory_full");
            return;
        }

        pendingExtraction = new PendingExtraction(
                source.inventorySlot(), source.shulkerSlot(), source.expectedStack(), false,
                source.shulkerItem(), source.shulkerName(), false, false, true,
                staging.inventorySlot(), requestedAmount, staging.baselineCount());
        extractionOperationId = operationId;
        extractionWaitTicks = 0;
        extractionMenuId = -1;
        extractionCloseDelay = -1;
        extractionMovedItemCount = 0;
        extractionExpectedSourceCount = -1;
        extractionOpenSyncVersion = containerSyncVersion;
        diagnostic(operationId,
                "cursor-staging inventorySlot=%d baseline=%d capacity=%d requested=%d",
                staging.inventorySlot(), staging.baselineCount(), staging.capacity(), requestedAmount);
        diagnostic(operationId,
                "packet-send open-shulker quickShulkerSlot=%d canSend=%s openSyncBaseline=%d target=cursor-staging",
                source.quickShulkerSlot(), canSendQuickShulker(), extractionOpenSyncVersion);
        OpenShulkerPacket.sendOpenPacket(source.quickShulkerSlot());
        diagnostic(operationId, "packet-sent open-shulker");
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

        long operationId = nextDiagnosticOperationId();
        diagnostic(operationId, "request type=litematica-restock item=%s",
                itemId(required));
        startLitematicaExtraction(client, required.copyWithCount(1), true, operationId);
    }

    private static void startLitematicaExtraction(
            Minecraft client, ItemStack required, boolean allowClear, long operationId) {
        if (client.player == null || client.gameMode == null) {
            diagnostic(operationId,
                    "litematica-restock-rejected reason=player-or-game-mode-missing");
            return;
        }

        Inventory inventory = client.player.getInventory();
        ResolvedSource source = findRestockSource(
                client.player.containerMenu, inventory, required);
        if (source == null) {
            diagnostic(operationId,
                    "litematica-restock-rejected reason=source-not-resolved item=%s",
                    itemId(required));
            return;
        }

        int capacity = inventoryCapacity(
                client.player.containerMenu, inventory,
                source.expectedStack(), source.inventorySlot());
        diagnostic(operationId,
                "litematica-capacity item=%s capacity=%d allowClear=%s sourceInventorySlot=%d shulkerSlot=%d quickShulkerSlot=%d",
                itemId(source.expectedStack()), capacity, allowClear, source.inventorySlot(),
                source.shulkerSlot(), source.quickShulkerSlot());
        if (capacity <= 0) {
            if (allowClear && beginLitematicaClearance(client, required, operationId)) return;
            diagnostic(operationId,
                    "litematica-restock-rejected reason=no-inventory-capacity allowClear=%s",
                    allowClear);
            show(client, allowClear
                    ? "message.better-shulker-hud.cannot_free_slot"
                    : "message.better-shulker-hud.inventory_full");
            return;
        }

        int requestedAmount = Math.min(source.expectedStack().getCount(), capacity);
        pendingExtraction = new PendingExtraction(
                source.inventorySlot(), source.shulkerSlot(), source.expectedStack(), false,
                source.shulkerItem(), source.shulkerName(), true, false, false,
                -1, requestedAmount, 0);
        extractionOperationId = operationId;
        pendingLitematicaSelection = required.copyWithCount(1);
        extractionWaitTicks = 0;
        extractionMenuId = -1;
        extractionCloseDelay = -1;
        extractionExpectedSourceCount = -1;
        extractionOpenSyncVersion = containerSyncVersion;
        diagnostic(operationId,
                "packet-send open-shulker type=litematica-restock item=%s inventorySlot=%d shulkerSlot=%d quickShulkerSlot=%d requested=%d capacity=%d canSend=%s openSyncBaseline=%d",
                itemId(source.expectedStack()), source.inventorySlot(), source.shulkerSlot(),
                source.quickShulkerSlot(), requestedAmount, capacity,
                canSendQuickShulker(), extractionOpenSyncVersion);
        OpenShulkerPacket.sendOpenPacket(source.quickShulkerSlot());
        diagnostic(operationId, "packet-sent open-shulker");
    }

    public static void requestReturnAll() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !(client.screen instanceof AbstractContainerScreen<?>)) return;

        if (isBusy()) {
            show(client, "message.better-shulker-hud.busy");
            return;
        }
        if (!canUseQuickShulker(client, 0)) return;

        organizeActive = true;
        organizeDelay = -1;
        organizedItemCount = 0;
        organizeRetryCount = 0;
        returnQueue.clear();
        if (Configs.Features.RETURN_HISTORY.getBooleanValue()) {
            originRecords.stream()
                    .filter(record -> record.remaining > 0
                            && !isOrganizeBlacklisted(record.prototype))
                    .forEach(returnQueue::addLast);
        }
        returnedItemCount = 0;
        if (returnQueue.isEmpty()) {
            organizeDelay = 0;
        } else {
            startNextReturn(client);
        }
    }

    public static void requestStoreCarried(AbstractContainerScreen<?> screen) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null) return;

        ItemStack carried = screen.getMenu().getCarried();
        if (carried.isEmpty()) return;
        long operationId = nextDiagnosticOperationId();
        diagnostic(operationId, "request type=drag-store item=%s count=%d",
                itemId(carried), carried.getCount());
        if (isBusy()) {
            diagnostic(operationId, "request-rejected reason=controller-busy activeOperation=%d",
                    activeDiagnosticOperationId());
            show(client, "message.better-shulker-hud.busy");
            return;
        }
        if (ShulkerContentsHelper.isShulker(carried)) {
            diagnostic(operationId, "store-rejected reason=cannot-nest-shulker");
            show(client, "message.better-shulker-hud.cannot_nest_shulker");
            return;
        }
        if (!canUseQuickShulker(client, operationId)) return;

        ItemStack prototype = carried.copyWithCount(1);
        StoreTarget target = findStoreTarget(client.player.getInventory(), prototype, carried.getCount());
        if (target == null) {
            diagnostic(operationId, "store-rejected reason=no-shulker-capacity");
            show(client, "message.better-shulker-hud.no_shulker_space");
            return;
        }

        PlayerDestination temporary = findTemporaryDestination(
                screen, client.player.getInventory(), carried, target.inventorySlot());
        if (temporary == null) {
            diagnostic(operationId, "store-rejected reason=no-temporary-inventory-slot");
            show(client, "message.better-shulker-hud.no_temporary_space");
            return;
        }

        int amount = carried.getCount();
        client.gameMode.handleContainerInput(
                screen.getMenu().containerId, temporary.menuSlot(), 0,
                ContainerInput.PICKUP, client.player);
        if (!screen.getMenu().getCarried().isEmpty()) {
            diagnostic(operationId, "store-rejected reason=cursor-staging-failed destinationInventorySlot=%d",
                    temporary.inventorySlot());
            show(client, "message.better-shulker-hud.no_temporary_space");
            return;
        }

        int targetMenuSlot = resolveQuickShulkerSlot(
                screen, client.player.getInventory(), target.inventorySlot());
        if (targetMenuSlot < 0) {
            diagnostic(operationId,
                    "store-rejected reason=quickshulker-slot-unresolved targetInventorySlot=%d",
                    target.inventorySlot());
            show(client, "message.better-shulker-hud.store_target_changed");
            return;
        }

        pendingStore = new PendingStore(
                temporary.inventorySlot(), amount, target.inventorySlot(), target.shulkerSlot(),
                prototype, target.shulkerItem(), target.shulkerName(), StorePurpose.MANUAL);
        diagnostic(operationId,
                "store-staged sourceInventorySlot=%d targetInventorySlot=%d shulkerSlot=%d quickShulkerSlot=%d amount=%d",
                temporary.inventorySlot(), target.inventorySlot(), target.shulkerSlot(),
                targetMenuSlot, amount);
        openPendingStore(targetMenuSlot, operationId);
    }

    private static boolean isBusy() {
        return pendingExtraction != null || pendingStore != null
                || pendingCursorPickup != null
                || deferredExtraction != null
                || activeReturn != null
                || !returnQueue.isEmpty() || nextReturnDelay >= 0
                || organizeActive;
    }

    public static boolean shouldHideQuickShulkerScreen() {
        return Configs.Features.HIDE_QUICK_SHULKER_SCREEN.getBooleanValue()
                && (pendingExtraction != null || pendingStore != null
                || deferredExtraction != null || activeReturn != null);
    }

    public static boolean shouldPreserveInventoryScreenDuringContainerClose() {
        return preserveInventoryScreenDuringContainerClose;
    }

    private static boolean canUseQuickShulker(Minecraft client, long operationId) {
        boolean canSend = canSendQuickShulker();
        diagnostic(operationId, "quickshulker-channel canSend=%s", canSend);
        if (canSend) return true;
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
        } else if (deferredExtraction != null) {
            tickDeferredExtraction(client);
            return;
        } else if (organizeActive) {
            tickOrganize(client);
            return;
        }
        tickAutoRestock(client);
    }

    private static void tickDeferredExtraction(Minecraft client) {
        if (client.player == null) {
            clearDeferredExtraction();
            return;
        }
        if (deferredExtractionDelay-- > 0) return;
        if (!ClientPlayNetworking.canSend(OpenShulkerPacket.OPEN_SHULKER_PACKET_ID)) {
            show(client, "message.better-shulker-hud.cannot_free_slot");
            clearDeferredExtraction();
            return;
        }

        DeferredExtraction deferred = deferredExtraction;
        if (client.player.containerMenu instanceof ShulkerBoxMenu menu
                && menu.containerId == deferredExtractionMenuId
                && resumeDeferredExtractionInCurrentMenu(client, menu, deferred)) {
            clearDeferredExtraction();
            return;
        }
        if (deferred.litematicaRestock()) {
            clearDeferredExtraction();
            startLitematicaExtraction(
                    client, deferred.litematicaRequired(), false, deferred.operationId());
            return;
        }
        if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
            show(client, "message.better-shulker-hud.cannot_free_slot");
            clearDeferredExtraction();
            return;
        }
        clearDeferredExtraction();
        if (deferred.cursorPickup()) {
            startCursorExtraction(client, screen, deferred.item(), false, deferred.operationId());
        } else {
            startInventoryExtraction(
                    client, screen, deferred.item(), deferred.takeOne(), false,
                    deferred.operationId());
        }
    }

    private static boolean beginExtractionClearance(
            Minecraft client, AbstractContainerScreen<?> screen,
            BundlePanelRenderer.FlatItem item, boolean takeOne, boolean cursorPickup,
            long operationId) {
        if (client.player == null) return false;

        Inventory inventory = client.player.getInventory();
        ResolvedSource extractionSource = findValidatedSource(screen, inventory, item);
        int preferredTargetInventorySlot = extractionSource == null
                ? -1 : extractionSource.inventorySlot();
        ClearanceCandidate candidate = findClearanceCandidate(
                inventory, preferredTargetInventorySlot);
        if (candidate == null) return false;

        int targetMenuSlot = resolveQuickShulkerSlot(
                screen, inventory, candidate.target().inventorySlot());
        if (targetMenuSlot < 0) return false;

        deferredExtraction = new DeferredExtraction(
                item, takeOne, cursorPickup, ItemStack.EMPTY, operationId);
        pendingStore = new PendingStore(
                candidate.sourceInventorySlot(), candidate.amount(),
                candidate.target().inventorySlot(), candidate.target().shulkerSlot(),
                candidate.prototype(), candidate.target().shulkerItem(),
                candidate.target().shulkerName(), StorePurpose.EXTRACTION_PREPARATION);
        diagnostic(operationId,
                "clearance-store sourceInventorySlot=%d targetInventorySlot=%d shulkerSlot=%d quickShulkerSlot=%d item=%s amount=%d",
                candidate.sourceInventorySlot(), candidate.target().inventorySlot(),
                candidate.target().shulkerSlot(), targetMenuSlot,
                itemId(candidate.prototype()), candidate.amount());
        openPendingStore(targetMenuSlot, operationId);
        return true;
    }

    private static boolean beginLitematicaClearance(
            Minecraft client, ItemStack required, long operationId) {
        if (client.player == null) return false;

        Inventory inventory = client.player.getInventory();
        int preferredTargetInventorySlot = -1;
        ResolvedSource extractionSource = findRestockSource(
                client.player.containerMenu, inventory, required);
        if (extractionSource != null) {
            preferredTargetInventorySlot = extractionSource.inventorySlot();
        }
        ClearanceCandidate candidate = findClearanceCandidate(
                inventory, preferredTargetInventorySlot);
        if (candidate == null) {
            diagnostic(operationId,
                    "litematica-clearance-unavailable reason=no-eligible-inventory-stack");
            return false;
        }

        int targetMenuSlot = resolveQuickShulkerSlot(
                client.player.containerMenu, inventory,
                candidate.target().inventorySlot());
        if (targetMenuSlot < 0) {
            diagnostic(operationId,
                    "litematica-clearance-unavailable reason=quickshulker-slot-unresolved targetInventorySlot=%d",
                    candidate.target().inventorySlot());
            return false;
        }

        deferredExtraction = new DeferredExtraction(
                null, false, false, required.copyWithCount(1), operationId);
        pendingStore = new PendingStore(
                candidate.sourceInventorySlot(), candidate.amount(),
                candidate.target().inventorySlot(), candidate.target().shulkerSlot(),
                candidate.prototype(), candidate.target().shulkerItem(),
                candidate.target().shulkerName(), StorePurpose.EXTRACTION_PREPARATION);
        diagnostic(operationId,
                "litematica-clearance-store sourceInventorySlot=%d targetInventorySlot=%d shulkerSlot=%d quickShulkerSlot=%d item=%s amount=%d",
                candidate.sourceInventorySlot(), candidate.target().inventorySlot(),
                candidate.target().shulkerSlot(), targetMenuSlot,
                itemId(candidate.prototype()), candidate.amount());
        openPendingStore(targetMenuSlot, operationId);
        return true;
    }

    private static int inventoryCapacity(
            AbstractContainerScreen<?> screen, Inventory inventory,
            ItemStack prototype, int sourceShulkerInventorySlot) {
        return inventoryCapacity(
                screen.getMenu(), inventory, prototype, sourceShulkerInventorySlot);
    }

    private static int inventoryCapacity(
            AbstractContainerMenu menu, Inventory inventory,
            ItemStack prototype, int sourceShulkerInventorySlot) {
        int capacity = 0;
        for (Slot slot : menu.slots) {
            if (slot.container != inventory
                    || slot.getContainerSlot() < 0
                    || slot.getContainerSlot() >= 36
                    || slot.getContainerSlot() == sourceShulkerInventorySlot
                    || !slot.mayPlace(prototype)) continue;
            ItemStack current = slot.getItem();
            if (!current.isEmpty()
                    && !ItemStack.isSameItemSameComponents(current, prototype)) continue;
            capacity += Math.max(0, slot.getMaxStackSize(prototype) - current.getCount());
        }
        return capacity;
    }

    private static ClearanceCandidate findClearanceCandidate(Inventory inventory) {
        return findClearanceCandidate(inventory, -1);
    }

    private static ClearanceCandidate findClearanceCandidate(
            Inventory inventory, int preferredTargetInventorySlot) {
        ClearanceCandidate best = null;
        int selected = inventory.getSelectedSlot();
        Set<String> whitelist = configuredItemIds(
                Configs.General.CLEAR_SLOT_WHITELIST.getStrings());
        Set<String> blacklist = configuredItemIds(
                Configs.General.CLEAR_SLOT_BLACKLIST.getStrings());
        Configs.ClearanceListMode listMode = (Configs.ClearanceListMode)
                Configs.General.CLEAR_SLOT_LIST_MODE.getOptionListValue();
        boolean useOriginalLogic = whitelist.isEmpty() && blacklist.isEmpty();
        for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
            ItemStack stack = inventory.getItem(inventorySlot);
            if (stack.isEmpty() || ShulkerContentsHelper.isShulker(stack)) continue;
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!useOriginalLogic) {
                if (listMode == Configs.ClearanceListMode.BLACKLIST
                        && blacklist.contains(itemId)) continue;
                if (listMode == Configs.ClearanceListMode.WHITELIST
                        && !whitelist.contains(itemId)) continue;
            }

            StoreTarget target = findStoreTarget(inventory, stack, stack.getCount());
            if (target == null) continue;

            int score = clearancePriority(stack) * 100;
            if (inventorySlot < 9) score += 10;
            if (inventorySlot == selected) score += 1000;
            if (target.inventorySlot() == preferredTargetInventorySlot) score -= 5000;
            if (best == null || score < best.score()) {
                best = new ClearanceCandidate(
                        inventorySlot, stack.getCount(), stack.copyWithCount(1), target, score);
            }
        }
        return best;
    }

    private static Set<String> configuredItemIds(List<String> entries) {
        Set<String> ids = new HashSet<>();
        Set<String> registeredIds = new HashSet<>();
        Map<String, Set<String>> localizedIds = new HashMap<>();

        for (Item item : BuiltInRegistries.ITEM) {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            String localizedName = new ItemStack(item).getHoverName().getString().trim();
            registeredIds.add(id);
            if (!localizedName.isEmpty()) {
                localizedIds.computeIfAbsent(
                        localizedName.toLowerCase(Locale.ROOT), ignored -> new HashSet<>()).add(id);
            }
        }

        for (String entry : entries) {
            if (entry == null || entry.isBlank()) continue;

            String trimmedEntry = entry.trim();
            if (addLocalizedItemIds(ids, localizedIds, trimmedEntry)) continue;

            for (String section : trimmedEntry.split("[\\r\\n,;，；、]+")) {
                String trimmedSection = section.trim();
                if (trimmedSection.isEmpty()) continue;
                if (addLocalizedItemIds(ids, localizedIds, trimmedSection)) continue;

                for (String token : trimmedSection.split("\\s+")) {
                    if (addLocalizedItemIds(ids, localizedIds, token)) continue;
                    String id = token.trim().toLowerCase(Locale.ROOT);
                    if (id.isEmpty()) continue;
                    if (!id.contains(":")) id = "minecraft:" + id;
                    if (registeredIds.contains(id)) ids.add(id);
                }
            }
        }
        return ids;
    }

    private static boolean isOrganizeBlacklisted(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && configuredItemIds(Configs.General.ORGANIZE_BLACKLIST.getStrings())
                .contains(itemId(stack));
    }

    private static boolean addLocalizedItemIds(
            Set<String> target, Map<String, Set<String>> localizedIds, String value) {
        Set<String> matches = localizedIds.get(value.trim().toLowerCase(Locale.ROOT));
        if (matches == null) return false;
        target.addAll(matches);
        return true;
    }

    private static int clearancePriority(ItemStack stack) {
        if (stack.has(DataComponents.FOOD)) return 0;
        if (stack.has(DataComponents.TOOL)
                || stack.has(DataComponents.WEAPON)
                || stack.has(DataComponents.EQUIPPABLE)
                || stack.has(DataComponents.BLOCKS_ATTACKS)
                || stack.has(DataComponents.PIERCING_WEAPON)
                || stack.has(DataComponents.KINETIC_WEAPON)) return 1;
        return 2;
    }

    private static void tickOrganize(Minecraft client) {
        if (client.player == null || client.gameMode == null) {
            clearOrganizeProcess();
            return;
        }
        if (organizeDelay-- > 0) return;
        if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
            client.setScreen(new InventoryScreen(client.player));
            organizeDelay = 0;
            return;
        }

        MatchingStore matching = findNextMatchingStore(client.player.getInventory());
        if (matching == null) {
            finishOrganize(client, false);
            return;
        }

        int targetMenuSlot = resolveQuickShulkerSlot(
                screen, client.player.getInventory(), matching.target().inventorySlot());
        if (targetMenuSlot < 0) {
            finishOrganize(client, true);
            return;
        }

        pendingStore = new PendingStore(
                matching.sourceInventorySlot(), matching.amount(),
                matching.target().inventorySlot(), matching.target().shulkerSlot(),
                matching.prototype(), matching.target().shulkerItem(),
                matching.target().shulkerName(), StorePurpose.MATCHING_SORT);
        organizeDelay = -1;
        openPendingStore(targetMenuSlot, nextDiagnosticOperationId());
    }

    private static MatchingStore findNextMatchingStore(Inventory inventory) {
        Set<String> organizeBlacklist = configuredItemIds(
                Configs.General.ORGANIZE_BLACKLIST.getStrings());
        for (int pass = 0; pass < 2; pass++) {
            int start = pass == 0 ? 9 : 0;
            int end = pass == 0 ? 36 : 9;
            for (int inventorySlot = start; inventorySlot < end; inventorySlot++) {
                ItemStack stack = inventory.getItem(inventorySlot);
                if (stack.isEmpty() || ShulkerContentsHelper.isShulker(stack)) continue;
                if (organizeBlacklist.contains(itemId(stack))) continue;
                MatchingTarget matching = findMatchingStoreTarget(inventory, stack);
                if (matching == null) continue;
                return new MatchingStore(
                        inventorySlot, Math.min(stack.getCount(), matching.capacity()),
                        stack.copyWithCount(1), matching.target());
            }
        }
        return null;
    }

    private static MatchingTarget findMatchingStoreTarget(
            Inventory inventory, ItemStack prototype) {
        MatchingTarget emptyTarget = null;
        for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
            ItemStack shulker = inventory.getItem(inventorySlot);
            if (!ShulkerContentsHelper.isShulker(shulker)) continue;

            List<ItemStack> contents = ShulkerContentsHelper.getStacks(shulker);
            boolean containsMatchingItem = false;
            int firstEmptySlot = -1;
            for (int shulkerSlot = 0; shulkerSlot < contents.size(); shulkerSlot++) {
                ItemStack current = contents.get(shulkerSlot);
                if (current.isEmpty()) {
                    if (firstEmptySlot < 0) firstEmptySlot = shulkerSlot;
                    continue;
                }
                if (!ItemStack.isSameItemSameComponents(current, prototype)) continue;
                containsMatchingItem = true;
                int capacity = current.getMaxStackSize() - current.getCount();
                if (capacity > 0) {
                    return new MatchingTarget(
                            new StoreTarget(inventorySlot, shulkerSlot, shulker.getItem(),
                                    shulker.get(DataComponents.CUSTOM_NAME)), capacity);
                }
            }
            if (containsMatchingItem && firstEmptySlot >= 0 && emptyTarget == null) {
                emptyTarget = new MatchingTarget(
                        new StoreTarget(inventorySlot, firstEmptySlot, shulker.getItem(),
                                shulker.get(DataComponents.CUSTOM_NAME)),
                        prototype.getMaxStackSize());
            }
        }
        return emptyTarget;
    }

    private static void openPendingStore(int targetMenuSlot, long operationId) {
        storeOperationId = operationId;
        storeWaitTicks = 0;
        storeMenuId = -1;
        storeCloseDelay = -1;
        storedItemCount = 0;
        storeExpectedTargetCount = -1;
        storeContinueAfterMove = false;
        storeOpenSyncVersion = containerSyncVersion;
        if (pendingStore != null) {
            diagnostic(operationId,
                    "packet-send open-shulker purpose=%s quickShulkerSlot=%d canSend=%s openSyncBaseline=%d item=%s amount=%d sourceInventorySlot=%d targetInventorySlot=%d shulkerSlot=%d",
                    pendingStore.purpose(), targetMenuSlot, canSendQuickShulker(),
                    storeOpenSyncVersion, itemId(pendingStore.prototype()), pendingStore.amount(),
                    pendingStore.sourceInventorySlot(), pendingStore.targetInventorySlot(),
                    pendingStore.shulkerSlot());
        }
        OpenShulkerPacket.sendOpenPacket(targetMenuSlot);
        diagnostic(operationId, "packet-sent open-shulker");
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

        long operationId = nextDiagnosticOperationId();
        pendingExtraction = new PendingExtraction(
                source.inventorySlot(), source.shulkerSlot(), source.expectedStack(), false,
                source.shulkerItem(), source.shulkerName(), false, true, false,
                targetInventorySlot, requestedAmount, targetStack.getCount());
        extractionOperationId = operationId;
        extractionWaitTicks = 0;
        extractionMenuId = -1;
        extractionCloseDelay = -1;
        extractionMovedItemCount = 0;
        extractionExpectedSourceCount = -1;
        extractionOpenSyncVersion = containerSyncVersion;
        diagnostic(operationId,
                "request type=automatic-restock item=%s sourceInventorySlot=%d shulkerSlot=%d quickShulkerSlot=%d targetInventorySlot=%d targetBaseline=%d requested=%d canSend=%s openSyncBaseline=%d",
                itemId(source.expectedStack()), source.inventorySlot(), source.shulkerSlot(),
                source.quickShulkerSlot(), targetInventorySlot, targetStack.getCount(),
                requestedAmount, canSendQuickShulker(), extractionOpenSyncVersion);
        OpenShulkerPacket.sendOpenPacket(source.quickShulkerSlot());
        diagnostic(operationId, "packet-sent open-shulker");
        return true;
    }

    private static void tickStore(Minecraft client) {
        if (client.player == null || client.gameMode == null) {
            diagnostic(storeOperationId, "store-aborted reason=player-or-game-mode-missing");
            clearStore();
            return;
        }

        if (storeCloseDelay >= 0) {
            if (!hasContainerSyncAfter(storeMenuId, storeMoveSyncVersion)) {
                if (++storeWaitTicks > OPEN_TIMEOUT_TICKS) {
                    diagnostic(storeOperationId,
                            "timeout stage=store-move-sync menu=%d waitedTicks=%d moveSyncBaseline=%d currentSyncVersion=%d",
                            storeMenuId, storeWaitTicks, storeMoveSyncVersion, containerSyncVersion);
                    failStore(client, "message.better-shulker-hud.store_failed");
                }
                return;
            }
            if (!(client.player.containerMenu instanceof ShulkerBoxMenu menu)
                    || !isConfirmedStoreTarget(menu)) {
                diagnostic(storeOperationId,
                        "store-confirmation-failed menu=%d expectedTargetCount=%d",
                        storeMenuId, storeExpectedTargetCount);
                failStore(client, "message.better-shulker-hud.store_failed");
                return;
            }
            closeAfterStore(client);
            return;
        }

        if (!(client.player.containerMenu instanceof ShulkerBoxMenu menu)) {
            if (++storeWaitTicks > OPEN_TIMEOUT_TICKS) {
                diagnostic(storeOperationId,
                        "timeout stage=store-container-open waitedTicks=%d openSyncBaseline=%d currentSyncVersion=%d currentMenu=%s",
                        storeWaitTicks, storeOpenSyncVersion, containerSyncVersion,
                        client.player.containerMenu.getClass().getSimpleName());
                failStore(client, "message.better-shulker-hud.open_failed");
            }
            return;
        }
        if (storeMenuId != menu.containerId) {
            storeMenuId = menu.containerId;
            storeWaitTicks = 0;
            diagnostic(storeOperationId, "container-opened type=shulker menu=%d slots=%d",
                    storeMenuId, menu.slots.size());
        }
        if (!hasContainerSyncAfter(menu.containerId, storeOpenSyncVersion)) {
            if (++storeWaitTicks > OPEN_TIMEOUT_TICKS) {
                diagnostic(storeOperationId,
                        "timeout stage=store-initial-sync menu=%d waitedTicks=%d openSyncBaseline=%d currentSyncVersion=%d menuSyncVersion=%d",
                        menu.containerId, storeWaitTicks, storeOpenSyncVersion,
                        containerSyncVersion,
                        containerSyncVersions.getOrDefault(menu.containerId, Long.MIN_VALUE));
                failStore(client, "message.better-shulker-hud.open_failed");
            }
            return;
        }

        if (!isExpectedStoreShulker(menu, client.player.getInventory(), pendingStore)) {
            diagnostic(storeOperationId,
                    "store-target-validation-failed menu=%d targetInventorySlot=%d shulkerSlot=%d",
                    menu.containerId, pendingStore.targetInventorySlot(), pendingStore.shulkerSlot());
            failStore(client, "message.better-shulker-hud.store_target_changed");
            return;
        }

        if (storeTransfer != null) {
            advanceStoreTransfer(client, menu);
            return;
        }

        if (storeContinueAfterMove) {
            if (!hasContainerSyncAfter(menu.containerId, storeMoveSyncVersion)) {
                if (++storeWaitTicks > OPEN_TIMEOUT_TICKS) {
                    diagnostic(storeOperationId,
                            "timeout stage=matching-transfer-sync menu=%d waitedTicks=%d moveSyncBaseline=%d currentSyncVersion=%d",
                            menu.containerId, storeWaitTicks, storeMoveSyncVersion,
                            containerSyncVersion);
                    failStore(client, "message.better-shulker-hud.store_failed");
                }
                return;
            }
            if (!prepareNextMatchingTransfer(menu)) {
                storeContinueAfterMove = false;
                storeCloseDelay = 0;
                storeWaitTicks = 0;
                return;
            }
            storeContinueAfterMove = false;
            storeWaitTicks = 0;
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
        storeExpectedTargetCount = targetStack.getCount() + pendingStore.amount();
        // A full source stack fits with one normal click. For a partial target
        // capacity, use right clicks to place exactly the accepted amount and
        // return the cursor remainder to the source slot.
        boolean placeMovedItemsOneByOne = pendingStore.amount() < before;
        int repeatedClicks = placeMovedItemsOneByOne ? pendingStore.amount() : 0;
        diagnostic(storeOperationId,
                "store-move-request menu=%d sourceMenuSlot=%d targetShulkerSlot=%d sourceBefore=%d targetBefore=%d requested=%d expectedTarget=%d repeatedClicks=%d",
                menu.containerId, sourceMenuSlot, pendingStore.shulkerSlot(), before,
                targetStack.getCount(), pendingStore.amount(), storeExpectedTargetCount,
                repeatedClicks);
        client.gameMode.handleContainerInput(
                menu.containerId, sourceMenuSlot, 0, ContainerInput.PICKUP, client.player);
        if (menu.getCarried().isEmpty()) {
            failStore(client, "message.better-shulker-hud.store_failed");
            return;
        }
        storeTransfer = new StoreTransfer(
                sourceMenuSlot, pendingStore.shulkerSlot(), before,
                pendingStore.amount(), placeMovedItemsOneByOne, repeatedClicks);
        advanceStoreTransfer(client, menu);
    }

    private static void advanceStoreTransfer(Minecraft client, ShulkerBoxMenu menu) {
        if (storeTransfer == null || pendingStore == null) return;
        if (!storeTransfer.placeMovedItemsOneByOne()
                && storeTransfer.repeatedClicksRemaining() == 0) {
            client.gameMode.handleContainerInput(
                    menu.containerId, storeTransfer.targetMenuSlot(), 0,
                    ContainerInput.PICKUP, client.player);
            if (!menu.getCarried().isEmpty()) {
                client.gameMode.handleContainerInput(
                        menu.containerId, storeTransfer.sourceMenuSlot(), 0,
                        ContainerInput.PICKUP, client.player);
            }
            storeTransfer = storeTransfer.withRemaining(-1);
        }
        int clickSlot = storeTransfer.placeMovedItemsOneByOne()
                ? storeTransfer.targetMenuSlot() : storeTransfer.sourceMenuSlot();
        int clicks = Math.min(MAX_STORE_CLICKS_PER_TICK,
                Math.max(0, storeTransfer.repeatedClicksRemaining()));
        for (int i = 0; i < clicks; i++) {
            client.gameMode.handleContainerInput(
                    menu.containerId, clickSlot, 1, ContainerInput.PICKUP, client.player);
        }
        storeTransfer = storeTransfer.withRemaining(
                storeTransfer.repeatedClicksRemaining() - clicks);
        if (storeTransfer.repeatedClicksRemaining() > 0) return;

        int finalSlot = storeTransfer.placeMovedItemsOneByOne()
                ? storeTransfer.sourceMenuSlot() : storeTransfer.targetMenuSlot();
        if (!menu.getCarried().isEmpty()) {
            client.gameMode.handleContainerInput(
                    menu.containerId, finalSlot, 0, ContainerInput.PICKUP, client.player);
        }
        int moved = storeTransfer.beforeSourceCount()
                - menu.getSlot(storeTransfer.sourceMenuSlot()).getItem().getCount();
        diagnostic(storeOperationId,
                "store-local-result menu=%d moved=%d requested=%d cursorCount=%d sourceAfter=%d",
                menu.containerId, moved, storeTransfer.amount(), menu.getCarried().getCount(),
                menu.getSlot(storeTransfer.sourceMenuSlot()).getItem().getCount());
        if (!menu.getCarried().isEmpty() || moved != storeTransfer.amount()) {
            failStore(client, "message.better-shulker-hud.store_failed");
            return;
        }

        storedItemCount += moved;
        storeTransfer = null;
        storeMoveSyncVersion = containerSyncVersion;
        if (pendingStore.purpose() == StorePurpose.MATCHING_SORT
                && hasRemainingMatchingSource(menu)) {
            storeContinueAfterMove = true;
            storeCloseDelay = -1;
        } else {
            storeCloseDelay = 0;
        }
        storeWaitTicks = 0;
    }

    private static boolean hasRemainingMatchingSource(ShulkerBoxMenu menu) {
        if (pendingStore == null || Minecraft.getInstance().player == null) return false;
        return findMatchingSourceMenuSlot(
                menu, Minecraft.getInstance().player.getInventory(),
                pendingStore.prototype(), pendingStore.targetInventorySlot()) >= 0;
    }

    private static boolean prepareNextMatchingTransfer(ShulkerBoxMenu menu) {
        if (pendingStore == null || pendingStore.purpose() != StorePurpose.MATCHING_SORT
                || Minecraft.getInstance().player == null) return false;
        Inventory inventory = Minecraft.getInstance().player.getInventory();
        int sourceMenuSlot = findMatchingSourceMenuSlot(
                menu, inventory, pendingStore.prototype(), pendingStore.targetInventorySlot());
        if (sourceMenuSlot < 0) return false;
        ItemStack source = menu.getSlot(sourceMenuSlot).getItem();
        if (source.isEmpty()) return false;
        int targetSlot = findMatchingTargetMenuSlot(menu, pendingStore);
        if (targetSlot < 0) return false;
        ItemStack target = menu.getSlot(targetSlot).getItem();
        int capacity = menu.getSlot(targetSlot).getMaxStackSize(pendingStore.prototype())
                - target.getCount();
        int amount = Math.min(source.getCount(), capacity);
        if (amount <= 0) return false;
        pendingStore = new PendingStore(
                menu.getSlot(sourceMenuSlot).getContainerSlot(), amount,
                pendingStore.targetInventorySlot(), targetSlot,
                pendingStore.prototype(), pendingStore.shulkerItem(),
                pendingStore.shulkerName(), pendingStore.purpose());
        diagnostic(storeOperationId,
                "matching-transfer-next sourceMenuSlot=%d targetShulkerSlot=%d sourceCount=%d targetCount=%d amount=%d",
                sourceMenuSlot, targetSlot, source.getCount(), target.getCount(), amount);
        return true;
    }

    private static int findMatchingTargetMenuSlot(
            ShulkerBoxMenu menu, PendingStore store) {
        int emptySlot = -1;
        for (int slotIndex = 0; slotIndex < ShulkerContentsHelper.SHULKER_SIZE; slotIndex++) {
            Slot slot = menu.getSlot(slotIndex);
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()
                    && ItemStack.isSameItemSameComponents(stack, store.prototype())
                    && stack.getMaxStackSize() > stack.getCount()) return slotIndex;
            if (stack.isEmpty() && emptySlot < 0) emptySlot = slotIndex;
        }
        return emptySlot;
    }

    /** Finds any remaining player stack for this item, not just the stack that
     * started the operation. This lets one open shulker session drain several
     * inventory stacks. */
    private static int findMatchingSourceMenuSlot(
            ShulkerBoxMenu menu, Inventory inventory, ItemStack prototype,
            int targetShulkerInventorySlot) {
        for (int pass = 0; pass < 2; pass++) {
            int start = pass == 0 ? 9 : 0;
            int end = pass == 0 ? 36 : 9;
            for (int menuSlot = 0; menuSlot < menu.slots.size(); menuSlot++) {
                Slot slot = menu.slots.get(menuSlot);
                int inventorySlot = slot.getContainerSlot();
                if (slot.container != inventory || inventorySlot < start
                        || inventorySlot >= end || inventorySlot == targetShulkerInventorySlot) {
                    continue;
                }
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty()
                        && !ShulkerContentsHelper.isShulker(stack)
                        && ItemStack.isSameItemSameComponents(stack, prototype)) {
                    return menuSlot;
                }
            }
        }
        return -1;
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
            diagnostic(extractionOperationId,
                    "extraction-aborted reason=player-or-game-mode-missing");
            clearExtraction();
            return;
        }

        if (extractionCloseDelay >= 0) {
            if (!hasContainerSyncAfter(extractionMenuId, extractionMoveSyncVersion)) {
                if (++extractionWaitTicks > OPEN_TIMEOUT_TICKS) {
                    diagnostic(extractionOperationId,
                            "timeout stage=extraction-move-sync menu=%d waitedTicks=%d moveSyncBaseline=%d currentSyncVersion=%d",
                            extractionMenuId, extractionWaitTicks,
                            extractionMoveSyncVersion, containerSyncVersion);
                    failExtraction(client, "message.better-shulker-hud.open_failed");
                }
                return;
            }
            if (!(client.player.containerMenu instanceof ShulkerBoxMenu menu)
                    || !isConfirmedExtractionSource(menu)) {
                diagnostic(extractionOperationId,
                        "extraction-confirmation-failed menu=%d expectedSourceCount=%d",
                        extractionMenuId, extractionExpectedSourceCount);
                failExtraction(client, "message.better-shulker-hud.source_changed");
                return;
            }
            closeAfterExtraction(client);
            return;
        }

        if (!(client.player.containerMenu instanceof ShulkerBoxMenu menu)) {
            if (++extractionWaitTicks > OPEN_TIMEOUT_TICKS) {
                diagnostic(extractionOperationId,
                        "timeout stage=extraction-container-open waitedTicks=%d openSyncBaseline=%d currentSyncVersion=%d currentMenu=%s",
                        extractionWaitTicks, extractionOpenSyncVersion, containerSyncVersion,
                        client.player.containerMenu.getClass().getSimpleName());
                failExtraction(client, "message.better-shulker-hud.open_failed");
            }
            return;
        }

        if (extractionMenuId != menu.containerId) {
            extractionMenuId = menu.containerId;
            extractionWaitTicks = 0;
            diagnostic(extractionOperationId,
                    "container-opened type=shulker menu=%d slots=%d",
                    extractionMenuId, menu.slots.size());
        }
        if (!hasContainerSyncAfter(menu.containerId, extractionOpenSyncVersion)) {
            if (++extractionWaitTicks > OPEN_TIMEOUT_TICKS) {
                diagnostic(extractionOperationId,
                        "timeout stage=extraction-initial-sync menu=%d waitedTicks=%d openSyncBaseline=%d currentSyncVersion=%d menuSyncVersion=%d",
                        menu.containerId, extractionWaitTicks, extractionOpenSyncVersion,
                        containerSyncVersion,
                        containerSyncVersions.getOrDefault(menu.containerId, Long.MIN_VALUE));
                failExtraction(client, "message.better-shulker-hud.open_failed");
            }
            return;
        }

        Slot source = menu.getSlot(pendingExtraction.shulkerSlot());
        ItemStack sourceStack = source.getItem();
        if (sourceStack.isEmpty()
                || !ItemStack.isSameItemSameComponents(sourceStack, pendingExtraction.expectedStack())) {
            diagnostic(extractionOperationId,
                    "source-validation-failed menu=%d shulkerSlot=%d expected=%s expectedCount=%d actual=%s actualCount=%d",
                    menu.containerId, pendingExtraction.shulkerSlot(),
                    itemId(pendingExtraction.expectedStack()),
                    pendingExtraction.expectedStack().getCount(), itemId(sourceStack),
                    sourceStack.getCount());
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
                diagnostic(extractionOperationId,
                        "destination-resolution-failed targetInventorySlot=%d cursorPickup=%s handRestock=%s",
                        pendingExtraction.targetInventorySlot(),
                        pendingExtraction.cursorPickup(), pendingExtraction.handRestock());
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
                diagnostic(extractionOperationId,
                        "move-rejected reason=zero-capacity destinationMenuSlot=%d targetCount=%d requested=%d",
                        destination, targetStack.getCount(), pendingExtraction.requestedAmount());
                failExtraction(client, targetError);
                return;
            }
            diagnostic(extractionOperationId,
                    "move-request menu=%d mode=exact sourceShulkerSlot=%d destinationMenuSlot=%d destinationInventorySlot=%d sourceBefore=%d targetBefore=%d requested=%d moveSyncBaseline=%d",
                    menu.containerId, pendingExtraction.shulkerSlot(), destination,
                    pendingExtraction.targetInventorySlot(), before, targetStack.getCount(),
                    amount, extractionMoveSyncVersion);
            moveExactAmount(client, menu, pendingExtraction.shulkerSlot(), destination, amount);
            int moved = before - menu.getSlot(pendingExtraction.shulkerSlot()).getItem().getCount();
            diagnostic(extractionOperationId,
                    "move-local-result mode=exact moved=%d requested=%d sourceAfter=%d cursorCount=%d",
                    moved, amount,
                    menu.getSlot(pendingExtraction.shulkerSlot()).getItem().getCount(),
                    menu.getCarried().getCount());
            if (moved != amount) {
                failExtraction(client, targetError);
                return;
            }
        } else if (pendingExtraction.litematicaRestock()) {
            int destination = findDestinationSlot(
                    menu, client.player.getInventory(), sourceStack,
                    pendingExtraction.inventorySlot());
            if (destination < 0) {
                diagnostic(extractionOperationId,
                        "destination-resolution-failed mode=litematica-exact sourceBefore=%d requested=%d",
                        before, pendingExtraction.requestedAmount());
                failExtraction(client, "message.better-shulker-hud.inventory_full");
                return;
            }

            Slot destinationSlot = menu.getSlot(destination);
            ItemStack targetStack = destinationSlot.getItem();
            int amount = Math.min(
                    pendingExtraction.requestedAmount(),
                    Math.min(sourceStack.getCount(),
                            destinationSlot.getMaxStackSize(sourceStack)
                                    - targetStack.getCount()));
            if (amount <= 0) {
                diagnostic(extractionOperationId,
                        "move-rejected reason=zero-capacity mode=litematica-exact destinationMenuSlot=%d targetCount=%d requested=%d",
                        destination, targetStack.getCount(),
                        pendingExtraction.requestedAmount());
                failExtraction(client, "message.better-shulker-hud.inventory_full");
                return;
            }

            diagnostic(extractionOperationId,
                    "move-request menu=%d mode=litematica-exact sourceShulkerSlot=%d destinationMenuSlot=%d destinationInventorySlot=%d sourceBefore=%d targetBefore=%d requested=%d moveSyncBaseline=%d",
                    menu.containerId, pendingExtraction.shulkerSlot(), destination,
                    destinationSlot.getContainerSlot(), before, targetStack.getCount(),
                    amount, extractionMoveSyncVersion);
            moveExactAmount(
                    client, menu, pendingExtraction.shulkerSlot(), destination, amount);
            int moved = before
                    - menu.getSlot(pendingExtraction.shulkerSlot()).getItem().getCount();
            diagnostic(extractionOperationId,
                    "move-local-result mode=litematica-exact moved=%d requested=%d sourceAfter=%d destinationAfter=%d cursorCount=%d",
                    moved, amount,
                    menu.getSlot(pendingExtraction.shulkerSlot()).getItem().getCount(),
                    menu.getSlot(destination).getItem().getCount(), menu.getCarried().getCount());
            if (moved != amount) {
                failExtraction(client, "message.better-shulker-hud.inventory_full");
                return;
            }
        } else if (pendingExtraction.takeOne()) {
            int destination = findDestinationSlot(menu, client.player.getInventory(), sourceStack,
                    pendingExtraction.inventorySlot());
            if (destination < 0) {
                diagnostic(extractionOperationId,
                        "destination-resolution-failed mode=take-one sourceBefore=%d",
                        before);
                failExtraction(client, "message.better-shulker-hud.inventory_full");
                return;
            }
            diagnostic(extractionOperationId,
                    "move-request menu=%d mode=take-one sourceShulkerSlot=%d destinationMenuSlot=%d sourceBefore=%d moveSyncBaseline=%d",
                    menu.containerId, pendingExtraction.shulkerSlot(), destination,
                    before, extractionMoveSyncVersion);
            takeOne(client, menu, pendingExtraction.shulkerSlot(), destination);
            int movedOne = before
                    - menu.getSlot(pendingExtraction.shulkerSlot()).getItem().getCount();
            diagnostic(extractionOperationId,
                    "move-local-result mode=take-one-primary-only moved=%d sourceAfter=%d destinationAfter=%d cursorCount=%d",
                    movedOne,
                    menu.getSlot(pendingExtraction.shulkerSlot()).getItem().getCount(),
                    menu.getSlot(destination).getItem().getCount(), menu.getCarried().getCount());
            if (movedOne != 1 || !menu.getCarried().isEmpty()) {
                failExtraction(client, "message.better-shulker-hud.source_changed");
                return;
            }
        } else {
            diagnostic(extractionOperationId,
                    "move-request menu=%d mode=quick-move sourceShulkerSlot=%d sourceBefore=%d moveSyncBaseline=%d",
                    menu.containerId, pendingExtraction.shulkerSlot(), before,
                    extractionMoveSyncVersion);
            client.gameMode.handleContainerInput(
                    menu.containerId, pendingExtraction.shulkerSlot(), 0,
                    ContainerInput.QUICK_MOVE, client.player);
        }

        int moved = before - menu.getSlot(pendingExtraction.shulkerSlot()).getItem().getCount();
        if (pendingExtraction.targetInventorySlot() < 0
                && !pendingExtraction.litematicaRestock()) {
            diagnostic(extractionOperationId,
                    "move-local-result mode=%s moved=%d sourceAfter=%d cursorCount=%d",
                    pendingExtraction.takeOne() ? "take-one" : "quick-move", moved,
                    menu.getSlot(pendingExtraction.shulkerSlot()).getItem().getCount(),
                    menu.getCarried().getCount());
        }
        extractionExpectedSourceCount = before - moved;
        extractionMovedItemCount = moved;
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
            diagnostic(cursorPickupOperationId,
                    "cursor-pickup-aborted reason=player-or-game-mode-missing");
            clearCursorPickup();
            return;
        }
        if (!(client.screen instanceof InventoryScreen)
                || client.player.containerMenu != client.player.inventoryMenu) {
            if (++cursorPickupWaitTicks > 20) {
                diagnostic(cursorPickupOperationId,
                        "timeout stage=cursor-return-to-inventory-screen waitedTicks=%d currentScreen=%s",
                        cursorPickupWaitTicks,
                        client.screen == null ? "null" : client.screen.getClass().getSimpleName());
                show(client, "message.better-shulker-hud.cursor_pickup_failed");
                clearCursorPickup();
            }
            return;
        }

        AbstractContainerMenu menu = client.player.inventoryMenu;
        if (!menu.getCarried().isEmpty()) {
            diagnostic(cursorPickupOperationId,
                    "cursor-pickup-failed reason=cursor-not-empty item=%s count=%d",
                    itemId(menu.getCarried()), menu.getCarried().getCount());
            show(client, "message.better-shulker-hud.cursor_pickup_failed");
            clearCursorPickup();
            return;
        }
        int menuSlot = findPlayerInventoryMenuSlot(
                menu, client.player.getInventory(), pendingCursorPickup.inventorySlot());
        if (menuSlot < 0) {
            diagnostic(cursorPickupOperationId,
                    "cursor-pickup-failed reason=staging-menu-slot-unresolved stagingInventorySlot=%d",
                    pendingCursorPickup.inventorySlot());
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
                diagnostic(cursorPickupOperationId,
                        "timeout stage=cursor-staging-sync stagingInventorySlot=%d menuSlot=%d expectedItem=%s expectedCount=%d actualItem=%s actualCount=%d",
                        pendingCursorPickup.inventorySlot(), menuSlot,
                        itemId(pendingCursorPickup.prototype()), expectedCount,
                        itemId(staged), staged.getCount());
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
            diagnostic(cursorPickupOperationId,
                    "operation-complete target=cursor confirmedAmount=%d stagingInventorySlot=%d restoredBaseline=%d",
                    carried.getCount(), pendingCursorPickup.inventorySlot(),
                    pendingCursorPickup.baselineCount());
            clearCursorPickup();
            return;
        }

        diagnostic(cursorPickupOperationId,
                "cursor-pickup-failed reason=post-click-validation carriedItem=%s carriedCount=%d restoredItem=%s restoredCount=%d expectedAmount=%d expectedBaseline=%d",
                itemId(carried), carried.getCount(), itemId(restored), restored.getCount(),
                pendingCursorPickup.extractedAmount(), pendingCursorPickup.baselineCount());
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

        if (returnAwaitingSync) {
            if (!hasContainerSyncAfter(menu.containerId, returnMoveSyncVersion)) {
                if (++returnWaitTicks > OPEN_TIMEOUT_TICKS) finishCurrentReturn(client);
                return;
            }
            if (!isConfirmedReturnTarget(menu)) {
                finishCurrentReturn(client);
                return;
            }
            activeReturn.remaining -= returnPendingMoved;
            returnedItemCount += returnPendingMoved;
            if (activeReturn.remaining <= 0) originRecords.remove(activeReturn);
            returnPendingMoved = 0;
            returnAwaitingSync = false;
            returnExpectedTargetSlot = -1;
            returnExpectedTargetCount = -1;
            returnWaitTicks = 0;
        }

        if (activeReturn.remaining <= 0
                || !hasMatchingPlayerItem(
                client.player.getInventory(), activeReturn.prototype)) {
            if (!selectNextReturnForOpenShulker(client, menu)) {
                finishCurrentReturn(client);
            }
            return;
        }

        int targetSlot = findReturnTargetSlot(menu, activeReturn.shulkerSlot, activeReturn.prototype);
        if (targetSlot < 0) {
            if (!selectNextReturnForOpenShulker(client, menu)) {
                finishCurrentReturn(client);
            }
            return;
        }
        Slot target = menu.getSlot(targetSlot);
        ItemStack targetStack = target.getItem();

        int capacity = target.getMaxStackSize(activeReturn.prototype) - targetStack.getCount();
        int sourceMenuSlot = findMatchingPlayerItemSlot(
                menu, client.player.getInventory(), activeReturn.prototype, activeReturnShulkerSlot);
        if (capacity <= 0 || activeReturn.remaining <= 0 || sourceMenuSlot < 0) {
            if (!selectNextReturnForOpenShulker(client, menu)) {
                finishCurrentReturn(client);
            }
            return;
        }

        ItemStack sourceStack = menu.getSlot(sourceMenuSlot).getItem();
        int amount = Math.min(Math.min(sourceStack.getCount(), capacity), activeReturn.remaining);
        int before = sourceStack.getCount();
        int targetBefore = targetStack.getCount();
        moveExactAmount(client, menu, sourceMenuSlot, targetSlot, amount);
        int after = menu.getSlot(sourceMenuSlot).getItem().getCount();
        int moved = Math.max(0, before - after);
        if (moved <= 0) {
            finishCurrentReturn(client);
            return;
        }
        returnMoveSyncVersion = containerSyncVersion;
        returnExpectedTargetSlot = targetSlot;
        returnExpectedTargetCount = targetBefore + moved;
        returnPendingMoved = moved;
        returnAwaitingSync = true;
        returnWaitTicks = 0;
    }

    private static boolean isConfirmedReturnTarget(ShulkerBoxMenu menu) {
        if (returnExpectedTargetSlot < 0
                || returnExpectedTargetSlot >= ShulkerContentsHelper.SHULKER_SIZE
                || returnExpectedTargetCount < 0 || activeReturn == null) return false;
        ItemStack target = menu.getSlot(returnExpectedTargetSlot).getItem();
        return ItemStack.isSameItemSameComponents(target, activeReturn.prototype)
                && target.getCount() >= returnExpectedTargetCount;
    }

    private static boolean selectNextReturnForOpenShulker(
            Minecraft client, ShulkerBoxMenu menu) {
        if (client.player == null || activeReturnShulkerSlot < 0) return false;
        Inventory inventory = client.player.getInventory();
        Iterator<OriginRecord> iterator = returnQueue.iterator();
        while (iterator.hasNext()) {
            OriginRecord record = iterator.next();
            if (record.remaining <= 0
                    || isOrganizeBlacklisted(record.prototype)
                    || !hasMatchingPlayerItem(inventory, record.prototype)) {
                iterator.remove();
                continue;
            }
            int shulkerInventorySlot = findOriginShulker(inventory, record);
            if (shulkerInventorySlot < 0) {
                iterator.remove();
                continue;
            }
            if (shulkerInventorySlot != activeReturnShulkerSlot) continue;
            if (findReturnTargetSlot(menu, record.shulkerSlot, record.prototype) < 0) {
                iterator.remove();
                continue;
            }
            iterator.remove();
            activeReturn = record;
            returnWaitTicks = 0;
            returnAwaitingSync = false;
            returnExpectedTargetSlot = -1;
            returnExpectedTargetCount = -1;
            returnPendingMoved = 0;
            return true;
        }
        activeReturn = null;
        return false;
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
            if (record.remaining <= 0
                    || isOrganizeBlacklisted(record.prototype)
                    || !hasMatchingPlayerItem(client.player.getInventory(), record.prototype)) {
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
            returnMoveSyncVersion = 0;
            returnAwaitingSync = false;
            returnExpectedTargetSlot = -1;
            returnExpectedTargetCount = -1;
            returnPendingMoved = 0;
            OpenShulkerPacket.sendOpenPacket(menuSlot);
            return;
        }

        int completed = returnedItemCount;
        clearReturnProcess();
        if (organizeActive) {
            organizedItemCount += completed;
            organizeDelay = 1;
            return;
        }
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
        returnMoveSyncVersion = 0;
        returnAwaitingSync = false;
        returnExpectedTargetSlot = -1;
        returnExpectedTargetCount = -1;
        returnPendingMoved = 0;
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
        return findCursorStagingDestination(
                screen.getMenu(), inventory, source, sourceShulkerInventorySlot);
    }

    private static CursorStaging findCursorStagingDestination(
            AbstractContainerMenu menu, Inventory inventory,
            ItemStack source, int sourceShulkerInventorySlot) {
        CursorStaging matching = null;
        for (Slot slot : menu.slots) {
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
            Minecraft client, AbstractContainerMenu menu,
            int sourceSlot, int targetSlot, int amount) {
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

    private static void takeOne(
            Minecraft client, ShulkerBoxMenu menu,
            int sourceSlot, int destinationSlot) {
        int sourceCount = menu.getSlot(sourceSlot).getItem().getCount();
        client.gameMode.handleContainerInput(
                menu.containerId, sourceSlot, 0, ContainerInput.PICKUP, client.player);

        if (sourceCount == 1) {
            client.gameMode.handleContainerInput(
                    menu.containerId, destinationSlot, 0,
                    ContainerInput.PICKUP, client.player);
            return;
        }

        int quickCraftType = AbstractContainerMenu.QUICKCRAFT_TYPE_GREEDY;
        client.gameMode.handleContainerInput(
                menu.containerId, -999,
                AbstractContainerMenu.getQuickcraftMask(
                        AbstractContainerMenu.QUICKCRAFT_HEADER_START, quickCraftType),
                ContainerInput.QUICK_CRAFT, client.player);
        client.gameMode.handleContainerInput(
                menu.containerId, destinationSlot,
                AbstractContainerMenu.getQuickcraftMask(
                        AbstractContainerMenu.QUICKCRAFT_HEADER_CONTINUE, quickCraftType),
                ContainerInput.QUICK_CRAFT, client.player);
        client.gameMode.handleContainerInput(
                menu.containerId, sourceSlot,
                AbstractContainerMenu.getQuickcraftMask(
                        AbstractContainerMenu.QUICKCRAFT_HEADER_CONTINUE, quickCraftType),
                ContainerInput.QUICK_CRAFT, client.player);
        client.gameMode.handleContainerInput(
                menu.containerId, -999,
                AbstractContainerMenu.getQuickcraftMask(
                        AbstractContainerMenu.QUICKCRAFT_HEADER_END, quickCraftType),
                ContainerInput.QUICK_CRAFT, client.player);

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
        return findValidatedSource(screen.getMenu(), inventory, item);
    }

    private static ResolvedSource findValidatedSource(
            AbstractContainerMenu menu, Inventory inventory,
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
                    menu, inventory, source.inventorySlot());
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
                int quickSlot = resolveQuickShulkerSlot(menu, inventory, inventorySlot);
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
        diagnostic(extractionOperationId,
                "operation-failed kind=extraction reason=%s menu=%d waitTicks=%d moved=%d expectedSourceCount=%d openSyncBaseline=%d moveSyncBaseline=%d currentSyncVersion=%d",
                messageKey, extractionMenuId, extractionWaitTicks,
                extractionMovedItemCount, extractionExpectedSourceCount,
                extractionOpenSyncVersion, extractionMoveSyncVersion, containerSyncVersion);
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
        long completedOperationId = extractionOperationId;
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
            cursorPickupOperationId = completedOperationId;
            cursorPickupWaitTicks = 0;
            diagnostic(completedOperationId,
                    "extraction-confirmed moved=%d sourceAfter=%d nextStage=cursor-pickup stagingInventorySlot=%d baseline=%d",
                    extractionMovedItemCount, extractionExpectedSourceCount,
                    completedExtraction.targetInventorySlot(),
                    completedExtraction.targetBaselineCount());
        } else {
            int confirmedAmount = completedExtraction == null ? 0
                    : Math.max(0, completedExtraction.expectedStack().getCount()
                    - extractionExpectedSourceCount);
            diagnostic(completedOperationId,
                    "operation-complete kind=extraction target=%s confirmedAmount=%d sourceAfter=%d menu=%d",
                    handRestock ? "main-hand" : litematicaRestock ? "litematica" : "inventory",
                    confirmedAmount, extractionExpectedSourceCount, extractionMenuId);
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
        extractionOperationId = 0;
    }

    private static void clearCursorPickup() {
        pendingCursorPickup = null;
        cursorPickupWaitTicks = 0;
        cursorPickupOperationId = 0;
    }

    private static void failStore(Minecraft client, String messageKey) {
        StorePurpose purpose = pendingStore == null
                ? StorePurpose.MANUAL : pendingStore.purpose();
        boolean backgroundClearance = purpose == StorePurpose.EXTRACTION_PREPARATION
                && deferredExtraction != null
                && deferredExtraction.litematicaRestock();
        diagnostic(storeOperationId,
                "operation-failed kind=store purpose=%s reason=%s menu=%d waitTicks=%d moved=%d expectedTargetCount=%d openSyncBaseline=%d moveSyncBaseline=%d currentSyncVersion=%d",
                purpose, messageKey, storeMenuId, storeWaitTicks, storedItemCount,
                storeExpectedTargetCount, storeOpenSyncVersion, storeMoveSyncVersion,
                containerSyncVersion);
        if (client.player != null && client.player.containerMenu instanceof ShulkerBoxMenu) {
            closeContainerAndSetScreen(
                    client,
                    backgroundClearance ? null : new InventoryScreen(client.player),
                    !backgroundClearance);
        }
        clearStore();
        if (purpose == StorePurpose.EXTRACTION_PREPARATION) {
            clearDeferredExtraction();
            show(client, "message.better-shulker-hud.cannot_free_slot");
        } else if (purpose == StorePurpose.MATCHING_SORT) {
            if (++organizeRetryCount <= 2) {
                organizeDelay = 2;
            } else {
                finishOrganize(client, true);
            }
        } else {
            show(client, messageKey);
        }
    }

    private static void closeAfterStore(Minecraft client) {
        StorePurpose purpose = pendingStore == null
                ? StorePurpose.MANUAL : pendingStore.purpose();
        long completedOperationId = storeOperationId;
        boolean backgroundClearance = purpose == StorePurpose.EXTRACTION_PREPARATION
                && deferredExtraction != null
                && deferredExtraction.litematicaRestock();
        ItemStack storedPrototype = pendingStore == null
                ? ItemStack.EMPTY : pendingStore.prototype();
        boolean keepMenuForDeferredExtraction = purpose == StorePurpose.EXTRACTION_PREPARATION
                && deferredExtraction != null
                && client.player != null
                && client.player.containerMenu instanceof ShulkerBoxMenu menu
                && canResumeDeferredExtractionInCurrentMenu(client, menu, deferredExtraction);
        deferredExtractionMenuId = keepMenuForDeferredExtraction
                ? client.player.containerMenu.containerId : -1;
        if (client.player != null && !keepMenuForDeferredExtraction) {
            closeContainerAndSetScreen(
                    client,
                    backgroundClearance ? null : new InventoryScreen(client.player),
                    !backgroundClearance);
        }
        int completed = storedItemCount;
        diagnostic(completedOperationId,
                "operation-complete kind=store purpose=%s confirmedAmount=%d menu=%d expectedTargetCount=%d",
                purpose, completed, storeMenuId, storeExpectedTargetCount);
        clearStore();
        if (completed > 0 && !storedPrototype.isEmpty()) {
            consumeOriginRecords(storedPrototype, completed);
        }
        if (purpose == StorePurpose.EXTRACTION_PREPARATION) {
            if (completed > 0 && deferredExtraction != null) {
                deferredExtractionDelay = 0;
            } else {
                clearDeferredExtraction();
                show(client, "message.better-shulker-hud.cannot_free_slot");
            }
        } else if (purpose == StorePurpose.MATCHING_SORT) {
            organizedItemCount += completed;
            organizeRetryCount = 0;
            organizeDelay = 2;
        } else if (completed > 0) {
            show(client, "message.better-shulker-hud.store_complete", completed);
        }
    }

    private static boolean canResumeDeferredExtractionInCurrentMenu(
            Minecraft client, ShulkerBoxMenu menu, DeferredExtraction deferred) {
        if (pendingStore == null || deferred == null || client.player == null) return false;
        ResolvedSource source = deferred.litematicaRestock()
                ? findRestockSource(menu, client.player.getInventory(), deferred.litematicaRequired())
                : findValidatedSource(menu, client.player.getInventory(), deferred.item());
        return source != null && source.inventorySlot() == pendingStore.targetInventorySlot();
    }

    private static boolean resumeDeferredExtractionInCurrentMenu(
            Minecraft client, ShulkerBoxMenu menu, DeferredExtraction deferred) {
        if (deferred == null || client.player == null || pendingStore != null) return false;
        Inventory inventory = client.player.getInventory();
        ResolvedSource source = deferred.litematicaRestock()
                ? findRestockSource(menu, inventory, deferred.litematicaRequired())
                : findValidatedSource(menu, inventory, deferred.item());
        if (source == null) return false;

        if (deferred.litematicaRestock()) {
            int capacity = inventoryCapacity(menu, inventory,
                    source.expectedStack(), source.inventorySlot());
            if (capacity <= 0) return false;
            int requestedAmount = Math.min(source.expectedStack().getCount(), capacity);
            pendingExtraction = new PendingExtraction(
                    source.inventorySlot(), source.shulkerSlot(), source.expectedStack(), false,
                    source.shulkerItem(), source.shulkerName(), true, false, false,
                    -1, requestedAmount, 0);
            pendingLitematicaSelection = deferred.litematicaRequired().copyWithCount(1);
        } else if (deferred.cursorPickup()) {
            CursorStaging staging = findCursorStagingDestination(
                    menu, inventory, source.expectedStack(), source.inventorySlot());
            if (staging == null) return false;
            int requestedAmount = Math.min(source.expectedStack().getCount(), staging.capacity());
            pendingExtraction = new PendingExtraction(
                    source.inventorySlot(), source.shulkerSlot(), source.expectedStack(), false,
                    source.shulkerItem(), source.shulkerName(), false, false, true,
                    staging.inventorySlot(), requestedAmount, staging.baselineCount());
        } else {
            int capacity = inventoryCapacity(menu, inventory,
                    source.expectedStack(), source.inventorySlot());
            int required = deferred.takeOne() ? 1 : source.expectedStack().getCount();
            if (capacity < required) return false;
            pendingExtraction = new PendingExtraction(
                    source.inventorySlot(), source.shulkerSlot(), source.expectedStack(),
                    deferred.takeOne(), source.shulkerItem(), source.shulkerName(),
                    false, false, false, -1, 0, 0);
        }

        extractionOperationId = deferred.operationId();
        extractionWaitTicks = 0;
        extractionMenuId = menu.containerId;
        extractionCloseDelay = -1;
        extractionMovedItemCount = 0;
        extractionExpectedSourceCount = -1;
        long menuSync = containerSyncVersions.getOrDefault(menu.containerId, containerSyncVersion);
        extractionOpenSyncVersion = menuSync - 1;
        extractionMoveSyncVersion = 0;
        diagnostic(extractionOperationId,
                "resume-open-menu extraction menu=%d sourceInventorySlot=%d shulkerSlot=%d",
                menu.containerId, source.inventorySlot(), source.shulkerSlot());
        return true;
    }

    private static void closeContainerAndSetScreen(
            Minecraft client, Screen nextScreen, boolean preserveCursor) {
        boolean keepCurrentInventoryScreen = nextScreen instanceof InventoryScreen
                && client.screen instanceof InventoryScreen;
        long window = client.getWindow().handle();
        double[] cursorX = new double[1];
        double[] cursorY = new double[1];
        if (preserveCursor) {
            GLFW.glfwGetCursorPos(window, cursorX, cursorY);
        }

        if (client.player != null) {
            preserveInventoryScreenDuringContainerClose = keepCurrentInventoryScreen;
            try {
                client.player.closeContainer();
            } finally {
                preserveInventoryScreenDuringContainerClose = false;
            }
        }
        if (!keepCurrentInventoryScreen) {
            client.setScreen(nextScreen);
        }

        if (preserveCursor && nextScreen != null && !keepCurrentInventoryScreen) {
            GLFW.glfwSetCursorPos(window, cursorX[0], cursorY[0]);
        }
    }

    private static void clearStore() {
        pendingStore = null;
        storeWaitTicks = 0;
        storeMenuId = -1;
        storeCloseDelay = -1;
        storedItemCount = 0;
        storeTransfer = null;
        storeOpenSyncVersion = 0;
        storeMoveSyncVersion = 0;
        storeExpectedTargetCount = -1;
        storeContinueAfterMove = false;
        storeOperationId = 0;
    }

    private static void clearDeferredExtraction() {
        deferredExtraction = null;
        deferredExtractionDelay = -1;
        deferredExtractionMenuId = -1;
    }

    private static void finishOrganize(Minecraft client, boolean failed) {
        int completed = organizedItemCount;
        clearOrganizeProcess();
        if (failed) {
            show(client, "message.better-shulker-hud.organize_failed", completed);
        } else if (completed > 0) {
            show(client, "message.better-shulker-hud.organize_complete", completed);
        } else {
            show(client, "message.better-shulker-hud.nothing_to_organize");
        }
    }

    private static void clearOrganizeProcess() {
        organizeActive = false;
        organizeDelay = -1;
        organizedItemCount = 0;
        organizeRetryCount = 0;
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
        returnMoveSyncVersion = 0;
        returnAwaitingSync = false;
        returnExpectedTargetSlot = -1;
        returnExpectedTargetCount = -1;
        returnPendingMoved = 0;
    }

    private static void show(Minecraft client, String key, Object... args) {
        if (client.player != null) {
            client.player.sendOverlayMessage(Component.translatable(key, args));
        }
    }

    private static boolean canSendQuickShulker() {
        return ClientPlayNetworking.canSend(OpenShulkerPacket.OPEN_SHULKER_PACKET_ID);
    }

    private static long nextDiagnosticOperationId() {
        if (!Configs.General.DIAGNOSTIC_LOGGING.getBooleanValue()) return 0;
        return ++diagnosticOperationSequence;
    }

    private static long activeDiagnosticOperationId() {
        if (pendingStore != null && storeOperationId != 0) return storeOperationId;
        if (pendingExtraction != null && extractionOperationId != 0) return extractionOperationId;
        if (pendingCursorPickup != null && cursorPickupOperationId != 0) {
            return cursorPickupOperationId;
        }
        return storeOperationId != 0 ? storeOperationId
                : extractionOperationId != 0 ? extractionOperationId : cursorPickupOperationId;
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "minecraft:air";
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static void diagnostic(long operationId, String message, Object... args) {
        if (!Configs.General.DIAGNOSTIC_LOGGING.getBooleanValue()) return;
        String detail = args.length == 0
                ? message : String.format(Locale.ROOT, message, args);
        BetterBundleMod.LOGGER.info("{} [op={}] {}", DIAGNOSTIC_PREFIX, operationId, detail);
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
            ItemStack prototype, Item shulkerItem, Component shulkerName,
            StorePurpose purpose) {}

    private enum StorePurpose {
        MANUAL,
        EXTRACTION_PREPARATION,
        MATCHING_SORT
    }

    private record StoreTarget(
            int inventorySlot, int shulkerSlot, Item shulkerItem, Component shulkerName) {}

    private record MatchingTarget(StoreTarget target, int capacity) {}

    private record MatchingStore(
            int sourceInventorySlot, int amount, ItemStack prototype, StoreTarget target) {}

    private record ClearanceCandidate(
            int sourceInventorySlot, int amount, ItemStack prototype,
            StoreTarget target, int score) {}

    private record StoreTransfer(
            int sourceMenuSlot, int targetMenuSlot, int beforeSourceCount,
            int amount, boolean placeMovedItemsOneByOne, int repeatedClicksRemaining) {
        private StoreTransfer withRemaining(int remaining) {
            return new StoreTransfer(
                    sourceMenuSlot, targetMenuSlot, beforeSourceCount,
                    amount, placeMovedItemsOneByOne, remaining);
        }
    }

    private record DeferredExtraction(
            BundlePanelRenderer.FlatItem item, boolean takeOne, boolean cursorPickup,
            ItemStack litematicaRequired, long operationId) {
        private boolean litematicaRestock() {
            return litematicaRequired != null && !litematicaRequired.isEmpty();
        }
    }

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
