package bettershulkerhud.gui;

import bettershulkerhud.Reference;
import bettershulkerhud.config.Configs;
import bettershulkerhud.config.Hotkeys;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.interfaces.IConfigGuiAllTab;
import fi.dy.masa.malilib.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public final class GuiConfigs extends GuiConfigsBase implements IConfigGuiAllTab {
    private static ConfigGuiTab selectedTab = ConfigGuiTab.GENERAL;

    public GuiConfigs() {
        super(10, 50, Reference.MOD_ID, null,
                Reference.MOD_ID + ".gui.title.configs", Reference.MOD_VERSION);
    }

    @Override
    public void initGui() {
        super.initGui();
        this.clearOptions();

        int x = 10;
        int y = 26;
        for (ConfigGuiTab tab : ConfigGuiTab.values()) {
            x += this.createTabButton(x, y, tab);
        }
    }

    private int createTabButton(int x, int y, ConfigGuiTab tab) {
        ButtonGeneric button = new ButtonGeneric(x, y, -1, 20, tab.getDisplayName());
        button.setEnabled(selectedTab != tab);
        this.addButton(button, new TabButtonListener(tab, this));
        return button.getWidth() + 2;
    }

    @Override
    protected int getConfigWidth() {
        return selectedTab == ConfigGuiTab.GENERAL ? 180 : 260;
    }

    @Override
    protected void onSettingsChanged() {
        super.onSettingsChanged();
        ((ConfigManager) ConfigManager.getInstance()).saveAllConfigs();
        InputEventHandler.getKeybindManager().updateUsedKeys();
    }

    @Override
    protected boolean useKeybindSearch() {
        return selectedTab == ConfigGuiTab.ALL
                || selectedTab == ConfigGuiTab.FEATURES
                || selectedTab == ConfigGuiTab.HOTKEYS;
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        return switch (selectedTab) {
            case ALL -> getAllConfigs();
            case GENERAL -> ConfigOptionWrapper.createFor(Configs.General.OPTIONS);
            case FEATURES -> ConfigOptionWrapper.createFor(Configs.Features.OPTIONS);
            case HOTKEYS -> ConfigOptionWrapper.createFor(Hotkeys.HOTKEY_LIST);
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

    private record TabButtonListener(ConfigGuiTab tab, GuiConfigs parent)
            implements IButtonActionListener {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
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
        GENERAL(Reference.MOD_ID + ".gui.button.config_gui.general"),
        FEATURES(Reference.MOD_ID + ".gui.button.config_gui.features"),
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
