// package me.cortex.voxy.client;

// import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
// import me.cortex.voxy.client.core.VoxyRenderSystem;
// import me.cortex.voxy.commonImpl.VoxyCommon;
// import net.minecraft.ChatFormatting;
// import net.minecraft.client.Minecraft;
// import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
// import net.minecraft.client.gui.components.debug.DebugScreenEntry;
// import net.minecraft.resources.ResourceLocation;
// import net.minecraft.world.level.Level;
// import net.minecraft.world.level.chunk.LevelChunk;
// import org.jetbrains.annotations.Nullable;

// import java.util.ArrayList;
// import java.util.List;

// public class VoxyDebugScreenEntry implements DebugScreenEntry {
//     @Override
//     public void display(DebugScreenDisplayer lines, @Nullable Level world, @Nullable LevelChunk clientChunk, @Nullable LevelChunk chunk) {
//         if (!VoxyCommon.isAvailable()) {
//             lines.addLine(ChatFormatting.RED + "voxy-"+VoxyCommon.MOD_VERSION);//Voxy installed, not avalible
//             return;
//         }
//         var instance = VoxyCommon.getInstance();
//         if (instance == null) {
//             lines.addLine(ChatFormatting.YELLOW + "voxy-" + VoxyCommon.MOD_VERSION);//Voxy avalible, no instance active
//             return;
//         }
//         VoxyRenderSystem vrs = null;
//         var wr = Minecraft.getInstance().levelRenderer;
//         if (wr != null) vrs = ((IGetVoxyRenderSystem) wr).getVoxyRenderSystem();

//         //Voxy instance active
//         lines.addLine((vrs==null?ChatFormatting.DARK_GREEN:ChatFormatting.GREEN)+"voxy-"+VoxyCommon.MOD_VERSION);

//         //lines.addLineToSection();
//         List<String> instanceLines = new ArrayList<>();
//         instance.addDebug(instanceLines);
//         lines.addToGroup(ResourceLocation.fromNamespaceAndPath("voxy", "instance_debug"), instanceLines);

//         if (vrs != null) {
//             List<String> renderLines = new ArrayList<>();
//             vrs.addDebugInfo(renderLines);
//             lines.addToGroup(ResourceLocation.fromNamespaceAndPath("voxy", "render_debug"), renderLines);
//         }
//     }


// }
