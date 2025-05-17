package me.cortex.voxy.client.mixin.sodium;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.cortex.voxy.client.VoxyClientInstance;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionFlags;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderSectionManager.class, remap = false)
public class MixinRenderSectionManager {
    @Shadow @Final private ClientWorld level;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void voxy$resetChunkTracker(ClientWorld level, int renderDistance, CommandList commandList, CallbackInfo ci) {
        if (level.worldRenderer != null) {
            var system = ((IGetVoxyRenderSystem)(level.worldRenderer)).getVoxyRenderSystem();
            if (system != null) {
                system.chunkBoundRenderer.reset();
            }
        }
    }

    /*
    @Inject(method = "onChunkAdded", at = @At("HEAD"))
    private void voxy$trackChunkAdd(int x, int z, CallbackInfo ci) {
        if (this.level.worldRenderer != null) {
            var system = ((IGetVoxyRenderSystem)(this.level.worldRenderer)).getVoxyRenderSystem();
            if (system != null) {
                system.chunkBoundRenderer.addChunk(ChunkPos.toLong(x, z));
            }
        }
    }*/

    /*
    @Inject(method = "onChunkRemoved", at = @At("HEAD"))
    private void voxy$trackChunkRemove(int x, int z, CallbackInfo ci) {
        if (this.level.worldRenderer != null) {
            var system = ((IGetVoxyRenderSystem)(this.level.worldRenderer)).getVoxyRenderSystem();
            if (system != null) {
                system.chunkBoundRenderer.removeSection(ChunkPos.toLong(x, z));
            }
        }
    }*/

    @Inject(method = "onChunkRemoved", at = @At("HEAD"))
    private void injectIngest(int x, int z, CallbackInfo ci) {
        //TODO: Am not quite sure if this is right
        var instance = VoxyCommon.getInstance();
        if (instance != null && VoxyConfig.CONFIG.ingestEnabled) {
            var chunk = this.level.getChunk(x, z);
            var world = chunk.getWorld();
            if (world instanceof ClientWorld cw) {
                var engine = ((VoxyClientInstance)instance).getOrMakeRenderWorld(cw);
                if (engine != null) {
                    instance.getIngestService().enqueueIngest(engine, chunk);
                }
            }
        }
    }

    @Redirect(method = "updateSectionInfo", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;setInfo(Lnet/caffeinemc/mods/sodium/client/render/chunk/data/BuiltSectionInfo;)Z"))
    private boolean voxy$updateOnUpload(RenderSection instance, BuiltSectionInfo info) {
        boolean wasBuilt = instance.isBuilt();
        int flags = instance.getFlags();
        if (!instance.setInfo(info)) {
            return false;
        }
        if (wasBuilt == instance.isBuilt()) {//Only want to do stuff on change
            return true;
        }
        flags |= instance.getFlags();
        if (flags == 0)//Only process things with stuff
            return true;

        VoxyRenderSystem system = ((IGetVoxyRenderSystem)(this.level.worldRenderer)).getVoxyRenderSystem();
        if (system == null) {
            return true;
        }
        long pos = ChunkSectionPos.asLong(instance.getChunkX(), instance.getChunkY(), instance.getChunkZ());
        if (wasBuilt) {//Remove
            system.chunkBoundRenderer.removeSection(pos);
        } else {//Add
            system.chunkBoundRenderer.addSection(pos);
        }
        return true;
    }

    /*
    @ModifyReturnValue(method = "getSearchDistance", at = @At("RETURN"))
    private float voxy$increaseSearchDistanceFix(float searchDistance) {
        if (((IGetVoxyRenderSystem)(this.level.worldRenderer)).getVoxyRenderSystem() == null) {
            return searchDistance;
        }
        return searchDistance + 32;
    }*/
}
