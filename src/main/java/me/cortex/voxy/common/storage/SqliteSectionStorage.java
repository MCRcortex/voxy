package me.cortex.voxy.common.storage;

import me.cortex.voxy.common.lod.LodSection;
import me.cortex.voxy.common.lod.WorldManifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * SQLite-backed implementation of {@link ISectionStorage}.
 *
 * <p>One database file per dimension, stored at the path supplied to the constructor.
 * Uses WAL mode for better concurrent read performance.  All mutating operations are
 * synchronised on {@code this} so the instance can be shared across threads.
 */
public final class SqliteSectionStorage implements ISectionStorage {

    private final Connection conn;

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS sections (
                section_key  INTEGER NOT NULL PRIMARY KEY,
                data         BLOB    NOT NULL,
                hash         INTEGER NOT NULL,
                updated_at   INTEGER NOT NULL DEFAULT (unixepoch())
            ) STRICT;
            """;

    private static final String CREATE_INDEX = """
            CREATE INDEX IF NOT EXISTS idx_hash ON sections (hash);
            """;

    public SqliteSectionStorage(Path dbPath) throws SQLException {
        try {
            Files.createDirectories(dbPath.getParent());
        } catch (IOException e) {
            throw new SQLException("Cannot create parent directories for " + dbPath, e);
        }

        String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        conn = DriverManager.getConnection(url);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL;");
            st.execute("PRAGMA synchronous=NORMAL;");
            st.execute("PRAGMA cache_size=-8000;"); // ~8 MB page cache
            st.execute(CREATE_TABLE);
            st.execute(CREATE_INDEX);
        }
    }

    // -------------------------------------------------------------------------

    @Override
    public synchronized void store(LodSection section) {
        String sql = """
                INSERT INTO sections (section_key, data, hash, updated_at)
                VALUES (?, ?, ?, unixepoch())
                ON CONFLICT(section_key) DO UPDATE
                    SET data       = excluded.data,
                        hash       = excluded.hash,
                        updated_at = excluded.updated_at
                WHERE excluded.hash != sections.hash;
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, section.key);
            ps.setBytes(2, section.toBytes());
            ps.setLong(3, section.hash);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to store section " + section.key, e);
        }
    }

    @Override
    public Optional<LodSection> load(long sectionKey) {
        String sql = "SELECT data FROM sections WHERE section_key = ?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, sectionKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                byte[] data = rs.getBytes(1);
                return Optional.of(LodSection.fromBytes(sectionKey, data));
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to load section " + sectionKey, e);
        }
    }

    @Override
    public boolean isCurrent(long sectionKey, long hash) {
        String sql = "SELECT 1 FROM sections WHERE section_key = ? AND hash = ?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, sectionKey);
            ps.setLong(2, hash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to check section currency", e);
        }
    }

    @Override
    public synchronized void remove(long sectionKey) {
        String sql = "DELETE FROM sections WHERE section_key = ?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, sectionKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to remove section " + sectionKey, e);
        }
    }

    @Override
    public WorldManifest buildManifest() {
        String sql = "SELECT section_key, hash FROM sections;";
        Map<Long, Long> map = new HashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs  = st.executeQuery(sql)) {
            while (rs.next()) {
                map.put(rs.getLong(1), rs.getLong(2));
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to build manifest", e);
        }
        return new WorldManifest(map);
    }

    @Override
    public synchronized void flush() {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA wal_checkpoint(PASSIVE);");
        } catch (SQLException e) {
            throw new StorageException("Failed to flush WAL", e);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        try {
            flush();
            conn.close();
        } catch (SQLException e) {
            throw new IOException("Failed to close SQLite connection", e);
        }
    }

    // -------------------------------------------------------------------------

    public static final class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
