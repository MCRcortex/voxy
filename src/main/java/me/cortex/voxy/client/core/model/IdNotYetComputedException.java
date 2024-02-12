package me.cortex.voxy.client.core.model;

public class IdNotYetComputedException extends RuntimeException {
    public IdNotYetComputedException(int id) {
        super("Id not yet computed: " + id);
    }
}
