package bettershulkerhud.compat;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.kyrptonaught.quickshulker.client.ClientUtil;
import net.kyrptonaught.quickshulker.network.OpenShulkerPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;

final class QuickShulkerCompat {
    private QuickShulkerCompat() {}

    static boolean canSend() {
        return FabricLoader.getInstance().isModLoaded("quickshulker")
                && ClientPlayNetworking.canSend(OpenShulkerPacket.OPEN_SHULKER_PACKET_ID);
    }

    static void open(int slot) {
        OpenShulkerPacket.sendOpenPacket(slot);
    }

    static int toServerSlot(AbstractContainerMenu menu, int menuSlot) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && ClientUtil.isCreativeScreen(client.player)) {
            return ClientUtil.getSlotId(menu, menu.slots.get(menuSlot));
        }
        return menuSlot;
    }
}
