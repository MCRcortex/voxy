package me.cortex.zenith.client.core.rendering;

import me.cortex.zenith.client.core.gl.shader.Shader;
import me.cortex.zenith.client.core.gl.shader.ShaderType;
import me.cortex.zenith.client.core.rendering.util.UploadStream;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;

import static org.lwjgl.opengl.ARBMultiDrawIndirect.glMultiDrawElementsIndirect;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.NVMeshShader.glDrawMeshTasksNV;

//TODO: make this a 2 phase culling system
// first phase renders the terrain, in the terrain task shader it also checks if the section was not visible in the frustum but now is
// and then renders it and marks it as being in the frustum
public class NvFarWorldRenderer extends AbstractFarWorldRenderer {
    private final Shader primaryTerrainRaster = Shader.make()
            .add(ShaderType.TASK, "voxelmon:lod/nvmesh/primary.task")
            .add(ShaderType.MESH, "voxelmon:lod/nvmesh/primary.mesh")
            .add(ShaderType.FRAGMENT, "voxelmon:lod/nvmesh/primary.frag")
            .compile();

    public NvFarWorldRenderer(int geometrySize, int maxSections) {
        super(geometrySize, maxSections);
    }

    @Override
    protected void setupVao() {

    }

    @Override
    public void renderFarAwayOpaque(MatrixStack stack, double cx, double cy, double cz) {
        if (this.geometry.getSectionCount() == 0) {
            return;
        }
        RenderLayer.getCutoutMipped().startDrawing();

        UploadStream.INSTANCE.commit();

        glBindVertexArray(this.vao);
        this.primaryTerrainRaster.bind();
        glDrawMeshTasksNV(0, this.geometry.getSectionCount());
        glBindVertexArray(0);


        RenderLayer.getCutoutMipped().endDrawing();
    }

    @Override
    public void shutdown() {
        super.shutdown();

    }
}
