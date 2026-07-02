package me.cortex.voxy.client.iris;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import me.cortex.voxy.common.Logger;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import org.lwjgl.opengl.ARBDrawBuffersBlend;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntSupplier;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL33.*;

public class IrisShaderPatch {
    public static final int VERSION = ((IntSupplier)()->1).getAsInt();
    public static final int SHADER_DEFINE_VERSION = 2;


    private static final class SSBODeserializer implements JsonDeserializer<Int2ObjectOpenHashMap<String>> {
        @Override
        public Int2ObjectOpenHashMap<String> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            Int2ObjectOpenHashMap<String> ret = new Int2ObjectOpenHashMap<>();
            if (json==null) return null;
            try {
                for (var entry : json.getAsJsonObject().entrySet()) {
                    ret.put(Integer.parseInt(entry.getKey()), entry.getValue().getAsString());
                }
            } catch (Exception e) {
                Logger.error(e);
            }
            return ret;
        }
    }
    private static final class SamplerDeserializer implements JsonDeserializer<Object2ObjectLinkedOpenHashMap<String, String>> {
        @Override
        public Object2ObjectLinkedOpenHashMap<String, String> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            Object2ObjectLinkedOpenHashMap<String, String> ret = new Object2ObjectLinkedOpenHashMap<>();
            if (json==null) return null;
            try {
                if (json.isJsonArray()) {
                    for (var entry : json.getAsJsonArray()) {
                        var name = entry.getAsString();
                        var type = "sampler2D";
                        if (name.matches("shadowtex")) {
                            type = "sampler2DShadow";
                        }
                        ret.put(name, type);
                    }
                } else {
                    for (var entry : json.getAsJsonObject().entrySet()) {
                        String type = "sampler2D";
                        if (entry.getValue().isJsonNull()) {
                            if (entry.getKey().matches("shadowtex")) {
                                type = "sampler2DShadow";
                            }
                        } else {
                            type = entry.getValue().getAsString();
                        }
                        ret.put(entry.getKey(), type);
                    }
                }
            } catch (Exception e) {
                Logger.error(e);
            }
            return ret;
        }
    }

    public record BlendState(int buffer, boolean off, int sRGB, int dRGB, int sA, int dA) {
        public static BlendState ALL_OFF = new BlendState(-1, true, 0,0,0,0);
    }


    private static final class BlendStateDeserializer implements JsonDeserializer<Int2ObjectMap<BlendState>> {
        private static int parseType(String type) {
            type = type.toUpperCase();
            if (!type.startsWith("GL_")) {
                type = "GL_"+type;
            }
            return switch (type) {
                case "GL_ZERO" -> GL_ZERO;
                case "GL_ONE" -> GL_ONE;
                case "GL_SRC_COLOR" -> GL_SRC_COLOR;
                case "GL_ONE_MINUS_SRC_COLOR" -> GL_ONE_MINUS_SRC_COLOR;
                case "GL_SRC_ALPHA" -> GL_SRC_ALPHA;
                case "GL_ONE_MINUS_SRC_ALPHA" -> GL_ONE_MINUS_SRC_ALPHA;
                case "GL_DST_ALPHA" -> GL_DST_ALPHA;
                case "GL_ONE_MINUS_DST_ALPHA" -> GL_ONE_MINUS_DST_ALPHA;
                case "GL_DST_COLOR" -> GL_DST_COLOR;
                case "GL_ONE_MINUS_DST_COLOR" -> GL_ONE_MINUS_DST_COLOR;
                case "GL_SRC_ALPHA_SATURATE" -> GL_SRC_ALPHA_SATURATE;
                case "GL_SRC1_COLOR" -> GL_SRC1_COLOR;
                case "GL_ONE_MINUS_SRC1_COLOR" -> GL_ONE_MINUS_SRC1_COLOR;
                case "GL_ONE_MINUS_SRC1_ALPHA" -> GL_ONE_MINUS_SRC1_ALPHA;
                default -> {
                    Logger.error("Unknown blend option " + type);
                    yield -1;
                }
            };
        }
        @Override
        public Int2ObjectMap<BlendState> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json==null) return null;
            Int2ObjectMap<BlendState> ret = new Int2ObjectOpenHashMap<>();
            try {
                if (json.isJsonPrimitive()) {
                    if (json.getAsString().equalsIgnoreCase("off")) {
                        ret.put(-1, BlendState.ALL_OFF);
                        return ret;
                    }
                } else if (json.isJsonObject()) {
                    for (var entry : json.getAsJsonObject().entrySet()) {
                        int buffer = Integer.parseInt(entry.getKey());
                        BlendState state = null;
                        var val = entry.getValue();
                        List<String> bs = null;
                        if (val.isJsonArray()) {
                            bs = val.getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList();
                        } else if (val.isJsonPrimitive()) {
                            var str = val.getAsString();
                            if (str.equalsIgnoreCase("off")) {
                                state = new BlendState(buffer, true, 0,0,0,0);
                            } else {
                                var parts = str.split(" ");
                                if (parts.length < 4) {
                                    state = new BlendState(buffer, true, -1, -1, -1, -1);
                                } else {
                                    bs = List.of(parts);
                                }
                            }
                        } else {
                            Logger.error("Unknown blend state "+val);
                            state = null;
                        }
                        if (bs != null) {
                            int[] v = bs.stream().mapToInt(BlendStateDeserializer::parseType).toArray();
                            state = new BlendState(buffer, false, v[0], v[1], v[2], v[3]);
                        }
                        ret.put(buffer, state);
                    }
                    return ret;
                }
            } catch (Exception e) {
                Logger.error(e);
            }
            Logger.error("Failed to parse blend state: " + json);
            return ret;
        }
    }

    private static class PatchGson {
        public int version;//TODO maybe replace with semver?
        public int[] opaqueDrawBuffers;
        public int[] translucentDrawBuffers;
        public String[] uniforms;
        @JsonAdapter(SamplerDeserializer.class)
        public Object2ObjectLinkedOpenHashMap<String, String> samplers;
        public String opaquePatchData;
        public String translucentPatchData;
        @JsonAdapter(SSBODeserializer.class)
        public Int2ObjectOpenHashMap<String> ssbos;
        @JsonAdapter(BlendStateDeserializer.class)
        public Int2ObjectOpenHashMap<BlendState> blending;
        public String taaOffset;
        public boolean excludeLodsFromVanillaDepth;
        public float[] renderScale;
        public boolean useViewportDims;
        public boolean skipShaderDepthHackFix;
        //public boolean deferTranslucentRendering;
        public String checkValid() {
            if (this.blending != null) {
                int i = 0;
                for (BlendState state : this.blending.values()) {
                    if (state.buffer != -1 && (state.buffer<0||this.translucentDrawBuffers.length<=state.buffer)) {
                        if (state.buffer<0) {
                            return "Blending buffer is <0 at index: " + i;
                        } else {
                            return "Blending buffer index out of bounds at "+i+" was "+state.buffer+" maximum is " +(this.translucentDrawBuffers.length-1);
                        }
                    }
                    i++;
                }
            }
            if (this.opaquePatchData == null) {
                return "Opaque patch data is null";
            }
            if (this.uniforms == null) {
                return "Uniforms are null";
            }
            if (this.opaqueDrawBuffers == null) {
                return "Opaque draw buffers are null";
            }
            if (this.translucentDrawBuffers == null) {
                return "Translucent draw buffers are null";
            }
            return null;
        }
    }



    private final PatchGson patchData;
    private final ShaderPack pack;
    private final Int2ObjectMap<String> ssbos;
    private IrisShaderPatch(PatchGson patchData, ShaderPack pack) {
        this.patchData = patchData;
        this.pack = pack;

        if (patchData.ssbos == null) {
            this.ssbos = new Int2ObjectOpenHashMap<>();
        } else {
            this.ssbos = patchData.ssbos;
        }
    }

    public boolean useViewportDims() {
        return this.patchData.useViewportDims;
    }

    public boolean skipShaderDepthHackFix() { return this.patchData.skipShaderDepthHackFix; }
    public Int2ObjectMap<String> getSSBOs() {
        return new Int2ObjectLinkedOpenHashMap<>(this.ssbos);
    }
    public String getPatchOpaqueSource() {
        return this.patchData.opaquePatchData;
    }
    public String getPatchTranslucentSource() {
        return this.patchData.translucentPatchData;
    }
    public String getTAAShift() {
        return this.patchData.taaOffset;// == null?"{return vec2(0.0);}":this.patchData.taaOffset;
    }
    public String[] getUniformList() {
        return this.patchData.uniforms;
    }
    public Object2ObjectLinkedOpenHashMap<String, String> getSamplerSet() {
        return this.patchData.samplers;
    }


    public int[] getOpqaueTargets() {
        return this.patchData.opaqueDrawBuffers;
    }

    public int[] getTranslucentTargets() {
        return this.patchData.translucentDrawBuffers;
    }

    public boolean emitToVanillaDepth() {
        return !this.patchData.excludeLodsFromVanillaDepth;
    }

    public float[] getRenderScale() {
        if (this.patchData.renderScale == null || this.patchData.renderScale.length==0) {
            return new float[]{1,1};
        }
        if (this.patchData.renderScale.length == 1) {
            return new float[]{this.patchData.renderScale[0],this.patchData.renderScale[0]};
        }
        return new float[]{Math.max(0.01f,this.patchData.renderScale[0]),Math.max(0.01f,this.patchData.renderScale[1])};
    }

    public boolean deferedTranslucentRendering() {
        return false;//this.patchData.deferTranslucentRendering;
    }

    public Runnable createBlendSetup() {
        if (this.patchData.blending == null || this.patchData.blending.isEmpty()) {
            return ()->{};//No blending change
        }
        return ()->{
            final var BS = this.patchData.blending;
            //Set inital state
            var init = BS.getOrDefault(-1, null);
            if (init != null) {
                if (init.off) {
                    glDisable(GL_BLEND);
                } else {
                    glEnable(GL_BLEND);
                    glBlendFuncSeparate(init.sRGB, init.dRGB, init.sA, init.dA);
                }
            }
            for (var entry:BS.int2ObjectEntrySet()) {
                if (entry.getIntKey() == -1) continue;
                final var s = entry.getValue();
                if (s.off) {
                    glDisablei(GL_BLEND, s.buffer);
                } else {
                    glEnablei(GL_BLEND, s.buffer);
                    //_sigh_ thanks nvidia
                    ARBDrawBuffersBlend.glBlendFuncSeparateiARB(s.buffer, s.sRGB, s.dRGB, s.sA, s.dA);
                }
            }
        };
    }

    private static final Gson GSON = new GsonBuilder()
            .excludeFieldsWithModifiers(Modifier.PRIVATE)
            .setLenient()
            .create();

    public static IrisShaderPatch makePatch(ShaderPack ipack, AbsolutePackPath directory, Function<AbsolutePackPath, String> sourceProvider) {
        String voxyPatchData = sourceProvider.apply(directory.resolve("voxy.json"));
        if (voxyPatchData == null) {//No voxy patch data in shaderpack
            return null;
        }

        //A more graceful exit on blank string
        if (voxyPatchData.isBlank()) {
            return null;
        }

        //Escape things
        voxyPatchData = voxyPatchData.replace("\\", "\\\\");

        PatchGson patchData = null;
        try {
            //TODO: basicly find any "commented out" quotation marks and escape them (if the line, when stripped starts with a // or /* then escape all quotation marks in that line)
            {
                StringBuilder builder = new StringBuilder(voxyPatchData.length());
                //Rebuild the patch, replacing commented out " with \"
                for (var line : voxyPatchData.split("\n")) {
                    int idx = line.indexOf("//");
                    if (idx != -1) {
                        builder.append(line, 0, idx);
                        builder.append(line.substring(idx).replace("\"","\\\""));
                    } else {
                        builder.append(line);
                    }
                    builder.append("\n");
                }
                voxyPatchData = builder.toString();
            }

            //Stupid chunk fade in patch (should probably just breaks
            voxyPatchData = voxyPatchData.replaceAll("void _cfi_ignoreMarker\\(\\) \\{\\}", "");

            patchData = GSON.fromJson(voxyPatchData, PatchGson.class);
            if (patchData == null) {
                throw new IllegalStateException("Voxy patch json returned null, this is most likely due to malformed json file");
            }

            {//Inject data from the auxilery files if they are present
                var opaque = sourceProvider.apply(directory.resolve("voxy_opaque.glsl"));
                if (opaque != null) {
                    Logger.info("External opaque shader patch applied");
                    patchData.opaquePatchData = opaque;
                }
                var translucent = sourceProvider.apply(directory.resolve("voxy_translucent.glsl"));
                if (translucent != null) {
                    Logger.info("External translucent shader patch applied");
                    patchData.translucentPatchData = translucent;
                }
                //This might be ok? not.. sure if is nice or not
                var taa = sourceProvider.apply(directory.resolve("voxy_taa.glsl"));
                if (taa != null) {
                    Logger.info("External taa shader patch applied");
                    patchData.taaOffset = taa;
                }
            }

            var invalidPatchDataReason = patchData.checkValid();
            if (invalidPatchDataReason!=null) {
                throw new IllegalStateException("voxy json patch not valid: " + invalidPatchDataReason);
            }
        } catch (Exception e) {
            patchData = null;
            Logger.error("Failed to parse patch data gson, dumping json",e);
            try {
                Files.writeString(Path.of("JSON_DUMP.txt"), voxyPatchData);
            } catch (IOException j) {
                throw new RuntimeException(j);
            }
            throw new ShaderLoadError("Failed to parse patch data gson, dumping json",e);
        }
        if (patchData == null) {
            return null;
        }
        if (patchData.version != VERSION) {
            Logger.error("Shader has voxy patch data, but patch version is incorrect. expected " + VERSION + " got "+patchData.version);
            throw new IllegalStateException("Shader version mismatch expected " + VERSION + " got "+patchData.version);
        }
        return new IrisShaderPatch(patchData, ipack);
    }
}
