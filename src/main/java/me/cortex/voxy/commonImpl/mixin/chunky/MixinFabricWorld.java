package me.cortex.voxy.commonImpl.mixin.chunky;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.popcraft.chunky.mixin.ServerChunkCacheMixin;
import org.popcraft.chunky.platform.FabricWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Mixin(FabricWorld.class)
public class MixinFabricWorld {
    @WrapOperation(method = "getChunkAtAsync", at = @At(value = "INVOKE", target = "Lorg/popcraft/chunky/mixin/ServerChunkCacheMixin;invokeGetChunkFutureMainThread(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<ChunkResult<ChunkAccess>> captureGeneratedChunk(ServerChunkCacheMixin instance, int i, int j, ChunkStatus chunkStatus, boolean b, Operation<CompletableFuture<ChunkResult<ChunkAccess>>> original) {
        var future = original.call(instance, i, j, chunkStatus, b);
        //TODO: do a sweep of chunks around players that just joined, else they'll see no chunks even if they were pregenerated before
        return future.thenApply(res -> {
            res.ifSuccess(chunk -> {
                if (chunk instanceof LevelChunk worldChunk) {
                    if(VoxyCommon.IS_DEDICATED_SERVER) {
                        var level = worldChunk.getLevel();
                        var id = level.dimension().identifier();
                        var server = Objects.requireNonNull(level.getServer());
                        var packet = new ClientboundLevelChunkWithLightPacket(worldChunk, level.getLightEngine(), null, null);
                        for(var levels : server.getAllLevels()) {
                            if (levels.dimension().identifier().equals(id)) {
                                for(var player : levels.players()) {
                                    player.connection.send(packet);
                                }
                                break;
                            }
                        }
                    }
                    else {
                        VoxelIngestService.tryAutoIngestChunk(worldChunk);
                    }
                }
            });
            return res;
        });
    }
}
