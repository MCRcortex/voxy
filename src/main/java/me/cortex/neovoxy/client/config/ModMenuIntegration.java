package me.cortex.neovoxy.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.cortex.neovoxy.common.Logger;
import me.cortex.neovoxy.commonImpl.NeoVoxyCommon;
import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.config.structure.OptionPage;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            if (NeoVoxyCommon.isAvailable()) {
                var screen = (VideoSettingsScreen)VideoSettingsScreen.createScreen(parent);
                var page = (OptionPage) ConfigManager.CONFIG.getModOptions().stream().filter(a->a.configId().equals("neovoxy")).findFirst().get().pages().get(0);
                ((IConfigPageSetter)screen).neovoxy$setPageJump(page);
                return screen;
            } else {
                return null;
            }
        };
    }
}