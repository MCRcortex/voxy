package me.cortex.voxy.commonImpl;

import me.cortex.voxy.common.world.WorldEngine;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class WorldMigrator {
    public CompletableFuture<WorldEngine> createMigrationFutureIfNeeded(WorldIdentifier id, Function<WorldIdentifier, WorldEngine> factory) {
        var exec = CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS);
        CompletableFuture<WorldEngine> future = new CompletableFuture<>();
        exec.execute(()->future.complete(factory.apply(id)));
        return future;
    }

    public void shutdown() {

    }
}
