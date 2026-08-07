package bettershulkerhud.compat;

import bettershulkerhud.BetterBundleMod;
import bettershulkerhud.config.Configs;
import bettershulkerhud.event.InventoryDragStoreController;
import bettershulkerhud.gui.BundlePanelRenderer;
import bettershulkerhud.util.ShulkerContentsHelper;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
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
    private static final int LOCAL_CONFIRMATION_GRACE_TICKS = 6;
    private static final int MAX_PROGRAMMATIC_DELAY_TICKS = 20;
    // Keep the interaction sequence server-authoritative, but finish a normal
    // stack in a few ticks instead of spreading it over eight-click batches.
    private static final int MAX_STORE_CLICKS_PER_TICK = 32;
    private static final int MAX_QUEUED_EXTRACTIONS = 64;
    private static final int AX_AUTOMATIC_RESTOCK_SETTLE_TICKS = 8;
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
    private static boolean extractionToOffhand;
    private static boolean extractionOffhandBackground;
    private static int extractionOffhandBaselineCount;
    private static ItemStack extractionOffhandOriginal = ItemStack.EMPTY;
    private static boolean extractionBucketReplacement;
    private static int extractionBucketSelectedSlot = -1;
    private static int extractionEmptyBucketDestination = -1;
    private static int extractionEmptyBucketDestinationBaseline;
    private static final ArrayDeque<QueuedExtraction> queuedExtractions = new ArrayDeque<>();
    private static int queuedExtractionWaitTicks;
    private static int queuedExtractionStartDelayTicks;
    private static int extractionOperationDelayTicks;
    private static boolean programmaticExtractionBatch;
    private static int programmaticInventorySettleTicks;

    private static PendingCursorPickup pendingCursorPickup;
    private static int cursorPickupWaitTicks;

    private static PendingOffhandTransfer pendingOffhandTransfer;
    private static int offhandTransferWaitTicks;
    private static long offhandTransferOperationId;
    private static long offhandTransferMoveSyncVersion;
    private static boolean offhandTransferAwaitingSync;

    private static PendingBucketTransfer pendingBucketTransfer;
    private static int bucketTransferWaitTicks;
    private static long bucketTransferOperationId;
    private static long bucketTransferMoveSyncVersion;
    private static boolean bucketTransferAwaitingSync;

    private static PendingStore pendingStore;
    private static int storeWaitTicks;
    private static int storeMenuId = -1;
    private static int storeCloseDelay = -1;
    private static int storedItemCount;
    private static StoreTransfer storeTransfer;
    private static long storeOpenSyncVersion;
    private static long storeMoveSyncVersion;
    private static int storeExpectedTargetCount = -1;
    private static int storeOperationDelayTicks;
    private static boolean storeContinueAfterMove;
    private static int queuedStoreSessionCount;
    private static int programmaticExtractionDelayTicks = 1;
    private static int programmaticStorageDelayTicks = 2;

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
    private static int axAutomaticRestockSettleTicks = -1;
    private static long blockedAxAutomaticRestockFingerprint = Long.MIN_VALUE;
    private static ItemStack rememberedMainSingleItem = ItemStack.EMPTY;
    private static int rememberedMainSingleSlot = -1;
    private static int rememberedMainSingleLooseCount;
    private static ItemStack rememberedOffhandSingleItem = ItemStack.EMPTY;
    private static int rememberedOffhandSingleLooseCount;
    private static ItemStack rememberedMainWaterBucket = ItemStack.EMPTY;
    private static int rememberedMainWaterBucketSlot = -1;
    private static int rememberedMainWaterBucketLooseCount;
    private static boolean preserveInventoryScreenDuringContainerClose;

    private static final List<OriginRecord> originRecords = new ArrayList<>();
    private static final ArrayDeque<OriginRecord> returnQueue = new ArrayDeque<>();
    private static OriginRecord activeReturn;
    private static int activeReturnShulkerSlot = -1;
    private static int returnWaitTicks;
    private static int returnSourceResolveWaitTicks;
    private static int returnMenuId = -1;
    private static int nextReturnDelay = -1;
    private static int returnedItemCount;
    private static long returnOpenSyncVersion;
    private static long returnMoveSyncVersion;
    private static boolean returnAwaitingSync;
    private static int returnExpectedTargetSlot = -1;
    private static int returnExpectedTargetCount = -1;
    private static int returnPendingMoved;
    private static int returnConfirmationTicks;
    private static ItemStack pendingLitematicaSelection = ItemStack.EMPTY;

    private QuickShulkerExtractionController() {}

    public static void onClientTick(Minecraft client) {
        updateRememberedHandItems(client);
        tick(client);
        if (pendingExtraction == null && queuedExtractions.isEmpty()
                && programmaticExtractionBatch) {
            programmaticExtractionBatch = false;
            programmaticInventorySettleTicks = Math.max(
                    programmaticInventorySettleTicks,
                    Math.max(3, programmaticExtractionDelayTicks));
        }
        tickProgrammaticInventorySettle(client);
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
        clearOffhandTransfer();
        clearBucketTransfer();
        clearStore();
        clearDeferredExtraction();
        queuedExtractions.clear();
        queuedExtractionWaitTicks = 0;
        queuedExtractionStartDelayTicks = 0;
        extractionOperationDelayTicks = 0;
        programmaticExtractionBatch = false;
        programmaticInventorySettleTicks = 0;
        storeOperationDelayTicks = 0;
        clearReturnProcess();
        clearOrganizeProcess();
        autoRestockCooldown = 0;
        axAutomaticRestockSettleTicks = -1;
        blockedAxAutomaticRestockFingerprint = Long.MIN_VALUE;
        rememberedMainSingleItem = ItemStack.EMPTY;
        rememberedMainSingleSlot = -1;
        rememberedMainSingleLooseCount = 0;
        rememberedOffhandSingleItem = ItemStack.EMPTY;
        rememberedOffhandSingleLooseCount = 0;
        rememberedMainWaterBucket = ItemStack.EMPTY;
        rememberedMainWaterBucketSlot = -1;
        rememberedMainWaterBucketLooseCount = 0;
        pendingLitematicaSelection = ItemStack.EMPTY;
        containerSyncVersion = 0;
        containerSyncVersions.clear();
        organizeAvailabilityFingerprint = Long.MIN_VALUE;
        cachedOrganizeAvailability = false;
        originRecords.clear();
        queuedStoreSessionCount = 0;
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

    private static long automaticRestockFingerprint(Inventory inventory) {
        long fingerprint = inventoryFingerprint(inventory);
        ItemStack offhand = inventory.getItem(Inventory.SLOT_OFFHAND);
        fingerprint = 31L * fingerprint + ItemStack.hashItemAndComponents(offhand);
        fingerprint = 31L * fingerprint + offhand.getCount();
        return 31L * fingerprint + inventory.selected;
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

        if (isBusy() || !queuedExtractions.isEmpty()) {
            if (queuedExtractions.size() < MAX_QUEUED_EXTRACTIONS) {
                queuedExtractions.addLast(new QueuedExtraction(item, takeOne, operationId));
                diagnostic(operationId,
                        "request-queued reason=controller-busy activeOperation=%d queueSize=%d",
                        activeDiagnosticOperationId(), queuedExtractions.size());
            }
            return;
        }
        if (!canUseShulkerBackend(client, operationId)) return;

        startInventoryExtraction(client, screen, item, takeOne, true, operationId);
    }

    public static void requestSource(
            BundlePanelRenderer.ItemSource source, boolean takeOne) {
        if (source == null || source.stack().isEmpty()) return;
        request(new BundlePanelRenderer.FlatItem(
                source.stack().copy(), List.of(source)), takeOne);
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
                source.quickShulkerSlot(), canUseConfiguredBackend(), extractionOpenSyncVersion,
                takeOne ? 1 : source.expectedStack().getCount());
        if (!openShulker(client, source.quickShulkerSlot(), operationId)) {
            failExtraction(client, "message.better-shulker-hud.open_failed");
            return;
        }
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
        if (!canUseShulkerBackend(client, operationId)) return;

        startCursorExtraction(client, screen, item, true, operationId);
    }

    public static void requestToOffhand(BundlePanelRenderer.FlatItem item) {
        Minecraft client = Minecraft.getInstance();
        long operationId = nextDiagnosticOperationId();
        if (client.player == null
                || !(client.screen instanceof AbstractContainerScreen<?> screen)
                || item == null || item.stack().isEmpty()
                || !screen.getMenu().getCarried().isEmpty()) {
            return;
        }
        if (isBusy()) {
            show(client, "message.better-shulker-hud.busy");
            return;
        }
        if (!canUseShulkerBackend(client, operationId)) return;

        ResolvedSource source = findValidatedSource(
                screen, client.player.getInventory(), item);
        if (source == null) {
            show(client, "message.better-shulker-hud.source_missing");
            return;
        }
        startOffhandExtraction(
                client, screen.getMenu(), source, Integer.MAX_VALUE, false, operationId);
    }

    private static boolean startOffhandExtraction(
            Minecraft client, AbstractContainerMenu sourceMenu,
            ResolvedSource source, int requestedLimit,
            boolean background, long operationId) {
        if (client.player == null || source == null) return false;
        Inventory inventory = client.player.getInventory();
        ItemStack offhand = inventory.getItem(Inventory.SLOT_OFFHAND);
        boolean swap = !offhand.isEmpty()
                && !ItemStack.isSameItemSameComponents(offhand, source.expectedStack());
        if (swap && background) return false;
        int offhandCapacity = swap ? source.expectedStack().getMaxStackSize()
                : source.expectedStack().getMaxStackSize() - offhand.getCount();
        CursorStaging staging = findCursorStagingDestination(
                sourceMenu, inventory, source.expectedStack(), source.inventorySlot());
        if (offhandCapacity <= 0 || staging == null || (swap && staging.baselineCount() != 0)) {
            if (!background) show(client, "message.better-shulker-hud.offhand_unavailable");
            return false;
        }
        int requestedAmount = Math.min(
                Math.min(source.expectedStack().getCount(), requestedLimit),
                Math.min(offhandCapacity, staging.capacity()));
        if (requestedAmount <= 0) return false;

        pendingExtraction = new PendingExtraction(
                source.inventorySlot(), source.shulkerSlot(), source.expectedStack(), false,
                source.shulkerItem(), source.shulkerName(), false, false, false,
                staging.inventorySlot(), requestedAmount, staging.baselineCount());
        extractionToOffhand = true;
        extractionOffhandBackground = background;
        extractionOffhandBaselineCount = offhand.getCount();
        extractionOffhandOriginal = offhand.copy();
        extractionOperationId = operationId;
        extractionWaitTicks = 0;
        extractionMenuId = -1;
        extractionCloseDelay = -1;
        extractionMovedItemCount = 0;
        extractionExpectedSourceCount = -1;
        extractionOpenSyncVersion = containerSyncVersion;
        diagnostic(operationId,
                "request type=%s item=%s sourceInventorySlot=%d shulkerSlot=%d stagingInventorySlot=%d offhandItem=%s offhandBaseline=%d swap=%s requested=%d",
                background ? "automatic-offhand-restock" : "offhand-hotkey",
                itemId(source.expectedStack()), source.inventorySlot(), source.shulkerSlot(),
                staging.inventorySlot(), itemId(offhand), offhand.getCount(), swap, requestedAmount);
        if (!openShulker(client, source.quickShulkerSlot(), operationId)) {
            failExtraction(client, "message.better-shulker-hud.open_failed");
            return false;
        }
        return true;
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
                source.quickShulkerSlot(), canUseConfiguredBackend(), extractionOpenSyncVersion);
        if (!openShulker(client, source.quickShulkerSlot(), operationId)) {
            failExtraction(client, "message.better-shulker-hud.open_failed");
            return;
        }
        diagnostic(operationId, "packet-sent open-shulker");
    }

    public static void requestLitematicaRestock(ItemStack required) {
        Minecraft client = Minecraft.getInstance();
        if (!Configs.Features.LITEMATICA_RESTOCK.getBooleanValue()
                || required == null || required.isEmpty() || client.player == null
                || client.gameMode == null || isBusy()
                || hasMatchingPlayerItem(client.player.getInventory(), required)
                || !canUseConfiguredBackend()) {
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
                canUseConfiguredBackend(), extractionOpenSyncVersion);
        if (!openShulker(client, source.quickShulkerSlot(), operationId)) {
            failExtraction(client, "message.better-shulker-hud.open_failed");
            return;
        }
        diagnostic(operationId, "packet-sent open-shulker");
    }

    public static void requestReturnAll() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !(client.screen instanceof AbstractContainerScreen<?>)) return;

        if (isBusy()) {
            show(client, "message.better-shulker-hud.busy");
            return;
        }
        if (!canUseShulkerBackend(client, 0)) return;

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
        if (!canUseShulkerBackend(client, operationId)) return;

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
        client.gameMode.handleInventoryMouseClick(
                screen.getMenu().containerId, temporary.menuSlot(), 0,
                ClickType.PICKUP, client.player);
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
                prototype, target.shulkerItem(), target.shulkerName(), StorePurpose.MANUAL,
                findStoredMainHandMemorySlot(client.player.getInventory(), prototype, -1));
        diagnostic(operationId,
                "store-staged sourceInventorySlot=%d targetInventorySlot=%d shulkerSlot=%d quickShulkerSlot=%d amount=%d",
                temporary.inventorySlot(), target.inventorySlot(), target.shulkerSlot(),
                targetMenuSlot, amount);
        openPendingStore(targetMenuSlot, operationId);
    }

    public static void requestStoreInventorySlot(
            AbstractContainerScreen<?> screen, int sourceInventorySlot, boolean oneItem) {
        requestStoreInventorySlot(
                screen, sourceInventorySlot, oneItem, StorePurpose.INVENTORY_DRAG);
    }

    public static void requestProgrammaticStoreInventorySlot(
            AbstractContainerScreen<?> screen, int sourceInventorySlot, boolean oneItem) {
        requestStoreInventorySlot(
                screen, sourceInventorySlot, oneItem, StorePurpose.PROGRAMMATIC_BATCH);
    }

    private static void requestStoreInventorySlot(
            AbstractContainerScreen<?> screen, int sourceInventorySlot, boolean oneItem,
            StorePurpose purpose) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null || isBusy()) return;
        if (!screen.getMenu().getCarried().isEmpty()
                || sourceInventorySlot < 0 || sourceInventorySlot >= 36) return;

        ItemStack source = client.player.getInventory().getItem(sourceInventorySlot);
        if (source.isEmpty() || ShulkerContentsHelper.isShulker(source)) return;

        int requestedAmount = oneItem ? 1 : source.getCount();
        long operationId = nextDiagnosticOperationId();
        diagnostic(operationId,
                "request type=inventory-drag-store sourceInventorySlot=%d item=%s count=%d oneItem=%s",
                sourceInventorySlot, itemId(source), requestedAmount, oneItem);
        if (!canUseShulkerBackend(client, operationId)) return;

        ItemStack prototype = source.copyWithCount(1);
        StoreTarget target = findStoreTarget(
                client.player.getInventory(), prototype, requestedAmount,
                purpose == StorePurpose.PROGRAMMATIC_BATCH);
        if (target == null) {
            diagnostic(operationId, "store-rejected reason=no-shulker-capacity");
            show(client, "message.better-shulker-hud.no_shulker_space");
            return;
        }
        int amount = Math.min(requestedAmount, storeTargetCapacity(
                client.player.getInventory(), target, prototype));
        if (amount <= 0) return;

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
                sourceInventorySlot, amount, target.inventorySlot(), target.shulkerSlot(),
                prototype, target.shulkerItem(), target.shulkerName(),
                purpose,
                findStoredMainHandMemorySlot(
                        client.player.getInventory(), prototype, sourceInventorySlot));
        queuedStoreSessionCount = 0;
        diagnostic(operationId,
                "inventory-drag-store queued sourceInventorySlot=%d targetInventorySlot=%d shulkerSlot=%d quickShulkerSlot=%d amount=%d",
                sourceInventorySlot, target.inventorySlot(), target.shulkerSlot(),
                targetMenuSlot, amount);
        openPendingStore(targetMenuSlot, operationId);
    }

    public static boolean requestStoreInventorySlots(
            AbstractContainerScreen<?> screen, List<Integer> sourceInventorySlots) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null || isBusy()
                || sourceInventorySlots == null || sourceInventorySlots.isEmpty()) return false;

        List<Integer> validSlots = sourceInventorySlots.stream()
                .filter(Objects::nonNull)
                .filter(slot -> slot >= 0 && slot < 36)
                .distinct()
                .filter(slot -> {
                    ItemStack stack = client.player.getInventory().getItem(slot);
                    return !stack.isEmpty() && !ShulkerContentsHelper.isShulker(stack);
                })
                .toList();
        if (validSlots.isEmpty()) return false;

        InventoryDragStoreController.clearProgrammaticQueue();
        InventoryDragStoreController.enqueueProgrammatic(
                client, validSlots);
        requestProgrammaticStoreInventorySlot(screen, validSlots.getFirst(), false);
        if (pendingStore == null) {
            InventoryDragStoreController.clearProgrammaticQueue();
            return false;
        }
        diagnostic(storeOperationId,
                "batch-store-start firstInventorySlot=%d queuedRemaining=%d",
                validSlots.getFirst(), validSlots.size() - 1);
        return true;
    }

    public static void configureProgrammaticTransferDelays(
            int extractionDelayTicks, int storageDelayTicks) {
        programmaticExtractionDelayTicks = clampProgrammaticDelay(extractionDelayTicks);
        programmaticStorageDelayTicks = clampProgrammaticDelay(storageDelayTicks);
    }

    public static void beginProgrammaticExtractionBatch() {
        programmaticExtractionBatch = true;
        programmaticInventorySettleTicks = 0;
    }

    private static int clampProgrammaticDelay(int ticks) {
        return Math.max(0, Math.min(MAX_PROGRAMMATIC_DELAY_TICKS, ticks));
    }

    public static boolean hasActiveOperation() {
        return isBusy() || !queuedExtractions.isEmpty()
                || programmaticExtractionBatch
                || programmaticInventorySettleTicks > 0;
    }

    private static void tickProgrammaticInventorySettle(Minecraft client) {
        if (programmaticInventorySettleTicks <= 0 || client.player == null) return;
        if (!(client.player.containerMenu instanceof InventoryMenu)) {
            programmaticInventorySettleTicks = Math.max(
                    3, programmaticExtractionDelayTicks);
            if (client.player.containerMenu instanceof ShulkerBoxMenu) {
                closeContainerAndSetScreen(
                        client, new InventoryScreen(client.player), true, true);
            }
            return;
        }
        if (!(client.screen instanceof InventoryScreen screen)
                || screen.getMenu() != client.player.containerMenu) {
            client.setScreen(new InventoryScreen(client.player));
            programmaticInventorySettleTicks = Math.max(
                    3, programmaticExtractionDelayTicks);
            return;
        }
        programmaticInventorySettleTicks--;
    }

    private static boolean isBusy() {
        return pendingExtraction != null || pendingStore != null
                || pendingCursorPickup != null
                || pendingOffhandTransfer != null
                || pendingBucketTransfer != null
                || deferredExtraction != null
                || activeReturn != null
                || !returnQueue.isEmpty() || nextReturnDelay >= 0
                || organizeActive;
    }

    public static boolean shouldHideQuickShulkerScreen() {
        boolean programmaticStorage = pendingStore != null
                && pendingStore.purpose() == StorePurpose.PROGRAMMATIC_BATCH;
        boolean programmaticExtraction = programmaticExtractionBatch
                && pendingExtraction != null;
        return programmaticStorage || programmaticExtraction
                || Configs.Features.HIDE_QUICK_SHULKER_SCREEN.getBooleanValue()
                && (pendingExtraction != null || pendingStore != null
                || deferredExtraction != null || activeReturn != null);
    }

    public static boolean shouldPreserveInventoryScreenDuringContainerClose() {
        return preserveInventoryScreenDuringContainerClose;
    }

    public static ActiveShulkerContents getActiveAxShulkerContents() {
        Minecraft client = Minecraft.getInstance();
        if (activeBackend() != Configs.ShulkerOpenBackend.AX_SHULKERS
                || client.player == null
                || !(client.player.containerMenu instanceof ShulkerBoxMenu menu)) {
            return null;
        }

        int inventorySlot;
        Item expectedItem;
        Component expectedName;
        if (pendingStore != null) {
            inventorySlot = pendingStore.targetInventorySlot();
            expectedItem = pendingStore.shulkerItem();
            expectedName = pendingStore.shulkerName();
        } else if (pendingExtraction != null) {
            inventorySlot = pendingExtraction.inventorySlot();
            expectedItem = pendingExtraction.shulkerItem();
            expectedName = pendingExtraction.shulkerName();
        } else if (activeReturn != null) {
            inventorySlot = activeReturnShulkerSlot;
            expectedItem = activeReturn.shulkerItem;
            expectedName = activeReturn.shulkerName;
        } else {
            return null;
        }
        if (inventorySlot < 0 || inventorySlot >= 36) return null;

        ItemStack shulker = client.player.getInventory().getItem(inventorySlot).copy();
        if (!ShulkerContentsHelper.isShulker(shulker)) {
            ItemStack carried = menu.getCarried();
            shulker = ShulkerContentsHelper.isShulker(carried)
                    ? carried.copy() : new ItemStack(expectedItem);
            if (expectedName != null) shulker.set(DataComponents.CUSTOM_NAME, expectedName);
        }
        List<ItemStack> contents = new ArrayList<>(ShulkerContentsHelper.SHULKER_SIZE);
        for (int slot = 0; slot < ShulkerContentsHelper.SHULKER_SIZE; slot++) {
            contents.add(menu.getSlot(slot).getItem().copy());
        }
        return new ActiveShulkerContents(
                inventorySlot, shulker, List.copyOf(contents));
    }

    public record ActiveShulkerContents(
            int inventorySlot, ItemStack shulkerStack, List<ItemStack> contents) {}

    private static boolean canUseShulkerBackend(Minecraft client, long operationId) {
        Configs.ShulkerOpenBackend configured = selectedBackend();
        Configs.ShulkerOpenBackend backend = activeBackend();
        boolean available = canUseConfiguredBackend();
        diagnostic(operationId, "shulker-backend configured=%s active=%s available=%s",
                configured.getStringValue(), backend.getStringValue(), available);
        if (available) return true;
        show(client, "message.better-shulker-hud.quickshulker_required");
        return false;
    }

    private static void tick(Minecraft client) {
        if (programmaticInventorySettleTicks > 0) return;
        if (pendingStore != null) {
            tickStore(client);
            return;
        } else if (pendingExtraction != null) {
            tickExtraction(client);
            return;
        } else if (pendingCursorPickup != null) {
            tickCursorPickup(client);
            return;
        } else if (pendingOffhandTransfer != null) {
            tickOffhandTransfer(client);
            return;
        } else if (pendingBucketTransfer != null) {
            tickBucketTransfer(client);
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
        } else if (!queuedExtractions.isEmpty()) {
            tickQueuedExtraction(client);
            return;
        }
        if (!tickAxAutomaticRestockGuard(client)) tickAutoRestock(client);
    }

    private static boolean tickAxAutomaticRestockGuard(Minecraft client) {
        if (activeBackend() != Configs.ShulkerOpenBackend.AX_SHULKERS) {
            axAutomaticRestockSettleTicks = -1;
            blockedAxAutomaticRestockFingerprint = Long.MIN_VALUE;
            return false;
        }
        if (client.player == null) return false;
        if (axAutomaticRestockSettleTicks >= 0) {
            if (client.player.containerMenu != client.player.inventoryMenu) return true;
            if (axAutomaticRestockSettleTicks-- > 0) return true;
            blockedAxAutomaticRestockFingerprint =
                    automaticRestockFingerprint(client.player.getInventory());
            axAutomaticRestockSettleTicks = -1;
            diagnostic(0, "ax-automatic-restock-paused fingerprint=%d",
                    blockedAxAutomaticRestockFingerprint);
            return true;
        }
        if (blockedAxAutomaticRestockFingerprint == Long.MIN_VALUE) return false;
        long current = automaticRestockFingerprint(client.player.getInventory());
        if (current == blockedAxAutomaticRestockFingerprint) return true;
        blockedAxAutomaticRestockFingerprint = Long.MIN_VALUE;
        diagnostic(0, "ax-automatic-restock-resumed inventory-changed");
        return false;
    }

    private static void pauseAxAutomaticRestock() {
        if (activeBackend() != Configs.ShulkerOpenBackend.AX_SHULKERS) return;
        axAutomaticRestockSettleTicks = AX_AUTOMATIC_RESTOCK_SETTLE_TICKS;
        blockedAxAutomaticRestockFingerprint = Long.MIN_VALUE;
    }

    private static void tickQueuedExtraction(Minecraft client) {
        if (client.player == null
                || !(client.screen instanceof AbstractContainerScreen<?> screen)
                || !screen.getMenu().getCarried().isEmpty()) {
            queuedExtractions.clear();
            queuedExtractionWaitTicks = 0;
            queuedExtractionStartDelayTicks = 0;
            programmaticExtractionBatch = false;
            return;
        }
        if (screen.getMenu() != client.player.containerMenu) {
            if (client.player.containerMenu instanceof ShulkerBoxMenu) {
                closeContainerAndSetScreen(
                        client, new InventoryScreen(client.player), true);
                queuedExtractionStartDelayTicks = Math.max(
                        queuedExtractionStartDelayTicks,
                        Math.max(2, programmaticExtractionDelayTicks));
                queuedExtractionWaitTicks = 0;
            } else if (client.player.containerMenu instanceof InventoryMenu) {
                client.setScreen(new InventoryScreen(client.player));
                queuedExtractionStartDelayTicks = Math.max(
                        queuedExtractionStartDelayTicks,
                        Math.max(2, programmaticExtractionDelayTicks));
                queuedExtractionWaitTicks = 0;
            } else if (++queuedExtractionWaitTicks > OPEN_TIMEOUT_TICKS) {
                queuedExtractions.clear();
                queuedExtractionWaitTicks = 0;
                queuedExtractionStartDelayTicks = 0;
                programmaticExtractionBatch = false;
            }
            return;
        }
        if (queuedExtractionStartDelayTicks > 0) {
            queuedExtractionStartDelayTicks--;
            return;
        }
        QueuedExtraction queued = queuedExtractions.peekFirst();
        if (queued == null) return;
        if (findValidatedSource(screen, client.player.getInventory(), queued.item()) == null) {
            if (++queuedExtractionWaitTicks > OPEN_TIMEOUT_TICKS) {
                show(client, "message.better-shulker-hud.source_missing");
                queuedExtractions.removeFirst();
                queuedExtractionWaitTicks = 0;
            } else if (queuedExtractions.size() > 1) {
                queuedExtractions.removeFirst();
                queuedExtractions.addLast(queued);
            }
            return;
        }
        queuedExtractions.removeFirst();
        queuedExtractionWaitTicks = 0;
        if (!canUseShulkerBackend(client, queued.operationId())) return;
        startInventoryExtraction(
                client, screen, queued.item(), queued.takeOne(), true, queued.operationId());
    }

    private static void tickDeferredExtraction(Minecraft client) {
        if (client.player == null) {
            clearDeferredExtraction();
            return;
        }
        if (deferredExtractionDelay-- > 0) return;
        if (!canUseConfiguredBackend()) {
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
                candidate.target().shulkerName(), StorePurpose.EXTRACTION_PREPARATION, -1);
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
                candidate.target().shulkerName(), StorePurpose.EXTRACTION_PREPARATION, -1);
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
        int selected = inventory.selected;
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

            StoreTarget target = findClearanceStoreTarget(
                    inventory, stack, stack.getCount(), preferredTargetInventorySlot);
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
        if (stack.has(DataComponents.TOOL) || stack.isDamageableItem()) return 1;
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
                matching.target().shulkerName(), StorePurpose.MATCHING_SORT,
                findStoredMainHandMemorySlot(
                        client.player.getInventory(), matching.prototype(),
                        matching.sourceInventorySlot()));
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
                    pendingStore.purpose(), targetMenuSlot, canUseConfiguredBackend(),
                    storeOpenSyncVersion, itemId(pendingStore.prototype()), pendingStore.amount(),
                    pendingStore.sourceInventorySlot(), pendingStore.targetInventorySlot(),
                    pendingStore.shulkerSlot());
        }
        if (!openShulker(Minecraft.getInstance(), targetMenuSlot, operationId)) {
            failStore(Minecraft.getInstance(), "message.better-shulker-hud.open_failed");
            return;
        }
        diagnostic(operationId, "packet-sent open-shulker");
    }

    private static void updateRememberedHandItems(Minecraft client) {
        if (client.player == null) return;
        Inventory inventory = client.player.getInventory();
        int selectedSlot = inventory.selected;
        ItemStack mainHand = inventory.getItem(selectedSlot);
        if (isSingleItemRestockable(mainHand)) {
            rememberedMainSingleItem = mainHand.copyWithCount(1);
            rememberedMainSingleSlot = selectedSlot;
            rememberedMainSingleLooseCount = countMatchingLoosePlayerItems(
                    inventory, rememberedMainSingleItem);
        } else if (mainHand.isEmpty()
                && rememberedMainSingleSlot == selectedSlot
                && countMatchingLoosePlayerItems(inventory, rememberedMainSingleItem)
                        >= rememberedMainSingleLooseCount) {
            rememberedMainSingleItem = ItemStack.EMPTY;
            rememberedMainSingleSlot = -1;
            rememberedMainSingleLooseCount = 0;
        } else if (!mainHand.isEmpty() || rememberedMainSingleSlot != selectedSlot) {
            rememberedMainSingleItem = ItemStack.EMPTY;
            rememberedMainSingleSlot = -1;
            rememberedMainSingleLooseCount = 0;
        }

        if (mainHand.getItem() == Items.WATER_BUCKET) {
            rememberedMainWaterBucket = mainHand.copyWithCount(1);
            rememberedMainWaterBucketSlot = selectedSlot;
            rememberedMainWaterBucketLooseCount = countMatchingLoosePlayerItems(
                    inventory, rememberedMainWaterBucket);
        } else if (mainHand.isEmpty()
                && rememberedMainWaterBucketSlot == selectedSlot
                && countMatchingLoosePlayerItems(inventory, rememberedMainWaterBucket)
                        >= rememberedMainWaterBucketLooseCount) {
            rememberedMainWaterBucket = ItemStack.EMPTY;
            rememberedMainWaterBucketSlot = -1;
            rememberedMainWaterBucketLooseCount = 0;
        } else if (rememberedMainWaterBucketSlot != selectedSlot
                || (!mainHand.isEmpty() && mainHand.getItem() != Items.BUCKET)) {
            rememberedMainWaterBucket = ItemStack.EMPTY;
            rememberedMainWaterBucketSlot = -1;
            rememberedMainWaterBucketLooseCount = 0;
        }

        ItemStack offhand = inventory.getItem(Inventory.SLOT_OFFHAND);
        if (isSingleItemRestockable(offhand)) {
            rememberedOffhandSingleItem = offhand.copyWithCount(1);
            rememberedOffhandSingleLooseCount = countMatchingLoosePlayerItems(
                    inventory, rememberedOffhandSingleItem);
        } else if (offhand.isEmpty()
                && countMatchingLoosePlayerItems(inventory, rememberedOffhandSingleItem)
                        >= rememberedOffhandSingleLooseCount) {
            rememberedOffhandSingleItem = ItemStack.EMPTY;
            rememberedOffhandSingleLooseCount = 0;
        } else if (!offhand.isEmpty()) {
            rememberedOffhandSingleItem = ItemStack.EMPTY;
            rememberedOffhandSingleLooseCount = 0;
        }
    }

    private static boolean isSingleItemRestockable(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.is(Items.TOTEM_OF_UNDYING);
    }

    private static int findStoredMainHandMemorySlot(
            Inventory inventory, ItemStack prototype, int sourceInventorySlot) {
        if (inventory == null || prototype == null || prototype.isEmpty()) return -1;
        int selectedSlot = inventory.selected;
        if (sourceInventorySlot >= 0 && sourceInventorySlot != selectedSlot) return -1;
        if (rememberedMainSingleSlot == selectedSlot
                && !rememberedMainSingleItem.isEmpty()
                && ItemStack.isSameItemSameComponents(rememberedMainSingleItem, prototype)) {
            return selectedSlot;
        }
        if (rememberedMainWaterBucketSlot == selectedSlot
                && !rememberedMainWaterBucket.isEmpty()
                && ItemStack.isSameItemSameComponents(rememberedMainWaterBucket, prototype)) {
            return selectedSlot;
        }
        return -1;
    }

    private static void forgetStoredMainHandMemory(PendingStore completedStore, int completed) {
        if (completedStore == null || completed <= 0
                || completedStore.mainHandMemorySlot() < 0
                || completedStore.purpose() == StorePurpose.EXTRACTION_PREPARATION) return;
        int memorySlot = completedStore.mainHandMemorySlot();
        ItemStack prototype = completedStore.prototype();
        if (rememberedMainSingleSlot == memorySlot
                && !rememberedMainSingleItem.isEmpty()
                && ItemStack.isSameItemSameComponents(rememberedMainSingleItem, prototype)) {
            rememberedMainSingleItem = ItemStack.EMPTY;
            rememberedMainSingleSlot = -1;
            rememberedMainSingleLooseCount = 0;
        }
        if (rememberedMainWaterBucketSlot == memorySlot
                && !rememberedMainWaterBucket.isEmpty()
                && ItemStack.isSameItemSameComponents(rememberedMainWaterBucket, prototype)) {
            rememberedMainWaterBucket = ItemStack.EMPTY;
            rememberedMainWaterBucketSlot = -1;
            rememberedMainWaterBucketLooseCount = 0;
        }
    }

    private static boolean isLowStack(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && !ShulkerContentsHelper.isShulker(stack)
                && stack.getItem() != Items.BUCKET
                && stack.getMaxStackSize() > 1
                && stack.getCount()
                <= Configs.General.AUTO_RESTOCK_THRESHOLD.getIntegerValue()
                && stack.getCount() < restockTargetCount(stack);
    }

    private static int restockTargetCount(ItemStack stack) {
        return stack.getMaxStackSize();
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
        if (!canUseConfiguredBackend()) return false;

        Inventory inventory = client.player.getInventory();
        int selectedSlot = inventory.selected;
        ItemStack mainHand = inventory.getItem(selectedSlot);
        if (rememberedMainWaterBucketSlot == selectedSlot
                && !rememberedMainWaterBucket.isEmpty()) {
            if (mainHand.getItem() == Items.BUCKET && mainHand.getCount() == 1
                    && startWaterBucketReplacement(
                            client, rememberedMainWaterBucket, selectedSlot)) {
                return true;
            }
            if (mainHand.isEmpty()
                    && startMainHandRestock(
                            client, rememberedMainWaterBucket, selectedSlot, 0)) {
                return true;
            }
        }
        if (isLowStack(mainHand)
                && startMainHandRestock(client, mainHand, selectedSlot, mainHand.getCount())) {
            return true;
        }
        if (mainHand.isEmpty()
                && Configs.Features.SINGLE_ITEM_AUTO_RESTOCK.getBooleanValue()
                && rememberedMainSingleSlot == selectedSlot
                && !rememberedMainSingleItem.isEmpty()
                && startMainHandRestock(
                        client, rememberedMainSingleItem, selectedSlot, 0)) {
            return true;
        }

        if (!Configs.Features.OFFHAND_AUTO_RESTOCK.getBooleanValue()) return false;
        ItemStack offhand = inventory.getItem(Inventory.SLOT_OFFHAND);
        ItemStack required = isLowStack(offhand)
                ? offhand.copyWithCount(1)
                : offhand.isEmpty()
                        && Configs.Features.SINGLE_ITEM_AUTO_RESTOCK.getBooleanValue()
                        ? rememberedOffhandSingleItem : ItemStack.EMPTY;
        if (required.isEmpty()) return false;
        int requestedAmount = requestedRestockAmount(
                required, offhand.isEmpty() ? 0 : offhand.getCount());
        if (requestedAmount <= 0) return false;
        ResolvedSource source = findRestockSource(
                client.player.containerMenu, inventory, required);
        if (source == null) return false;
        long operationId = nextDiagnosticOperationId();
        if (!startOffhandExtraction(
                client, client.player.containerMenu, source,
                requestedAmount, true, operationId)) return false;
        return true;
    }

    private static boolean startMainHandRestock(
            Minecraft client, ItemStack required,
            int targetInventorySlot, int targetBaselineCount) {
        if (client.player == null || required == null || required.isEmpty()) return false;
        Inventory inventory = client.player.getInventory();
        ResolvedSource source = findRestockSource(
                client.player.containerMenu, inventory, required.copyWithCount(1));
        if (source == null) return false;
        int requestedAmount = requestedRestockAmount(required, targetBaselineCount);
        if (requestedAmount <= 0) return false;

        long operationId = nextDiagnosticOperationId();
        pendingExtraction = new PendingExtraction(
                source.inventorySlot(), source.shulkerSlot(), source.expectedStack(), false,
                source.shulkerItem(), source.shulkerName(), false, true, false,
                targetInventorySlot, requestedAmount, targetBaselineCount);
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
                source.quickShulkerSlot(), targetInventorySlot, targetBaselineCount,
                requestedAmount, canUseConfiguredBackend(), extractionOpenSyncVersion);
        if (!openShulker(client, source.quickShulkerSlot(), operationId)) {
            failExtraction(client, "message.better-shulker-hud.open_failed");
            return false;
        }
        diagnostic(operationId, "packet-sent open-shulker");
        return true;
    }

    private static int requestedRestockAmount(ItemStack stack, int baselineCount) {
        int remaining = Math.max(0, restockTargetCount(stack) - baselineCount);
        return Math.min(Configs.General.AUTO_RESTOCK_AMOUNT.getIntegerValue(), remaining);
    }

    private static boolean startWaterBucketReplacement(
            Minecraft client, ItemStack required, int selectedSlot) {
        if (client.player == null || required.isEmpty()
                || required.getItem() != Items.WATER_BUCKET) return false;
        Inventory inventory = client.player.getInventory();
        AbstractContainerMenu menu = client.player.containerMenu;
        ResolvedSource source = findRestockSource(menu, inventory, required);
        if (source == null) return false;
        CursorStaging staging = findCursorStagingDestination(
                menu, inventory, required, source.inventorySlot());
        if (staging == null) return false;
        BucketDestination emptyBucketDestination = findEmptyBucketDestination(
                menu, inventory, selectedSlot, staging.inventorySlot(), source.inventorySlot());
        if (emptyBucketDestination == null) return false;

        long operationId = nextDiagnosticOperationId();
        pendingExtraction = new PendingExtraction(
                source.inventorySlot(), source.shulkerSlot(), source.expectedStack(), false,
                source.shulkerItem(), source.shulkerName(), false, true, false,
                staging.inventorySlot(), 1, staging.baselineCount());
        extractionBucketReplacement = true;
        extractionBucketSelectedSlot = selectedSlot;
        extractionEmptyBucketDestination = emptyBucketDestination.inventorySlot();
        extractionEmptyBucketDestinationBaseline = emptyBucketDestination.baselineCount();
        extractionOperationId = operationId;
        extractionWaitTicks = 0;
        extractionMenuId = -1;
        extractionCloseDelay = -1;
        extractionMovedItemCount = 0;
        extractionExpectedSourceCount = -1;
        extractionOpenSyncVersion = containerSyncVersion;
        diagnostic(operationId,
                "request type=water-bucket-replacement sourceInventorySlot=%d shulkerSlot=%d stagingInventorySlot=%d selectedSlot=%d emptyBucketDestination=%d emptyBucketBaseline=%d",
                source.inventorySlot(), source.shulkerSlot(), staging.inventorySlot(),
                selectedSlot, emptyBucketDestination.inventorySlot(),
                emptyBucketDestination.baselineCount());
        if (!openShulker(client, source.quickShulkerSlot(), operationId)) {
            failExtraction(client, "message.better-shulker-hud.open_failed");
            return false;
        }
        return true;
    }

    private static BucketDestination findEmptyBucketDestination(
            AbstractContainerMenu menu, Inventory inventory, int selectedSlot,
            int stagingSlot, int sourceShulkerSlot) {
        BucketDestination empty = null;
        for (Slot slot : menu.slots) {
            int inventorySlot = slot.getContainerSlot();
            if (slot.container != inventory || inventorySlot < 0 || inventorySlot >= 36
                    || inventorySlot == selectedSlot || inventorySlot == stagingSlot
                    || inventorySlot == sourceShulkerSlot
                    || !slot.mayPlace(new ItemStack(Items.BUCKET))) continue;
            ItemStack current = slot.getItem();
            if (current.getItem() == Items.BUCKET
                    && current.getCount() < slot.getMaxStackSize(current)) {
                return new BucketDestination(inventorySlot, current.getCount());
            }
            if (current.isEmpty() && empty == null) {
                empty = new BucketDestination(inventorySlot, 0);
            }
        }
        return empty;
    }

    private static void tickStore(Minecraft client) {
        if (client.player == null || client.gameMode == null) {
            diagnostic(storeOperationId, "store-aborted reason=player-or-game-mode-missing");
            clearStore();
            return;
        }
        if (storeOperationDelayTicks > 0) {
            storeOperationDelayTicks--;
            return;
        }

        if (storeCloseDelay >= 0) {
            boolean synced = hasContainerSyncAfter(storeMenuId, storeMoveSyncVersion);
            boolean confirmed = client.player.containerMenu instanceof ShulkerBoxMenu menu
                    && menu.containerId == storeMenuId && isConfirmedStoreResult(menu);
            if (!synced) {
                storeCloseDelay++;
                if (confirmed && storeCloseDelay >= LOCAL_CONFIRMATION_GRACE_TICKS) {
                    diagnostic(storeOperationId,
                            "store-confirmed-from-local-state menu=%d graceTicks=%d",
                            storeMenuId, storeCloseDelay);
                    closeAfterStore(client);
                    return;
                }
                if (++storeWaitTicks > OPEN_TIMEOUT_TICKS) {
                    diagnostic(storeOperationId,
                            "timeout stage=store-move-sync menu=%d waitedTicks=%d moveSyncBaseline=%d currentSyncVersion=%d",
                            storeMenuId, storeWaitTicks, storeMoveSyncVersion, containerSyncVersion);
                    failStore(client, "message.better-shulker-hud.store_failed");
                }
                return;
            }
            if (!confirmed) {
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
            boolean synced = hasContainerSyncAfter(menu.containerId, storeMoveSyncVersion);
            boolean confirmed = isConfirmedStoreTarget(menu);
            if (!synced) {
                storeWaitTicks++;
                if (confirmed && storeWaitTicks >= LOCAL_CONFIRMATION_GRACE_TICKS) {
                    diagnostic(storeOperationId,
                            "matching-transfer-confirmed-from-local-state menu=%d graceTicks=%d",
                            menu.containerId, storeWaitTicks);
                } else if (storeWaitTicks > OPEN_TIMEOUT_TICKS) {
                    diagnostic(storeOperationId,
                            "timeout stage=matching-transfer-sync menu=%d waitedTicks=%d moveSyncBaseline=%d currentSyncVersion=%d",
                            menu.containerId, storeWaitTicks, storeMoveSyncVersion,
                            containerSyncVersion);
                    failStore(client, "message.better-shulker-hud.store_failed");
                    return;
                } else {
                    return;
                }
            } else if (!confirmed) {
                diagnostic(storeOperationId,
                        "matching-transfer-confirmation-failed menu=%d expectedTargetCount=%d",
                        menu.containerId, storeExpectedTargetCount);
                failStore(client, "message.better-shulker-hud.store_failed");
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

        int sourceMenuSlot = findExactPlayerItemSlot(
                menu, client.player.getInventory(), pendingStore.sourceInventorySlot(),
                pendingStore.prototype(), pendingStore.targetInventorySlot());
        if (pendingStore.purpose() == StorePurpose.EXTRACTION_PREPARATION) {
            int capacity = shulkerCapacity(menu, pendingStore.prototype());
            if (sourceMenuSlot < 0 || capacity < pendingStore.amount()) {
                failStore(client, "message.better-shulker-hud.store_target_changed");
                return;
            }

            int before = menu.getSlot(sourceMenuSlot).getItem().getCount();
            diagnostic(storeOperationId,
                    "clearance-quick-move menu=%d sourceMenuSlot=%d sourceBefore=%d requested=%d totalCapacity=%d",
                    menu.containerId, sourceMenuSlot, before, pendingStore.amount(), capacity);
            client.gameMode.handleInventoryMouseClick(
                    menu.containerId, sourceMenuSlot, 0, ClickType.QUICK_MOVE, client.player);
            int moved = before - menu.getSlot(sourceMenuSlot).getItem().getCount();
            if (!menu.getCarried().isEmpty() || moved != pendingStore.amount()) {
                failStore(client, "message.better-shulker-hud.store_failed");
                return;
            }

            storedItemCount = moved;
            storeExpectedTargetCount = -1;
            storeMoveSyncVersion = containerSyncVersion;
            storeCloseDelay = 0;
            storeWaitTicks = 0;
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
        client.gameMode.handleInventoryMouseClick(
                menu.containerId, sourceMenuSlot, 0, ClickType.PICKUP, client.player);
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
            client.gameMode.handleInventoryMouseClick(
                    menu.containerId, storeTransfer.targetMenuSlot(), 0,
                    ClickType.PICKUP, client.player);
            if (!menu.getCarried().isEmpty()) {
                client.gameMode.handleInventoryMouseClick(
                        menu.containerId, storeTransfer.sourceMenuSlot(), 0,
                        ClickType.PICKUP, client.player);
            }
            storeTransfer = storeTransfer.withRemaining(-1);
        }
        int clickSlot = storeTransfer.placeMovedItemsOneByOne()
                ? storeTransfer.targetMenuSlot() : storeTransfer.sourceMenuSlot();
        int clicks = Math.min(MAX_STORE_CLICKS_PER_TICK,
                Math.max(0, storeTransfer.repeatedClicksRemaining()));
        for (int i = 0; i < clicks; i++) {
            client.gameMode.handleInventoryMouseClick(
                    menu.containerId, clickSlot, 1, ClickType.PICKUP, client.player);
        }
        storeTransfer = storeTransfer.withRemaining(
                storeTransfer.repeatedClicksRemaining() - clicks);
        if (storeTransfer.repeatedClicksRemaining() > 0) return;

        int finalSlot = storeTransfer.placeMovedItemsOneByOne()
                ? storeTransfer.sourceMenuSlot() : storeTransfer.targetMenuSlot();
        if (!menu.getCarried().isEmpty()) {
            client.gameMode.handleInventoryMouseClick(
                    menu.containerId, finalSlot, 0, ClickType.PICKUP, client.player);
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
        int sourceInventorySlot = menu.getSlot(sourceMenuSlot).getContainerSlot();
        int mainHandMemorySlot = pendingStore.mainHandMemorySlot() >= 0
                ? pendingStore.mainHandMemorySlot()
                : findStoredMainHandMemorySlot(
                        inventory, pendingStore.prototype(), sourceInventorySlot);
        pendingStore = new PendingStore(
                sourceInventorySlot, amount,
                pendingStore.targetInventorySlot(), targetSlot,
                pendingStore.prototype(), pendingStore.shulkerItem(),
                pendingStore.shulkerName(), pendingStore.purpose(), mainHandMemorySlot);
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

    private static boolean isConfirmedStoreResult(ShulkerBoxMenu menu) {
        if (pendingStore != null
                && pendingStore.purpose() == StorePurpose.EXTRACTION_PREPARATION) {
            if (Minecraft.getInstance().player == null
                    || storedItemCount != pendingStore.amount()) return false;
            int sourceMenuSlot = findPlayerInventoryMenuSlot(
                    menu, Minecraft.getInstance().player.getInventory(),
                    pendingStore.sourceInventorySlot());
            return sourceMenuSlot >= 0 && menu.getSlot(sourceMenuSlot).getItem().isEmpty();
        }
        return isConfirmedStoreTarget(menu);
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
        if (extractionOperationDelayTicks > 0) {
            extractionOperationDelayTicks--;
            return;
        }

        if (extractionCloseDelay >= 0) {
            boolean synced = hasContainerSyncAfter(
                    extractionMenuId, extractionMoveSyncVersion);
            boolean confirmed = client.player.containerMenu instanceof ShulkerBoxMenu menu
                    && menu.containerId == extractionMenuId
                    && isConfirmedExtractionSource(menu);
            if (!synced) {
                extractionCloseDelay++;
                if (confirmed
                        && extractionCloseDelay >= LOCAL_CONFIRMATION_GRACE_TICKS) {
                    diagnostic(extractionOperationId,
                            "extraction-confirmed-from-local-state menu=%d graceTicks=%d",
                            extractionMenuId, extractionCloseDelay);
                    closeAfterExtraction(client);
                    return;
                }
                if (++extractionWaitTicks > OPEN_TIMEOUT_TICKS) {
                    diagnostic(extractionOperationId,
                            "timeout stage=extraction-move-sync menu=%d waitedTicks=%d moveSyncBaseline=%d currentSyncVersion=%d",
                            extractionMenuId, extractionWaitTicks,
                            extractionMoveSyncVersion, containerSyncVersion);
                    failExtraction(client, "message.better-shulker-hud.open_failed");
                }
                return;
            }
            if (!confirmed) {
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
            client.gameMode.handleInventoryMouseClick(
                    menu.containerId, pendingExtraction.shulkerSlot(), 0,
                    ClickType.QUICK_MOVE, client.player);
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
        if (moved > 0 && !pendingExtraction.handRestock()
                && !(extractionToOffhand && extractionOffhandBackground)) {
            recordExtraction(menu, pendingExtraction, moved);
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

        client.gameMode.handleInventoryMouseClick(
                menu.containerId, menuSlot, 0, ClickType.PICKUP, client.player);
        for (int i = 0; i < pendingCursorPickup.baselineCount(); i++) {
            client.gameMode.handleInventoryMouseClick(
                    menu.containerId, menuSlot, 1, ClickType.PICKUP, client.player);
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
            client.gameMode.handleInventoryMouseClick(
                    menu.containerId, menuSlot, 0, ClickType.PICKUP, client.player);
        }
        show(client, "message.better-shulker-hud.cursor_pickup_failed");
        clearCursorPickup();
    }

    private static void tickOffhandTransfer(Minecraft client) {
        if (client.player == null || client.gameMode == null) {
            clearOffhandTransfer();
            return;
        }
        if (client.player.containerMenu != client.player.inventoryMenu) {
            if (++offhandTransferWaitTicks > 20) failOffhandTransfer(client);
            return;
        }

        AbstractContainerMenu menu = client.player.inventoryMenu;
        if (!menu.getCarried().isEmpty()) {
            failOffhandTransfer(client);
            return;
        }
        Inventory inventory = client.player.getInventory();
        int stagingMenuSlot = findPlayerInventoryMenuSlot(
                menu, inventory, pendingOffhandTransfer.inventorySlot());
        int offhandMenuSlot = findPlayerInventoryMenuSlot(
                menu, inventory, Inventory.SLOT_OFFHAND);
        if (stagingMenuSlot < 0 || offhandMenuSlot < 0) {
            failOffhandTransfer(client);
            return;
        }

        ItemStack staged = menu.getSlot(stagingMenuSlot).getItem();
        int expectedStagedCount = pendingOffhandTransfer.stagingBaselineCount()
                + pendingOffhandTransfer.extractedAmount();
        ItemStack offhand = menu.getSlot(offhandMenuSlot).getItem();
        boolean swap = isOffhandSwap(pendingOffhandTransfer);
        int expectedOffhandCount = swap ? pendingOffhandTransfer.extractedAmount()
                : pendingOffhandTransfer.offhandBaselineCount()
                + pendingOffhandTransfer.extractedAmount();

        if (offhandTransferAwaitingSync) {
            boolean confirmed = menu.getCarried().isEmpty()
                    && (swap
                            ? ItemStack.matches(staged, pendingOffhandTransfer.originalOffhand())
                            : isExpectedCount(staged, pendingOffhandTransfer.prototype(),
                                    pendingOffhandTransfer.stagingBaselineCount()))
                    && isExpectedCount(offhand, pendingOffhandTransfer.prototype(),
                            expectedOffhandCount);
            boolean synced = hasContainerSyncAfter(
                    menu.containerId, offhandTransferMoveSyncVersion);
            if (confirmed && (synced
                    || offhandTransferWaitTicks >= LOCAL_CONFIRMATION_GRACE_TICKS)) {
                diagnostic(offhandTransferOperationId,
                        "operation-complete target=offhand confirmedAmount=%d offhandCount=%d swap=%s serverSync=%s waitTicks=%d",
                        pendingOffhandTransfer.extractedAmount(), offhand.getCount(),
                        swap, synced, offhandTransferWaitTicks);
                if (pendingOffhandTransfer.background()) pauseAxAutomaticRestock();
                clearOffhandTransfer();
                return;
            }
            if (++offhandTransferWaitTicks > OPEN_TIMEOUT_TICKS) {
                diagnostic(offhandTransferOperationId,
                        "offhand-transfer-failed reason=confirmation-timeout stagedCount=%d offhandCount=%d cursorCount=%d syncBaseline=%d currentSyncVersion=%d",
                        staged.getCount(), offhand.getCount(), menu.getCarried().getCount(),
                        offhandTransferMoveSyncVersion, containerSyncVersion);
                failOffhandTransfer(client);
            }
            return;
        }

        boolean validOffhandBaseline = swap
                ? ItemStack.matches(offhand, pendingOffhandTransfer.originalOffhand())
                : pendingOffhandTransfer.offhandBaselineCount() == 0
                        ? offhand.isEmpty()
                        : ItemStack.isSameItemSameComponents(
                                offhand, pendingOffhandTransfer.prototype())
                        && offhand.getCount() == pendingOffhandTransfer.offhandBaselineCount();
        if (!ItemStack.isSameItemSameComponents(
                staged, pendingOffhandTransfer.prototype())
                || staged.getCount() != expectedStagedCount || !validOffhandBaseline) {
            if (++offhandTransferWaitTicks > 20) failOffhandTransfer(client);
            return;
        }

        offhandTransferMoveSyncVersion = containerSyncVersion;
        client.gameMode.handleInventoryMouseClick(
                menu.containerId, stagingMenuSlot, 0, ClickType.PICKUP, client.player);
        for (int i = 0; i < pendingOffhandTransfer.stagingBaselineCount(); i++) {
            client.gameMode.handleInventoryMouseClick(
                    menu.containerId, stagingMenuSlot, 1,
                    ClickType.PICKUP, client.player);
        }
        ItemStack carried = menu.getCarried();
        if (!ItemStack.isSameItemSameComponents(
                carried, pendingOffhandTransfer.prototype())
                || carried.getCount() != pendingOffhandTransfer.extractedAmount()) {
            failOffhandTransfer(client);
            return;
        }

        client.gameMode.handleInventoryMouseClick(
                menu.containerId, offhandMenuSlot, 0, ClickType.PICKUP, client.player);
        if (swap) {
            ItemStack replaced = menu.getCarried();
            if (!ItemStack.matches(replaced, pendingOffhandTransfer.originalOffhand())) {
                failOffhandTransfer(client);
                return;
            }
            client.gameMode.handleInventoryMouseClick(
                    menu.containerId, stagingMenuSlot, 0,
                    ClickType.PICKUP, client.player);
        }
        ItemStack result = menu.getSlot(offhandMenuSlot).getItem();
        if (!menu.getCarried().isEmpty()
                || !ItemStack.isSameItemSameComponents(
                        result, pendingOffhandTransfer.prototype())
                || result.getCount() != expectedOffhandCount
                || (swap && !ItemStack.matches(
                        menu.getSlot(stagingMenuSlot).getItem(),
                        pendingOffhandTransfer.originalOffhand()))) {
            failOffhandTransfer(client);
            return;
        }
        offhandTransferAwaitingSync = true;
        offhandTransferWaitTicks = 0;
    }

    private static boolean isOffhandSwap(PendingOffhandTransfer transfer) {
        return transfer != null && !transfer.originalOffhand().isEmpty()
                && !ItemStack.isSameItemSameComponents(
                        transfer.originalOffhand(), transfer.prototype());
    }

    private static boolean isExpectedCount(
            ItemStack stack, ItemStack prototype, int expectedCount) {
        return expectedCount == 0 ? stack.isEmpty()
                : ItemStack.isSameItemSameComponents(stack, prototype)
                && stack.getCount() == expectedCount;
    }

    private static void failOffhandTransfer(Minecraft client) {
        boolean background = pendingOffhandTransfer != null
                && pendingOffhandTransfer.background();
        int preferredSlot = pendingOffhandTransfer == null
                ? -1 : pendingOffhandTransfer.inventorySlot();
        returnCursorToInventory(
                client, preferredSlot, offhandTransferOperationId, "offhand-transfer");
        if (pendingOffhandTransfer != null && !pendingOffhandTransfer.background()) {
            show(client, "message.better-shulker-hud.offhand_transfer_failed");
        }
        if (background) pauseAxAutomaticRestock();
        clearOffhandTransfer();
    }

    private static void returnCursorToInventory(
            Minecraft client, int preferredInventorySlot,
            long operationId, String operationName) {
        if (client.player == null || client.gameMode == null
                || client.player.containerMenu != client.player.inventoryMenu) return;
        AbstractContainerMenu menu = client.player.inventoryMenu;
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) return;

        Inventory inventory = client.player.getInventory();
        int preferred = findPlayerInventoryMenuSlot(
                menu, inventory, preferredInventorySlot);
        int destination = canAcceptEntireStack(menu, preferred, carried) ? preferred : -1;
        if (destination < 0) {
            for (int menuSlot = 0; menuSlot < menu.slots.size(); menuSlot++) {
                Slot slot = menu.slots.get(menuSlot);
                if (slot.container == inventory && slot.getContainerSlot() >= 0
                        && slot.getContainerSlot() < 36
                        && canAcceptEntireStack(menu, menuSlot, carried)) {
                    destination = menuSlot;
                    break;
                }
            }
        }
        if (destination >= 0) {
            client.gameMode.handleInventoryMouseClick(
                    menu.containerId, destination, 0, ClickType.PICKUP, client.player);
        }
        diagnostic(operationId,
                "%s-rollback destinationMenuSlot=%d cursorRemaining=%d",
                operationName, destination, menu.getCarried().getCount());
    }

    private static boolean canAcceptEntireStack(
            AbstractContainerMenu menu, int menuSlot, ItemStack carried) {
        if (menuSlot < 0 || menuSlot >= menu.slots.size()) return false;
        Slot slot = menu.getSlot(menuSlot);
        ItemStack current = slot.getItem();
        return slot.mayPlace(carried)
                && (current.isEmpty() || ItemStack.isSameItemSameComponents(current, carried))
                && slot.getMaxStackSize(carried) - current.getCount() >= carried.getCount();
    }

    private static void tickBucketTransfer(Minecraft client) {
        if (client.player == null || client.gameMode == null) {
            clearBucketTransfer();
            return;
        }
        if (client.player.containerMenu != client.player.inventoryMenu) {
            if (++bucketTransferWaitTicks > OPEN_TIMEOUT_TICKS) {
                failBucketTransfer(client, "inventory-menu-unavailable");
            }
            return;
        }

        AbstractContainerMenu menu = client.player.inventoryMenu;
        Inventory inventory = client.player.getInventory();
        int stagingMenuSlot = findPlayerInventoryMenuSlot(
                menu, inventory, pendingBucketTransfer.stagingInventorySlot());
        int selectedMenuSlot = findPlayerInventoryMenuSlot(
                menu, inventory, pendingBucketTransfer.selectedInventorySlot());
        int emptyBucketMenuSlot = findPlayerInventoryMenuSlot(
                menu, inventory, pendingBucketTransfer.emptyBucketInventorySlot());
        if (stagingMenuSlot < 0 || selectedMenuSlot < 0 || emptyBucketMenuSlot < 0) {
            failBucketTransfer(client, "slot-resolution-failed");
            return;
        }

        ItemStack staged = menu.getSlot(stagingMenuSlot).getItem();
        ItemStack selected = menu.getSlot(selectedMenuSlot).getItem();
        ItemStack emptyBucketDestination = menu.getSlot(emptyBucketMenuSlot).getItem();
        int expectedWaterCount = pendingBucketTransfer.stagingBaselineCount()
                + pendingBucketTransfer.extractedAmount();
        int expectedEmptyBucketCount = pendingBucketTransfer.emptyBucketBaselineCount() + 1;

        if (bucketTransferAwaitingSync) {
            boolean confirmed = menu.getCarried().isEmpty()
                    && isExpectedCount(staged, pendingBucketTransfer.prototype(),
                            pendingBucketTransfer.stagingBaselineCount())
                    && isExpectedCount(selected, pendingBucketTransfer.prototype(),
                            pendingBucketTransfer.extractedAmount())
                    && isExpectedCount(emptyBucketDestination,
                            new ItemStack(Items.BUCKET), expectedEmptyBucketCount);
            boolean synced = hasContainerSyncAfter(
                    menu.containerId, bucketTransferMoveSyncVersion);
            if (confirmed && (synced
                    || bucketTransferWaitTicks >= LOCAL_CONFIRMATION_GRACE_TICKS)) {
                diagnostic(bucketTransferOperationId,
                        "operation-complete target=water-bucket-replacement selectedSlot=%d serverSync=%s waitTicks=%d",
                        pendingBucketTransfer.selectedInventorySlot(), synced,
                        bucketTransferWaitTicks);
                pauseAxAutomaticRestock();
                clearBucketTransfer();
                return;
            }
            if (++bucketTransferWaitTicks > OPEN_TIMEOUT_TICKS) {
                failBucketTransfer(client, "confirmation-timeout");
            }
            return;
        }

        boolean validInitialState = menu.getCarried().isEmpty()
                && isExpectedCount(staged, pendingBucketTransfer.prototype(), expectedWaterCount)
                && selected.getItem() == Items.BUCKET && selected.getCount() == 1
                && isExpectedCount(emptyBucketDestination,
                        new ItemStack(Items.BUCKET),
                        pendingBucketTransfer.emptyBucketBaselineCount());
        if (!validInitialState) {
            if (++bucketTransferWaitTicks > OPEN_TIMEOUT_TICKS) {
                failBucketTransfer(client, "initial-state-timeout");
            }
            return;
        }

        bucketTransferMoveSyncVersion = containerSyncVersion;
        client.gameMode.handleInventoryMouseClick(
                menu.containerId, selectedMenuSlot, 0, ClickType.PICKUP, client.player);
        client.gameMode.handleInventoryMouseClick(
                menu.containerId, emptyBucketMenuSlot, 0, ClickType.PICKUP, client.player);
        if (!menu.getCarried().isEmpty()
                || !menu.getSlot(selectedMenuSlot).getItem().isEmpty()
                || !isExpectedCount(menu.getSlot(emptyBucketMenuSlot).getItem(),
                        new ItemStack(Items.BUCKET), expectedEmptyBucketCount)) {
            failBucketTransfer(client, "empty-bucket-relocation-failed");
            return;
        }

        client.gameMode.handleInventoryMouseClick(
                menu.containerId, stagingMenuSlot, 0, ClickType.PICKUP, client.player);
        client.gameMode.handleInventoryMouseClick(
                menu.containerId, selectedMenuSlot, 0, ClickType.PICKUP, client.player);
        if (!menu.getCarried().isEmpty()
                || !isExpectedCount(menu.getSlot(stagingMenuSlot).getItem(),
                        pendingBucketTransfer.prototype(),
                        pendingBucketTransfer.stagingBaselineCount())
                || !isExpectedCount(menu.getSlot(selectedMenuSlot).getItem(),
                        pendingBucketTransfer.prototype(),
                        pendingBucketTransfer.extractedAmount())) {
            failBucketTransfer(client, "water-bucket-placement-failed");
            return;
        }

        bucketTransferAwaitingSync = true;
        bucketTransferWaitTicks = 0;
    }

    private static void failBucketTransfer(Minecraft client, String reason) {
        diagnostic(bucketTransferOperationId,
                "operation-failed kind=water-bucket-replacement reason=%s",
                reason);
        int preferredSlot = pendingBucketTransfer == null
                ? -1 : pendingBucketTransfer.stagingInventorySlot();
        returnCursorToInventory(
                client, preferredSlot, bucketTransferOperationId,
                "water-bucket-replacement");
        pauseAxAutomaticRestock();
        clearBucketTransfer();
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
            boolean synced = hasContainerSyncAfter(
                    menu.containerId, returnMoveSyncVersion);
            boolean confirmed = isConfirmedReturnTarget(menu);
            if (!synced && !(confirmed
                    && ++returnConfirmationTicks >= LOCAL_CONFIRMATION_GRACE_TICKS)) {
                if (++returnWaitTicks > OPEN_TIMEOUT_TICKS) finishCurrentReturn(client);
                return;
            }
            if (synced && !confirmed) {
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
            returnConfirmationTicks = 0;
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
        returnConfirmationTicks = 0;
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
            returnConfirmationTicks = 0;
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
            if (shulkerInventorySlot < 0) {
                if (activeBackend() == Configs.ShulkerOpenBackend.AX_SHULKERS
                        && ++returnSourceResolveWaitTicks <= OPEN_TIMEOUT_TICKS) {
                    returnQueue.addFirst(record);
                    nextReturnDelay = 1;
                    return;
                }
                returnSourceResolveWaitTicks = 0;
                continue;
            }
            returnSourceResolveWaitTicks = 0;
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
            returnConfirmationTicks = 0;
            if (!openShulker(client, menuSlot, 0)) {
                finishCurrentReturn(client);
            }
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
        if (activeReturn != null) {
            rollbackAxOpenClick(client, activeReturnShulkerSlot,
                    activeReturn.shulkerItem, activeReturn.shulkerName, 0);
        }
        if (client.player != null && client.player.containerMenu instanceof ShulkerBoxMenu) {
            closeContainerAndSetScreen(
                    client, new InventoryScreen(client.player), true);
        }
        activeReturn = null;
        activeReturnShulkerSlot = -1;
        returnWaitTicks = 0;
        returnSourceResolveWaitTicks = 0;
        returnMenuId = -1;
        nextReturnDelay = activeBackend()
                == Configs.ShulkerOpenBackend.AX_SHULKERS ? 2 : 0;
        returnMoveSyncVersion = 0;
        returnAwaitingSync = false;
        returnExpectedTargetSlot = -1;
        returnExpectedTargetCount = -1;
        returnPendingMoved = 0;
        returnConfirmationTicks = 0;
    }

    private static StoreTarget findStoreTarget(
            Inventory inventory, ItemStack prototype, int amount) {
        return findStoreTarget(inventory, prototype, amount, false);
    }

    private static StoreTarget findStoreTarget(
            Inventory inventory, ItemStack prototype, int amount,
            boolean allowPartialMerge) {
        StoreTarget emptyTarget = null;
        StoreTarget matchingBoxEmptyTarget = null;
        for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
            ItemStack shulker = inventory.getItem(inventorySlot);
            if (!ShulkerContentsHelper.isShulker(shulker)) continue;

            List<ItemStack> contents = ShulkerContentsHelper.getStacks(shulker);
            int emptySlot = -1;
            boolean containsMatchingItem = false;
            for (int shulkerSlot = 0; shulkerSlot < contents.size(); shulkerSlot++) {
                ItemStack current = contents.get(shulkerSlot);
                if (!current.isEmpty()
                        && ItemStack.isSameItemSameComponents(current, prototype)) {
                    containsMatchingItem = true;
                    int capacity = current.getMaxStackSize() - current.getCount();
                    if (capacity >= amount || allowPartialMerge && capacity > 0) {
                        return new StoreTarget(
                                inventorySlot, shulkerSlot, shulker.getItem(),
                                shulker.get(DataComponents.CUSTOM_NAME));
                    }
                }
                if (current.isEmpty() && emptySlot < 0
                        && prototype.getMaxStackSize() >= amount) {
                    emptySlot = shulkerSlot;
                }
            }
            if (emptySlot < 0) continue;
            StoreTarget candidate = new StoreTarget(
                    inventorySlot, emptySlot, shulker.getItem(),
                    shulker.get(DataComponents.CUSTOM_NAME));
            if (containsMatchingItem && matchingBoxEmptyTarget == null) {
                matchingBoxEmptyTarget = candidate;
            }
            if (emptyTarget == null) emptyTarget = candidate;
        }
        return matchingBoxEmptyTarget != null ? matchingBoxEmptyTarget : emptyTarget;
    }

    private static int storeTargetCapacity(
            Inventory inventory, StoreTarget target, ItemStack prototype) {
        if (target.inventorySlot() < 0 || target.inventorySlot() >= 36) return 0;
        ItemStack shulker = inventory.getItem(target.inventorySlot());
        if (!ShulkerContentsHelper.isShulker(shulker)) return 0;
        List<ItemStack> contents = ShulkerContentsHelper.getStacks(shulker);
        if (target.shulkerSlot() < 0 || target.shulkerSlot() >= contents.size()) return 0;
        ItemStack current = contents.get(target.shulkerSlot());
        if (!current.isEmpty()
                && !ItemStack.isSameItemSameComponents(current, prototype)) return 0;
        return prototype.getMaxStackSize() - current.getCount();
    }

    private static StoreTarget findClearanceStoreTarget(
            Inventory inventory, ItemStack prototype, int amount,
            int preferredTargetInventorySlot) {
        for (int pass = 0; pass < 2; pass++) {
            for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
                if (pass == 0 && inventorySlot != preferredTargetInventorySlot) continue;
                if (pass == 1 && inventorySlot == preferredTargetInventorySlot) continue;

                ItemStack shulker = inventory.getItem(inventorySlot);
                if (!ShulkerContentsHelper.isShulker(shulker)) continue;
                List<ItemStack> contents = ShulkerContentsHelper.getStacks(shulker);
                int totalCapacity = 0;
                int firstTargetSlot = -1;
                for (int shulkerSlot = 0; shulkerSlot < contents.size(); shulkerSlot++) {
                    ItemStack current = contents.get(shulkerSlot);
                    int capacity = current.isEmpty()
                            ? prototype.getMaxStackSize()
                            : ItemStack.isSameItemSameComponents(current, prototype)
                            ? current.getMaxStackSize() - current.getCount() : 0;
                    if (capacity <= 0) continue;
                    if (firstTargetSlot < 0) firstTargetSlot = shulkerSlot;
                    totalCapacity += capacity;
                }
                if (firstTargetSlot >= 0 && totalCapacity >= amount) {
                    return new StoreTarget(
                            inventorySlot, firstTargetSlot, shulker.getItem(),
                            shulker.get(DataComponents.CUSTOM_NAME));
                }
            }
        }
        return null;
    }

    private static int shulkerCapacity(ShulkerBoxMenu menu, ItemStack prototype) {
        int capacity = 0;
        for (int slotIndex = 0;
                slotIndex < ShulkerContentsHelper.SHULKER_SIZE; slotIndex++) {
            ItemStack current = menu.getSlot(slotIndex).getItem();
            if (current.isEmpty()) {
                capacity += prototype.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(current, prototype)) {
                capacity += Math.max(0, current.getMaxStackSize() - current.getCount());
            }
        }
        return capacity;
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

    private static void recordExtraction(
            ShulkerBoxMenu menu, PendingExtraction extraction, int moved) {
        if (!Configs.Features.RETURN_HISTORY.getBooleanValue()) return;
        ItemStack prototype = extraction.expectedStack().copyWithCount(1);
        ItemStack sourceShulker = findPlayerInventoryStack(
                menu, extraction.inventorySlot());
        CustomData identityData = copyIdentityData(sourceShulker);
        for (OriginRecord record : originRecords) {
            if (record.inventorySlot == extraction.inventorySlot()
                    && record.shulkerSlot == extraction.shulkerSlot()
                    && record.shulkerItem == extraction.shulkerItem()
                    && Objects.equals(record.shulkerName, extraction.shulkerName())
                    && sameRecordedIdentity(record.shulkerIdentityData, identityData)
                    && ItemStack.isSameItemSameComponents(record.prototype, prototype)) {
                if (record.shulkerIdentityData == null && identityData != null) {
                    record.shulkerIdentityData = identityData;
                }
                record.remaining += moved;
                return;
            }
        }
        originRecords.add(new OriginRecord(
                extraction.inventorySlot(), extraction.shulkerSlot(), prototype,
                extraction.shulkerItem(), extraction.shulkerName(), identityData, moved));
    }

    private static ItemStack findPlayerInventoryStack(
            AbstractContainerMenu menu, int inventorySlot) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return ItemStack.EMPTY;
        Inventory inventory = client.player.getInventory();
        int menuSlot = findPlayerInventoryMenuSlot(menu, inventory, inventorySlot);
        return menuSlot < 0 ? inventory.getItem(inventorySlot) : menu.getSlot(menuSlot).getItem();
    }

    private static CustomData copyIdentityData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null || data.isEmpty() ? null : CustomData.of(data.copyTag());
    }

    private static boolean sameRecordedIdentity(CustomData first, CustomData second) {
        return first == null || second == null || Objects.equals(first, second);
    }

    private static int findOriginShulker(Inventory inventory, OriginRecord record) {
        ItemStack originalSlotStack = inventory.getItem(record.inventorySlot);
        if (isMatchingOriginShulker(originalSlotStack, record)) return record.inventorySlot;
        for (int slot = 0; slot < 36; slot++) {
            if (slot != record.inventorySlot && isMatchingOriginShulker(inventory.getItem(slot), record)) {
                return slot;
            }
        }
        if (record.shulkerIdentityData != null
                && isMatchingOriginShulkerWithoutStableData(originalSlotStack, record)) {
            return record.inventorySlot;
        }
        return -1;
    }

    private static boolean isMatchingOriginShulker(ItemStack shulker, OriginRecord record) {
        if (!isOriginShulkerIdentity(shulker, record)) return false;
        List<ItemStack> contents = ShulkerContentsHelper.getStacks(shulker);
        return findReturnTargetSlot(contents, record.shulkerSlot, record.prototype) >= 0;
    }

    private static boolean isOriginShulkerIdentity(ItemStack shulker, OriginRecord record) {
        if (!isOriginShulkerBasicIdentity(shulker, record)) return false;
        if (record.shulkerIdentityData == null) return true;
        return Objects.equals(
                shulker.get(DataComponents.CUSTOM_DATA), record.shulkerIdentityData);
    }

    private static boolean isOriginShulkerBasicIdentity(
            ItemStack shulker, OriginRecord record) {
        return ShulkerContentsHelper.isShulker(shulker)
                && shulker.getItem() == record.shulkerItem
                && Objects.equals(shulker.get(DataComponents.CUSTOM_NAME), record.shulkerName);
    }

    private static boolean isMatchingOriginShulkerWithoutStableData(
            ItemStack shulker, OriginRecord record) {
        if (!isOriginShulkerBasicIdentity(shulker, record)) return false;
        return findReturnTargetSlot(
                ShulkerContentsHelper.getStacks(shulker), record.shulkerSlot,
                record.prototype) >= 0;
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

    private static int countMatchingLoosePlayerItems(
            Inventory inventory, ItemStack prototype) {
        if (prototype == null || prototype.isEmpty()) return 0;
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (ItemStack.isSameItemSameComponents(stack, prototype)) {
                count += stack.getCount();
            }
        }
        ItemStack offhand = inventory.getItem(Inventory.SLOT_OFFHAND);
        if (ItemStack.isSameItemSameComponents(offhand, prototype)) {
            count += offhand.getCount();
        }
        return count;
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
        client.gameMode.handleInventoryMouseClick(
                menu.containerId, sourceSlot, 0, ClickType.PICKUP, client.player);
        if (amount == sourceCount) {
            client.gameMode.handleInventoryMouseClick(
                    menu.containerId, targetSlot, 0, ClickType.PICKUP, client.player);
        } else {
            for (int i = 0; i < amount; i++) {
                client.gameMode.handleInventoryMouseClick(
                        menu.containerId, targetSlot, 1, ClickType.PICKUP, client.player);
            }
        }
        if (!menu.getCarried().isEmpty()) {
            client.gameMode.handleInventoryMouseClick(
                    menu.containerId, sourceSlot, 0, ClickType.PICKUP, client.player);
        }
    }

    private static void takeOne(
            Minecraft client, ShulkerBoxMenu menu,
            int sourceSlot, int destinationSlot) {
        int sourceCount = menu.getSlot(sourceSlot).getItem().getCount();
        client.gameMode.handleInventoryMouseClick(
                menu.containerId, sourceSlot, 0, ClickType.PICKUP, client.player);

        if (sourceCount == 1) {
            client.gameMode.handleInventoryMouseClick(
                    menu.containerId, destinationSlot, 0,
                    ClickType.PICKUP, client.player);
            return;
        }

        int quickCraftType = AbstractContainerMenu.QUICKCRAFT_TYPE_GREEDY;
        client.gameMode.handleInventoryMouseClick(
                menu.containerId, -999,
                AbstractContainerMenu.getQuickcraftMask(
                        AbstractContainerMenu.QUICKCRAFT_HEADER_START, quickCraftType),
                ClickType.QUICK_CRAFT, client.player);
        client.gameMode.handleInventoryMouseClick(
                menu.containerId, destinationSlot,
                AbstractContainerMenu.getQuickcraftMask(
                        AbstractContainerMenu.QUICKCRAFT_HEADER_CONTINUE, quickCraftType),
                ClickType.QUICK_CRAFT, client.player);
        client.gameMode.handleInventoryMouseClick(
                menu.containerId, sourceSlot,
                AbstractContainerMenu.getQuickcraftMask(
                        AbstractContainerMenu.QUICKCRAFT_HEADER_CONTINUE, quickCraftType),
                ClickType.QUICK_CRAFT, client.player);
        client.gameMode.handleInventoryMouseClick(
                menu.containerId, -999,
                AbstractContainerMenu.getQuickcraftMask(
                        AbstractContainerMenu.QUICKCRAFT_HEADER_END, quickCraftType),
                ClickType.QUICK_CRAFT, client.player);

        if (!menu.getCarried().isEmpty()) {
            client.gameMode.handleInventoryMouseClick(
                    menu.containerId, sourceSlot, 0, ClickType.PICKUP, client.player);
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
        if (activeBackend() == Configs.ShulkerOpenBackend.AX_SHULKERS) return menuSlot;
        return QuickShulkerCompat.toServerSlot(menu, menuSlot);
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
        if (menu == null || inventorySlot < 0
                || (inventorySlot >= 36 && inventorySlot != Inventory.SLOT_OFFHAND)) return -1;
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
        boolean automaticRestock = handRestock
                || (extractionToOffhand && extractionOffhandBackground);
        boolean background = pendingExtraction != null
                && (pendingExtraction.litematicaRestock() || handRestock
                || (extractionToOffhand && extractionOffhandBackground));
        diagnostic(extractionOperationId,
                "operation-failed kind=extraction reason=%s menu=%d waitTicks=%d moved=%d expectedSourceCount=%d openSyncBaseline=%d moveSyncBaseline=%d currentSyncVersion=%d",
                messageKey, extractionMenuId, extractionWaitTicks,
                extractionMovedItemCount, extractionExpectedSourceCount,
                extractionOpenSyncVersion, extractionMoveSyncVersion, containerSyncVersion);
        if (pendingExtraction != null) {
            rollbackAxOpenClick(client, pendingExtraction.inventorySlot(),
                    pendingExtraction.shulkerItem(), pendingExtraction.shulkerName(),
                    extractionOperationId);
        }
        if (!background) show(client, messageKey);
        if (client.player != null && client.player.containerMenu instanceof ShulkerBoxMenu) {
            closeContainerAndSetScreen(
                    client,
                    background ? null : new InventoryScreen(client.player),
                    !background);
        }
        if (automaticRestock) pauseAxAutomaticRestock();
        clearExtraction();
    }

    private static void closeAfterExtraction(Minecraft client) {
        PendingExtraction completedExtraction = pendingExtraction;
        ItemStack selected = pendingLitematicaSelection;
        long completedOperationId = extractionOperationId;
        int completedExpectedSourceCount = extractionExpectedSourceCount;
        boolean litematicaRestock = completedExtraction != null
                && completedExtraction.litematicaRestock();
        boolean handRestock = completedExtraction != null
                && completedExtraction.handRestock();
        boolean cursorPickup = completedExtraction != null
                && completedExtraction.cursorPickup()
                && extractionMovedItemCount > 0;
        boolean offhandTransfer = extractionToOffhand && extractionMovedItemCount > 0;
        boolean bucketTransfer = extractionBucketReplacement && extractionMovedItemCount > 0;
        boolean offhandBackground = extractionOffhandBackground;
        int offhandBaselineCount = extractionOffhandBaselineCount;
        ItemStack offhandOriginal = extractionOffhandOriginal.copy();
        int bucketSelectedSlot = extractionBucketSelectedSlot;
        int emptyBucketDestination = extractionEmptyBucketDestination;
        int emptyBucketDestinationBaseline = extractionEmptyBucketDestinationBaseline;
        boolean background = litematicaRestock || handRestock || offhandBackground;
        boolean continuedInOpenMenu = !background && !cursorPickup && !offhandTransfer
                && client.player != null
                && client.player.containerMenu instanceof ShulkerBoxMenu menu
                && continueQueuedExtractionInCurrentMenu(
                client, menu, completedExtraction);
        if (continuedInOpenMenu) {
            extractionOperationDelayTicks = programmaticExtractionDelayTicks;
            if (extractionOperationDelayTicks == 0) tickExtraction(client);
        }
        if (client.player != null && !continuedInOpenMenu) {
            boolean refreshProgrammaticInventory = programmaticExtractionBatch
                    && queuedExtractions.isEmpty() && !background;
            closeContainerAndSetScreen(
                    client,
                    background ? null : new InventoryScreen(client.player),
                    !background, refreshProgrammaticInventory);
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
        } else if (offhandTransfer) {
            pendingOffhandTransfer = new PendingOffhandTransfer(
                    completedExtraction.targetInventorySlot(),
                    completedExtraction.expectedStack().copyWithCount(1),
                    completedExtraction.targetBaselineCount(), extractionMovedItemCount,
                    offhandBaselineCount, offhandOriginal, offhandBackground);
            offhandTransferOperationId = completedOperationId;
            offhandTransferWaitTicks = 0;
            diagnostic(completedOperationId,
                    "extraction-confirmed moved=%d nextStage=offhand-transfer stagingInventorySlot=%d stagingBaseline=%d offhandBaseline=%d",
                    extractionMovedItemCount, completedExtraction.targetInventorySlot(),
                    completedExtraction.targetBaselineCount(), offhandBaselineCount);
        } else if (bucketTransfer) {
            pendingBucketTransfer = new PendingBucketTransfer(
                    completedExtraction.targetInventorySlot(),
                    completedExtraction.expectedStack().copyWithCount(1),
                    completedExtraction.targetBaselineCount(), extractionMovedItemCount,
                    bucketSelectedSlot, emptyBucketDestination,
                    emptyBucketDestinationBaseline);
            bucketTransferOperationId = completedOperationId;
            bucketTransferWaitTicks = 0;
            bucketTransferMoveSyncVersion = 0;
            bucketTransferAwaitingSync = false;
            diagnostic(completedOperationId,
                    "extraction-confirmed moved=%d nextStage=water-bucket-replacement stagingInventorySlot=%d selectedSlot=%d emptyBucketDestination=%d",
                    extractionMovedItemCount, completedExtraction.targetInventorySlot(),
                    bucketSelectedSlot, emptyBucketDestination);
        } else {
            int confirmedAmount = completedExtraction == null ? 0
                    : Math.max(0, completedExtraction.expectedStack().getCount()
                    - completedExpectedSourceCount);
            diagnostic(completedOperationId,
                    "operation-complete kind=extraction target=%s confirmedAmount=%d sourceAfter=%d menu=%d",
                    handRestock ? "main-hand" : litematicaRestock ? "litematica" : "inventory",
                    confirmedAmount, completedExpectedSourceCount, extractionMenuId);
            if (handRestock) pauseAxAutomaticRestock();
        }
        if (!continuedInOpenMenu) {
            clearExtraction();
            if (!queuedExtractions.isEmpty()) {
                queuedExtractionStartDelayTicks = programmaticExtractionDelayTicks;
            }
        }
        if (litematicaRestock) selectLitematicaItem(client, selected);
    }

    private static boolean continueQueuedExtractionInCurrentMenu(
            Minecraft client, ShulkerBoxMenu menu, PendingExtraction completedExtraction) {
        if (client.player == null || completedExtraction == null
                || queuedExtractions.isEmpty()) return false;

        QueuedExtraction queued = queuedExtractions.peekFirst();
        ItemStack requested = queued.item().stack().copyWithCount(1);
        int sourceSlot = -1;
        for (BundlePanelRenderer.ItemSource source : queued.item().sources()) {
            if (source.inventorySlot() != completedExtraction.inventorySlot()
                    || source.shulkerSlot() < 0
                    || source.shulkerSlot() >= ShulkerContentsHelper.SHULKER_SIZE) continue;
            ItemStack current = menu.getSlot(source.shulkerSlot()).getItem();
            if (!current.isEmpty()
                    && ItemStack.isSameItemSameComponents(current, requested)) {
                sourceSlot = source.shulkerSlot();
                break;
            }
        }
        if (sourceSlot < 0) {
            for (int slotIndex = 0;
                    slotIndex < ShulkerContentsHelper.SHULKER_SIZE; slotIndex++) {
                ItemStack current = menu.getSlot(slotIndex).getItem();
                if (!current.isEmpty()
                        && ItemStack.isSameItemSameComponents(current, requested)) {
                    sourceSlot = slotIndex;
                    break;
                }
            }
        }
        if (sourceSlot < 0) return false;

        Inventory inventory = client.player.getInventory();
        ItemStack sourceStack = menu.getSlot(sourceSlot).getItem();
        int required = queued.takeOne() ? 1 : sourceStack.getCount();
        if (inventoryCapacity(
                menu, inventory, sourceStack, completedExtraction.inventorySlot()) < required) {
            return false;
        }

        ItemStack shulker = inventory.getItem(completedExtraction.inventorySlot());
        if (!ShulkerContentsHelper.isShulker(shulker)) return false;
        queuedExtractions.removeFirst();
        pendingExtraction = new PendingExtraction(
                completedExtraction.inventorySlot(), sourceSlot, sourceStack.copy(),
                queued.takeOne(), shulker.getItem(), shulker.get(DataComponents.CUSTOM_NAME),
                false, false, false, -1, 0, 0);
        extractionOperationId = queued.operationId();
        extractionWaitTicks = 0;
        extractionMenuId = menu.containerId;
        extractionCloseDelay = -1;
        extractionMovedItemCount = 0;
        extractionExpectedSourceCount = -1;
        extractionToOffhand = false;
        extractionOffhandBackground = false;
        extractionOffhandBaselineCount = 0;
        extractionOffhandOriginal = ItemStack.EMPTY;
        extractionBucketReplacement = false;
        extractionBucketSelectedSlot = -1;
        extractionEmptyBucketDestination = -1;
        extractionEmptyBucketDestinationBaseline = 0;
        long menuSync = containerSyncVersions.getOrDefault(menu.containerId, containerSyncVersion);
        extractionOpenSyncVersion = menuSync - 1;
        extractionMoveSyncVersion = 0;
        diagnostic(extractionOperationId,
                "resume-open-menu queued-extraction menu=%d sourceInventorySlot=%d shulkerSlot=%d requested=%d queueSize=%d",
                menu.containerId, completedExtraction.inventorySlot(), sourceSlot,
                required, queuedExtractions.size());
        return true;
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
        pendingLitematicaSelection = ItemStack.EMPTY;
        extractionWaitTicks = 0;
        extractionMenuId = -1;
        extractionCloseDelay = -1;
        extractionMovedItemCount = 0;
        extractionOpenSyncVersion = 0;
        extractionMoveSyncVersion = 0;
        extractionExpectedSourceCount = -1;
        extractionToOffhand = false;
        extractionOffhandBackground = false;
        extractionOffhandBaselineCount = 0;
        extractionBucketReplacement = false;
        extractionBucketSelectedSlot = -1;
        extractionEmptyBucketDestination = -1;
        extractionEmptyBucketDestinationBaseline = 0;
        extractionOperationDelayTicks = 0;
        extractionOperationId = 0;
    }

    private static void clearCursorPickup() {
        pendingCursorPickup = null;
        cursorPickupWaitTicks = 0;
        cursorPickupOperationId = 0;
    }

    private static void clearOffhandTransfer() {
        pendingOffhandTransfer = null;
        offhandTransferWaitTicks = 0;
        offhandTransferOperationId = 0;
        offhandTransferMoveSyncVersion = 0;
        offhandTransferAwaitingSync = false;
    }

    private static void clearBucketTransfer() {
        pendingBucketTransfer = null;
        bucketTransferWaitTicks = 0;
        bucketTransferOperationId = 0;
        bucketTransferMoveSyncVersion = 0;
        bucketTransferAwaitingSync = false;
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
        if (pendingStore != null) {
            rollbackAxOpenClick(client, pendingStore.targetInventorySlot(),
                    pendingStore.shulkerItem(), pendingStore.shulkerName(), storeOperationId);
        }
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
        } else if (purpose == StorePurpose.PROGRAMMATIC_BATCH) {
            queuedStoreSessionCount = 0;
            InventoryDragStoreController.clearProgrammaticQueue();
            show(client, messageKey);
        } else if (purpose == StorePurpose.INVENTORY_DRAG) {
            queuedStoreSessionCount = 0;
            show(client, messageKey);
        } else {
            show(client, messageKey);
        }
    }

    private static void closeAfterStore(Minecraft client) {
        PendingStore completedStore = pendingStore;
        StorePurpose purpose = completedStore == null
                ? StorePurpose.MANUAL : completedStore.purpose();
        long completedOperationId = storeOperationId;
        boolean backgroundClearance = purpose == StorePurpose.EXTRACTION_PREPARATION
                && deferredExtraction != null
                && deferredExtraction.litematicaRestock();
        ItemStack storedPrototype = completedStore == null
                ? ItemStack.EMPTY : completedStore.prototype();
        int completed = storedItemCount;
        diagnostic(completedOperationId,
                "operation-complete kind=store purpose=%s confirmedAmount=%d menu=%d expectedTargetCount=%d",
                purpose, completed, storeMenuId, storeExpectedTargetCount);
        if (completed > 0 && !storedPrototype.isEmpty()) {
            consumeOriginRecords(storedPrototype, completed);
        }
        forgetStoredMainHandMemory(completedStore, completed);
        if (isQueuedStorePurpose(purpose)) {
            queuedStoreSessionCount += completed;
            if (client.player != null
                    && client.player.containerMenu instanceof ShulkerBoxMenu menu
                    && continueQueuedStoreInCurrentMenu(client, menu, completedStore)) {
                storeOperationDelayTicks = purpose == StorePurpose.PROGRAMMATIC_BATCH
                        ? programmaticStorageDelayTicks : 0;
                if (storeOperationDelayTicks == 0) tickStore(client);
                return;
            }
        }

        boolean keepMenuForDeferredExtraction = purpose == StorePurpose.EXTRACTION_PREPARATION
                && deferredExtraction != null
                && client.player != null
                && client.player.containerMenu instanceof ShulkerBoxMenu menu
                && canResumeDeferredExtractionInCurrentMenu(client, menu, deferredExtraction);
        deferredExtractionMenuId = keepMenuForDeferredExtraction
                ? client.player.containerMenu.containerId : -1;
        boolean deferProgrammaticReopen = purpose == StorePurpose.PROGRAMMATIC_BATCH
                && client.player != null
                && InventoryDragStoreController.peekNextProgrammatic(client) != null;
        if (client.player != null && !keepMenuForDeferredExtraction) {
            closeContainerAndSetScreen(
                    client,
                    backgroundClearance ? null : new InventoryScreen(client.player),
                    !backgroundClearance);
        }
        clearStore();
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
        } else if (isQueuedStorePurpose(purpose)) {
            int batchCompleted = queuedStoreSessionCount;
            queuedStoreSessionCount = 0;
            if (deferProgrammaticReopen) {
                InventoryDragStoreController.deferProgrammaticQueue(
                        programmaticStorageDelayTicks);
            }
            if (batchCompleted > 0) {
                show(client, "message.better-shulker-hud.store_complete", batchCompleted);
            }
        } else if (completed > 0) {
            show(client, "message.better-shulker-hud.store_complete", completed);
        }
    }

    private static boolean continueQueuedStoreInCurrentMenu(
            Minecraft client, ShulkerBoxMenu menu, PendingStore completedStore) {
        if (client.player == null || completedStore == null) return false;
        InventoryDragStoreController.QueuedStoreRequest request =
                completedStore.purpose() == StorePurpose.PROGRAMMATIC_BATCH
                        ? InventoryDragStoreController.peekNextProgrammatic(client)
                        : InventoryDragStoreController.peekNextValid(client);
        if (request == null) return false;

        Inventory inventory = client.player.getInventory();
        ItemStack source = inventory.getItem(request.inventorySlot());
        ItemStack prototype = source.copyWithCount(1);
        int requestedAmount = request.oneItem() ? 1 : source.getCount();
        int targetSlot = findOpenMenuStoreTarget(
                menu, prototype, requestedAmount, request.programmatic());
        if (targetSlot < 0) return false;
        ItemStack target = menu.getSlot(targetSlot).getItem();
        int capacity = prototype.getMaxStackSize() - target.getCount();
        int amount = Math.min(request.oneItem() ? 1 : source.getCount(), capacity);
        if (amount <= 0) return false;

        if (!request.programmatic()) InventoryDragStoreController.consume(request);
        pendingStore = new PendingStore(
                request.inventorySlot(), amount, completedStore.targetInventorySlot(), targetSlot,
                prototype, completedStore.shulkerItem(), completedStore.shulkerName(),
                completedStore.purpose(),
                findStoredMainHandMemorySlot(inventory, prototype, request.inventorySlot()));
        storeOperationId = nextDiagnosticOperationId();
        storeWaitTicks = 0;
        storeMenuId = menu.containerId;
        storeCloseDelay = -1;
        storedItemCount = 0;
        storeTransfer = null;
        long menuSync = containerSyncVersions.getOrDefault(menu.containerId, containerSyncVersion);
        storeOpenSyncVersion = menuSync - 1;
        storeMoveSyncVersion = 0;
        storeExpectedTargetCount = -1;
        storeContinueAfterMove = false;
        diagnostic(storeOperationId,
                "resume-open-menu drag-store menu=%d sourceInventorySlot=%d targetInventorySlot=%d shulkerSlot=%d amount=%d",
                menu.containerId, request.inventorySlot(), completedStore.targetInventorySlot(),
                targetSlot, amount);
        return true;
    }

    private static int findOpenMenuStoreTarget(
            ShulkerBoxMenu menu, ItemStack prototype, int amount,
            boolean allowPartialMerge) {
        int emptySlot = -1;
        for (int slotIndex = 0;
                slotIndex < ShulkerContentsHelper.SHULKER_SIZE; slotIndex++) {
            ItemStack current = menu.getSlot(slotIndex).getItem();
            if (!current.isEmpty()
                    && ItemStack.isSameItemSameComponents(current, prototype)) {
                int capacity = current.getMaxStackSize() - current.getCount();
                if (capacity >= amount || allowPartialMerge && capacity > 0) {
                    return slotIndex;
                }
            }
            if (current.isEmpty() && emptySlot < 0
                    && prototype.getMaxStackSize() >= amount) {
                emptySlot = slotIndex;
            }
        }
        return emptySlot;
    }

    private static boolean canResumeDeferredExtractionInCurrentMenu(
            Minecraft client, ShulkerBoxMenu menu, DeferredExtraction deferred) {
        if (pendingStore == null || deferred == null || client.player == null) return false;
        ResolvedSource source = deferred.litematicaRestock()
                ? findRestockSource(menu, client.player.getInventory(), deferred.litematicaRequired())
                : findValidatedSource(menu, client.player.getInventory(), deferred.item());
        return source != null && source.inventorySlot() == pendingStore.targetInventorySlot();
    }

    private static boolean isQueuedStorePurpose(StorePurpose purpose) {
        return purpose == StorePurpose.INVENTORY_DRAG
                || purpose == StorePurpose.PROGRAMMATIC_BATCH;
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
            pendingLitematicaSelection =
                    deferred.litematicaRequired().copyWithCount(1);
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
        closeContainerAndSetScreen(client, nextScreen, preserveCursor, false);
    }

    private static void closeContainerAndSetScreen(
            Minecraft client, Screen nextScreen, boolean preserveCursor,
            boolean forceScreenRefresh) {
        boolean keepCurrentInventoryScreen = !forceScreenRefresh
                && nextScreen instanceof InventoryScreen
                && client.screen instanceof InventoryScreen;
        long window = client.getWindow().getWindow();
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
        storeOperationDelayTicks = 0;
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
        returnSourceResolveWaitTicks = 0;
        returnMenuId = -1;
        nextReturnDelay = -1;
        returnedItemCount = 0;
        returnOpenSyncVersion = 0;
        returnMoveSyncVersion = 0;
        returnAwaitingSync = false;
        returnExpectedTargetSlot = -1;
        returnExpectedTargetCount = -1;
        returnPendingMoved = 0;
        returnConfirmationTicks = 0;
    }

    private static void show(Minecraft client, String key, Object... args) {
        if (client.player != null) {
            client.player.displayClientMessage(Component.translatable(key, args), true);
        }
    }

    private static Configs.ShulkerOpenBackend selectedBackend() {
        return (Configs.ShulkerOpenBackend)
                Configs.General.SHULKER_OPEN_BACKEND.getOptionListValue();
    }

    private static boolean canUseConfiguredBackend() {
        return activeBackend() == Configs.ShulkerOpenBackend.AX_SHULKERS
                || QuickShulkerCompat.canSend();
    }

    private static Configs.ShulkerOpenBackend activeBackend() {
        Configs.ShulkerOpenBackend configured = selectedBackend();
        if (configured != Configs.ShulkerOpenBackend.AUTO) return configured;
        return QuickShulkerCompat.canSend()
                ? Configs.ShulkerOpenBackend.QUICK_SHULKER
                : Configs.ShulkerOpenBackend.AX_SHULKERS;
    }

    private static boolean openShulker(Minecraft client, int menuSlot, long operationId) {
        Configs.ShulkerOpenBackend backend = activeBackend();
        diagnostic(operationId, "open-shulker backend=%s slot=%d",
                backend.getStringValue(), menuSlot);
        if (backend == Configs.ShulkerOpenBackend.AX_SHULKERS) {
            if (client.player == null || client.gameMode == null
                    || client.getConnection() == null) return false;
            AbstractContainerMenu menu = client.player.containerMenu;
            if (menuSlot < 0 || menuSlot >= menu.slots.size()
                    || !menu.getCarried().isEmpty()) return false;
            Slot clicked = menu.getSlot(menuSlot);
            if (clicked.container != client.player.getInventory()
                    || !ShulkerContentsHelper.isShulker(clicked.getItem())) return false;
            client.getConnection().send(new ServerboundContainerClickPacket(
                    menu.containerId, menu.getStateId(), menuSlot, 1,
                    ClickType.PICKUP, ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
            return true;
        }
        if (!QuickShulkerCompat.canSend()) return false;
        QuickShulkerCompat.open(menuSlot);
        return true;
    }

    private static void rollbackAxOpenClick(
            Minecraft client, int inventorySlot, Item expectedItem,
            Component expectedName, long operationId) {
        if (activeBackend() != Configs.ShulkerOpenBackend.AX_SHULKERS
                || client.player == null || client.gameMode == null
                || client.player.containerMenu instanceof ShulkerBoxMenu) {
            return;
        }
        AbstractContainerMenu menu = client.player.containerMenu;
        ItemStack carried = menu.getCarried();
        if (!ShulkerContentsHelper.isShulker(carried)
                || carried.getCount() != 1 || carried.getItem() != expectedItem
                || !Objects.equals(carried.get(DataComponents.CUSTOM_NAME), expectedName)) {
            return;
        }
        int menuSlot = findPlayerInventoryMenuSlot(
                menu, client.player.getInventory(), inventorySlot);
        if (menuSlot < 0 || !menu.getSlot(menuSlot).getItem().isEmpty()) return;
        diagnostic(operationId,
                "axshulkers-open-rollback inventorySlot=%d menuSlot=%d item=%s",
                inventorySlot, menuSlot, itemId(carried));
        client.gameMode.handleInventoryMouseClick(
                menu.containerId, menuSlot, 0, ClickType.PICKUP, client.player);
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
        if (pendingOffhandTransfer != null && offhandTransferOperationId != 0) {
            return offhandTransferOperationId;
        }
        if (pendingBucketTransfer != null && bucketTransferOperationId != 0) {
            return bucketTransferOperationId;
        }
        return storeOperationId != 0 ? storeOperationId
                : extractionOperationId != 0 ? extractionOperationId
                : cursorPickupOperationId != 0 ? cursorPickupOperationId
                : offhandTransferOperationId != 0 ? offhandTransferOperationId
                : bucketTransferOperationId;
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

    private record PendingOffhandTransfer(
            int inventorySlot, ItemStack prototype,
            int stagingBaselineCount, int extractedAmount,
            int offhandBaselineCount, ItemStack originalOffhand, boolean background) {}

    private record PendingBucketTransfer(
            int stagingInventorySlot, ItemStack prototype,
            int stagingBaselineCount, int extractedAmount,
            int selectedInventorySlot, int emptyBucketInventorySlot,
            int emptyBucketBaselineCount) {}

    private record QueuedExtraction(
            BundlePanelRenderer.FlatItem item, boolean takeOne, long operationId) {}

    private record ResolvedSource(
            int inventorySlot, int shulkerSlot, ItemStack expectedStack, int quickShulkerSlot,
            Item shulkerItem, Component shulkerName) {}

    private record PendingStore(
            int sourceInventorySlot, int amount, int targetInventorySlot, int shulkerSlot,
            ItemStack prototype, Item shulkerItem, Component shulkerName,
            StorePurpose purpose, int mainHandMemorySlot) {}

    private enum StorePurpose {
        MANUAL,
        INVENTORY_DRAG,
        PROGRAMMATIC_BATCH,
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

    private record BucketDestination(int inventorySlot, int baselineCount) {}

    private static final class OriginRecord {
        private final int inventorySlot;
        private final int shulkerSlot;
        private final ItemStack prototype;
        private final Item shulkerItem;
        private final Component shulkerName;
        private CustomData shulkerIdentityData;
        private int remaining;

        private OriginRecord(
                int inventorySlot, int shulkerSlot, ItemStack prototype,
                Item shulkerItem, Component shulkerName,
                CustomData shulkerIdentityData, int remaining) {
            this.inventorySlot = inventorySlot;
            this.shulkerSlot = shulkerSlot;
            this.prototype = prototype;
            this.shulkerItem = shulkerItem;
            this.shulkerName = shulkerName;
            this.shulkerIdentityData = shulkerIdentityData;
            this.remaining = remaining;
        }
    }
}
