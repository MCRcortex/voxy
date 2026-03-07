package me.cortex.voxy.client.core.util;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.cortex.voxy.client.core.gpu.IGpuBuffer;
import me.cortex.voxy.client.core.rendering.util.DownloadStream;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.util.Pair;
import me.cortex.voxy.common.util.TrackedObject;
import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;
import java.util.function.Consumer;

import static org.lwjgl.opengl.ARBTimerQuery.GL_TIMESTAMP;
import static org.lwjgl.opengl.ARBTimerQuery.glQueryCounter;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL11.glFlush;
import static org.lwjgl.opengl.GL15.glDeleteQueries;
import static org.lwjgl.opengl.GL15.glGenQueries;
import static org.lwjgl.opengl.GL15C.*;
import static org.lwjgl.opengl.GL33.glGetQueryObjecti64;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL44.GL_QUERY_RESULT_NO_WAIT;
import static org.lwjgl.opengl.GL45.glGetQueryBufferObjectui64v;

public class GPUTiming {
    public static GPUTiming INSTANCE = new GPUTiming();

    private final GlTimestampQuerySet timingSet = new GlTimestampQuerySet();

    public void marker() {
        this.timingSet.capture(0);
    }

    public void tick() {
        this.timingSet.download((meta,data)->{
            long current = data[0];
            for (int i = 1; i < meta.length; i++) {
                long next = data[i];
                long delta = next - current;
                //System.out.println(delta);
                current = next;
            }
        });
        this.timingSet.tick();
    }

    public void free() {
        this.timingSet.free();
    }

    public interface TimingDataConsumer {
        void accept(int[] metadata, long[] timings);
    }
    private static final class GlTimestampQuerySet extends TrackedObject {
        private record InflightRequest(int[] queries, int[] meta, TimingDataConsumer callback) {
            private boolean callbackIfReady(IntArrayFIFOQueue queryPool) {
                boolean ready = glGetQueryObjecti(this.queries[this.queries.length-1], GL_QUERY_RESULT_AVAILABLE) == GL_TRUE;
                if (!ready) {
                    return false;
                }
                long[] results = new long[this.queries.length];
                for (int i = 0; i < this.queries.length; i++) {
                    results[i] = glGetQueryObjecti64(this.queries[i], GL_QUERY_RESULT);
                    queryPool.enqueue(this.queries[i]);
                }
                this.callback.accept(this.meta, results);
                return true;
            }
        }
        private final IntArrayFIFOQueue POOL = new IntArrayFIFOQueue();
        private final ObjectArrayFIFOQueue<InflightRequest> INFLIGHT = new ObjectArrayFIFOQueue();

        private final int[] queries = new int[64];
        private final int[] metadata = new int[64];
        private int index;


        public void capture(int metadata) {
            if (this.index > this.metadata.length) {
                throw new IllegalStateException();
            }
            int slot = this.index++;
            this.metadata[slot] = metadata;
            int query = this.getQuery();
            glQueryCounter(query, GL_TIMESTAMP);
            this.queries[slot] = query;

        }

        public void download(TimingDataConsumer consumer) {
            var queries = Arrays.copyOf(this.queries, this.index);
            var metadata = Arrays.copyOf(this.metadata, this.index);
            this.index = 0;
            this.INFLIGHT.enqueue(new InflightRequest(queries, metadata, consumer));
        }

        public void tick() {
            while (!INFLIGHT.isEmpty()) {
                if (INFLIGHT.first().callbackIfReady(POOL)) {
                    INFLIGHT.dequeue();
                } else {
                    break;
                }
            }
        }

        private int getQuery() {
            if (POOL.isEmpty()) {
                return glGenQueries();
            } else {
                return POOL.dequeueInt();
            }
        }

        @Override
        public void free() {
            super.free0();
            while (!POOL.isEmpty()) {
                glDeleteQueries(POOL.dequeueInt());
            }
            while (!INFLIGHT.isEmpty()) {
                glDeleteQueries(INFLIGHT.dequeue().queries);
            }
        }
    }
    /*
    private static final class GlTimestampQuerySet extends TrackedObject {
        private final int query = glGenQueries();
        public final GlBuffer store;
        public final int[] metadata;
        public int index;
        public GlTimestampQuerySet(int maxCount) {
            this.store = new GlBuffer(maxCount*8L);
            this.metadata = new int[maxCount];
        }

        public void capture(int metadata) {
            if (this.index>this.metadata.length) {
                throw new IllegalStateException();
            }
            int slot = this.index++;
            this.metadata[slot] = metadata;
            glQueryCounter(this.query, GL_TIMESTAMP);//This should be gpu side, so should be fast
            glFinish();
            glGetQueryBufferObjectui64v(this.query, this.store.id, GL_QUERY_RESULT_NO_WAIT, slot*8L);
            glMemoryBarrier(-1);
        }

        public void download(TimingDataConsumer consumer) {
            var meta = Arrays.copyOf(this.metadata, this.index);
            this.index = 0;
            //DownloadStream.INSTANCE.download(this.store, buffer->consumer.accept(meta, buffer));
        }

        @Override
        public void free() {
            super.free0();
            glDeleteQueries(this.query);
            this.store.free();
        }
    }*/
}
