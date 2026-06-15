package me.cortex.neovoxy.client;

import me.cortex.neovoxy.client.core.IGetNeoVoxyRenderSystem;
import me.cortex.neovoxy.client.core.NeoVoxyRenderSystem;
import me.cortex.neovoxy.commonImpl.NeoVoxyCommon;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class NeoVoxyDebugScreenEntry implements DebugScreenEntry {
    @Override
    public void display(DebugScreenDisplayer lines, @Nullable Level world, @Nullable LevelChunk clientChunk, @Nullable LevelChunk chunk) {
        if (!NeoVoxyCommon.isAvailable()) {
            return;
        }

        var instance = NeoVoxyCommon.getInstance();
        if (instance == null) {
            return;
        }

        NeoVoxyRenderSystem vrs = null;
        var wr = Minecraft.getInstance().levelRenderer;
        if (wr != null) vrs = ((IGetNeoVoxyRenderSystem) wr).getNeoVoxyRenderSystem();

        //lines.addLineToSection();
        List<String> instanceLines = new ArrayList<>();
        instance.addDebug(instanceLines);
        lines.addToGroup(ResourceLocation.fromNamespaceAndPath("neovoxy", "instance_debug"), instanceLines);

        if (vrs != null) {
            List<String> renderLines = new ArrayList<>();
            vrs.addDebugInfo(renderLines);
            lines.addToGroup(ResourceLocation.fromNamespaceAndPath("neovoxy", "render_debug"), renderLines);
        }
    }


}
