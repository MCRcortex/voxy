package me.cortex.voxy.client.taskbar;

import me.cortex.voxy.common.Logger;
import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.SystemUtils;

public abstract class Taskbar {
    public interface ITaskbar {
        void setProgress(long count, long outOf);

        void setIsNone();
        void setIsProgression();
        void setIsPaused();
        void setIsError();
    }

    public static class NoopTaskbar implements ITaskbar {
        private NoopTaskbar() {}

        @Override
        public void setIsNone() {}

        @Override
        public void setProgress(long count, long outOf) {}

        @Override
        public void setIsPaused() {}

        @Override
        public void setIsProgression() {}

        @Override
        public void setIsError() {}
    }

    public static final ITaskbar INSTANCE = createInterface();
    private static ITaskbar createInterface() {
        if (SystemUtils.IS_OS_WINDOWS) {
            try {
                return new WindowsTaskbar(Minecraft.getInstance().getWindow().getWindow());
            } catch (Exception e) {
                Logger.error("Unable to create windows taskbar interface", e);
                return new NoopTaskbar();
            }
        } else {
            return new NoopTaskbar();
        }
    }
}
