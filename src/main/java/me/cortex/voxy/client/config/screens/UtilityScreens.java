package me.cortex.voxy.client.config.screens;

import me.cortex.voxy.client.config.BlockConfig;
import me.shedaniel.clothconfig2.api.AbstractConfigEntry;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.DropdownMenuBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class UtilityScreens {
    private static final ConfigEntryBuilder ENTRY_BUILDER = ConfigEntryBuilder.create();
    public static AbstractConfigListEntry<Block> makeBlockSelectionScreen(Text name, Block selectedBlock, Consumer<Block> onSave) {
        return makeBlockSelectionScreen(name, Blocks.AIR, selectedBlock, onSave);
    }
    public static AbstractConfigListEntry<Block> makeBlockSelectionScreen(Text name, Block defaultBlock, Block selectedBlock, Consumer<Block> onSave) {
        return ENTRY_BUILDER.startDropdownMenu(name,
                DropdownMenuBuilder.TopCellElementBuilder.ofBlockObject(selectedBlock),
                DropdownMenuBuilder.CellCreatorBuilder.ofBlockObject())
                .setDefaultValue(defaultBlock)
                .setSelections(Registries.BLOCK.stream().toList())
                .setSaveConsumer(onSave)
                .build();
    }
}
