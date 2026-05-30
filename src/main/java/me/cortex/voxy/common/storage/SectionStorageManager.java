package me.cortex.voxy.common.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages one {@link SqliteSectionStorage} per dimension/world-key string.
 *
 * <p>All operations are thread-safe via a read/write lock.  Call {@link #close()} on
 * server shutdown or level unload to flush and release all database connections.
 */
public final class SectionStorageManager implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SectionStorageManager.class);

    private final Path baseDir;
    private final Map<String, SqliteSectionStorage> storages = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /** @param baseDir root directory under which per-dimension DB files are created */
    public SectionStorageManager(Path baseDir) {
        this.baseDir = baseDir;
    }

    /**
     * Returns (and lazily opens) the storage for the given dimension key.
     *
     * @param dimensionKey a stable string identifying the dimension,
     *                     e.g. {@code "minecraft:overworld"}
     */
    public SqliteSectionStorage get(String dimensionKey) {
        lock.readLock().lock();
        try {
            SqliteSectionStorage s = storages.get(dimensionKey);
            if (s != null) return s;
        } finally {
            lock.readLock().unlock();
        }

        lock.writeLock().lock();
        try {
            // Double-check after upgrading to write lock
            SqliteSectionStorage s = storages.get(dimensionKey);
            if (s != null) return s;

            Path dbPath = baseDir.resolve(sanitize(dimensionKey)).resolve("sections.db");
            LOGGER.info("[Voxy] Opening section storage for dimension '{}' at {}", dimensionKey, dbPath);
            s = new SqliteSectionStorage(dbPath);
            storages.put(dimensionKey, s);
            return s;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to open section storage for " + dimensionKey, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes and closes the storage for the given dimension, if open.
     * Called when a dimension is unloaded.
     */
    public void unload(String dimensionKey) {
        lock.writeLock().lock();
        try {
            SqliteSectionStorage s = storages.remove(dimensionKey);
            if (s != null) {
                tryClose(dimensionKey, s);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Closes all open storages. */
    @Override
    public void close() {
        lock.writeLock().lock();
        try {
            for (Map.Entry<String, SqliteSectionStorage> e : storages.entrySet()) {
                tryClose(e.getKey(), e.getValue());
            }
            storages.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    // -------------------------------------------------------------------------

    private void tryClose(String key, SqliteSectionStorage s) {
        try {
            s.close();
        } catch (IOException e) {
            LOGGER.error("[Voxy] Failed to close storage for '{}': {}", key, e.getMessage());
        }
    }

    /** Converts a dimension key like {@code "minecraft:overworld"} into a safe directory name. */
    private static String sanitize(String key) {
        return key.replace(':', '_').replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
