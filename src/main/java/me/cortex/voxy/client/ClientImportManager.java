package me.cortex.voxy.client;

import me.cortex.voxy.common.Logger;
import me.cortex.voxy.commonImpl.ImportManager;
import me.cortex.voxy.commonImpl.importers.IDataImporter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import java.util.UUID;

public class ClientImportManager extends ImportManager {
    protected class ClientImportTask extends ImportTask {
        private final UUID bossbarUUID;
        private final LerpingBossEvent bossBar;
        protected ClientImportTask(IDataImporter importer) {
            super(importer);

            this.bossbarUUID = UUID.randomUUID();
            this.bossBar = new LerpingBossEvent(this.bossbarUUID, Component.nullToEmpty("Voxy world importer"), 0.0f, BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS, false, false, false);
            Minecraft.getInstance().execute(()->{
                Minecraft.getInstance().gui.getBossOverlay().events.put(bossBar.getId(), bossBar);
            });
        }

        @Override
        protected boolean onUpdate(int completed, int outOf) {
            if (!super.onUpdate(completed, outOf)) {
                return false;
            }
            Minecraft.getInstance().execute(()->{
                this.bossBar.setProgress((float) (((double)completed) / ((double) Math.max(1, outOf))));
                this.bossBar.setName(Component.nullToEmpty("Voxy import: " + completed + "/" + outOf + " chunks"));
            });
            return true;
        }

        @Override
        protected void onCompleted(int total) {
            super.onCompleted(total);
            Minecraft.getInstance().execute(()->{
                Minecraft.getInstance().gui.getBossOverlay().events.remove(this.bossbarUUID);
                long delta = Math.max(System.currentTimeMillis() - this.startTime, 1);

                String msg = "Voxy world import finished in " + (delta/1000) + " seconds, averaging " + (int)(total/(delta/1000f)) + " chunks per second";
                Minecraft.getInstance().gui.getChat().addMessage(Component.literal(msg));
                Logger.info(msg);
            });
        }
    }

    @Override
    protected synchronized ImportTask createImportTask(IDataImporter importer) {
        return new ClientImportTask(importer);
    }
}
