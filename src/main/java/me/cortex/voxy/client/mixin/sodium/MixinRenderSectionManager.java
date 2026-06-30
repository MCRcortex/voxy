package me.cortex.voxy.client.mixin.sodium;

import me.cortex.voxy.client.ICheekyClientChunkCache;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderSectionManager.class, remap = false)
public class MixinRenderSectionManager {
    @Unique
    private static final boolean BOBBY_INSTALLED = FabricLoader.getInstance().isModLoaded("bobby");

    @Shadow @Final private ClientLevel level;

    @Shadow @Final private ChunkBuilder builder;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void voxy$resetChunkTracker(ClientLevel level, int renderDistance, SortBehavior sortBehavior, CallbackInfo ci) {
        var levelRenderer = Minecraft.getInstance().levelRenderer;
        if (levelRenderer != null) {
            var system = ((IGetVoxyRenderSystem)(levelRenderer)).voxy$getRenderSystem();
            if (system != null) {
                system.chunkBoundRenderer.reset();
            }
        }
        this.bottomSectionY = this.level.getMinY()>>4;
    }

    @Inject(method = "onChunkRemoved", at = @At("HEAD"))
    private void voxy$injectIngest(int x, int z, CallbackInfo ci) {
        //TODO: Am not quite sure if this is right
        if (VoxyConfig.CONFIG.ingestEnabled && !BOBBY_INSTALLED) {
            var cccm = (ICheekyClientChunkCache)this.level.getChunkSource();
            if (cccm != null) {
                var chunk = cccm.voxy$cheekyGetChunk(x, z);
                if (chunk != null) {
                    VoxelIngestService.tryAutoIngestChunk(chunk);
                }
            }
        }
    }


    @Inject(method = "onChunkAdded", at = @At("HEAD"))
    private void voxy$ingestOnAdd(int x, int z, CallbackInfo ci) {
        if (Minecraft.getInstance().levelRenderer != null && VoxyConfig.CONFIG.ingestEnabled) {
            var cccm = this.level.getChunkSource();
            if (cccm != null) {
                var chunk = cccm.getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk != null) {
                    VoxelIngestService.tryAutoIngestChunk(chunk);
                }
            }
        }
    }

    @Unique private long cachedChunkPos = -1;
    @Unique private int cachedChunkStatus;
    @Unique private int bottomSectionY;

    @Redirect(method = "updateSectionInfo", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;setInfo(Lnet/caffeinemc/mods/sodium/client/render/chunk/data/BuiltSectionInfo;)I"))
    private int voxy$updateOnUpload(RenderSection instance, BuiltSectionInfo info) {
        //needsRender() reflects the HAS_BLOCK_GEOMETRY/HAS_BLOCK_ENTITIES/HAS_ANIMATED_SPRITES bits only,
        // unlike isBuilt() which is also true for built-but-empty sections (MASK_IS_BUILT)
        boolean wasBuilt = instance.needsRender();
        int result = instance.setInfo(info);
        if (result == 0) {
            return 0;
        }
        boolean nowBuilt = instance.needsRender();
        if (wasBuilt == nowBuilt) {
            return result;
        }

        VoxyRenderSystem system = ((IGetVoxyRenderSystem)(Minecraft.getInstance().levelRenderer)).voxy$getRenderSystem();
        if (system == null) {
            return result;
        }
        int x = instance.getChunkX(), y = instance.getChunkY(), z = instance.getChunkZ();

        if (wasBuilt && VoxyConfig.CONFIG.ingestEnabled) {
            var tracker = ((AccessorChunkTracker)ChunkTrackerHolder.get(this.level)).getChunkStatus();
            long key = ChunkPos.pack(x, z);
            if (key != this.cachedChunkPos) {
                this.cachedChunkPos = key;
                this.cachedChunkStatus = tracker.getOrDefault(key, 0);
            }
            if (this.cachedChunkStatus == 3) {
                var cccm = this.level.getChunkSource();
                var chunk = cccm.getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk != null) {
                    var section = chunk.getSection(y - this.bottomSectionY);
                    var lp = this.level.getLightEngine();

                    var csp = SectionPos.of(x, y, z);
                    var blp = lp.getLayerListener(LightLayer.BLOCK).getDataLayerData(csp);
                    var slp = lp.getLayerListener(LightLayer.SKY).getDataLayerData(csp);

                    VoxelIngestService.rawIngest(system.getEngine(), section, x, y, z, blp == null ? null : blp.copy(), slp == null ? null : slp.copy());
                }
            }
        }

        if (VoxyCommon.IS_MINE_IN_ABYSS) {
            int sector = (x+512)>>10;
            x-=sector<<10;
            y+=16+(256-32-sector*30);
        }
        long pos = SectionPos.asLong(x,y,z);
        if (wasBuilt) {
            system.chunkBoundRenderer.removeSection(pos);
        } else {
            system.chunkBoundRenderer.addSection(pos);
        }
        return result;
    }
}
