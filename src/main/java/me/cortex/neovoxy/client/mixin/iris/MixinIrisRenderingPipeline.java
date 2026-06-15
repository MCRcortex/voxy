package me.cortex.neovoxy.client.mixin.iris;

import me.cortex.neovoxy.client.core.IGetNeoVoxyRenderSystem;
import me.cortex.neovoxy.client.core.util.IrisUtil;
import me.cortex.neovoxy.client.iris.IGetIrisNeoVoxyPipelineData;
import me.cortex.neovoxy.client.iris.IGetNeoVoxyPatchData;
import me.cortex.neovoxy.client.iris.IrisShaderPatch;
import me.cortex.neovoxy.client.iris.IrisNeoVoxyRenderPipelineData;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
public class MixinIrisRenderingPipeline implements IGetNeoVoxyPatchData, IGetIrisNeoVoxyPipelineData {
    @Shadow @Final private CustomUniforms customUniforms;
    @Shadow private ShaderStorageBufferHolder shaderStorageBufferHolder;
    @Unique IrisShaderPatch patchData;
    @Unique
    IrisNeoVoxyRenderPipelineData pipeline;

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/pipeline/transform/ShaderPrinter;resetPrintState()V", shift = At.Shift.AFTER))
    private void neovoxy$injectPatchDataStore(ProgramSet programSet, CallbackInfo ci) {
        if (IrisUtil.SHADER_SUPPORT) {
            this.patchData = ((IGetNeoVoxyPatchData) programSet).neovoxy$getPatchData();
        }
    }

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/pipeline/IrisRenderingPipeline;createSetupComputes([Lnet/irisshaders/iris/shaderpack/programs/ComputeSource;Lnet/irisshaders/iris/shaderpack/programs/ProgramSet;Lnet/irisshaders/iris/shaderpack/texture/TextureStage;)[Lnet/irisshaders/iris/gl/program/ComputeProgram;"))
    private void neovoxy$injectPipeline(ProgramSet programSet, CallbackInfo ci) {
        if (this.patchData != null) {
            this.pipeline = IrisNeoVoxyRenderPipelineData.buildPipeline((IrisRenderingPipeline)(Object)this, this.patchData, this.customUniforms, this.shaderStorageBufferHolder);
        }
    }

    @Inject(method = "beginLevelRendering", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/opengl/GlStateManager;_activeTexture(I)V", shift = At.Shift.BEFORE), remap = false)
    private void neovoxy$injectViewportSetup(CallbackInfo ci) {
        if (IrisUtil.CAPTURED_VIEWPORT_PARAMETERS != null) {
            var renderer = ((IGetNeoVoxyRenderSystem) Minecraft.getInstance().levelRenderer).getNeoVoxyRenderSystem();
            if (renderer != null) {
                IrisUtil.CAPTURED_VIEWPORT_PARAMETERS.apply(renderer);
            }
        }
    }

    @Override
    public IrisShaderPatch neovoxy$getPatchData() {
        return this.patchData;
    }

    @Override
    public IrisNeoVoxyRenderPipelineData neovoxy$getPipelineData() {
        return this.pipeline;
    }
}
