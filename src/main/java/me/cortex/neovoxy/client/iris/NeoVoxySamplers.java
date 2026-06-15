package me.cortex.neovoxy.client.iris;

import net.irisshaders.iris.gl.sampler.GlSampler;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;

public class NeoVoxySamplers {
    public static void addSamplers(IrisRenderingPipeline pipeline, SamplerHolder samplers) {
        var patchData = ((IGetNeoVoxyPatchData)pipeline).neovoxy$getPatchData();
        if (patchData != null) {
            String[] opaqueNames = new String[]{"vxDepthTexOpaque"};
            String[] translucentNames = new String[]{"vxDepthTexTrans"};

            if (IrisShaderPatch.IMPERSONATE_DISTANT_HORIZONS) {
                opaqueNames = new String[]{"vxDepthTexOpaque", "dhDepthTex1"};
                translucentNames = new String[]{"vxDepthTexTrans", "dhDepthTex", "dhDepthTex0"};
            }

            //TODO replace ()->0 with the actual depth texture id
            samplers.addDynamicSampler(TextureType.TEXTURE_2D, () -> {
                var pipeData = ((IGetIrisNeoVoxyPipelineData)pipeline).neovoxy$getPipelineData();
                if (pipeData == null) {
                    return 0;
                }
                if (pipeData.thePipeline == null) {
                    return 0;
                }

                //In theory the first frame could be null
                var dt = pipeData.thePipeline.fb.getDepthTex();
                if (dt == null) {
                    return 0;
                }
                return dt.id;
            }, ()->GlSampler.MIPPED_NEAREST_NEAREST, opaqueNames);

            samplers.addDynamicSampler(TextureType.TEXTURE_2D, () -> {
                var pipeData = ((IGetIrisNeoVoxyPipelineData)pipeline).neovoxy$getPipelineData();
                if (pipeData == null) {
                    return 0;
                }
                if (pipeData.thePipeline == null) {
                    return 0;
                }
                //In theory the first frame could be null
                var dt = pipeData.thePipeline.fbTranslucent.getDepthTex();
                if (dt == null) {
                    return 0;
                }
                return dt.id;
            }, ()->GlSampler.MIPPED_NEAREST_NEAREST, translucentNames);
        }
    }
}
