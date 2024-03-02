package me.cortex.voxy.client.mixin.iris;

import com.llamalad7.mixinextras.lib.apache.commons.ArrayUtils;
import me.cortex.voxy.client.compat.iris.General;
import net.coderbot.iris.shaderpack.loading.ProgramGroup;
import net.coderbot.iris.shaderpack.loading.ProgramId;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = ProgramId.class, remap = false)
public abstract class MixinProgramId {
    @SuppressWarnings("target")
    @Shadow(remap = false)
    @Final
    @Mutable
    private static ProgramId[] $VALUES;

    static {
        int baseOrdinal = $VALUES.length;
        General.TERRAIN_PROGRAM_ID = ProgramIdAccessor.createProgramId("VoxyTerrain", baseOrdinal, General.PROGRAM_GROUP, "terrain");
        $VALUES = ArrayUtils.addAll($VALUES, General.TERRAIN_PROGRAM_ID);
    }
}
