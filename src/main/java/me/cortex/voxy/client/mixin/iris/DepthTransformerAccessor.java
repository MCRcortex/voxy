package me.cortex.voxy.client.mixin.iris;

import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import net.irisshaders.iris.pipeline.transform.transformer.DepthTransformer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Set;

@Mixin(value = DepthTransformer.class, remap = false)
public interface DepthTransformerAccessor {
    @Invoker("invertDepthReads")
    static void voxy$invertDepthReads(ASTParser t, TranslationUnit tree, Root root, Set<String> depthSamplers) {}
    @Accessor("DEPTH_SAMPLERS")
    static Set<String> voxy$getDepthSamplers(){return null;}
}
