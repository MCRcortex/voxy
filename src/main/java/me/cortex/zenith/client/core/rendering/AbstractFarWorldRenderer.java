package me.cortex.zenith.client.core.rendering;

//NOTE: an idea on how to do it is so that any render section, we _keep_ aquired (yes this will be very memory intensive)
// could maybe tosomething else

import me.cortex.zenith.client.core.gl.GlBuffer;
import me.cortex.zenith.client.core.model.ModelManager;
import me.cortex.zenith.client.core.rendering.building.BuiltSection;
import me.cortex.zenith.client.core.rendering.building.BuiltSectionGeometry;
import me.cortex.zenith.client.core.rendering.util.UploadStream;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.FrustumIntersection;
import org.lwjgl.system.MemoryUtil;

import java.util.List;

import static org.lwjgl.opengl.ARBMultiDrawIndirect.glMultiDrawElementsIndirect;
import static org.lwjgl.opengl.GL30.*;

//can make it so that register the key of the sections we have rendered, then when a section changes and is registered,
// dispatch an update to the render section data builder which then gets consumed by the render system and updates
// the rendered data

//Contains all the logic to render the world and manage gpu memory
// processes section load,unload,update render data and renders the world each frame


//Todo: tinker with having the compute shader where each thread is a position to render? maybe idk
public abstract class AbstractFarWorldRenderer {
    protected final int vao = glGenVertexArrays();

    protected final GlBuffer uniformBuffer;
    protected final GeometryManager geometry;
    protected final ModelManager models;
    protected final GlBuffer lightDataBuffer;

    //Current camera base level section position
    protected int sx;
    protected int sy;
    protected int sz;

    protected FrustumIntersection frustum;

    public AbstractFarWorldRenderer(int geometrySize, int maxSections) {
        this.uniformBuffer  = new GlBuffer(1024);
        this.lightDataBuffer  = new GlBuffer(256*4);//256 of uint
        this.geometry = new GeometryManager(geometrySize*8L, maxSections);
        this.models = new ModelManager(16);
    }

    protected abstract void setupVao();

    public void setupRender(Frustum frustum, Camera camera) {
        this.frustum = frustum.frustumIntersection;

        this.sx = camera.getBlockPos().getX() >> 5;
        this.sy = camera.getBlockPos().getY() >> 5;
        this.sz = camera.getBlockPos().getZ() >> 5;


        //TODO: move this to a render function that is only called
        // once per frame when using multi viewport mods
        //it shouldent matter if its called multiple times a frame however, as its synced with fences
        UploadStream.INSTANCE.tick();

        //Update the lightmap
        {
            long upload = UploadStream.INSTANCE.upload(this.lightDataBuffer, 0, 256*4);
            var lmt = MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager().texture.getImage();
            for (int light = 0; light < 256; light++) {
                int x = light&0xF;
                int y = ((light>>4)&0xF);
                int sample = lmt.getColor(x,y);
                sample = ((sample&0xFF0000)>>16)|(sample&0xFF00)|((sample&0xFF)<<16);
                MemoryUtil.memPutInt(upload + (((x<<4)|(15-y))*4), sample|(0xFF<<28));//Skylight is inverted
            }
        }

        //Upload any new geometry
        this.geometry.uploadResults();
    }

    public abstract void renderFarAwayOpaque(MatrixStack stack, double cx, double cy, double cz);

    public void enqueueResult(BuiltSection result) {
        this.geometry.enqueueResult(result);
    }

    public void addDebugData(List<String> debug) {
        this.models.addDebugInfo(debug);
    }

    public void shutdown() {
        glDeleteVertexArrays(this.vao);
        this.models.free();
        this.geometry.free();
        this.uniformBuffer.free();
        this.lightDataBuffer.free();
    }

    public ModelManager getModelManager() {
        return this.models;
    }
}
