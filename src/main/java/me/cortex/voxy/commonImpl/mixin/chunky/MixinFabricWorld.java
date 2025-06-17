package me.cortex.voxy.commonImpl.mixin.chunky;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.server.world.OptionalChunk;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.popcraft.chunky.mixin.ServerChunkCacheMixin;
import org.popcraft.chunky.platform.FabricWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;

@Mixin(FabricWorld.class)
public class MixinFabricWorld {
    @WrapOperation(method = "getChunkAtAsync", at = @At(value = "INVOKE", target = "Lorg/popcraft/chunky/mixin/ServerChunkCacheMixin;invokeGetChunkFutureMainThread(IILnet/minecraft/world/chunk/ChunkStatus;Z)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<OptionalChunk<Chunk>> captureGeneratedChunk(ServerChunkCacheMixin instance, int i, int j, ChunkStatus chunkStatus, boolean b, Operation<CompletableFuture<OptionalChunk<Chunk>>> original) {
        var future = original.call(instance, i, j, chunkStatus, b);
        if (false) {//TODO: ADD SERVER CONFIG THING
            return future;
        } else {
            return future.thenApply(res -> {
                res.ifPresent(chunk -> {
                    if (chunk instanceof WorldChunk worldChunk) {
                        VoxelIngestService.tryAutoIngestChunk(worldChunk);
                    }
                });
                return res;
            });
        }
    }
}
