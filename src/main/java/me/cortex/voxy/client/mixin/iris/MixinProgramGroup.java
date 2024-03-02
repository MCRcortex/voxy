package me.cortex.voxy.client.mixin.iris;

import com.llamalad7.mixinextras.lib.apache.commons.ArrayUtils;
import me.cortex.voxy.client.compat.iris.General;
import net.coderbot.iris.shaderpack.loading.ProgramGroup;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = ProgramGroup.class, remap = false)
public abstract class MixinProgramGroup {
    @SuppressWarnings("target")
    @Shadow(remap = false)
    @Final
    @Mutable
    private static ProgramGroup[] $VALUES;

    static {
        int baseOrdinal = $VALUES.length;
        General.PROGRAM_GROUP = ProgramGroupAccessor.createProgramGroup("voxy", baseOrdinal, "voxy");
        $VALUES = ArrayUtils.addAll($VALUES, General.PROGRAM_GROUP);
    }
}
