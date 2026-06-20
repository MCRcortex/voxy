package me.cortex.voxy.client.mixin.minecraft;

import me.cortex.voxy.client.VoxyClientInstance;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer implements IGetVoxyRenderSystem {
    @Shadow private @Nullable ClientLevel level;
    @Unique private VoxyRenderSystem renderer;

    // Bumped on every shutdown/create (all on the render thread). An in-flight async engine
    // load captures the value at start and re-checks it before installing the renderer, so a
    // load that has been superseded by a level change / disconnect / reload is discarded.
    @Unique private int voxy$loadGeneration;

    @Override
    public VoxyRenderSystem voxy$getRenderSystem() {
        return this.renderer;
    }

    @Inject(method = "allChanged()V", at = @At("RETURN"), order = 900)//We want to inject before sodium
    private void voxy$reloadVoxyRenderer(CallbackInfo ci) {
        this.voxy$shutdownRenderer();
        if (this.level != null) {
            this.voxy$createRenderer();
        }
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void voxy$captureSetWorld(ClientLevel world, CallbackInfo ci) {
        if (this.level != world) {
            this.voxy$shutdownRenderer();
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void voxy$injectClose(CallbackInfo ci) {
        this.voxy$shutdownRenderer();
    }

    @Override
    public void voxy$shutdownRenderer() {
        // invalidate any in-flight async engine load
        this.voxy$loadGeneration++;
        if (this.renderer != null) {
            this.renderer.shutdown();
            this.renderer = null;
        }
    }

    @Override
    public void voxy$createRenderer() {
        if (this.renderer != null) throw new IllegalStateException("Cannot have multiple renderers");
        if (!VoxyConfig.CONFIG.enabled) {
            Logger.info("Not creating renderer due to disabled");
            return;
        }
        if (!VoxyConfig.CONFIG.isRenderingEnabled()) {
            Logger.info("Not creating renderer due to disabled rendering");
            return;
        }
        if (this.level == null) {
            Logger.error("Not creating renderer due to null world");
            return;
        }
        var instance = (VoxyClientInstance)VoxyCommon.getInstance();
        if (instance == null) {
            //This is now legal (e.g. when the instance is disabled)
            Logger.info("Not creating renderer due to null instance");
            return;
        }

        // Opening the WorldEngine (RocksDB open + Mapper/palette load) reads the on-disk LOD
        // store and can take many seconds for a large store. Done here on the render thread
        // (inside LevelRenderer.allChanged) it freezes the client on join. So build the engine
        // on a background thread, then install the GL-bound VoxyRenderSystem back on the render
        // thread once the engine is ready.
        final ClientLevel capturedLevel = this.level;
        final VoxyClientInstance capturedInstance = instance;
        final int generation = ++this.voxy$loadGeneration;

        Thread loader = new Thread(() -> {
            WorldEngine world;
            try {
                world = WorldIdentifier.ofEngine(capturedLevel);
            } catch (Throwable e) {
                Logger.error("Async Voxy engine load failed", e);
                return;
            }
            if (world == null) {
                Logger.error("Null world selected");
                return;
            }
            Minecraft.getInstance().execute(() ->
                    this.voxy$installRenderer(world, capturedInstance, capturedLevel, generation));
        }, "Voxy-EngineLoader");
        loader.setDaemon(true);
        loader.start();
    }

    // Runs on the render thread (GL context current) after the engine has been built off-thread.
    @Unique
    private void voxy$installRenderer(WorldEngine world, VoxyClientInstance capturedInstance,
                                      ClientLevel capturedLevel, int generation) {
        if (generation != this.voxy$loadGeneration) {
            // superseded by a shutdown / newer load; leave the engine cached (Voxy's idle-world
            // cleaner frees it if unused) and do nothing.
            return;
        }
        if (this.level != capturedLevel || this.level == null) {
            return;
        }
        if (this.renderer != null) {
            return;
        }
        if (VoxyCommon.getInstance() != capturedInstance) {
            return;
        }
        try {
            this.renderer = new VoxyRenderSystem(world, capturedInstance.getServiceManager());
        } catch (RuntimeException e) {
            if (IrisUtil.irisShaderPackEnabled()) {
                IrisUtil.disableIrisShaders();
            } else {
                throw e;
            }
        }
        capturedInstance.updateDedicatedThreads();
    }
}
