package me.cortex.voxy.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.cortex.voxy.client.mixin.sodium.AccessorSodiumOptionsGUI;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI;
import me.jellysquid.mods.sodium.client.gui.screen.ConfigCorruptedScreen;
import net.minecraft.client.gui.screens.Screen;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            if (VoxyCommon.isAvailable()) {
                Screen screen = SodiumClientMod.options().isReadOnly() ? new ConfigCorruptedScreen(parent, AccessorSodiumOptionsGUI::newScreen) : AccessorSodiumOptionsGUI.newScreen(parent);
                //Sorry jelly and douira, please dont hurt me
                try {
                    //We cant use .setPage() as that invokes rebuildGui, however the screen hasnt been initalized yet
                    // causing things to crash
                    var field = SodiumOptionsGUI.class.getDeclaredField("currentPage");
                    field.setAccessible(true);
                    field.set(screen, VoxyConfigScreenPages.voxyOptionPage);
                    field.setAccessible(false);
                } catch (Exception e) {
                    Logger.error("Failed to set the current page to voxy", e);
                }
                return screen;
            } else {
                return null;
            }
        };
    }
}