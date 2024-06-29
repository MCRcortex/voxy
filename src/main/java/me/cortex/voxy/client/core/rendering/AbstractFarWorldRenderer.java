package me.cortex.voxy.client.core.rendering;

//NOTE: an idea on how to do it is so that any render section, we _keep_ aquired (yes this will be very memory intensive)
// could maybe tosomething else

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.model.ModelManager;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.util.DownloadStream;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.lwjgl.opengl.ARBMultiDrawIndirect.glMultiDrawElementsIndirect;
import static org.lwjgl.opengl.GL30.*;

//can make it so that register the key of the sections we have rendered, then when a section changes and is registered,
// dispatch an update to the render section data builder which then gets consumed by the render system and updates
// the rendered data

//Contains all the logic to render the world and manage gpu memory
// processes section load,unload,update render data and renders the world each frame


//Todo: tinker with having the compute shader where each thread is a position to render? maybe idk
public abstract class AbstractFarWorldRenderer <T extends Viewport, J extends AbstractGeometryManager> {
    public static final int STATIC_VAO = glGenVertexArrays();

    protected final GlBuffer uniformBuffer;
    protected final J geometry;
    protected final ModelManager models;
    protected final GlBuffer lightDataBuffer;

    protected final int maxSections;

    //Current camera base level section position
    protected int sx;
    protected int sy;
    protected int sz;

    protected FrustumIntersection frustum;

    private final List<T> viewports = new ArrayList<>();

    protected IntArrayList updatedSectionIds;

    private final ConcurrentLinkedDeque<Mapper.StateEntry> blockStateUpdates = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<Mapper.BiomeEntry> biomeUpdates = new ConcurrentLinkedDeque<>();
    public AbstractFarWorldRenderer(J geometry) {
        this.maxSections = geometry.getMaxSections();
        this.uniformBuffer  = new GlBuffer(1024);
        this.lightDataBuffer  = new GlBuffer(256*4);//256 of uint
        this.geometry = geometry;
        this.models = new ModelManager(16);
    }

    public void setupRender(Frustum frustum, Camera camera) {
        this.frustum = frustum.frustumIntersection;

        this.sx = camera.getBlockPos().getX() >> 5;
        this.sy = camera.getBlockPos().getY() >> 5;
        this.sz = camera.getBlockPos().getZ() >> 5;

        //TODO: move this to a render function that is only called
        // once per frame when using multi viewport mods
        //it shouldent matter if its called multiple times a frame however, as its synced with fences
        UploadStream.INSTANCE.tick();
        DownloadStream.INSTANCE.tick();

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
        this.updatedSectionIds = this.geometry.uploadResults();
        {
            boolean didHaveBiomeChange = false;

            //Do any BiomeChanges
            while (!this.biomeUpdates.isEmpty()) {
                var update = this.biomeUpdates.pop();
                var biomeReg = MinecraftClient.getInstance().world.getRegistryManager().get(RegistryKeys.BIOME);
                this.models.addBiome(update.id, biomeReg.get(Identifier.of(update.biome)));
                didHaveBiomeChange = true;
            }

            if (didHaveBiomeChange) {
                UploadStream.INSTANCE.commit();
            }

            int maxUpdatesPerFrame = 40;

            //Do any BlockChanges
            while ((!this.blockStateUpdates.isEmpty()) && (maxUpdatesPerFrame-- > 0)) {
                var update = this.blockStateUpdates.pop();
                this.models.addEntry(update.id, update.state);
            }
            //this.models.bakery.renderFaces(Blocks.ROSE_BUSH.getDefaultState(), 1234, false);
        }

        //TODO: fix this in a better way than this ungodly hacky stuff, causes clouds to dissapear
        //RenderSystem.setShaderFogColor(1f, 1f, 1f, 0f);
        RenderSystem.setShaderFogEnd(99999999);
        RenderSystem.setShaderFogStart(9999999);
    }

    public abstract void renderFarAwayOpaque(T viewport);

    public abstract void renderFarAwayTranslucent(T viewport);

    public void enqueueResult(BuiltSection result) {
        this.geometry.enqueueResult(result);
    }

    public void addBlockState(Mapper.StateEntry entry) {
        this.blockStateUpdates.add(entry);
    }

    public void addBiome(Mapper.BiomeEntry entry) {
        this.biomeUpdates.add(entry);
    }

    public void addDebugData(List<String> debug) {
        this.models.addDebugInfo(debug);
        debug.add("Geometry buffer usage: " + ((float)Math.round((this.geometry.getGeometryBufferUsage()*100000))/1000) + "%");
        debug.add("Render Sections: " + this.geometry.getSectionCount());
    }

    public void shutdown() {
        this.models.free();
        this.geometry.free();
        this.uniformBuffer.free();
        this.lightDataBuffer.free();
    }

    public ModelManager getModelManager() {
        return this.models;
    }

    public final T createViewport() {
        var viewport = createViewport0();
        this.viewports.add(viewport);
        return viewport;
    }

    final void removeViewport(T viewport) {
        this.viewports.remove(viewport);
    }

    protected abstract T createViewport0();

    public boolean usesMeshlets() {
        return false;
    }
}
