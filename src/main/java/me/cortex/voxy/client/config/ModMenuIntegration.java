package me.cortex.voxy.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            if (VoxyCommon.isAvailable()) {
                var screen = (SodiumOptionsGUI) SodiumOptionsGUI.createScreen(parent);
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