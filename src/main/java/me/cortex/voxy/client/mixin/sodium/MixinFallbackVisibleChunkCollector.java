package me.cortex.voxy.client.mixin.sodium;

import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.util.IrisUtil;
import net.caffeinemc.mods.sodium.client.render.chunk.LocalSectionIndex;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionFlags;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.FallbackVisibleChunkCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.storage.SectionStorage;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = FallbackVisibleChunkCollector.class, remap = false)
public class MixinFallbackVisibleChunkCollector {
    /*
    @Inject(method = "<init>", at = @At("HEAD"))
    private static void voxy$injectVisibleStreamReset(CallbackInfo ci) {
        var vrs = IVoxyRenderSystemHolder.getEngineNullable();
        if (vrs != null) {
            vrs.visbleSectionStream.reset();
        }
    }*/

    //Use redirect for performance
    @Redirect(method = "visit", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/storage/SectionStorage;getCurrent(III)Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;"), remap = false)
    private RenderSection voxy$injectVisibleSectionGather(SectionStorage instance, int x, int y, int z) {
        var section = instance.getCurrent(x,y,z);
        VoxyRenderSystem vrs;
        if (!IrisUtil.irisShadowActive() && (vrs = IVoxyRenderSystemHolder.getNullable()) != null && voxy$shouldUseForChunkBound(section, LocalSectionIndex.pack(x, y, z))) {
            if (vrs.visbleSectionStream != null)
                vrs.visbleSectionStream.put(SectionPos.asLong(x,y,z));
        }
        return section;
    }

    @Unique
    private static boolean voxy$shouldUseForChunkBound(RenderSection section, int localIndex) {
        if (section == null) return false;
        return (section.getRegion().getSectionFlags(localIndex)&RenderSectionFlags.MASK_IS_BUILT)!=0;
    }
}
