package me.cortex.voxy.client.core;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.rendering.AbstractFarWorldRenderer;
import me.cortex.voxy.client.core.rendering.IRenderInterface;
import me.cortex.voxy.client.core.rendering.RenderTracker;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.building.RenderGenerationService;
import me.cortex.voxy.client.saver.ContextSelectionSystem;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import net.minecraft.client.MinecraftClient;

public class DefaultRenderWorldInteractor implements AbstractRenderWorldInteractor {
    private final DistanceTracker distanceTracker;
    private final RenderTracker renderTracker;


    public DefaultRenderWorldInteractor(ContextSelectionSystem.WorldConfig cfg, WorldEngine world, IRenderInterface renderer)  {
        this.renderTracker = new RenderTracker(world, (AbstractFarWorldRenderer) renderer);

        //To get to chunk scale multiply the scale by 2, the scale is after how many chunks does the lods halve
        int q = VoxyConfig.CONFIG.qualityScale;
        int minY = MinecraftClient.getInstance().world.getBottomSectionCoord()/2;
        int maxY = MinecraftClient.getInstance().world.getTopSectionCoord()/2;

        if (cfg.minYOverride != Integer.MAX_VALUE) {
            minY = cfg.minYOverride;
        }

        if (cfg.maxYOverride != Integer.MIN_VALUE) {
            maxY = cfg.maxYOverride;
        }

        this.distanceTracker = new DistanceTracker(this.renderTracker, new int[]{q,q,q,q},
                (VoxyConfig.CONFIG.renderDistance<0?VoxyConfig.CONFIG.renderDistance:((VoxyConfig.CONFIG.renderDistance+1)/2)),
                minY, maxY);

        System.out.println("Distance tracker initialized");
    }

    @Override
    public void processBuildResult(BuiltSection section) {
        this.renderTracker.processBuildResult(section);
    }

    @Override
    public void sectionUpdated(WorldSection worldSection) {
        this.renderTracker.sectionUpdated(worldSection);
    }

    @Override
    public void setRenderGen(RenderGenerationService renderService) {
        this.renderTracker.setRenderGen(renderService);
    }

    @Override
    public void initPosition(int x, int z) {
        this.distanceTracker.init(x,z);
    }

    @Override
    public void setCenter(int x, int y, int z) {
        this.distanceTracker.setCenter(x,y,z);
    }
}
