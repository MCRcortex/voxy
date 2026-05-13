package me.cortex.voxy.common.config.compressors;

import me.cortex.voxy.common.config.ConfigBuildCtx;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.util.ThreadLocalMemoryBuffer;
import me.cortex.voxy.common.world.SaveLoadSystem;

import static me.cortex.voxy.common.util.GlobalCleaner.CLEANER;
import static org.lwjgl.util.zstd.Zstd.*;

public class ZSTDCompressor implements StorageCompressor {
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
    /**
     * Thread-local scratch input for compress(). M13 chunk 1 hardening: once the
     * Metal bakery + mesher pipeline started running at full pace, multiple
     * Sodium workers concurrently exercised the save path. SIGSEGV inside
     * {@code nZSTD_compressCCtx} on Apple Silicon traced back to the source
     * MemoryBuffer being freed/modified mid-compress by another thread. We
     * memcpy into a thread-local scratch first, so zstd reads from a buffer
     * only this thread owns for the duration of the call.
     */
    private static final ThreadLocalMemoryBuffer COMPRESS_INPUT_SCRATCH = new ThreadLocalMemoryBuffer(SaveLoadSystem.BIGGEST_SERIALIZED_SECTION_SIZE + 1024);

    private final int level;

    public ZSTDCompressor(int level) {
        this.level = level;
    }

    @Override
    public MemoryBuffer compress(MemoryBuffer saveData) {
        // M13 chunk 1 hardening: copy saveData into a thread-local scratch
        // before handing the pointer to native zstd. On Apple Silicon, once
        // the Metal bakery + mesher pipeline started exercising the save
        // path at full pace from multiple Sodium workers, nZSTD_compressCCtx
        // SIGSEGV'd reading saveData — most-likely cause is a concurrent
        // free/modify on the source buffer by another worker. A thread-local
        // scratch is owned solely by this thread for the call's duration.
        long size = saveData.size;
        MemoryBuffer stableInput = COMPRESS_INPUT_SCRATCH.get();
        if (stableInput.size < size) {
            // Source larger than scratch (rare — exceeds BIGGEST_SERIALIZED_SECTION_SIZE).
            // Fall back to a one-shot copy that we free after compress.
            MemoryBuffer oneShot = new MemoryBuffer(size).cpyFrom(saveData.address);
            MemoryBuffer compressedData = new MemoryBuffer((int)ZSTD_COMPRESSBOUND(size));
            long compressedSize = nZSTD_compressCCtx(COMPRESSION_CTX.get().ptr, compressedData.address, compressedData.size, oneShot.address, size, this.level);
            oneShot.free();
            return compressedData.subSize(compressedSize);
        }
        // Scratch path: copy source bytes into the thread-local buffer, then
        // hand the stable pointer to zstd. Reuses the same backing memory
        // across invocations on the same thread — no allocation churn.
        me.cortex.voxy.common.util.UnsafeUtil.memcpy(saveData.address, stableInput.address, size);

        MemoryBuffer compressedData = new MemoryBuffer((int)ZSTD_COMPRESSBOUND(size));
        long compressedSize = nZSTD_compressCCtx(COMPRESSION_CTX.get().ptr, compressedData.address, compressedData.size, stableInput.address, size, this.level);
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
