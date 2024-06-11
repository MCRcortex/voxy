package me.cortex.voxy.client.core.gl.shader;

import me.cortex.voxy.common.util.TrackedObject;
import org.lwjgl.opengl.GL20C;

import java.util.*;
import java.util.stream.Collectors;

import static org.lwjgl.opengl.GL20.glDeleteProgram;
import static org.lwjgl.opengl.GL20.glUseProgram;

public class Shader extends TrackedObject {
    private final int id;
    private Shader(int program) {
        id = program;
    }

    public static Builder make(IShaderProcessor... processors) {
        List<IShaderProcessor> aa = new ArrayList<>(List.of(processors));
        Collections.reverse(aa);
        IShaderProcessor applicator = (type,source)->source;
        for (IShaderProcessor processor : processors) {
            IShaderProcessor finalApplicator = applicator;
            applicator = (type, source) -> finalApplicator.process(type, processor.process(type, source));
        }
        return new Builder(applicator);
    }

    public static Builder make() {
        return new Builder((aa,source)->source);
    }

    public int id() {
        return this.id;
    }

    public void bind() {
        glUseProgram(this.id);
    }

    public void free() {
        super.free0();
        glDeleteProgram(this.id);
    }

    public static class Builder {
        private final Map<String, String> defines = new HashMap<>();
        private final Map<ShaderType, String> sources = new HashMap<>();
        private final IShaderProcessor processor;
        private Builder(IShaderProcessor processor) {
            this.processor = processor;
        }

        public Builder define(String name) {
            this.defines.put(name, "");
            return this;
        }

        public Builder define(String name, int value) {
            this.defines.put(name, Integer.toString(value));
            return this;
        }

        public Builder add(ShaderType type, String id) {
            this.addSource(type, ShaderLoader.parse(id));
            return this;
        }

        public Builder addSource(ShaderType type, String source) {
            this.sources.put(type, this.processor.process(type, source));
            return this;
        }

        public Shader compile() {
            int program = GL20C.glCreateProgram();
            int[] shaders = new int[this.sources.size()];
            {
                String defs = this.defines.entrySet().stream().map(a->"#define " + a.getKey() + " " + a.getValue() + "\n").collect(Collectors.joining());
                int i = 0;
                for (var entry : this.sources.entrySet()) {
                    String src = entry.getValue();

                    //Inject defines
                    src = src.substring(0, src.indexOf('\n')+1) +
                            defs
                            + src.substring(src.indexOf('\n')+1);

                    shaders[i++] = createShader(entry.getKey(), src);
                }
            }

            for (int i : shaders) {
                GL20C.glAttachShader(program, i);
            }
            GL20C.glLinkProgram(program);
            for (int i : shaders) {
                GL20C.glDetachShader(program, i);
                GL20C.glDeleteShader(i);
            }
            printProgramLinkLog(program);
            verifyProgramLinked(program);
            return new Shader(program);
        }


        private static void printProgramLinkLog(int program) {
            String log = GL20C.glGetProgramInfoLog(program);

            if (!log.isEmpty()) {
                System.err.println(log);
            }
        }

        private static void verifyProgramLinked(int program) {
            int result = GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS);

            if (result != GL20C.GL_TRUE) {
                throw new RuntimeException("Shader program linking failed, see log for details");
            }
        }

        private static int createShader(ShaderType type, String src) {
            int shader = GL20C.glCreateShader(type.gl);
            GL20C.glShaderSource(shader, src);
            GL20C.glCompileShader(shader);
            String log = GL20C.glGetShaderInfoLog(shader);

            if (!log.isEmpty()) {
                System.err.println(log);
            }

            int result = GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS);

            if (result != GL20C.GL_TRUE) {
                GL20C.glDeleteShader(shader);

                throw new RuntimeException("Shader compilation failed of type " + type.name() + ", see log for details");
            }

            return shader;
        }
    }

}
