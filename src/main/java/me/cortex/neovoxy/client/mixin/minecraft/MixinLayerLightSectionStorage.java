package me.cortex.neovoxy.client.mixin.minecraft;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LayerLightSectionStorage.class)
public class MixinLayerLightSectionStorage {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectMaps;synchronize(Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;)Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;"), remap = false)
    private static Long2ObjectMap<DataLayer> neovoxy$removeSynchronized(Long2ObjectMap<DataLayer> map) {
        return map;
    }
}
