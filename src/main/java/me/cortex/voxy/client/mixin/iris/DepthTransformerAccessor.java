package me.cortex.voxy.client.mixin.iris;

import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import net.irisshaders.iris.pipeline.transform.transformer.DepthTransformer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

//Only exists on Iris 1.11.4+ (26.2), guarded by IrisUtil.IRIS_REVERSE_Z
@Mixin(value = DepthTransformer.class, remap = false)
public interface DepthTransformerAccessor {
    @Invoker("invertDepthReads")
    static void voxy$invertDepthReads(ASTParser t, TranslationUnit tree, Root root) {}
}
