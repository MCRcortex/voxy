package me.cortex.voxy.client;

import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.util.GPUTiming;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class DebugEntries {
    public static final Identifier GPU_DEBUG = Identifier.fromNamespaceAndPath("voxy", "gpu_debug");
    public static void init() {
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
                if (wr != null) vrs = ((IGetVoxyRenderSystem) wr).voxy$getRenderSystem();

                //Voxy instance active
                lines.addLine((vrs==null?ChatFormatting.DARK_GREEN:ChatFormatting.GREEN)+"voxy-"+VoxyCommon.MOD_VERSION);
            }
        });

        DebugScreenEntries.register(Identifier.fromNamespaceAndPath("voxy","debug"), new VoxyDebugScreenEntry());

        DebugScreenEntries.register(GPU_DEBUG, new DebugScreenEntry() {
            @Override
            public void display(DebugScreenDisplayer debugScreenDisplayer, @Nullable Level level, @Nullable LevelChunk levelChunk, @Nullable LevelChunk levelChunk2) {

            }
        });
    }

    private static boolean previousGpuDebugEnabled = false;
    public static void onRebuild(Map<Identifier, DebugScreenEntryStatus> allStatuses, List<Identifier> enabled) {
        var entry = allStatuses.getOrDefault(GPU_DEBUG, DebugScreenEntryStatus.NEVER);
        if ((entry!=DebugScreenEntryStatus.NEVER)!=previousGpuDebugEnabled) {
            previousGpuDebugEnabled ^= true;

            GPUTiming.INSTANCE.setEnabled(previousGpuDebugEnabled);
            RenderStatistics.enabled = previousGpuDebugEnabled;
            var renderer = Minecraft.getInstance().levelExtractor;
            if (renderer!=null)renderer.allChanged();
        }
    }
}
