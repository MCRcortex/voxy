//package me.cortex.voxy.client.mixin.sodium;
//
//import me.cortex.voxy.client.config.IConfigPageSetter;
//import net.caffeinemc.mods.sodium.client.config.structure.OptionPage;
//import net.caffeinemc.mods.sodium.client.config.structure.Page;
//import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.Shadow;
//import org.spongepowered.asm.mixin.Unique;
//import org.spongepowered.asm.mixin.injection.At;
//import org.spongepowered.asm.mixin.injection.Inject;
//import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//
//@Mixin(value = VideoSettingsScreen.class, remap = false)
//public abstract class MixinVideoSettingsScreen implements IConfigPageSetter {
//    @Shadow public abstract void jumpToPage(Page page);
//
//    @Shadow protected abstract void onSectionFocused(Page page);
//
//    @Unique
//    private OptionPage voxyJumpPage;
//
//    public void voxy$setPageJump(OptionPage page) {
//        this.voxyJumpPage = page;
//    }
//
//    @Inject(method = "rebuild", at = @At("TAIL"))
//    private void voxy$jumpPages(CallbackInfo ci) {
//        if (this.voxyJumpPage != null) {
//            this.jumpToPage(this.voxyJumpPage);
//            this.onSectionFocused(this.voxyJumpPage);
//        }
//    }
//}
