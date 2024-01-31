package me.cortex.voxy.client.terrain;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.command.ServerCommandSource;

import static net.minecraft.server.command.CommandManager.literal;

public class TestSparseGenCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("testsparsegen")
                .executes(ctx -> test()));
    }

    private static int test() {
        var world = MinecraftClient.getInstance().getServer().getWorld(MinecraftClient.getInstance().world.getRegistryKey());
        var gen = new SparseTerrainGenerator(world.getChunkManager().getNoiseConfig(), world.getChunkManager().getChunkGenerator().getBiomeSource());
        long s = System.currentTimeMillis();
        int c = 0;
        for (int x = -50; x <= 50; x++) {
            for (int z = -50; z <= 50; z++) {
                var biome = gen.getBiome((x*16)>>2, 64>>2, (z*16)>>2);
                int minHeight = gen.getInitialHeight(x*16, z*16);
                c += minHeight;
            }
        }
        System.err.println((System.currentTimeMillis()-s) + " e " + c);
        return 0;
    }
}
