package me.cortex.voxy.client.core.rendering.section.geometry;

import me.cortex.voxy.client.core.gl.Capabilities;
import me.cortex.voxy.client.core.gpu.IGpuBuffer;
import me.cortex.voxy.client.core.gpu.RenderBackendFactory;
import me.cortex.voxy.common.Logger;

import static org.lwjgl.opengl.ARBSparseBuffer.*;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.glBindBuffer;

public class BasicSectionGeometryData implements IGeometryData {
    public static final int SECTION_METADATA_SIZE = 32;
    private final IGpuBuffer sectionMetadataBuffer;
    private final IGpuBuffer geometryBuffer;

    private final int maxSectionCount;
    private int currentSectionCount;

    public BasicSectionGeometryData(int maxSectionCount, long geometryCapacity) {
        this.maxSectionCount = maxSectionCount;
        this.sectionMetadataBuffer = RenderBackendFactory.get().createBuffer((long) maxSectionCount * SECTION_METADATA_SIZE);
        //8 Cause a quad is 8 bytes
        if ((geometryCapacity%8)!=0) {
            throw new IllegalStateException();
        }
        long start = System.currentTimeMillis();
        String msg = "Creating and zeroing " + (geometryCapacity/(1024*1024)) + "MB geometry buffer";
        if (Capabilities.INSTANCE.canQueryGpuMemory) {
            msg += " driver states " + (Capabilities.INSTANCE.getFreeDedicatedGpuMemory()/(1024*1024)) + "MB of free memory";
        }
        Logger.info(msg);
        Logger.info("if your game crashes/exits here without any other log message, try manually decreasing the geometry capacity");
        glGetError();//Clear any errors
        IGpuBuffer buffer = null;
        if (!(Capabilities.INSTANCE.isNvidia)) {// && ThreadUtils.isWindows
            buffer = RenderBackendFactory.get().createBuffer(geometryCapacity, 0, false);//Only do this if we are not on nvidia
            //TODO: FIXME: TEST, see if the issue is that we are trying to zero the entire buffer, try only zeroing increments
            // or dont zero it at all
        } else {
            Logger.info("Running on nvidia, using workaround sparse buffer allocation");
        }
        int error = glGetError();
        if (error != GL_NO_ERROR || buffer == null) {
            if ((buffer == null || error == GL_OUT_OF_MEMORY) && RenderBackendFactory.get().hasSparseBuffer()) {
                if (buffer != null) {
                    Logger.error("Failed to allocate geometry buffer, attempting workaround with sparse buffers");
                    buffer.free();
                }
                buffer = RenderBackendFactory.get().createBuffer(geometryCapacity, GL_SPARSE_STORAGE_BIT_ARB);
                //buffer.zero();
                error = glGetError();
                if (error != GL_NO_ERROR) {
                    buffer.free();
                    throw new IllegalStateException("Unable to allocate geometry buffer using workaround, got gl error " + error);
                }
            } else {
                throw new IllegalStateException("Unable to allocate geometry buffer, got gl error " + error);
            }
        }
        this.geometryBuffer = buffer;
        long delta = System.currentTimeMillis() - start;
        Logger.info("Successfully allocated the geometry buffer in " + delta + "ms");
    }

    private long sparseCommitment = 0;//Tracks the current range of the allocated sparse buffer
    public void ensureAccessable(int maxElementAccess) {
        long size = (Integer.toUnsignedLong(maxElementAccess)*8L+65535L)&~65535L;
        //If we are a sparse buffer, ensure the memory upto the requested size is allocated
        if (this.geometryBuffer.isSparse()) {
            if (this.sparseCommitment < size) {//if we try to access memory outside the allocation range, allocate it
                glBindBuffer(GL_ARRAY_BUFFER, this.geometryBuffer.id());
                size += 65536L*1024;//increase size by 64mb to prevent driver allocation thrashing
                glBufferPageCommitmentARB(GL_ARRAY_BUFFER, this.sparseCommitment, size-this.sparseCommitment, true);
                glBindBuffer(GL_ARRAY_BUFFER, 0);
                this.sparseCommitment = size;
            }
        }
    }

    public IGpuBuffer getGeometryBuffer() {
        return this.geometryBuffer;
    }

    public IGpuBuffer getMetadataBuffer() {
        return this.sectionMetadataBuffer;
    }

    public int getSectionCount() {
        return this.currentSectionCount;
    }

    public void setSectionCount(int count) {
        this.currentSectionCount = count;
    }

    public int getMaxSectionCount() {
        return this.maxSectionCount;
    }

    public long getGeometryCapacityBytes() {//In bytes
        return this.geometryBuffer.size();
    }

    @Override
    public void free() {
        this.sectionMetadataBuffer.free();

        long gpuMemory = 0;
        if (Capabilities.INSTANCE.canQueryGpuMemory) {
            glFinish();
            gpuMemory = Capabilities.INSTANCE.getFreeDedicatedGpuMemory();
        }
        if (this.geometryBuffer.isSparse()) {
            glBindBuffer(GL_ARRAY_BUFFER, this.geometryBuffer.id());
            glBufferPageCommitmentARB(GL_ARRAY_BUFFER, 0, this.sparseCommitment, false);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
        }

        glFinish();
        this.geometryBuffer.free();
        glFinish();
        if (Capabilities.INSTANCE.canQueryGpuMemory) {
            long releaseSize = (long) (this.geometryBuffer.size()*0.75);//if gpu memory usage drops by 75% of the expected value assume we freed it
            if (this.geometryBuffer.isSparse()) {//If we are using sparse buffers, use the commited size instead
                releaseSize = (long)(this.sparseCommitment*0.75);
            }
            if (Capabilities.INSTANCE.getFreeDedicatedGpuMemory()-gpuMemory<=releaseSize) {
                Logger.info("Attempting to wait for gpu memory to release");
                long start = System.currentTimeMillis();

                long TIMEOUT = 2500;

                while (System.currentTimeMillis() - start > TIMEOUT) {//Wait up to 2.5 seconds for memory to release
                    glFinish();
                    if (Capabilities.INSTANCE.getFreeDedicatedGpuMemory() - gpuMemory > releaseSize) break;
                }
                if (Capabilities.INSTANCE.getFreeDedicatedGpuMemory() - gpuMemory <= releaseSize) {
                    Logger.warn("Failed to wait for gpu memory to be freed, this could indicate an issue with the driver");
                }
            }
        }
    }
}
