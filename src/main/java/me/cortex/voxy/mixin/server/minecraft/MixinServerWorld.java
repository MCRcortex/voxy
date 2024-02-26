package me.cortex.voxy.mixin.server.minecraft;

import me.cortex.voxy.server.world.IVoxyWorldGetterSetter;
import me.cortex.voxy.server.world.VoxyWorld;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerWorld.class)
public class MixinServerWorld implements IVoxyWorldGetterSetter {
    @Unique private VoxyWorld voxyWorld;

    @Override
    public void setVoxyWorld(VoxyWorld world) {
        if (world != null && this.voxyWorld != null) {
            throw new IllegalStateException("Voxy world not null");
        }
        this.voxyWorld = world;
    }

    @Override
    public VoxyWorld getVoxyWorld() {
        return this.voxyWorld;
    }
}
