package me.cortex.neovoxy.client.core;

import net.minecraft.client.Minecraft;

public interface IGetNeoVoxyRenderSystem {
    NeoVoxyRenderSystem getNeoVoxyRenderSystem();
    void shutdownRenderer();
    void createRenderer();

    static NeoVoxyRenderSystem getNullable() {
        var lr = (IGetNeoVoxyRenderSystem)Minecraft.getInstance().levelRenderer;
        if (lr == null) return null;
        return lr.getNeoVoxyRenderSystem();
    }
}
