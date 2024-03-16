package me.cortex.voxy.client.terrain;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.cortex.voxy.client.core.IGetVoxelCore;
import me.cortex.voxy.client.importers.WorldImporter;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;


public class WorldImportCommand {
    public static LiteralArgumentBuilder<FabricClientCommandSource> register() {
        return ClientCommandManager.literal("voxy").then(
                ClientCommandManager.literal("import")
                        .then(ClientCommandManager.literal("world")
                                .then(ClientCommandManager.argument("world_name", StringArgumentType.string())
                                        .suggests(WorldImportCommand::importWorldSuggester)
                                        .executes(WorldImportCommand::importWorld)))
                        .then(ClientCommandManager.literal("bobby")
                                .then(ClientCommandManager.argument("world_name", StringArgumentType.string())
                                        .executes(WorldImportCommand::importBobby)))
                        .then(ClientCommandManager.literal("raw")
                                .then(ClientCommandManager.argument("path", StringArgumentType.string())
                                        .executes(WorldImportCommand::importRaw))));
    }


    private static int importRaw(CommandContext<FabricClientCommandSource> ctx) {
        var instance = MinecraftClient.getInstance();
        var file = new File(ctx.getArgument("path", String.class));
        ((IGetVoxelCore)instance.worldRenderer).getVoxelCore().createWorldImporter(MinecraftClient.getInstance().player.clientWorld, file);
        return 0;
    }

    private static int importBobby(CommandContext<FabricClientCommandSource> ctx) {
        var instance = MinecraftClient.getInstance();
        var file = new File(".bobby").toPath().resolve(ctx.getArgument("world_name", String.class)).toFile();
        ((IGetVoxelCore)instance.worldRenderer).getVoxelCore().createWorldImporter(MinecraftClient.getInstance().player.clientWorld, file);
        return 0;
    }

    private static CompletableFuture<Suggestions> importWorldSuggester(CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder sb) {
        try {
            var worlds = Files.list(MinecraftClient.getInstance().runDirectory.toPath().resolve("saves")).toList();
            for (var world : worlds) {
                if (!world.toFile().isDirectory()) {
                    continue;
                }
                var wn = world.getFileName().toString();
                if (CommandSource.shouldSuggest(sb.getRemaining(), wn) || CommandSource.shouldSuggest(sb.getRemaining(), '"'+wn)) {
                    if (wn.contains(" ")) {
                        wn = '"' + wn + '"';
                    }
                    sb.suggest(wn);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return sb.buildFuture();
    }

    private static int importWorld(CommandContext<FabricClientCommandSource> ctx) {
        var instance = MinecraftClient.getInstance();
        var file = new File("saves").toPath().resolve(ctx.getArgument("world_name", String.class)).resolve("region").toFile();
        ((IGetVoxelCore)instance.worldRenderer).getVoxelCore().createWorldImporter(MinecraftClient.getInstance().player.clientWorld, file);
        return 0;
    }

}