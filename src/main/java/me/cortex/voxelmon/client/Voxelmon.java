package me.cortex.voxelmon.client;

//import me.cortex.voxelmon.client.terrain.WorldImportCommand;
import me.cortex.voxelmon.client.terrain.WorldImportCommand;
import net.fabricmc.api.ClientModInitializer;
        import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

public class Voxelmon implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(WorldImportCommand.register());
        });
    }
}
