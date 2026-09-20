package me.cortex.voxy.client.iris;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.RenderProperties;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.function.Supplier;

import static net.irisshaders.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;

public class VoxyUniforms {
    //TODO: fix this so that it directly capturesthe render system? (or atleast the holder?)

    public static Matrix4f getViewProjection() {
        var vrs = IVoxyRenderSystemHolder.getNullable();
        if (vrs == null) {
            return new Matrix4f();
        }
        var view = vrs.getViewport();
        return conditionallyReverseRevZ(view.properties, new Matrix4f(view.MVP));
    }

    public static Matrix4f getModelView() {
        var vrs = IVoxyRenderSystemHolder.getNullable();
        if (vrs == null) {
            return new Matrix4f();
        }
        return new Matrix4f(vrs.getViewport().modelView);
    }

    public static Matrix4f getProjection() {
        var vrs = IVoxyRenderSystemHolder.getNullable();
        if (vrs == null) {
            return new Matrix4f();
        }
        var view = vrs.getViewport();
        var mat = view.projection;
        if (mat == null) {
            return new Matrix4f();
        }
        return conditionallyReverseRevZ(view.properties, new Matrix4f(mat));
    }

    //In is safe to mutate
    private static Matrix4f conditionallyReverseRevZ(RenderProperties props, Matrix4f in) {
        //TODO: FIXME add and wire up the conditional disabling of the reverse revz patcher
        if (!props.isReverseZ()) return in;

        //todo:this do the conversion, REMENER TO KEEP RESPECT TO props.isZero2One()
        //in.mul

        return in;
    }

    public static void addUniforms(UniformHolder uniforms) {
        uniforms
                .uniform1i(PER_FRAME, "vxRenderDistance", ()->Math.round(VoxyConfig.CONFIG.sectionRenderDistance*32))//In chunks
                .uniformMatrix(PER_FRAME, "vxViewProj", VoxyUniforms::getViewProjection)
                .uniformMatrix(PER_FRAME, "vxViewProjInv", new Inverted(VoxyUniforms::getViewProjection))
                .uniformMatrix(PER_FRAME, "vxViewProjPrev", new PreviousMat(VoxyUniforms::getViewProjection))
                .uniformMatrix(PER_FRAME, "vxModelView", VoxyUniforms::getModelView)
                .uniformMatrix(PER_FRAME, "vxModelViewInv", new Inverted(VoxyUniforms::getModelView))
                .uniformMatrix(PER_FRAME, "vxModelViewPrev", new PreviousMat(VoxyUniforms::getModelView))
                .uniformMatrix(PER_FRAME, "vxProj", VoxyUniforms::getProjection)
                .uniformMatrix(PER_FRAME, "vxProjInv", new Inverted(VoxyUniforms::getProjection))
                .uniformMatrix(PER_FRAME, "vxProjPrev", new PreviousMat(VoxyUniforms::getProjection));

        /*
        if (IrisShaderPatch.IMPERSONATE_DISTANT_HORIZONS) {
            uniforms
                    .uniform1f(PER_FRAME, "dhNearPlane", ()->16)//Presently hardcoded in voxy
                    .uniform1f(PER_FRAME, "dhFarPlane", ()->16*3000)//Presently hardcoded in voxy

                    .uniform1i(PER_FRAME, "dhRenderDistance", ()->Math.round(VoxyConfig.CONFIG.sectionRenderDistance*32*16))//In blocks
                    .uniformMatrix(PER_FRAME, "dhProjection", VoxyUniforms::getProjection)
                    .uniformMatrix(PER_FRAME, "dhProjectionInverse", new Inverted(VoxyUniforms::getProjection))
                    .uniformMatrix(PER_FRAME, "dhPreviousProjection", new PreviousMat(VoxyUniforms::getProjection));
        }*/
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
