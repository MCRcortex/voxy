package me.cortex.neovoxy.client;

public class LoadException extends RuntimeException {
    public LoadException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
