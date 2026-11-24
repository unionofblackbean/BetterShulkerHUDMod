package bettershulkerhud.compat;

import bettershulkerhud.gui.BundlePanelRenderer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Plans a 2x2 or 3x3 craft without moving anything. A null result means unavailable. */
public final class RecipeSupplyPlanner {
    private RecipeSupplyPlanner() {}

    public static List<BundlePanelRenderer.FlatItem> plan(
            List<Ingredient> requirements, List<ItemStack> looseAndGrid,
            List<BundlePanelRenderer.FlatItem> boxed) {
        if (requirements == null || requirements.isEmpty() || requirements.size() > 9) return null;

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
