package me.cortex.voxy.commonImpl.mixin.distanthorizons;

import java.util.ArrayList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;

import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import net.minecraft.world.level.chunk.ChunkAccess;
// import net.minecraft.util.math.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;

@Mixin(value = SharedApi.class, remap = false)
public class MixinSharedApi {
    @Inject(method = "queueChunkUpdate", at = @At(
        value = "NEW",
        target = "Lcom/seibel/distanthorizons/core/api/internal/chunkUpdating/ChunkUpdateData;"),
//        cancellable = true,
        remap = false
    )
    private static void beforeChunkUpdateCreation(
            IChunkWrapper chunkWrapper,
            IDhLevel dhLevel,
            CallbackInfo ci
    ) {
        ChunkAccess chunkAccess;
        if (chunkWrapper instanceof loaderCommon.fabric.com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper cw) {
            chunkAccess = cw.getChunk();
        } else if (chunkWrapper instanceof loaderCommon.forge.com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper cw) {
            chunkAccess = cw.getChunk();
        } else {
            Logger.error("DH MixinSharedApi: Unknown chunk wrapper class: " + chunkWrapper.getClass().getName());
            return;
        }

        if (!(chunkAccess instanceof LevelChunk wc)) {
            Logger.error("DH MixinSharedApi: ChunkAccess is not LevelChunk: " + chunkAccess.getClass().getName());
            return;
        }

        // for (int x = 0; x < 16; x++) {
        //     System.out.println(wc.getBlockState(new BlockPos(x, 0, 0)).toString());
        // }

        if (VoxelIngestService.tryAutoIngestChunk(wc)) {
            // Logger.info("DH MixinSharedApi: Auto-ingest triggered for chunk: " + chunkWrapper.getChunkPos());
        } else {
            // Logger.info("DH MixinSharedApi: Auto-ingest NOT triggered for chunk: " + chunkWrapper.getChunkPos());
        }
        // ci.cancel();
    }
}
