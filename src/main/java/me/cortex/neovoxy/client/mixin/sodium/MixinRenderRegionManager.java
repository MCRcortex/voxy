package me.cortex.neovoxy.client.mixin.sodium;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cortex.neovoxy.client.config.NeoVoxyConfig;
import me.cortex.neovoxy.client.core.IGetNeoVoxyRenderSystem;
import me.cortex.neovoxy.commonImpl.NeoVoxyCommon;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderRegionManager.class, remap = false)
public class MixinRenderRegionManager {
    @Redirect(method = "uploadResults(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegion;Ljava/util/Collection;)V", at = @At(value = "INVOKE", target = "Ljava/lang/Math;toIntExact(J)I"), remap = false)
    private int neovoxy$cancelFade(long time) {
        var vrs = ((IGetNeoVoxyRenderSystem)(Minecraft.getInstance().levelRenderer)).getNeoVoxyRenderSystem();
        if (vrs!=null) {
            return -2;
        } else {
            return Math.toIntExact(time);
        }
    }
}