package bettershulkerhud.gui;

import bettershulkerhud.Reference;
import bettershulkerhud.config.Configs;
import bettershulkerhud.config.Hotkeys;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigBoolean;
import fi.dy.masa.malilib.config.IConfigResettable;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.button.ConfigButtonBoolean;
import fi.dy.masa.malilib.gui.button.ConfigButtonKeybind;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptions;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.gui.interfaces.IConfigGuiAllTab;
import fi.dy.masa.malilib.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public final class GuiConfigs extends GuiConfigsBase implements IConfigGuiAllTab {
    private static ConfigGuiTab selectedTab = ConfigGuiTab.DISPLAY;

    public GuiConfigs() {
        super(10, 50, Reference.MOD_ID, null,
                Reference.MOD_ID + ".gui.title.configs", Reference.MOD_VERSION);
    }

    /** Keep our registry identity and the selected entry consistent. */
    @Override
    protected void buildConfigSwitcher() {
        var registry = fi.dy.masa.malilib.registry.Registry.CONFIG_SCREEN;
        var ownMod = registry.getAllModsWithConfigScreens().stream()
                .filter(info -> Reference.MOD_ID.equals(info.modId()))
                .findFirst().orElse(null);
        if (ownMod == null || !Reference.MOD_NAME.equals(ownMod.modName())
                || ownMod.configScreenSupplier() == null) {
            ownMod = new fi.dy.masa.malilib.util.data.ModInfo(
                    Reference.MOD_ID, Reference.MOD_NAME, GuiConfigs::new);
            registry.registerConfigScreenFactory(ownMod);
        }
        super.buildConfigSwitcher();
        if (this.modSwitchWidget != null) {
            this.modSwitchWidget.setSelectedEntry(ownMod);
        }
    }

    private boolean listInitialized;

    @Override
    public void initGui() {
        int listY = this.layoutToolbar(false) + 6;
        boolean recreate = this.listInitialized && this.getListY() != listY;
        String filter = "";
        boolean searchOpen = false;
        List<Integer> searchKeys = List.of();
        int scrollbar = 0;
        if (recreate) {
            // The pinned MaLiLib list stores its position in final fields.
            // Commit text edits before replacing it, then restore its view state.
            this.applyCurrentTabChanges();
            var list = this.getListWidget();
            var search = list.getSearchBarWidget();
            filter = search.getFilter();
            searchOpen = search.isSearchOpen();
            if (search instanceof fi.dy.masa.malilib.gui.widgets.WidgetSearchBarConfigs configs) {
                searchKeys = new ArrayList<>(configs.getKeybind().getKeys());
            }
            scrollbar = list.getScrollbar().getValue();
        }
        this.setListPosition(10, listY);
        if (recreate) this.reCreateListWidget();
        super.initGui();
        this.listInitialized = true;
        if (recreate) {
            var list = this.getListWidget();
            var search = list.getSearchBarWidget();
            // Search has no public text setter; replay only into its local text field.
            search.setSearchOpen(true);
            filter.codePoints().forEach(codepoint -> search.onCharTyped(
                    new net.minecraft.client.input.CharacterEvent(codepoint)));
            search.setSearchOpen(searchOpen);
            if (search instanceof fi.dy.masa.malilib.gui.widgets.WidgetSearchBarConfigs configs) {
                for (int key : searchKeys) configs.getKeybind().addKey(key);
            }
            list.refreshEntries();
            list.getScrollbar().setValue(scrollbar);
            list.refreshEntries();
        }
        this.clearOptions();
        this.layoutToolbar(true);
    }

    @Override
    protected int getBrowserHeight() {
        return Math.max(0, this.height - this.getListY() - 30);
    }

    /** Measure native buttons first so wrapped rows never cover the config list. */
    private int layoutToolbar(boolean addButtons) {
        int x = 10;
        int y = 26;
        for (ConfigGuiTab tab : ConfigGuiTab.values()) {
            int width = new ButtonGeneric(0, 0, -1, 20, tab.getDisplayName()).getWidth();
            if (x > 10 && x + width > this.width - 10) {
                x = 10;
                y += 22;
            }
            if (addButtons) this.createTabButton(x, y, tab);
            x += width + 2;
        }
        String label = StringUtils.translate(Reference.MOD_ID + ".gui.button.edit_layout");
        ButtonGeneric edit = new ButtonGeneric(x, y, -1, 20, label);
        if (x > 10 && x + edit.getWidth() > this.width - 10) {
            x = 10;
            y += 22;
            edit = new ButtonGeneric(x, y, -1, 20, label);
        }
        if (addButtons) {
            this.addButton(edit, (button, mouseButton) -> {
                this.applyCurrentTabChanges();
                BundlePanelRenderer.startPanelLayoutEditor();
            });
        }
        return y + 20;
    }

    private int createTabButton(int x, int y, ConfigGuiTab tab) {
        ButtonGeneric button = new ButtonGeneric(x, y, -1, 20, tab.getDisplayName());
        button.setEnabled(selectedTab != tab);
        this.addButton(button, new TabButtonListener(tab, this));
        return button.getWidth() + 2;
    }

    @Override
    protected WidgetListConfigOptions createListWidget(int listX, int listY) {
        return new WidgetListConfigOptions(listX, listY, this.getBrowserWidth(), this.getBrowserHeight(),
                this.getConfigWidth(), 0.0F, this.useKeybindSearch(), this) {
            @Override
            protected WidgetConfigOption createListEntryWidget(int x, int y, int index, boolean selected,
                    ConfigOptionWrapper wrapper) {
                return new WidgetConfigOption(x, y, this.browserEntryWidth, this.browserEntryHeight,
                        this.maxLabelWidth, this.configWidth, wrapper, index, GuiConfigs.this, this) {
                    @Override
                    protected void addBooleanAndHotkeyWidgets(int x, int y, int width,
                            IConfigResettable resettable, IConfigBoolean config, IKeybind keybind) {
                        super.addBooleanAndHotkeyWidgets(x, y, width, resettable, config, keybind);
                        if (width >= 124) return;
                        ConfigButtonBoolean booleanButton = null;
                        ConfigButtonKeybind keyButton = null;
                        for (var widget : this.subWidgets) {
                            if (widget instanceof ConfigButtonBoolean button) booleanButton = button;
                            else if (widget instanceof ConfigButtonKeybind button) keyButton = button;
                        }
                        if (booleanButton == null || keyButton == null) return;
                        int minimumBooleanWidth = Math.max(40, Math.max(
                                this.getStringWidth(StringUtils.translate("malilib.gui.button.true")),
                                this.getStringWidth(StringUtils.translate("malilib.gui.button.false"))) + 8);
                        int shift = Math.max(0, 36 - keyButton.getWidth());
                        if (shift > 0 && booleanButton.getWidth() - shift >= minimumBooleanWidth) {
                            // Reclaim only boolean padding; retain native listeners and reset/settings positions.
                            booleanButton.setWidth(booleanButton.getWidth() - shift);
                            keyButton.setX(keyButton.getX() - shift);
                            keyButton.setWidth(keyButton.getWidth() + shift);
                        }
                    }
                };
            }
        };
    }

    @Override
    protected int getConfigWidth() {
        return Math.min(260, Math.max(100, this.width - 220));
    }

    @Override
    protected void onSettingsChanged() {
        super.onSettingsChanged();
        ((ConfigManager) ConfigManager.getInstance()).saveAllConfigs();
        InputEventHandler.getKeybindManager().updateUsedKeys();
    }

    @Override
    protected boolean useKeybindSearch() {
        return true;
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        return switch (selectedTab) {
            case ALL -> getAllConfigs();
            case DISPLAY -> ConfigOptionWrapper.createFor(List.of(
                    Configs.Features.HUD_ENABLED, Configs.Features.SHOW_HUD_TOGGLE_BUTTON,
                    Configs.Features.HIDE_RECIPE_BOOK_BUTTON, Configs.Features.PINYIN_SEARCH,
                    Configs.General.COMPACT_COUNTS, Configs.General.SHOW_SOURCE_DETAILS,
                    Configs.General.HUD_MAX_COLUMNS, Configs.General.HUD_MAX_ROWS,
                    Configs.General.HUD_PANEL_X, Configs.General.HUD_PANEL_Y,
                    Configs.General.HUD_TOGGLE_POSITION_EDIT,
                    Configs.General.HUD_TOGGLE_POSITION_X, Configs.General.HUD_TOGGLE_POSITION_Y));
            case TRANSFER -> ConfigOptionWrapper.createFor(List.of(
                    Configs.Features.INVENTORY_DRAG_STORE, Configs.Features.RETURN_HISTORY,
                    Configs.General.CLEAR_SLOT_LIST_MODE, Configs.General.CLEAR_SLOT_WHITELIST,
                    Configs.General.CLEAR_SLOT_BLACKLIST, Configs.General.ORGANIZE_BLACKLIST));
            case RESTOCK -> ConfigOptionWrapper.createFor(List.of(
                    Configs.Features.AUTO_RESTOCK, Configs.Features.SINGLE_ITEM_AUTO_RESTOCK,
                    Configs.Features.OFFHAND_AUTO_RESTOCK, Configs.General.AUTO_RESTOCK_THRESHOLD,
                    Configs.General.AUTO_RESTOCK_AMOUNT, Configs.General.AUTO_RESTOCK_SCAN_INTERVAL));
            case COMPAT -> ConfigOptionWrapper.createFor(List.of(
                    Configs.General.SHULKER_OPEN_BACKEND, Configs.Features.HIDE_QUICK_SHULKER_SCREEN,
                    Configs.Features.LITEMATICA_RESTOCK, Configs.General.DIAGNOSTIC_LOGGING));
            case HOTKEYS -> getHotkeyConfigs();
        };
    }

    @Override
    public boolean useAllTab() {
        return true;
    }

    @Override
    public List<ConfigOptionWrapper> getAllConfigs() {
        List<ConfigOptionWrapper> options = new ArrayList<>();
        options.addAll(ConfigOptionWrapper.createFor(Configs.General.OPTIONS));
        options.addAll(ConfigOptionWrapper.createFor(Configs.Features.OPTIONS));
        options.addAll(ConfigOptionWrapper.createFor(Hotkeys.HOTKEY_LIST));
        return options;
    }

    private List<ConfigOptionWrapper> getHotkeyConfigs() {
        List<ConfigOptionWrapper> options = new ArrayList<>();
        options.addAll(ConfigOptionWrapper.createFor(Configs.Features.OPTIONS));
        options.addAll(ConfigOptionWrapper.createFor(Hotkeys.HOTKEY_LIST));
        return options;
    }

    private void applyCurrentTabChanges() {
        var listWidget = this.getListWidget();
        if (listWidget != null && listWidget.wereConfigsModified()) {
            listWidget.applyPendingModifications();
            this.onSettingsChanged();
            listWidget.clearConfigsModifiedFlag();
        }
    }

    private record TabButtonListener(ConfigGuiTab tab, GuiConfigs parent)
            implements IButtonActionListener {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            parent.applyCurrentTabChanges();
            selectedTab = tab;
            parent.reCreateListWidget();
            if (parent.getListWidget() != null) {
                parent.getListWidget().resetScrollbarPosition();
            }
            parent.initGui();
        }
    }

    private enum ConfigGuiTab {
        ALL(IConfigGuiAllTab.getTranslationKey()),
        DISPLAY(Reference.MOD_ID + ".gui.button.config_gui.display"),
        TRANSFER(Reference.MOD_ID + ".gui.button.config_gui.transfer"),
        RESTOCK(Reference.MOD_ID + ".gui.button.config_gui.restock"),
        COMPAT(Reference.MOD_ID + ".gui.button.config_gui.compat"),
        HOTKEYS(Reference.MOD_ID + ".gui.button.config_gui.hotkeys");

        private final String translationKey;

        ConfigGuiTab(String translationKey) {
            this.translationKey = translationKey;
        }

        public String getDisplayName() {
            return StringUtils.translate(translationKey);
        }
    }
}
