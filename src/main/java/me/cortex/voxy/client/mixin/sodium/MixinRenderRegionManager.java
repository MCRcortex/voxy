package me.cortex.voxy.client.mixin.sodium;

import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = RenderRegionManager.class, remap = false)
public class MixinRenderRegionManager {
    @Redirect(method = "uploadResults(Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegion;Ljava/util/Collection;Lnet/caffeinemc/mods/sodium/client/render/chunk/UniformBufferManager;)V",
              at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;consumeFade()Z"), remap = false)
    private boolean voxy$cancelFade(RenderSection instance) {
        var vrs = ((IGetVoxyRenderSystem)(Minecraft.getInstance().levelRenderer)).voxy$getRenderSystem();
        if (vrs != null) {
            return false;
        } else {
            return instance.consumeFade();
        }
    }
}
