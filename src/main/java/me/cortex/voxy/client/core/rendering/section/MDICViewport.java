package me.cortex.voxy.client.core.rendering.section;

import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.rendering.Viewport;
import me.cortex.voxy.client.core.rendering.hierachical.HierarchicalOcclusionTraverser;
import me.cortex.voxy.client.core.rendering.util.HiZBuffer;

public class MDICViewport extends Viewport<MDICViewport> {
    public final GlBuffer drawCountCallBuffer = new GlBuffer(1024).zero();
    public final GlBuffer drawCallBuffer = new GlBuffer(5*4*(400_000+100_000+100_000)).zero();//400k draw calls
    public final GlBuffer positionScratchBuffer  = new GlBuffer(8*400000).zero();//400k positions
    public final GlBuffer indirectLookupBuffer = new GlBuffer(HierarchicalOcclusionTraverser.MAX_QUEUE_SIZE *4+4);
    public final GlBuffer visibilityBuffer;

    public MDICViewport(int maxSectionCount) {
        this.visibilityBuffer = new GlBuffer(maxSectionCount*4L);
    }

    @Override
    protected void delete0() {
        super.delete0();
        this.visibilityBuffer.free();
        this.indirectLookupBuffer.free();
        this.drawCountCallBuffer.free();
        this.drawCallBuffer.free();
        this.positionScratchBuffer.free();
    }

    @Override
    public GlBuffer getRenderList() {
        return this.indirectLookupBuffer;
    }
}
