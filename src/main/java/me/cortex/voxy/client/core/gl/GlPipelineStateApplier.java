package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.client.core.gpu.PipelineState;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL20C;

/**
 * Applies {@link PipelineState} (depth / blend / raster) to GL global state.
 *
 * Metal and Vulkan bake this into a PSO at pipeline creation time; GL has no
 * such object, so we re-issue the relevant {@code glEnable}/{@code glBlendFunc}/
 * {@code glPolygonMode} calls each time the encoder binds a pipeline. Cheap on
 * GL — these are pure driver state, no GPU work.
 */
final class GlPipelineStateApplier {

    private GlPipelineStateApplier() {
    }

    static void apply(PipelineState state) {
        applyDepth(state.depth);
        applyBlend(state.blend);
        applyRaster(state.raster);
    }

    private static void applyDepth(PipelineState.DepthState depth) {
        if (depth.testEnabled) {
            GL11C.glEnable(GL11C.GL_DEPTH_TEST);
            GL11C.glDepthFunc(mapCompare(depth.compareOp));
        } else {
            GL11C.glDisable(GL11C.GL_DEPTH_TEST);
        }
        GL11C.glDepthMask(depth.writeEnabled);
    }

    private static void applyBlend(PipelineState.BlendState blend) {
        if (!blend.enabled) {
            GL11C.glDisable(GL11C.GL_BLEND);
            return;
        }
        GL11C.glEnable(GL11C.GL_BLEND);
        GL14C.glBlendFuncSeparate(
                mapBlendFactor(blend.srcColor), mapBlendFactor(blend.dstColor),
                mapBlendFactor(blend.srcAlpha), mapBlendFactor(blend.dstAlpha));
        GL20C.glBlendEquationSeparate(mapBlendOp(blend.colorOp), mapBlendOp(blend.alphaOp));
    }

    private static void applyRaster(PipelineState.RasterState raster) {
        switch (raster.cullMode) {
            case NONE -> GL11C.glDisable(GL11C.GL_CULL_FACE);
            case FRONT -> {
                GL11C.glEnable(GL11C.GL_CULL_FACE);
                GL11C.glCullFace(GL11C.GL_FRONT);
            }
            case BACK -> {
                GL11C.glEnable(GL11C.GL_CULL_FACE);
                GL11C.glCullFace(GL11C.GL_BACK);
            }
        }
        GL11C.glFrontFace(raster.frontFace == PipelineState.FrontFace.CLOCKWISE
                ? GL11C.GL_CW : GL11C.GL_CCW);
        GL11C.glPolygonMode(GL11C.GL_FRONT_AND_BACK,
                raster.polygonMode == PipelineState.PolygonMode.LINE ? GL11C.GL_LINE : GL11C.GL_FILL);
    }

    private static int mapCompare(PipelineState.CompareOp op) {
        return switch (op) {
            case NEVER -> GL11C.GL_NEVER;
            case LESS -> GL11C.GL_LESS;
            case EQUAL -> GL11C.GL_EQUAL;
            case LESS_EQUAL -> GL11C.GL_LEQUAL;
            case GREATER -> GL11C.GL_GREATER;
            case NOT_EQUAL -> GL11C.GL_NOTEQUAL;
            case GREATER_EQUAL -> GL11C.GL_GEQUAL;
            case ALWAYS -> GL11C.GL_ALWAYS;
        };
    }

    private static int mapBlendFactor(PipelineState.BlendFactor f) {
        return switch (f) {
            case ZERO -> GL11C.GL_ZERO;
            case ONE -> GL11C.GL_ONE;
            case SRC_COLOR -> GL11C.GL_SRC_COLOR;
            case ONE_MINUS_SRC_COLOR -> GL11C.GL_ONE_MINUS_SRC_COLOR;
            case DST_COLOR -> GL11C.GL_DST_COLOR;
            case ONE_MINUS_DST_COLOR -> GL11C.GL_ONE_MINUS_DST_COLOR;
            case SRC_ALPHA -> GL11C.GL_SRC_ALPHA;
            case ONE_MINUS_SRC_ALPHA -> GL11C.GL_ONE_MINUS_SRC_ALPHA;
            case DST_ALPHA -> GL11C.GL_DST_ALPHA;
            case ONE_MINUS_DST_ALPHA -> GL11C.GL_ONE_MINUS_DST_ALPHA;
        };
    }

    private static int mapBlendOp(PipelineState.BlendOp op) {
        return switch (op) {
            case ADD -> GL14C.GL_FUNC_ADD;
            case SUBTRACT -> GL14C.GL_FUNC_SUBTRACT;
            case REVERSE_SUBTRACT -> GL14C.GL_FUNC_REVERSE_SUBTRACT;
            case MIN -> GL14C.GL_MIN;
            case MAX -> GL14C.GL_MAX;
        };
    }
}
