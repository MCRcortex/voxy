package me.cortex.voxy.client.mixin.sodium;

import me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SodiumOptionsGUI.class)
public interface AccessorSodiumOptionsGUI {
    @Invoker(value = "<init>")
    static SodiumOptionsGUI newScreen(Screen currentScreen) {
        throw new AssertionError(); // Used for Embeddium support
    }
}
