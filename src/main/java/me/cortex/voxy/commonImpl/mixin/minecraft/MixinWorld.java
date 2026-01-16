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

import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Level.class)
public class MixinWorld implements IWorldGetIdentifier {
    @Unique
    private WorldIdentifier identifier;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void voxy$injectIdentifier(WritableLevelData properties,
                                       ResourceKey<Level> key,
                                       RegistryAccess registryManager,
                                       Holder<DimensionType> dimensionEntry,
                                       Supplier<ProfilerFiller> profiler,
                                       boolean isClient,
                                       boolean debugWorld,
                                       long seed,
                                       int maxChainedNeighborUpdates,
                                       CallbackInfo ci) {
        if (key != null) {
            this.identifier = new WorldIdentifier(key, seed, dimensionEntry == null?null:dimensionEntry.unwrapKey().orElse(null));
        } else {
            this.identifier = null;
        }
    }

    @Override
    public WorldIdentifier voxy$getIdentifier() {
        return this.identifier;
    }
}
