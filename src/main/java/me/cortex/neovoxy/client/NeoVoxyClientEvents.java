package me.cortex.neovoxy.client;

import me.cortex.neovoxy.client.config.NeoVoxyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Client event handlers for NeoVoxy on NeoForge.
 *
 * Handles fog rendering to push fog to infinity so NeoVoxy LODs render
 * without a fog wall at vanilla render distance.
 */
@EventBusSubscriber(modid = "neovoxy", value = Dist.CLIENT)
public class NeoVoxyClientEvents {
    private static boolean suppressLodRenderingForFog;

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }

        if (!NeoVoxyConfig.CONFIG.isRenderingEnabled()) {
            suppressLodRenderingForFog = false;
            return;
        }

        float renderDistance = Minecraft.getInstance().gameRenderer.getRenderDistance();
        boolean fluidFog = event.getType() != FogType.NONE;
        boolean closeFog = event.getFarPlaneDistance() < 10.0f;
        boolean environmentalFog = event.getFarPlaneDistance() < renderDistance - 1.0f;

        suppressLodRenderingForFog = fluidFog
                || closeFog
                || NeoVoxyConfig.CONFIG.useEnvironmentalFog && environmentalFog;

        if (!suppressLodRenderingForFog) {
            event.setNearPlaneDistance(999999.0f);
            event.setFarPlaneDistance(9999999.0f);
            event.setCanceled(true);
        }
    }

    public static boolean shouldSuppressLodRenderingForFog() {
        return suppressLodRenderingForFog;
    }
}
