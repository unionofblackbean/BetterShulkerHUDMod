package bettershulkerhud.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Source memory for the new bundle and ender-chest server paths.
 * Adapted from Sakurastreet/BetterShulkerHUDMod under the MIT license.
 */
final class PortableReturnTracker {
    enum Kind {
        BUNDLE,
        ENDER_CHEST
    }

    @FunctionalInterface
    interface RouteInserter {
        int insert(int inventorySlot, ItemStack candidate);
    }

    record RestoreResult(int restoredCount, int fallbackCount) {}

    private static final Map<ServerPlayer, List<ItemTrace>> TRACES = new WeakHashMap<>();

    private PortableReturnTracker() {}

    static void remember(ServerPlayer player, ItemStack item, Kind kind,
                         ItemStack sourceContainer, int inventorySlot, int count) {
        if (item == null || item.isEmpty() || count <= 0) return;
        ItemTrace trace = findTrace(player, item, true);
        for (Route route : trace.routes) {
            if (route.kind == kind && route.sourceContainer == sourceContainer
                    && route.inventorySlot == inventorySlot) {
                route.count += count;
                return;
            }
        }
        trace.routes.add(new Route(kind, sourceContainer, inventorySlot, count));
    }

    static RestoreResult restore(ServerPlayer player, ItemStack item, int requested,
                                 Kind kind, RouteInserter inserter) {
        if (item == null || item.isEmpty() || requested <= 0) {
            return new RestoreResult(0, Math.max(0, requested));
        }
        ItemTrace trace = findTrace(player, item, false);
        if (trace == null) return new RestoreResult(0, requested);

        int trackedForKind = trace.routes.stream()
                .filter(route -> route.kind == kind)
                .mapToInt(route -> Math.max(0, route.count))
                .sum();
        int remainingTrackedRequest = Math.min(requested, trackedForKind);
        int restored = 0;
        Iterator<Route> iterator = trace.routes.iterator();
        while (iterator.hasNext() && remainingTrackedRequest > 0) {
            Route route = iterator.next();
            if (route.kind != kind || route.count <= 0) continue;
            int routeRequest = Math.min(remainingTrackedRequest, route.count);
            int resolvedSlot = resolveInventorySlot(player, route);
            if (kind != Kind.ENDER_CHEST && resolvedSlot < 0) continue;
            int inserted = Math.clamp(
                    inserter.insert(resolvedSlot, item.copyWithCount(routeRequest)),
                    0, routeRequest);
            if (inserted <= 0) continue;
            route.count -= inserted;
            remainingTrackedRequest -= inserted;
            restored += inserted;
            if (route.count == 0) iterator.remove();
        }
        removeTraceIfEmpty(player, trace);

        // Tracked items that could not be restored stay in the player's
        // inventory; only the untracked portion may fall back elsewhere.
        return new RestoreResult(restored, requested - Math.min(requested, trackedForKind));
    }

    static void consumeOtherKinds(ServerPlayer player, ItemStack item,
                                  Kind destinationKind, int count) {
        if (item == null || item.isEmpty() || count <= 0) return;
        ItemTrace trace = findTrace(player, item, false);
        if (trace == null) return;
        int remaining = count;
        Iterator<Route> iterator = trace.routes.iterator();
        while (iterator.hasNext() && remaining > 0) {
            Route route = iterator.next();
            if (route.kind == destinationKind || route.count <= 0) continue;
            int consumed = Math.min(remaining, route.count);
            route.count -= consumed;
            remaining -= consumed;
            if (route.count == 0) iterator.remove();
        }
        removeTraceIfEmpty(player, trace);
    }

    private static int resolveInventorySlot(ServerPlayer player, Route route) {
        if (route.kind == Kind.ENDER_CHEST) return -1;
        Inventory inventory = player.getInventory();
        if (StorageServerUtil.isInventorySlot(route.inventorySlot)
                && inventory.getItem(route.inventorySlot) == route.sourceContainer) {
            return route.inventorySlot;
        }
        for (int slot = 0; slot < 36; slot++) {
            if (inventory.getItem(slot) == route.sourceContainer) {
                route.inventorySlot = slot;
                return slot;
            }
        }
        if (inventory.getItem(Inventory.SLOT_OFFHAND) == route.sourceContainer) {
            route.inventorySlot = Inventory.SLOT_OFFHAND;
            return Inventory.SLOT_OFFHAND;
        }
        return -1;
    }

    private static ItemTrace findTrace(
            ServerPlayer player, ItemStack item, boolean create) {
        List<ItemTrace> traces = TRACES.get(player);
        if (traces == null) {
            if (!create) return null;
            traces = new ArrayList<>();
            TRACES.put(player, traces);
        }
        for (ItemTrace trace : traces) {
            if (ItemStack.isSameItemSameComponents(trace.sample, item)) return trace;
        }
        if (!create) return null;
        ItemTrace trace = new ItemTrace(item.copyWithCount(1));
        traces.add(trace);
        return trace;
    }

    private static void removeTraceIfEmpty(ServerPlayer player, ItemTrace trace) {
        if (!trace.routes.isEmpty()) return;
        List<ItemTrace> traces = TRACES.get(player);
        if (traces == null) return;
        traces.remove(trace);
        if (traces.isEmpty()) TRACES.remove(player);
    }

    private static final class ItemTrace {
        private final ItemStack sample;
        private final List<Route> routes = new ArrayList<>();

        private ItemTrace(ItemStack sample) {
            this.sample = sample;
        }
    }

    private static final class Route {
        private final Kind kind;
        private final ItemStack sourceContainer;
        private int inventorySlot;
        private int count;

        private Route(Kind kind, ItemStack sourceContainer,
                      int inventorySlot, int count) {
            this.kind = kind;
            this.sourceContainer = sourceContainer;
            this.inventorySlot = inventorySlot;
            this.count = count;
        }
    }
}
