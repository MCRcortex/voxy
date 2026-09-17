package smoke;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;

public class PortSmoke implements ClientModInitializer {
    private boolean started;
    private int ticks;
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!started && client.gui.screen() instanceof TitleScreen screen) {
                started = true;
                client.createWorldOpenFlows().createFreshLevel("voxy-port-smoke-" + System.currentTimeMillis(),
                    MinecraftServer.DEMO_SETTINGS.withGameType(GameType.CREATIVE), WorldOptions.DEMO_OPTIONS,
                    WorldPresets::createNormalWorldDimensions, screen);
            }
            if (client.level != null && client.player != null && ++ticks == 400) {
                if (IVoxyRenderSystemHolder.getNullable() == null) throw new AssertionError("Voxy renderer missing");
                System.out.println("VOXY_PORT_SMOKE_OK: world rendered for 400 client ticks");
                client.stop();
            }
        });
    }
}
