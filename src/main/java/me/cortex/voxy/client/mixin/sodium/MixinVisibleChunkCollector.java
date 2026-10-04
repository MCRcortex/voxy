package me.cortex.voxy.client.mixin.sodium;

import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.util.IrisUtil;
import net.caffeinemc.mods.sodium.client.render.chunk.LocalSectionIndex;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionFlags;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.VisibleChunkCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = VisibleChunkCollector.class, remap = false)
public class MixinVisibleChunkCollector {
    /*
    @Inject(method = "<init>", at = @At("HEAD"))
    private static void voxy$injectVisibleStreamReset(CallbackInfo ci) {
        var vrs = IVoxyRenderSystemHolder.getEngineNullable();
        if (vrs != null) {
            vrs.visbleSectionStream.reset();
        }
    }*/

    //Use redirect for performance
    @Redirect(method = "visit", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegionManager;getForChunk(III)Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegion;"), remap = false)
    private RenderRegion voxy$injectVisibleSectionGather(RenderRegionManager instance, int x, int y, int z) {
        var region = instance.getForChunk(x,y,z);
        VoxyRenderSystem vrs;
        if (!IrisUtil.irisShadowActive() && (vrs = IVoxyRenderSystemHolder.getNullable()) != null && vrs.visbleSectionStream != null && voxy$shouldUseForChunkBound(region, LocalSectionIndex.pack(x, y, z))) {
            vrs.visbleSectionStream.put(SectionPos.asLong(x,y,z));
        }
        return region;
    }

    @Unique
    private static boolean voxy$shouldUseForChunkBound(RenderRegion region, int localIndex) {
        if (region == null) return false;
        return (region.getSectionFlags(localIndex)&RenderSectionFlags.MASK_IS_BUILT)!=0;
    }
}
