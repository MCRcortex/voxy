package me.cortex.neovoxy;

import me.cortex.neovoxy.client.config.NeoVoxyNeoForgeConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

/**
 * Main mod class for NeoVoxy on NeoForge.
 *
 * Handles config registration and config screen setup.
 * Actual initialization happens via mixins (MixinRenderSystem).
 */
@Mod("neovoxy")
public class NeoVoxy {

    public NeoVoxy(IEventBus modEventBus, ModContainer container) {
        // Only register client config on client side
        if (FMLLoader.getDist() == Dist.CLIENT) {
            // Register NeoForge config
            NeoVoxyNeoForgeConfig.register(container);

            // Register the built-in NeoForge config screen
            container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        }
    }
}
