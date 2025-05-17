package me.cortex.voxy.common.config.compressors;

import me.cortex.voxy.common.config.ConfigBuildCtx;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.util.ThreadLocalMemoryBuffer;
import me.cortex.voxy.common.world.SaveLoadSystem;

import java.lang.ref.Cleaner;

import static org.lwjgl.util.zstd.Zstd.*;

public class ZSTDCompressor implements StorageCompressor {
    private static final Cleaner CLEANER = Cleaner.create();
    private record Ref(long ptr) {}

    private static Ref createCleanableCompressionContext() {
        long ctx = ZSTD_createCCtx();
        var ref = new Ref(ctx);
        CLEANER.register(ref, ()->ZSTD_freeCCtx(ctx));
        return ref;
    }

    private static Ref createCleanableDecompressionContext() {
        long ctx = ZSTD_createDCtx();
        nZSTD_DCtx_setParameter(ctx, ZSTD_d_experimentalParam3, 1);//experimental ZSTD_d_forceIgnoreChecksum
        var ref = new Ref(ctx);
        CLEANER.register(ref, ()->ZSTD_freeDCtx(ctx));
        return ref;
    }

    private static final ThreadLocal<Ref> COMPRESSION_CTX = ThreadLocal.withInitial(ZSTDCompressor::createCleanableCompressionContext);
    private static final ThreadLocal<Ref> DECOMPRESSION_CTX = ThreadLocal.withInitial(ZSTDCompressor::createCleanableDecompressionContext);

    private static final ThreadLocalMemoryBuffer SCRATCH = new ThreadLocalMemoryBuffer(SaveLoadSystem.BIGGEST_SERIALIZED_SECTION_SIZE + 1024);

    private final int level;

    public ZSTDCompressor(int level) {
        this.level = level;
    }

    @Override
    public MemoryBuffer compress(MemoryBuffer saveData) {
        MemoryBuffer compressedData = new MemoryBuffer((int)ZSTD_COMPRESSBOUND(saveData.size));
        long compressedSize = nZSTD_compressCCtx(COMPRESSION_CTX.get().ptr, compressedData.address, compressedData.size, saveData.address, saveData.size, this.level);
        return compressedData.subSize(compressedSize);
    }

    @Override
    public MemoryBuffer decompress(MemoryBuffer saveData) {
        var decompressed = SCRATCH.get().createUntrackedUnfreeableReference();
        long size = nZSTD_decompressDCtx(DECOMPRESSION_CTX.get().ptr, decompressed.address, decompressed.size, saveData.address, saveData.size);
        //TODO:FIXME: DONT ASSUME IT DOESNT FAIL
        return decompressed.subSize(size);
    }

    @Override
    public void close() {

    }

    public static class Config extends CompressorConfig {
        public int compressionLevel;

        @Override
        public StorageCompressor build(ConfigBuildCtx ctx) {
            return new ZSTDCompressor(this.compressionLevel);
        }

        public static String getConfigTypeName() {
            return "ZSTD";
        }
    }
}
