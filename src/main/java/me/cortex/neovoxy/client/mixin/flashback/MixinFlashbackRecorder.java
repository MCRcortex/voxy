package me.cortex.neovoxy.client.mixin.flashback;

import com.moulberry.flashback.record.FlashbackMeta;
import com.moulberry.flashback.record.Recorder;
import me.cortex.neovoxy.client.NeoVoxyClientInstance;
import me.cortex.neovoxy.client.compat.IFlashbackMeta;
import me.cortex.neovoxy.commonImpl.NeoVoxyCommon;
import net.minecraft.core.RegistryAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Recorder.class, remap = false)
public class MixinFlashbackRecorder {
    @Shadow @Final private FlashbackMeta metadata;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void neovoxy$getStoragePath(RegistryAccess registryAccess, CallbackInfo retInf) {
        if (NeoVoxyCommon.isAvailable()) {
            var instance = NeoVoxyCommon.getInstance();
            if (instance instanceof NeoVoxyClientInstance ci) {
                ((IFlashbackMeta)this.metadata).setNeoVoxyPath(ci.getStorageBasePath().toFile());
            }
        }
    }
}
