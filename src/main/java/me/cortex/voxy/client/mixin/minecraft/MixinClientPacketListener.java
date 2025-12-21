package me.cortex.voxy.client.mixin.minecraft;

import me.cortex.voxy.client.VoxyClientInstance;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {
    @Shadow
    public abstract ClientLevel getLevel();

    @Inject(method = "handleLogin", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;commonPlayerSpawnInfo()Lnet/minecraft/network/protocol/game/CommonPlayerSpawnInfo;"))
    private void voxy$init(ClientboundLoginPacket packet, CallbackInfo ci) {
        if (VoxyCommon.isAvailable() && !VoxyClientInstance.isInGame) {
            VoxyClientInstance.isInGame = true;
            if (VoxyConfig.CONFIG.enabled) {
                if (VoxyCommon.getInstance() != null) {
                    VoxyCommon.shutdownInstance();
                }
                VoxyCommon.createInstance();
            }
        }
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;packetProcessor()Lnet/minecraft/network/PacketProcessor;"))
    private void voxy$handleServerChunk(ClientboundLevelChunkWithLightPacket clientboundLevelChunkWithLightPacket, CallbackInfo ci) {
        if (VoxyCommon.isAvailable() && VoxyClientInstance.isInGame) {
            if(VoxyConfig.CONFIG.enabled) {
                var level = getLevel();
                if(VoxyCommon.getInstance() != null && level != null) {
                    int x = clientboundLevelChunkWithLightPacket.getX();
                    int z = clientboundLevelChunkWithLightPacket.getZ();
                    CompletableFuture.supplyAsync(() -> {
                        var chunkData = clientboundLevelChunkWithLightPacket.getChunkData();
                        var worldChunk = new LevelChunk(level, new ChunkPos(x, z));
                        try {
                            worldChunk.replaceWithPacketData(chunkData.getReadBuffer(), chunkData.getHeightmaps(), chunkData.getBlockEntitiesTagsConsumer(x, z));
                        } catch (Exception e) {
                            System.out.println(e.getMessage());
                        }
                        return worldChunk;
                    }).thenAccept(chunk -> {
                        VoxelIngestService.tryAutoIngestChunk(chunk);
                    });
                }
            }
        }
    }
}
