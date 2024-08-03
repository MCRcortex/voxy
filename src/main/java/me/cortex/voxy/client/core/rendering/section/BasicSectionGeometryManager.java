package me.cortex.voxy.client.core.rendering.section;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.geometry.OLD.DefaultGeometryManager;
import me.cortex.voxy.client.core.rendering.util.BufferArena;
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
    public void free() {
        super.free();
        this.sectionMetadataBuffer.free();
        this.geometry.free();
    }
}
