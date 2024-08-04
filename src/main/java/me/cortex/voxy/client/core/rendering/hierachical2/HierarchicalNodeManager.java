package me.cortex.voxy.client.core.rendering.hierachical2;


import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.section.AbstractSectionGeometryManager;
import me.cortex.voxy.common.world.WorldSection;
import me.jellysquid.mods.sodium.client.util.MathUtil;
import org.lwjgl.system.MemoryUtil;

//Contains no logic to interface with the gpu, nor does it contain any gpu buffers
public class HierarchicalNodeManager {
    public static final int NODE_MSK = ((1<<24)-1);
    public final int maxNodeCount;
    private final long[] localNodeData;
    private final AbstractSectionGeometryManager geometryManager;

    public HierarchicalNodeManager(int maxNodeCount, AbstractSectionGeometryManager geometryManager) {
        if (!MathUtil.isPowerOfTwo(maxNodeCount)) {
            throw new IllegalArgumentException("Max node count must be a power of 2");
        }
        if (maxNodeCount>(1<<24)) {
            throw new IllegalArgumentException("Max node count cannot exceed 2^24");
        }
        this.maxNodeCount = maxNodeCount;
        this.localNodeData = new long[maxNodeCount*4];
        this.geometryManager = geometryManager;
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

    //Called when a section is updated in the world engine
    public void sectionUpdate(WorldSection section) {

    }
}
