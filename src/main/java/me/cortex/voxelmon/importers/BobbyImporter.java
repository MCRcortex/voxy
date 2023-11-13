package me.cortex.voxelmon.importers;

import me.cortex.voxelmon.core.world.WorldEngine;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class BobbyImporter {
    private final WorldEngine world;
    private final World mcWorld;
    public BobbyImporter(WorldEngine worldEngine, World mcWorld) {
        this.world = worldEngine;
        this.mcWorld = mcWorld;
    }

    //TODO: make the importer run in another thread with a progress callback
    public void importBobby(Path directory) {
        directory.forEach(child->{
            var file = child.toFile();
            if (!file.isFile()) {
                return;
            }
            var name = file.getName();
            var sections = name.split("\\.");
            if (sections.length != 4 || (!sections[0].equals("r")) || (!sections[3].equals("mca"))) {
                throw new IllegalStateException();
            }
            int rx = Integer.parseInt(sections[1]);
            int rz = Integer.parseInt(sections[2]);
        });
    }

    private void importRegionFile(Path file, int x, int z) throws IOException {
        try (var fileStream = FileChannel.open(file, StandardOpenOption.READ)) {
            var sectorsSavesBB = MemoryUtil.memAlloc(8192);
            if (fileStream.read(sectorsSavesBB) != 8192) {
                throw new IllegalStateException("Header of region file invalid");
            }
            var sectorsSaves = sectorsSavesBB.asIntBuffer();

            //Find and load all saved chunks
            for (int idx = 0; idx < 1024; idx++) {
                int sectorMeta = sectorsSaves.get(idx);
                if (sectorMeta == 0) {
                    //Empty chunk
                    continue;
                }
                int sectorStart = sectorMeta>>>8;
                int sectorCount = sectorMeta&((1<<8)-1);


            }
        }
    }

    private void importChunkNBT(ChunkPos pos, NbtCompound chunk) {

    }
}
