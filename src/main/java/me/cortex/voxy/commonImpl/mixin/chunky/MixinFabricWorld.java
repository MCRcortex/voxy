package me.cortex.voxy.commonImpl.mixin.chunky;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Either;

import me.cortex.voxy.common.world.service.VoxelIngestService;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkHolder.ChunkLoadingFailure;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.popcraft.chunky.platform.FabricWorld;

import java.util.concurrent.CompletableFuture;

@Mixin(FabricWorld.class)
public class MixinFabricWorld {

    @WrapOperation(
        method = "getChunkAtAsync",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkHolder;getOrScheduleFuture(Lnet/minecraft/world/level/chunk/ChunkStatus;Lnet/minecraft/server/level/ChunkMap;)Ljava/util/concurrent/CompletableFuture;"
        )
    )
    private CompletableFuture<?> wrapGetOrScheduleFuture(
        ChunkHolder holder,
        ChunkStatus status,
        ChunkMap storage,
        Operation<CompletableFuture<Either<ChunkAccess, ChunkLoadingFailure>>> original
    ) {
        CompletableFuture<Either<ChunkAccess, ChunkLoadingFailure>> future = original.call(holder, status, storage);

        return future.thenApply(res -> {
            res.ifLeft(chunk -> {
                if (chunk instanceof LevelChunk worldChunk) {
                    VoxelIngestService.tryAutoIngestChunk(worldChunk);
                }
            });
            return res;
        });
    }
}
