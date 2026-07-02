package me.cortex.voxy.client.core.rendering;

import me.cortex.voxy.client.core.util.IrisUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ViewportSelector <T extends Viewport<?>> {
    // Vivecraft has no NeoForge build, so it can never be present on this platform.
    // (Same class of unavoidable gap as Nvidium/Flashback — not a parity regression.)
    public static final boolean VIVECRAFT_INSTALLED = false;

    private final Supplier<T> creator;
    private final T defaultViewport;
    private final Map<Object, T> extraViewports = new HashMap<>();//TODO should maybe be a weak hashmap with value cleanup queue thing?

    public ViewportSelector(Supplier<T> viewportCreator) {
        this.creator = viewportCreator;
        this.defaultViewport = viewportCreator.get();
    }

    private T getOrCreate(Object holder) {
        return this.extraViewports.computeIfAbsent(holder, a->this.creator.get());
    }

    // Vivecraft VR viewport — unavailable on NeoForge (no Vivecraft build). Always null.
    private T getVivecraftViewport() {
        return null;
    }

    private static final Object IRIS_SHADOW_OBJECT = new Object();
    public T getViewport() {
        T viewport = null;
        if (viewport == null && VIVECRAFT_INSTALLED) {
            viewport = getVivecraftViewport();
        }

        if (viewport == null && IrisUtil.irisShadowActive()) {
            viewport = this.getOrCreate(IRIS_SHADOW_OBJECT);
        }

        if (viewport == null) {
            viewport = this.defaultViewport;
        }
        return viewport;
    }

    public void free() {
        this.defaultViewport.delete();
        this.extraViewports.values().forEach(Viewport::delete);
        this.extraViewports.clear();
    }
}
