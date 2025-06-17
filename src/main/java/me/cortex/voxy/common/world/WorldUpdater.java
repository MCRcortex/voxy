package me.cortex.voxy.common.world;

import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.world.other.Mapper;

import static me.cortex.voxy.common.world.WorldEngine.*;

public class WorldUpdater {
    //TODO: move this to auxilery class  so that it can take into account larger than 4 mip levels
    //Executes an update to the world and automatically updates all the parent mip layers up to level 4 (e.g. where 1 chunk section is 1 block big)

    //NOTE: THIS RUNS ON THE THREAD IT WAS EXECUTED ON, when this method exits, the calling method may assume that VoxelizedSection is no longer needed
    public static void insertUpdate(WorldEngine into, VoxelizedSection section) {//TODO: add a bitset of levels to update and if it should force update

        //Do some very cheeky stuff for MiB
        if (false) {
            int sector = (section.x+512)>>10;
            section.setPosition(section.x-(sector<<10), section.y+16+(256-32-sector*30), section.z);//Note sector size mult is 30 because the top chunk is replicated (and so is bottom chunk)
        }

        if (!into.isLive) throw new IllegalStateException("World is not live");
        boolean shouldCheckEmptiness = false;
        WorldSection previousSection = null;
        final var vdat = section.section;

        for (int lvl = 0; lvl < MAX_LOD_LAYER+1; lvl++) {
            var worldSection = into.acquire(lvl, section.x >> (lvl + 1), section.y >> (lvl + 1), section.z >> (lvl + 1));

            int emptinessStateChange = 0;
            //Propagate the child existence state of the previous iteration to this section
            if (lvl != 0 && shouldCheckEmptiness) {
                emptinessStateChange = worldSection.updateEmptyChildState(previousSection);
                //We kept the previous section acquired, so we need to release it
                previousSection.release();
                previousSection = null;
            }


            int msk = (1<<(lvl+1))-1;
            int bx = (section.x&msk)<<(4-lvl);
            int by = (section.y&msk)<<(4-lvl);
            int bz = (section.z&msk)<<(4-lvl);

            int nonAirCountDelta = 0;
            boolean didStateChange = false;


            {//Do a bunch of funny math
                var secD = worldSection.data;

                int baseVIdx = VoxelizedSection.getBaseIndexForLevel(lvl);
                int baseSec = bx | (bz << 5) | (by << 10);

                int secMsk = 0xF >> lvl;
                secMsk |= (secMsk << 5) | (secMsk << 10);
                int iSecMsk1 =(~secMsk)+1;

                int secIdx = 0;
                //TODO: manually unroll and do e.g. 4 iterations per loop
                for (int i = baseVIdx; i <= (0xFFF >> (lvl * 3)) + baseVIdx; i++) {
                    int cSecIdx = secIdx+baseSec;
                    secIdx = (secIdx + iSecMsk1)&secMsk;

                    long newId = vdat[i];
                    long oldId = secD[cSecIdx]; secD[cSecIdx] = newId;
                    nonAirCountDelta += (Mapper.isAir(newId)?0:1)-(Mapper.isAir(oldId)?0:1);//its 0:1 cause its nonAir
                    didStateChange |= newId != oldId;
                }
            }

            if (nonAirCountDelta != 0) {
                worldSection.addNonEmptyBlockCount(nonAirCountDelta);
                if (lvl == 0) {
                    emptinessStateChange = worldSection.updateLvl0State() ? 2 : 0;
                }
            }

            if (didStateChange||(emptinessStateChange!=0)) {
                into.markDirty(worldSection, (didStateChange?UPDATE_TYPE_BLOCK_BIT:0)|(emptinessStateChange!=0?UPDATE_TYPE_CHILD_EXISTENCE_BIT:0));
            }

            //Need to release the section after using it
            if (didStateChange||(emptinessStateChange==2)) {
                if (emptinessStateChange==2) {
                    //Major state emptiness change, bubble up
                    shouldCheckEmptiness = true;
                    //Dont release the section, it will be released on the next loop
                    previousSection = worldSection;
                } else {
                    //Propagate up without state change
                    shouldCheckEmptiness = false;
                    previousSection = null;
                    worldSection.release();
                }
            } else {
                //If nothing changed just need to release, dont need to update parent mips
                worldSection.release();
                break;
            }
        }

        if (previousSection != null) {
            previousSection.release();
        }
    }

    public static void main(String[] args) {
        int MSK = 0b110110010100;
        int iMSK = ~MSK;
        int iMSK1 = iMSK+1;
        int i = 0;
        do  {
            System.err.println(Integer.toBinaryString(i));
            if (i==MSK) break;
            i = (i+iMSK1)&MSK;
        } while (true);
    }
}
