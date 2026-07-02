package me.cortex.voxy.client.core;

import me.cortex.voxy.client.core.gl.Capabilities;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.GlTexture;
import me.cortex.voxy.client.core.model.ModelFactory;
import me.cortex.voxy.client.core.rendering.section.geometry.BasicSectionGeometryData;
import me.cortex.voxy.client.core.rendering.section.geometry.IGeometryData;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.ThreadUtils;
import me.cortex.voxy.common.util.TrackedObject;
import me.cortex.voxy.commonImpl.VoxyCommon;

import java.util.ArrayList;

import static org.lwjgl.opengl.ARBSparseBuffer.GL_SPARSE_STORAGE_BIT_ARB;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL11C.GL_NO_ERROR;

//System to allow reuse/recycling of render buffer/texture allocations
// specfically the geometry buffer and texture atlas allocation
public class RenderResourceReuse {
    private static final ArrayList<GlTexture> MODEL_TEXTURE_CACHE = new ArrayList<>();
    private static final ArrayList<GlBuffer> GEOMETRY_BUFFER_CACHE = new ArrayList<>();

    //Clears and frees any cached resources (used when the entire instance is shutdown)
    public static void clearResources() {
        MODEL_TEXTURE_CACHE.forEach(TrackedObject::free);
        GEOMETRY_BUFFER_CACHE.forEach(TrackedObject::free);
        MODEL_TEXTURE_CACHE.clear();
        GEOMETRY_BUFFER_CACHE.clear();
    }


    public static GlTexture getOrCreateModelStoreTextureAtlas() {
        GlTexture atlas = null;
        if (!MODEL_TEXTURE_CACHE.isEmpty()) {
            atlas = MODEL_TEXTURE_CACHE.removeFirst().zero();
        } else {
            atlas = new GlTexture().store(GL_RGBA8,
                        Integer.numberOfTrailingZeros(ModelFactory.MODEL_TEXTURE_SIZE),
                        ModelFactory.MODEL_TEXTURE_SIZE*3*256,
                        ModelFactory.MODEL_TEXTURE_SIZE*2*256)
                    .name("ModelTextures");
        }
        return atlas;
    }
    public static void giveBackModelStoreTextureAtlas(GlTexture texture) {
        MODEL_TEXTURE_CACHE.add(texture);
    }

    static GlBuffer getOrCreateGeometryBuffer() {
        GlBuffer buffer = null;
        if (!GEOMETRY_BUFFER_CACHE.isEmpty()) {
            buffer = GEOMETRY_BUFFER_CACHE.removeFirst();
            //Reuse buffer, todo: probably check the geometry size and try upsize if possible
        } else {
            long capacity = getGeometryBufferSize();
            long driverMemory = -1;
            if (Capabilities.INSTANCE.canQueryGpuMemory) {
                driverMemory = Capabilities.INSTANCE.getFreeDedicatedGpuMemory();
            }

            glGetError();//Clear any errors
            if (!(Capabilities.INSTANCE.isNvidia&& ThreadUtils.isWindows&&Capabilities.INSTANCE.sparseBuffer)) {//This hack makes it so it doesnt crash on renderdoc
                buffer = new GlBuffer(capacity, false);//Only do this if we are not on nvidia
                //TODO: FIXME: TEST, see if the issue is that we are trying to zero the entire buffer, try only zeroing increments
                // or dont zero it at all
            } else {
                Logger.info("Running on nvidia, using workaround sparse buffer allocation");
            }
            int error = glGetError();
            if (error != GL_NO_ERROR || buffer == null) {
                if ((buffer == null || error == GL_OUT_OF_MEMORY) && Capabilities.INSTANCE.sparseBuffer) {
                    if (buffer != null) {
                        Logger.error("Failed to allocate geometry buffer, attempting workaround with sparse buffers");
                        buffer.free();
                    }
                    buffer = new GlBuffer(capacity, GL_SPARSE_STORAGE_BIT_ARB);
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
            String extra = "";
            if (driverMemory != -1) {
                extra = ", driver stated " + (driverMemory/(1024*1024)) + "Mb of free memory";
            }
            Logger.info("Allocated new geometry buffer: " + buffer.size() + ", isSparse: " + buffer.isSparse() + extra);
        }
        return buffer;
    }

    public static void giveBackGeometryBuffer(GlBuffer geometryBuffer) {
        GEOMETRY_BUFFER_CACHE.add(geometryBuffer);
    }

    private static long getGeometryBufferSize() {
        long geometryCapacity = Math.min((1L<<(64-Long.numberOfLeadingZeros(Capabilities.INSTANCE.ssboMaxSize-1)))<<1, 1L<<32)-1024/*(1L<<32)-1024*/;
        if (Capabilities.INSTANCE.isIntel) {
            geometryCapacity = Math.max(geometryCapacity, 1L<<30);//intel moment, force min 1gb
        }
        if (Capabilities.INSTANCE.isNvidia && ThreadUtils.isLinux) {
            geometryCapacity = Math.min(geometryCapacity, 2000L*1024L*1024L);//nvidia linux moment, force max 2gb heap
        }

        geometryCapacity = Math.max(512*1024*1024, geometryCapacity);//min of 512 mb

        //Limit to available dedicated memory if possible
        if (Capabilities.INSTANCE.canQueryGpuMemory) {
            //512mb less than avalible,
            long limit = Capabilities.INSTANCE.getFreeDedicatedGpuMemory() - (long)(1.5*1024*1024*1024);//1.5gb vram buffer
            // Give a minimum of 512 mb requirement
            limit = Math.max(512*1024*1024, limit);

            geometryCapacity = Math.min(geometryCapacity, limit);
        }
        //geometryCapacity = 1<<28;
        //geometryCapacity = 1<<30;//1GB test
        var override = System.getProperty("voxy.geometryBufferSizeOverrideMB", "");
        if (!override.isEmpty()) {
            geometryCapacity = Long.parseLong(override)*1024L*1024L;
        }
        return geometryCapacity;
    }
}
