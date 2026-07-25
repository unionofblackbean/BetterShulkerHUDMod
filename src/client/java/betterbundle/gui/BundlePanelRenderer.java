package bettershulkerhud.gui;

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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import bettershulkerhud.util.ShulkerContentsHelper;
import bettershulkerhud.compat.QuickShulkerExtractionController;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

public final class BundlePanelRenderer {

    public static final int SLOT_SIZE = 18;
    public static final int SLOT_SPACING = 0;
    public static final int PADDING = 5;
    public static final int SCROLL_BAR_WIDTH = 10;
    public static final int CAT_BUTTON_SIZE = 18;
    public static final int CAT_BAR_WIDTH = CAT_BUTTON_SIZE;
    public static final int SEARCH_BAR_HEIGHT = 18;
    public static final int HEADER_HEIGHT = 24;
    public static final int FOOTER_HEIGHT = 24;

    private static final int SCREEN_MARGIN = 4;
    private static final int PANEL_GAP = 4;
    private static final int CATEGORY_GAP = 2;
    private static final int SCROLL_GAP = 2;
    private static final int BODY_INSET = 12;
    private static final int CONTROL_SIZE = 14;
    private static final int COLOR_PANEL = 0xFFC6C6C6;
    private static final int COLOR_PANEL_HOVER = 0xFFD6D6D6;
    private static final int COLOR_PANEL_SELECTED = 0xFF9A9A9A;
    private static final int COLOR_BORDER_LIGHT = 0xFFFFFFFF;
    private static final int COLOR_BORDER_MID = 0xFF8B8B8B;
    private static final int COLOR_BORDER_DARK = 0xFF373737;
    private static final int COLOR_TEXT = 0xFF404040;
    private static final int COLOR_TEXT_MUTED = 0xFF707070;
    private static final int COLOR_SHADOW_SOFT = 0x38000000;
    private static final int COLOR_SHADOW_DEEP = 0x68000000;

    private static final Identifier SLOT_SPRITE =
            Identifier.withDefaultNamespace("container/slot");
    private static final Identifier TEXT_FIELD_SPRITE =
            Identifier.withDefaultNamespace("widget/text_field");
    private static final Identifier TEXT_FIELD_HIGHLIGHTED_SPRITE =
            Identifier.withDefaultNamespace("widget/text_field_highlighted");
    private static final Identifier SCROLLER_SPRITE =
            Identifier.withDefaultNamespace("container/creative_inventory/scroller");
    private static final Identifier SCROLLER_DISABLED_SPRITE =
            Identifier.withDefaultNamespace("container/creative_inventory/scroller_disabled");

    private static int scrollOffset = 0;
    public static String searchQuery = "";
    public static boolean searchFocused = false;
    private static int searchCursorTick = 0;
    private static int hoveredShulkerInventorySlot = -1;
    private static final PinIn PIN_IN = createPinIn();

    private static Player cachedPlayer;
    private static long cachedInventoryFingerprint = Long.MIN_VALUE;
    private static List<ShulkerSlotEntry> cachedAllShulkers = List.of();
    private static List<ShulkerSlotEntry> cachedNonEmptyShulkers = List.of();
    private static List<FlatItem> cachedFlatItems = List.of();
    private static List<FlatItem> cachedVisibleItems = List.of();
    private static String cachedSearchQuery = null;
    private static BundleCategory cachedCategory = null;
    private static boolean cachedEnderChestPreview;

    public static BundleCategory currentCategory = BundleCategory.OVERVIEW;

    private BundlePanelRenderer() {}

    public record ShulkerSlotEntry(int inventorySlot, ItemStack shulkerStack, List<ItemStack> contents) {}

    public static int columnCount(int leftPos) {
        Minecraft client = Minecraft.getInstance();
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int imageWidth = client.screen instanceof AbstractContainerScreen<?> screen
                ? screen.imageWidth : 176;
        int leftSpace = leftPos - SCREEN_MARGIN - PANEL_GAP;
        int rightSpace = screenWidth - (leftPos + imageWidth)
                - SCREEN_MARGIN - PANEL_GAP;
        boolean enderChestPreview =
                QuickShulkerExtractionController.isEnderChestPreviewActive();
        int available = enderChestPreview ? leftSpace : Math.max(leftSpace, rightSpace);
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

    public static int panelX(int leftPos) {
        Minecraft client = Minecraft.getInstance();
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int imageWidth = client.screen instanceof AbstractContainerScreen<?> screen
                ? screen.imageWidth
                : 176;
        int width = panelWidth(leftPos);
        int right = leftPos + imageWidth + PANEL_GAP;
        int immediateLeft = leftPos - width - PANEL_GAP;
        int leftSpace = leftPos - SCREEN_MARGIN;
        int rightSpace = screenWidth - (leftPos + imageWidth) - SCREEN_MARGIN;

        // Keep the Ender Chest panel on the left. REI and similar item lists
        // normally reserve the right side of container screens.
        if (QuickShulkerExtractionController.isEnderChestPreviewActive()) {
            return immediateLeft;
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
        int desired = client.screen instanceof InventoryScreen
                ? leftPos + 130 : leftPos + imageWidth - 24;
        return Math.clamp(desired, SCREEN_MARGIN,
                Math.max(SCREEN_MARGIN,
                        client.getWindow().getGuiScaledWidth() - 20 - SCREEN_MARGIN));
    }

    public static int toggleY(int topPos) {
        Minecraft client = Minecraft.getInstance();
        int desired = client.screen instanceof InventoryScreen
                ? topPos + 60
                : topPos + (FabricLoader.getInstance().isModLoaded("better-bundle") ? 27 : 5);
        return Math.clamp(desired, SCREEN_MARGIN,
                Math.max(SCREEN_MARGIN,
                        client.getWindow().getGuiScaledHeight() - 20 - SCREEN_MARGIN));
    }

    public static boolean shouldShowEnderChestButton() {
        Minecraft client = Minecraft.getInstance();
        return Configs.Features.SHOW_ENDER_CHEST_BUTTON.getBooleanValue()
                && client.screen instanceof InventoryScreen;
    }

    public static int enderChestButtonX(int leftPos, int imageWidth) {
        Minecraft client = Minecraft.getInstance();
        int toggle = toggleX(leftPos, imageWidth);
        int desired = toggle + 24;
        int maximum = client.getWindow().getGuiScaledWidth() - 20 - SCREEN_MARGIN;
        return desired <= maximum ? desired : Math.max(SCREEN_MARGIN, toggle - 24);
    }

    public static int enderChestButtonY(int topPos) {
        return toggleY(topPos);
    }

    public static boolean isEnderChestButtonHovered(
            double mouseX, double mouseY, int leftPos, int topPos, int imageWidth) {
        if (!shouldShowEnderChestButton()) return false;
        int x = enderChestButtonX(leftPos, imageWidth);
        int y = enderChestButtonY(topPos);
        return mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20;
    }

    public static boolean shouldRenderHudToggleButton() {
        Minecraft client = Minecraft.getInstance();
        if (!QuickShulkerExtractionController.isEnderChestPreviewActive()) return true;
        return client.screen instanceof AbstractContainerScreen<?> screen
                && canFitEnderChestPreview(screen);
    }

    private static boolean canFitEnderChestPreview(AbstractContainerScreen<?> screen) {
        int minimumPanelWidth = PADDING + CAT_BAR_WIDTH + CATEGORY_GAP
                + SCROLL_BAR_WIDTH + SCROLL_GAP + SLOT_SIZE + PADDING;
        int availableLeft = screen.leftPos - SCREEN_MARGIN - PANEL_GAP;
        return availableLeft >= minimumPanelWidth;
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
        if (!(client.screen instanceof AbstractContainerScreen<?> screen)) return;
        List<FlatItem> items = getVisibleItems();
        if (items.isEmpty()) { scrollOffset = 0; return; }
        int columns = columnCount(screen.leftPos);
        int rows = visibleRowCount(screen.topPos, screen.imageHeight);
        int totalRows = (items.size() + columns - 1) / columns;
        int maxScroll = Math.max(0, totalRows - rows);
        scrollOffset = Math.clamp(scrollOffset + delta, 0, maxScroll);
    }

    public record ItemSource(int inventorySlot, int shulkerSlot, ItemStack stack) {}

    public record FlatItem(ItemStack stack, List<ItemSource> sources) {
        public int inventorySlot() { return sources.get(0).inventorySlot(); }
        public int shulkerSlot() { return sources.get(0).shulkerSlot(); }
    }

    public static List<FlatItem> buildFlatItemList(List<ShulkerSlotEntry> shulkers) {
        Map<StackKey, MutableFlatItem> aggregated = new LinkedHashMap<>();
        for (ShulkerSlotEntry entry : shulkers) {
            List<ItemStack> items = entry.contents();
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                if (stack.isEmpty()) continue;

                ItemSource source = new ItemSource(entry.inventorySlot(), i, stack.copy());
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
        result.sort(Comparator
                .comparingInt((FlatItem item) -> item.stack().getCount())
                .reversed()
                .thenComparing(item -> BuiltInRegistries.ITEM
                        .getKey(item.stack().getItem()).toString()));
        return result;
    }

    public static List<FlatItem> getVisibleItems() {
        ensureCache();
        if (BundleCategory.registerCategoryItems()) {
            cachedCategory = null;
        }
        if (cachedCategory != currentCategory
                || !java.util.Objects.equals(cachedSearchQuery, searchQuery)) {
            cachedVisibleItems = List.copyOf(filterItems(cachedFlatItems, searchQuery));
            cachedCategory = currentCategory;
            cachedSearchQuery = searchQuery;
        }
        return cachedVisibleItems;
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
        cachedInventoryFingerprint = Long.MIN_VALUE;
        cachedAllShulkers = List.of();
        cachedNonEmptyShulkers = List.of();
        cachedFlatItems = List.of();
        cachedVisibleItems = List.of();
        cachedSearchQuery = null;
        cachedCategory = null;
        cachedEnderChestPreview = false;
        hoveredShulkerInventorySlot = -1;
    }

    private static void ensureCache() {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null) {
            invalidateCache();
            return;
        }
        boolean enderChestPreview =
                QuickShulkerExtractionController.isEnderChestPreviewActive();
        Inventory inv = player.getInventory();
        List<ItemStack> enderChestContents = enderChestPreview
                ? QuickShulkerExtractionController.getEnderChestSnapshot() : List.of();
        long fingerprint = 1;
        int sourceSize = enderChestPreview ? enderChestContents.size() : 36;
        for (int i = 0; i < sourceSize; i++) {
            ItemStack stack = enderChestPreview
                    ? enderChestContents.get(i) : inv.getItem(i);
            fingerprint = 31L * fingerprint + ItemStack.hashItemAndComponents(stack);
            fingerprint = 31L * fingerprint + stack.getCount();
        }
        if (cachedPlayer == player
                && cachedEnderChestPreview == enderChestPreview
                && cachedInventoryFingerprint == fingerprint) return;

        List<ShulkerSlotEntry> all = new ArrayList<>();
        List<ShulkerSlotEntry> nonEmpty = new ArrayList<>();
        if (enderChestPreview) {
            List<ItemStack> contents = new ArrayList<>(sourceSize);
            for (int i = 0; i < sourceSize; i++) {
                contents.add(enderChestContents.get(i).copy());
            }
            ShulkerSlotEntry entry = new ShulkerSlotEntry(
                    -1, new ItemStack(Items.ENDER_CHEST), List.copyOf(contents));
            all.add(entry);
            if (contents.stream().anyMatch(item -> !item.isEmpty())) nonEmpty.add(entry);
        } else {
            for (int i = 0; i < 36; i++) {
                ItemStack stack = inv.getItem(i);
                if (!ShulkerContentsHelper.isShulker(stack)) continue;
                List<ItemStack> contents = List.copyOf(ShulkerContentsHelper.getStacks(stack));
                ShulkerSlotEntry entry = new ShulkerSlotEntry(i, stack.copy(), contents);
                all.add(entry);
                if (contents.stream().anyMatch(item -> !item.isEmpty())) nonEmpty.add(entry);
            }
        }
        cachedPlayer = player;
        cachedEnderChestPreview = enderChestPreview;
        cachedInventoryFingerprint = fingerprint;
        cachedAllShulkers = List.copyOf(all);
        cachedNonEmptyShulkers = List.copyOf(nonEmpty);
        cachedFlatItems = List.copyOf(buildFlatItemList(cachedNonEmptyShulkers));
        cachedVisibleItems = List.of();
        cachedSearchQuery = null;
        cachedCategory = null;
    }

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
        if (client.screen instanceof AbstractRecipeBookScreen<?> screen) return screen.recipeBookComponent.isVisible();
        return false;
    }

    public static int getHoveredShulkerInventorySlot() {
        return Configs.Features.HUD_ENABLED.getBooleanValue()
                && !QuickShulkerExtractionController.isEnderChestPreviewActive()
                ? hoveredShulkerInventorySlot : -1;
    }
    public static boolean isEffectivelyVisible() {
        if (!Configs.Features.HUD_ENABLED.getBooleanValue() || isRecipeBookOpen()) return false;
        Minecraft client = Minecraft.getInstance();
        return !QuickShulkerExtractionController.isEnderChestPreviewActive()
                || (client.screen instanceof AbstractContainerScreen<?> screen
                && canFitEnderChestPreview(screen));
    }
    public static void toggleVisible() {
        Configs.Features.HUD_ENABLED.setBooleanValue(
                !Configs.Features.HUD_ENABLED.getBooleanValue());
        Configs.saveToFile();
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
        int sbx = searchBarX(leftPos);
        int sby = panelY(topPos, imageHeight) + 3;
        int sbw = searchBarWidth(leftPos);
        return mouseX >= sbx && mouseX <= sbx + sbw && mouseY >= sby && mouseY <= sby + SEARCH_BAR_HEIGHT;
    }

    public static boolean isInsidePanelBounds(
            double mouseX, double mouseY, int leftPos, int topPos, int imageHeight) {
        if (!isEffectivelyVisible()) return false;
        int x = panelX(leftPos) + BODY_INSET;
        int y = panelY(topPos, imageHeight);
        return mouseX >= x && mouseX < panelX(leftPos) + panelWidth(leftPos)
                && mouseY >= y && mouseY < y + panelHeight(topPos, imageHeight);
    }

    public static boolean isMinimizeButtonHovered(
            double mouseX, double mouseY, int leftPos, int topPos, int imageHeight) {
        int x = minimizeButtonX(leftPos);
        int y = panelY(topPos, imageHeight) + 4;
        return isEffectivelyVisible()
                && mouseX >= x && mouseX < x + CONTROL_SIZE
                && mouseY >= y && mouseY < y + CONTROL_SIZE;
    }

    private static int minimizeButtonX(int leftPos) {
        return panelX(leftPos) + panelWidth(leftPos) - CONTROL_SIZE - 4;
    }

    private static int searchBarX(int leftPos) {
        return panelX(leftPos) + BODY_INSET + 4;
    }

    private static int searchBarWidth(int leftPos) {
        int right = minimizeButtonX(leftPos) - 4;
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
        if (!isEffectivelyVisible()) return;
        List<ShulkerSlotEntry> allShulkers = getAllShulkers();
        if (allShulkers.isEmpty()) { scrollOffset = 0; return; }
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

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        ItemStack carried = client.screen instanceof AbstractContainerScreen<?> screen
                ? screen.getMenu().getCarried() : ItemStack.EMPTY;
        boolean dropHovered = !carried.isEmpty()
                && isInsidePanelBounds(mouseX, mouseY, leftPos, topPos, imageHeight);
        if (!carried.isEmpty()) {
            int color = dropHovered ? 0x505CBA79 : 0x303E789B;
            int border = dropHovered ? 0xFFD9F4DF : 0xFFDCEAF4;
            graphics.fill(bodyX + 1, panelY + 1,
                    panelX + pw - 1, panelY + panelHeight - 1, color);
            drawBorder(graphics, bodyX, panelY, bodyWidth, panelHeight, border);
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
            int bg = selected ? COLOR_PANEL_SELECTED
                    : (hovered ? COLOR_PANEL_HOVER : COLOR_PANEL);
            if (selected) {
                drawInsetFrame(graphics, bx, by, bw, catHeight, bg);
            } else {
                drawFrame(graphics, bx, by, bw, catHeight, bg);
            }
            renderScaledCategoryIcon(
                    graphics, cats[i].getIcon(), bx, by, CAT_BAR_WIDTH, catHeight);
        }

        // Scroll bar
        int sbX = gridX - SCROLL_BAR_WIDTH - SCROLL_GAP;
        int sbY = gridY;
        int sbH = gridHeight;

        drawInsetFrame(graphics, sbX, sbY, SCROLL_BAR_WIDTH, sbH, COLOR_BORDER_MID);
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
            drawControlShadow(graphics, sbx, sby, sbw, SEARCH_BAR_HEIGHT, 2);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    active ? TEXT_FIELD_HIGHLIGHTED_SPRITE : TEXT_FIELD_SPRITE,
                    sbx, sby, sbw, SEARCH_BAR_HEIGHT);
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
        int[] stats = getShulkerStats();
        String countText = fitTail(font, stats[0] + " / " + stats[1], gridWidth);
        int textW = font.width(countText);
        int countX = gridX + gridWidth - textW;
        int countY = panelY + panelHeight - 17;
        graphics.text(font, countText, countX, countY, COLOR_TEXT_MUTED, false);

        int returnX = bodyX + 4;
        int returnY = panelY + panelHeight - 21;
        boolean returnHovered = isReturnButtonHovered(
                mouseX, mouseY, leftPos, topPos, imageHeight);
        boolean enderChestPreview =
                QuickShulkerExtractionController.isEnderChestPreviewActive();
        boolean canReturn = !enderChestPreview
                && QuickShulkerExtractionController.canOrganizeInventory();
        drawFrame(graphics, returnX, returnY, 18, 18,
                returnHovered ? COLOR_PANEL_HOVER
                        : (canReturn ? COLOR_PANEL : COLOR_BORDER_MID));
        graphics.item(new ItemStack(
                enderChestPreview ? Items.ENDER_CHEST : Items.HOPPER), returnX + 1, returnY + 1);

        int categoryX = returnX + 22;
        int categoryWidth = Math.max(0, countX - categoryX - 6);
        if (categoryWidth >= 18) {
            drawInsetFrame(graphics, categoryX, returnY + 1, categoryWidth, 16, COLOR_PANEL);
            String categoryName = fitTail(
                    font, currentCategory.getDisplayName(), categoryWidth - 8);
            graphics.text(font, categoryName, categoryX + 4,
                    returnY + 1 + (16 - font.lineHeight) / 2, COLOR_TEXT, false);
        }
        if (returnHovered) {
            graphics.setTooltipForNextFrame(client.font,
                    Component.translatable(enderChestPreview
                            ? "message.better-shulker-hud.ender_chest_preview"
                            : "message.better-shulker-hud.return_button"),
                    mouseX, mouseY);
        }

        int minimizeX = minimizeButtonX(leftPos);
        int minimizeY = panelY + 4;
        boolean minimizeHovered = isMinimizeButtonHovered(
                mouseX, mouseY, leftPos, topPos, imageHeight);
        drawFrame(graphics, minimizeX, minimizeY, CONTROL_SIZE, CONTROL_SIZE,
                minimizeHovered ? COLOR_PANEL_HOVER : COLOR_PANEL);
        graphics.fill(minimizeX + 4, minimizeY + 7,
                minimizeX + CONTROL_SIZE - 4, minimizeY + 8, COLOR_TEXT);
        if (minimizeHovered) {
            graphics.setTooltipForNextFrame(client.font,
                    Component.translatable("message.better-shulker-hud.minimize"),
                    mouseX, mouseY);
        } else if (dropHovered) {
            graphics.setTooltipForNextFrame(client.font,
                    Component.translatable(enderChestPreview
                            ? "message.better-shulker-hud.ender_chest_drop_target"
                            : "message.better-shulker-hud.store_drop_target"),
                    mouseX, mouseY);
        }

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
        int radius = Math.min(Configs.General.HUD_CORNER_RADIUS.getIntegerValue(),
                Math.max(0, Math.min(width, height) / 2));
        drawControlShadow(graphics, x, y, width, height, radius);
        fillRoundedRect(graphics, x, y, width, height, radius, fill);
        if (radius == 0) {
            graphics.fill(x, y, x + width - 1, y + 1, COLOR_BORDER_LIGHT);
            graphics.fill(x, y, x + 1, y + height - 1, COLOR_BORDER_LIGHT);
            graphics.fill(x + 1, y + height - 1, x + width, y + height, COLOR_BORDER_DARK);
            graphics.fill(x + width - 1, y + 1, x + width, y + height, COLOR_BORDER_DARK);
        } else {
            graphics.fill(x + radius, y, x + width - radius, y + 1, COLOR_BORDER_LIGHT);
            graphics.fill(x, y + radius, x + 1, y + height - radius, COLOR_BORDER_LIGHT);
            graphics.fill(x + radius, y + height - 1, x + width - radius, y + height, COLOR_BORDER_DARK);
            graphics.fill(x + width - 1, y + radius, x + width, y + height - radius, COLOR_BORDER_DARK);
        }
    }

    private static void drawInsetFrame(
            GuiGraphicsExtractor graphics, int x, int y, int width, int height, int fill) {
        int radius = Math.min(Configs.General.HUD_CORNER_RADIUS.getIntegerValue(),
                Math.max(0, Math.min(width, height) / 2));
        drawInsetShadow(graphics, x, y, width, height, radius);
        fillRoundedRect(graphics, x, y, width, height, radius, fill);
        if (radius == 0) {
            graphics.fill(x, y, x + width - 1, y + 1, COLOR_BORDER_DARK);
            graphics.fill(x, y, x + 1, y + height - 1, COLOR_BORDER_DARK);
            graphics.fill(x + 1, y + height - 1, x + width, y + height, COLOR_BORDER_LIGHT);
            graphics.fill(x + width - 1, y + 1, x + width, y + height, COLOR_BORDER_LIGHT);
        } else {
            graphics.fill(x + radius, y, x + width - radius, y + 1, COLOR_BORDER_DARK);
            graphics.fill(x, y + radius, x + 1, y + height - radius, COLOR_BORDER_DARK);
            graphics.fill(x + radius, y + height - 1, x + width - radius, y + height, COLOR_BORDER_LIGHT);
            graphics.fill(x + width - 1, y + radius, x + width, y + height - radius, COLOR_BORDER_LIGHT);
        }
    }

    private static void drawControlShadow(
            GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius) {
        fillRoundedRect(graphics, x + 1, y + 1, width, height, radius, COLOR_SHADOW_SOFT);
        fillRoundedRect(graphics, x + 2, y + 3, width, height, radius, COLOR_SHADOW_DEEP);
    }

    private static void drawInsetShadow(
            GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius) {
        fillRoundedRect(graphics, x + 1, y + 2, width, height, radius, COLOR_SHADOW_SOFT);
    }

    private static void fillRoundedRect(
            GuiGraphicsExtractor graphics, int x, int y, int width, int height,
            int radius, int color) {
        if (radius <= 0) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        for (int row = 0; row < height; row++) {
            int distance = Math.min(row, height - 1 - row);
            int inset = distance >= radius ? 0 : radius - distance;
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    private static void drawBorder(
            GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
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
        return panelX(leftPos) + BODY_INSET + 4;
    }

    public static int returnButtonY(int topPos, int imageHeight) {
        return panelY(topPos, imageHeight) + panelHeight(topPos, imageHeight) - 21;
    }

    public static boolean isReturnButtonHovered(
            double mouseX, double mouseY, int leftPos, int topPos, int imageHeight) {
        int x = returnButtonX(leftPos);
        int y = returnButtonY(topPos, imageHeight);
        return mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
    }

    private static int[] getShulkerStats() {
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
