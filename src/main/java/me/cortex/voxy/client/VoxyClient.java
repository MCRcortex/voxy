package me.cortex.voxy.client;

import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.gl.Capabilities;
import me.cortex.voxy.client.core.gpu.RenderBackend;
import me.cortex.voxy.client.core.gpu.RenderBackendFactory;
import me.cortex.voxy.client.core.model.bakery.BudgetBufferRenderer;
import me.cortex.voxy.client.core.rendering.util.SharedIndexBuffer;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.function.Consumer;
import java.util.function.Function;

public class VoxyClient implements ClientModInitializer {
    private static final HashSet<String> FREX = new HashSet<>();

    public static void initVoxyClient() {
        Capabilities.init();//Ensure clinit is called

        if (Capabilities.INSTANCE.hasBrokenDepthSampler) {
            Logger.error("AMD broken depth sampler detected, voxy does not work correctly and has been disabled, this will hopefully be fixed in the future");
        }

        // Query the active render backend instead of OpenGL capabilities directly:
        // on macOS Apple Silicon the OpenGL driver tops out at 4.1 (no compute,
        // no indirect draws), which would fail this check even though the Metal
        // backend supports both. MetalRenderBackend reports compute and indirect
        // as available; the OpenGL backend delegates to the legacy Capabilities.
        RenderBackend backend = RenderBackendFactory.get();
        Logger.info("Render backend: " + backend.getType()
                + " (compute=" + backend.hasCompute()
                + ", indirectParameters=" + backend.hasIndirectParameters() + ")");

        boolean systemSupported = backend.hasCompute() && backend.hasIndirectParameters() && !Capabilities.INSTANCE.hasBrokenDepthSampler;

        // M9 transitional: even though MetalRenderBackend reports compute=true and
        // indirectParameters=true, Voxy's render path (MDICSectionRenderer,
        // HiZBuffer2, HierarchicalOcclusionTraverser, ChunkBoundRenderer,
        // bakery, AbstractRenderPipeline) is still raw OpenGL DSA and aborts
        // the JVM the first time the GL driver hits an unsupported call on
        // Apple's frozen GL 4.1. Until that work lands (M9-M11 migration to
        // the encoder API + IOSurface bridge), force-disable Voxy on
        // non-OpenGL backends so the mixins find VoxyRenderSystem == null
        // and Sodium's chunk render runs unmodified — the user sees normal
        // close-distance MC + Sodium rendering instead of a blue screen.
        // Feature flag to opt into the Metal render path. The flag exists
        // separately from regular env vars so opting in is intentional —
        // until the full MDIC encoder migration lands, this path produces
        // a clear color via the IOSurfaceBridge, not actual LOD chunks.
        boolean forceMetal = "1".equals(System.getenv("VOXY_FORCE_METAL"))
                || "true".equals(System.getenv("VOXY_FORCE_METAL"))
                || "true".equals(System.getProperty("voxy.forceMetal", "false"));

        if (systemSupported && backend.getType() != me.cortex.voxy.client.core.gpu.BackendType.OPENGL) {
            if (forceMetal) {
                Logger.info("[VOXY_FORCE_METAL] Voxy enabled on " + backend.getType()
                        + " backend. Render output flows through the IOSurface bridge; "
                        + "MDIC opaque/temporal/translucent passes draw real LOD geometry "
                        + "(M12 close). Remaining M13 gaps: model atlas (VOXY_NO_ATLAS debug "
                        + "colour stays), depth import (real HiZ + cull stub), SSAO + fog "
                        + "parity. See docs/STATUS.md.");
            } else {
                Logger.warn("[M9 TRANSITIONAL] Voxy disabled on " + backend.getType()
                        + " backend. Set VOXY_FORCE_METAL=1 to enable the Metal render path.");
                systemSupported = false;
            }
        }

        if (systemSupported) {

            SharedIndexBuffer.INSTANCE.id();
            BudgetBufferRenderer.init();

            VoxyCommon.setInstanceFactory(VoxyClientInstance::new);

            if (!Capabilities.INSTANCE.subgroup) {
                Logger.warn("GPU does not support subgroup operations, expect some performance degradation");
            }

        } else {
            Logger.error("Voxy is unsupported on your system.");
        }
    }

    @Override
    public void onInitializeClient() {
        DebugScreenEntries.register(Identifier.fromNamespaceAndPath("voxy", "version"), new DebugScreenEntry() {
            @Override
            public void display(DebugScreenDisplayer lines, @Nullable Level level, @Nullable LevelChunk levelChunk, @Nullable LevelChunk levelChunk2) {
                if (!VoxyCommon.isAvailable()) {
                    lines.addLine(ChatFormatting.RED + "voxy-"+VoxyCommon.MOD_VERSION);//Voxy installed, not avalible
                    return;
                }
                var instance = VoxyCommon.getInstance();
                if (instance == null) {
                    lines.addLine(ChatFormatting.YELLOW + "voxy-" + VoxyCommon.MOD_VERSION);//Voxy avalible, no instance active
                    return;
                }
                VoxyRenderSystem vrs = null;
                var wr = Minecraft.getInstance().levelRenderer;
                if (wr != null) vrs = ((IGetVoxyRenderSystem) wr).getVoxyRenderSystem();

                //Voxy instance active
                lines.addLine((vrs==null?ChatFormatting.DARK_GREEN:ChatFormatting.GREEN)+"voxy-"+VoxyCommon.MOD_VERSION);
            }
        });

        DebugScreenEntries.register(Identifier.fromNamespaceAndPath("voxy","debug"), new VoxyDebugScreenEntry());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            if (VoxyCommon.isAvailable()) {
                dispatcher.register(VoxyCommands.register());
            }
        });

        FabricLoader.getInstance()
                .getEntrypoints("frex_flawless_frames", Consumer.class)
                .forEach(api -> ((Consumer<Function<String,Consumer<Boolean>>>)api).accept(name->active->{if (active) {
                    FREX.add(name);
                } else {
                    FREX.remove(name);
                }}));
    }

    public static boolean isFrexActive() {
        return !FREX.isEmpty();
    }

    public static int getOcclusionDebugState() {
        return 0;
    }

    public static boolean disableSodiumChunkRender() {
        return false;// getOcclusionDebugState() != 0;
    }
}