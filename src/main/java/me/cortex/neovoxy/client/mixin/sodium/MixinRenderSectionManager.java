package me.cortex.neovoxy.client.mixin.sodium;

import me.cortex.neovoxy.client.core.IGetNeoVoxyRenderSystem;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderSectionManager.class, remap = false)
public class MixinRenderSectionManager {
    @Shadow
    @Final
    private ClientLevel level;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void neovoxy$resetChunkBounds(ClientLevel level, int renderDistance, SortBehavior sortBehavior,
                                          CommandList commandList, CallbackInfo ci) {
        if (level.levelRenderer != null) {
            var system = ((IGetNeoVoxyRenderSystem) level.levelRenderer).getNeoVoxyRenderSystem();
            if (system != null) {
                system.chunkBoundRenderer.reset();
            }
        }
    }

    @Redirect(
            method = "updateSectionInfo",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;setInfo(Lnet/caffeinemc/mods/sodium/client/render/chunk/data/BuiltSectionInfo;)Z"
            )
    )
    private boolean neovoxy$trackBuiltSections(RenderSection section, BuiltSectionInfo info) {
        boolean hadGeometry = section.getFlags() != 0;
        int previousFlags = section.getFlags();

        if (!section.setInfo(info)) {
            return false;
        }

        boolean hasGeometry = section.getFlags() != 0;
        if (hadGeometry == hasGeometry || (previousFlags | section.getFlags()) == 0) {
            return true;
        }

        var levelRenderer = this.level.levelRenderer;
        if (levelRenderer == null) {
            return true;
        }

        var system = ((IGetNeoVoxyRenderSystem) levelRenderer).getNeoVoxyRenderSystem();
        if (system == null) {
            return true;
        }

        long position = SectionPos.asLong(section.getChunkX(), section.getChunkY(), section.getChunkZ());
        if (hadGeometry) {
            system.chunkBoundRenderer.removeSection(position);
        } else {
            system.chunkBoundRenderer.addSection(position);
        }

        return true;
    }
}
