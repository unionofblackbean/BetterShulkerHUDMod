package bettershulkerhud.compat;

import bettershulkerhud.gui.BundlePanelRenderer;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.List;

public final class ReiExclusionPlugin implements REIClientPlugin {
    @Override
    public void registerExclusionZones(ExclusionZones zones) {
        zones.register(AbstractContainerScreen.class, screen -> {
            if (!BundlePanelRenderer.isEffectivelyVisible()) return List.of();
            return List.of(new Rectangle(
                    BundlePanelRenderer.exclusionX(screen.leftPos),
                    BundlePanelRenderer.exclusionY(screen.topPos, screen.imageHeight),
                    BundlePanelRenderer.exclusionWidth(screen.leftPos),
                    BundlePanelRenderer.exclusionHeight(screen.topPos, screen.imageHeight)));
        });
    }
}
