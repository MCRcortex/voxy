package me.cortex.voxelmon.client.terrain;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import me.cortex.voxelmon.client.IGetVoxelCore;
import me.cortex.voxelmon.client.importers.WorldImporter;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;

import java.io.File;


public class WorldImportCommand {
    public static LiteralArgumentBuilder<FabricClientCommandSource> register() {
        return ClientCommandManager.literal("zenith").then(ClientCommandManager.literal("import").then(ClientCommandManager.literal("world").then(ClientCommandManager.argument("world_name", StringArgumentType.string()).executes(WorldImportCommand::importWorld))));
    }

    public static WorldImporter importerInstance;

    private static int importWorld(CommandContext<FabricClientCommandSource> ctx) {
        var instance = MinecraftClient.getInstance();
        var file = new File(ctx.getArgument("world_name", String.class));
        importerInstance = ((IGetVoxelCore)instance.worldRenderer).getVoxelCore().createWorldImporter(MinecraftClient.getInstance().player.clientWorld, file);
        return 0;
    }

}