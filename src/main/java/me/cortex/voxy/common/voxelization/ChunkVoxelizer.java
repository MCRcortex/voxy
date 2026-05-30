package me.cortex.voxy.common.voxelization;

import me.cortex.voxy.VoxyConstants;
import me.cortex.voxy.common.lod.LodSection;
import me.cortex.voxy.common.lod.SectionKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Converts a Minecraft {@link LevelChunk} into a {@link LodSection} at LOD level 0.
 *
 * <p>For each column (x, z) within the chunk the voxelizer walks downward from the top
 * of the world and records the first non-air, non-transparent-leaves block it encounters,
 * together with the surface biome and light values.
 *
 * <p>All work is done on the server (or integrated-server) thread; do not call from the
 * render thread.
 */
public final class ChunkVoxelizer {
    private ChunkVoxelizer() {}

    private static final int S = VoxyConstants.SECTION_SIZE; // 16

    /**
     * Voxelizes {@code chunk} at LOD 0.
     *
     * @param chunk the fully-loaded chunk
     * @return a new {@link LodSection} at LOD 0 representing the chunk's surface
     */
    public static LodSection voxelize(LevelChunk chunk) {
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        long key   = SectionKey.encode(0, chunkX, chunkZ);

        int[]   blockStates = new int[S * S];
        short[] heights     = new short[S * S];
        byte[]  lightData   = new byte[S * S];
        int[]   biomeIds    = new int[S * S];

        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight() - 1;

        Registry<Biome> biomeRegistry = chunk.getLevel()
                .registryAccess()
                .registryOrThrow(Registries.BIOME);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();

        for (int lz = 0; lz < S; lz++) {
            for (int lx = 0; lx < S; lx++) {
                int worldX = baseX + lx;
                int worldZ = baseZ + lz;
                int idx    = lx + lz * S;

                // Walk down from sky to find the surface block
                int surfaceY = minY;
                BlockState surfaceState = chunk.getLevel().getBlockState(pos.set(worldX, minY, worldZ));

                for (int y = maxY; y >= minY; y--) {
                    pos.set(worldX, y, worldZ);
                    BlockState state = chunk.getBlockState(pos);
                    if (!state.isAir() && state.isSolid()) {
                        surfaceY     = y;
                        surfaceState = state;
                        break;
                    }
                }

                blockStates[idx] = net.minecraft.world.level.block.Block.getId(surfaceState);
                heights[idx]     = (short) surfaceY;

                // Light: sample just above the surface block
                pos.set(worldX, surfaceY + 1, worldZ);
                int skyLight   = chunk.getLevel().getBrightness(net.minecraft.world.level.LightLayer.SKY,   pos);
                int blockLight = chunk.getLevel().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos);
                lightData[idx] = (byte) ((skyLight << 4) | (blockLight & 0xF));

                // Biome: sample at surface level
                Holder<Biome> biomeHolder = chunk.getNoiseBiome(lx >> 2, surfaceY >> 2, lz >> 2);
                biomeIds[idx] = biomeRegistry.getId(biomeHolder.value());
            }
        }

        return new LodSection(key, blockStates, heights, lightData, biomeIds);
    }
}
