package me.cortex.voxy.client.mixin.iris;

import net.coderbot.iris.shaderpack.loading.ProgramGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ProgramGroup.class)
public interface ProgramGroupAccessor {
    @Invoker(value="<init>")
    static ProgramGroup createProgramGroup(String ename, int ordinal, String baseName) {
        throw new AssertionError();
    }
}
