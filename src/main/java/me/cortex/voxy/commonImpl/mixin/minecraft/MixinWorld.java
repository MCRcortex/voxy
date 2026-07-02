package me.cortex.voxy.commonImpl.mixin.minecraft;

import me.cortex.voxy.commonImpl.IWorldGetIdentifier;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(Level.class)
public class MixinWorld implements IWorldGetIdentifier {
    @Unique
    private WorldIdentifier identifier;

    // MC 1.21.1 Level constructor signature:
    // protected Level(WritableLevelData, ResourceKey<Level>, RegistryAccess, Holder<DimensionType>,
    //                 Supplier<ProfilerFiller>, boolean, boolean, long, int)
    @Inject(method = "<init>", at = @At("RETURN"))
    private void voxy$injectIdentifier(WritableLevelData levelData,
                                       ResourceKey<Level> dimension,
                                       RegistryAccess registryAccess,
                                       Holder<DimensionType> dimensionTypeRegistration,
                                       Supplier<ProfilerFiller> profiler,
                                       boolean isClientSide,
                                       boolean isDebug,
                                       long biomeZoomSeed,
                                       int maxChainedNeighborUpdates,
                                       CallbackInfo ci) {
        if (dimension != null) {
            this.identifier = new WorldIdentifier(dimension, biomeZoomSeed, dimensionTypeRegistration == null ? null : dimensionTypeRegistration.unwrapKey().orElse(null));
        } else {
            this.identifier = null;
        }
    }

    @Override
    public WorldIdentifier voxy$getIdentifier() {
        return this.identifier;
    }
}
