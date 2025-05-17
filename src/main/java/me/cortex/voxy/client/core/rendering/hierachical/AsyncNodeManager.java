package me.cortex.voxy.client.core.rendering.hierachical;

import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.cortex.voxy.client.TimingStatistics;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.rendering.ISectionWatcher;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.section.geometry.BasicAsyncGeometryManager;
import me.cortex.voxy.client.core.rendering.section.geometry.BasicSectionGeometryData;
import me.cortex.voxy.client.core.rendering.section.geometry.IGeometryData;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.AllocationArena;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.world.WorldSection;
import org.lwjgl.system.MemoryUtil;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.StampedLock;

import static org.lwjgl.opengl.ARBUniformBufferObject.glBindBufferBase;
import static org.lwjgl.opengl.GL30C.glUniform1ui;
import static org.lwjgl.opengl.GL42C.GL_UNIFORM_BARRIER_BIT;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.GL43C.*;

//TODO: create an "async upload stream", that is, the upload stream is a raw mapped buffer pointer that can be written to
// which is then synced to the gpu on "render thread sync",


//An "async host" for a NodeManager, has specific synchonius entry and exit points
// this is done off thread to reduce the amount of work done on the render thread, improving frame stability and reducing runtime overhead
public class AsyncNodeManager {
    private static final VarHandle RESULT_HANDLE;
    private static final VarHandle RESULT_CACHE_1_HANDLE;
    private static final VarHandle RESULT_CACHE_2_HANDLE;
    static {
        try {
            RESULT_HANDLE = MethodHandles.lookup().findVarHandle(AsyncNodeManager.class, "results", SyncResults.class);
            RESULT_CACHE_1_HANDLE = MethodHandles.lookup().findVarHandle(AsyncNodeManager.class, "resultCache1", SyncResults.class);
            RESULT_CACHE_2_HANDLE = MethodHandles.lookup().findVarHandle(AsyncNodeManager.class, "resultCache2", SyncResults.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private final Thread thread;
    public final int maxNodeCount;
    private final long geometryCapacity;
    private volatile boolean running = true;

    private final NodeManager manager;
    private final BasicAsyncGeometryManager geometryManager;
    private final IGeometryData geometryData;

    private final AtomicInteger workCounter = new AtomicInteger();

    @SuppressWarnings("FieldMayBeFinal")
    private volatile SyncResults results = null, resultCache1 = new SyncResults(), resultCache2 = new SyncResults();


    //locals for during iteration
    private final IntOpenHashSet tlnIdChange = new IntOpenHashSet();//"Encoded" add/remove id, first bit indicates if its add or remove, 1 is add
    //Top bit indicates clear or reset
    private final IntOpenHashSet cleanerIdResetClear = new IntOpenHashSet();//Tells the cleaner if it needs to clear the id to 0, or reset the id to the current frame

    private boolean needsWaitForSync = false;

    public AsyncNodeManager(int maxNodeCount, ISectionWatcher watcher, IGeometryData geometryData) {
        //Note the current implmentation of ISectionWatcher is threadsafe
        //Note: geometry data is the data store/source, not the management, it is just a raw store of data
        // it MUST ONLY be accessed on the render thread
        // AsyncNodeManager will use an AsyncGeometryManager as the manager for the data store, and sync the results on the render thread
        this.geometryData = geometryData;
        this.geometryCapacity = ((BasicSectionGeometryData)geometryData).getGeometryCapacityBytes();

        this.maxNodeCount = maxNodeCount;

        this.thread = new Thread(()->{
            try {
                while (this.running) {
                    this.run();
                }
            } catch (Exception e) {
                Logger.error("Critical error occurred in async processor, things will be broken", e);
            }
        });
        this.thread.setName("Async Node Manager");

        this.geometryManager = new BasicAsyncGeometryManager(((BasicSectionGeometryData)geometryData).getMaxSectionCount(), this.geometryCapacity);
        this.manager = new NodeManager(maxNodeCount, this.geometryManager, watcher);
        //Dont do the move... is just to much effort
        this.manager.setClear(new NodeManager.ICleaner() {
            @Override
            public void alloc(int id) {
                AsyncNodeManager.this.cleanerIdResetClear.remove(id);//Remove clear
                AsyncNodeManager.this.cleanerIdResetClear.add(id|(1<<31));//Add reset
            }

            @Override
            public void move(int from, int to) {
                //noop (sorry :( will cause some perf loss/incorrect cleaning )
            }

            @Override
            public void free(int id) {
                AsyncNodeManager.this.cleanerIdResetClear.remove(id|(1<<31));//Remove reset
                AsyncNodeManager.this.cleanerIdResetClear.add(id);//Add clear
            }
        });
        this.manager.setTLNCallbacks(id->{
            if (!this.tlnIdChange.remove(id)) {
                if (!this.tlnIdChange.add(id|(1<<31))) {
                    throw new IllegalStateException();
                }
            }
        }, id -> {
            if (!this.tlnIdChange.remove(id|(1<<31))) {
                if (!this.tlnIdChange.add(id)) {
                    throw new IllegalStateException();
                }
            }
        });
    }

    private SyncResults getMakeResultObject() {
        SyncResults resultSet = (SyncResults)RESULT_CACHE_1_HANDLE.getAndSet(this, null);
        if (resultSet == null) {//Not in the first object
            resultSet = (SyncResults)RESULT_CACHE_2_HANDLE.getAndSet(this, null);
        }
        if (resultSet == null) {
            throw new IllegalStateException("There should always be an object in the result set cache pair");
        }
        //Reset everything to default
        resultSet.reset();
        return resultSet;
    }

    private final Shader scatterWrite = Shader.make()
            .define("INPUT_BUFFER_BINDING", 0)
            .define("OUTPUT_BUFFER1_BINDING", 1)
            .define("OUTPUT_BUFFER2_BINDING", 2)
            .add(ShaderType.COMPUTE, "voxy:util/scatter.comp")
            .compile();

    private final Shader multiMemcpy = Shader.make()
            .define("INPUT_HEADER_BUFFER_BINDING", 0)
            .define("INPUT_DATA_BUFFER_BINDING", 1)
            .define("OUTPUT_BUFFER_BINDING", 2)
            .add(ShaderType.COMPUTE, "voxy:util/memcpy.comp")
            .compile();

    private void run() {
        if (this.workCounter.get() <= 0) {
            LockSupport.park();
            if (this.workCounter.get() <= 0 || !this.running) {//No work
                return;
            }
            //This is a funny thing, wait a bit, this allows for better batching, but this thread is independent of everything else so waiting a bit should be mostly ok
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        if (!this.running) {
            return;
        }


        int workDone = 0;

        {
            LongOpenHashSet add = null;
            LongOpenHashSet rem = null;
            long stamp = this.tlnLock.writeLock();

            if (!this.tlnAdd.isEmpty()) {
                add = new LongOpenHashSet(this.tlnAdd);
                this.tlnAdd.clear();
            }
            if (!this.tlnRem.isEmpty()) {
                rem = new LongOpenHashSet(this.tlnRem);
                this.tlnRem.clear();
            }

            this.tlnLock.unlockWrite(stamp);
            int work = 0;
            if (rem != null) {
                var iter = rem.longIterator();
                while (iter.hasNext()) {
                    this.manager.removeTopLevelNode(iter.nextLong());
                    work++;
                }
            }

            if (add != null) {
                var iter = add.longIterator();
                while (iter.hasNext()) {
                    this.manager.insertTopLevelNode(iter.nextLong());
                    work++;
                }
            }

            workDone += work;
        }

        do {
            var job = this.childUpdateQueue.poll();
            if (job == null)
                break;
            workDone++;
            this.manager.processChildChange(job.key, job.getNonEmptyChildren());
            job.release();
        } while (true);

        final int UPLOAD_LIMIT = 500;
        for (int limit = 0; limit < UPLOAD_LIMIT/2; limit++) //Limit uploading, TODO: limit this by frame sync count, not here
        {
            var job = this.geometryUpdateQueue.poll();
            if (job == null)
                break;
            workDone++;
            this.manager.processGeometryResult(job);
        }

        while (true) {//Process all request batches
            var job = this.requestBatchQueue.poll();
            if (job == null)
                break;
            workDone++;
            long ptr = job.address;
            int count = MemoryUtil.memGetInt(ptr);
            ptr += 8;//Its 8 to keep alignment
            if (job.size < count * 8L + 8) {
                throw new IllegalStateException();
            }
            for (int i = 0; i < count; i++) {
                long pos = ((long) MemoryUtil.memGetInt(ptr)) << 32; ptr += 4;
                pos |= Integer.toUnsignedLong(MemoryUtil.memGetInt(ptr)); ptr += 4;
                this.manager.processRequest(pos);
            }
            job.free();
        }


        do {
            var job = this.removeBatchQueue.poll();
            if (job == null)
                break;
            workDone++;
            long ptr = job.address;
            for (int i = 0; i < NodeCleaner.OUTPUT_COUNT; i++) {
                long pos = ((long) MemoryUtil.memGetInt(ptr)) << 32; ptr += 4;
                pos |= Integer.toUnsignedLong(MemoryUtil.memGetInt(ptr)); ptr += 4;

                if (pos == -1) {
                    //TODO: investigate how or what this happens
                    continue;
                }

                this.manager.removeNodeGeometry(pos);
            }
            job.free();
        } while (true);

        if (this.workCounter.addAndGet(-workDone) < 0) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            //Due to synchronization "issues", wait a millis (give up this time slice)
            if (this.workCounter.get() < 0) {
                Logger.error("Work counter less than zero, hope it fixes itself...");
                //return;
            }
        }

        if (workDone == 0) {//Nothing happened, which is odd, but just return
            return;
        }
        //=====================
        //process output events and atomically sync to results

        //Events into manager
        //manager.insertTopLevelNode();
        //manager.removeTopLevelNode();

        //manager.removeNodeGeometry();

        //manager.processRequest();
        //manager.processChildChange();
        //manager.processGeometryResult();


        //Outputs from manager
        //manager.setClear();
        //manager.setTLNCallbacks();

        //manager.writeChanges()


        //Run in a loop, process all the input events, collect the output events merge with previous and publish
        // note: inner event processing is a loop, is.. should be synced to attomic/volatile variable that is being watched
        // when frametime comes around, want to exit out as quick as possible, or make the event publishing
        // "effectivly immediately", that is, atomicly swap out the render side event updates

        //like
        // var current = <new events>
        // var old = getAndSet(this.events, null);
        // if (old != null) {current = merge(old, current);}
        // getAndSet(this.events, current);
        // if (old == null) {cleanAllEventsUpToThisPoint();}//(i.e. clear any buffers or maps containing data revolving around uncommited render thread data events)

        // this creates a lock free event update loop, allowing the render thread to never stall on waiting

        //TODO: NOTE: THIS MUST BE A SINGLE OBJECT THAT IS EXCHANGED
        // for it to be effectivly synchonized all outgoing events/effects _MUST_ happen at the same time
        // for this to be lock free an entire object containing ALL the events that must be synced must be exchanged


        //TODO: also note! this can be done for the processing of rendered out block models!!
        // (it might be able to also be put in this thread, maybe? but is proabably worth putting in own thread for latency reasons)
        if (this.needsWaitForSync) {
            while (RESULT_HANDLE.get(this) != null && this.running) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }


        var prev = (SyncResults) RESULT_HANDLE.getAndSet(this, null);
        SyncResults results = null;
        if (prev == null) {
            this.needsWaitForSync = false;
            results = this.getMakeResultObject();
            //Clear old data (if it exists), create a new result set
            results.tlnDelta.addAll(this.tlnIdChange);
            this.tlnIdChange.clear();

            if (!this.geometryManager.getUploads().isEmpty()){//Put in new data into sync set
                var iter = this.geometryManager.getUploads().int2ObjectEntrySet().fastIterator();
                while (iter.hasNext()) {
                    var val = iter.next();
                    results.geometryUpload.upload(val.getIntKey(), val.getValue());
                    val.getValue().free();
                }
                this.geometryManager.getUploads().clear();
            }

            this.geometryManager.getHeapRemovals().clear();//We dont do removals on new data (as there is "none")
            results.cleanerOperations.addAll(this.cleanerIdResetClear); this.cleanerIdResetClear.clear();
        } else {
            results = prev;
            // merge with the previous result set

            if (!this.tlnIdChange.isEmpty()) {//Merge top level node id changes
                var iter = this.tlnIdChange.intIterator();
                while (iter.hasNext()) {
                    int val = iter.nextInt();
                    if (!results.tlnDelta.remove(val ^ (1 << 31))) {//Remove opposite
                        results.tlnDelta.add(val);//Add this if not added
                    }
                }
                this.tlnIdChange.clear();
            }

            if (!this.cleanerIdResetClear.isEmpty()) {//Merge top level node id changes
                var iter = this.cleanerIdResetClear.intIterator();
                while (iter.hasNext()) {
                    int val = iter.nextInt();
                    results.cleanerOperations.remove(val^(1<<31));//Remove opposite
                    results.cleanerOperations.add(val);//Add this
                }
                this.cleanerIdResetClear.clear();
            }

            if (!this.geometryManager.getHeapRemovals().isEmpty()) {//Remove and free all the removed geometry uploads
                var rem = this.geometryManager.getHeapRemovals();
                var iter = rem.intIterator();
                while (iter.hasNext()) {
                    results.geometryUpload.remove(iter.nextInt());
                }
                rem.clear();
            }

            if (!this.geometryManager.getUploads().isEmpty()) {//Add all the new uploads to the result set
                var add = this.geometryManager.getUploads();
                var iter = add.int2ObjectEntrySet().fastIterator();
                while (iter.hasNext()) {
                    var val = iter.next();
                    results.geometryUpload.upload(val.getIntKey(), val.getValue());
                    val.getValue().free();
                }
                add.clear();
            }
        }

        {//This is the same regardless of if is a merge or new result
            //Geometry id metadata updates
            if (!this.geometryManager.getUpdateIds().isEmpty()) {
                var ids = this.geometryManager.getUpdateIds();
                var iter = ids.intIterator();
                while (iter.hasNext()) {
                    int val = iter.nextInt();
                    int scatterAddr = (val<<1)|(1<<31);//Since we write to the second buffer

                    //Geometry buffer is index of 1, so mutate to put it in that location, it is also 32 bytes, so needs to be split into 2 separate scatter writes
                    long ptrA = results.getScatterWritePtr(scatterAddr+0, 1);
                    long ptrB = results.getScatterWritePtr(scatterAddr+1, 0);

                    //Write update data
                    this.geometryManager.writeMetadataSplit(val, ptrA, ptrB);
                }
                ids.clear();
            }

            //Node updates
            if (!this.manager.getNodeUpdates().isEmpty()) {
                var ids = this.manager.getNodeUpdates();
                var iter = ids.intIterator();
                while (iter.hasNext()) {
                    int val = iter.nextInt();
                    //Dont need to modify the write location since we write to buffer 0
                    long ptr = results.getScatterWritePtr(val);
                    //Write updated data
                    this.manager.writeNode(val, ptr);
                }
                ids.clear();
            }
        }

        results.geometrySectionCount = this.geometryManager.getSectionCount();
        results.usedGeometry = this.geometryManager.getGeometryUsedBytes();
        results.currentMaxNodeId = this.manager.getCurrentMaxNodeId();

        this.needsWaitForSync |= results.geometryUpload.currentElemCopyAmount*8L > 4L<<20;//4mb limit per frame

        if (!RESULT_HANDLE.compareAndSet(this, null, results)) {
            throw new IllegalArgumentException("Should always have null");
        }
    }

    private IntConsumer tlnAddCallback; private IntConsumer tlnRemoveCallback;
    //Render thread synchronization
    public void tick(GlBuffer nodeBuffer, NodeCleaner cleaner) {//TODO: dont pass nodeBuffer here??, do something else thats better
        var results = (SyncResults)RESULT_HANDLE.getAndSet(this, null);//Acquire the results
        if (results == null) {//There are no new results to process, return
            return;
        }

        //top level node add/remove
        if (!results.tlnDelta.isEmpty()) {
            var iter = results.tlnDelta.intIterator();
            while (iter.hasNext()) {
                int val = iter.nextInt();
                if ((val&(1<<31))!=0) {//Add node
                    this.tlnAddCallback.accept(val&(-1>>>1));
                } else {
                    this.tlnRemoveCallback.accept(val);
                }
            }
            //Dont need to clear as is not used again
        }

        {//Update basic geometry data
            var store = (BasicSectionGeometryData)this.geometryData;

            store.setSectionCount(results.geometrySectionCount);

            var upload = results.geometryUpload;
            if (!upload.dataUploadPoints.isEmpty()) {
                TimingStatistics.A.start();

                int copies = upload.dataUploadPoints.size();
                int scratchSize = (int) upload.arena.getSize() * 8;
                long ptr = UploadStream.INSTANCE.rawUploadAddress(scratchSize + copies * 16);
                MemoryUtil.memCopy(upload.scratchHeaderBuffer.address, UploadStream.INSTANCE.getBaseAddress() + ptr, copies * 16L);
                MemoryUtil.memCopy(upload.scratchDataBuffer.address, UploadStream.INSTANCE.getBaseAddress() + ptr + copies * 16L, scratchSize);
                UploadStream.INSTANCE.commit();//Commit the buffer

                this.multiMemcpy.bind();
                glBindBufferRange(GL_SHADER_STORAGE_BUFFER, 0, UploadStream.INSTANCE.getRawBufferId(), ptr, copies*16L);
                glBindBufferRange(GL_SHADER_STORAGE_BUFFER, 1, UploadStream.INSTANCE.getRawBufferId(), ptr+copies*16L, scratchSize);
                glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, ((BasicSectionGeometryData) this.geometryData).getGeometryBuffer().id);

                if (copies > 500) {
                    Logger.warn("Large amount of copies, lag will probably happen: " + copies);
                }

                glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
                glDispatchCompute(copies, 1, 1);//Execute the copies
                glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

                TimingStatistics.A.stop();
            }
        }

        TimingStatistics.B.start();
        if (!results.scatterWriteLocationMap.isEmpty()) {//Scatter write
            int count = results.scatterWriteLocationMap.size();//Number of writes, not chunks or uvec4 count
            int chunks = (count+3)/4;
            int streamSize = chunks*80;//80 bytes per chunk, it is guaranteed the buffer is big enough
            long ptr = UploadStream.INSTANCE.rawUploadAddress(streamSize + 16);//Ensure it is 16 byte aligned
            ptr = (ptr+15L)&~0xFL;//Align up to 16 bytes
            MemoryUtil.memCopy(results.scatterWriteBuffer.address, UploadStream.INSTANCE.getBaseAddress() + ptr, streamSize);
            UploadStream.INSTANCE.commit();//Commit the buffer

            this.scatterWrite.bind();
            glBindBufferRange(GL_SHADER_STORAGE_BUFFER, 0, UploadStream.INSTANCE.getRawBufferId(), ptr, streamSize);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, nodeBuffer.id);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, ((BasicSectionGeometryData) this.geometryData).getMetadataBuffer().id);
            glUniform1ui(0, count);
            glMemoryBarrier(GL_UNIFORM_BARRIER_BIT|GL_SHADER_STORAGE_BARRIER_BIT);
            glDispatchCompute((count+127)/128, 1, 1);
            glMemoryBarrier(GL_UNIFORM_BARRIER_BIT|GL_SHADER_STORAGE_BARRIER_BIT);
        }
        TimingStatistics.B.stop();

        TimingStatistics.C.start();
        if (!results.cleanerOperations.isEmpty()) {
            cleaner.updateIds(results.cleanerOperations);
        }
        TimingStatistics.C.stop();

        this.currentMaxNodeId = results.currentMaxNodeId;
        this.usedGeometryAmount = results.usedGeometry;

        //Insert the result set into the cache
        if (!RESULT_CACHE_1_HANDLE.compareAndSet(this, null, results)) {
            //Failed to insert into result set 1, insert it into result set 2
            if (!RESULT_CACHE_2_HANDLE.compareAndSet(this, null, results)) {
                throw new IllegalStateException("Could not insert result into cache");
            }
        }
    }


    public void setTLNAddRemoveCallbacks(IntConsumer add, IntConsumer remove) {
        this.tlnAddCallback = add;
        this.tlnRemoveCallback = remove;
    }

    private int currentMaxNodeId = 0;
    public int getCurrentMaxNodeId() {
        return this.currentMaxNodeId;
    }

    private long usedGeometryAmount = 0;
    public long getUsedGeometryCapacity() {
        return this.usedGeometryAmount;
    }

    public long getGeometryCapacity() {
        return this.geometryCapacity;
    }


    //==================================================================================================================
    //Incoming events

    //TODO: add atomic counters for each event type probably
    private final ConcurrentLinkedDeque<MemoryBuffer> requestBatchQueue = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<WorldSection> childUpdateQueue = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<BuiltSection> geometryUpdateQueue = new ConcurrentLinkedDeque<>();

    private final ConcurrentLinkedDeque<MemoryBuffer> removeBatchQueue = new ConcurrentLinkedDeque<>();

    private final StampedLock tlnLock = new StampedLock();
    private final LongOpenHashSet tlnAdd = new LongOpenHashSet();
    private final LongOpenHashSet tlnRem = new LongOpenHashSet();

    private void addWork() {
        if (!this.running) throw new IllegalStateException("Not running");
        if (this.workCounter.getAndIncrement() == 0) {
            LockSupport.unpark(this.thread);
        }
    }

    public void submitRequestBatch(MemoryBuffer batch) {//Only called from render thread
        this.requestBatchQueue.add(batch);
        this.addWork();
    }

    public void submitChildChange(WorldSection section) {
        if (!this.running) {
            return;
        }
        section.acquire();//We must acquire the section before putting in the queue
        this.childUpdateQueue.add(section);
        this.addWork();
    }

    public void submitGeometryResult(BuiltSection geometry) {
        if (!this.running) {
            geometry.free();
            return;
        }
        this.geometryUpdateQueue.add(geometry);
        this.addWork();
    }

    public void submitRemoveBatch(MemoryBuffer batch) {//Only called from render thread
        this.removeBatchQueue.add(batch);
        this.addWork();
    }

    public void addTopLevel(long section) {//Only called from render thread
        if (!this.running) throw new IllegalStateException("Not running");
        long stamp = this.tlnLock.writeLock();
        int state = this.tlnAdd.add(section)?1:0;
        state -= this.tlnRem.remove(section)?1:0;
        if (state != 0) {
            if (this.workCounter.getAndAdd(state) == 0) {
                LockSupport.unpark(this.thread);
            }
        }
        this.tlnLock.unlockWrite(stamp);
    }

    public void removeTopLevel(long section) {//Only called from render thread
        if (!this.running) throw new IllegalStateException("Not running");
        long stamp = this.tlnLock.writeLock();
        int state = this.tlnRem.add(section)?1:0;
        state -= this.tlnAdd.remove(section)?1:0;
        if (state != 0) {
            if (this.workCounter.getAndAdd(state) == 0) {
                LockSupport.unpark(this.thread);
            }
        }
        this.tlnLock.unlockWrite(stamp);
    }

    //==================================================================================================================

    public void start() {
        this.thread.start();
    }

    public void stop() {
        if (!this.running) {
            throw new IllegalStateException();
        }
        this.running = false;
        LockSupport.unpark(this.thread);
        try {
            while (this.thread.isAlive()) {
                LockSupport.unpark(this.thread);
                this.thread.join(1000);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        //TODO CLEAN
        while (true) {
            var buffer = this.requestBatchQueue.poll();
            if (buffer == null) break;
            buffer.free();
        }

        while (true) {
            var buffer = this.requestBatchQueue.poll();
            if (buffer == null) break;
            buffer.free();
        }

        while (true) {
            var buffer = this.geometryUpdateQueue.poll();
            if (buffer == null) break;
            buffer.free();
        }

        if (RESULT_HANDLE.get(this) != null) {
            var result = (SyncResults)RESULT_HANDLE.getAndSet(this, null);
            result.geometryUpload.free();
            result.scatterWriteBuffer.free();
        }

        if (RESULT_CACHE_1_HANDLE.get(this) != null) {//Clear cache 1
            var result = (SyncResults)RESULT_CACHE_1_HANDLE.getAndSet(this, null);
            result.geometryUpload.free();
            result.scatterWriteBuffer.free();
        }

        if (RESULT_CACHE_2_HANDLE.get(this) != null) {//Clear cache 2
            var result = (SyncResults)RESULT_CACHE_2_HANDLE.getAndSet(this, null);
            result.geometryUpload.free();
            result.scatterWriteBuffer.free();
        }

        this.scatterWrite.free();
        this.multiMemcpy.free();
    }

    public void addDebug(List<String> debug) {
        debug.add("UC/GC: " + (this.getUsedGeometryCapacity()/(1<<20))+"/"+(this.getGeometryCapacity()/(1<<20)));
    }

    //Results object, which is to be synced between the render thread and worker thread
    private static final class SyncResults {
        //Contains
        // geometry uploads and id invalidations and the data
        // node ids to invalidate/update and its data
        // top level node ids to add/remove
        // cleaner move and set operations

        //Node id updates + size
        private int currentMaxNodeId;// the id of the ending of the node ids

        //TLN add/rem
        private final IntOpenHashSet tlnDelta = new IntOpenHashSet();

        //Deltas for geometry store
        private int geometrySectionCount;
        private long usedGeometry;
        private final ComputeMemoryCopy geometryUpload = new ComputeMemoryCopy();


        //Scatter writes for both geometry and node metadata
        private MemoryBuffer scatterWriteBuffer = new MemoryBuffer(8192*2);
        private final Int2IntOpenHashMap scatterWriteLocationMap = new Int2IntOpenHashMap(1024);
        {this.scatterWriteLocationMap.defaultReturnValue(-1);}

        //Cleaner operations
        private final IntOpenHashSet cleanerOperations = new IntOpenHashSet();

        public void reset() {
            this.cleanerOperations.clear();
            this.scatterWriteLocationMap.clear();
            this.currentMaxNodeId = 0;
            this.tlnDelta.clear();
            this.geometrySectionCount = 0;
            this.usedGeometry = 0;
            this.geometryUpload.reset();
        }

        //Get or create a scatter write address for the given location
        public long getScatterWritePtr(int location) {
            return this.getScatterWritePtr(location, 0);
        }

        //ensureExtra is used to ensure that allocations are "effectivly" in the same memory block (kinda?)
        public long getScatterWritePtr(int location, int ensureExtra) {
            int loc = this.scatterWriteLocationMap.get(location);
            if (loc == -1) {//Location doesnt exist, create it
                this.ensureScatterBufferCapacity(1+ensureExtra);//Ensure can contain capacity for this + extra
                int baseId = this.scatterWriteLocationMap.size();
                int chunkBase = (baseId/4)*5;//Base uvec4 index
                int innerId   = baseId&3;
                MemoryUtil.memPutInt(this.scatterWriteBuffer.address + (chunkBase*16L) + (innerId*4L), location);//Set the write location
                int writeLocation = (chunkBase+1+innerId);//Write location in uvec4
                this.scatterWriteLocationMap.put(location, writeLocation);
                return this.scatterWriteBuffer.address + (writeLocation*16L);
            } else {
                return this.scatterWriteBuffer.address + (16L*loc);
            }
        }

        private void ensureScatterBufferCapacity(int extra) {
            int requiredChunks = ((this.scatterWriteLocationMap.size()+extra)+3)/4;//4 entries in a chunk
            long requiredSize = requiredChunks*5L*16L;//5 uvec4 per chunk, 16 bytes per uvec4
            if (this.scatterWriteBuffer.size <= requiredSize) {//Needs resize
                long newSize = (long) ((this.scatterWriteBuffer.size*1.5) + extra*80L);
                newSize = ((newSize+79)/80)*80;//Ceil to chunk size

                Logger.info("Expanding scatter update buffer to " + newSize);

                var newBuffer = new MemoryBuffer(newSize);
                this.scatterWriteBuffer.cpyTo(newBuffer.address);
                this.scatterWriteBuffer.free();
                this.scatterWriteBuffer = newBuffer;
            }
        }
    }

    private static class ComputeMemoryCopy {
        public int currentElemCopyAmount;
        private MemoryBuffer scratchHeaderBuffer = new MemoryBuffer(1<<16);
        private MemoryBuffer scratchDataBuffer = new MemoryBuffer(1<<20);

        private final AllocationArena arena = new AllocationArena();
        private final Int2IntOpenHashMap dataUploadPoints = new Int2IntOpenHashMap();//Points to the header index
        {this.dataUploadPoints.defaultReturnValue(-1);}


        public void remove(int point) {
            int header = this.dataUploadPoints.remove(point);
            if (header == -1) {//No upload for point
                return;
            }
            int size = MemoryUtil.memGetInt(this.scratchHeaderBuffer.address + header*16L + 8L);
            this.currentElemCopyAmount -= size;
            //Free the old memory addr from arena
            if (this.arena.free(MemoryUtil.memGetInt(this.scratchHeaderBuffer.address + header*16L)) != size) {
                throw new IllegalStateException("Freed memory not same size as expected");
            }
            if (MemoryUtil.memGetInt(this.scratchHeaderBuffer.address + header*16L + 4L) != point) {
                throw new IllegalStateException("Destination not the same as point");
            }

            //If we were the end upload header, return as we dont need to shuffle
            if (header == this.dataUploadPoints.size()) {
                long A = this.scratchHeaderBuffer.address + header*16L;
                //Zero the memory, for consistancy
                MemoryUtil.memPutLong(A, 0);
                MemoryUtil.memPutLong(A+8, 0);
                return;
            }

            //Else: we need to move the ending upload header from the end to where the freed point was
            int endingPoint = MemoryUtil.memGetInt(this.scratchHeaderBuffer.address + this.dataUploadPoints.size()*16L + 4);
            if (this.dataUploadPoints.get(endingPoint) != this.dataUploadPoints.size()) {
                throw new IllegalStateException("ending header not pointing at end point");
            }

            //Move the end header to the old header location
            long A = this.scratchHeaderBuffer.address + this.dataUploadPoints.size()*16L;
            long B = this.scratchHeaderBuffer.address + header*16L;
            MemoryUtil.memPutLong(B, MemoryUtil.memGetLong(A)); MemoryUtil.memPutLong(A, 0);
            MemoryUtil.memPutLong(B+8, MemoryUtil.memGetLong(A+8)); MemoryUtil.memPutLong(A+8, 0);

            //Update the map
            this.dataUploadPoints.put(endingPoint, header);
        }

        public void upload(int point, MemoryBuffer data) {
            if ((data.size%8)!=0) throw new IllegalStateException("Data must be of size multiple 8");
            int elemSize = (int) (data.size / 8);
            int header = this.dataUploadPoints.get(point);
            if (header != -1) {
                //If we already have a header location, we just need to reallocate the data
                long headerPtr = this.scratchHeaderBuffer.address + header*16L;
                if (MemoryUtil.memGetInt(headerPtr+4L) != point) {
                    throw new IllegalStateException("Existing destination not the point");
                }
                int pSize = MemoryUtil.memGetInt(headerPtr+8L);//Previous size
                if (pSize == elemSize) {
                    //The data we are replacing is the same size, so just overwrite it, this is the easiest
                    data.cpyTo(this.scratchDataBuffer.address+MemoryUtil.memGetInt(headerPtr)*8L);
                } else {
                    //Dealloc
                    if (this.arena.free(MemoryUtil.memGetInt(headerPtr)) != pSize) {
                        throw new IllegalStateException("Freed allocation not size as expected");
                    }

                    this.currentElemCopyAmount -= pSize;
                    this.currentElemCopyAmount += elemSize;

                    int alloc = this.allocScratchDataPos(elemSize);//New allocation position
                    //Copy data into position
                    data.cpyTo(this.scratchDataBuffer.address+alloc*8L);

                    //Update the header
                    MemoryUtil.memPutInt(headerPtr, alloc);
                    MemoryUtil.memPutInt(headerPtr+8, elemSize);
                }
            } else {
                //We need to create and allocate a new header for the upload
                header = this.dataUploadPoints.size();
                this.dataUploadPoints.put(point, header);

                if (this.scratchHeaderBuffer.size<=header*16L) {
                    //We must resize the header buffer
                    long newSize = Math.max(this.scratchHeaderBuffer.size*2, header*16L);
                    Logger.info("Resizing scratch header buffer to: " + newSize);
                    var newScratch = new MemoryBuffer(newSize);
                    this.scratchHeaderBuffer.cpyTo(newScratch.address);
                    this.scratchHeaderBuffer.free();
                    this.scratchHeaderBuffer = newScratch;
                }

                long headerPtr = this.scratchHeaderBuffer.address + header*16L;//Header resize has happened so this is a stable address

                this.currentElemCopyAmount += elemSize;

                int alloc = this.allocScratchDataPos(elemSize);//New allocation position
                //Copy data into position
                data.cpyTo(this.scratchDataBuffer.address+alloc*8L);

                //Set header data
                MemoryUtil.memPutInt(headerPtr, alloc);
                MemoryUtil.memPutInt(headerPtr+4, point);
                MemoryUtil.memPutInt(headerPtr+8, elemSize);
            }
        }

        //This is done here as it enables easily doing scratch data resizing
        private int allocScratchDataPos(int size) {
            int pos = (int) this.arena.alloc(size);
            if (this.scratchDataBuffer.size <= (pos+size)*8L) {
                //We must resize :cri:
                long newSize = Math.max(this.scratchDataBuffer.size*2, (pos+size)*8L);
                Logger.info("Resizing scratch data buffer to: " + newSize);
                var newScratch = new MemoryBuffer(newSize);
                this.scratchDataBuffer.cpyTo(newScratch.address);
                this.scratchDataBuffer.free();
                this.scratchDataBuffer = newScratch;
            }
            return pos;
        }

        public void reset() {
            this.currentElemCopyAmount = 0;
            this.dataUploadPoints.clear();
            this.arena.reset();
        }

        public void free() {
            this.scratchHeaderBuffer.free(); this.scratchHeaderBuffer = null;
            this.scratchDataBuffer.free(); this.scratchDataBuffer = null;
        }
    }
}
