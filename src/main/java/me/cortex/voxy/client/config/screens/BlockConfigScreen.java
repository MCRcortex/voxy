package me.cortex.voxy.client.config.screens;

import me.cortex.voxy.client.config.BlockConfig;
import me.shedaniel.clothconfig2.api.AbstractConfigEntry;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.block.Blocks;
import net.minecraft.text.Text;

public class BlockConfigScreen {
    private static final ConfigEntryBuilder ENTRY_BUILDER = ConfigEntryBuilder.create();
    public static AbstractConfigListEntry<BlockConfig> makeScreen(BlockConfig config) {
        var entry = ENTRY_BUILDER.startSubCategory(config.block.getName());
        entry.add(UtilityScreens.makeBlockSelectionScreen(Text.literal("a"), Blocks.AIR, null));
        return null;
    }
}
