package me.cortex.voxy.client;

import me.cortex.voxy.client.core.VoxelCore;
import me.cortex.voxy.client.saver.ContextSelectionSystem;
import me.cortex.voxy.client.terrain.WorldImportCommand;
import me.cortex.voxy.common.config.Serialization;
import me.cortex.voxy.common.storage.compressors.ZSTDCompressor;
import me.cortex.voxy.common.storage.config.StorageConfig;
import net.fabricmc.api.ClientModInitializer;
        import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.world.ClientWorld;

public class Voxy implements ClientModInitializer {
    public static final String VERSION;

    static {
        ModContainer mod = (ModContainer) FabricLoader.getInstance().getModContainer("voxy").orElseThrow(NullPointerException::new);
        VERSION = mod.getMetadata().getVersion().getFriendlyString();
        Serialization.init();
    }

    @Override
    public void onInitializeClient() {

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(WorldImportCommand.register());
        });
    }


    private static final ContextSelectionSystem selector = new ContextSelectionSystem();

    public static VoxelCore createVoxelCore(ClientWorld world) {
        var selection = selector.getBestSelectionOrCreate(world);
        return new VoxelCore(selection);
    }
}
