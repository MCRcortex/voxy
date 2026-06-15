package me.cortex.neovoxy.client.mixin.minecraft;

import com.mojang.blaze3d.platform.DisplayData;
import com.mojang.blaze3d.platform.ScreenManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.WindowEventHandler;
import me.cortex.neovoxy.client.GPUSelectorWindows2;
import me.cortex.neovoxy.common.util.ThreadUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class MixinWindow {
    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;setBootErrorCallback()V"))
    private void injectInitWindow(WindowEventHandler eventHandler, ScreenManager monitorTracker, DisplayData settings, String fullscreenVideoMode, String title, CallbackInfo ci) {
        //System.load("C:\\Program Files\\RenderDoc\\renderdoc.dll");
        var prop = System.getProperty("neovoxy.forceGpuSelectionIndex", "NO");
        if (!prop.equals("NO")) {
            GPUSelectorWindows2.doSelector(Integer.parseInt(prop));
        }

        //Force the current thread priority to be realtime
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        ThreadUtils.SetSelfThreadPriorityWin32(ThreadUtils.WIN32_THREAD_PRIORITY_TIME_CRITICAL);
    }
}
