package me.cortex.neovoxy.client.mixin.sodium;

import me.cortex.neovoxy.client.compat.SemaphoreBlockImpersonator;
import me.cortex.neovoxy.client.config.NeoVoxyConfig;
import me.cortex.neovoxy.common.thread.MultiThreadPrioritySemaphore;
import me.cortex.neovoxy.commonImpl.NeoVoxyCommon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.Semaphore;

@Mixin(targets={"net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobQueue"},remap = false)
public class MixinChunkJobQueue {
    @Unique private MultiThreadPrioritySemaphore.Block neovoxy$semaphoreBlock;

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "(I)Ljava/util/concurrent/Semaphore;"))
    private Semaphore neovoxy$injectUnifiedPool(int permits) {
        var instance = NeoVoxyCommon.getInstance();
        if (instance != null && !NeoVoxyConfig.CONFIG.dontUseSodiumBuilderThreads) {
            this.neovoxy$semaphoreBlock = instance.getThreadPool().groupSemaphore.createBlock();
            return new SemaphoreBlockImpersonator(this.neovoxy$semaphoreBlock);
        }
        return new Semaphore(permits);
    }

    @Inject(method = "shutdown", at = @At("RETURN"))
    private void neovoxy$injectAtShutdown(CallbackInfoReturnable ci) {
        if (this.neovoxy$semaphoreBlock != null) {
            this.neovoxy$semaphoreBlock.free();
        }
    }
}
