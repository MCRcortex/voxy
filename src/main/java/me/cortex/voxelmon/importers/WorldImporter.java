package me.cortex.voxelmon.importers;

import com.mojang.serialization.Codec;
import me.cortex.voxelmon.core.util.ByteBufferBackedInputStream;
import me.cortex.voxelmon.core.voxelization.VoxelizedSection;
import me.cortex.voxelmon.core.voxelization.WorldConversionFactory;
import me.cortex.voxelmon.core.world.WorldEngine;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.ReadableContainer;
import net.minecraft.world.storage.ChunkStreamVersion;
import org.lwjgl.system.MemoryUtil;

import java.io.*;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;

public class WorldImporter {
    private final WorldEngine world;
    private final World mcWorld;
    private final Codec<ReadableContainer<RegistryEntry<Biome>>> biomeCodec;

    public WorldImporter(WorldEngine worldEngine, World mcWorld) {
        this.world = worldEngine;
        this.mcWorld = mcWorld;

        var biomeRegistry = mcWorld.getRegistryManager().get(RegistryKeys.BIOME);
        this.biomeCodec = PalettedContainer.createReadableContainerCodec(biomeRegistry.getIndexedEntries(), biomeRegistry.createEntryCodec(), PalettedContainer.PaletteProvider.BIOME, biomeRegistry.entryOf(BiomeKeys.PLAINS));
    }

    private Thread worker;
    public void importWorldAsyncStart(File directory) {
        this.worker = new Thread(() -> {
            Arrays.stream(directory.listFiles()).parallel().forEach(file -> {
                if (!file.isFile()) {
                    return;
                }
                var name = file.getName();
                var sections = name.split("\\.");
                if (sections.length != 4 || (!sections[0].equals("r")) || (!sections[3].equals("mca"))) {
                    System.err.println("Unknown file: " + name);
                    return;
                }
                int rx = Integer.parseInt(sections[1]);
                int rz = Integer.parseInt(sections[2]);
                try {
                    this.importRegionFile(file.toPath(), rx, rz);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            System.err.println("Done");
        });
        this.worker.setName("World importer");
        this.worker.start();
    }

    private void importRegionFile(Path file, int x, int z) throws IOException {
        //if (true) return;
        try (var fileStream = FileChannel.open(file, StandardOpenOption.READ)) {
            var sectorsSavesBB = MemoryUtil.memAlloc(8192);
            if (fileStream.read(sectorsSavesBB, 0) != 8192) {
                System.err.println("Header of region file invalid");
                return;
            }
            sectorsSavesBB.rewind();
            var sectorsSaves = sectorsSavesBB.order(ByteOrder.BIG_ENDIAN).asIntBuffer();

            //Find and load all saved chunks
            for (int idx = 0; idx < 1024; idx++) {
                int sectorMeta = sectorsSaves.get(idx);
                if (sectorMeta == 0) {
                    //Empty chunk
                    continue;
                }
                int sectorStart = sectorMeta>>>8;
                int sectorCount = sectorMeta&((1<<8)-1);
                var data = MemoryUtil.memAlloc(sectorCount*4096).order(ByteOrder.BIG_ENDIAN);
                fileStream.read(data, sectorStart*4096L);
                data.flip();
                {
                    int m = data.getInt();
                    byte b = data.get();
                    if (m == 0) {
                        System.err.println("Chunk is allocated, but stream is missing");
                    } else {
                        int n = m - 1;
                        if ((b & 128) != 0) {
                            if (n != 0) {
                                System.err.println("Chunk has both internal and external streams");
                            }
                            System.err.println("Chunk has external stream which is not supported");
                        } else if (n > data.remaining()) {
                            System.err.println("Chunk stream is truncated: expected "+n+" but read " + data.remaining());
                        } else if (n < 0) {
                            System.err.println("Declared size of chunk is negative");
                        } else {
                            try (var decompressedData = this.decompress(b, new ByteBufferBackedInputStream(data))) {
                                if (decompressedData == null) {
                                    System.err.println("Error decompressing chunk data");
                                } else {
                                    var nbt = NbtIo.readCompound(decompressedData);
                                    this.importChunkNBT(nbt);
                                }
                            }
                        }
                    }
                }

                MemoryUtil.memFree(data);
            }

            MemoryUtil.memFree(sectorsSavesBB);
        }
    }

    private DataInputStream decompress(byte flags, InputStream stream) throws IOException {
        ChunkStreamVersion chunkStreamVersion = ChunkStreamVersion.get(flags);
        if (chunkStreamVersion == null) {
            System.err.println("Chunk has invalid chunk stream version");
            return null;
        } else {
            return new DataInputStream(chunkStreamVersion.wrap(stream));
        }
    }

    private void importChunkNBT(NbtCompound chunk) {
        try {
            int x = chunk.getInt("xPos");
            int z = chunk.getInt("zPos");
            for (var sectionE : chunk.getList("sections", NbtElement.COMPOUND_TYPE)) {
                var section = (NbtCompound) sectionE;
                int y = section.getInt("Y");
                this.importSectionNBT(x, y, z, section);
            }
        } catch (Exception e) {
            System.err.println("Exception importing world chunk:");
            e.printStackTrace();
        }
    }


    private static final Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC = PalettedContainer.createPalettedContainerCodec(Block.STATE_IDS, BlockState.CODEC, PalettedContainer.PaletteProvider.BLOCK_STATE, Blocks.AIR.getDefaultState());
    private void importSectionNBT(int x, int y, int z, NbtCompound section) {
        if (section.getCompound("block_states").isEmpty()) {
            return;
        }

        var blockStates = BLOCK_STATE_CODEC.parse(NbtOps.INSTANCE, section.getCompound("block_states")).result().get();
        var biomes = this.biomeCodec.parse(NbtOps.INSTANCE, section.getCompound("biomes")).result().get();

        VoxelizedSection csec = WorldConversionFactory.convert(
                this.world.getMapper(),
                blockStates,
                biomes,
                (bx, by, bz, state) -> (byte) 0,
                x,
                y,
                z
        );

        this.world.insertUpdate(csec);
    }

}
