package me.cortex.voxy.client.mixin.minecraft;

import me.cortex.voxy.client.DebugEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(DebugScreenEntryList.class)
public abstract class MixinDebugScreenEntryList {
    @Shadow @Final private List<Identifier> currentlyEnabled;
    @Shadow public abstract boolean isOverlayVisible();

    @Final
    @Shadow
    private Map<Identifier, DebugScreenEntryStatus> allStatuses;

    @Inject(method = "rebuildCurrentList", at = @At(value = "INVOKE", target = "Ljava/util/List;sort(Ljava/util/Comparator;)V"))
    private void voxy$injectVersionDisplay(CallbackInfo cir) {
        if (this.isOverlayVisible()) {
            var id = Identifier.fromNamespaceAndPath("voxy", "version");
            if (!this.currentlyEnabled.contains(id)) {
                this.currentlyEnabled.add(id);
            }
        }

        DebugEntries.onRebuild(this.allStatuses, this.currentlyEnabled);
    }
}
