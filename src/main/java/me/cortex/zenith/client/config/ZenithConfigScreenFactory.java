package me.cortex.zenith.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.cortex.zenith.client.IGetVoxelCore;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.api.Requirement;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import me.shedaniel.clothconfig2.gui.entries.IntegerListEntry;
import me.shedaniel.clothconfig2.gui.entries.IntegerSliderEntry;
import me.shedaniel.clothconfig2.impl.builders.DropdownMenuBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ZenithConfigScreenFactory implements ModMenuApi {
    private static final ZenithConfig DEFAULT = new ZenithConfig();
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> buildConfigScreen(parent, ZenithConfig.CONFIG);
    }

    private static Screen buildConfigScreen(Screen parent, ZenithConfig config) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("title.zenith.config"));


        addGeneralCategory(builder, config);
        addThreadsCategory(builder, config);
        addStorageCategory(builder, config);

        builder.setSavingRunnable(() -> {
            //After saving the core should be reloaded/reset
            var world = (IGetVoxelCore)MinecraftClient.getInstance().worldRenderer;
            if (world != null) {
                world.reloadVoxelCore();
            }
            ZenithConfig.CONFIG.save();
        });

        return builder.build();
    }

    private static void addGeneralCategory(ConfigBuilder builder, ZenithConfig config) {
        ConfigCategory category = builder.getOrCreateCategory(Text.translatable("zenith.config.general"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();


        category.addEntry(entryBuilder.startBooleanToggle(Text.translatable("zenith.config.general.enabled"), config.enabled)
                .setTooltip(Text.translatable("zenith.config.general.enabled.tooltip"))
                .setSaveConsumer(val -> config.enabled = val)
                .setDefaultValue(DEFAULT.enabled)
                .build());

        category.addEntry(entryBuilder.startIntSlider(Text.translatable("zenith.config.general.quality"), config.qualityScale, 10, 50)
                .setTooltip(Text.translatable("zenith.config.general.quality.tooltip"))
                .setSaveConsumer(val -> config.qualityScale = val)
                .setDefaultValue(DEFAULT.qualityScale)
                .build());

        category.addEntry(entryBuilder.startIntSlider(Text.translatable("zenith.config.general.compression"), config.savingCompressionLevel, 1, 21)
                .setTooltip(Text.translatable("zenith.config.general.compression.tooltip"))
                .setSaveConsumer(val -> config.savingCompressionLevel = val)
                .setDefaultValue(DEFAULT.savingCompressionLevel)
                .build());
    }

    private static void addThreadsCategory(ConfigBuilder builder, ZenithConfig config) {
        ConfigCategory category = builder.getOrCreateCategory(Text.translatable("zenith.config.threads"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        category.addEntry(entryBuilder.startIntSlider(Text.translatable("zenith.config.threads.ingest"), config.ingestThreads, 1, Runtime.getRuntime().availableProcessors())
                .setTooltip(Text.translatable("zenith.config.general.ingest.tooltip"))
                .setSaveConsumer(val -> config.ingestThreads = val)
                .setDefaultValue(DEFAULT.ingestThreads)
                .build());

        category.addEntry(entryBuilder.startIntSlider(Text.translatable("zenith.config.threads.saving"), config.savingThreads, 1, Runtime.getRuntime().availableProcessors())
                .setTooltip(Text.translatable("zenith.config.general.saving.tooltip"))
                .setSaveConsumer(val -> config.savingThreads = val)
                .setDefaultValue(DEFAULT.savingThreads)
                .build());

        category.addEntry(entryBuilder.startIntSlider(Text.translatable("zenith.config.threads.render"), config.renderThreads, 1, Runtime.getRuntime().availableProcessors())
                .setTooltip(Text.translatable("zenith.config.general.render.tooltip"))
                .setSaveConsumer(val -> config.renderThreads = val)
                .setDefaultValue(DEFAULT.renderThreads)
                .build());
    }

    private static void addStorageCategory(ConfigBuilder builder, ZenithConfig config) {
        ConfigCategory category = builder.getOrCreateCategory(Text.translatable("zenith.config.storage"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        //Temporary until i figure out how to do more complex multi layer configuration for storage
        category.addEntry(entryBuilder.startStrField(Text.translatable("zenith.config.storage.path"), config.storagePath)
                .setTooltip(Text.translatable("zenith.config.storage.path.tooltip"))
                .setSaveConsumer(val -> config.storagePath = val)
                .setDefaultValue(DEFAULT.storagePath)
                .build());
    }

}
