package bettershulkerhud.gui;

import bettershulkerhud.compat.StorageClientNetwork;
import bettershulkerhud.config.Configs;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import me.towdium.pinin.PinIn;
import net.minecraft.client.Minecraft;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import bettershulkerhud.util.ShulkerContentsHelper;
import bettershulkerhud.compat.QuickShulkerExtractionController;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

public final class BundlePanelRenderer {

    public static final int SLOT_SIZE = 18;
    public static final int SLOT_SPACING = 0;
    public static final int PADDING = 5;
    public static final int SCROLL_BAR_WIDTH = 12;
    public static final int CAT_BUTTON_SIZE = 18;
    public static final int CAT_BAR_WIDTH = CAT_BUTTON_SIZE;
    public static final int SEARCH_BAR_HEIGHT = 18;
    public static final int HEADER_HEIGHT = 24;
    public static final int FOOTER_HEIGHT = 24;
    public static final int TOGGLE_WIDTH = 20;
    public static final int TOGGLE_HEIGHT = 18;
    public static final int STORAGE_TAB_SIZE = 18;

    private static final int SCREEN_MARGIN = 4;
    private static final int PANEL_GAP = 6;
    private static final int CATEGORY_GAP = 2;
    private static final int SCROLL_GAP = 7;
    private static final int STORAGE_TAB_GAP = 2;
    private static final int BODY_INSET = 12;
    private static final int CONTROL_SIZE = 14;
    private static final int COLOR_PANEL = 0xFFC6C6C6;
    private static final int COLOR_BORDER_LIGHT = 0xFFFFFFFF;
    private static final int COLOR_BORDER_MID = 0xFF8B8B8B;
    private static final int COLOR_BORDER_DARK = 0xFF373737;
    private static final int COLOR_SHADOW = 0x70000000;
    private static final int COLOR_BUTTON_HOVER = 0xFFD8D8D8;
    private static final int COLOR_BUTTON_DISABLED = 0xFF9B9B9B;
    private static final int COLOR_INPUT = 0xFFE3E3E3;
    private static final int COLOR_TEXT = 0xFF404040;
    private static final int COLOR_TEXT_MUTED = 0xFF707070;

    private static final Identifier SLOT_SPRITE =
            Identifier.withDefaultNamespace("container/slot");
    private static final Identifier TEXT_FIELD_SPRITE =
            Identifier.withDefaultNamespace("widget/text_field");
    private static final Identifier TEXT_FIELD_HIGHLIGHTED_SPRITE =
            Identifier.withDefaultNamespace("widget/text_field_highlighted");
    private static final Identifier BUTTON_SPRITE =
            Identifier.withDefaultNamespace("widget/button");
    private static final Identifier BUTTON_HIGHLIGHTED_SPRITE =
            Identifier.withDefaultNamespace("widget/button_highlighted");
    private static final Identifier BUTTON_DISABLED_SPRITE =
            Identifier.withDefaultNamespace("widget/button_disabled");
    private static final Identifier BUTTON_TEXTURE =
            Identifier.withDefaultNamespace("textures/gui/sprites/widget/button.png");
    private static final Identifier TEXT_FIELD_TEXTURE =
            Identifier.withDefaultNamespace("textures/gui/sprites/widget/text_field.png");
    private static final Identifier SCROLLER_SPRITE =
            Identifier.withDefaultNamespace("container/creative_inventory/scroller");
    private static final Identifier SCROLLER_DISABLED_SPRITE =
            Identifier.withDefaultNamespace("container/creative_inventory/scroller_disabled");
    private static final Identifier SCROLLER_BACKGROUND_SPRITE =
            Identifier.withDefaultNamespace("widget/scroller_background");
    private static int scrollOffset = 0;
    private static boolean togglePositionAdjustMode;
    private static boolean toggleButtonDragging;
    private static double toggleButtonDragOffsetX;
    private static double toggleButtonDragOffsetY;
    public static String searchQuery = "";
    public static boolean searchFocused = false;
    private static int searchCursorTick = 0;
    private static int hoveredShulkerInventorySlot = -1;
    private static final PinIn PIN_IN = createPinIn();

    private static Player cachedPlayer;
    private static Object cachedScreen;
    private static boolean sortPreparedAfterClose;
    private static int cachedInventoryRevision = Integer.MIN_VALUE;
    private static long cachedActiveContentsFingerprint = Long.MIN_VALUE;
    private static boolean contentsCacheDirty = true;
    private static List<ShulkerSlotEntry> cachedAllShulkers = List.of();
    private static List<ShulkerSlotEntry> cachedNonEmptyShulkers = List.of();
    private static List<FlatItem> cachedFlatItems = List.of();
    private static List<FlatItem> cachedVisibleItems = List.of();
    private static String cachedSearchQuery = null;
    private static BundleCategory cachedCategory = null;
    private static StorageView cachedStorageView = null;
    private static List<FlatItem> cachedBundleFlatItems = List.of();
    private static List<FlatItem> cachedEnderFlatItems = List.of();
    private static long cachedEnderRevision = Long.MIN_VALUE;
    private static long cachedVisibleEnderRevision = Long.MIN_VALUE;
    private static ActiveContentsSnapshot activeContentsSnapshot;
    public static BundleCategory currentCategory = BundleCategory.OVERVIEW;
    private static StorageView storageView = StorageView.SHULKERS;

    private BundlePanelRenderer() {}

    public record ShulkerSlotEntry(int inventorySlot, ItemStack shulkerStack, List<ItemStack> contents) {}

    public static int columnCount(int leftPos) {
        Minecraft client = Minecraft.getInstance();
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int imageWidth = client.gui.screen() instanceof AbstractContainerScreen<?> screen
                ? screen.imageWidth : 176;
        int leftSpace = leftPos - SCREEN_MARGIN - PANEL_GAP;
        int rightSpace = screenWidth - (leftPos + imageWidth)
                - SCREEN_MARGIN - PANEL_GAP;
        int available = isRecipeBookOpen() ? rightSpace : Math.max(leftSpace, rightSpace);
        int fixedWidth = PADDING + CAT_BAR_WIDTH + CATEGORY_GAP
                + SCROLL_BAR_WIDTH + SCROLL_GAP + PADDING;
        int columns = (available - fixedWidth + SLOT_SPACING)
                / (SLOT_SIZE + SLOT_SPACING);
        return Math.clamp(columns, 1, Configs.General.HUD_MAX_COLUMNS.getIntegerValue());
    }

    public static int visibleRowCount(int topPos, int imageHeight) {
        Minecraft client = Minecraft.getInstance();
        int availableHeight = client.getWindow().getGuiScaledHeight() - SCREEN_MARGIN * 2;
        int contentHeight = availableHeight - HEADER_HEIGHT - PADDING * 2 - FOOTER_HEIGHT;
        int rows = (contentHeight + SLOT_SPACING) / (SLOT_SIZE + SLOT_SPACING);
        return Math.clamp(rows, 1, Configs.General.HUD_MAX_ROWS.getIntegerValue());
    }

    public static int panelWidth(int leftPos) {
        int columns = columnCount(leftPos);
        return PADDING + CAT_BAR_WIDTH + CATEGORY_GAP + SCROLL_BAR_WIDTH + SCROLL_GAP
                + columns * (SLOT_SIZE + SLOT_SPACING) - SLOT_SPACING + PADDING;
    }

    public static int panelHeight(int topPos, int imageHeight) {
        int rows = visibleRowCount(topPos, imageHeight);
        return HEADER_HEIGHT + PADDING * 2
                + rows * SLOT_SIZE + (rows - 1) * SLOT_SPACING + FOOTER_HEIGHT;
    }

    public static int exclusionX(int leftPos) {
        return panelX(leftPos) - 4;
    }

    public static int exclusionY(int topPos, int imageHeight) {
        return panelY(topPos, imageHeight) - 1;
    }

    public static int exclusionWidth(int leftPos) {
        return panelWidth(leftPos) + 8;
    }

    public static int exclusionHeight(int topPos, int imageHeight) {
        return panelHeight(topPos, imageHeight) + 5;
    }

    public static int panelX(int leftPos) {
        Minecraft client = Minecraft.getInstance();
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int imageWidth = client.gui.screen() instanceof AbstractContainerScreen<?> screen
                ? screen.imageWidth
                : 176;
        int width = panelWidth(leftPos);
        int right = leftPos + imageWidth + PANEL_GAP;
        int immediateLeft = leftPos - width - PANEL_GAP;
        int leftSpace = leftPos - SCREEN_MARGIN;
        int rightSpace = screenWidth - (leftPos + imageWidth) - SCREEN_MARGIN;

        if (isRecipeBookOpen()) {
            return Math.clamp(right, SCREEN_MARGIN,
                    Math.max(SCREEN_MARGIN, screenWidth - width - SCREEN_MARGIN));
        }

        if (leftSpace >= rightSpace && immediateLeft >= SCREEN_MARGIN) return immediateLeft;
        if (right + width <= screenWidth - SCREEN_MARGIN) return right;
        if (immediateLeft >= SCREEN_MARGIN) return immediateLeft;
        return Math.clamp(immediateLeft, SCREEN_MARGIN,
                Math.max(SCREEN_MARGIN, screenWidth - width - SCREEN_MARGIN));
    }

    public static int panelY(int topPos, int imageHeight) {
        Minecraft client = Minecraft.getInstance();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int height = panelHeight(topPos, imageHeight);
        return Math.clamp(topPos, SCREEN_MARGIN,
                Math.max(SCREEN_MARGIN, screenHeight - height - SCREEN_MARGIN));
    }

    public static int toggleX(int leftPos, int imageWidth) {
        Minecraft client = Minecraft.getInstance();
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int configured = Configs.General.HUD_TOGGLE_POSITION_X.getIntegerValue();
        if (client.gui.screen() instanceof InventoryScreen
                && configured >= 0
                && Configs.General.HUD_TOGGLE_POSITION_Y.getIntegerValue() >= 0) {
            int desired = resolvedConfiguredTogglePosition()[0] + recipeBookShiftX();
            return Math.clamp(desired, SCREEN_MARGIN,
                    Math.max(SCREEN_MARGIN,
                            screenWidth - TOGGLE_WIDTH - SCREEN_MARGIN));
        }
        int desired;
        if (client.gui.screen() instanceof InventoryScreen) {
            desired = leftPos + 130;
        } else {
            desired = leftPos - TOGGLE_WIDTH - 4;
        }
        return Math.clamp(desired, SCREEN_MARGIN,
                Math.max(SCREEN_MARGIN,
                        screenWidth - TOGGLE_WIDTH - SCREEN_MARGIN));
    }

    public static int toggleY(int topPos) {
        Minecraft client = Minecraft.getInstance();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int configured = Configs.General.HUD_TOGGLE_POSITION_Y.getIntegerValue();
        if (client.gui.screen() instanceof InventoryScreen
                && configured >= 0
                && Configs.General.HUD_TOGGLE_POSITION_X.getIntegerValue() >= 0) {
            return resolvedConfiguredTogglePosition()[1];
        }
        int desired = client.gui.screen() instanceof InventoryScreen
                ? topPos + 61
                : topPos + (FabricLoader.getInstance().isModLoaded("better-bundle") ? 27 : 5);
        return Math.clamp(desired, SCREEN_MARGIN,
                Math.max(SCREEN_MARGIN,
                        screenHeight - TOGGLE_HEIGHT - SCREEN_MARGIN));
    }

    private static int[] resolvedConfiguredTogglePosition() {
        Minecraft client = Minecraft.getInstance();
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int availableX = Math.max(0, screenWidth - TOGGLE_WIDTH - SCREEN_MARGIN * 2);
        int availableY = Math.max(0, screenHeight - TOGGLE_HEIGHT - SCREEN_MARGIN * 2);
        int x = SCREEN_MARGIN + (int) Math.round(availableX
                * (Configs.General.HUD_TOGGLE_POSITION_X.getIntegerValue() / 10000.0));
        int y = SCREEN_MARGIN + (int) Math.round(availableY
                * (Configs.General.HUD_TOGGLE_POSITION_Y.getIntegerValue() / 10000.0));
        return new int[]{x, y};
    }

    private static int recipeBookShiftX() {
        Minecraft client = Minecraft.getInstance();
        if (!(client.gui.screen() instanceof InventoryScreen screen)) return 0;
        int centeredLeftPos = (screen.width - screen.imageWidth) / 2;
        return screen.leftPos - centeredLeftPos;
    }

    public static int gridX(int leftPos) {
        return panelX(leftPos) + PADDING + CAT_BAR_WIDTH + CATEGORY_GAP
                + SCROLL_BAR_WIDTH + SCROLL_GAP;
    }

    public static int gridY(int topPos, int imageHeight) {
        return panelY(topPos, imageHeight) + HEADER_HEIGHT + PADDING;
    }

    private static int gridWidth(int leftPos) {
        int columns = columnCount(leftPos);
        return columns * SLOT_SIZE + (columns - 1) * SLOT_SPACING;
    }

    private static int gridHeight(int topPos, int imageHeight) {
        int rows = visibleRowCount(topPos, imageHeight);
        return rows * SLOT_SIZE + (rows - 1) * SLOT_SPACING;
    }

    public static int getScrollOffset() { return scrollOffset; }
    public static void scrollToTop() { scrollOffset = 0; }

    public static void scrollBy(int delta) {
        Minecraft client = Minecraft.getInstance();
        if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) return;
        List<FlatItem> items = getVisibleItems();
        if (items.isEmpty()) { scrollOffset = 0; return; }
        int columns = columnCount(screen.leftPos);
        int rows = visibleRowCount(screen.topPos, screen.imageHeight);
        int totalRows = (items.size() + columns - 1) / columns;
        int maxScroll = Math.max(0, totalRows - rows);
        scrollOffset = Math.clamp(scrollOffset + delta, 0, maxScroll);
    }

    public record ItemSource(
            StorageView storageView, int inventorySlot, int shulkerSlot,
            ItemStack stack, ItemStack containerStack) {
        public ItemSource(int inventorySlot, int shulkerSlot, ItemStack stack) {
            this(StorageView.SHULKERS, inventorySlot, shulkerSlot,
                    stack, ItemStack.EMPTY);
        }
    }

    public record FlatItem(ItemStack stack, List<ItemSource> sources) {
        public int inventorySlot() { return sources.get(0).inventorySlot(); }
        public int shulkerSlot() { return sources.get(0).shulkerSlot(); }
        public StorageView storageView() { return sources.get(0).storageView(); }
    }

    public static List<FlatItem> buildFlatItemList(List<ShulkerSlotEntry> shulkers) {
        return buildFlatItemList(shulkers, true);
    }

    private static List<FlatItem> buildFlatItemList(
            List<ShulkerSlotEntry> shulkers, boolean sortByCount) {
        Map<StackKey, MutableFlatItem> aggregated = new LinkedHashMap<>();
        for (ShulkerSlotEntry entry : shulkers) {
            List<ItemStack> items = entry.contents();
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                if (stack.isEmpty()) continue;

                ItemSource source = new ItemSource(
                        StorageView.SHULKERS, entry.inventorySlot(), i,
                        stack.copy(), entry.shulkerStack().copy());
                StackKey key = new StackKey(stack);
                MutableFlatItem item = aggregated.computeIfAbsent(
                        key, ignored -> new MutableFlatItem(stack.copyWithCount(1)));
                item.total += stack.getCount();
                item.sources.add(source);
            }
        }
        List<FlatItem> result = new ArrayList<>(aggregated.size());
        for (MutableFlatItem item : aggregated.values()) {
            result.add(new FlatItem(
                    item.prototype.copyWithCount(item.total), List.copyOf(item.sources)));
        }
        if (sortByCount) sortFlatItems(result);
        return result;
    }

    private static void sortFlatItems(List<FlatItem> items) {
        items.sort(Comparator
                .comparingInt((FlatItem item) -> item.stack().getCount())
                .reversed()
                .thenComparing(item -> BuiltInRegistries.ITEM
                        .getKey(item.stack().getItem()).toString()));
    }

    private static List<FlatItem> updateFlatItemsKeepingOrder(
            List<FlatItem> previous, List<FlatItem> current) {
        if (previous.isEmpty()) return current;

        Map<StackKey, FlatItem> currentByKey = new LinkedHashMap<>();
        for (FlatItem item : current) {
            currentByKey.put(new StackKey(item.stack()), item);
        }

        List<FlatItem> ordered = new ArrayList<>(current.size());
        Set<StackKey> used = new HashSet<>();
        for (FlatItem previousItem : previous) {
            StackKey key = new StackKey(previousItem.stack());
            FlatItem refreshed = currentByKey.get(key);
            if (refreshed != null && used.add(key)) {
                ordered.add(refreshed);
            }
        }
        for (FlatItem item : current) {
            if (used.add(new StackKey(item.stack()))) {
                ordered.add(item);
            }
        }
        return ordered;
    }

    public static List<FlatItem> getVisibleItems() {
        ensureCache();
        if (BundleCategory.registerCategoryItems()) {
            cachedCategory = null;
        }
        long visibleEnderRevision = storageView == StorageView.ENDER_CHEST
                ? StorageClientNetwork.getEnderRevision() : Long.MIN_VALUE;
        if (cachedCategory != currentCategory
                || cachedStorageView != storageView
                || cachedVisibleEnderRevision != visibleEnderRevision
                || !java.util.Objects.equals(cachedSearchQuery, searchQuery)) {
            cachedVisibleItems = List.copyOf(filterItems(currentFlatItems(), searchQuery));
            cachedCategory = currentCategory;
            cachedStorageView = storageView;
            cachedVisibleEnderRevision = visibleEnderRevision;
            cachedSearchQuery = searchQuery;
        }
        return cachedVisibleItems;
    }

    public static StorageView getStorageView() {
        return storageView;
    }

    public static boolean selectStorageView(StorageView selected) {
        if (selected == null) return false;
        if (!isStorageViewEnabled(selected)) {
            if ((selected == StorageView.ENDER_CHEST
                    && StorageClientNetwork.hasPortableEnderChest())
                    || (selected == StorageView.BUNDLES && hasBundle())) {
                StorageClientNetwork.showServerRequired();
            }
            return false;
        }
        if (storageView == selected) return true;
        storageView = selected;
        scrollOffset = 0;
        searchFocused = false;
        cachedVisibleItems = List.of();
        cachedStorageView = null;
        if (selected == StorageView.ENDER_CHEST) {
            StorageClientNetwork.requestEnderContents();
        }
        return true;
    }

    public static StorageView getStorageViewAt(
            double mouseX, double mouseY,
            int leftPos, int topPos, int imageHeight) {
        if (!isEffectivelyVisible()) return null;
        ensureCache();
        if (!hasAnyPortableStorage()) return null;
        int y = storageTabY(topPos, imageHeight);
        if (mouseY < y || mouseY >= y + STORAGE_TAB_SIZE) return null;
        StorageView[] views = StorageView.values();
        for (int index = 0; index < views.length; index++) {
            int x = storageTabX(leftPos, index);
            if (mouseX >= x && mouseX < x + STORAGE_TAB_SIZE) return views[index];
        }
        return null;
    }

    public static boolean isStorageViewEnabled(StorageView view) {
        if (view == null) return false;
        ensureCache();
        return switch (view) {
            case SHULKERS -> !cachedAllShulkers.isEmpty();
            case ENDER_CHEST -> StorageClientNetwork.isEnderChestAvailable();
            case BUNDLES -> hasBundle() && StorageClientNetwork.hasStorageServer();
        };
    }

    public static int storageTabX(int leftPos, int index) {
        return panelX(leftPos) + BODY_INSET + 4
                + index * (STORAGE_TAB_SIZE + STORAGE_TAB_GAP);
    }

    public static int storageTabY(int topPos, int imageHeight) {
        return panelY(topPos, imageHeight)
                + panelHeight(topPos, imageHeight) - 21;
    }

    private static boolean hasBundle() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return false;
        Inventory inventory = client.player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            if (inventory.getItem(slot).getItem() instanceof BundleItem) return true;
        }
        return false;
    }

    private static boolean hasAnyPortableStorage() {
        return !cachedAllShulkers.isEmpty()
                || hasBundle()
                || StorageClientNetwork.hasPortableEnderChest();
    }

    private static void selectFirstAvailableStorageView() {
        if (isStorageViewEnabled(storageView)) return;
        for (StorageView candidate : StorageView.values()) {
            if (isStorageViewEnabled(candidate)) {
                selectStorageView(candidate);
                return;
            }
        }
    }

    private static List<FlatItem> currentFlatItems() {
        return switch (storageView) {
            case SHULKERS -> cachedFlatItems;
            case BUNDLES -> cachedBundleFlatItems;
            case ENDER_CHEST -> getEnderFlatItems();
        };
    }

    private static List<FlatItem> getEnderFlatItems() {
        long revision = StorageClientNetwork.getEnderRevision();
        if (cachedEnderRevision == revision) return cachedEnderFlatItems;
        Map<StackKey, MutableFlatItem> aggregated = new LinkedHashMap<>();
        List<ItemStack> contents = StorageClientNetwork.getEnderContents();
        for (int slot = 0; slot < contents.size(); slot++) {
            ItemStack stack = contents.get(slot);
            if (stack.isEmpty()) continue;
            addSource(aggregated, new ItemSource(
                    StorageView.ENDER_CHEST, -1, slot, stack.copy(),
                    new ItemStack(Items.ENDER_CHEST)));
        }
        cachedEnderFlatItems = finishSources(aggregated);
        cachedEnderRevision = revision;
        return cachedEnderFlatItems;
    }

    private static List<FlatItem> buildBundleFlatItems(Inventory inventory) {
        Map<StackKey, MutableFlatItem> aggregated = new LinkedHashMap<>();
        for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
            ItemStack bundle = inventory.getItem(inventorySlot);
            if (!(bundle.getItem() instanceof BundleItem)) continue;
            List<ItemStack> contents = bundle.getOrDefault(
                    DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY)
                    .itemCopyStream().toList();
            for (int contentSlot = 0; contentSlot < contents.size(); contentSlot++) {
                ItemStack stack = contents.get(contentSlot);
                if (stack.isEmpty()) continue;
                addSource(aggregated, new ItemSource(
                        StorageView.BUNDLES, inventorySlot, contentSlot,
                        stack.copy(), bundle.copy()));
            }
        }
        return finishSources(aggregated);
    }

    private static void addSource(
            Map<StackKey, MutableFlatItem> aggregated, ItemSource source) {
        StackKey key = new StackKey(source.stack());
        MutableFlatItem item = aggregated.computeIfAbsent(
                key, ignored -> new MutableFlatItem(source.stack().copyWithCount(1)));
        item.total += source.stack().getCount();
        item.sources.add(source);
    }

    private static List<FlatItem> finishSources(
            Map<StackKey, MutableFlatItem> aggregated) {
        List<FlatItem> result = new ArrayList<>(aggregated.size());
        for (MutableFlatItem item : aggregated.values()) {
            result.add(new FlatItem(
                    item.prototype.copyWithCount(item.total), List.copyOf(item.sources)));
        }
        sortFlatItems(result);
        return List.copyOf(result);
    }

    public static List<FlatItem> filterItems(List<FlatItem> items, String query) {
        List<FlatItem> filtered = new ArrayList<>();
        for (FlatItem fi : items) {
            if (currentCategory.matches(fi.stack())) filtered.add(fi);
        }
        if (query.isEmpty()) return filtered;
        String q = query.toLowerCase(Locale.ROOT);
        List<FlatItem> matches = new ArrayList<>();
        for (FlatItem fi : filtered) {
            if (matchesSearch(fi, q)) matches.add(fi);
        }
        return matches;
    }

    private static boolean matchesSearch(FlatItem fi, String q) {
        String name = fi.stack().getDisplayName().getString().toLowerCase(Locale.ROOT);
        if (name.contains(q)) return true;
        if (Configs.Features.PINYIN_SEARCH.getBooleanValue() && PIN_IN != null) {
            try {
                if (PIN_IN.contains(name, q)) return true;
            } catch (RuntimeException ignored) {
                // Fall through to the legacy conversion for unusual text components.
            }
        }
        if (Configs.Features.PINYIN_SEARCH.getBooleanValue() && toPinyin(name).contains(q)) return true;
        var key = BuiltInRegistries.ITEM.getKey(fi.stack().getItem());
        String fullId = key.toString().toLowerCase(Locale.ROOT);
        String path = key.getPath().toLowerCase(Locale.ROOT);
        return fullId.contains(q) || path.contains(q);
    }

    private static PinIn createPinIn() {
        try {
            return new PinIn().config().accelerate(true).commit();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String toPinyin(String text) {
        try {
            HanyuPinyinOutputFormat fmt = new HanyuPinyinOutputFormat();
            fmt.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
            StringBuilder sb = new StringBuilder();
            for (char c : text.toCharArray()) {
                String[] arr = PinyinHelper.toHanyuPinyinStringArray(c, fmt);
                if (arr != null && arr.length > 0) sb.append(arr[0]);
            }
            return sb.toString().toLowerCase(Locale.ROOT);
        } catch (Throwable t) {
            return "";
        }
    }

    public static List<ShulkerSlotEntry> getShulkers() {
        ensureCache();
        return cachedNonEmptyShulkers;
    }

    public static List<ShulkerSlotEntry> getAllShulkers() {
        ensureCache();
        return cachedAllShulkers;
    }

    public static void invalidateCache() {
        cachedPlayer = null;
        cachedScreen = null;
        sortPreparedAfterClose = false;
        cachedInventoryRevision = Integer.MIN_VALUE;
        cachedActiveContentsFingerprint = Long.MIN_VALUE;
        contentsCacheDirty = true;
        cachedAllShulkers = List.of();
        cachedNonEmptyShulkers = List.of();
        cachedFlatItems = List.of();
        cachedVisibleItems = List.of();
        cachedSearchQuery = null;
        cachedCategory = null;
        cachedStorageView = null;
        cachedBundleFlatItems = List.of();
        cachedEnderFlatItems = List.of();
        cachedEnderRevision = Long.MIN_VALUE;
        cachedVisibleEnderRevision = Long.MIN_VALUE;
        hoveredShulkerInventorySlot = -1;
        activeContentsSnapshot = null;
    }

    /**
     * Captures the next count-based order while the closing inventory is still
     * available. The current screen never sees this reordered list.
     */
    public static void prepareSortAfterContainerClose() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        ensureCache();
        if (!cachedFlatItems.isEmpty()) {
            List<FlatItem> sorted = new ArrayList<>(cachedFlatItems);
            sortFlatItems(sorted);
            cachedFlatItems = List.copyOf(sorted);
        }
        cachedScreen = null;
        sortPreparedAfterClose = true;
        cachedVisibleItems = List.of();
        cachedSearchQuery = null;
        cachedCategory = null;
        cachedStorageView = null;
    }

    /**
     * Keeps the current HUD order across an internal QuickShulker/AxShulkers
     * container transition. Counts and sources are refreshed for the next
     * inventory screen, but the item under the cursor must not move while a
     * continuous extraction is still in progress.
     */
    public static void prepareOrderAfterTransientContainerClose() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        ensureCache();
        cachedScreen = null;
        sortPreparedAfterClose = true;
        cachedVisibleItems = List.of();
        cachedSearchQuery = null;
        cachedCategory = null;
        cachedStorageView = null;
    }

    /**
     * Refreshes inventory contents without discarding the order captured when
     * the current container screen was opened.
     */
    public static void invalidateContentsCache() {
        contentsCacheDirty = true;
        cachedVisibleItems = List.of();
        cachedSearchQuery = null;
        cachedCategory = null;
        cachedStorageView = null;
    }

    private static void ensureCache() {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null) {
            invalidateCache();
            return;
        }
        Inventory inv = player.getInventory();
        QuickShulkerExtractionController.ActiveShulkerContents liveContents =
                QuickShulkerExtractionController.getActiveShulkerContents();
        if (liveContents != null) {
            activeContentsSnapshot = new ActiveContentsSnapshot(
                    liveContents.inventorySlot(), liveContents.shulkerStack().copy(),
                    copyStacks(liveContents.contents()), player.tickCount + 12);
        } else if (activeContentsSnapshot != null
                && player.tickCount > activeContentsSnapshot.expiresAtTick()) {
            activeContentsSnapshot = null;
        }
        ActiveContentsSnapshot contentsOverride = activeContentsSnapshot;

        int inventoryRevision = inv.getTimesChanged();
        long activeContentsFingerprint = activeContentsFingerprint(contentsOverride);
        boolean newContainerScreen = cachedPlayer != player || cachedScreen != client.gui.screen();
        if (!newContainerScreen && !contentsCacheDirty
                && cachedInventoryRevision == inventoryRevision
                && cachedActiveContentsFingerprint == activeContentsFingerprint) return;

        List<ShulkerSlotEntry> all = new ArrayList<>();
        List<ShulkerSlotEntry> nonEmpty = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            List<ItemStack> contents;
            if (contentsOverride != null && contentsOverride.inventorySlot() == i
                    && (stack.isEmpty() || ShulkerContentsHelper.isShulker(stack))) {
                if (stack.isEmpty()) stack = contentsOverride.shulkerStack();
                contents = contentsOverride.contents();
            } else {
                if (!ShulkerContentsHelper.isShulker(stack)) continue;
                contents = copyStacks(ShulkerContentsHelper.getStacks(stack));
            }
            if (!ShulkerContentsHelper.isShulker(stack)) continue;
            ShulkerSlotEntry entry = new ShulkerSlotEntry(i, stack.copy(), contents);
            all.add(entry);
            if (contents.stream().anyMatch(item -> !item.isEmpty())) nonEmpty.add(entry);
        }
        boolean usePreparedOrder = newContainerScreen
                && sortPreparedAfterClose && !cachedFlatItems.isEmpty();
        List<FlatItem> rebuiltFlatItems = buildFlatItemList(
                nonEmpty, newContainerScreen && !usePreparedOrder);
        if ((newContainerScreen && !usePreparedOrder) || cachedFlatItems.isEmpty()) {
            cachedFlatItems = List.copyOf(rebuiltFlatItems);
        } else {
            cachedFlatItems = List.copyOf(
                    updateFlatItemsKeepingOrder(cachedFlatItems, rebuiltFlatItems));
        }
        cachedPlayer = player;
        cachedScreen = client.gui.screen();
        cachedInventoryRevision = inventoryRevision;
        cachedActiveContentsFingerprint = activeContentsFingerprint;
        contentsCacheDirty = false;
        cachedAllShulkers = List.copyOf(all);
        cachedNonEmptyShulkers = List.copyOf(nonEmpty);
        cachedBundleFlatItems = buildBundleFlatItems(inv);
        cachedVisibleItems = List.of();
        cachedSearchQuery = null;
        cachedCategory = null;
        cachedStorageView = null;
        if (newContainerScreen) sortPreparedAfterClose = false;
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        List<ItemStack> copy = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) copy.add(stack.copy());
        return List.copyOf(copy);
    }

    private static long activeContentsFingerprint(ActiveContentsSnapshot snapshot) {
        if (snapshot == null) return Long.MIN_VALUE;
        long fingerprint = 31L + snapshot.inventorySlot();
        for (ItemStack stack : snapshot.contents()) {
            fingerprint = 31L * fingerprint + ItemStack.hashItemAndComponents(stack);
            fingerprint = 31L * fingerprint + stack.getCount();
        }
        return fingerprint;
    }

    private record ActiveContentsSnapshot(
            int inventorySlot, ItemStack shulkerStack,
            List<ItemStack> contents, int expiresAtTick) {}

    private static final class StackKey {
        private final ItemStack prototype;
        private final int hash;

        private StackKey(ItemStack stack) {
            this.prototype = stack.copyWithCount(1);
            this.hash = ItemStack.hashItemAndComponents(stack);
        }

        @Override
        public int hashCode() { return hash; }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof StackKey other
                    && ItemStack.isSameItemSameComponents(prototype, other.prototype);
        }
    }

    private static final class MutableFlatItem {
        private final ItemStack prototype;
        private final List<ItemSource> sources = new ArrayList<>();
        private int total;

        private MutableFlatItem(ItemStack prototype) {
            this.prototype = prototype;
        }
    }

    public static boolean isRecipeBookOpen() {
        Minecraft client = Minecraft.getInstance();
        if (client.gui.screen() instanceof AbstractRecipeBookScreen<?> screen) return screen.recipeBookComponent.isVisible();
        return false;
    }

    public static int getHoveredShulkerInventorySlot() {
        return Configs.Features.HUD_ENABLED.getBooleanValue()
                ? hoveredShulkerInventorySlot : -1;
    }
    public static boolean isEffectivelyVisible() {
        return Configs.Features.HUD_ENABLED.getBooleanValue()
                && !isCreativeInventoryScreen();
    }

    public static boolean hasRenderablePanel() {
        if (!isEffectivelyVisible()) return false;
        ensureCache();
        return hasAnyPortableStorage();
    }

    public static void toggleVisible() {
        Configs.Features.HUD_ENABLED.setBooleanValue(
                !Configs.Features.HUD_ENABLED.getBooleanValue());
        Configs.saveToFile();
    }

    public static boolean shouldShowToggleButton() {
        return Configs.Features.SHOW_HUD_TOGGLE_BUTTON.getBooleanValue()
                && Minecraft.getInstance().gui.screen() instanceof InventoryScreen;
    }

    private static boolean isCreativeInventoryScreen() {
        return Minecraft.getInstance().gui.screen() instanceof
                net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
    }

    public static boolean isToggleButtonHovered(
            double mouseX, double mouseY, int leftPos, int topPos, int imageWidth) {
        if (!shouldShowToggleButton()) return false;
        int x = toggleX(leftPos, imageWidth);
        int y = toggleY(topPos);
        return mouseX >= x && mouseX < x + TOGGLE_WIDTH
                && mouseY >= y && mouseY < y + TOGGLE_HEIGHT;
    }

    public static boolean handleToggleButtonClick(
            double mouseX, double mouseY, int button,
            int leftPos, int topPos, int imageWidth) {
        if (!isToggleButtonHovered(mouseX, mouseY, leftPos, topPos, imageWidth)) {
            return false;
        }
        playButtonClick();
        if (Configs.General.HUD_TOGGLE_POSITION_EDIT.getBooleanValue()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                togglePositionAdjustMode = true;
                toggleButtonDragging = true;
                toggleButtonDragOffsetX = mouseX - toggleX(leftPos, imageWidth);
                toggleButtonDragOffsetY = mouseY - toggleY(topPos);
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) toggleVisible();
        return true;
    }

    public static boolean handleToggleButtonDrag(double mouseX, double mouseY, int button) {
        if (!togglePositionAdjustMode || !toggleButtonDragging
                || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        setToggleButtonPosition(
                mouseX - toggleButtonDragOffsetX,
                mouseY - toggleButtonDragOffsetY);
        return true;
    }

    public static boolean handleToggleButtonRelease(double mouseX, double mouseY, int button) {
        if (!togglePositionAdjustMode || !toggleButtonDragging
                || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        toggleButtonDragging = false;
        setToggleButtonPosition(
                mouseX - toggleButtonDragOffsetX,
                mouseY - toggleButtonDragOffsetY);
        Configs.saveToFile();
        return true;
    }

    public static void resetToggleButtonInteraction() {
        toggleButtonDragging = false;
        togglePositionAdjustMode = false;
    }

    public static boolean isTogglePositionEditEnabled() {
        return togglePositionAdjustMode;
    }

    public static boolean isTogglePositionAdjustMode() {
        return togglePositionAdjustMode;
    }

    /** Handles all clicks while the adjust overlay is active on the real inventory screen. */
    public static boolean handleAdjustModeClick(
            double mouseX, double mouseY, int button,
            int leftPos, int topPos, int imageWidth) {
        if (!togglePositionAdjustMode) return false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int[] done = adjustDoneRect();
            int[] reset = adjustResetRect();
            if (mouseX >= done[0] && mouseX < done[0] + done[2]
                    && mouseY >= done[1] && mouseY < done[1] + done[3]) {
                togglePositionAdjustMode = false;
                Configs.saveToFile();
                playButtonClick();
                return true;
            }
            if (mouseX >= reset[0] && mouseX < reset[0] + reset[2]
                    && mouseY >= reset[1] && mouseY < reset[1] + reset[3]) {
                Configs.General.HUD_TOGGLE_POSITION_X.setIntegerValue(-1);
                Configs.General.HUD_TOGGLE_POSITION_Y.setIntegerValue(-1);
                Configs.saveToFile();
                playButtonClick();
                return true;
            }
            if (isToggleButtonHovered(mouseX, mouseY, leftPos, topPos, imageWidth)) {
                toggleButtonDragging = true;
                toggleButtonDragOffsetX = mouseX - toggleX(leftPos, imageWidth);
                toggleButtonDragOffsetY = mouseY - toggleY(topPos);
                return true;
            }
        }
        // While adjusting, consume all clicks so the real inventory is never modified.
        return true;
    }

    public static int[] adjustDoneRect() {
        Minecraft client = Minecraft.getInstance();
        int w = client.getWindow().getGuiScaledWidth();
        int startX = Math.max(SCREEN_MARGIN, (w - 128) / 2);
        return new int[]{startX + 80, 9, 48, 20};
    }

    public static int[] adjustResetRect() {
        Minecraft client = Minecraft.getInstance();
        int w = client.getWindow().getGuiScaledWidth();
        int startX = Math.max(SCREEN_MARGIN, (w - 128) / 2);
        return new int[]{startX, 9, 72, 20};
    }

    private static void setToggleButtonPosition(double desiredX, double desiredY) {
        Minecraft client = Minecraft.getInstance();
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int availableX = Math.max(0, screenWidth - TOGGLE_WIDTH - SCREEN_MARGIN * 2);
        int availableY = Math.max(0, screenHeight - TOGGLE_HEIGHT - SCREEN_MARGIN * 2);
        double baseDesiredX = desiredX - recipeBookShiftX();
        double x = Math.clamp(baseDesiredX, SCREEN_MARGIN, SCREEN_MARGIN + availableX);
        double y = Math.clamp(desiredY, SCREEN_MARGIN, SCREEN_MARGIN + availableY);
        Configs.General.HUD_TOGGLE_POSITION_X.setIntegerValue(
                availableX == 0 ? 0 : (int) Math.round((x - SCREEN_MARGIN) * 10000.0 / availableX));
        Configs.General.HUD_TOGGLE_POSITION_Y.setIntegerValue(
                availableY == 0 ? 0 : (int) Math.round((y - SCREEN_MARGIN) * 10000.0 / availableY));
    }

    public static void renderAdjustControls(
            net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!togglePositionAdjustMode) return;
        int[] done = adjustDoneRect();
        int[] reset = adjustResetRect();
        boolean doneHover = mouseX >= done[0] && mouseX < done[0] + done[2]
                && mouseY >= done[1] && mouseY < done[1] + done[3];
        boolean resetHover = mouseX >= reset[0] && mouseX < reset[0] + reset[2]
                && mouseY >= reset[1] && mouseY < reset[1] + reset[3];
        var font = Minecraft.getInstance().font;
        drawVanillaButton(graphics, reset[0], reset[1], reset[2], reset[3],
                resetHover, true);
        graphics.text(font, "Reset",
                reset[0] + (reset[2] - font.width("Reset")) / 2, reset[1] + 6, 0xFF404040);
        drawVanillaButton(graphics, done[0], done[1], done[2], done[3],
                doneHover, true);
        graphics.text(font, "Done",
                done[0] + (done[2] - font.width("Done")) / 2, done[1] + 6, 0xFF404040);
    }
    public static void playButtonClick() {
        Minecraft client = Minecraft.getInstance();
        client.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // --- category button layout ---

    private static int catButtonHeight(int panelHeight) {
        int available = Math.max(0, panelHeight - 4);
        return Math.max(1, Math.min(
                CAT_BUTTON_SIZE, available / BundleCategory.values().length));
    }

    /** Shared button layout: returns Y position of category button i. */
    private static int catButtonY(int i, int panelY, int panelHeight) {
        return panelY + 2 + i * catButtonHeight(panelHeight);
    }

    public static BundleCategory getCategoryAt(double mouseX, double mouseY, int leftPos, int topPos, int imageHeight) {
        if (!hasRenderablePanel()) return null;
        int panelHeight = panelHeight(topPos, imageHeight);
        int panelX = panelX(leftPos);
        int baseCatX = panelX;
        int panelY = panelY(topPos, imageHeight);

        BundleCategory[] cats = BundleCategory.values();
        int buttonHeight = catButtonHeight(panelHeight);
        for (int i = 0; i < cats.length; i++) {
            int by = catButtonY(i, panelY, panelHeight);
            if (by + buttonHeight > panelY + panelHeight - 2) break;
            int bx = baseCatX;
            int bw = CAT_BAR_WIDTH;
            if (cats[i] == currentCategory) { bx -= 3; bw += 4; }
            if (mouseX >= bx && mouseX < bx + bw
                    && mouseY >= by && mouseY < by + buttonHeight) {
                return cats[i];
            }
        }
        return null;
    }

    // --- search ---

    public static boolean isInsideSearchBar(double mouseX, double mouseY, int leftPos, int topPos, int imageHeight) {
        if (!hasRenderablePanel()) return false;
        int sbx = searchBarX(leftPos);
        int sby = panelY(topPos, imageHeight) + 3;
        int sbw = searchBarWidth(leftPos);
        return mouseX >= sbx && mouseX <= sbx + sbw && mouseY >= sby && mouseY <= sby + SEARCH_BAR_HEIGHT;
    }

    public static boolean isInsidePanelBounds(
            double mouseX, double mouseY, int leftPos, int topPos, int imageHeight) {
        if (!hasRenderablePanel()) return false;
        int x = panelX(leftPos) + BODY_INSET;
        int y = panelY(topPos, imageHeight);
        return mouseX >= x && mouseX < panelX(leftPos) + panelWidth(leftPos)
                && mouseY >= y && mouseY < y + panelHeight(topPos, imageHeight);
    }

    private static int headerActionButtonX(int leftPos) {
        return panelX(leftPos) + panelWidth(leftPos) - CONTROL_SIZE - 4;
    }

    private static int searchBarX(int leftPos) {
        return panelX(leftPos) + BODY_INSET + 4;
    }

    private static int searchBarWidth(int leftPos) {
        int right = headerActionButtonX(leftPos) - 4;
        return Math.max(1, right - searchBarX(leftPos));
    }

    public static boolean onCharTyped(int codepoint) {
        if (!searchFocused) return false;
        if (Character.isValidCodePoint(codepoint)
                && !Character.isISOControl(codepoint)) {
            searchQuery += new String(Character.toChars(codepoint));
            scrollOffset = 0;
        }
        return true;
    }

    public static boolean onSearchKeyPress(int key, int modifiers) {
        if (!searchFocused) return false;
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!searchQuery.isEmpty()) { searchQuery = searchQuery.substring(0, searchQuery.length() - 1); scrollOffset = 0; }
            return true;
        } else if (key == GLFW.GLFW_KEY_ESCAPE) {
            searchQuery = ""; searchFocused = false; scrollOffset = 0;
            return true;
        } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            searchFocused = false;
            return true;
        }

        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (control && key == GLFW.GLFW_KEY_V) {
            appendSearchText(Minecraft.getInstance().keyboardHandler.getClipboard());
            return true;
        }

        if ((control && key == GLFW.GLFW_KEY_SPACE)
                || key == GLFW.GLFW_KEY_LEFT_SHIFT
                || key == GLFW.GLFW_KEY_RIGHT_SHIFT
                || key == GLFW.GLFW_KEY_LEFT_CONTROL
                || key == GLFW.GLFW_KEY_RIGHT_CONTROL
                || key == GLFW.GLFW_KEY_LEFT_ALT
                || key == GLFW.GLFW_KEY_RIGHT_ALT
                || key == GLFW.GLFW_KEY_CAPS_LOCK) {
            return false;
        }
        return true;
    }

    private static void appendSearchText(String text) {
        if (text == null || text.isEmpty()) return;
        StringBuilder accepted = new StringBuilder();
        text.codePoints()
                .filter(codepoint -> !Character.isISOControl(codepoint))
                .forEach(accepted::appendCodePoint);
        if (!accepted.isEmpty()) {
            searchQuery += accepted;
            scrollOffset = 0;
        }
    }

    // --- render ---

    public static void render(GuiGraphicsExtractor graphics, int leftPos, int topPos, int imageHeight, int mouseX, int mouseY) {
        if (!hasRenderablePanel()) { scrollOffset = 0; return; }
        selectFirstAvailableStorageView();
        if (storageView == StorageView.ENDER_CHEST
                && !StorageClientNetwork.isEnderLoaded()
                && !StorageClientNetwork.isEnderRequestPending()) {
            StorageClientNetwork.requestEnderContents();
        }
        List<FlatItem> items = getVisibleItems();
        if (items.isEmpty()) scrollOffset = 0;

        int pw = panelWidth(leftPos);
        int panelX = panelX(leftPos);
        int panelY = panelY(topPos, imageHeight);

        int columns = columnCount(leftPos);
        int rows = visibleRowCount(topPos, imageHeight);
        int panelHeight = panelHeight(topPos, imageHeight);
        int bodyX = panelX + BODY_INSET;
        int bodyWidth = pw - BODY_INSET;
        int gridX = gridX(leftPos);
        int gridY = gridY(topPos, imageHeight);
        int gridWidth = gridWidth(leftPos);
        int gridHeight = gridHeight(topPos, imageHeight);

        drawFrame(graphics, bodyX, panelY, bodyWidth, panelHeight, COLOR_PANEL);
        drawInsetFrame(graphics, gridX - 2, gridY - 2,
                gridWidth + 4, gridHeight + 4, COLOR_PANEL);
        graphics.fill(bodyX + 3, panelY + HEADER_HEIGHT - 1,
                panelX + pw - 3, panelY + HEADER_HEIGHT, 0x558B8B8B);
        graphics.fill(bodyX + 3, gridY + gridHeight + PADDING,
                panelX + pw - 3, gridY + gridHeight + PADDING + 1, 0x558B8B8B);

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        ItemStack carried = client.gui.screen() instanceof AbstractContainerScreen<?> screen
                ? screen.getMenu().getCarried() : ItemStack.EMPTY;
        boolean dropHovered = !carried.isEmpty()
                && isInsidePanelBounds(mouseX, mouseY, leftPos, topPos, imageHeight);
        if (!carried.isEmpty()) {
            int color = dropHovered ? 0x505CBA79 : 0x303E789B;
            int border = dropHovered ? 0xFFD9F4DF : 0xFFDCEAF4;
            fillRoundedRect(graphics, bodyX + 1, panelY + 1,
                    bodyWidth - 2, panelHeight - 2, color);
            drawRoundedOutline(graphics, bodyX, panelY, bodyWidth, panelHeight, border);
        }

        int totalRows = Math.max(1, (items.size() + columns - 1) / columns);
        int maxScroll = Math.max(0, totalRows - rows);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        // Category tabs overlap the panel edge, matching the framed inventory style.
        BundleCategory[] cats = BundleCategory.values();
        int catX = panelX;
        int catHeight = catButtonHeight(panelHeight);

        for (int i = 0; i < cats.length; i++) {
            int by = catButtonY(i, panelY, panelHeight);
            if (by + catHeight > panelY + panelHeight - 2) break;

            boolean selected = cats[i] == currentCategory;
            int bx = catX;
            int bw = CAT_BAR_WIDTH;
            if (selected) { bx -= 3; bw += 4; }
            boolean hovered = mouseX >= bx && mouseX < bx + bw
                    && mouseY >= by && mouseY < by + catHeight;
            drawVanillaButton(graphics, bx, by, bw, catHeight, hovered, !selected);
            renderScaledCategoryIcon(
                    graphics, cats[i].getIcon(), bx, by, CAT_BAR_WIDTH, catHeight);
        }

        // Scroll bar
        int sbX = gridX - SCROLL_BAR_WIDTH - SCROLL_GAP;
        int sbY = gridY;
        int sbH = gridHeight;

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_BACKGROUND_SPRITE,
                sbX, sbY, SCROLL_BAR_WIDTH, sbH);
        int thumbH = Math.min(15, sbH);
        if (maxScroll > 0) {
            int thumbY = sbY + (sbH - thumbH) * scrollOffset / maxScroll;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_SPRITE,
                    sbX, thumbY, SCROLL_BAR_WIDTH, thumbH);
        } else {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_DISABLED_SPRITE,
                    sbX, sbY, SCROLL_BAR_WIDTH, thumbH);
        }

        int startRow = scrollOffset;
        int hoveredFlatIndex = -1;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                int flatIndex = (startRow + row) * columns + col;
                int sx = gridX + col * (SLOT_SIZE + SLOT_SPACING);
                int sy = gridY + row * (SLOT_SIZE + SLOT_SPACING);

                boolean hovered = mouseX >= sx && mouseX < sx + SLOT_SIZE
                        && mouseY >= sy && mouseY < sy + SLOT_SIZE;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE,
                        sx, sy, SLOT_SIZE, SLOT_SIZE);
                if (hovered) {
                    graphics.fill(sx + 1, sy + 1,
                            sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0x80FFFFFF);
                }

                if (flatIndex >= items.size()) continue;
                FlatItem fi = items.get(flatIndex);
                graphics.item(fi.stack(), sx + 1, sy + 1);
                renderItemDecorations(graphics, client.font, fi.stack(), sx + 1, sy + 1);

                if (hovered) {
                    hoveredFlatIndex = flatIndex;
                }
            }
        }

        if (hoveredFlatIndex >= 0) {
            int hRow = hoveredFlatIndex / columns - startRow;
            int hCol = hoveredFlatIndex % columns;
            int hx = gridX + hCol * (SLOT_SIZE + SLOT_SPACING);
            int hy = gridY + hRow * (SLOT_SIZE + SLOT_SPACING);
            graphics.setTooltipForNextFrame(client.font, items.get(hoveredFlatIndex).stack(), mouseX, mouseY);
            hoveredShulkerInventorySlot = items.get(hoveredFlatIndex).inventorySlot();
        } else {
            hoveredShulkerInventorySlot = -1;
        }

        // Framed search/title area.
        {
            int sbx = searchBarX(leftPos);
            int sby = panelY + 3;
            int sbw = searchBarWidth(leftPos);
            boolean active = searchFocused;
            drawInputField(graphics, sbx, sby, sbw, SEARCH_BAR_HEIGHT, active);
            int textY = sby + (SEARCH_BAR_HEIGHT - font.lineHeight) / 2;
            if (searchQuery.isEmpty() && !searchFocused) {
                String placeholder = Component.translatable(
                        "message.better-shulker-hud.search").getString();
                graphics.text(font, fitTail(font, placeholder, sbw - 7),
                        sbx + 4, textY, COLOR_TEXT_MUTED, false);
            } else if (searchQuery.isEmpty()) {
                searchCursorTick = (searchCursorTick + 1) % 40;
                if (searchCursorTick < 20) {
                    graphics.fill(sbx + 4, textY, sbx + 5,
                            textY + font.lineHeight, COLOR_TEXT);
                }
            } else if (!searchQuery.isEmpty()) {
                String shown = fitTail(font, searchQuery, sbw - 8);
                graphics.text(font, shown, sbx + 4, textY, COLOR_TEXT, false);
                searchCursorTick = (searchCursorTick + 1) % 40;
                if (searchFocused && searchCursorTick < 20) {
                    int cursorX = sbx + 4 + font.width(shown);
                    graphics.fill(cursorX, textY, cursorX + 1, textY + font.lineHeight, COLOR_TEXT);
                }
            }
        }

        // Capacity display in the footer.
        int[] stats = getStorageStats();
        String countText = fitTail(font, stats[0] + " / " + stats[1], gridWidth);
        int textW = font.width(countText);
        int countX = gridX + gridWidth - textW;
        int countY = panelY + panelHeight - 17;
        graphics.text(font, countText, countX, countY, COLOR_TEXT_MUTED, false);

        int returnX = returnButtonX(leftPos);
        int returnY = returnButtonY(topPos, imageHeight);
        boolean returnHovered = isReturnButtonHovered(
                mouseX, mouseY, leftPos, topPos, imageHeight);
        boolean canReturn = storageView == StorageView.SHULKERS
                && QuickShulkerExtractionController.canOrganizeInventory();
        drawVanillaButton(graphics, returnX, returnY, CONTROL_SIZE, CONTROL_SIZE,
                returnHovered, canReturn);
        renderScaledCategoryIcon(graphics, new ItemStack(Items.HOPPER),
                returnX, returnY, CONTROL_SIZE, CONTROL_SIZE);
        if (returnHovered) {
            graphics.setTooltipForNextFrame(client.font,
                    Component.translatable("message.better-shulker-hud.return_button"),
                    mouseX, mouseY);
        }

        StorageView hoveredStorage = null;
        StorageView[] storageViews = StorageView.values();
        for (int index = 0; index < storageViews.length; index++) {
            StorageView view = storageViews[index];
            int tabX = storageTabX(leftPos, index);
            int tabY = storageTabY(topPos, imageHeight);
            boolean hovered = mouseX >= tabX && mouseX < tabX + STORAGE_TAB_SIZE
                    && mouseY >= tabY && mouseY < tabY + STORAGE_TAB_SIZE;
            boolean enabled = isStorageViewEnabled(view);
            drawVanillaButton(graphics, tabX, tabY,
                    STORAGE_TAB_SIZE, STORAGE_TAB_SIZE, hovered, enabled);
            renderScaledCategoryIcon(graphics, view.icon(), tabX, tabY,
                    STORAGE_TAB_SIZE, STORAGE_TAB_SIZE);
            if (view == storageView) {
                drawRoundedOutline(graphics, tabX - 1, tabY - 1,
                        STORAGE_TAB_SIZE + 2, STORAGE_TAB_SIZE + 2, 0xFFFFFFFF);
            }
            if (hovered) hoveredStorage = view;
        }

        if (hoveredStorage != null) {
            graphics.setTooltipForNextFrame(client.font,
                    storageViewTooltip(hoveredStorage), mouseX, mouseY);
        } else if (dropHovered) {
            graphics.setTooltipForNextFrame(client.font,
                    Component.translatable("message.better-shulker-hud.store_drop_target"),
                    mouseX, mouseY);
        }

    }

    public static void renderToggleButton(
            GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        if (!shouldShowToggleButton()) return;
        boolean hovered = mouseX >= x && mouseX < x + TOGGLE_WIDTH
                && mouseY >= y && mouseY < y + TOGGLE_HEIGHT;
        drawVanillaButton(graphics, x, y, TOGGLE_WIDTH, TOGGLE_HEIGHT, hovered, true);
        graphics.item(new ItemStack(Items.SHULKER_BOX), x + 2, y + 1);
        if (isTogglePositionEditEnabled()) {
            drawRoundedOutline(graphics, x - 1, y - 1,
                    TOGGLE_WIDTH + 2, TOGGLE_HEIGHT + 2, 0xFF2ECC40);
        }
    }

    public static void renderCursorTransferPreview(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Minecraft client = Minecraft.getInstance();
        if (!(client.gui.screen() instanceof InventoryScreen screen)
                || !screen.getMenu().getCarried().isEmpty()) return;
        ItemStack preview = QuickShulkerExtractionController.getCursorTransferPreview();
        if (preview.isEmpty()) return;
        int x = mouseX - 8;
        int y = mouseY - 8;
        graphics.item(preview, x, y);
        renderItemDecorations(graphics, client.font, preview, x, y);
    }

    private static void renderScaledCategoryIcon(
            GuiGraphicsExtractor graphics, ItemStack icon,
            int x, int y, int width, int height) {
        int iconSize = Math.max(1, Math.min(16, Math.min(width - 2, height - 2)));
        float scale = iconSize / 16.0F;
        float drawX = x + (width - iconSize) / 2.0F;
        float drawY = y + (height - iconSize) / 2.0F;
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(drawX, drawY);
        pose.scale(scale, scale);
        graphics.item(icon, 0, 0);
        pose.popMatrix();
    }

    private static void drawFrame(
            GuiGraphicsExtractor graphics, int x, int y, int width, int height, int fill) {
        fillRoundedRect(graphics, x + 2, y + 3, width, height, COLOR_SHADOW);
        fillRoundedRect(graphics, x, y, width, height, COLOR_BORDER_DARK);
        fillRoundedRect(graphics, x + 1, y + 1, width - 2, height - 2, fill);
        graphics.fill(x + 3, y + 1, x + width - 3, y + 2, COLOR_BORDER_LIGHT);
        graphics.fill(x + 1, y + 3, x + 2, y + height - 3, COLOR_BORDER_LIGHT);
        graphics.fill(x + 3, y + height - 2,
                x + width - 3, y + height - 1, COLOR_BORDER_MID);
        graphics.fill(x + width - 2, y + 3,
                x + width - 1, y + height - 3, COLOR_BORDER_MID);
    }

    private static void drawInsetFrame(
            GuiGraphicsExtractor graphics, int x, int y, int width, int height, int fill) {
        fillRoundedRect(graphics, x, y, width, height, COLOR_BORDER_DARK);
        fillRoundedRect(graphics, x + 1, y + 1, width - 2, height - 2, fill);
        graphics.fill(x + 3, y + 1, x + width - 3, y + 2, COLOR_BORDER_MID);
        graphics.fill(x + 1, y + 3, x + 2, y + height - 3, COLOR_BORDER_MID);
        graphics.fill(x + 3, y + height - 2,
                x + width - 3, y + height - 1, COLOR_BORDER_LIGHT);
        graphics.fill(x + width - 2, y + 3,
                x + width - 1, y + height - 3, COLOR_BORDER_LIGHT);
    }

    private static void drawVanillaButton(
            GuiGraphicsExtractor graphics, int x, int y, int width, int height,
            boolean hovered, boolean active) {
        if (usesCozyUiSprites()) {
            Identifier sprite = !active ? BUTTON_DISABLED_SPRITE
                    : hovered ? BUTTON_HIGHLIGHTED_SPRITE : BUTTON_SPRITE;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height);
            return;
        }
        int fill = !active ? COLOR_BUTTON_DISABLED
                : hovered ? COLOR_BUTTON_HOVER : COLOR_PANEL;
        fillRoundedRect(graphics, x + 1, y + 2, width, height, 0x50000000);
        fillRoundedRect(graphics, x, y, width, height, COLOR_BORDER_DARK);
        fillRoundedRect(graphics, x + 1, y + 1, width - 2, height - 2, fill);
        if (active) {
            graphics.fill(x + 3, y + 1, x + width - 3, y + 2, COLOR_BORDER_LIGHT);
            graphics.fill(x + 1, y + 3, x + 2, y + height - 3, COLOR_BORDER_LIGHT);
        }
    }

    private static void drawInputField(
            GuiGraphicsExtractor graphics, int x, int y, int width, int height,
            boolean focused) {
        if (usesCozyUiSprites()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    focused ? TEXT_FIELD_HIGHLIGHTED_SPRITE : TEXT_FIELD_SPRITE,
                    x, y, width, height);
            return;
        }
        int outline = focused ? COLOR_BORDER_LIGHT : COLOR_BORDER_DARK;
        fillRoundedRect(graphics, x + 1, y + 2, width, height, 0x40000000);
        fillRoundedRect(graphics, x, y, width, height, outline);
        fillRoundedRect(graphics, x + 1, y + 1,
                width - 2, height - 2, COLOR_INPUT);
        if (!focused) {
            graphics.fill(x + 3, y + 1, x + width - 3, y + 2, COLOR_BORDER_MID);
            graphics.fill(x + 1, y + 3, x + 2, y + height - 3, COLOR_BORDER_MID);
        }
    }

    private static boolean usesCozyUiSprites() {
        var resourceManager = Minecraft.getInstance().getResourceManager();
        return resourceManager.getResource(BUTTON_TEXTURE)
                .map(resource -> isCozyUiPackId(resource.sourcePackId()))
                .orElse(false)
                || resourceManager.getResource(TEXT_FIELD_TEXTURE)
                .map(resource -> isCozyUiPackId(resource.sourcePackId()))
                .orElse(false);
    }

    private static boolean isCozyUiPackId(String packId) {
        return packId != null
                && packId.toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "")
                .contains("cozyui");
    }

    private static void fillRoundedRect(
            GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        if (width <= 4 || height <= 4) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        graphics.fill(x + 2, y, x + width - 2, y + height, color);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, color);
        graphics.fill(x, y + 2, x + width, y + height - 2, color);
    }

    private static void drawRoundedOutline(
            GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        if (width <= 4 || height <= 4) {
            graphics.fill(x, y, x + width, y + 1, color);
            graphics.fill(x, y + height - 1, x + width, y + height, color);
            graphics.fill(x, y, x + 1, y + height, color);
            graphics.fill(x + width - 1, y, x + width, y + height, color);
            return;
        }
        graphics.fill(x + 2, y, x + width - 2, y + 1, color);
        graphics.fill(x + 2, y + height - 1, x + width - 2, y + height, color);
        graphics.fill(x, y + 2, x + 1, y + height - 2, color);
        graphics.fill(x + width - 1, y + 2, x + width, y + height - 2, color);
        graphics.fill(x + 1, y + 1, x + 2, y + 2, color);
        graphics.fill(x + width - 2, y + 1, x + width - 1, y + 2, color);
        graphics.fill(x + 1, y + height - 2, x + 2, y + height - 1, color);
        graphics.fill(x + width - 2, y + height - 2,
                x + width - 1, y + height - 1, color);
    }

    private static String fitTail(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        int start = 0;
        while (start < text.length() && font.width(text.substring(start)) > maxWidth) {
            int codePoint = text.codePointAt(start);
            start += Character.charCount(codePoint);
        }
        return text.substring(Math.min(start, text.length()));
    }

    private static void renderItemDecorations(
            GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y) {
        ItemStack vanillaDecorations = stack.getCount() > 1
                ? stack.copyWithCount(1)
                : stack;
        graphics.itemDecorations(font, vanillaDecorations, x, y);
        if (stack.getCount() <= 1) return;

        String countText = Integer.toString(stack.getCount());
        int textWidth = Math.max(1, font.width(countText));
        float scale = Math.min(1.0F, 14.0F / textWidth);
        float drawX = x + 16.0F - textWidth * scale;
        float drawY = y + 16.0F - font.lineHeight * scale;

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(drawX, drawY);
        pose.scale(scale, scale);
        graphics.text(font, countText, 0, 0, 0xFFFFFFFF, true);
        pose.popMatrix();
    }

    public static int returnButtonX(int leftPos) {
        return headerActionButtonX(leftPos);
    }

    public static int returnButtonY(int topPos, int imageHeight) {
        return panelY(topPos, imageHeight) + 4;
    }

    public static boolean isReturnButtonHovered(
            double mouseX, double mouseY, int leftPos, int topPos, int imageHeight) {
        int x = returnButtonX(leftPos);
        int y = returnButtonY(topPos, imageHeight);
        return hasRenderablePanel()
                && mouseX >= x && mouseX < x + CONTROL_SIZE
                && mouseY >= y && mouseY < y + CONTROL_SIZE;
    }

    private static Component storageViewTooltip(StorageView view) {
        if (isStorageViewEnabled(view)) return view.displayName();
        if ((view == StorageView.ENDER_CHEST
                && StorageClientNetwork.hasPortableEnderChest())
                || (view == StorageView.BUNDLES && hasBundle())) {
            return Component.translatable(
                    "message.better-shulker-hud.storage_server_required");
        }
        return Component.translatable(
                "message.better-shulker-hud.storage_view_unavailable",
                view.displayName());
    }

    private static int[] getStorageStats() {
        if (storageView == StorageView.ENDER_CHEST) {
            int total = StorageClientNetwork.getEnderContents().stream()
                    .mapToInt(ItemStack::getCount).sum();
            return new int[]{total, 27 * 64};
        }
        if (storageView == StorageView.BUNDLES) {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return new int[]{0, 0};
            int total = 0;
            int bundleCount = 0;
            Inventory inventory = client.player.getInventory();
            for (int slot = 0; slot < 36; slot++) {
                ItemStack bundle = inventory.getItem(slot);
                if (!(bundle.getItem() instanceof BundleItem)) continue;
                bundleCount++;
                BundleContents contents = bundle.getOrDefault(
                        DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
                total += contents.weight().result()
                        .map(weight -> Math.clamp(
                                (int) Math.ceil(weight.doubleValue() * 64.0), 0, 64))
                        .orElseGet(() -> contents.itemCopyStream()
                                .mapToInt(ItemStack::getCount).sum());
            }
            return new int[]{total, bundleCount * 64};
        }
        List<ShulkerSlotEntry> all = getAllShulkers();
        int totalItems = 0;
        for (ShulkerSlotEntry entry : all) {
            totalItems += entry.contents().stream().mapToInt(ItemStack::getCount).sum();
        }
        // remaining weight → how many more "standard" items (weight 1/64) would fit
        int capacity = all.size() * ShulkerContentsHelper.SHULKER_SIZE * 64;
        return new int[] { totalItems, capacity };
    }
}
