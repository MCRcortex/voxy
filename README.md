Voxy is an LoD rendering mod for minecraft

## Minecraft 26.3 port

Requires Java 25, Fabric Loader 0.19.5+, Fabric API 0.160.7+26.3 and
Sodium mc26.3-0.9.2-fabric. This port uses **OpenGL**. Vulkan LoD rendering
is not implemented; Voxy disables its renderer when another backend is selected.

Build with a Java 25 JDK selected in `JAVA_HOME`:

```powershell
.\gradlew.bat build
```

The mod jar is written to `build/libs`. Optional development dependencies are
pinned in `gradle.properties`. Vivecraft, Flashback and Nvidium remain compile-only
integration targets from earlier Minecraft releases; their 26.3 compatibility is
not claimed. Iris 1.11.6+26.3 can be enabled in the development client with
`-Pruntime.iris_version=1.11.6+26.3-fabric`.

### In-world smoke test

```powershell
.\gradlew.bat -I scripts/smoke.gradle runClient
.\gradlew.bat -I scripts/smoke.gradle runClient -Pruntime.iris_version=1.11.6+26.3-fabric
```

Each invocation opens a development client and creates a new disposable world
under `run/saves/voxy-port-smoke-*`. It checks that Voxy has a renderer after
400 in-world client ticks, prints `VOXY_PORT_SMOKE_OK`, and closes the client.
The smoke-test mod is excluded from the normal build. This checks startup,
world loading and renderer lifetime, not visual correctness or shaderpack compatibility.

See [the Vulkan port assessment](docs/vulkan-port.md) for the remaining rendering work.
