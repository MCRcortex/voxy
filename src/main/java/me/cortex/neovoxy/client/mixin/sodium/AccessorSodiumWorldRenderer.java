package me.cortex.neovoxy.client.mixin.sodium;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SodiumWorldRenderer.class, remap = false)
public interface AccessorSodiumWorldRenderer {
    @Accessor
    RenderSectionManager getRenderSectionManager();
}
