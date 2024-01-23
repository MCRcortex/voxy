package me.cortex.zenith.client.core.rendering;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.cortex.zenith.client.core.gl.GlBuffer;
import me.cortex.zenith.client.core.rendering.building.BuiltSectionGeometry;
import me.cortex.zenith.client.core.rendering.util.BufferArena;
import me.cortex.zenith.client.core.rendering.util.UploadStream;
import org.lwjgl.system.MemoryUtil;

import java.util.concurrent.ConcurrentLinkedDeque;

public class GeometryManager {
    private static final int SECTION_METADATA_SIZE = 32;


    //Note! the opaquePreDataCount and translucentPreDataCount are never writen to the meta buffer, as they are indexed in reverse relative to the base opaque and translucent geometry
    private record SectionMeta(long position, long opaqueGeometryPtr, int opaqueQuadCount, int opaquePreDataCount, long translucentGeometryPtr, int translucentQuadCount, int translucentPreDataCount) {
        public void writeMetadata(long ptr) {
            //THIS IS DUE TO ENDIANNESS and that we are splitting a long into 2 ints
            MemoryUtil.memPutInt(ptr, (int) (this.position>>32)); ptr += 4;
            MemoryUtil.memPutInt(ptr, (int) this.position); ptr += 4;
            ptr += 8;

            MemoryUtil.memPutInt(ptr, (int) this.opaqueGeometryPtr + this.opaquePreDataCount); ptr += 4;
            MemoryUtil.memPutInt(ptr, this.opaqueQuadCount); ptr += 4;

            MemoryUtil.memPutInt(ptr, (int) this.translucentGeometryPtr + this.translucentPreDataCount); ptr += 4;
            MemoryUtil.memPutInt(ptr, this.translucentQuadCount); ptr += 4;
        }
    }

    private final ConcurrentLinkedDeque<BuiltSectionGeometry> buildResults = new ConcurrentLinkedDeque<>();

    private int sectionCount = 0;
    private final Long2IntOpenHashMap pos2id = new Long2IntOpenHashMap();
    private final LongArrayList id2pos = new LongArrayList();
    private final ObjectArrayList<SectionMeta> sectionMetadata = new ObjectArrayList<>();

    private final GlBuffer sectionMetaBuffer;
    private final BufferArena geometryBuffer;


    public GeometryManager(long geometryBufferSize, int maxSections) {
        this.sectionMetaBuffer = new GlBuffer(((long) maxSections) * SECTION_METADATA_SIZE, 0);
        this.geometryBuffer = new BufferArena(geometryBufferSize, 8);
        this.pos2id.defaultReturnValue(-1);
    }

    public void enqueueResult(BuiltSectionGeometry sectionGeometry) {
        this.buildResults.add(sectionGeometry);
    }

    private SectionMeta createMeta(BuiltSectionGeometry geometry) {
        long geometryPtr = this.geometryBuffer.upload(geometry.geometryBuffer);

        //TODO: support translucent geometry
        return new SectionMeta(geometry.position, geometryPtr, (int) (geometry.geometryBuffer.size/8), 0, -1,0, 0);
    }

    private void freeMeta(SectionMeta meta) {
        if (meta.opaqueGeometryPtr != -1) {
            this.geometryBuffer.free(meta.opaqueGeometryPtr);
        }
        if (meta.translucentGeometryPtr != -1) {
            this.geometryBuffer.free(meta.translucentGeometryPtr);
        }
    }

    void uploadResults() {
        while (!this.buildResults.isEmpty()) {
            var result = this.buildResults.pop();
            boolean isDelete = result.geometryBuffer == null && result.translucentGeometryBuffer == null;
            if (isDelete) {
                int id = -1;
                if ((id = this.pos2id.remove(result.position)) != -1) {
                    if (this.id2pos.getLong(id) != result.position) {
                        throw new IllegalStateException("Removed position id not the same requested");
                    }

                    var meta = this.sectionMetadata.get(id);
                    this.freeMeta(meta);


                    this.sectionCount--;
                    if (id == this.sectionCount) {
                        //if we are at the end of the array dont have to do anything (maybe just upload a blank data, just to be sure)

                        //Remove the last element
                        this.sectionMetadata.remove(id);
                        this.id2pos.removeLong(id);
                    } else {
                        long swapLodPos = this.id2pos.getLong(this.sectionCount);
                        this.pos2id.put(swapLodPos, id);
                        this.id2pos.set(id, swapLodPos);
                        //Remove from the lists
                        this.id2pos.removeLong(this.sectionCount);
                        var swapMeta = this.sectionMetadata.remove(this.sectionCount);
                        this.sectionMetadata.set(id, swapMeta);
                        if (swapMeta.position != swapLodPos) {
                            throw new IllegalStateException();
                        }
                        long ptr = UploadStream.INSTANCE.upload(this.sectionMetaBuffer, (long) SECTION_METADATA_SIZE * id, SECTION_METADATA_SIZE);
                        swapMeta.writeMetadata(ptr);
                    }
                }
            } else {
                int id = -1;
                if ((id = this.pos2id.get(result.position)) != -1) {
                    //Update the existing data
                    var meta = this.sectionMetadata.get(id);
                    if (meta.position != result.position) {
                        throw new IllegalStateException("Meta position != result position");
                    }
                    //Delete the old data
                    this.freeMeta(meta);

                    //Create the new meta
                    meta = this.createMeta(result);
                    this.sectionMetadata.set(id, meta);
                    long ptr = UploadStream.INSTANCE.upload(this.sectionMetaBuffer, (long)SECTION_METADATA_SIZE * id, SECTION_METADATA_SIZE);
                    meta.writeMetadata(ptr);
                } else {
                    //Add to the end of the array
                    id = this.sectionCount++;
                    this.pos2id.put(result.position, id);
                    this.id2pos.add(result.position);

                    //Create the new meta
                    var meta = this.createMeta(result);
                    this.sectionMetadata.add(meta);
                    long ptr = UploadStream.INSTANCE.upload(this.sectionMetaBuffer, (long)SECTION_METADATA_SIZE * id, SECTION_METADATA_SIZE);
                    meta.writeMetadata(ptr);
                }
            }

            //Assert some invarients
            if (this.id2pos.size() != this.sectionCount || this.sectionCount != this.pos2id.size()) {
                throw new IllegalStateException("Invariants broken");
            }

            result.free();
        }
    }

    public int getSectionCount() {
        return this.sectionCount;
    }

    public void free() {
        while (!this.buildResults.isEmpty()) {
            this.buildResults.pop().free();
        }
        this.sectionMetaBuffer.free();
        this.geometryBuffer.free();
    }

    public int geometryId() {
        return this.geometryBuffer.id();
    }

    public int metaId() {
        return this.sectionMetaBuffer.id;
    }


    public float getGeometryBufferUsage() {
        return this.geometryBuffer.usage();
    }

}
