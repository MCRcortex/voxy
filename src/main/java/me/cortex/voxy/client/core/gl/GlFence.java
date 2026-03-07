package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.common.util.TrackedObject;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL32.*;

public class GlFence extends TrackedObject implements me.cortex.voxy.client.core.gpu.IGpuFence {
    private final long fence;
    private boolean signaled;

    public GlFence() {
        this.fence = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
    }

    private static final long SCRATCH = MemoryUtil.nmemCalloc(1,4);

    public boolean signaled() {
        if (!this.signaled) {
            /*
            int ret = glClientWaitSync(this.fence, 0, 0);
            if (ret == GL_ALREADY_SIGNALED || ret == GL_CONDITION_SATISFIED) {
                this.signaled = true;
            } else if (ret != GL_TIMEOUT_EXPIRED) {
                throw new IllegalStateException("Poll for fence failed, glError: " + glGetError());
            }*/
            MemoryUtil.memPutInt(SCRATCH, -1);
            nglGetSynciv(this.fence, GL_SYNC_STATUS, 1, 0, SCRATCH);
            int val = MemoryUtil.memGetInt(SCRATCH);
            if (val == GL_SIGNALED) {
                this.signaled = true;
            } else if (val != GL_UNSIGNALED) {
                throw new IllegalStateException("Unknown data from glGetSync: "+val);
            }
        }
        return this.signaled;
    }

    @Override
    public void free() {
        super.free0();
        glDeleteSync(this.fence);
    }
}
