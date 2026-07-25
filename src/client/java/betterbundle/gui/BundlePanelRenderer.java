package bettershulkerhud.gui;

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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import bettershulkerhud.util.ShulkerContentsHelper;
import bettershulkerhud.compat.QuickShulkerExtractionController;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

public final class BundlePanelRenderer {

    public static final int SLOT_SIZE = 18;
    public static final int MAX_COLUMNS = 6;
    public static final int MAX_VISIBLE_ROWS = 8;
    public static final int SLOT_SPACING = 1;
    public static final int PADDING = 3;
    public static final int SCROLL_BAR_WIDTH = 4;
    public static final int CAT_BUTTON_SIZE = 23;
    public static final int CAT_BAR_WIDTH = CAT_BUTTON_SIZE;
    public static final int SEARCH_BAR_HEIGHT = 14;

    private static int scrollOffset = 0;
    public static boolean visible = false;

    public static String searchQuery = "";
    public static boolean searchFocused = false;
    private static int searchCursorTick = 0;
    private static int hoveredShulkerInventorySlot = -1;
    private static final PinIn PIN_IN = createPinIn();

    public static BundleCategory currentCategory = BundleCategory.ALL;

    private BundlePanelRenderer() {}

    public record ShulkerSlotEntry(int inventorySlot, ItemStack shulkerStack, List<ItemStack> contents) {}

    public static int columnCount(int leftPos) {
        Minecraft client = Minecraft.getInstance();
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int imageWidth = client.screen instanceof AbstractContainerScreen<?> screen
                ? screen.imageWidth : 176;
        int leftSpace = leftPos - 4;
        int rightSpace = screenWidth - (leftPos + imageWidth + 28);
        int available = Math.max(leftSpace, rightSpace);
        int fixedWidth = CAT_BAR_WIDTH + 2 + SCROLL_BAR_WIDTH + 2 + PADDING * 2;
        int columns = (available - fixedWidth + SLOT_SPACING)
                / (SLOT_SIZE + SLOT_SPACING);
        return Math.clamp(columns, 2, MAX_COLUMNS);
    }

    public static int visibleRowCount(int topPos, int imageHeight) {
        Minecraft client = Minecraft.getInstance();
        int availableHeight = client.getWindow().getGuiScaledHeight() - topPos - 4;
        int contentHeight = availableHeight - (SEARCH_BAR_HEIGHT + 3) - PADDING * 2 - 24;
        int rows = (contentHeight + SLOT_SPACING) / (SLOT_SIZE + SLOT_SPACING);
        return Math.clamp(rows, 3, MAX_VISIBLE_ROWS);
    }

    public static int panelWidth(int leftPos) {
        int columns = columnCount(leftPos);
        return CAT_BAR_WIDTH + 2 + SCROLL_BAR_WIDTH + 2
                + columns * (SLOT_SIZE + SLOT_SPACING) - SLOT_SPACING + PADDING * 2;
    }

    public static int panelHeight(int topPos, int imageHeight) {
        int rows = visibleRowCount(topPos, imageHeight);
        return SEARCH_BAR_HEIGHT + 3 + PADDING * 2
                + rows * SLOT_SIZE + (rows - 1) * SLOT_SPACING + 24;
    }

    public static int panelX(int leftPos) {
        Minecraft client = Minecraft.getInstance();
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int imageWidth = client.screen instanceof AbstractContainerScreen<?> screen
                ? screen.imageWidth
                : 176;
        int width = panelWidth(leftPos);
        int right = leftPos + imageWidth + 24;
        int immediateLeft = leftPos - width - 4;

        if (immediateLeft >= 4) return immediateLeft;
        if (right + width <= screenWidth - 4) return right;
        return Math.clamp(immediateLeft, 4, Math.max(4, screenWidth - width - 4));
    }

    public static int toggleX(int leftPos, int imageWidth) {
        if (Minecraft.getInstance().screen instanceof InventoryScreen) {
            return leftPos + 130;
        }
        return Math.max(4, leftPos - 22);
    }

    public static int toggleY(int topPos) {
        if (Minecraft.getInstance().screen instanceof InventoryScreen) {
            return topPos + 60;
        }
        return topPos + (FabricLoader.getInstance().isModLoaded("better-bundle") ? 27 : 5);
    }

    public static int getScrollOffset() { return scrollOffset; }
    public static void scrollToTop() { scrollOffset = 0; }

    public static void scrollBy(int delta) {
        Minecraft client = Minecraft.getInstance();
        if (!(client.screen instanceof AbstractContainerScreen<?> screen)) return;
        List<ShulkerSlotEntry> shulkers = getShulkers();
        if (shulkers.isEmpty()) { scrollOffset = 0; return; }
        List<FlatItem> items = buildFlatItemList(shulkers);
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
        List<FlatItem> result = new ArrayList<>();
        for (ShulkerSlotEntry entry : shulkers) {
            List<ItemStack> items = entry.contents();
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                if (stack.isEmpty()) continue;

                ItemSource source = new ItemSource(entry.inventorySlot(), i, stack.copy());
                int matchingIndex = -1;
                for (int resultIndex = 0; resultIndex < result.size(); resultIndex++) {
                    if (ItemStack.isSameItemSameComponents(result.get(resultIndex).stack(), stack)) {
                        matchingIndex = resultIndex;
                        break;
                    }
                }
                if (matchingIndex < 0) {
                    result.add(new FlatItem(stack.copy(), List.of(source)));
                    continue;
                }

                FlatItem existing = result.get(matchingIndex);
                List<ItemSource> sources = new ArrayList<>(existing.sources());
                sources.add(source);
                int total = existing.stack().getCount() + stack.getCount();
                result.set(matchingIndex, new FlatItem(
                        existing.stack().copyWithCount(total), List.copyOf(sources)));
            }
        }
        return result;
    }

    public static List<FlatItem> filterItems(List<FlatItem> items, String query) {
        List<FlatItem> filtered = new ArrayList<>();
        for (FlatItem fi : items) {
            String key = BuiltInRegistries.ITEM.getKey(fi.stack().getItem()).toString();
            if (currentCategory.matches(key)) filtered.add(fi);
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
        if (PIN_IN != null) {
            try {
                if (PIN_IN.contains(name, q)) return true;
            } catch (RuntimeException ignored) {
                // Fall through to the legacy conversion for unusual text components.
            }
        }
        if (toPinyin(name).contains(q)) return true;
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

    public static List<ShulkerSlotEntry> getShulkers() { return findShulkers(false); }
    public static List<ShulkerSlotEntry> getAllShulkers() { return findShulkers(true); }

    private static List<ShulkerSlotEntry> findShulkers(boolean includeEmpty) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null) return List.of();
        List<ShulkerSlotEntry> result = new ArrayList<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            boolean matches = includeEmpty
                    ? ShulkerContentsHelper.isShulker(stack)
                    : ShulkerContentsHelper.isNonEmptyShulker(stack);
            if (matches) {
                result.add(new ShulkerSlotEntry(i, stack, ShulkerContentsHelper.getStacks(stack)));
            }
        }
        return result;
    }

    public static boolean isRecipeBookOpen() {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof AbstractRecipeBookScreen<?> screen) return screen.recipeBookComponent.isVisible();
        return false;
    }

    public static int getHoveredShulkerInventorySlot() {
        return visible ? hoveredShulkerInventorySlot : -1;
    }
    public static boolean isEffectivelyVisible() { return visible && !isRecipeBookOpen(); }
    public static void toggleVisible() { visible = !visible; }

    // --- category button layout ---

    /** Shared button layout: returns Y position of category button i. */
    private static int catButtonY(int i, int panelY) {
        return panelY + PADDING - 3 + i * CAT_BAR_WIDTH;
    }

    public static BundleCategory getCategoryAt(double mouseX, double mouseY, int leftPos, int topPos, int imageHeight) {
        int pw = panelWidth(leftPos);
        int panelHeight = panelHeight(topPos, imageHeight);
        int panelX = panelX(leftPos);
        int baseCatX = panelX + PADDING - 10;
        int panelY = topPos;
        int searchH = SEARCH_BAR_HEIGHT + 3;

        BundleCategory[] cats = BundleCategory.values();
        for (int i = 0; i < cats.length; i++) {
            int by = catButtonY(i, panelY);
            if (by + CAT_BAR_WIDTH > panelY + panelHeight) break;
            int bx = baseCatX;
            int bw = CAT_BAR_WIDTH;
            if (cats[i] == currentCategory) { bx -= 5; bw += 5; }
            if (mouseX >= bx && mouseX < bx + bw
                    && mouseY >= by && mouseY < by + CAT_BAR_WIDTH) {
                return cats[i];
            }
        }
        return null;
    }

    // --- search ---

    public static boolean isInsideSearchBar(double mouseX, double mouseY, int leftPos, int topPos, int imageHeight) {
        if (currentCategory != BundleCategory.ALL) return false; // Only ALL mode has interactive search
        int pw = panelWidth(leftPos);
        int panelX = panelX(leftPos);
        int sbx = panelX + PADDING + CAT_BAR_WIDTH + 2;
        int sby = topPos + 2;
        int sbw = pw - PADDING - CAT_BAR_WIDTH - 2 - PADDING - 10;
        return mouseX >= sbx && mouseX <= sbx + sbw && mouseY >= sby && mouseY <= sby + SEARCH_BAR_HEIGHT;
    }

    public static boolean isInsidePanelBounds(
            double mouseX, double mouseY, int leftPos, int topPos, int imageHeight) {
        if (!isEffectivelyVisible()) return false;
        int x = panelX(leftPos) + 16;
        int y = topPos;
        return mouseX >= x && mouseX < panelX(leftPos) + panelWidth(leftPos)
                && mouseY >= y && mouseY < y + panelHeight(topPos, imageHeight);
    }

    public static boolean isMinimizeButtonHovered(
            double mouseX, double mouseY, int leftPos, int topPos, int imageHeight) {
        int x = minimizeButtonX(leftPos);
        int y = topPos + 1;
        return isEffectivelyVisible()
                && mouseX >= x && mouseX < x + 11
                && mouseY >= y && mouseY < y + 11;
    }

    private static int minimizeButtonX(int leftPos) {
        return panelX(leftPos) + panelWidth(leftPos) - 12;
    }

    public static boolean onCharTyped(int codepoint) {
        if (!searchFocused || currentCategory != BundleCategory.ALL) return false;
        if (Character.isValidCodePoint(codepoint)
                && !Character.isISOControl(codepoint)) {
            searchQuery += new String(Character.toChars(codepoint));
            scrollOffset = 0;
        }
        return true;
    }

    public static boolean onSearchKeyPress(int key, int modifiers) {
        if (!searchFocused || currentCategory != BundleCategory.ALL) return false;
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
        List<FlatItem> allItems = buildFlatItemList(getShulkers());

        List<FlatItem> items = filterItems(allItems, searchQuery);
        if (items.isEmpty()) scrollOffset = 0;

        int pw = panelWidth(leftPos);
        int panelX = panelX(leftPos);
        int panelY = topPos;

        boolean isAllMode = currentCategory == BundleCategory.ALL;
        int searchH = SEARCH_BAR_HEIGHT + 3;

        int columns = columnCount(leftPos);
        int rows = visibleRowCount(topPos, imageHeight);
        int panelHeight = panelHeight(topPos, imageHeight);

        // Panel background (left edge inset 16px)
        graphics.fill(panelX + 16, panelY, panelX + pw, panelY + panelHeight, 0xC0101010);

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        ItemStack carried = client.screen instanceof AbstractContainerScreen<?> screen
                ? screen.getMenu().getCarried() : ItemStack.EMPTY;
        boolean dropHovered = !carried.isEmpty()
                && isInsidePanelBounds(mouseX, mouseY, leftPos, topPos, imageHeight);
        if (!carried.isEmpty()) {
            int color = dropHovered ? 0x6040B060 : 0x302E6FA8;
            int border = dropHovered ? 0xFF70E080 : 0xFF5590C8;
            graphics.fill(panelX + 16, panelY, panelX + pw, panelY + panelHeight, color);
            graphics.fill(panelX + 16, panelY, panelX + pw, panelY + 1, border);
            graphics.fill(panelX + 16, panelY + panelHeight - 1, panelX + pw, panelY + panelHeight, border);
            graphics.fill(panelX + 16, panelY, panelX + 17, panelY + panelHeight, border);
            graphics.fill(panelX + pw - 1, panelY, panelX + pw, panelY + panelHeight, border);
        }

        int totalRows = Math.max(1, (items.size() + columns - 1) / columns);
        int maxScroll = Math.max(0, totalRows - rows);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        // Category buttons always at panel top (no searchH offset)
        int catTop = panelY;
        // Grid starts below search bar
        int gridTop = panelY + searchH;
        int gridContentH = panelHeight - searchH - 24;

        // Category buttons
        BundleCategory[] cats = BundleCategory.values();
        int catX = panelX + PADDING - 10;
        int catAreaH = panelHeight - PADDING * 2;

        for (int i = 0; i < cats.length; i++) {
            int by = catButtonY(i, catTop);
            if (by + CAT_BAR_WIDTH > catTop + panelHeight) break;

            boolean selected = cats[i] == currentCategory;
            int bx = catX;
            int bw = CAT_BAR_WIDTH;
            if (selected) { bx -= 5; bw += 5; }
            boolean hovered = mouseX >= bx && mouseX < bx + bw
                    && mouseY >= by && mouseY < by + CAT_BAR_WIDTH;
            int bg = selected ? 0xC0101010 : (hovered ? 0xFF555555 : 0xFF2D2D2D);
            graphics.fill(bx, by, bx + bw, by + CAT_BAR_WIDTH, bg);
            int iconOff = (CAT_BAR_WIDTH - 16) / 2;
            graphics.item(cats[i].getIcon(), bx + iconOff, by + iconOff);
        }

        // Scroll bar
        int sbX = panelX + PADDING + CAT_BAR_WIDTH + 2;
        int sbY = gridTop + PADDING;
        int sbH = gridContentH - PADDING * 2;

        graphics.fill(sbX, sbY, sbX + SCROLL_BAR_WIDTH, sbY + sbH, 0xFF2D2D2D);
        if (maxScroll > 0) {
            int thumbH = Math.max(12, sbH * rows / totalRows);
            int thumbY = sbY + (sbH - thumbH) * scrollOffset / maxScroll;
            graphics.fill(sbX, thumbY, sbX + SCROLL_BAR_WIDTH, thumbY + thumbH, 0xFF888888);
        }

        // Item grid
        int gridX = sbX + SCROLL_BAR_WIDTH + 2;
        int gridY = gridTop + PADDING;
        int startRow = scrollOffset;
        int hoveredFlatIndex = -1;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                int flatIndex = (startRow + row) * columns + col;
                if (flatIndex >= items.size()) break;
                int sx = gridX + col * (SLOT_SIZE + SLOT_SPACING);
                int sy = gridY + row * (SLOT_SIZE + SLOT_SPACING);

                graphics.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF373737);
                graphics.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0xFFC6C6C6);

                FlatItem fi = items.get(flatIndex);
                graphics.item(fi.stack(), sx + 1, sy + 1);
                graphics.itemDecorations(client.font, fi.stack(), sx + 1, sy + 1);

                if (mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= sy && mouseY < sy + SLOT_SIZE) {
                    hoveredFlatIndex = flatIndex;
                }
            }
        }

        if (hoveredFlatIndex >= 0) {
            int hRow = hoveredFlatIndex / columns - startRow;
            int hCol = hoveredFlatIndex % columns;
            int hx = gridX + hCol * (SLOT_SIZE + SLOT_SPACING);
            int hy = gridY + hRow * (SLOT_SIZE + SLOT_SPACING);
            graphics.fill(hx, hy, hx + SLOT_SIZE, hy + SLOT_SIZE, 0x80FFFFFF);
            graphics.setTooltipForNextFrame(client.font, items.get(hoveredFlatIndex).stack(), mouseX, mouseY);
            hoveredShulkerInventorySlot = items.get(hoveredFlatIndex).inventorySlot();
        } else {
            hoveredShulkerInventorySlot = -1;
        }

        // Search bar (always visible, only interactive in ALL mode)
        {
            int sbx = panelX + PADDING + CAT_BAR_WIDTH + 2;
            int sby = panelY + 2;
            int sbw = pw - PADDING - CAT_BAR_WIDTH - 2 - PADDING - 10;
            boolean active = isAllMode && searchFocused;
            int bg = isAllMode ? (active ? 0xFF000000 : 0xFF2D2D2D) : 0xFF1A1A1A;
            graphics.fill(sbx, sby, sbx + sbw, sby + SEARCH_BAR_HEIGHT, bg);
            if (active) graphics.fill(sbx + 1, sby + 1, sbx + sbw - 1, sby + SEARCH_BAR_HEIGHT - 1, 0xFF3D3D3D);
            int textY = sby + (SEARCH_BAR_HEIGHT - font.lineHeight) / 2;
            if (isAllMode && searchQuery.isEmpty() && !searchFocused) {
                graphics.text(font, "Search...", sbx + 3, textY, 0xFF666666, false);
            } else if (isAllMode && !searchQuery.isEmpty()) {
                graphics.text(font, searchQuery, sbx + 3, textY, 0xFFFFFFFF, false);
                searchCursorTick = (searchCursorTick + 1) % 40;
                if (searchFocused && searchCursorTick < 20) {
                    int cursorX = sbx + 3 + font.width(searchQuery);
                    graphics.fill(cursorX, textY, cursorX + 1, textY + font.lineHeight, 0xFFFFFFFF);
                }
            }
        }

        // Category title (on top of search bar)
        if (currentCategory != BundleCategory.ALL) {
            String label = currentCategory.getDisplayName();
            graphics.text(font, label, panelX + 16 + 3, panelY + 2, 0xFFCCCCCC, false);
        }

        // Bundle count display (bottom-right of grid)
        int[] stats = getShulkerStats();
        String countText = stats[0] + "/" + stats[1];
        int textW = font.width(countText);
        int countX = gridX + columns * (SLOT_SIZE + SLOT_SPACING) - SLOT_SPACING - textW;
        int countY = gridY + rows * SLOT_SIZE + (rows - 1) * SLOT_SPACING + 7;
        graphics.fill(countX - 2, countY, countX + textW + 2, countY + font.lineHeight, 0xC0101010);
        graphics.text(font, countText, countX, countY, 0xFFAAAAAA, false);

        int returnX = panelX + 16;
        int returnY = panelY + panelHeight - 21;
        boolean returnHovered = isReturnButtonHovered(
                mouseX, mouseY, leftPos, topPos, imageHeight);
        boolean canReturn = QuickShulkerExtractionController.hasReturnableHistory();
        graphics.fill(returnX, returnY, returnX + 18, returnY + 18,
                returnHovered ? 0x80FFFFFF : (canReturn ? 0xFF555555 : 0xFF303030));
        graphics.item(new ItemStack(Items.HOPPER), returnX + 1, returnY + 1);
        if (returnHovered) {
            graphics.setTooltipForNextFrame(client.font,
                    Component.translatable("message.better-shulker-hud.return_button"),
                    mouseX, mouseY);
        }

        int minimizeX = minimizeButtonX(leftPos);
        int minimizeY = panelY + 1;
        boolean minimizeHovered = isMinimizeButtonHovered(
                mouseX, mouseY, leftPos, topPos, imageHeight);
        graphics.fill(minimizeX, minimizeY, minimizeX + 11, minimizeY + 11,
                minimizeHovered ? 0xFF666666 : 0xFF303030);
        graphics.text(font, "-", minimizeX + 4, minimizeY, 0xFFFFFFFF, false);
        if (minimizeHovered) {
            graphics.setTooltipForNextFrame(client.font,
                    Component.translatable("message.better-shulker-hud.minimize"),
                    mouseX, mouseY);
        } else if (dropHovered) {
            graphics.setTooltipForNextFrame(client.font,
                    Component.translatable("message.better-shulker-hud.store_drop_target"),
                    mouseX, mouseY);
        }

    }

    public static int returnButtonX(int leftPos) {
        return panelX(leftPos) + 16;
    }

    public static int returnButtonY(int topPos, int imageHeight) {
        return topPos + panelHeight(topPos, imageHeight) - 21;
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
