package me.cortex.voxy.commonImpl.mixin.minecraft;

import me.cortex.voxy.commonImpl.IWorldGetIdentifier;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.MutableWorldProperties;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public class MixinWorld implements IWorldGetIdentifier {
    @Unique
    private WorldIdentifier identifier;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void voxy$injectIdentifier(MutableWorldProperties properties,
                                       RegistryKey<World> key,
                                       DynamicRegistryManager registryManager,
                                       RegistryEntry<DimensionType> dimensionEntry,
                                       boolean isClient,
                                       boolean debugWorld,
                                       long seed,
                                       int maxChainedNeighborUpdates,
                                       CallbackInfo ci) {
        this.identifier = new WorldIdentifier(key, seed, dimensionEntry.getKey().orElse(null));
    }

    @Override
    public WorldIdentifier voxy$getIdentifier() {
        return this.identifier;
    }
}
