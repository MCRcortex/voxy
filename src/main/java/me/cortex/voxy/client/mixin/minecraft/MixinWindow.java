package me.cortex.voxy.client.mixin.minecraft;

import me.cortex.voxy.client.GPUSelectorWindows2;
import me.cortex.voxy.common.util.ThreadUtils;
import net.minecraft.client.WindowEventHandler;
import net.minecraft.client.WindowSettings;
import net.minecraft.client.util.MonitorTracker;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class MixinWindow {
    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/Window;throwOnGlError()V"))
    private void injectInitWindow(WindowEventHandler eventHandler, MonitorTracker monitorTracker, WindowSettings settings, String fullscreenVideoMode, String title, CallbackInfo ci) {
        //System.load("C:\\Program Files\\RenderDoc\\renderdoc.dll");
        var prop = System.getProperty("voxy.forceGpuSelectionIndex", "NO");
        if (!prop.equals("NO")) {
            GPUSelectorWindows2.doSelector(Integer.parseInt(prop));
        }

        //Force the current thread priority to be realtime
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        ThreadUtils.SetSelfThreadPriorityWin32(ThreadUtils.WIN32_THREAD_PRIORITY_TIME_CRITICAL);
    }
}
