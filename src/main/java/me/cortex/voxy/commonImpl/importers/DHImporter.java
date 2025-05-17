package me.cortex.voxy.commonImpl.importers;

import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.thread.ServiceSlice;
import me.cortex.voxy.common.thread.ServiceThreadPool;
import me.cortex.voxy.common.util.Pair;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.voxelization.WorldConversionFactory;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldUpdater;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.common.world.service.SectionSavingService;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import org.apache.commons.io.IOUtils;
import org.tukaani.xz.BasicArrayCache;
import org.tukaani.xz.ResettableArrayCache;
import org.tukaani.xz.XZInputStream;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class DHImporter implements IDataImporter {
    private final Connection db;
    private final WorldEngine engine;
    private final ServiceSlice threadPool;
    private final World world;
    private final int bottomOfWorld;
    private final int worldHeightSections;
    private final RegistryEntry.Reference<Biome> defaultBiome;
    private final Registry<Biome> biomeRegistry;
    private final Registry<Block> blockRegistry;
    private Thread runner;
    private volatile boolean isRunning = false;
    private final AtomicInteger processedChunks = new AtomicInteger();
    private int totalChunks;
    private IUpdateCallback updateCallback;

    private record Task(int x, int z, int fmt, int compression) {
        public long distanceFromZero() {
            return ((long)this.x)*this.x+((long)this.z)*this.z;
        }
    }
    private final ConcurrentLinkedDeque<Task> tasks = new ConcurrentLinkedDeque<>();
    private record WorkCTX(PreparedStatement stmt, ResettableArrayCache cache, long[] storageCache, byte[] colScratch, VoxelizedSection section) {
        public WorkCTX(PreparedStatement stmt, int worldHeight) {
            this(stmt, new ResettableArrayCache(new BasicArrayCache()), new long[64*16*worldHeight], new byte[1<<16], VoxelizedSection.createEmpty());
        }
    }

    public DHImporter(File file, WorldEngine worldEngine, World mcWorld, ServiceThreadPool servicePool, SectionSavingService savingService) {
        this.engine = worldEngine;
        this.world = mcWorld;
        this.biomeRegistry = mcWorld.getRegistryManager().getOrThrow(RegistryKeys.BIOME);
        this.defaultBiome = this.biomeRegistry.getOrThrow(BiomeKeys.PLAINS);
        this.blockRegistry = mcWorld.getRegistryManager().getOrThrow(RegistryKeys.BLOCK);

        this.bottomOfWorld = mcWorld.getBottomY();
        int worldHeight = mcWorld.getHeight();
        this.worldHeightSections = (worldHeight+15)/16;

        String con = "jdbc:sqlite:" + file.getPath();
        try {
            this.db = DriverManager.getConnection(con);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        this.threadPool = servicePool.createService("DH Importer", 1, ()->{
            try {
                var dataFetchStmt = this.db.prepareStatement("SELECT Data,ColumnGenerationStep,Mapping FROM FullData WHERE DetailLevel = 0 AND PosX = ? AND PosZ = ?;");
                var ctx = new WorkCTX(dataFetchStmt, this.worldHeightSections*16);
                return new Pair<>(()->{
                    this.importSection(dataFetchStmt, ctx, this.tasks.poll());
                },()->{
                    try {
                        dataFetchStmt.close();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, ()->savingService.getTaskCount() < 500);
    }

    public void runImport(IUpdateCallback updateCallback, ICompletionCallback completionCallback) {
        if (this.isRunning()) {
            throw new IllegalStateException();
        }
        this.updateCallback = updateCallback;
        this.runner = new Thread(()-> {
            Queue<Task> taskQ = new PriorityQueue<>(Comparator.comparingLong(Task::distanceFromZero));
            try (var stmt = this.db.createStatement()) {
                var resSet = stmt.executeQuery("SELECT PosX,PosZ,CompressionMode,DataFormatVersion FROM FullData WHERE DetailLevel = 0;");
                while (resSet.next()) {
                    int x = resSet.getInt(1);
                    int z = resSet.getInt(2);
                    int compression = resSet.getInt(3);
                    int format = resSet.getInt(4);
                    if (format != 1) {
                        Logger.warn("Unknown format mode: " + format);
                        continue;
                    }
                    if (compression != 3) {
                        Logger.warn("Unknown compression mode: " + compression);
                        continue;
                    }
                    taskQ.add(new Task(x, z, format, compression));
                }
                resSet.close();

            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            this.totalChunks = taskQ.size() * (4*4);//(since there are 4*4 chunks to every dh section)

            while (this.isRunning&&!taskQ.isEmpty()) {
                this.tasks.add(taskQ.poll());
                this.threadPool.execute();

                while (this.tasks.size() > 100 && this.isRunning) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            while (!this.tasks.isEmpty()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            completionCallback.onCompletion(this.processedChunks.get());
            this.shutdown();
        });
        this.isRunning = true;
        this.runner.setDaemon(true);
        this.runner.start();
    }

    private static void readStream(InputStream in, ResettableArrayCache cache, byte[] into) throws IOException {
        cache.reset();
        var stream = new XZInputStream(IOUtils.toBufferedInputStream(in), cache);
        stream.read(into);
        stream.close();
    }

    private static String getSerialBlockState(BlockState state) {
        var props = new ArrayList<>(state.getProperties());
        props.sort((a, b) -> a.getName().compareTo(b.getName()));
        StringBuilder b = new StringBuilder();
        for (var prop : props) {
            String val = "NULL";
            if (state.contains(prop)) {
                val = state.get(prop).toString();
            }
            b.append("{").append(prop.getName()).append(":").append(val).append("}");
        }
        return b.toString();
    }

    //TODO: add global mapping cache (with thread local secondary cache)
    private long[] readMappings(InputStream in, WorkCTX ctx) throws IOException {
        final String BLOCK_STATE_SEPARATOR_STRING = "_DH-BSW_";
        final String STATE_STRING_SEPARATOR = "_STATE_";
        ctx.cache.reset();
        var stream = new DataInputStream(new XZInputStream(IOUtils.toBufferedInputStream(in), ctx.cache));
        int entries = stream.readInt();
        if (entries < 0)
            throw new IllegalStateException();
        long[] out = new long[entries];
        for (int i = 0; i < entries; i++) {
            int biomeId;
            int blockId;
            String encEntry = stream.readUTF();
            int idx = encEntry.indexOf(BLOCK_STATE_SEPARATOR_STRING);
            if (idx == -1)
                throw new IllegalStateException();
            {
                var biomeRes = Identifier.of(encEntry.substring(0, idx));
                var biome = this.biomeRegistry.getEntry(biomeRes).orElse(this.defaultBiome);
                biomeId = this.engine.getMapper().getIdForBiome(biome);
            }
            {
                int b = idx + BLOCK_STATE_SEPARATOR_STRING.length();
                if (encEntry.substring(b).equals("AIR")) {
                    blockId = 0;
                } else {
                    var sIdx = encEntry.indexOf(STATE_STRING_SEPARATOR, b);
                    String bStateStr = null;
                    if (sIdx != -1) {
                        bStateStr = encEntry.substring(sIdx + STATE_STRING_SEPARATOR.length());
                    }
                    var bId = Identifier.of(encEntry.substring(b, sIdx != -1 ? sIdx : encEntry.length()));
                    var block = this.blockRegistry.getEntry(bId).orElse(Blocks.AIR.getRegistryEntry()).value();
                    var state = block.getDefaultState();
                    if (bStateStr != null && block != Blocks.AIR) {
                        boolean found = false;
                        for (BlockState bState : block.getStateManager().getStates()) {
                            if (getSerialBlockState(bState).equals(bStateStr)) {
                                state = bState;
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            Logger.warn("Could not find block state with data", encEntry.substring(b));
                        }
                    }
                    if (block  == Blocks.AIR) {
                        Logger.warn("Could not find block entry with id:", bId);
                    }
                    blockId = this.engine.getMapper().getIdForBlockState(state);
                }
            }
            out[i] = Mapper.composeMappingId((byte) 0, blockId, biomeId);
        }
        stream.close();
        return out;
    }

    private static int getId(long dp) {
        return (int)(dp&Integer.MAX_VALUE);
    }

    private static int getHeight(long dp) {
        return (int)((dp>>>32)&((1<<12)-1));
    }

    private static int getMinHeight(long dp) {
        return (int)((dp>>>(32+12))&((1<<12)-1));
    }

    private static int getSkyLight(long dp) {
        return (int)((dp>>>(32+12+12))&0xF);
    }

    private static int getBlockLight(long dp) {
        return (int)((dp>>>(32+12+12+4))&0xF);
    }

    //TODO: create VoxelizedSection of 32*32*32
    private void readColumnData(int X, int Z, InputStream in, WorkCTX ctx, long[] mapping) throws IOException {
        ctx.cache.reset();
        //TODO: add datacache betweein XZ input stream
        var stream = new DataInputStream(new XZInputStream(IOUtils.toBufferedInputStream(in), -1, false, ctx.cache));
        long[] storage = ctx.storageCache;
        VoxelizedSection section = ctx.section;
        byte[] col = ctx.colScratch;
        for (int x = 0; x < 64; x++) {
            for (int z = 0; z < 64; z++) {
                int bPos = Integer.expand(x&0xF, 0b00_00_0000_0000_1111) |
                           Integer.expand(z, 0b00_11_0000_1111_0000);
                short cl = stream.readShort();
                if (cl < 0) {
                    throw new IllegalStateException();
                }
                stream.read(col, 0, cl*8);
                for (int j = 0; j < cl; j++) {
                    long entry = (long) LONG.get(col, j*8);
                    long mEntry = Mapper.withLight(mapping[getId(entry)], (getBlockLight(entry) << 4) | getSkyLight(entry));
                    int startY = getMinHeight(entry);
                    int tall = getHeight(entry);
                    int endY = Math.min(startY+tall, this.worldHeightSections*16);
                    //if (endY < startY+tall && ((this.worldHeightSections*16)+1 != startY+tall)) {
                    //    int a = 0;
                    //}
                    //Insert all entries into data cache
                    startY = Integer.expand(startY, 0b11111111_00_1111_0000_0000);
                    endY = Integer.expand(endY, 0b11111111_00_1111_0000_0000);
                    final int Msk = 0b11111111_00_1111_0000_0000;
                    final int iMsk1 = (~Msk)+1;
                    for (int y = startY; y != endY; y = (y+iMsk1)&Msk) {
                        storage[y+bPos] = mEntry;
                        //touched[(idx >>> 12)>>6] |= 1L<<(idx&0x3f);
                    }
                }
            }

            if ((x+1)%16==0) {
                for (int sz = 0; sz < 4; sz++) {
                    for (int sy = 0; sy < this.worldHeightSections; sy++) {
                        System.arraycopy(storage, (sz|(sy<<2))<<12, section.section, 0, 16 * 16 * 16);
                        WorldConversionFactory.mipSection(section, this.engine.getMapper());

                        section.setPosition(X*4+(x>>4), sy+(this.bottomOfWorld>>4), (Z*4)+sz);
                        WorldUpdater.insertUpdate(this.engine, section);
                    }

                    int count = this.processedChunks.incrementAndGet();
                    this.updateCallback.onUpdate(count, this.totalChunks);
                }
                Arrays.fill(storage, 0);
                //Process batch
            }
        }
        stream.close();
    }
    private void importSection(PreparedStatement dataFetchStmt, WorkCTX ctx, Task task) {
        if (!this.isRunning) {
            return;
        }
        try {
            dataFetchStmt.setInt(1, task.x);
            dataFetchStmt.setInt(2, task.z);
            try (var rs = dataFetchStmt.executeQuery()) {
                var mapping = readMappings(rs.getBinaryStream(3), ctx);
                //var columnGenStep = new byte[64*64];
                //readStream(rs.getBinaryStream(2), cache, columnGenStep);
                readColumnData(task.x, task.z, rs.getBinaryStream(1), ctx, mapping);
            };
        } catch (SQLException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void shutdown() {
        if (!this.isRunning) {
            return;
        }
        this.isRunning = false;
        while (!this.tasks.isEmpty())
            this.tasks.poll();
        try {
            if (this.runner != Thread.currentThread()) {
                this.runner.join();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        this.threadPool.shutdown();
        try {
            this.db.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        this.updateCallback = null;
        this.runner = null;
    }

    @Override
    public boolean isRunning() {
        return this.isRunning;
    }

    @Override
    public WorldEngine getEngine() {
        return this.engine;
    }

    private static VarHandle create(Class<?> viewArrayClass) {
        return MethodHandles.byteArrayViewVarHandle(viewArrayClass, ByteOrder.BIG_ENDIAN);
    }

    public static final boolean HasRequiredLibraries;

    private static final VarHandle LONG = create(long[].class);
    static {
        boolean hasJDBC = false;
        try {
            Class.forName("org.sqlite.JDBC");
            Class.forName("org.tukaani.xz.XZInputStream");
            hasJDBC = true;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            //throw new RuntimeException(e);
            Logger.warn("Unable to load sqlite JDBC or lzma decompressor, DHImporting wont be available", e);
        }
        HasRequiredLibraries = hasJDBC;
    }

}
