package me.cortex.voxy.client;

import me.cortex.voxy.client.core.gl.Capabilities;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.fabricmc.api.ClientModInitializer;
        import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public class VoxyClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {

        ClientLifecycleEvents.CLIENT_STARTED.register(client->{
            boolean systemSupported = Capabilities.INSTANCE.compute && Capabilities.INSTANCE.indirectParameters;
            if (systemSupported) {
                VoxyCommon.setInstanceFactory(VoxyClientInstance::new);
            } else {
                Logger.error("Voxy is unsupported on your system.");
            }
        });


        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            if (VoxyCommon.isAvailable()) {
                dispatcher.register(VoxyCommands.register());
            }
        });
    }
}
