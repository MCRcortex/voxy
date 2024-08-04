package me.cortex.voxy.client.core.rendering.section;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.util.BufferArena;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.common.util.HierarchicalBitSet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.lwjgl.system.MemoryUtil;

public class BasicSectionGeometryManager extends AbstractSectionGeometryManager {
    private static final int SECTION_METADATA_SIZE = 32;
    private final GlBuffer sectionMetadataBuffer;
    private final BufferArena geometry;
    private final HierarchicalBitSet allocationSet;
    private final ObjectArrayList<SectionMeta> sectionMetadata = new ObjectArrayList<>(1<<15);

    //These are section ids that need to be written to the gpu buffer
    private final IntOpenHashSet invalidatedSectionIds = new IntOpenHashSet();

    public BasicSectionGeometryManager(int maxSectionCount, long geometryCapacity) {
        super(maxSectionCount, geometryCapacity);
        this.allocationSet = new HierarchicalBitSet(maxSectionCount);
        this.sectionMetadataBuffer = new GlBuffer((long) maxSectionCount * SECTION_METADATA_SIZE);
        this.geometry = new BufferArena(geometryCapacity, 8);//8 Cause a quad is 8 bytes
    }

    @Override
    public int uploadReplaceSection(int oldId, BuiltSection sectionData) {
        if (sectionData.isEmpty()) {
            throw new IllegalArgumentException("sectionData is empty, cannot upload nothing");
        }

        //Free the old id and replace it with a new one
        // if oldId is -1, then treat it as not previously existing

        //Free the old data if oldId is supplied
        if (oldId != -1) {
            //Its here just for future optimization potential
            this.removeSection(oldId);
        }

        int newId =  this.allocationSet.allocateNext();
        if (newId == HierarchicalBitSet.SET_FULL) {
            throw new IllegalStateException("Tried adding section when section count is already at capacity");
        }
        if (newId > this.sectionMetadata.size()) {
            throw new IllegalStateException();
        }

        var newMeta = createMeta(sectionData);
        //Release the section data as its not needed anymore
        sectionData.free();

        if (newId == this.sectionMetadata.size()) {
            this.sectionMetadata.add(newMeta);
        } else {
            this.sectionMetadata.set(newId, newMeta);
        }

        //Invalidate the section id
        this.invalidatedSectionIds.add(newId);
        return newId;
    }

    @Override
    public void removeSection(int id) {
        if (!this.allocationSet.free(id)) {
            throw new IllegalStateException("Id was not already allocated");
        }
        var oldMetadata = this.sectionMetadata.set(id, null);
        this.geometry.free(oldMetadata.geometryPtr);
        this.invalidatedSectionIds.add(id);
    }

    private SectionMeta createMeta(BuiltSection geometry) {
        int geometryPtr = (int) this.geometry.upload(geometry.geometryBuffer);
        if (geometryPtr == -1) {
            throw new IllegalStateException("Unable to upload section geometry as geometry buffer is full");
        }
        //8 bytes per quad
        return new SectionMeta(geometry.position, geometry.aabb, geometryPtr, (int) (geometry.geometryBuffer.size/8), geometry.offsets);
    }

    private record SectionMeta(long position, int aabb, int geometryPtr, int itemCount, int[] offsets) {
        public void writeMetadata(long ptr) {
            //Split the long into 2 ints to solve endian issues
            MemoryUtil.memPutInt(ptr, (int) (this.position>>32)); ptr += 4;
            MemoryUtil.memPutInt(ptr, (int) this.position); ptr += 4;
            MemoryUtil.memPutInt(ptr, (int) this.aabb); ptr += 4;
            MemoryUtil.memPutInt(ptr, this.geometryPtr + this.offsets[0]); ptr += 4;

            MemoryUtil.memPutInt(ptr, (this.offsets[1]-this.offsets[0])|((this.offsets[2]-this.offsets[1])<<16)); ptr += 4;
            MemoryUtil.memPutInt(ptr, (this.offsets[3]-this.offsets[2])|((this.offsets[4]-this.offsets[3])<<16)); ptr += 4;
            MemoryUtil.memPutInt(ptr, (this.offsets[5]-this.offsets[4])|((this.offsets[6]-this.offsets[5])<<16)); ptr += 4;
            MemoryUtil.memPutInt(ptr, (this.offsets[7]-this.offsets[6])|((this.itemCount -this.offsets[7])<<16)); ptr += 4;
        }
    }

    @Override
    void tick() {
        //Upload all invalidated bits
        if (!this.invalidatedSectionIds.isEmpty()) {
            for (int id : this.invalidatedSectionIds) {
                var meta = this.sectionMetadata.get(id);
                long ptr = UploadStream.INSTANCE.upload(this.sectionMetadataBuffer, (long) id *SECTION_METADATA_SIZE, SECTION_METADATA_SIZE);
                if (meta == null) {//We need to clear the gpu side buffer
                    MemoryUtil.memSet(ptr, 0, SECTION_METADATA_SIZE);
                } else {
                    meta.writeMetadata(ptr);
                }
            }
            this.invalidatedSectionIds.clear();
            UploadStream.INSTANCE.commit();
        }
    }

    @Override
    public void free() {
        super.free();
        this.sectionMetadataBuffer.free();
        this.geometry.free();
    }

    int getSectionCount() {
        return this.allocationSet.getCount();
    }

    long getGeometryUsed() {
        return this.geometry.getUsedBytes();
    }

    int getGeometryBufferId() {
        return this.geometry.id();
    }

    int getMetadataBufferId() {
        return this.sectionMetadataBuffer.id;
    }
}
