package me.cortex.voxy.client.core.model;

import org.lwjgl.system.MemoryUtil;

public record NewModelBufferDelta(int modelClientId, long modelBufferChangesPtr, int biomeIndex, int[] biomeData, ModelTextureUpload textureUpload) {
    public static NewModelBufferDelta empty(int modelClientId) {
        return new NewModelBufferDelta(modelClientId, 0, -1, null, null);
    }

    public boolean isEmpty() {
        return this.modelBufferChangesPtr == 0;
    }

    public void free() {
        MemoryUtil.nmemFree(this.modelBufferChangesPtr);
    }
}
