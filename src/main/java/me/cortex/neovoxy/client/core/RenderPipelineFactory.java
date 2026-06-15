package me.cortex.neovoxy.client.core;

import me.cortex.neovoxy.client.core.rendering.hierachical.AsyncNodeManager;
import me.cortex.neovoxy.client.core.rendering.hierachical.HierarchicalOcclusionTraverser;
import me.cortex.neovoxy.client.core.rendering.hierachical.NodeCleaner;
// TODO: Re-enable Iris integration when NeoForge 1.21.1 version available
// import me.cortex.neovoxy.client.core.util.IrisUtil;
// import me.cortex.neovoxy.client.iris.IGetIrisNeoVoxyPipelineData;
import me.cortex.neovoxy.common.Logger;
// import net.irisshaders.iris.Iris;
// import net.irisshaders.iris.api.v0.IrisApi;

import java.util.function.BooleanSupplier;

public class RenderPipelineFactory {
    public static AbstractRenderPipeline createPipeline(AsyncNodeManager nodeManager, NodeCleaner nodeCleaner, HierarchicalOcclusionTraverser traversal, BooleanSupplier frexSupplier) {
        //Note this is where will choose/create e.g. IrisRenderPipeline or normal pipeline
        AbstractRenderPipeline pipeline = null;
        // Iris integration disabled for NeoForge 1.21.1 port
        // if (IrisUtil.IRIS_INSTALLED && IrisUtil.SHADER_SUPPORT) {
        //     pipeline = createIrisPipeline(nodeManager, nodeCleaner, traversal, frexSupplier);
        // }
        if (pipeline == null) {
            pipeline = new NormalRenderPipeline(nodeManager, nodeCleaner, traversal, frexSupplier);
        }
        return pipeline;
    }

    private static AbstractRenderPipeline createIrisPipeline(AsyncNodeManager nodeManager, NodeCleaner nodeCleaner, HierarchicalOcclusionTraverser traversal, BooleanSupplier frexSupplier) {
        // Stubbed out - Iris integration disabled for NeoForge port
        return null;
        /*
        var irisPipe = Iris.getPipelineManager().getPipelineNullable();
        if (irisPipe == null) {
            return null;
        }
        if (irisPipe instanceof IGetIrisNeoVoxyPipelineData getNeoVoxyPipeData) {
            var pipeData = getNeoVoxyPipeData.neovoxy$getPipelineData();
            if (pipeData == null) {
                return null;
            }
            Logger.info("Creating neovoxy iris render pipeline");
            try {
                return new IrisNeoVoxyRenderPipeline(pipeData, nodeManager, nodeCleaner, traversal, frexSupplier);
            } catch (Exception e) {
                Logger.error("Failed to create iris render pipeline", e);
                IrisUtil.disableIrisShaders();
                return null;
            }
        }
        return null;
        */
    }
}
