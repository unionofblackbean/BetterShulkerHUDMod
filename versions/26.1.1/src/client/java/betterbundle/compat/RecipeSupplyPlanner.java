package bettershulkerhud.compat;

import bettershulkerhud.gui.BundlePanelRenderer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Plans a 2x2 or 3x3 craft without moving anything. A null result means unavailable. */
public final class RecipeSupplyPlanner {
    private RecipeSupplyPlanner() {}

    /** One source stack and the exact amount to stage before vanilla fills the grid. */
    public record Supply(BundlePanelRenderer.FlatItem item, int amount) {}

    public record MaxPlan(int crafts, List<Supply> supplies) {
        public int missingCount() {
            return supplies.stream().mapToInt(Supply::amount).sum();
        }
    }

    public static List<BundlePanelRenderer.FlatItem> plan(
            List<Ingredient> requirements, List<ItemStack> looseAndGrid,
            List<BundlePanelRenderer.FlatItem> boxed) {
        if (requirements == null || requirements.isEmpty() || requirements.size() > 9) return null;

        Map<Item, Stock> stock = buildStock(looseAndGrid, boxed);
        if (stock.isEmpty()) return null;

        Map<Item, Integer> chosen = new HashMap<>();
        Search search = new Search();
        search(requirements, 0, stock, chosen, search);
        if (search.best == null) return null;

        List<BundlePanelRenderer.FlatItem> toExtract = new ArrayList<>();
        for (Map.Entry<Item, Stock> entry : stock.entrySet()) {
            int need = Math.max(0, search.best.getOrDefault(entry.getKey(), 0)
                    - entry.getValue().loose);
            for (BundlePanelRenderer.FlatItem flat : entry.getValue().sources) {
                int fromThisSource = Math.min(need, flat.stack().getCount());
                for (int i = 0; i < fromThisSource; i++) toExtract.add(flat);
                need -= fromThisSource;
                if (need == 0) break;
            }
            if (need != 0) return null;
        }
        return List.copyOf(toExtract);
    }

    /** Choose the most complete crafts that fit each ingredient's stack limit. */
    public static MaxPlan planMax(
            List<Ingredient> requirements, List<ItemStack> looseAndGrid,
            List<BundlePanelRenderer.FlatItem> boxed) {
        if (requirements == null || requirements.isEmpty() || requirements.size() > 9) return null;
        Map<Item, Stock> stock = buildStock(looseAndGrid, boxed);
        if (stock.isEmpty()) return null;
        int requiredSlots = (int) requirements.stream()
                .filter(ingredient -> ingredient != null && !ingredient.isEmpty()).count();
        if (requiredSlots == 0) return null;
        int total = stock.values().stream()
                .mapToInt(entry -> entry.loose + entry.boxed).sum();
        int upper = Math.min(64, total / requiredSlots);
        Map<Item, Integer> chosen = null;
        int crafts = 0;
        // A first feasible assignment is enough at each amount. Searching every
        // permutation of interchangeable ingredients can otherwise freeze the UI.
        for (int candidateCrafts = upper; candidateCrafts > 0; candidateCrafts--) {
            chosen = findMaxAssignment(requirements, 0, stock,
                    new HashMap<>(), candidateCrafts);
            if (chosen != null) {
                crafts = candidateCrafts;
                break;
            }
        }
        if (chosen == null) return null;

        List<Supply> supplies = new ArrayList<>();
        for (Map.Entry<Item, Stock> entry : stock.entrySet()) {
            int need = Math.max(0, chosen.getOrDefault(entry.getKey(), 0)
                    * crafts - entry.getValue().loose);
            for (BundlePanelRenderer.FlatItem flat : entry.getValue().sources) {
                for (BundlePanelRenderer.ItemSource source : flat.sources()) {
                    int count = Math.min(need, source.stack().getCount());
                    if (count > 0) {
                        supplies.add(new Supply(new BundlePanelRenderer.FlatItem(
                                source.stack().copy(), List.of(source)), count));
                        need -= count;
                    }
                    if (need == 0) break;
                }
                if (need == 0) break;
            }
            if (need != 0) return null;
        }
        return new MaxPlan(crafts, List.copyOf(supplies));
    }

    private static Map<Item, Stock> buildStock(
            List<ItemStack> looseAndGrid, List<BundlePanelRenderer.FlatItem> boxed) {
        Map<Item, Stock> stock = new LinkedHashMap<>();
        for (ItemStack stack : looseAndGrid) {
            if (stack.isEmpty() || !Inventory.isUsableForCrafting(stack)) continue;
            Stock entry = stock.computeIfAbsent(stack.getItem(), ignored -> new Stock(stack));
            entry.loose += Math.min(stack.getCount(), stack.getMaxStackSize());
        }
        for (BundlePanelRenderer.FlatItem flat : boxed) {
            ItemStack stack = flat.stack();
            if (stack.isEmpty() || !Inventory.isUsableForCrafting(stack)) continue;
            Stock entry = stock.computeIfAbsent(stack.getItem(), ignored -> new Stock(stack));
            entry.boxed += stack.getCount();
            entry.sources.add(flat);
        }
        return stock;
    }

    private static Map<Item, Integer> findMaxAssignment(
            List<Ingredient> requirements, int index, Map<Item, Stock> stock,
            Map<Item, Integer> chosen, int crafts) {
        if (index == requirements.size()) {
            return Map.copyOf(chosen);
        }
        Ingredient ingredient = requirements.get(index);
        if (ingredient == null || ingredient.isEmpty()) {
            return findMaxAssignment(requirements, index + 1, stock,
                    chosen, crafts);
        }
        List<Map.Entry<Item, Stock>> candidates = new ArrayList<>(stock.entrySet());
        candidates.sort(Comparator.comparingInt((Map.Entry<Item, Stock> candidate) -> {
            int assigned = chosen.getOrDefault(candidate.getKey(), 0);
            int loose = candidate.getValue().loose;
            return Math.max(0, (assigned + 1) * crafts - loose)
                    - Math.max(0, assigned * crafts - loose);
        }).thenComparingInt(candidate -> -candidate.getValue().loose));
        for (Map.Entry<Item, Stock> candidate : candidates) {
            Stock available = candidate.getValue();
            int assigned = chosen.getOrDefault(candidate.getKey(), 0);
            if (crafts > available.sample.getMaxStackSize()
                    || (assigned + 1) * crafts > available.loose + available.boxed
                    || !ingredient.test(available.sample)) continue;
            chosen.put(candidate.getKey(), assigned + 1);
            Map<Item, Integer> found = findMaxAssignment(requirements,
                    index + 1, stock, chosen, crafts);
            if (found != null) return found;
            if (assigned == 0) chosen.remove(candidate.getKey());
            else chosen.put(candidate.getKey(), assigned);
        }
        return null;
    }

    private static void search(
            List<Ingredient> requirements, int index, Map<Item, Stock> stock,
            Map<Item, Integer> chosen, Search result) {
        if (index == requirements.size()) {
            int missing = 0;
            for (Map.Entry<Item, Integer> selected : chosen.entrySet()) {
                missing += Math.max(0, selected.getValue() - stock.get(selected.getKey()).loose);
            }
            if (missing < result.missing) {
                result.missing = missing;
                result.best = Map.copyOf(chosen);
            }
            return;
        }

        Ingredient ingredient = requirements.get(index);
        if (ingredient == null || ingredient.isEmpty()) {
            search(requirements, index + 1, stock, chosen, result);
            return;
        }
        for (Map.Entry<Item, Stock> candidate : stock.entrySet()) {
            Stock available = candidate.getValue();
            int assigned = chosen.getOrDefault(candidate.getKey(), 0);
            if (assigned >= available.loose + available.boxed
                    || !ingredient.test(available.sample)) continue;
            chosen.put(candidate.getKey(), assigned + 1);
            search(requirements, index + 1, stock, chosen, result);
            if (assigned == 0) chosen.remove(candidate.getKey());
            else chosen.put(candidate.getKey(), assigned);
        }
    }

    private static final class Stock {
        private final ItemStack sample;
        private final List<BundlePanelRenderer.FlatItem> sources = new ArrayList<>();
        private int loose;
        private int boxed;

        private Stock(ItemStack sample) {
            this.sample = sample;
        }
    }

    private static final class Search {
        private int missing = Integer.MAX_VALUE;
        private Map<Item, Integer> best;
    }

}
