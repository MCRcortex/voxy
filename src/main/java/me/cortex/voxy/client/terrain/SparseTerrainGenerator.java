package me.cortex.voxy.client.terrain;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.List;

public class SparseTerrainGenerator {

    private final DensityFunction initialDensity;
    private final DensityFunction finalDensity;
    private final BiomeSource biomeSource;
    private final MultiNoiseUtil.MultiNoiseSampler biomeSampler;
    public SparseTerrainGenerator(NoiseConfig config, BiomeSource biomeSource) {
        this.biomeSource = biomeSource;

        var router = config.getNoiseRouter();
        this.initialDensity = router.initialDensityWithoutJaggedness();
        this.finalDensity = router.finalDensity();

        this.biomeSampler = new MultiNoiseUtil.MultiNoiseSampler(router.temperature(), router.vegetation(), router.continents(), router.erosion(), router.depth(), router.ridges(), List.of());
    }

    public int getInitialHeight(int x, int z) {
        int minHeight = -64;
        int verticalCellBlockCount = 8;
        int worldHeight = 384;
        for(int y = minHeight + worldHeight; y >= minHeight; y -= verticalCellBlockCount) {
            if (this.initialDensity.sample(new DensityFunction.UnblendedNoisePos(x, y, z)) > 0.390625) {
                return y;
            }
        }

        return Integer.MAX_VALUE;
    }

    public RegistryEntry<Biome> getBiome(int bx, int by, int bz) {
        return this.biomeSource.getBiome(bx, by, bz, this.biomeSampler);
    }




    private final class NoisePos implements DensityFunction.NoisePos {
        private final int x;
        private final int y;
        private final int z;

        private NoisePos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int blockX() {
            return this.x;
        }

        @Override
        public int blockY() {
            return this.y;
        }

        @Override
        public int blockZ() {
            return this.z;
        }
    }
}
