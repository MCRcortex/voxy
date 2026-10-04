package me.cortex.voxy.client.mixin.minecraft;

import me.cortex.voxy.client.VoxyClientInstance;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.commonImpl.VoxyInstance;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer implements IVoxyRenderSystemHolder {
    @Unique @Nullable private WorldIdentifier identifier;
    @Unique private @Nullable VoxyRenderSystem renderer;

    @Override
    public VoxyRenderSystem voxy$getRenderSystem() {
        return this.renderer;
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void voxy$injectClose(CallbackInfo ci) {
        this.voxy$shutdownRenderer();
    }

    @Override
    public void voxy$shutdownRenderer() {
        if (this.renderer != null) {
            this.renderer.shutdown();
            this.renderer = null;
        }
    }

    /*
    @Override
    public void voxy$reloadRenderer() {
        this.voxy$shutdownRenderer();
        this.voxy$createRenderer();
    }*/

    @Override
    public void voxy$setWorld(Level level) {
        WorldIdentifier identifier = level==null?null:WorldIdentifier.of(level);
        if (Objects.equals(this.identifier, identifier)) return;
        this.voxy$shutdownRenderer();
        this.identifier = identifier;
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
        if (this.identifier == null) {
            Logger.info("Not creating renderer due to null identifier");
            return;
        }
        var instance = (VoxyClientInstance)VoxyCommon.getInstance();
        if (instance == null) {
            //This is now legal (e.g. when the instance is disabled)
            Logger.info("Not creating renderer due to null instance");
            return;
        }
        var worldOrFuture = this.identifier.getOrStartEngine(true);
        if (worldOrFuture == null) {
            Logger.warn("Not creating renderer due to null engine/future");
            return;
        }
        if (worldOrFuture instanceof CompletableFuture<?> future) {
            Logger.info("Not creating renderer at the moment due to loading future");
            var identifierAtStart = this.identifier;
            ((CompletableFuture<WorldEngine>)future).thenAcceptAsync(engine -> {
                if (VoxyCommon.getInstance() == null) {
                    Logger.warn("Voxy instance null on loading future finish, not creating renderer");
                    engine.releaseRef();
                    return;
                }
                if (engine == null) {
                    Logger.error("Loading future return null, this is really bad, not creating renderer");
                    return;
                }
                if (!identifierAtStart.equals(this.identifier)) {
                    Logger.warn("Dimension identifier not the same as when loading started, not creating renderer");
                    engine.releaseRef();
                    return;
                }

                if (this.renderer != null) {
                    Logger.warn("Renderer not null when loading future finished, not creating renderer");
                    engine.releaseRef();
                    return;
                }

                //TODO: check if its better to maybe reload the renderer
                this.voxy$createEngineDirect(engine);
            }, Minecraft.getInstance());
        } else {
            this.voxy$createEngineDirect((WorldEngine) worldOrFuture);
        }
    }

    @Unique
    private void voxy$createEngineDirect(WorldEngine world) {
        //World has a ref when we acquire it so we need to release it on exit
        try {
            var instance = world.instanceIn;
            if (instance == null)
                throw new IllegalStateException();//in theory this could be null if is like in a test suit or something
            if (instance != VoxyCommon.getInstance())
                throw new IllegalStateException();//in theory this could be null if is like in a test suit or something
            try {
                this.renderer = new VoxyRenderSystem(world, instance.getServiceManager());
            } catch (RuntimeException e) {
                if (IrisUtil.irisShaderPackEnabled()) {
                    IrisUtil.disableIrisShaders();
                } else {
                    throw e;
                }
            }
            instance.updateDedicatedThreads();
        } finally {
            world.releaseRef();
        }
    }
}
