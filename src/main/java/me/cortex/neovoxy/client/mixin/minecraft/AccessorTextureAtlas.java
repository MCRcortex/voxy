package me.cortex.neovoxy.client.mixin.minecraft;

import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin to expose the private mipLevel field in TextureAtlas.
 * Required for NeoVoxy to match texture sampler LOD settings with the block atlas.
 */
@Mixin(TextureAtlas.class)
public interface AccessorTextureAtlas {
    @Accessor("mipLevel")
    int neovoxy$getMipLevel();
}
