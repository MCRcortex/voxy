package me.cortex.voxy.mixin.server.minecraft;

import me.cortex.voxy.server.world.IVoxyWorldGetterSetter;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ThreadedAnvilChunkStorage.class)
public class MixinThreadedAnvilChunkStorage {
    @Shadow @Final ServerWorld world;

    @Inject(method = "save(Lnet/minecraft/world/chunk/Chunk;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/Chunk;setNeedsSaving(Z)V", shift = At.Shift.AFTER))
    private void injectVoxyIngest(Chunk chunk, CallbackInfoReturnable<Boolean> cir) {
        var voxy = ((IVoxyWorldGetterSetter)world).getVoxyWorld();
        if (voxy != null) {
            voxy.enqueueIngest((WorldChunk) chunk);
        }
    }
}
