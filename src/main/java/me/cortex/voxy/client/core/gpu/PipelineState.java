package me.cortex.voxy.client.core.gpu;

/**
 * Backend-agnostic graphics pipeline static state — depth/stencil, blend,
 * rasterization. Carried inside {@link GraphicsPipelineDesc}; baked into the
 * Metal MTLRenderPipelineState / MTLDepthStencilState / Vulkan VkPipeline at
 * creation time so the encoder doesn't need per-state setters.
 *
 * Use {@link #DEFAULT} for "Voxy's typical opaque-mesh" shape: depth-test
 * less-equal + write enabled, blend disabled, cull back faces. Translucent
 * passes override blend; debug paths override polygon mode and cull mode.
 */
public final class PipelineState {

    public final DepthState depth;
    public final BlendState blend;
    public final RasterState raster;

    public PipelineState(DepthState depth, BlendState blend, RasterState raster) {
        this.depth = depth != null ? depth : DepthState.DEFAULT;
        this.blend = blend != null ? blend : BlendState.OPAQUE;
        this.raster = raster != null ? raster : RasterState.DEFAULT;
    }

    /**
     * Test-friendly default — no depth, no blend, no cull. Smoke tests rendering
     * to a single color attachment without a depth target use this implicitly.
     * Production callers should pick {@link #OPAQUE_MESH}, {@link #TRANSLUCENT_MESH},
     * or build their own.
     */
    public static final PipelineState DEFAULT = new PipelineState(DepthState.DISABLED, BlendState.OPAQUE, RasterState.NO_CULL);

    /** Voxy's standard opaque mesh state: depth less-equal + write, no blend, cull back, fill. */
    public static final PipelineState OPAQUE_MESH = new PipelineState(DepthState.DEFAULT, BlendState.OPAQUE, RasterState.DEFAULT);

    /** State for Voxy's translucent terrain pass — premultiplied-alpha blend, depth test but no write. */
    public static final PipelineState TRANSLUCENT_MESH = new PipelineState(DepthState.TEST_NO_WRITE, BlendState.PREMULTIPLIED_ALPHA, RasterState.DEFAULT);

    public enum CompareOp {
        NEVER, LESS, EQUAL, LESS_EQUAL, GREATER, NOT_EQUAL, GREATER_EQUAL, ALWAYS
    }

    public enum CullMode { NONE, FRONT, BACK }

    public enum FrontFace { CLOCKWISE, COUNTER_CLOCKWISE }

    public enum PolygonMode { FILL, LINE }

    public enum BlendFactor {
        ZERO, ONE,
        SRC_COLOR, ONE_MINUS_SRC_COLOR,
        DST_COLOR, ONE_MINUS_DST_COLOR,
        SRC_ALPHA, ONE_MINUS_SRC_ALPHA,
        DST_ALPHA, ONE_MINUS_DST_ALPHA
    }

    public enum BlendOp { ADD, SUBTRACT, REVERSE_SUBTRACT, MIN, MAX }

    public static final class DepthState {
        public final boolean testEnabled;
        public final boolean writeEnabled;
        public final CompareOp compareOp;

        public DepthState(boolean testEnabled, boolean writeEnabled, CompareOp compareOp) {
            this.testEnabled = testEnabled;
            this.writeEnabled = writeEnabled;
            this.compareOp = compareOp;
        }

        /** Standard opaque mesh: test enabled, write enabled, less-equal. */
        public static final DepthState DEFAULT = new DepthState(true, true, CompareOp.LESS_EQUAL);
        /** Translucent: test enabled, write disabled. */
        public static final DepthState TEST_NO_WRITE = new DepthState(true, false, CompareOp.LESS_EQUAL);
        /** Disabled: no depth test, no write. */
        public static final DepthState DISABLED = new DepthState(false, false, CompareOp.ALWAYS);
    }

    public static final class BlendState {
        public final boolean enabled;
        public final BlendFactor srcColor, dstColor;
        public final BlendOp colorOp;
        public final BlendFactor srcAlpha, dstAlpha;
        public final BlendOp alphaOp;

        public BlendState(boolean enabled,
                          BlendFactor srcColor, BlendFactor dstColor, BlendOp colorOp,
                          BlendFactor srcAlpha, BlendFactor dstAlpha, BlendOp alphaOp) {
            this.enabled = enabled;
            this.srcColor = srcColor;
            this.dstColor = dstColor;
            this.colorOp = colorOp;
            this.srcAlpha = srcAlpha;
            this.dstAlpha = dstAlpha;
            this.alphaOp = alphaOp;
        }

        /** Blend disabled — write source color directly. */
        public static final BlendState OPAQUE = new BlendState(false,
                BlendFactor.ONE, BlendFactor.ZERO, BlendOp.ADD,
                BlendFactor.ONE, BlendFactor.ZERO, BlendOp.ADD);

        /** Standard alpha blend: src*alpha + dst*(1-alpha) for color, alpha=1 for output. */
        public static final BlendState ALPHA = new BlendState(true,
                BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA, BlendOp.ADD,
                BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA, BlendOp.ADD);

        /** Premultiplied alpha — matches Voxy's translucent shader output. */
        public static final BlendState PREMULTIPLIED_ALPHA = new BlendState(true,
                BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA, BlendOp.ADD,
                BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA, BlendOp.ADD);
    }

    public static final class RasterState {
        public final CullMode cullMode;
        public final FrontFace frontFace;
        public final PolygonMode polygonMode;

        public RasterState(CullMode cullMode, FrontFace frontFace, PolygonMode polygonMode) {
            this.cullMode = cullMode;
            this.frontFace = frontFace;
            this.polygonMode = polygonMode;
        }

        /** Standard: cull back, CCW front face, fill. */
        public static final RasterState DEFAULT = new RasterState(CullMode.BACK, FrontFace.COUNTER_CLOCKWISE, PolygonMode.FILL);
        /** Two-sided rendering — used by Voxy's terrain so back-face culling can be toggled per-frame. */
        public static final RasterState NO_CULL = new RasterState(CullMode.NONE, FrontFace.COUNTER_CLOCKWISE, PolygonMode.FILL);
        /** Wireframe debug. */
        public static final RasterState WIREFRAME = new RasterState(CullMode.NONE, FrontFace.COUNTER_CLOCKWISE, PolygonMode.LINE);
    }
}
