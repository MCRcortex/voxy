package me.cortex.neovoxy.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.cortex.neovoxy.client.core.IGetNeoVoxyRenderSystem;
import me.cortex.neovoxy.common.Logger;
import me.cortex.neovoxy.commonImpl.NeoVoxyCommon;
import me.cortex.neovoxy.commonImpl.WorldIdentifier;
import me.cortex.neovoxy.commonImpl.importers.DHImporter;
import me.cortex.neovoxy.commonImpl.importers.WorldImporter;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.DimensionType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;


public class NeoVoxyCommands {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        var imports = Commands.literal("import")
                .then(Commands.literal("world")
                        .then(Commands.argument("world_name", StringArgumentType.string())
                                .suggests(NeoVoxyCommands::importWorldSuggester)
                                .executes(NeoVoxyCommands::importWorld)))
                .then(Commands.literal("bobby")
                        .then(Commands.argument("world_name", StringArgumentType.string())
                                .suggests(NeoVoxyCommands::importBobbySuggester)
                                .executes(NeoVoxyCommands::importBobby)))
                .then(Commands.literal("raw")
                        .then(Commands.argument("path", StringArgumentType.string())
                                .executes(NeoVoxyCommands::importRaw)))
                .then(Commands.literal("zip")
                        .then(Commands.argument("zipPath", StringArgumentType.string())
                                .executes(NeoVoxyCommands::importZip)
                                .then(Commands.argument("innerPath", StringArgumentType.string())
                                        .executes(NeoVoxyCommands::importZip))))
                .then(Commands.literal("cancel")
                        .executes(NeoVoxyCommands::cancelImport));

        if (DHImporter.HasRequiredLibraries) {
            imports = imports
                    .then(Commands.literal("distant_horizons")
                    .then(Commands.argument("sqlDbPath", StringArgumentType.string())
                            .executes(NeoVoxyCommands::importDistantHorizons)));
        }

        return Commands.literal("neovoxy")//.requires((ctx)-> NeoVoxyCommon.getInstance() != null)
                .then(Commands.literal("reload")
                        .executes(NeoVoxyCommands::reloadInstance))
                .then(imports);
    }

    private static int reloadInstance(CommandContext<CommandSourceStack> ctx) {
        var instance = (NeoVoxyClientInstance)NeoVoxyCommon.getInstance();
        if (instance == null) {
            ctx.getSource().sendFailure(Component.translatable("NeoVoxy must be enabled in settings to use this"));
            return 1;
        }
        var wr = Minecraft.getInstance().levelRenderer;
        if (wr!=null) {
            ((IGetNeoVoxyRenderSystem)wr).shutdownRenderer();
        }

        NeoVoxyCommon.shutdownInstance();
        System.gc();
        NeoVoxyCommon.createInstance();

        var r = Minecraft.getInstance().levelRenderer;
        if (r != null) r.allChanged();
        return 0;
    }




    private static int importDistantHorizons(CommandContext<CommandSourceStack> ctx) {
        var instance = (NeoVoxyClientInstance)NeoVoxyCommon.getInstance();
        if (instance == null) {
            ctx.getSource().sendFailure(Component.translatable("NeoVoxy must be enabled in settings to use this"));
            return 1;
        }
        var dbFile = new File(ctx.getArgument("sqlDbPath", String.class));
        if (!dbFile.exists()) {
            return 1;
        }
        if (dbFile.isDirectory()) {
            dbFile = dbFile.toPath().resolve("DistantHorizons.sqlite").toFile();
            if (!dbFile.exists()) {
                return 1;
            }
        }

        File dbFile_ = dbFile;
        var engine = WorldIdentifier.ofEngine(Minecraft.getInstance().level);
        if (engine==null)return 1;
        return instance.getImportManager().makeAndRunIfNone(engine, ()->
                new DHImporter(dbFile_, engine, Minecraft.getInstance().level, instance.getServiceManager(), instance.savingServiceRateLimiter))?0:1;
    }

    private static boolean fileBasedImporter(File directory) {
        var instance = (NeoVoxyClientInstance)NeoVoxyCommon.getInstance();
        if (instance == null) {
            return false;
        }

        var engine = WorldIdentifier.ofEngine(Minecraft.getInstance().level);
        if (engine==null) return false;
        return instance.getImportManager().makeAndRunIfNone(engine, ()->{
            var importer = new WorldImporter(engine, Minecraft.getInstance().level, instance.getServiceManager(), instance.savingServiceRateLimiter);
            importer.importRegionDirectoryAsync(directory);
            return importer;
        });
    }

    private static int importRaw(CommandContext<CommandSourceStack> ctx) {
        if (NeoVoxyCommon.getInstance() == null) {
            ctx.getSource().sendFailure(Component.translatable("NeoVoxy must be enabled in settings to use this"));
            return 1;
        }

        return fileBasedImporter(new File(ctx.getArgument("path", String.class)))?0:1;
    }

    private static int importBobby(CommandContext<CommandSourceStack> ctx) {
        if (NeoVoxyCommon.getInstance() == null) {
            ctx.getSource().sendFailure(Component.translatable("NeoVoxy must be enabled in settings to use this"));
            return 1;
        }

        var file = new File(".bobby").toPath().resolve(ctx.getArgument("world_name", String.class)).toFile();
        return fileBasedImporter(file)?0:1;
    }

    private static CompletableFuture<Suggestions> importWorldSuggester(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder sb) {
        return fileDirectorySuggester(Minecraft.getInstance().gameDirectory.toPath().resolve("saves"), sb);
    }
    private static CompletableFuture<Suggestions> importBobbySuggester(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder sb) {
        return fileDirectorySuggester(Minecraft.getInstance().gameDirectory.toPath().resolve(".bobby"), sb);
    }

    private static CompletableFuture<Suggestions> fileDirectorySuggester(Path dir, SuggestionsBuilder sb) {
        var str = sb.getRemaining().replace("\\\\", "\\").replace("\\", "/");
        if (str.startsWith("\"")) {
            str = str.substring(1);
        }
        if (str.endsWith("\"")) {
            str = str.substring(0,str.length()-1);
        }
        var remaining = str;
        if (str.contains("/")) {
            int idx = str.lastIndexOf('/');
            remaining = str.substring(idx+1);
            try {
                dir = dir.resolve(str.substring(0, idx));
            } catch (Exception e) {
                return Suggestions.empty();
            }
            str = str.substring(0, idx+1);
        } else {
            str = "";
        }

        try {
            var worlds = Files.list(dir).toList();
            for (var world : worlds) {
                if (!world.toFile().isDirectory()) {
                    continue;
                }
                var wn = world.getFileName().toString();
                if (wn.equals(remaining)) {
                    continue;
                }
                if (SharedSuggestionProvider.matchesSubStr(remaining, wn) || SharedSuggestionProvider.matchesSubStr(remaining, '"'+wn)) {
                    wn = str+wn + "/";
                    sb.suggest(StringArgumentType.escapeIfRequired(wn));
                }
            }
        } catch (IOException e) {}

        return sb.buildFuture();
    }

    private static int importWorld(CommandContext<CommandSourceStack> ctx) {
        if (NeoVoxyCommon.getInstance() == null) {
            ctx.getSource().sendFailure(Component.translatable("NeoVoxy must be enabled in settings to use this"));
            return 1;
        }

        var name = ctx.getArgument("world_name", String.class);
        var file = new File("saves").toPath().resolve(name);
        name = name.toLowerCase(Locale.ROOT);
        if (name.endsWith("/")) {
            name = name.substring(0, name.length()-1);
        }
        if (file.resolve("level.dat").toFile().exists()) {
            var dimFile = DimensionType.getStorageFolder(Minecraft.getInstance().level.dimension(), file)
                    .resolve("region")
                    .toFile();
            if (!dimFile.isDirectory()) return 1;
            return fileBasedImporter(dimFile)?0:1;
            //We are in a world directory, so import the current dimension we are in
            /*
            for (var dim : new String[]{"overworld", "the_nether", "the_end"}) {//This is so annoying that you cant loop through all the dimensions
                var id = ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace(dim));
                var dimPath = DimensionType.getStorageFolder(id, file);
                dimPath = dimPath.resolve("region");
                var dimFile = dimPath.toFile();
                if (dimFile.isDirectory()) {//exists and is a directory
                    if (!fileBasedImporter(dimFile)) {
                        Logger.error("Failed to import dimension: " + id);
                    }
                }
            }*/
        } else {
            if (!(name.endsWith("region"))) {
                file = file.resolve("region");
            }
            return fileBasedImporter(file.toFile()) ? 0 : 1;
        }
    }

    private static int importZip(CommandContext<CommandSourceStack> ctx) {
        var zip =  new File(ctx.getArgument("zipPath", String.class));
        var innerDir = "region/";
        try {
            innerDir = ctx.getArgument("innerPath", String.class);
        } catch (Exception e) {}

        var instance = (NeoVoxyClientInstance)NeoVoxyCommon.getInstance();
        if (instance == null) {
            ctx.getSource().sendFailure(Component.translatable("NeoVoxy must be enabled in settings to use this"));
            return 1;
        }
        String finalInnerDir = innerDir;

        var engine = WorldIdentifier.ofEngine(Minecraft.getInstance().level);
        if (engine != null) {
            return instance.getImportManager().makeAndRunIfNone(engine, () -> {
                var importer = new WorldImporter(engine, Minecraft.getInstance().level, instance.getServiceManager(), instance.savingServiceRateLimiter);
                importer.importZippedRegionDirectoryAsync(zip, finalInnerDir);
                return importer;
            }) ? 0 : 1;
        }
        return 1;
    }

    private static int cancelImport(CommandContext<CommandSourceStack> ctx) {
        var instance = (NeoVoxyClientInstance)NeoVoxyCommon.getInstance();
        if (instance == null) {
            ctx.getSource().sendFailure(Component.translatable("NeoVoxy must be enabled in settings to use this"));
            return 1;
        }
        var world = WorldIdentifier.ofEngineNullable(Minecraft.getInstance().level);
        if (world != null) {
            return instance.getImportManager().cancelImport(world)?0:1;
        }
        return 1;
    }
}