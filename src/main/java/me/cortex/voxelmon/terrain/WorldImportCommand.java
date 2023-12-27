package me.cortex.voxelmon.terrain;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import me.cortex.voxelmon.core.VoxelCore;
import me.cortex.voxelmon.importers.WorldImporter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.server.command.ServerCommandSource;

import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;


/*
public class WorldImportCommand {
    public static void register(CommandDispatcher<ClientCommandSource> dispatcher) {
        dispatcher.register(literal("zenith")
                .then(literal("import").then(literal("world").then(argument("world_name", StringArgumentType.string()).executes(WorldImportCommand::importWorld)))));
    }


    private static int importWorld(CommandContext<ClientCommandSource> ctx) {
        VoxelCore.INSTANCE.createWorldImporter(MinecraftClient.getInstance().world, ctx.getArgument("world_name", String.class));
        return 0;
    }
}
*/