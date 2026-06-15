package me.cortex.neovoxy.client.iris;

public class ShaderLoadError extends RuntimeException {
    public ShaderLoadError(String reason) {
        super(reason);
    }

    public ShaderLoadError(String reason, Exception cause) {
        super(reason, cause);
    }
}
