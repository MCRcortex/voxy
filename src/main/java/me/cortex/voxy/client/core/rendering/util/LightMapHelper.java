package me.cortex.voxy.client.core.rendering.util;

import static org.lwjgl.opengl.GL11C.GL_RGBA;
import static org.lwjgl.opengl.GL11C.GL_RGBA8;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL13C.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL33.glBindSampler;
import static me.cortex.voxy.client.core.gl.GLCompat.bindTextureUnit;

import me.cortex.voxy.client.core.gpu.BackendType;
import me.cortex.voxy.client.core.gpu.IGpuSampler;
import me.cortex.voxy.client.core.gpu.IGpuTexture;
import me.cortex.voxy.client.core.gpu.RenderBackend;
import me.cortex.voxy.client.core.gpu.RenderBackendFactory;
import me.cortex.voxy.client.core.gpu.RenderEncoder;
import me.cortex.voxy.client.core.gpu.SamplerDesc;
import me.cortex.voxy.client.core.metal.MetalTexture;
import net.minecraft.client.Minecraft;
import org.lwjgl.system.MemoryUtil;

/**
 * Routes the MC lightmap to Voxy's terrain shaders.
 *
 * GL path: binds MC's GlTexture directly to the configured texture unit —
 * MC keeps its own lightmap upload current so we just point at it.
 *
 * Metal path (M13 chunk 2): MC's GlTexture handle is unusable from Metal,
 * so Voxy keeps a Shared-storage mirror texture and copies MC's 16×16
 * RGBA8 lightmap into it once per frame via {@code glGetTexImage} →
 * {@link IGpuTexture#uploadSubImage2D}. The mirror is then bound on the
 * active {@link RenderEncoder} alongside a LINEAR / CLAMP_TO_EDGE sampler
 * that matches MC's lightmap sampling. Lazy-allocated; gated by frame id
 * so the three terrain passes (opaque + temporal + translucent) share a
 * single readback.
 */
public class LightMapHelper {

    private static final int LIGHTMAP_WIDTH = 16;
    private static final int LIGHTMAP_HEIGHT = 16;
    private static final int LIGHTMAP_BYTES = LIGHTMAP_WIDTH * LIGHTMAP_HEIGHT * 4;

    private static IGpuTexture metalLightmap;
    private static IGpuSampler metalSampler;
    private static long stagingAddr;
    private static int lastSyncedFrame = -1;

    public static void bind(int lightingIndex) {
        glBindSampler(lightingIndex, 0);
        bindTextureUnit(lightingIndex, ((com.mojang.blaze3d.opengl.GlTexture)(Minecraft.getInstance().gameRenderer.lightTexture().getTextureView().texture())).glId());
    }

    /**
     * Metal-only path. Syncs MC's GL lightmap into the Voxy-side Shared
     * texture (once per {@code frameId}) and binds it + a matching sampler
     * at {@code slot} on the encoder.
     *
     * Safe to call from any of the three terrain passes in a frame — the
     * frame-id gate keeps the readback + upload to one round per frame
     * even though {@code renderTerrainMetal} dispatches three times.
     */
    public static void bindMetal(RenderEncoder encoder, int slot, int frameId) {
        ensureMetalResources();
        syncFromMc(frameId);
        encoder.setTexture(slot, metalLightmap);
        encoder.setSampler(slot, metalSampler);
    }

    private static void ensureMetalResources() {
        if (metalLightmap == null) {
            RenderBackend backend = RenderBackendFactory.get();
            if (backend.getType() != BackendType.METAL) {
                throw new IllegalStateException(
                        "LightMapHelper.bindMetal called on non-Metal backend: " + backend.getType());
            }
            MetalTexture tex = (MetalTexture) backend.createTexture(GL_TEXTURE_2D);
            tex.storeUploadable(GL_RGBA8, 1, LIGHTMAP_WIDTH, LIGHTMAP_HEIGHT);
            tex.name("Voxy.MCLightmapMirror");
            metalLightmap = tex;
        }
        if (metalSampler == null) {
            metalSampler = RenderBackendFactory.get().createSampler(SamplerDesc.builder()
                    .filter(SamplerDesc.Filter.LINEAR, SamplerDesc.Filter.LINEAR)
                    .mipFilter(SamplerDesc.MipFilter.NOT_MIPMAPPED)
                    .wrap(SamplerDesc.Wrap.CLAMP_TO_EDGE, SamplerDesc.Wrap.CLAMP_TO_EDGE)
                    .label("Voxy.MCLightmapSampler")
                    .build());
        }
        if (stagingAddr == 0L) {
            stagingAddr = MemoryUtil.nmemAllocChecked(LIGHTMAP_BYTES);
        }
    }

    /**
     * Read MC's 16×16 GL lightmap into the staging buffer and push it into
     * the Metal mirror. Apple's GL caps at 4.1 so we use the bind-then-read
     * legacy path rather than DSA's {@code glGetTextureImage}. The
     * 1 KB-per-call cost is negligible; the frame-id gate makes this happen
     * at most once per frame even with three terrain passes.
     */
    private static void syncFromMc(int frameId) {
        if (frameId == lastSyncedFrame) return;
        lastSyncedFrame = frameId;

        var lightTex = Minecraft.getInstance().gameRenderer.lightTexture().getTextureView().texture();
        int glId = ((com.mojang.blaze3d.opengl.GlTexture) lightTex).glId();

        int prevActive = glGetInteger(GL_ACTIVE_TEXTURE);
        int prevBinding;
        glActiveTexture(GL_TEXTURE0);
        prevBinding = glGetInteger(GL_TEXTURE_BINDING_2D);
        try {
            glBindTexture(GL_TEXTURE_2D, glId);
            org.lwjgl.opengl.GL11C.nglGetTexImage(
                    GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, stagingAddr);
        } finally {
            glBindTexture(GL_TEXTURE_2D, prevBinding);
            glActiveTexture(prevActive);
        }

        metalLightmap.uploadSubImage2D(0, 0, 0,
                LIGHTMAP_WIDTH, LIGHTMAP_HEIGHT,
                GL_RGBA, GL_UNSIGNED_BYTE, stagingAddr);
    }
}
