# NeoVoxy NeoForge 1.21.1 Testing Guide

## Prerequisites

Before setting up the testing environment, ensure you have:

- **Java 21 or later** (required for Minecraft 1.21.1)
- **Prism Launcher** installed ([download here](https://prismlauncher.org/download/))
- **Internet connection** for downloading Minecraft and mod dependencies

## Step 1: Create NeoForge 1.21.1 Profile

### 1.1 Create New Instance

1. Open Prism Launcher
2. Click **"Add Instance"** in the top toolbar
3. In the Create New Instance dialog:
   - **Name:** `NeoVoxy NeoForge 1.21.1 Testing`
   - **Version:** Select `1.21.1`
   - **Mod Loader:** Select **NeoForge**
   - **Loader Version:** Select latest `21.1.x` version (e.g., `21.1.73` or newer)
4. Click **"OK"** to create the instance

### 1.2 Configure Java Runtime

1. Right-click the newly created instance
2. Select **"Edit Instance"**
3. Navigate to **"Settings"** → **"Java"**
4. Enable **"Use custom Java installation"** if needed
5. Ensure Java 21+ is selected
6. Set **"Maximum memory allocation"** to at least **4096 MiB** (recommended: 6144 MiB or higher for NeoVoxy's voxelization)

### 1.3 Configure JVM Arguments (Optional but Recommended)

In the Java settings, add these JVM arguments for better performance:

```
-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1
```

## Step 2: Install Required Dependencies

### 2.1 Download Sodium for NeoForge

NeoVoxy requires **Sodium 0.6.9 or later** for NeoForge 1.21.1:

1. Go to [Modrinth Sodium page](https://modrinth.com/mod/sodium) or [CurseForge Sodium page](https://www.curseforge.com/minecraft/mc-mods/sodium)
2. Download the **NeoForge 1.21.1** compatible version (0.6.9+)
3. **Important:** Ensure you download the **NeoForge** version, NOT the Fabric version

### 2.2 Install Sodium

1. In Prism Launcher, right-click the instance
2. Select **"Edit Instance"**
3. Navigate to **"Mods"** tab
4. Click **"Add .jar"** or **"Add file"**
5. Select the downloaded Sodium jar file
6. Verify Sodium appears in the mod list

## Step 3: Install NeoVoxy Mod

### 3.1 Build NeoVoxy (if not already built)

From the project directory:

```bash
./gradlew build
```

The compiled mod will be located at:
```
build/libs/neovoxy-0.2.9-alpha.jar
```

### 3.2 Install NeoVoxy

1. In Prism Launcher, ensure you're still in **"Edit Instance"** → **"Mods"** tab
2. Click **"Add .jar"**
3. Navigate to `build/libs/neovoxy-0.2.9-alpha.jar`
4. Click **"Open"**
5. Verify both Sodium and NeoVoxy appear in the mod list

### 3.3 Verify Dependencies

Ensure the following mods are installed and enabled:
- ✅ **Sodium** (0.6.9+)
- ✅ **NeoVoxy** (0.2.9-alpha)

## Step 4: Launch Configuration

### 4.1 First Launch

1. Close the "Edit Instance" window
2. Double-click the instance to launch
3. Wait for Minecraft to start (first launch may take longer)
4. If you encounter any crashes, check the logs (see Troubleshooting section)

### 4.2 In-Game NeoVoxy Configuration

Once in-game:

1. Create or load a world
2. Access NeoVoxy settings (check key bindings or mod menu)
3. Configure render distance and LOD settings
4. Test voxelization and chunk rendering

## Step 5: Testing Checklist

### Basic Functionality Tests

- [ ] **Mod loads without crashes**
- [ ] **NeoVoxy appears in mod list** (check via Mod Menu if installed)
- [ ] **World loads successfully**
- [ ] **Chunk voxelization executes** (check for distant terrain rendering)
- [ ] **No console errors during normal gameplay**
- [ ] **Shader compilation succeeds** (check logs for GL errors)
- [ ] **Block model textures render correctly**
- [ ] **Fluid rendering works** (test with water/lava)
- [ ] **Biome tinting applies correctly** (grass, foliage, water colors)

### Performance Tests

- [ ] **FPS is stable during chunk loading**
- [ ] **Memory usage is reasonable** (press F3 in-game)
- [ ] **No memory leaks over extended play sessions**
- [ ] **Voxelization performance is acceptable** (check taskbar progress if on Windows)

### Edge Case Tests

- [ ] **Leaves render correctly** (RenderType.solid override)
- [ ] **Translucent blocks work** (glass, stained glass)
- [ ] **Cutout blocks work** (doors, trapdoors)
- [ ] **Block entities handle correctly** (or are skipped appropriately)
- [ ] **Biome transitions are smooth**
- [ ] **LOD mipmap levels display correctly**

## Troubleshooting

### Viewing Logs

To check for errors:

1. Right-click the instance in Prism Launcher
2. Select **"Folder"** → **"View Logs"**
3. Open `latest.log` to see runtime errors

### Common Issues

#### Issue: Crash on Launch

**Symptoms:** Game crashes before reaching main menu

**Possible Causes:**
1. Incompatible Sodium version (check you have NeoForge version, not Fabric)
2. Java version mismatch (ensure Java 21+)
3. Missing NeoForge dependencies

**Fix:**
- Check `crash-reports/` folder for crash details
- Verify Sodium is the NeoForge version
- Ensure NeoForge 21.1+ is installed

#### Issue: Black/Missing Textures

**Symptoms:** Blocks render black or pink/purple checkerboard

**Possible Causes:**
1. Texture GL ID access not implemented (known limitation - see `ModelTextureBakery.java:231-234`)
2. Shader compilation failures
3. Block atlas binding issues

**Fix:**
- Check logs for GL errors
- Check `blockTextureId` TODO implementation status
- Consider implementing mixin accessor for `AbstractTexture.getId()`

#### Issue: Reflection Errors

**Symptoms:** Errors mentioning `PalettedContainer`, `Data`, `palette`, or `storage`

**Possible Causes:**
- Minecraft internals changed in update/patch
- NeoForge remapping differences

**Fix:**
- Check `WorldConversionFactory.java` reflection initialization (lines 29-42)
- Verify field names match current Minecraft version
- Check console for `IllegalAccessException` or `NoSuchFieldException`

#### Issue: FOV/Camera Problems

**Symptoms:** Incorrect field of view or camera rendering

**Possible Causes:**
- FOV calculation fallback in `NeoVoxyRenderSystem.java:385` may not account for modifiers

**Fix:**
- Consider implementing `ViewportEvent.ComputeFov` hook for accurate FOV
- Check if other mods are modifying FOV

#### Issue: Mipmap Issues

**Symptoms:** LOD textures look incorrect at distance

**Possible Causes:**
- Hardcoded `maxMipLevel = 4` in `ModelStore.java:35` may not match actual atlas

**Fix:**
- Try adjusting the hardcoded value (2, 4, or 6)
- Implement reflection to access `TextureAtlas.maxMipLevel`
- Check in-game video settings for mipmap level configuration

## Known Limitations (Ported from Fabric)

The following are known limitations in this NeoForge port:

1. **PalettedContainer.Data Access**
   - Uses reflection due to package-private visibility
   - Functional but may have performance overhead
   - Location: `WorldConversionFactory.java:24-66`

2. **TextureAtlas.maxMipLevel**
   - Hardcoded to `4` (field is now private)
   - May need adjustment based on user settings
   - Location: `ModelStore.java:33-35`

3. **BlockColor Access**
   - Uses reflection to access private `blockColors` field
   - Location: `ModelFactory.java:741-742`

4. **Texture GL ID Access**
   - Stubbed out, currently not implemented
   - Will fail at runtime if code path is reached
   - Location: `ModelTextureBakery.java:231-234`
   - **Fix Required:** Implement mixin accessor for texture GL IDs

5. **FOV Calculation**
   - Uses fallback `options.fov().get()` instead of `GameRenderer.getFov()`
   - May not account for FOV modifiers from other mods
   - Location: `NeoVoxyRenderSystem.java:384-386`

## Advanced Testing

### Debug Mode

To enable verbose logging for debugging:

1. Edit `options.txt` in the instance folder
2. Add NeoVoxy-specific debug flags (if implemented)
3. Restart Minecraft

### Profiling

To profile performance:

1. Use Minecraft's built-in profiler (press F3 + L)
2. Use external tools like JProfiler or VisualVM
3. Monitor taskbar progress on Windows for voxelization status

### Shader Debugging

To check shader compilation:

1. Monitor logs for GL shader errors
2. Check `resources/assets/neovoxy/shaders/` for GLSL source
3. Use OpenGL debugging tools (apitrace, RenderDoc)

## Reporting Issues

When reporting issues, please include:

1. **Crash report** (from `crash-reports/` folder)
2. **Latest log** (from `logs/latest.log`)
3. **Mod list** (all installed mods and versions)
4. **System information** (Java version, GPU, OS)
5. **Steps to reproduce**
6. **Expected vs actual behavior**

## Next Steps After Testing

Once basic testing is complete:

1. **Address Runtime TODOs:**
   - Implement texture GL ID access (ModelTextureBakery.java:231-234)
   - Consider FOV event integration (NeoVoxyRenderSystem.java:385)
   - Test maxMipLevel value across different texture packs

2. **Optimize Reflection:**
   - Profile PalettedContainer.Data access performance
   - Consider caching or alternative approaches

3. **Expand Testing:**
   - Test with various texture packs
   - Test with complementary mods (shaders, optimization mods)
   - Test in multiplayer environments

4. **Documentation:**
   - Document any new issues discovered
   - Update inline TODO comments as issues are resolved
   - Create user-facing documentation for NeoVoxy features

---

**Happy Testing!**

For issues or questions about this NeoForge port, use the [NeoVoxy issue tracker](https://github.com/EyeCrasher07/NeoVoxy/issues).
