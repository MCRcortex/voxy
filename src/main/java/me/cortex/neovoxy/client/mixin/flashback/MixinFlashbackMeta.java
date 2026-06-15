package me.cortex.neovoxy.client.mixin.flashback;

import com.google.gson.JsonObject;
import com.moulberry.flashback.record.FlashbackMeta;
import me.cortex.neovoxy.client.compat.IFlashbackMeta;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;

@Mixin(value = FlashbackMeta.class, remap = false)
public class MixinFlashbackMeta implements IFlashbackMeta {
    @Unique private File neovoxyPath;

    @Override
    public void setNeoVoxyPath(File path) {
        this.neovoxyPath = path;
    }

    @Override
    public File getNeoVoxyPath() {
        return this.neovoxyPath;
    }

    @Inject(method = "toJson", at = @At("RETURN"))
    private void neovoxy$injectSaveNeoVoxyPath(CallbackInfoReturnable<JsonObject> cir) {
        var val = cir.getReturnValue();
        if (val != null && this.neovoxyPath != null) {
            val.addProperty("neovoxy_storage_path", this.neovoxyPath.getAbsoluteFile().getPath());
        }
    }

    @Inject(method = "fromJson", at = @At("RETURN"))
    private static void neovoxy$injectGetNeoVoxyPath(JsonObject meta, CallbackInfoReturnable<FlashbackMeta> cir) {
        var val = cir.getReturnValue();
        if (val != null && meta != null) {
            if (meta.has("neovoxy_storage_path")) {
                ((IFlashbackMeta)val).setNeoVoxyPath(new File(meta.get("neovoxy_storage_path").getAsString()));
            }
        }
    }
}
