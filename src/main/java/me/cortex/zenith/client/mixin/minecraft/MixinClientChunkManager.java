package me.cortex.zenith.client.mixin.minecraft;

import me.cortex.zenith.client.IGetVoxelCore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(ClientChunkManager.class)
public class MixinClientChunkManager {
    @Inject(require = 0, method = "unload", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientChunkManager$ClientChunkMap;compareAndSet(ILnet/minecraft/world/chunk/WorldChunk;Lnet/minecraft/world/chunk/WorldChunk;)Lnet/minecraft/world/chunk/WorldChunk;", shift = At.Shift.BEFORE), locals = LocalCapture.CAPTURE_FAILHARD)
    private void injectUnload(ChunkPos pos, CallbackInfo ci, int index, WorldChunk worldChunk) {
        var core = ((IGetVoxelCore)MinecraftClient.getInstance().worldRenderer).getVoxelCore();
        if (core != null) {
            core.enqueueIngest(worldChunk);
        }
    }
}
