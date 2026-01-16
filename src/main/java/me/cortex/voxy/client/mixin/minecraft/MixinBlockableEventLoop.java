// package me.cortex.voxy.client.mixin.minecraft;

// import me.cortex.voxy.client.LoadException;
// import net.minecraft.util.thread.BlockableEventLoop;
// import org.spongepowered.asm.mixin.Mixin;
// import org.spongepowered.asm.mixin.Shadow;
// import org.spongepowered.asm.mixin.injection.At;
// import org.spongepowered.asm.mixin.injection.Redirect;

// @Mixin(BlockableEventLoop.class)
// public abstract class MixinBlockableEventLoop {

//     @Shadow public static boolean isNonRecoverable(Throwable throwable){return false;}

//     @Redirect(method = "doRunTask", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/thread/BlockableEventLoop;isNonRecoverable(Ljava/lang/Throwable;)Z"))
//     private boolean voxy$forceCrashOnError(Throwable exception) {
//         if (exception instanceof LoadException le) {
//             if (le.getCause() instanceof RuntimeException cause) {
//                 throw cause;
//             }
//             throw le;
//         }
//         return isNonRecoverable(exception);
//     }
// }
