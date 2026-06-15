package me.cortex.neovoxy.client.mixin.minecraft;

import me.cortex.neovoxy.client.NeoVoxyClient;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevel {

    @Shadow @Final public LevelRenderer levelRenderer;

    @Shadow public abstract ClientChunkCache getChunkSource();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void neovoxy$getBottom(
            ClientPacketListener networkHandler,
            ClientLevel.ClientLevelData properties,
            ResourceKey<Level> registryRef,
            Holder<DimensionType> dimensionType,
            int loadDistance,
            int simulationDistance,
            Supplier<ProfilerFiller> profiler,
            LevelRenderer worldRenderer,
            boolean debugWorld,
            long seed,
            CallbackInfo cir) {
    }

    @Inject(method = "setBlocksDirty", at = @At("TAIL"))
    private void neovoxy$injectIngestOnStateChange(BlockPos pos, BlockState old, BlockState updated, CallbackInfo cir) {
        if (old == updated) return;

        var self = (Level)(Object)this;
        int sectionX = pos.getX() >> 4;
        int sectionY = pos.getY() >> 4;
        int sectionZ = pos.getZ() >> 4;
        NeoVoxyClient.queueSectionIngest(self, sectionX, sectionY, sectionZ);

        int localX = pos.getX() & 15;
        int localY = pos.getY() & 15;
        int localZ = pos.getZ() & 15;
        if (localX == 0) NeoVoxyClient.queueSectionIngest(self, sectionX - 1, sectionY, sectionZ);
        if (localX == 15) NeoVoxyClient.queueSectionIngest(self, sectionX + 1, sectionY, sectionZ);
        if (localY == 0) NeoVoxyClient.queueSectionIngest(self, sectionX, sectionY - 1, sectionZ);
        if (localY == 15) NeoVoxyClient.queueSectionIngest(self, sectionX, sectionY + 1, sectionZ);
        if (localZ == 0) NeoVoxyClient.queueSectionIngest(self, sectionX, sectionY, sectionZ - 1);
        if (localZ == 15) NeoVoxyClient.queueSectionIngest(self, sectionX, sectionY, sectionZ + 1);
    }
}
