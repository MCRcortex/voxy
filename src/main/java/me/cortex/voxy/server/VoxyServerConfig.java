package me.cortex.voxy.server;

import me.cortex.voxy.common.config.AbstractConfig;
import me.cortex.voxy.server.world.VoxyWorld;

public class VoxyServerConfig {
    String basePath = "";
    AbstractConfig<VoxyWorld> worldConfig = new VoxyWorld.Config();
}
