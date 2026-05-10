package me.cortex.voxy.client.core.gpu;

/**
 * Sampler state description. Covers what Voxy's existing samplers configure
 * via {@code glSamplerParameteri} — min/mag filter, mip filter, wrap modes,
 * LOD clamps, optional comparison sampling.
 *
 * Use {@link Builder} for non-default config; the defaults match a typical
 * Voxy "nearest, clamp-to-edge, no mipmaps, no compare" sampler so common
 * cases stay terse.
 */
public final class SamplerDesc {

    public final Filter minFilter;
    public final Filter magFilter;
    public final MipFilter mipFilter;
    public final Wrap wrapS;
    public final Wrap wrapT;
    public final Wrap wrapR;
    public final float lodMinClamp;
    public final float lodMaxClamp;
    public final boolean compareEnable;
    public final PipelineState.CompareOp compareOp;
    public final String label;

    private SamplerDesc(Builder b) {
        this.minFilter = b.minFilter;
        this.magFilter = b.magFilter;
        this.mipFilter = b.mipFilter;
        this.wrapS = b.wrapS;
        this.wrapT = b.wrapT;
        this.wrapR = b.wrapR;
        this.lodMinClamp = b.lodMinClamp;
        this.lodMaxClamp = b.lodMaxClamp;
        this.compareEnable = b.compareEnable;
        this.compareOp = b.compareOp;
        this.label = b.label;
    }

    public static Builder builder() { return new Builder(); }

    public enum Filter { NEAREST, LINEAR }

    /** Per Apple/Vulkan separation — mip filter is independent of min filter. */
    public enum MipFilter { NOT_MIPMAPPED, NEAREST, LINEAR }

    public enum Wrap { CLAMP_TO_EDGE, REPEAT, MIRRORED_REPEAT, CLAMP_TO_ZERO }

    public static final class Builder {
        private Filter minFilter = Filter.NEAREST;
        private Filter magFilter = Filter.NEAREST;
        private MipFilter mipFilter = MipFilter.NOT_MIPMAPPED;
        private Wrap wrapS = Wrap.CLAMP_TO_EDGE;
        private Wrap wrapT = Wrap.CLAMP_TO_EDGE;
        private Wrap wrapR = Wrap.CLAMP_TO_EDGE;
        private float lodMinClamp = 0.0f;
        private float lodMaxClamp = Float.MAX_VALUE;
        private boolean compareEnable = false;
        private PipelineState.CompareOp compareOp = PipelineState.CompareOp.NEVER;
        private String label;

        public Builder filter(Filter min, Filter mag) { this.minFilter = min; this.magFilter = mag; return this; }
        public Builder mipFilter(MipFilter f) { this.mipFilter = f; return this; }
        public Builder wrap(Wrap s, Wrap t) { this.wrapS = s; this.wrapT = t; return this; }
        public Builder wrap(Wrap s, Wrap t, Wrap r) { this.wrapS = s; this.wrapT = t; this.wrapR = r; return this; }
        public Builder lod(float min, float max) { this.lodMinClamp = min; this.lodMaxClamp = max; return this; }
        public Builder compare(PipelineState.CompareOp op) {
            this.compareEnable = true;
            this.compareOp = op;
            return this;
        }
        public Builder label(String label) { this.label = label; return this; }
        public SamplerDesc build() { return new SamplerDesc(this); }
    }
}
