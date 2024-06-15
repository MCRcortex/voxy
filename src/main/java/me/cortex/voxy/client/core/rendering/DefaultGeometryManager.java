package me.cortex.voxy.client.core.rendering;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.util.BufferArena;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.lwjgl.system.MemoryUtil;

import java.util.concurrent.ConcurrentLinkedDeque;

public class DefaultGeometryManager extends AbstractGeometryManager {
    private static final int SECTION_METADATA_SIZE = 32;
    private final Long2IntOpenHashMap pos2id = new Long2IntOpenHashMap();
    private final LongArrayList id2pos = new LongArrayList();
    private final ObjectArrayList<SectionMeta> sectionMetadata = new ObjectArrayList<>();
    private final IntArrayList markSectionIds = new IntArrayList();//Section ids to mark as visible (either due to being new, or swapping)

    private final GlBuffer sectionMetaBuffer;
    private final BufferArena geometryBuffer;

    private final int geometryElementSize;

    public DefaultGeometryManager(long geometryBufferSize, int maxSections) {
        this(geometryBufferSize, maxSections, 8);//8 is default quad size
    }

    public DefaultGeometryManager(long geometryBufferSize, int maxSections, int elementSize) {
        super(maxSections);
        this.sectionMetaBuffer = new GlBuffer(((long) maxSections) * SECTION_METADATA_SIZE);
        this.geometryBuffer = new BufferArena(geometryBufferSize, elementSize);
        this.pos2id.defaultReturnValue(-1);
        this.geometryElementSize = elementSize;
    }

    IntArrayList uploadResults() {
        this.markSectionIds.clear();
        while (!this.buildResults.isEmpty()) {
            var result = this.buildResults.pop();
            boolean isDelete = result.isEmpty();
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
                        this.markSectionIds.add(id);
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
                    if (meta == null) {
                        continue;
                    }
                    this.sectionMetadata.set(id, meta);
                    long ptr = UploadStream.INSTANCE.upload(this.sectionMetaBuffer, (long)SECTION_METADATA_SIZE * id, SECTION_METADATA_SIZE);
                    meta.writeMetadata(ptr);
                } else {
                    //Create the new meta
                    var meta = this.createMeta(result);
                    if (meta == null) {
                        continue;
                    }

                    //Add to the end of the array
                    id = this.sectionCount++;
                    this.pos2id.put(result.position, id);
                    this.id2pos.add(result.position);

                    this.sectionMetadata.add(meta);
                    long ptr = UploadStream.INSTANCE.upload(this.sectionMetaBuffer, (long)SECTION_METADATA_SIZE * id, SECTION_METADATA_SIZE);
                    meta.writeMetadata(ptr);
                    this.markSectionIds.add(id);
                }
            }

            //Assert some invarients
            if (this.id2pos.size() != this.sectionCount || this.sectionCount != this.pos2id.size()) {
                throw new IllegalStateException("Invariants broken");
            }

            result.free();
        }
        return this.markSectionIds;
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


    //=========================================================================================================================================================================================
    //=========================================================================================================================================================================================
    //=========================================================================================================================================================================================


    //TODO: pack the offsets of each axis so that implicit face culling can work
    //Note! the opaquePreDataCount and translucentPreDataCount are never writen to the meta buffer, as they are indexed in reverse relative to the base opaque and translucent geometry
    protected record SectionMeta(long position, int aabb, int geometryPtr, int size, int[] offsets) {
        public void writeMetadata(long ptr) {
            //THIS IS DUE TO ENDIANNESS and that we are splitting a long into 2 ints
            MemoryUtil.memPutInt(ptr, (int) (this.position>>32)); ptr += 4;
            MemoryUtil.memPutInt(ptr, (int) this.position); ptr += 4;
            MemoryUtil.memPutInt(ptr, (int) this.aabb); ptr += 4;
            MemoryUtil.memPutInt(ptr, this.geometryPtr + this.offsets[0]); ptr += 4;

            MemoryUtil.memPutInt(ptr, (this.offsets[1]-this.offsets[0])|((this.offsets[2]-this.offsets[1])<<16)); ptr += 4;
            MemoryUtil.memPutInt(ptr, (this.offsets[3]-this.offsets[2])|((this.offsets[4]-this.offsets[3])<<16)); ptr += 4;
            MemoryUtil.memPutInt(ptr, (this.offsets[5]-this.offsets[4])|((this.offsets[6]-this.offsets[5])<<16)); ptr += 4;
            MemoryUtil.memPutInt(ptr, (this.offsets[7]-this.offsets[6])|((this.size      -this.offsets[7])<<16)); ptr += 4;
        }
    }

    protected SectionMeta createMeta(BuiltSection geometry) {
        int geometryPtr = (int) this.geometryBuffer.upload(geometry.geometryBuffer);
        if (geometryPtr == -1) {
            String msg = "Buffer arena out of memory, please increase it in settings or decrease LoD quality";
            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.literal(msg));
            System.err.println(msg);
            return null;
        }
        return new SectionMeta(geometry.position, geometry.aabb, geometryPtr, (int) (geometry.geometryBuffer.size/this.geometryElementSize), geometry.offsets);
    }

    protected void freeMeta(SectionMeta meta) {
        if (meta.geometryPtr != -1) {
            this.geometryBuffer.free(meta.geometryPtr);
        }
    }
}
