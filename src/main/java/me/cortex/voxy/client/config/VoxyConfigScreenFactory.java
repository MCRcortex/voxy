package me.cortex.voxy.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.cortex.voxy.client.core.IGetVoxelCore;
import me.shedaniel.clothconfig2.ClothConfigDemo;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

public class VoxyConfigScreenFactory implements ModMenuApi {
    private static VoxyConfig DEFAULT;
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> buildConfigScreen(parent, VoxyConfig.CONFIG);
    }

    private static Screen buildConfigScreen(Screen parent, VoxyConfig config) {
        if (DEFAULT == null) {
            DEFAULT = new VoxyConfig();
        }
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("voxy.config.title"));


        addGeneralCategory(builder, config);
        addThreadsCategory(builder, config);
        addStorageCategory(builder, config);

        builder.setSavingRunnable(() -> {
            //After saving the core should be reloaded/reset
            var world = (IGetVoxelCore)MinecraftClient.getInstance().worldRenderer;
            if (world != null) {
                world.reloadVoxelCore();
            }
            VoxyConfig.CONFIG.save();
        });

        return builder.build();//
    }

    private static void addGeneralCategory(ConfigBuilder builder, VoxyConfig config) {


        ConfigCategory category = builder.getOrCreateCategory(Text.translatable("voxy.config.general"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        /*
        category.addEntry(entryBuilder.startSubCategory(Text.translatable("aaa"), List.of(entryBuilder.startBooleanToggle(Text.translatable("voxy.config.general.enabled"), config.enabled)
                .setTooltip(Text.translatable("voxy.config.general.enabled.tooltip"))
                .setSaveConsumer(val -> config.enabled = val)
                .setDefaultValue(DEFAULT.enabled)
                .build(), entryBuilder.startSubCategory(Text.translatable("bbb"), List.of(entryBuilder.startIntSlider(Text.translatable("voxy.config.general.geometryBuffer"), config.geometryBufferSize, (1<<27)/8, ((1<<31)-1)/8)
                        .setTooltip(Text.translatable("voxy.config.general.geometryBuffer.tooltip"))
                        .setSaveConsumer(val -> config.geometryBufferSize = val)
                        .setDefaultValue(DEFAULT.geometryBufferSize)
                        .build())).build()
                )).build());
        */

        category.addEntry(entryBuilder.startBooleanToggle(Text.translatable("voxy.config.general.enabled"), config.enabled)
                .setTooltip(Text.translatable("voxy.config.general.enabled.tooltip"))
                .setSaveConsumer(val -> config.enabled = val)
                .setDefaultValue(DEFAULT.enabled)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(Text.translatable("voxy.config.general.ingest"), config.ingestEnabled)
                .setTooltip(Text.translatable("voxy.config.general.ingest.tooltip"))
                .setSaveConsumer(val -> config.ingestEnabled = val)
                .setDefaultValue(DEFAULT.ingestEnabled)
                .build());

        category.addEntry(entryBuilder.startIntSlider(Text.translatable("voxy.config.general.quality"), config.qualityScale, 8, 32)
                .setTooltip(Text.translatable("voxy.config.general.quality.tooltip"))
                .setSaveConsumer(val -> config.qualityScale = val)
                .setDefaultValue(DEFAULT.qualityScale)
                .build());

        category.addEntry(entryBuilder.startIntSlider(Text.translatable("voxy.config.general.geometryBuffer"), config.geometryBufferSize, (1<<27)/8, ((1<<31)-1)/8)
                .setTooltip(Text.translatable("voxy.config.general.geometryBuffer.tooltip"))
                .setSaveConsumer(val -> config.geometryBufferSize = val)
                .setDefaultValue(DEFAULT.geometryBufferSize)
                .build());

        category.addEntry(entryBuilder.startIntSlider(Text.translatable("voxy.config.general.maxSections"), config.maxSections, 100_000, 400_000)
                .setTooltip(Text.translatable("voxy.config.general.maxSections.tooltip"))
                .setSaveConsumer(val -> config.maxSections = val)
                .setDefaultValue(DEFAULT.maxSections)
                .build());

        category.addEntry(entryBuilder.startIntField(Text.translatable("voxy.config.general.renderDistance"), config.renderDistance)
                .setTooltip(Text.translatable("voxy.config.general.renderDistance.tooltip"))
                .setSaveConsumer(val -> config.renderDistance = val)
                .setDefaultValue(DEFAULT.renderDistance)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(Text.translatable("voxy.config.general.nvmesh"), config.useMeshShaderIfPossible)
                .setTooltip(Text.translatable("voxy.config.general.nvmesh.tooltip"))
                .setSaveConsumer(val -> config.useMeshShaderIfPossible = val)
                .setDefaultValue(DEFAULT.useMeshShaderIfPossible)
                .build());

        //category.addEntry(entryBuilder.startIntSlider(Text.translatable("voxy.config.general.compression"), config.savingCompressionLevel, 1, 21)
        //        .setTooltip(Text.translatable("voxy.config.general.compression.tooltip"))
        //        .setSaveConsumer(val -> config.savingCompressionLevel = val)
        //        .setDefaultValue(DEFAULT.savingCompressionLevel)
        //        .build());
    }

    private static void addThreadsCategory(ConfigBuilder builder, VoxyConfig config) {
        ConfigCategory category = builder.getOrCreateCategory(Text.translatable("voxy.config.threads"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        category.addEntry(entryBuilder.startIntSlider(Text.translatable("voxy.config.threads.ingest"), config.ingestThreads, 1, Runtime.getRuntime().availableProcessors())
                .setTooltip(Text.translatable("voxy.config.threads.ingest.tooltip"))
                .setSaveConsumer(val -> config.ingestThreads = val)
                .setDefaultValue(DEFAULT.ingestThreads)
                .build());

        category.addEntry(entryBuilder.startIntSlider(Text.translatable("voxy.config.threads.saving"), config.savingThreads, 1, Runtime.getRuntime().availableProcessors())
                .setTooltip(Text.translatable("voxy.config.threads.saving.tooltip"))
                .setSaveConsumer(val -> config.savingThreads = val)
                .setDefaultValue(DEFAULT.savingThreads)
                .build());

        category.addEntry(entryBuilder.startIntSlider(Text.translatable("voxy.config.threads.render"), config.renderThreads, 1, Runtime.getRuntime().availableProcessors())
                .setTooltip(Text.translatable("voxy.config.threads.render.tooltip"))
                .setSaveConsumer(val -> config.renderThreads = val)
                .setDefaultValue(DEFAULT.renderThreads)
                .build());
    }

    private static void addStorageCategory(ConfigBuilder builder, VoxyConfig config) {
        ConfigCategory category = builder.getOrCreateCategory(Text.translatable("voxy.config.storage"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        ////Temporary until i figure out how to do more complex multi layer configuration for storage
        //category.addEntry(entryBuilder.startStrField(Text.translatable("voxy.config.storage.path"), config.storagePath)
        //        .setTooltip(Text.translatable("voxy.config.storage.path.tooltip"))
        //        .setSaveConsumer(val -> config.storagePath = val)
        //        .setDefaultValue(DEFAULT.storagePath)
        //        .build());
    }

}
