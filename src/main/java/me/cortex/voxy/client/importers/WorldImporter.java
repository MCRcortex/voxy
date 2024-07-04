package me.cortex.voxy.client.importers;

import com.mojang.serialization.Codec;
import me.cortex.voxy.client.core.util.ByteBufferBackedInputStream;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.voxelization.WorldConversionFactory;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.other.Mipper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.collection.IndexedIterable;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.ReadableContainer;
import net.minecraft.world.storage.ChunkCompressionFormat;
import org.lwjgl.system.MemoryUtil;

import java.io.*;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class WorldImporter {
    public interface UpdateCallback {
        void update(int finished, int outof);
    }

    private final WorldEngine world;
    private final ReadableContainer<RegistryEntry<Biome>> defaultBiomeProvider;
    private final Codec<ReadableContainer<RegistryEntry<Biome>>> biomeCodec;
    private final AtomicInteger totalRegions = new AtomicInteger();
    private final AtomicInteger regionsProcessed = new AtomicInteger();

    private volatile boolean isRunning;
    public WorldImporter(WorldEngine worldEngine, World mcWorld) {
        this.world = worldEngine;

        var biomeRegistry = mcWorld.getRegistryManager().get(RegistryKeys.BIOME);
        var defaultBiome = biomeRegistry.entryOf(BiomeKeys.PLAINS);
        this.defaultBiomeProvider = new ReadableContainer<RegistryEntry<Biome>>() {
            @Override
            public RegistryEntry<Biome> get(int x, int y, int z) {
                return defaultBiome;
            }

            @Override
            public void forEachValue(Consumer<RegistryEntry<Biome>> action) {

            }

            @Override
            public void writePacket(PacketByteBuf buf) {

            }

            @Override
            public int getPacketSize() {
                return 0;
            }

            @Override
            public boolean hasAny(Predicate<RegistryEntry<Biome>> predicate) {
                return false;
            }

            @Override
            public void count(PalettedContainer.Counter<RegistryEntry<Biome>> counter) {

            }

            @Override
            public PalettedContainer<RegistryEntry<Biome>> slice() {
                return null;
            }

            @Override
            public Serialized<RegistryEntry<Biome>> serialize(IndexedIterable<RegistryEntry<Biome>> idList, PalettedContainer.PaletteProvider paletteProvider) {
                return null;
            }
        };

        this.biomeCodec = PalettedContainer.createReadableContainerCodec(
                biomeRegistry.getIndexedEntries(), biomeRegistry.getEntryCodec(), PalettedContainer.PaletteProvider.BIOME, biomeRegistry.entryOf(BiomeKeys.PLAINS)
        );
    }


    public void shutdown() {
        this.isRunning = false;
        try {this.worker.join();} catch (InterruptedException e) {throw new RuntimeException(e);}
    }

    private Thread worker;
    public void importWorldAsyncStart(File directory, int threads, UpdateCallback updateCallback, Runnable onCompletion) {
        this.worker = new Thread(() -> {
            this.isRunning = true;
            var workers = new ForkJoinPool(threads);
            var files = directory.listFiles();
            for (var file : files) {
                if (!file.isFile()) {
                    continue;
                }
                var name = file.getName();
                var sections = name.split("\\.");
                if (sections.length != 4 || (!sections[0].equals("r")) || (!sections[3].equals("mca"))) {
                    System.err.println("Unknown file: " + name);
                    continue;
                }
                int rx = Integer.parseInt(sections[1]);
                int rz = Integer.parseInt(sections[2]);
                this.totalRegions.addAndGet(1);
                workers.submit(() -> {
                    try {
                        if (!isRunning) {
                            return;
                        }
                        this.importRegionFile(file.toPath(), rx, rz);
                        int regionsProcessedCount = this.regionsProcessed.addAndGet(1);
                        updateCallback.update(regionsProcessedCount, this.totalRegions.get());
                    } catch (
                            Exception e) {
                        e.printStackTrace();
                    }
                });
            }
            workers.shutdown();
            try {
                workers.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {}
            onCompletion.run();
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
        ChunkCompressionFormat chunkStreamVersion = ChunkCompressionFormat.get(flags);
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

    private static int getIndex(int x, int y, int z) {
        return y << 8 | z << 4 | x;
    }


    private static final Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC = PalettedContainer.createPalettedContainerCodec(Block.STATE_IDS, BlockState.CODEC, PalettedContainer.PaletteProvider.BLOCK_STATE, Blocks.AIR.getDefaultState());
    private void importSectionNBT(int x, int y, int z, NbtCompound section) {
        if (section.getCompound("block_states").isEmpty()) {
            return;
        }

        byte[] blockLightData = section.getByteArray("BlockLight");
        byte[] skyLightData = section.getByteArray("SkyLight");

        ChunkNibbleArray blockLight;
        if (blockLightData.length != 0) {
            blockLight = new ChunkNibbleArray(blockLightData);
        } else {
            blockLight = null;
        }

        ChunkNibbleArray skyLight;
        if (skyLightData.length != 0) {
            skyLight = new ChunkNibbleArray(skyLightData);
        } else {
            skyLight = null;
        }

        var blockStates = BLOCK_STATE_CODEC.parse(NbtOps.INSTANCE, section.getCompound("block_states")).result().get();
        var biomes = this.biomeCodec.parse(NbtOps.INSTANCE, section.getCompound("biomes")).result().orElse(this.defaultBiomeProvider);
        VoxelizedSection csec = WorldConversionFactory.convert(
                this.world.getMapper(),
                blockStates,
                biomes,
                (bx, by, bz, state) -> {
                    int block = 0;
                    int sky = 0;
                    if (blockLight != null) {
                        block = blockLight.get(bx, by, bz);
                    }
                    if (skyLight != null) {
                        sky = skyLight.get(bx, by, bz);
                    }
                    sky = 15-sky;
                    return (byte) (sky|(block<<4));
                },
                x,
                y,
                z
        );

        WorldConversionFactory.mipSection(csec, this.world.getMapper());

        this.world.insertUpdate(csec);
        while (this.world.savingService.getTaskCount() > 4000) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
