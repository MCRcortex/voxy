package me.cortex.neovoxy.common.config.compressors;

/*
public class LZMACompressor implements StorageCompressor {
    private static final ThreadLocal<Pair<byte[], ResettableArrayCache>> CACHE_THREAD_LOCAL = ThreadLocal.withInitial(()->new Pair<>(new byte[SaveLoadSystem.BIGGEST_SERIALIZED_SECTION_SIZE], new ResettableArrayCache(new ArrayCache())));
    private static final ThreadLocalMemoryBuffer SCRATCH = new ThreadLocalMemoryBuffer(SaveLoadSystem.BIGGEST_SERIALIZED_SECTION_SIZE + 1024);

    public LZMACompressor(int compressionLevel) {

    }

    @Override
    public MemoryBuffer compress(MemoryBuffer saveData) {
        MemoryBuffer res = new MemoryBuffer(saveData.size+1024);
        try {
            var cache = CACHE_THREAD_LOCAL.get();
            var xzCache = cache.right();
            xzCache.reset();
            var out = new FinishableOutputStream() {
                private final long ptr = res.address+4;
                private long size = 0;
                @Override
                public void write(int b) throws IOException {
                    MemoryUtil.memPutByte(this.ptr+this.size++, (byte) b);
                }

                @Override
                public void write(byte[] b) throws IOException {
                    UnsafeUtil.memcpy(b, this.ptr+this.size); this.size+=b.length;
                }
            };
            var bCache = cache.left();
            UnsafeUtil.memcpy(saveData.address, (int) saveData.size, bCache);
            var stream = new XZOutputStream(out, new LZMA2Options(3), XZ.CHECK_NONE, xzCache);
            stream.write(bCache);
            stream.close();
            MemoryUtil.memPutInt(res.address, (int) saveData.size);
            return res.subSize(out.size+4);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public MemoryBuffer decompress(MemoryBuffer saveData) {
        var ret = SCRATCH.get().createUntrackedUnfreeableReference();

        try {
            var cache = CACHE_THREAD_LOCAL.get();
            var xzCache = cache.right();
            xzCache.reset();
            var bCache = cache.left();
            var stream = new XZInputStream(new InputStream() {
                private long ptr = saveData.address+4;

                @Override
                public int read() {
                    return MemoryUtil.memGetByte(this.ptr++) & 0xFF;
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    len = Math.min(len, this.available());
                    UnsafeUtil.memcpy(this.ptr, len, b, off); this.ptr+=len;
                    return len;
                }

                @Override
                public int available() {
                    return (int) (saveData.size-(this.ptr-saveData.address));
                }
            }, -1, false, xzCache);

            stream.read(bCache, 0, MemoryUtil.memGetInt(saveData.address));
            UnsafeUtil.memcpy(bCache, MemoryUtil.memGetInt(saveData.address), ret.address);

            stream.close();
            return ret.subSize(MemoryUtil.memGetInt(saveData.address));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {

    }

    public static class Config extends CompressorConfig {
        public int compressionLevel;

        @Override
        public StorageCompressor build(ConfigBuildCtx ctx) {
            return new LZMACompressor(this.compressionLevel);
        }

        public static String getConfigTypeName() {
            return "LZMA2";
        }
    }
}
 */
