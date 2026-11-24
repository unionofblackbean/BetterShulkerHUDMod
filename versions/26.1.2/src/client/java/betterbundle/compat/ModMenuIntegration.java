package bettershulkerhud.compat;

import bettershulkerhud.gui.GuiConfigs;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Loaded only by Mod Menu through its optional client-side entrypoint. */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            GuiConfigs screen = new GuiConfigs();
            screen.setParent(parent);
            return screen;
        };
    }
}
