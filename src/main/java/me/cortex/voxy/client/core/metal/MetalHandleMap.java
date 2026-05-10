package me.cortex.voxy.client.core.metal;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Maps integer IDs (compatible with the existing interface contract) to native
 * Metal object handles (long pointers).
 *
 * The existing GPU abstraction uses int id() for resource identification (matching
 * OpenGL's GLuint names). Metal uses Objective-C object pointers (long). This map
 * bridges the two by assigning monotonically increasing int IDs to Metal handles.
 *
 * Thread-safe for concurrent allocation and lookup.
 */
public final class MetalHandleMap {

    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static final ConcurrentHashMap<Integer, Long> ID_TO_HANDLE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Integer> HANDLE_TO_ID = new ConcurrentHashMap<>();

    private MetalHandleMap() {}

    /**
     * Registers a native Metal handle and returns a unique int ID.
     * @param handle native pointer (must not be 0)
     * @return a positive integer ID
     */
    public static int register(long handle) {
        if (handle == 0) {
            throw new IllegalArgumentException("Cannot register null Metal handle");
        }
        int id = NEXT_ID.getAndIncrement();
        ID_TO_HANDLE.put(id, handle);
        HANDLE_TO_ID.put(handle, id);
        return id;
    }

    /**
     * Gets the native handle for a given int ID.
     * @throws IllegalArgumentException if the ID is not registered
     */
    public static long getHandle(int id) {
        Long handle = ID_TO_HANDLE.get(id);
        if (handle == null) {
            throw new IllegalArgumentException("Unknown Metal handle ID: " + id);
        }
        return handle;
    }

    /**
     * Gets the int ID for a given native handle.
     * @return the ID, or -1 if the handle is not registered
     */
    public static int getId(long handle) {
        Integer id = HANDLE_TO_ID.get(handle);
        return id != null ? id : -1;
    }

    /**
     * Updates the native handle associated with an existing ID.
     *
     * Used when a resource is allocated lazily (e.g. {@link MetalTexture#store})
     * after the ID was reserved at construction with a sentinel handle. Keeps
     * the int ID stable across the construction → allocation transition so any
     * caller that holds the ID continues to resolve to the correct handle.
     *
     * @throws IllegalArgumentException if the ID was never registered.
     */
    public static void setHandle(int id, long handle) {
        if (handle == 0) {
            throw new IllegalArgumentException("Cannot set null Metal handle for id=" + id);
        }
        Long previous = ID_TO_HANDLE.put(id, handle);
        if (previous == null) {
            // Restore and complain — caller must register() before setHandle().
            ID_TO_HANDLE.remove(id);
            throw new IllegalArgumentException("setHandle on unknown id=" + id);
        }
        HANDLE_TO_ID.remove(previous);
        HANDLE_TO_ID.put(handle, id);
    }

    /**
     * Unregisters an ID and its associated handle.
     * Call this when the Metal resource is freed.
     * @return the native handle that was associated, or 0 if not found
     */
    public static long unregister(int id) {
        Long handle = ID_TO_HANDLE.remove(id);
        if (handle != null) {
            HANDLE_TO_ID.remove(handle);
            return handle;
        }
        return 0;
    }

    /**
     * Returns the number of currently registered handles (for debugging).
     */
    public static int size() {
        return ID_TO_HANDLE.size();
    }
}
