# Voxy for NeoForge 26.2

Voxy is a far-distance rendering mod for Minecraft. The original Fabric mod is by Cortex; this NeoForge 26.2 port is by [@not-oshi](https://github.com/not-oshi).

## Compatibility

- Java 25
- NeoForge 26.2
- Sodium for NeoForge 0.9.2 or newer (required)
- Iris, Lithium, and Vivecraft (optional integrations)

The configuration screen is registered through Sodium's Config API and appears in Sodium's video settings.

## Build

Run the Gradle wrapper:

- Windows: `gradlew.bat build`
- Linux/macOS: `./gradlew build`

## Port notes

Fabric entrypoints, loader metadata, access widener, and Fabric API calls have been replaced with NeoForge equivalents. The Flashback replay-storage, Nvidium renderer, and Fabric-specific Chunky compatibility integrations are not included in this port. Iris mixins are only applied when Iris is installed.
