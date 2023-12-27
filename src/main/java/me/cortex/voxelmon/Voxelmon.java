package me.cortex.voxelmon;

import me.cortex.voxelmon.terrain.TestSparseGenCommand;
//import me.cortex.voxelmon.terrain.WorldImportCommand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class Voxelmon implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        //CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> WorldImportCommand.register(dispatcher));
    }
}
