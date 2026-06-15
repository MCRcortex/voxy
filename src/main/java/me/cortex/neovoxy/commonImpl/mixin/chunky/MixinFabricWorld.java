package me.cortex.neovoxy.commonImpl.mixin.chunky;

// TODO: Re-enable Chunky integration when NeoForge 1.21.1 version available
// Disabled for NeoForge port - Chunky integration temporarily removed
/*
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cortex.neovoxy.common.world.service.VoxelIngestService;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.popcraft.chunky.mixin.ServerChunkCacheMixin;
import org.popcraft.chunky.platform.FabricWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;

@Mixin(FabricWorld.class)
public class MixinFabricWorld {
    @WrapOperation(method = "getChunkAtAsync", at = @At(value = "INVOKE", target = "Lorg/popcraft/chunky/mixin/ServerChunkCacheMixin;invokeGetChunkFutureMainThread(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<ChunkResult<ChunkAccess>> captureGeneratedChunk(ServerChunkCacheMixin instance, int i, int j, ChunkStatus chunkStatus, boolean b, Operation<CompletableFuture<ChunkResult<ChunkAccess>>> original) {
        var future = original.call(instance, i, j, chunkStatus, b);
        if (false) {//TODO: ADD SERVER CONFIG THING
            return future;
        } else {
            return future.thenApply(res -> {
                res.ifSuccess(chunk -> {
                    if (chunk instanceof LevelChunk worldChunk) {
                        VoxelIngestService.tryAutoIngestChunk(worldChunk);
                    }
                });
                return res;
            });
        }
    }
}
*/
