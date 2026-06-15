package me.cortex.neovoxy.common.config.compressors;

import me.cortex.neovoxy.common.config.ConfigBuildCtx;
import me.cortex.neovoxy.common.util.MemoryBuffer;
import me.cortex.neovoxy.common.util.ThreadLocalMemoryBuffer;
import me.cortex.neovoxy.common.world.SaveLoadSystem;
import net.jpountz.lz4.LZ4Factory;
import org.lwjgl.system.MemoryUtil;

public class LZ4Compressor implements StorageCompressor {
    private static final ThreadLocalMemoryBuffer SCRATCH = new ThreadLocalMemoryBuffer(SaveLoadSystem.BIGGEST_SERIALIZED_SECTION_SIZE + 1024);

    private final net.jpountz.lz4.LZ4Compressor compressor;
    private final net.jpountz.lz4.LZ4FastDecompressor decompressor;
    public LZ4Compressor() {
        this.decompressor = LZ4Factory.nativeInstance().fastDecompressor();
        this.compressor = LZ4Factory.nativeInstance().fastCompressor();
    }

    @Override
    public MemoryBuffer compress(MemoryBuffer saveData) {
        var res = new MemoryBuffer(this.compressor.maxCompressedLength((int) saveData.size)+4);
        MemoryUtil.memPutInt(res.address, (int) saveData.size);
        int size = this.compressor.compress(saveData.asByteBuffer(), 0, (int) saveData.size, res.asByteBuffer(), 4, (int) res.size-4);
        return res.subSize(size+4);
    }

    @Override
    public MemoryBuffer decompress(MemoryBuffer saveData) {
        var res = SCRATCH.get().createUntrackedUnfreeableReference();
        int size = this.decompressor.decompress(saveData.asByteBuffer(), 4, res.asByteBuffer(), 0, MemoryUtil.memGetInt(saveData.address));
        return res.subSize(size);
    }

    @Override
    public void close() {
    }

    public static class Config extends CompressorConfig {

        @Override
        public StorageCompressor build(ConfigBuildCtx ctx) {
            return new LZ4Compressor();
        }

        public static String getConfigTypeName() {
            return "LZ4";
        }
    }
}
