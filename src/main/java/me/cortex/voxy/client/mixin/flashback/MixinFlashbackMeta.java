package me.cortex.voxy.client.mixin.flashback;

import me.cortex.voxy.client.compat.IFlashbackMeta;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.io.File;

// TODO: re-enable when flashback updates for 26.2
@Mixin(targets = "com.moulberry.flashback.record.FlashbackMeta", remap = false)
public class MixinFlashbackMeta implements IFlashbackMeta {
    @Unique private File voxyPath;

    @Override
    public void setVoxyPath(File path) { this.voxyPath = path; }

    @Override
    public File getVoxyPath() { return this.voxyPath; }
}
