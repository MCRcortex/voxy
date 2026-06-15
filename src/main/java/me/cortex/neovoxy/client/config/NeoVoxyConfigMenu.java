package me.cortex.neovoxy.client.config;

import me.cortex.neovoxy.client.RenderStatistics;
import me.cortex.neovoxy.client.config.SodiumConfigBuilder.*;
import me.cortex.neovoxy.client.NeoVoxyClient;
import me.cortex.neovoxy.client.NeoVoxyClientInstance;
import me.cortex.neovoxy.client.core.IGetNeoVoxyRenderSystem;
import me.cortex.neovoxy.client.core.util.IrisUtil;
import me.cortex.neovoxy.common.util.cpu.CpuLayout;
import me.cortex.neovoxy.commonImpl.NeoVoxyCommon;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.option.Range;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class NeoVoxyConfigMenu implements ConfigEntryPoint {
    @Override
    public void registerConfigLate(ConfigBuilder B) {
        if (!NeoVoxyCommon.isAvailable()) return;//Dont even register the config if its not avalible

        var CFG = NeoVoxyConfig.CONFIG;

        var cc = B.registerModOptions("neovoxy", "NeoVoxy", NeoVoxyCommon.MOD_VERSION)
                .setIcon(ResourceLocation.parse("neovoxy:icon.png"));

        final var RENDER_RELOAD = OptionFlag.REQUIRES_RENDERER_RELOAD.getId().toString();

        SodiumConfigBuilder.buildToSodium(B, cc, CFG::save, postOp->{
                    postOp.register("neovoxy:update_threads", ()->{
                        var instance = NeoVoxyCommon.getInstance();
                        if (instance != null) {
                            instance.updateDedicatedThreads();
                        }
                    }, "neovoxy:enabled").register("neovoxy:iris_reload", ()->IrisUtil.reload());
                },
                new Page(Component.translatable("neovoxy.config.general"),
                        new Group(
                                new BoolOption(
                                        "neovoxy:enabled",
                                        Component.translatable("neovoxy.config.general.enabled"),
                                        ()->CFG.enabled, v->{
                                            CFG.enabled=v;
                                            //we need to special case enabled, since the render reload flag runs befor us and its quite important we get it right
                                            if (v&&NeoVoxyClientInstance.isInGame) {
                                                NeoVoxyCommon.createInstance();
                                            }
                                        })
                                        .setPostChangeRunner(c->{
                                            if (!c) {
                                                var vrsh = (IGetNeoVoxyRenderSystem) Minecraft.getInstance().levelRenderer;
                                                if (vrsh != null) {
                                                    vrsh.shutdownRenderer();
                                                }
                                                NeoVoxyCommon.shutdownInstance();
                                            }
                                        }).setPostChangeFlags(RENDER_RELOAD, "neovoxy:iris_reload").setEnabler(null)
                        ), new Group(
                                new IntOption(
                                        "neovoxy:thread_count",
                                        Component.translatable("neovoxy.config.general.serviceThreads"),
                                        ()->CFG.serviceThreads, v->CFG.serviceThreads=v,
                                        new Range(1, CpuLayout.getCoreCount(), 1))
                                        .setPostChangeFlags("neovoxy:update_threads"),
                                new BoolOption(
                                        "neovoxy:use_sodium_threads",
                                        Component.translatable("neovoxy.config.general.useSodiumBuilder"),
                                        ()->!CFG.dontUseSodiumBuilderThreads, v->CFG.dontUseSodiumBuilderThreads=!v)
                                        .setPostChangeFlags("neovoxy:update_threads")
                        ), new Group(
                                new BoolOption(
                                        "neovoxy:ingest_enabled",
                                        Component.translatable("neovoxy.config.general.ingest"),
                                        ()->CFG.ingestEnabled, v->CFG.ingestEnabled=v)
                        )
                ).setEnabler("neovoxy:enabled"),
                new Page(Component.translatable("neovoxy.config.rendering"),
                        new Group(
                                new BoolOption(
                                        "neovoxy:rendering",
                                        Component.translatable("neovoxy.config.general.rendering"),
                                        ()->CFG.enableRendering, v->CFG.enableRendering=v)
                                        .setPostChangeRunner(c->{
                                            var vrsh = (IGetNeoVoxyRenderSystem)Minecraft.getInstance().levelRenderer;
                                            if (vrsh != null) {
                                                if (c) {
                                                    vrsh.createRenderer();
                                                } else {
                                                    vrsh.shutdownRenderer();
                                                }
                                            }
                                        },"neovoxy:enabled", RENDER_RELOAD)
                                        .setPostChangeFlags("neovoxy:iris_reload")
                                        .setEnabler("neovoxy:enabled")
                        ), new Group(
                                new IntOption(
                                        "neovoxy:subdivsize",
                                        Component.translatable("neovoxy.config.general.subDivisionSize"),
                                        ()->subDiv2ln(CFG.subDivisionSize), v->CFG.subDivisionSize=ln2subDiv(v),
                                        new Range(0, SUBDIV_IN_MAX, 1))
                                        .setFormatter(v->Component.literal(Integer.toString(Math.round(ln2subDiv(v))))),
                                new IntOption(
                                        "neovoxy:render_distance",
                                        Component.translatable("neovoxy.config.general.renderDistance"),
                                        ()->CFG.sectionRenderDistance, v->CFG.sectionRenderDistance=v,
                                        new Range(2, 64, 1))
                                        .setFormatter(v->Component.literal(Integer.toString(v*32)))//Top level rd == 32 chunks
                                        .setPostChangeRunner(c->{
                                            var vrsh = (IGetNeoVoxyRenderSystem)Minecraft.getInstance().levelRenderer;
                                            if (vrsh != null) {
                                                var vrs = vrsh.getNeoVoxyRenderSystem();
                                                if (vrs != null) {
                                                    vrs.setRenderDistance(c);
                                                }
                                            }
                                        }, "neovoxy:rendering", RENDER_RELOAD)
                        ), new Group(
                                new BoolOption(
                                        "neovoxy:eviromental_fog",
                                        Component.translatable("neovoxy.config.general.environmental_fog"),
                                        ()->CFG.useEnvironmentalFog, v->CFG.useEnvironmentalFog=v)
                                        .setPostChangeFlags(RENDER_RELOAD)
                        ), new Group(
                                new BoolOption(
                                        "neovoxy:render_debug",
                                        Component.translatable("neovoxy.config.general.render_statistics"),
                                        ()-> RenderStatistics.enabled, v->RenderStatistics.enabled=v)
                                        .setPostChangeFlags(RENDER_RELOAD))
                ).setEnablerAND("neovoxy:enabled", "neovoxy:rendering"));

    }


    private static final int SUBDIV_IN_MAX = 100;
    private static final double SUBDIV_MIN = 28;
    private static final double SUBDIV_MAX = 256;
    private static final double SUBDIV_CONST = Math.log(SUBDIV_MAX/SUBDIV_MIN)/Math.log(2);

    //In range is 0->200
    //Out range is 28->256
    private static float ln2subDiv(int in) {
        return (float) (SUBDIV_MIN*Math.pow(2, SUBDIV_CONST*((double)in/SUBDIV_IN_MAX)));
    }

    //In range is ... any?
    //Out range is 0->200
    private static int subDiv2ln(float in) {
        return (int) (((Math.log(((double)in)/SUBDIV_MIN)/Math.log(2))/SUBDIV_CONST)*SUBDIV_IN_MAX);
    }
}
