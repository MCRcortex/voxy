package me.cortex.voxy.client.mixin.sodium;

import me.cortex.voxy.client.config.VoxyConfigScreenPages;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI;
import net.caffeinemc.mods.sodium.client.gui.options.OptionPage;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(SodiumOptionsGUI.class)
public class MixinSodiumOptionsGUI {
    @Shadow(remap = false) @Final private List<OptionPage> pages;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void voxy$addConfigPage(Screen prevScreen, CallbackInfo ci) {
        if (VoxyCommon.isAvailable()) {
            this.pages.add(VoxyConfigScreenPages.voxyOptionPage = VoxyConfigScreenPages.page());
        }
    }
}
