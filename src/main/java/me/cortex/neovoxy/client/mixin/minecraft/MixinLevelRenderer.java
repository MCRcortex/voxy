package me.cortex.neovoxy.client.mixin.minecraft;

import me.cortex.neovoxy.client.NeoVoxyClientInstance;
import me.cortex.neovoxy.client.config.NeoVoxyConfig;
import me.cortex.neovoxy.client.core.IGetNeoVoxyRenderSystem;
import me.cortex.neovoxy.client.core.NeoVoxyRenderSystem;
// MC 1.21.1 NeoForge: Iris shader integration excluded
// import me.cortex.neovoxy.client.core.util.IrisUtil;
import me.cortex.neovoxy.common.Logger;
import me.cortex.neovoxy.common.world.WorldEngine;
import me.cortex.neovoxy.commonImpl.NeoVoxyCommon;
import me.cortex.neovoxy.commonImpl.WorldIdentifier;
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
public abstract class MixinLevelRenderer implements IGetNeoVoxyRenderSystem {
    @Shadow private @Nullable ClientLevel level;
    @Unique private NeoVoxyRenderSystem renderer;

    @Override
    public NeoVoxyRenderSystem getNeoVoxyRenderSystem() {
        return this.renderer;
    }

    @Inject(method = "allChanged()V", at = @At("RETURN"), order = 900)//We want to inject before sodium
    private void reloadNeoVoxyRenderer(CallbackInfo ci) {
        this.shutdownRenderer();
        if (this.level != null) {
            this.createRenderer();
        }
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void neovoxy$captureSetWorld(ClientLevel world, CallbackInfo ci) {
        if (this.level != world) {
            this.shutdownRenderer();
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void injectClose(CallbackInfo ci) {
        this.shutdownRenderer();
    }

    @Override
    public void shutdownRenderer() {
        if (this.renderer != null) {
            this.renderer.shutdown();
            this.renderer = null;
        }
    }

    @Override
    public void createRenderer() {
        if (this.renderer != null) throw new IllegalStateException("Cannot have multiple renderers");
        if (!NeoVoxyConfig.CONFIG.enabled) {
            Logger.info("Not creating renderer due to disabled");
            return;
        }
        if (!NeoVoxyConfig.CONFIG.isRenderingEnabled()) {
            Logger.info("Not creating renderer due to disabled rendering");
            return;
        }
        if (this.level == null) {
            Logger.error("Not creating renderer due to null world");
            return;
        }
        var instance = (NeoVoxyClientInstance)NeoVoxyCommon.getInstance();
        if (instance == null) {
            Logger.error("Not creating renderer due to null instance");
            return;
        }
        WorldEngine world = WorldIdentifier.ofEngine(this.level);
        if (world == null) {
            Logger.error("Null world selected");
            return;
        }
        try {
            this.renderer = new NeoVoxyRenderSystem(world, instance.getServiceManager());
        } catch (RuntimeException e) {
            // MC 1.21.1 NeoForge: Iris shader integration excluded - irisShaderPackEnabled() returns false
            if (false) {
                // IrisUtil.disableIrisShaders();
            } else {
                throw e;
            }
        }
        instance.updateDedicatedThreads();
    }
}
