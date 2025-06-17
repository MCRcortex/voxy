package me.cortex.voxy.commonImpl.importers;

import com.mojang.serialization.Codec;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.util.UnsafeUtil;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.voxelization.WorldConversionFactory;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.thread.ServiceSlice;
import me.cortex.voxy.common.thread.ServiceThreadPool;
import me.cortex.voxy.common.world.WorldUpdater;
import me.cortex.voxy.common.world.service.SectionSavingService;
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
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.ReadableContainer;
import net.minecraft.world.storage.ChunkCompressionFormat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.lwjgl.system.MemoryUtil;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class WorldImporter implements IDataImporter {
    private final WorldEngine world;
    private final ReadableContainer<RegistryEntry<Biome>> defaultBiomeProvider;
    private final Codec<ReadableContainer<RegistryEntry<Biome>>> biomeCodec;
    private final AtomicInteger estimatedTotalChunks = new AtomicInteger();//Slowly converges to the true value
    private final AtomicInteger totalChunks = new AtomicInteger();
    private final AtomicInteger chunksProcessed = new AtomicInteger();

    private final ConcurrentLinkedDeque<Runnable> jobQueue = new ConcurrentLinkedDeque<>();
    private final ServiceSlice threadPool;

    private volatile boolean isRunning;

    public WorldImporter(WorldEngine worldEngine, World mcWorld, ServiceThreadPool servicePool, BooleanSupplier runChecker) {
        this.world = worldEngine;
        this.threadPool = servicePool.createServiceNoCleanup("World importer", 3, ()->()->this.jobQueue.poll().run(), runChecker);

        var biomeRegistry = mcWorld.getRegistryManager().getOrThrow(RegistryKeys.BIOME);
        var defaultBiome = biomeRegistry.getOrThrow(BiomeKeys.PLAINS);
        this.defaultBiomeProvider = new ReadableContainer<>() {
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
            public PalettedContainer<RegistryEntry<Biome>> copy() {
                return null;
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
                biomeRegistry.getIndexedEntries(), biomeRegistry.getEntryCodec(), PalettedContainer.PaletteProvider.BIOME, biomeRegistry.getOrThrow(BiomeKeys.PLAINS)
        );
    }


    @Override
    public void runImport(IUpdateCallback updateCallback, ICompletionCallback completionCallback) {
        if (this.isRunning) {
            throw new IllegalStateException();
        }
        if (this.worker == null) {//Can happen if no files
            completionCallback.onCompletion(0);
            return;
        }
        this.isRunning = true;
        this.world.acquireRef();
        this.updateCallback = updateCallback;
        this.completionCallback = completionCallback;
        this.worker.start();
    }

    @Override
    public WorldEngine getEngine() {
        return this.world;
    }

    public void shutdown() {
        this.isRunning = false;
        if (this.worker != null) {
            try {
                this.worker.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        if (!this.threadPool.isFreed()) {
            this.world.releaseRef();
            this.threadPool.shutdown();
        }
    }

    private interface IImporterMethod <T> {
        void importRegion(T file) throws Exception;
    }

    private volatile Thread worker;
    private IUpdateCallback updateCallback;
    private ICompletionCallback completionCallback;
    public void importRegionDirectoryAsync(File directory) {
        var files = directory.listFiles((dir, name) -> {
            var sections = name.split("\\.");
            if (sections.length != 4 || (!sections[0].equals("r")) || (!sections[3].equals("mca"))) {
                Logger.error("Unknown file: " + name);
                return false;
            }
            return true;
        });
        if (files == null) {
            return;
        }
        Arrays.sort(files, File::compareTo);
        this.importRegionsAsync(files, this::importRegionFile);
    }

    public void importZippedRegionDirectoryAsync(File zip, String innerDirectory) {
        try {
            innerDirectory = innerDirectory.replace("\\\\", "\\").replace("\\", "/");
            var file = ZipFile.builder().setFile(zip).get();
            ArrayList<ZipArchiveEntry> regions = new ArrayList<>();
            for (var e = file.getEntries(); e.hasMoreElements();) {
                var entry = e.nextElement();
                if (entry.isDirectory()||!entry.getName().startsWith(innerDirectory)) {
                    continue;
                }
                var parts = entry.getName().split("/");
                var name = parts[parts.length-1];
                var sections = name.split("\\.");
                if (sections.length != 4 || (!sections[0].equals("r")) || (!sections[3].equals("mca"))) {
                    Logger.error("Unknown file: " + name);
                    continue;
                }
                regions.add(entry);
            }
            this.importRegionsAsync(regions.toArray(ZipArchiveEntry[]::new), (entry)->{
                if (entry.getSize() == 0) {
                    return;
                }
                var buf = new MemoryBuffer(entry.getSize());
                try (var channel = Channels.newChannel(file.getInputStream(entry))) {
                    if (channel.read(buf.asByteBuffer()) != buf.size) {
                        buf.free();
                        throw new IllegalStateException("Could not read full zip entry");
                    }
                }

                var parts = entry.getName().split("/");
                var name = parts[parts.length-1];
                var sections = name.split("\\.");

                try {
                    this.importRegion(buf, Integer.parseInt(sections[1]), Integer.parseInt(sections[2]));
                } catch (NumberFormatException e) {
                    Logger.error("Invalid format for region position, x: \""+sections[1]+"\" z: \"" + sections[2] + "\" skipping region");
                }
                buf.free();
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private <T> void importRegionsAsync(T[] regionFiles, IImporterMethod<T> importer) {
        this.totalChunks.set(0);
        this.estimatedTotalChunks.set(0);
        this.chunksProcessed.set(0);
        this.worker = new Thread(() -> {
            this.estimatedTotalChunks.addAndGet(regionFiles.length*1024);
            for (var file : regionFiles) {
                this.estimatedTotalChunks.addAndGet(-1024);
                try {
                    importer.importRegion(file);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                while ((this.totalChunks.get()-this.chunksProcessed.get() > 10_000) && this.isRunning) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (!this.isRunning) {
                    this.threadPool.blockTillEmpty();
                    this.completionCallback.onCompletion(this.totalChunks.get());
                    this.worker = null;
                    return;
                }
            }
            this.threadPool.blockTillEmpty();
            while (this.chunksProcessed.get() != this.totalChunks.get() && this.isRunning) {
                Thread.yield();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            this.worker = null;
            this.world.releaseRef();
            this.threadPool.shutdown();
            this.completionCallback.onCompletion(this.totalChunks.get());
        });
        this.worker.setName("World importer");
    }

    public boolean isBusy() {
        return this.isRunning || this.worker != null;
    }

    public boolean isRunning() {
        return this.isRunning || (this.worker != null && this.worker.isAlive());
    }

    private void importRegionFile(File file) throws IOException {
        var name = file.getName();
        var sections = name.split("\\.");
        if (sections.length != 4 || (!sections[0].equals("r")) || (!sections[3].equals("mca"))) {
            Logger.error("Unknown file: " + name);
            throw new IllegalStateException();
        }
        int rx = 0;
        int rz = 0;
        try {
            rx = Integer.parseInt(sections[1]);
            rz = Integer.parseInt(sections[2]);
        } catch (NumberFormatException e) {
            Logger.error("Invalid format for region position, x: \""+sections[1]+"\" z: \"" + sections[2] + "\" skipping region");
            return;
        }
        try (var fileStream = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            if (fileStream.size() == 0) {
                return;
            }
            var fileData = new MemoryBuffer(fileStream.size());
            if (fileStream.read(fileData.asByteBuffer(), 0) < 8192) {
                fileData.free();
                Logger.warn("Header of region file invalid");
                return;
            }
            this.importRegion(fileData, rx, rz);
            fileData.free();
        }
    }


    private void importRegion(MemoryBuffer regionFile, int x, int z) {
        //Find and load all saved chunks
        if (regionFile.size < 8192) {//File not big enough
            Logger.warn("Header of region file invalid");
            return;
        }
        for (int idx = 0; idx < 1024; idx++) {
            int sectorMeta = Integer.reverseBytes(MemoryUtil.memGetInt(regionFile.address+idx*4));//Assumes little endian
            if (sectorMeta == 0) {
                //Empty chunk
                continue;
            }
            int sectorStart = sectorMeta>>>8;
            int sectorCount = sectorMeta&((1<<8)-1);

            if (sectorCount == 0) {
                continue;
            }

            //TODO: create memory copy for each section
            if (regionFile.size < ((sectorCount-1) + sectorStart) * 4096L) {
                Logger.warn("Cannot access chunk sector as it goes out of bounds. start bytes: " + (sectorStart*4096) + " sector count: " + sectorCount + " fileSize: " + regionFile.size);
                continue;
            }

            {
                long base = regionFile.address + sectorStart * 4096L;
                int chunkLen = sectorCount * 4096;
                int m = Integer.reverseBytes(MemoryUtil.memGetInt(base));
                byte b = MemoryUtil.memGetByte(base + 4L);
                if (m == 0) {
                    Logger.error("Chunk is allocated, but stream is missing");
                } else {
                    int n = m - 1;
                    if (regionFile.size < (n + sectorStart*4096L)) {
                        Logger.warn("Chunk stream to small");
                    } else if ((b & 128) != 0) {
                        if (n != 0) {
                            Logger.error("Chunk has both internal and external streams");
                        }
                        Logger.error("Chunk has external stream which is not supported");
                    } else if (n > chunkLen-5) {
                        Logger.error("Chunk stream is truncated: expected "+n+" but read " + (chunkLen-5));
                    } else if (n < 0) {
                        Logger.error("Declared size of chunk is negative");
                    } else {
                        var data = new MemoryBuffer(n).cpyFrom(base + 5);
                        this.jobQueue.add(()-> {
                            if (!this.isRunning) {
                                data.free();
                                return;
                            }
                            try {
                                try (var decompressedData = this.decompress(b, data)) {
                                    if (decompressedData == null) {
                                        Logger.error("Error decompressing chunk data");
                                    } else {
                                        var nbt = NbtIo.readCompound(decompressedData);
                                        this.importChunkNBT(nbt, x, z);
                                    }
                                }
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            } finally {
                                data.free();
                            }
                        });
                        this.totalChunks.incrementAndGet();
                        this.estimatedTotalChunks.incrementAndGet();
                        this.threadPool.execute();
                    }
                }
            }
        }
    }

    private static InputStream createInputStream(MemoryBuffer data) {
        return new InputStream() {
            private long offset = 0;
            @Override
            public int read() {
                return MemoryUtil.memGetByte(data.address + (this.offset++)) & 0xFF;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                len = Math.min(len, this.available());
                if (len == 0) {
                    return -1;
                }
                UnsafeUtil.memcpy(data.address+this.offset, len, b, off); this.offset+=len;
                return len;
            }

            @Override
            public int available() {
                return (int) (data.size-this.offset);
            }
        };
    }

    private DataInputStream decompress(byte flags, MemoryBuffer stream) throws IOException {
        ChunkCompressionFormat chunkStreamVersion = ChunkCompressionFormat.get(flags);
        if (chunkStreamVersion == null) {
            Logger.error("Chunk has invalid chunk stream version");
            return null;
        } else {
            return new DataInputStream(chunkStreamVersion.wrap(createInputStream(stream)));
        }
    }

    private void importChunkNBT(NbtCompound chunk, int regionX, int regionZ) {
        if (!chunk.contains("Status")) {
            //Its not real so decrement the chunk
            this.totalChunks.decrementAndGet();
            return;
        }

        //Dont process non full chunk sections
        var status = ChunkStatus.byId(chunk.getString("Status", null));
        if (status != ChunkStatus.FULL && status != ChunkStatus.EMPTY) {//We also import empty since they are from data upgrade
            this.totalChunks.decrementAndGet();
            return;
        }

        try {
            int x = chunk.getInt("xPos", Integer.MIN_VALUE);
            int z = chunk.getInt("zPos", Integer.MIN_VALUE);
            if (x>>5 != regionX || z>>5 != regionZ) {
                Logger.error("Chunk position is not located in correct region, expected: (" + regionX + ", " + regionZ+"), got: " + "(" + (x>>5) + ", " + (z>>5)+"), importing anyway");
            }

            for (var sectionE : chunk.getList("sections").orElseThrow()) {
                var section = (NbtCompound) sectionE;
                int y = section.getInt("Y", Integer.MIN_VALUE);
                this.importSectionNBT(x, y, z, section);
            }
        } catch (Exception e) {
            Logger.error("Exception importing world chunk:",e);
        }

        this.updateCallback.onUpdate(this.chunksProcessed.incrementAndGet(), this.estimatedTotalChunks.get());
    }

    private static final byte[] EMPTY = new byte[0];
    private static final ThreadLocal<VoxelizedSection> SECTION_CACHE = ThreadLocal.withInitial(VoxelizedSection::createEmpty);
    private static final Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC = PalettedContainer.createPalettedContainerCodec(Block.STATE_IDS, BlockState.CODEC, PalettedContainer.PaletteProvider.BLOCK_STATE, Blocks.AIR.getDefaultState());
    private void importSectionNBT(int x, int y, int z, NbtCompound section) {
        if (section.getCompound("block_states").isEmpty()) {
            return;
        }

        byte[] blockLightData = section.getByteArray("BlockLight").orElse(EMPTY);
        byte[] skyLightData = section.getByteArray("SkyLight").orElse(EMPTY);

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

        var blockStatesRes = BLOCK_STATE_CODEC.parse(NbtOps.INSTANCE, section.getCompound("block_states").get());
        if (!blockStatesRes.hasResultOrPartial()) {
            //TODO: if its only partial, it means should try to upgrade the nbt format with datafixerupper probably
            return;
        }
        var blockStates = blockStatesRes.getPartialOrThrow();
        var biomes = this.biomeCodec.parse(NbtOps.INSTANCE, section.getCompound("biomes").get()).result().orElse(this.defaultBiomeProvider);

        VoxelizedSection csec = WorldConversionFactory.convert(
                SECTION_CACHE.get().setPosition(x, y, z),
                this.world.getMapper(),
                blockStates,
                biomes,
                (bx, by, bz) -> {
                    int block = 0;
                    int sky = 0;
                    if (blockLight != null) {
                        block = blockLight.get(bx, by, bz);
                    }
                    if (skyLight != null) {
                        sky = skyLight.get(bx, by, bz);
                    }
                    return (byte) (sky|(block<<4));
                }
        );

        WorldConversionFactory.mipSection(csec, this.world.getMapper());
        WorldUpdater.insertUpdate(this.world, csec);
    }
}
