package me.cortex.voxy.client.core.rendering.building;

import org.jetbrains.annotations.Nullable;

public record SectionUpdate(long position, long buildTime, @Nullable BuiltSection geometry, byte childExistence) {
}
