package me.cortex.voxy.client.iris;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import kroppeb.stareval.function.FunctionReturn;
import kroppeb.stareval.function.Type;
import me.cortex.voxy.client.core.IrisVoxyRenderPipeline;
import me.cortex.voxy.client.core.rendering.util.LightMapHelper;
import me.cortex.voxy.client.mixin.iris.CustomUniformsAccessor;
import me.cortex.voxy.client.mixin.iris.IrisRenderingPipelineAccessor;
import me.cortex.voxy.common.Logger;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.gl.image.ImageHolder;
import net.irisshaders.iris.gl.sampler.GlSampler;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import net.irisshaders.iris.gl.texture.InternalTextureFormat;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.gl.uniform.*;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.irisshaders.iris.uniforms.custom.cached.*;
import org.joml.*;
import org.lwjgl.system.MemoryUtil;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

import static org.lwjgl.opengl.ARBDirectStateAccess.glBindTextureUnit;
import static org.lwjgl.opengl.ARBUniformBufferObject.glBindBufferBase;
import static org.lwjgl.opengl.GL33C.glBindSampler;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;

public class IrisVoxyRenderPipelineData {
    public IrisVoxyRenderPipeline thePipeline;
    public final int[] opaqueDrawTargets;
    public final int[] translucentDrawTargets;
    private final String opaquePatch;
    private final String translucentPatch;
    private final StructLayout uniforms;
    private final Runnable blendingSetup;
    private final ImageSet imageSet;
    private final SSBOSet ssboSet;
    public final boolean renderToVanillaDepth;
    public final float[] resolutionScale;
    public final String TAA;
    public final boolean useViewportDims;
    public final boolean deferTranslucency;
    public boolean skipShaderDepthHackFix;

    private IrisVoxyRenderPipelineData(IrisShaderPatch patch, int[] opaqueDrawTargets, int[] translucentDrawTargets, StructLayout uniformSet, Runnable blendingSetup, ImageSet imageSet, SSBOSet ssboSet) {
        this.opaqueDrawTargets = opaqueDrawTargets;
        this.translucentDrawTargets = translucentDrawTargets;
        this.opaquePatch = patch.getPatchOpaqueSource();
        this.translucentPatch = patch.getPatchTranslucentSource();
        this.uniforms = uniformSet;
        this.blendingSetup = blendingSetup;
        this.imageSet = imageSet;
        this.ssboSet = ssboSet;
        this.renderToVanillaDepth = patch.emitToVanillaDepth();
        this.TAA = patch.getTAAShift();
        this.resolutionScale = patch.getRenderScale();
        this.useViewportDims = patch.useViewportDims();
        this.deferTranslucency = patch.deferedTranslucentRendering();
        this.skipShaderDepthHackFix = patch.skipShaderDepthHackFix();
    }

    public SSBOSet getSsboSet() {
        return this.ssboSet;
    }

    public ImageSet getImageSet() {
        return this.imageSet;
    }

    public StructLayout getUniforms() {
        return this.uniforms;
    }
    public Runnable getBlender() {
        return this.blendingSetup;
    }
    public String opaqueFragPatch() {
        return this.opaquePatch;
    }
    public String translucentFragPatch() {
        return this.translucentPatch;
    }


    public static IrisVoxyRenderPipelineData buildPipeline(IrisRenderingPipeline ipipe, IrisShaderPatch patch, CustomUniforms cu, ShaderStorageBufferHolder ssboHolder) {
        var uniforms = createUniformLayoutStructAndUpdater(createUniformSet(cu, patch));


        var imageSet = createImageSet(ipipe, patch);

        var ssboSet = createSSBOLayouts(patch.getSSBOs(), ssboHolder);

        var opaqueDrawTargets = getDrawBuffers(patch.getOpqaueTargets(), ipipe.getFlippedAfterPrepare(), ((IrisRenderingPipelineAccessor)ipipe).getRenderTargets());
        var translucentDrawTargets = getDrawBuffers(patch.getTranslucentTargets(), ipipe.getFlippedAfterPrepare(), ((IrisRenderingPipelineAccessor)ipipe).getRenderTargets());



        //TODO: need to transform the string patch with the uniform decleration aswell as sampler declerations
        return new IrisVoxyRenderPipelineData(patch, opaqueDrawTargets, translucentDrawTargets, uniforms, patch.createBlendSetup(), imageSet, ssboSet);
    }

    private static int[] getDrawBuffers(int[] targets, ImmutableSet<Integer> stageWritesToAlt, RenderTargets rt) {
        int[] targetTextures = new int[targets.length];
        for(int i = 0; i < targets.length; i++) {
            RenderTarget target = rt.getOrCreate(targets[i]);
            int textureId = stageWritesToAlt.contains(targets[i]) ? target.getAltTexture() : target.getMainTexture();
            targetTextures[i] = textureId;
        }
        return targetTextures;
    }


    private static String convertToGlslType(UniformType type) {
        return switch (type) {
            case INT -> "int";
            case FLOAT -> "float";
            case MAT3 -> "mat3";
            case MAT4 -> "mat4";
            case VEC2 -> "vec2";
            case VEC2I -> "ivec2";
            case VEC3 -> "vec3";
            case VEC3I -> "ivec3";
            case VEC4 -> "vec4";
            case VEC4I -> "ivec4";
        };
    }

    public boolean shouldDeferTranslucency() {
        return false;
    }

    public record StructLayout(int size, String layout, LongConsumer updater) {}
    private static StructLayout createUniformLayoutStructAndUpdater(List<UniformWritingHolder> uniforms) {
        if (uniforms.size() == 0) {
            return null;
        }

        List<UniformWritingHolder>[] ordering = new List[]{new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()};

        //Creates an optimial struct layout for the uniforms
        for (var uniform : uniforms) {
            int order = getUniformOrdering(uniform.type);
            ordering[order].add(uniform);
        }

        //Emit the ordering, note this is not optimial, but good enough, e.g. if have even number of align 2, emit that after align 4
        int pos = 0;
        Int2ObjectLinkedOpenHashMap<UniformWritingHolder> layout = new Int2ObjectLinkedOpenHashMap<>();
        for (var uniform : ordering[0]) {//Emit exact align 4
            layout.put(pos, uniform); pos += getSizeAndAlignment(uniform.type)>>5;
        }
        if (!ordering[1].isEmpty() && (ordering[1].size()&1)==0) {
            //Emit all the align 2 as there is an even number of them
            for (var uniform : ordering[1]) {
                layout.put(pos, uniform); pos += getSizeAndAlignment(uniform.type)>>5;
            }
            ordering[1].clear();
        }
        //Emit align 3
        for (var uniform : ordering[2]) {//Emit size odd, alignment must be 4
            layout.put(pos, uniform); pos += getSizeAndAlignment(uniform.type)>>5;
            //We must get a size 1 to pad to align 4
            if (!ordering[3].isEmpty()) {//Size 1
                uniform = ordering[3].removeFirst();
                layout.put(pos, uniform); pos += getSizeAndAlignment(uniform.type)>>5;
            } else {//Padding must be injected
                pos += 1;
            }
        }

        //Emit align 2
        for (var uniform : ordering[1]) {
            layout.put(pos, uniform); pos += getSizeAndAlignment(uniform.type)>>5;
        }

        //Emit align 1
        for (var uniform : ordering[3]) {
            layout.put(pos, uniform); pos += getSizeAndAlignment(uniform.type)>>5;
        }

        if (layout.size()!=uniforms.size()) {
            throw new IllegalStateException();
        }

        //We have our ordering and aligned offsets, generate an updater aswell as the layout

        String structLayout;
        {
            StringBuilder struct = new StringBuilder("{\n");
            for (var pair : layout.int2ObjectEntrySet()) {
                struct.append("\t").append(convertToGlslType(pair.getValue().type)).append(" ").append(pair.getValue().name).append(";\n");
            }
            struct.append("}");
            structLayout = struct.toString();
        }

        LongConsumer updater;
        {
            LongConsumer[] updaters = new LongConsumer[uniforms.size()];
            int i = 0;
            for (var pair : layout.int2ObjectEntrySet()) {
                updaters[i++] = pair.getValue().writingFactory.get(pair.getIntKey()*4L);
            }

            updater = ptr -> {
                for (var u : updaters) {
                    u.accept(ptr);
                }
            };//Writes all the uniforms to the locations
        }
        return new StructLayout(pos*4, structLayout, updater);//*4 since each slot is 4 bytes
    }

    private static LongConsumer createWriter(long offset, FunctionReturn ret, CachedUniform uniform) {
        if (uniform instanceof BooleanCachedUniform bcu) {
            return ptr->{ptr += offset;
                bcu.writeTo(ret);
                MemoryUtil.memPutInt(ptr, ret.booleanReturn?1:0);
            };
        } else if (uniform instanceof FloatCachedUniform fcu) {
            return ptr->{ptr += offset;
                fcu.writeTo(ret);
                MemoryUtil.memPutFloat(ptr, ret.floatReturn);
            };
        } else if (uniform instanceof IntCachedUniform icu) {
            return ptr->{ptr += offset;
                icu.writeTo(ret);
                MemoryUtil.memPutInt(ptr, ret.intReturn);
            };
        } else if (uniform instanceof Float2VectorCachedUniform v2fcu) {
            return ptr->{ptr += offset;
                v2fcu.writeTo(ret);
                ((Vector2f)ret.objectReturn).getToAddress(ptr);
            };
        } else if (uniform instanceof Float3VectorCachedUniform v3fcu) {
            return ptr->{ptr += offset;
                v3fcu.writeTo(ret);
                ((Vector3f)ret.objectReturn).getToAddress(ptr);
            };
        } else if (uniform instanceof Float4VectorCachedUniform v4fcu) {
            return ptr->{ptr += offset;
                v4fcu.writeTo(ret);
                ((Vector4f)ret.objectReturn).getToAddress(ptr);
            };
        } else if (uniform instanceof Int2VectorCachedUniform v2icu) {
            return ptr->{ptr += offset;
                v2icu.writeTo(ret);
                ((Vector2i)ret.objectReturn).getToAddress(ptr);
            };
        } else if (uniform instanceof Int3VectorCachedUniform v3icu) {
            return ptr->{ptr += offset;
                v3icu.writeTo(ret);
                ((Vector3i)ret.objectReturn).getToAddress(ptr);
            };
        } else if (uniform instanceof Float4MatrixCachedUniform f4mcu) {
            return ptr->{ptr += offset;
                f4mcu.writeTo(ret);
                ((Matrix4f)ret.objectReturn).getToAddress(ptr);
            };
        } else {
            throw new IllegalStateException("Unknown uniform type " + uniform.getClass().getName());
        }
    }


    private static int P(int size, int align) {
        return size<<5|align;
    }
    private static int getSizeAndAlignment(UniformType type) {
        return switch (type) {
            case INT, FLOAT -> P(1,1);//Size, Alignment
            case MAT3 -> P(4+4+3,4);//is funky as each row is a vec3 padded to a vec4
            case MAT4 -> P(4*4,4);
            case VEC2, VEC2I -> P(2,2);
            case VEC3, VEC3I -> P(3,4);
            case VEC4, VEC4I -> P(4,4);
        };
    }
    private static int getUniformOrdering(UniformType type) {
        return switch (type) {
            case MAT4, VEC4, VEC4I -> 0;
            case VEC2, VEC2I -> 1;
            case VEC3, VEC3I, MAT3 -> 2;
            case INT, FLOAT -> 3;
        };
    }

    private record UniformWritingHolder(String name, UniformType type, Long2ObjectFunction<LongConsumer> writingFactory) {

    }
    private static List<UniformWritingHolder> createUniformSet(CustomUniforms cu, IrisShaderPatch patch) {
        //This is a fking awful hack... but it works thinks

        List<UniformWritingHolder> uniforms = new ArrayList<>();
        Set<String> seenUniforms = new HashSet<>();
        DynamicLocationalUniformHolder uniformBuilder = new DynamicLocationalUniformHolder() {
            @Override
            public DynamicLocationalUniformHolder uniform1i(UniformUpdateFrequency updateFrequency, String name, IntSupplier value) {
                return this.uniform1i(name, value, null);
            }

            @Override
            public DynamicLocationalUniformHolder uniform1i(String name, IntSupplier value, ValueUpdateNotifier notifier) {
                this.injectDynamicUniformType(name, UniformType.INT, offset->{
                    return ptr->{
                        MemoryUtil.memPutInt(ptr+offset, value.getAsInt());
                    };
                });
                return this;
            }


            @Override
            public DynamicLocationalUniformHolder uniform1f(UniformUpdateFrequency updateFrequency, String name, FloatSupplier value) {
                return this.uniform1f(name, value, null);
            }

            @Override
            public DynamicLocationalUniformHolder uniform1f(String name, FloatSupplier value, ValueUpdateNotifier notifier) {
                this.injectDynamicUniformType(name, UniformType.FLOAT, offset->{
                    return ptr->{
                        MemoryUtil.memPutFloat(ptr+offset, value.getAsFloat());
                    };
                });
                return this;
            }


            @Override
            public DynamicLocationalUniformHolder uniform3f(UniformUpdateFrequency updateFrequency, String name, Supplier<Vector3f> value) {
                return this.uniform3f(name, value, null);
            }

            @Override
            public DynamicLocationalUniformHolder uniform3f(String name, Supplier<Vector3f> value, ValueUpdateNotifier notifier) {
                this.injectDynamicUniformType(name, UniformType.VEC3, offset->{
                    return ptr->{
                      value.get().getToAddress(ptr+offset);
                    };
                });
                return this;
            }

            private void injectDynamicUniformType(String name, UniformType type, Long2ObjectFunction<LongConsumer> supplier) {
                var names = patch.getUniformList();
                for (int i = 0; i < names.length; i++) {
                    if (names[i].equals(name)) {
                        if (!seenUniforms.add(name)) {
                            throw new IllegalArgumentException("Already added uniform: " + name);
                        }
                        uniforms.add(new UniformWritingHolder(name, type, supplier));
                        break;
                    }
                }
            }

            @Override
            public DynamicLocationalUniformHolder addDynamicUniform(Uniform uniform, ValueUpdateNotifier valueUpdateNotifier) {
                throw new IllegalStateException("Type not implemented for uniform: " + uniform);
                //return this;
            }
            //TODO: override the uniform1b call to specialcase booleans

            @Override
            public LocationalUniformHolder addUniform(UniformUpdateFrequency uniformUpdateFrequency, Uniform uniform) {
                //TODO: error/log the type of uniform that was added (and its location)

                if (uniform instanceof BooleanUniform bu) {
                    //TODO: need to assert the loc is from a actually valid location
                    int loc = bu.getLocation();
                    var ul = patch.getUniformList();
                    if (loc<ul.length) {
                        var uniformName = ul[loc];
                    }
                }
                return this;
            }

            @Override
            public OptionalInt location(String uniformName, UniformType uniformType) {
                //Yes am aware how performant inefficent this is... just dont care tbh since is on setup and is small
                var names = patch.getUniformList();
                for (int i = 0; i < names.length; i++) {
                    if (names[i].equals(uniformName)) {
                        return OptionalInt.of(i);//Have a base uniform offset of 10
                    }
                }
                return OptionalInt.empty();
            }

            @Override
            public UniformHolder externallyManagedUniform(String s, UniformType uniformType) {
                return null;
            }
        };
        CommonUniforms.addDynamicUniforms(uniformBuilder, FogMode.PER_FRAGMENT);
        cu.assignTo(uniformBuilder);
        cu.mapholderToPass(uniformBuilder, patch);

        FunctionReturn cachedReturn = new FunctionReturn();
        ((CustomUniformsAccessor)cu).getLocationMap().get(patch).object2IntEntrySet().forEach(entry-> {
            if (!seenUniforms.add(entry.getKey().getName())) {
                throw new IllegalArgumentException("Already added uniform: " + entry.getKey().getName());
            }
            uniforms.add(new UniformWritingHolder(entry.getKey().getName(), Type.convert(entry.getKey().getType()),offset->createWriter(offset, cachedReturn, entry.getKey())));
        });

        if (uniforms.size() != patch.getUniformList().length) {
            Set<String> uniformsUnseen = new HashSet<>(List.of(patch.getUniformList()));
            for (var uniform : uniforms) {
                uniformsUnseen.remove(uniform.name);
            }
            Logger.error("The following uniforms could not be found: [" + uniformsUnseen.stream().sorted(String::compareToIgnoreCase).collect(Collectors.joining(","))+"]");
        }
        //In _theory_ this should work?
        return uniforms;
    }

    private record TextureWSampler(String name, IntSupplier texture, int sampler) { }
    public record ImageSet(String layout, IntConsumer bindingFunction) {

    }
    private static ImageSet createImageSet(IrisRenderingPipeline ipipe, IrisShaderPatch patch) {
        var samplerDataSet = patch.getSamplerSet();
        if (samplerDataSet == null) return null;
        Set<String> samplerNameSet = new LinkedHashSet<>(samplerDataSet.keySet());
        if (samplerNameSet.isEmpty()) return null;
        Set<TextureWSampler> samplerSet = new LinkedHashSet<>();

        //Built up the external samplers list
        Map<String, IntSupplier> externalTextures = new HashMap<>();
        externalTextures.put("lightmap", LightMapHelper::getLightmapTextureId);


        SamplerHolder samplerBuilder = new SamplerHolder() {
            @Override
            public boolean hasSampler(String s) {
                return samplerNameSet.contains(s);
            }

            public boolean hasSampler(String... names) {
                for (var name : names) {
                    if (samplerNameSet.contains(name)) return true;
                }
                return false;
            }

            private String name(String... names) {
                for (var name : names) {
                    if (samplerNameSet.contains(name)) return name;
                }
                return null;
            }

            @Override
            public boolean addDefaultSampler(TextureType type, IntSupplier texture, ValueUpdateNotifier notifier, GlSampler sampler, String... names) {
                Logger.error("Unsupported default sampler");
                return false;
            }

            @Override
            public boolean addDynamicSampler(TextureType type, IntSupplier texture, GlSampler sampler, String... names) {
                return this.addDynamicSampler(type, texture, null, sampler, names);
            }

            @Override
            public boolean addDynamicSampler(TextureType type, IntSupplier texture, ValueUpdateNotifier notifier, GlSampler sampler, String... names) {
                if (!this.hasSampler(names)) return false;
                samplerSet.add(new TextureWSampler(this.name(names), texture, sampler!=null?sampler.getId():-1));
                return true;
            }

            @Override
            public void addExternalSampler(int texture, String... names) {
                if (!this.hasSampler(names)) return;
                var name = this.name(names);
                var ex = externalTextures.get(name);
                if (ex != null) {
                    samplerSet.add(new TextureWSampler(name, ex, 0));//unbind any sampler and use the externalTextureSupplier
                } else {
                    samplerSet.add(new TextureWSampler(name, () -> texture, -1));
                }
            }
        };

        //Unsupported
        ImageHolder imageBuilder = new ImageHolder() {
            @Override
            public boolean hasImage(String s) {
                return false;
            }

            @Override
            public void addTextureImage(IntSupplier intSupplier, InternalTextureFormat internalTextureFormat, String s) {

            }
        };

        ipipe.addGbufferOrShadowSamplers(samplerBuilder, imageBuilder, ipipe::getFlippedAfterPrepare, false, true, true, false);

        //samplerSet contains our samplers
        if (samplerSet.size() != samplerNameSet.size()) {
            Logger.error("Did not find all requested samplers. Found [" + samplerSet.stream().map(a->a.name).collect(Collectors.joining(", ")) + "] expected " + samplerNameSet);
        }

        //TODO: generate a layout (defines) for all the samplers with the correct types

        StringBuilder builder = new StringBuilder();
        TextureWSampler[] samplers = new TextureWSampler[samplerSet.size()];
        int i = 0;
        for (var entry : samplerSet) {
            samplers[i]=entry;

            String samplerType = samplerDataSet.get(entry.name);
            builder.append("layout(binding=(BASE_SAMPLER_BINDING_INDEX+").append(i).append(")) uniform ").append(samplerType).append(" ").append(entry.name).append(";\n");
            i++;
        }


        IntConsumer bindingFunction = base->{
            for (int j = 0; j < samplers.length; j++) {
                int unit = j+base;
                var ts = samplers[j];
                glBindTextureUnit(unit, ts.texture.getAsInt());
                int sampler = ts.sampler;
                if (sampler != -1) {
                    glBindSampler(unit, sampler);
                }//TODO: might need to bind sampler 0
            }
        };
        return new ImageSet(builder.toString(), bindingFunction);
    }

    public record SSBOSet(String layout, IntConsumer bindingFunction){}
    private record SSBOBinding(int irisIndex, int bindingOffset) {}
    private static SSBOSet createSSBOLayouts(Int2ObjectMap<String> ssbos, ShaderStorageBufferHolder ssboStore) {
        if (ssboStore == null) return null;//If there is no store, there cannot be any ssbos
        if (ssbos.isEmpty()) return null;
        String header = "";
        if (ssbos.containsKey(-1)) header = ssbos.remove(-1);
        StringBuilder builder = new StringBuilder(header);
        builder.append("\n");
        SSBOBinding[] bindings = new SSBOBinding[ssbos.size()];
        int i = 0;
        for (var entry : ssbos.int2ObjectEntrySet()) {
            var val = entry.getValue();
            bindings[i] = new SSBOBinding(entry.getIntKey(), i);
            builder.append("layout(binding = (BUFFER_BINDING_INDEX_BASE+").append(i).append(")) restrict buffer IrisBufferBinding").append(i);
            builder.append(" ").append(val).append(";\n");
            i++;
        }
        //ssboStore.getBufferIndex()
        IntConsumer bindingFunction = base->{
            for (var binding : bindings) {
                glBindBufferBase(GL_SHADER_STORAGE_BUFFER, base+binding.bindingOffset, ssboStore.getBufferIndex(binding.irisIndex));
            }
        };
        return new SSBOSet(builder.toString(), bindingFunction);
    }
}
