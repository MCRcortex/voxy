package me.cortex.voxy.client.core.rendering.hierachical2;


import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.building.SectionPositionUpdateFilterer;
import me.cortex.voxy.client.core.rendering.section.AbstractSectionGeometryManager;
import me.cortex.voxy.common.util.HierarchicalBitSet;
import me.cortex.voxy.common.world.WorldSection;
import me.jellysquid.mods.sodium.client.util.MathUtil;
import org.lwjgl.system.MemoryUtil;

//Contains no logic to interface with the gpu, nor does it contain any gpu buffers
public class HierarchicalNodeManager {
    public static final int NODE_MSK = ((1<<24)-1);
    public final int maxNodeCount;
    private final long[] localNodeData;
    private final AbstractSectionGeometryManager geometryManager;
    private final HierarchicalBitSet allocationSet;
    private final Long2IntOpenHashMap activeSectionMap = new Long2IntOpenHashMap();
    private final SectionPositionUpdateFilterer updateFilterer;
    public HierarchicalNodeManager(int maxNodeCount, AbstractSectionGeometryManager geometryManager, SectionPositionUpdateFilterer updateFilterer) {
        if (!MathUtil.isPowerOfTwo(maxNodeCount)) {
            throw new IllegalArgumentException("Max node count must be a power of 2");
        }
        if (maxNodeCount>(1<<24)) {
            throw new IllegalArgumentException("Max node count cannot exceed 2^24");
        }
        this.updateFilterer = updateFilterer;
        this.allocationSet = new HierarchicalBitSet(maxNodeCount);
        this.maxNodeCount = maxNodeCount;
        this.localNodeData = new long[maxNodeCount*4];
        this.geometryManager = geometryManager;



        for(int x = -1; x<=1;x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = -3; y <= 3; y++) {
                    updateFilterer.watch(0,x,y,z);
                }
            }
        }
    }

    public void processRequestQueue(int count, long ptr) {
        for (int i = 0; i < count; i++) {
            int op = MemoryUtil.memGetInt(ptr+(i*4L));
            int node = op&NODE_MSK;

        }
    }

    public void processBuildResult(BuiltSection section) {
        if (!section.isEmpty()) {
            this.geometryManager.uploadSection(section);
        } else {
            section.free();
        }
    }
}
