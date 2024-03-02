package me.cortex.voxy.client.mixin.iris;

import net.coderbot.iris.shaderpack.loading.ProgramGroup;
import net.coderbot.iris.shaderpack.loading.ProgramId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ProgramId.class)
public interface ProgramIdAccessor {
    @Invoker(value = "<init>")
    static ProgramId createProgramId(String ename, int ordinal, ProgramGroup group, String name) {
        throw new AssertionError();
    }
}
