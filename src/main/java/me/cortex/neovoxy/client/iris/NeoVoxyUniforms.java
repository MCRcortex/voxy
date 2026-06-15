package me.cortex.neovoxy.client.iris;

import me.cortex.neovoxy.client.config.NeoVoxyConfig;
import me.cortex.neovoxy.client.core.IGetNeoVoxyRenderSystem;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.function.Supplier;

import static net.irisshaders.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;

public class NeoVoxyUniforms {

    public static Matrix4f getViewProjection() {//This is 1 frame late ;-; cries, since the update occurs _before_ the neovoxy render pipeline
        var getVrs = (IGetNeoVoxyRenderSystem) Minecraft.getInstance().levelRenderer;
        if (getVrs == null || getVrs.getNeoVoxyRenderSystem() == null) {
            return new Matrix4f();
        }
        var vrs = getVrs.getNeoVoxyRenderSystem();
        return new Matrix4f(vrs.getViewport().MVP);
    }

    public static Matrix4f getModelView() {//This is 1 frame late ;-; cries, since the update occurs _before_ the neovoxy render pipeline
        var getVrs = (IGetNeoVoxyRenderSystem) Minecraft.getInstance().levelRenderer;
        if (getVrs == null || getVrs.getNeoVoxyRenderSystem() == null) {
            return new Matrix4f();
        }
        var vrs = getVrs.getNeoVoxyRenderSystem();
        return new Matrix4f(vrs.getViewport().modelView);
    }

    public static Matrix4f getProjection() {//This is 1 frame late ;-; cries, since the update occurs _before_ the neovoxy render pipeline
        var getVrs = (IGetNeoVoxyRenderSystem) Minecraft.getInstance().levelRenderer;
        if (getVrs == null || getVrs.getNeoVoxyRenderSystem() == null) {
            return new Matrix4f();
        }
        var vrs = getVrs.getNeoVoxyRenderSystem();
        var mat = vrs.getViewport().projection;
        if (mat == null) {
            return new Matrix4f();
        }
        return new Matrix4f(mat);
    }

    public static void addUniforms(UniformHolder uniforms) {
        uniforms
                .uniform1i(PER_FRAME, "vxRenderDistance", ()-> NeoVoxyConfig.CONFIG.sectionRenderDistance*32)//In chunks
                .uniformMatrix(PER_FRAME, "vxViewProj", NeoVoxyUniforms::getViewProjection)
                .uniformMatrix(PER_FRAME, "vxViewProjInv", new Inverted(NeoVoxyUniforms::getViewProjection))
                .uniformMatrix(PER_FRAME, "vxViewProjPrev", new PreviousMat(NeoVoxyUniforms::getViewProjection))
                .uniformMatrix(PER_FRAME, "vxModelView", NeoVoxyUniforms::getModelView)
                .uniformMatrix(PER_FRAME, "vxModelViewInv", new Inverted(NeoVoxyUniforms::getModelView))
                .uniformMatrix(PER_FRAME, "vxModelViewPrev", new PreviousMat(NeoVoxyUniforms::getModelView))
                .uniformMatrix(PER_FRAME, "vxProj", NeoVoxyUniforms::getProjection)
                .uniformMatrix(PER_FRAME, "vxProjInv", new Inverted(NeoVoxyUniforms::getProjection))
                .uniformMatrix(PER_FRAME, "vxProjPrev", new PreviousMat(NeoVoxyUniforms::getProjection));

        if (IrisShaderPatch.IMPERSONATE_DISTANT_HORIZONS) {
            uniforms
                    .uniform1f(PER_FRAME, "dhNearPlane", ()->16)//Presently hardcoded in neovoxy
                    .uniform1f(PER_FRAME, "dhFarPlane", ()->16*3000)//Presently hardcoded in neovoxy

                    .uniform1i(PER_FRAME, "dhRenderDistance", ()-> NeoVoxyConfig.CONFIG.sectionRenderDistance*32*16)//In blocks
                    .uniformMatrix(PER_FRAME, "dhProjection", NeoVoxyUniforms::getProjection)
                    .uniformMatrix(PER_FRAME, "dhProjectionInverse", new Inverted(NeoVoxyUniforms::getProjection))
                    .uniformMatrix(PER_FRAME, "dhPreviousProjection", new PreviousMat(NeoVoxyUniforms::getProjection));
        }
    }




    private record Inverted(Supplier<Matrix4fc> parent) implements Supplier<Matrix4fc> {
        private Inverted(Supplier<Matrix4fc> parent) {
            this.parent = parent;
        }

        public Matrix4fc get() {
            Matrix4f copy = new Matrix4f(this.parent.get());
            copy.invert();
            return copy;
        }

        public Supplier<Matrix4fc> parent() {
            return this.parent;
        }
    }

    private static class PreviousMat implements Supplier<Matrix4fc> {
        private final Supplier<Matrix4fc> parent;
        private Matrix4f previous;

        PreviousMat(Supplier<Matrix4fc> parent) {
            this.parent = parent;
            this.previous = new Matrix4f();
        }

        public Matrix4fc get() {
            Matrix4f previous = this.previous;
            this.previous = new Matrix4f(this.parent.get());
            return previous;
        }
    }
}
