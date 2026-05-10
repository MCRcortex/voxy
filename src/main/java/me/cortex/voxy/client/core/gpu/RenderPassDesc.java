package me.cortex.voxy.client.core.gpu;

import java.util.List;

/**
 * Backend-agnostic description of a render pass.
 *
 * Models the union of Metal's MTLRenderPassDescriptor and Vulkan's
 * VkRenderingInfo (dynamic rendering). The OpenGL backend translates this
 * into a framebuffer bind + clear sequence.
 *
 * Created via {@link Builder}; instances are immutable so they can be reused
 * across frames if attachments don't change.
 */
public record RenderPassDesc(List<ColorAttachment> colorAttachments,
                             DepthAttachment depthAttachment,
                             int viewportWidth,
                             int viewportHeight) {

    public enum LoadAction { LOAD, CLEAR, DONT_CARE }
    public enum StoreAction { STORE, DONT_CARE }

    public record ColorAttachment(IGpuTexture texture,
                                  int level,
                                  LoadAction loadAction,
                                  StoreAction storeAction,
                                  float clearR, float clearG, float clearB, float clearA) {}

    public record DepthAttachment(IGpuTexture texture,
                                  int level,
                                  LoadAction loadAction,
                                  StoreAction storeAction,
                                  float clearDepth) {}

    public static Builder builder(int width, int height) {
        return new Builder(width, height);
    }

    public static final class Builder {
        private final int viewportWidth;
        private final int viewportHeight;
        private final java.util.ArrayList<ColorAttachment> colors = new java.util.ArrayList<>(4);
        private DepthAttachment depth;

        private Builder(int width, int height) {
            this.viewportWidth = width;
            this.viewportHeight = height;
        }

        public Builder addColorAttachment(IGpuTexture texture, int level,
                                          LoadAction load, StoreAction store,
                                          float r, float g, float b, float a) {
            this.colors.add(new ColorAttachment(texture, level, load, store, r, g, b, a));
            return this;
        }

        public Builder clearColor(IGpuTexture texture, float r, float g, float b, float a) {
            return this.addColorAttachment(texture, 0, LoadAction.CLEAR, StoreAction.STORE, r, g, b, a);
        }

        public Builder depthAttachment(IGpuTexture texture, int level,
                                       LoadAction load, StoreAction store, float clearDepth) {
            this.depth = new DepthAttachment(texture, level, load, store, clearDepth);
            return this;
        }

        public Builder clearDepth(IGpuTexture texture, float depthValue) {
            return this.depthAttachment(texture, 0, LoadAction.CLEAR, StoreAction.STORE, depthValue);
        }

        public RenderPassDesc build() {
            return new RenderPassDesc(List.copyOf(this.colors), this.depth,
                    this.viewportWidth, this.viewportHeight);
        }
    }
}
