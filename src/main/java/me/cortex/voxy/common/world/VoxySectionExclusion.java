package me.cortex.voxy.common.world;

import java.util.Arrays;

import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.world.other.Mapper;

/**
 * Hardcoded Voxy skip region. Vanilla/Sodium still render this volume while in render distance;
 * Voxy must not ingest, load, or mesh it.
 *
 * Inclusive block AABB: (6000, 78, 224) to (6065, 179, 288).
 * LOD-0 WorldSections are 32^3 ({@code block >> 5}), so the covering cells are:
 *   sx 187..189 (blocks 5984..6079),
 *   sy 2..5     (blocks 64..191),
 *   sz 7..9     (blocks 224..319).
 */
public final class VoxySectionExclusion {
    private static final int MIN_X = 6000;
    private static final int MIN_Y = 78;
    private static final int MIN_Z = 224;
    private static final int MAX_X = 6065;
    private static final int MAX_Y = 179;
    private static final int MAX_Z = 288;

    private VoxySectionExclusion() {}

    public static boolean isLod0Excluded(long pos) {
        int chunky_x= WorldEngine.getX(pos);
        int x = chunky_x * 32;
        int chunky_y = WorldEngine.getY(pos);
        int y = chunky_y * 32;
        int chunky_z = WorldEngine.getZ(pos);
        int z= chunky_z * 32;
        Logger.info("!!!!!!! isLod0Excluded called: " + x + ", " + y + ", " + z);

        return isMinecraftSectionExcluded(x, y, z);
    }

    /** Minecraft 16^3 section coords; skipped if they sit in an excluded LOD-0 cell. */
    public static boolean isMinecraftSectionExcluded(int x, int y, int z) {
        return x >= MIN_X && x <= MAX_X && y >= MIN_Y && y <= MAX_Y && z >= MIN_Z && z <= MAX_Z;
    }

    public static boolean intersectsWorldSection(int lvl, int x, int y, int z) {
        int shift = 5 + lvl;
        int size = 32 << lvl;
        int minX = x << shift;
        int minY = y << shift;
        int minZ = z << shift;
        return aabbIntersects(minX, minY, minZ, minX + size - 1, minY + size - 1, minZ + size - 1);
    }

    public static void carveExcludedVoxels(WorldSection section) {
        if (section.lvl == 0) {
            if (isMinecraftSectionExcluded(section.x, section.y, section.z)) {
                Arrays.fill(section._unsafeGetRawDataArray(), Mapper.AIR);
            }
            return;
        }
        if (!intersectsWorldSection(section.lvl, section.x, section.y, section.z)) {
            return;
        }

        int shift = 5 + section.lvl;
        int sample = 1 << section.lvl;
        int baseX = section.x << shift;
        int baseY = section.y << shift;
        int baseZ = section.z << shift;
        long[] data = section._unsafeGetRawDataArray();

        for (int lx = 0; lx < 32; lx++) {
            int vx0 = baseX + (lx * sample);
            int vx1 = vx0 + sample - 1;
            if (vx1 < MIN_X || vx0 > MAX_X) {
                continue;
            }
            for (int lz = 0; lz < 32; lz++) {
                int vz0 = baseZ + (lz * sample);
                int vz1 = vz0 + sample - 1;
                if (vz1 < MIN_Z || vz0 > MAX_Z) {
                    continue;
                }
                for (int ly = 0; ly < 32; ly++) {
                    int vy0 = baseY + (ly * sample);
                    int vy1 = vy0 + sample - 1;
                    if (vy1 < MIN_Y || vy0 > MAX_Y) {
                        continue;
                    }
                    data[WorldSection.getIndex(lx, ly, lz)] = Mapper.AIR;
                }
            }
        }
    }

    private static boolean aabbIntersects(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return minX <= MAX_X && maxX >= MIN_X
                && minY <= MAX_Y && maxY >= MIN_Y
                && minZ <= MAX_Z && maxZ >= MIN_Z;
    }
}
