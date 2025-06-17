package me.cortex.voxy.commonImpl;

import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.config.Serialization;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

public class VoxyCommon implements ModInitializer {
    public static final String MOD_VERSION;
    public static final boolean IS_DEDICATED_SERVER;
    public static final boolean IS_IN_MINECRAFT;

    static {
        ModContainer mod = (ModContainer) FabricLoader.getInstance().getModContainer("voxy").orElse(null);
        if (mod == null) {
            IS_IN_MINECRAFT = false;
            Logger.error("Running voxy without minecraft");
            MOD_VERSION = "<UNKNOWN>";
            IS_DEDICATED_SERVER = false;
        } else {
            IS_IN_MINECRAFT = true;
            var version = mod.getMetadata().getVersion().getFriendlyString();
            var commit = mod.getMetadata().getCustomValue("commit").getAsString();
            MOD_VERSION = version + "-" + commit;
            IS_DEDICATED_SERVER = FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
            Serialization.init();
        }
    }

    //This is hardcoded like this because people do not understand what they are doing
    private static final boolean GlobalVerificationDisableOverride = true;//System.getProperty("voxy.verificationDisableOverride", "false").equals("true");
    public static boolean isVerificationFlagOn(String name) {
        return (!GlobalVerificationDisableOverride) && System.getProperty("voxy."+name, "true").equals("true");
    }

    public static void breakpoint() {
        int breakpoint = 0;
    }

    @Override
    public void onInitialize() {

    }

    public interface IInstanceFactory {VoxyInstance create();}
    private static VoxyInstance INSTANCE;
    private static IInstanceFactory FACTORY = null;

    public static void setInstanceFactory(IInstanceFactory factory) {
        if (FACTORY != null) {
            throw new IllegalStateException("Cannot set instance factory more than once");
        }
        FACTORY = factory;
    }

    public static VoxyInstance getInstance() {
        return INSTANCE;
    }

    public static void shutdownInstance() {
        if (INSTANCE != null) {
            INSTANCE.shutdown();
            INSTANCE = null;
        }
    }

    public static void createInstance() {
        if (FACTORY == null) {
            //Logger.info("Voxy factory");
            return;
        }
        if (INSTANCE != null) {
            throw new IllegalStateException("Cannot create multiple instances");
        }
        INSTANCE = FACTORY.create();
    }

    //Is voxy available in any capacity
    public static boolean isAvailable() {
        return FACTORY != null;
    }
}
