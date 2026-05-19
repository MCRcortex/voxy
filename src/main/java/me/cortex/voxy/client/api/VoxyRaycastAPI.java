package me.cortex.voxy.client.api;

import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class VoxyRaycastAPI {

    private static final int SECTION_MASK = 31;
    private static final int MAX_LOD = 4;

    private VoxyRaycastAPI() {}

    public static RaycastResult raycast(Level level, Vec3 start, Vec3 direction, double maxDistance) {
        WorldEngine engine = WorldIdentifier.ofEngineNullable(level);
        if (engine == null) {
            return null;
        }

        double dx = direction.x;
        double dy = direction.y;
        double dz = direction.z;

        int stepX;
        int stepY;
        int stepZ;
        double tDeltaX;
        double tDeltaY;
        double tDeltaZ;

        if (dx == 0) {
            stepX = 0;
            tDeltaX = Double.POSITIVE_INFINITY;
        } else {
            stepX = dx > 0 ? 1 : -1;
            tDeltaX = Math.abs(1.0 / dx);
        }
        if (dy == 0) {
            stepY = 0;
            tDeltaY = Double.POSITIVE_INFINITY;
        } else {
            stepY = dy > 0 ? 1 : -1;
            tDeltaY = Math.abs(1.0 / dy);
        }
        if (dz == 0) {
            stepZ = 0;
            tDeltaZ = Double.POSITIVE_INFINITY;
        } else {
            stepZ = dz > 0 ? 1 : -1;
            tDeltaZ = Math.abs(1.0 / dz);
        }

        int x = (int) Math.floor(start.x);
        int y = (int) Math.floor(start.y);
        int z = (int) Math.floor(start.z);

        double tMaxX = dx == 0 ? Double.POSITIVE_INFINITY :
            (dx > 0 ? (x + 1 - start.x) : (start.x - x)) * tDeltaX;
        double tMaxY = dy == 0 ? Double.POSITIVE_INFINITY :
            (dy > 0 ? (y + 1 - start.y) : (start.y - y)) * tDeltaY;
        double tMaxZ = dz == 0 ? Double.POSITIVE_INFINITY :
            (dz > 0 ? (z + 1 - start.z) : (start.z - z)) * tDeltaZ;

        // Acquire section for the starting voxel
        WorldSection currentSection = null;
        int currentSectionX = x >> 5;
        int currentSectionY = y >> 5;
        int currentSectionZ = z >> 5;
        int currentLod = 0;
        for (int lod = 0; lod <= MAX_LOD; lod++) {
            int shift = 5 + lod;
            WorldSection sec = engine.acquireIfExists(lod, x >> shift, y >> shift, z >> shift);
            if (sec != null) {
                currentSection = sec;
                currentLod = lod;
                break;
            }
        }

        //Check starting voxel, the ray originates inside it so entry distance is 0
        if (currentSection != null) {
            int lx = (x >> currentLod) & SECTION_MASK;
            int ly = (y >> currentLod) & SECTION_MASK;
            int lz = (z >> currentLod) & SECTION_MASK;
            long voxel = currentSection._unsafeGetRawDataArray()[WorldSection.getIndex(lx, ly, lz)];
            if (!Mapper.isAir(voxel)) {
                int blockId = Mapper.getBlockId(voxel);
                BlockState state = engine.getMapper().getBlockStateFromBlockId(blockId);
                currentSection.release();
                return new RaycastResult(new BlockPos(x, y, z), Direction.UP, start, state);
            }
        }

        Direction lastStepFace = null;

        while (true) {
            double traveled = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
            if (traveled > maxDistance) {
                break;
            }

            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    x += stepX;
                    lastStepFace = stepX > 0 ? Direction.WEST : Direction.EAST;
                    tMaxX += tDeltaX;
                } else {
                    z += stepZ;
                    lastStepFace = stepZ > 0 ? Direction.NORTH : Direction.SOUTH;
                    tMaxZ += tDeltaZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    y += stepY;
                    lastStepFace = stepY > 0 ? Direction.DOWN : Direction.UP;
                    tMaxY += tDeltaY;
                } else {
                    z += stepZ;
                    lastStepFace = stepZ > 0 ? Direction.NORTH : Direction.SOUTH;
                    tMaxZ += tDeltaZ;
                }
            }

            //Reacquire section if we crossed a LOD 0 section boundary
            int scx = x >> 5;
            int scy = y >> 5;
            int scz = z >> 5;
            if (currentSection == null || scx != currentSectionX || scy != currentSectionY || scz != currentSectionZ) {
                if (currentSection != null) {
                    currentSection.release();
                    currentSection = null;
                }
                currentSectionX = scx;
                currentSectionY = scy;
                currentSectionZ = scz;
                for (int lod = 0; lod <= MAX_LOD; lod++) {
                    int shift = 5 + lod;
                    WorldSection sec = engine.acquireIfExists(lod, x >> shift, y >> shift, z >> shift);
                    if (sec != null) {
                        currentSection = sec;
                        currentLod = lod;
                        break;
                    }
                }
            }

            if (currentSection != null) {
                int lx = (x >> currentLod) & SECTION_MASK;
                int ly = (y >> currentLod) & SECTION_MASK;
                int lz = (z >> currentLod) & SECTION_MASK;
                long voxel = currentSection._unsafeGetRawDataArray()[WorldSection.getIndex(lx, ly, lz)];
                if (!Mapper.isAir(voxel)) {
                    int blockId = Mapper.getBlockId(voxel);
                    BlockState state = engine.getMapper().getBlockStateFromBlockId(blockId);
                    Vec3 hitLocation = start.add(direction.scale(traveled));
                    RaycastResult result = new RaycastResult(new BlockPos(x, y, z),
                        lastStepFace != null ? lastStepFace : Direction.UP, hitLocation, state);
                    currentSection.release();
                    return result;
                }
            }
        }

        if (currentSection != null) {
            currentSection.release();
        }
        return null;
    }
}
