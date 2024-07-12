package me.cortex.voxy.client.core.gl.shader;

import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.rendering.util.DownloadStream;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import static org.lwjgl.opengl.ARBDirectStateAccess.nglClearNamedBufferData;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL30.GL_R32UI;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL30C.GL_RED_INTEGER;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL45.nglClearNamedBufferSubData;

public class PrintfInjector implements IShaderProcessor {
    private final GlBuffer textBuffer;
    private final HashMap<String, Integer> printfStringMap = new HashMap<>();
    private final HashMap<Integer, String> idToPrintfStringMap = new HashMap<>();
    private final int bindingIndex;
    private final Consumer<String> callback;
    public PrintfInjector(int bufferSize, int bufferBindingIndex, Consumer<String> callback) {
        this.textBuffer = new GlBuffer(bufferSize*4L+4);
        nglClearNamedBufferData(this.textBuffer.id, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, 0);
        this.bindingIndex = bufferBindingIndex;
        this.callback = callback;
    }

    private static int findNextCall(String src, int after) {
        while (true) {
            int idx = src.indexOf("printf", after);
            if (idx == -1) {
                return -1;
            }

            boolean lineComment = false;
            boolean multiLineComment = false;
            //Check for comments
            for (int i = 0; i < idx; i++) {
                if (src.charAt(i) == '/' && src.charAt(i + 1) == '/') {
                    lineComment = true;
                }
                if (src.charAt(i) == '\n') {
                    lineComment = false;
                }
                if ((!lineComment) && src.charAt(i) == '/' && src.charAt(i + 1) == '*') {
                    multiLineComment = true;
                }
                if ((!lineComment) && src.charAt(i) == '*' && src.charAt(i + 1) == '/') {
                    multiLineComment = false;
                }
            }
            if (lineComment || multiLineComment) {
                after = idx+1;
                continue;
            }
            return idx;
        }
    }

    private static void parsePrintfTypes(String fmtStr, List<Character> types) {
        for (int i = 0; i < fmtStr.length()-1; i++) {
            if (fmtStr.charAt(i)=='%' && (i==0||fmtStr.charAt(i-1)!='%')) {
                types.add(fmtStr.charAt(i+1));
            }
        }
    }

    public String transformInject(String src) {
        String original = src;
        //Quick exit
        if (!src.contains("printf")) {
            return src;
        }

        int pos = 0;
        StringBuilder result = new StringBuilder();
        List<String> argVals = new ArrayList<>();
        List<Character> types = new ArrayList<>();

        {
            int bufferInjection = Math.max(src.lastIndexOf("#version"), src.lastIndexOf("#extension"));
            bufferInjection = src.indexOf("\n", bufferInjection);

            result.append(src, 0, bufferInjection+1);

            result.append(String.format("""
                    layout(binding = %s, std430) restrict buffer PrintfOutputStream {
                        uint index;
                        uint stream[];
                    } printfOutputStruct;
                    """, this.bindingIndex));

            src = src.substring(bufferInjection+1);
        }
        boolean usedPrintf = false;
        while (true) {
            int nextCall = findNextCall(src, pos);
            if (nextCall == -1) {
                result.append(src, pos, src.length());
                break;
            }
            result.append(src, pos, nextCall);


            //Parse the printf() call
            String sub = src.substring(nextCall);
            sub = sub.substring(sub.indexOf('"')+1);
            sub = sub.substring(0, sub.indexOf(';'));
            String fmtStr = sub.substring(0, sub.indexOf('"'));
            String args = sub.substring(sub.indexOf('"'));

            //Parse the commas in the args
            int prev = 0;
            int brace = 0;
            argVals.clear();
            for (int i = 0; i < args.length(); i++) {
                if (args.charAt(i) == '(' || args.charAt(i) == '[') brace++;
                if (args.charAt(i) == ')' || args.charAt(i) == ']') brace--;
                if ((args.charAt(i) == ',' && brace==0) || brace==-1) {
                    if (prev == 0) {
                        prev = i;
                        continue;
                    }
                    String arg = args.substring(prev+1, i);
                    prev = i;
                    argVals.add(arg);

                    if (brace==-1) {
                        break;
                    }
                }
            }

            //Parse the format string
            types.clear();
            parsePrintfTypes(fmtStr, types);

            if (types.size() != argVals.size()) {
                throw new IllegalStateException("Printf obj count dont match arg size");
            }



            //Inject the printf code
            StringBuilder subCode = new StringBuilder();
            subCode.append(String.format("{" +
                            "uint printfWriteIndex = atomicAdd(printfOutputStruct.index,%s);" +
                            "printfOutputStruct.stream[printfWriteIndex]=%s;", types.size()+1,
                    this.printfStringMap.computeIfAbsent(fmtStr, a->{int id = this.printfStringMap.size();
                        this.idToPrintfStringMap.put(id, a);
                        return id;})));

            for (int i = 0; i < types.size(); i++) {
                subCode.append("printfOutputStruct.stream[printfWriteIndex+").append(i+1).append("]=");
                if (types.get(i) == 'd' || types.get(i) == 'i') {
                    subCode.append("uint(").append(argVals.get(i)).append(")");
                } else if (types.get(i) == 'f') {
                    subCode.append("floatBitsToUint(").append(argVals.get(i)).append(")");
                } else {
                    throw new IllegalStateException("Unknown type " + types.get(i));
                }
                subCode.append(";");
            }
            subCode.append("}");
            result.append(subCode);
            usedPrintf = true;
            pos = src.indexOf(';', nextCall)+1;
        }
        if (!usedPrintf) {
            return original;
        }
        return result.toString();
    }

    public void bind() {
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, this.bindingIndex, this.textBuffer.id);
    }

    private void processResult(long ptr, long size) {
        int total = MemoryUtil.memGetInt(ptr); ptr += 4;
        int cnt = 0;
        List<Character> types = new ArrayList<>();
        while (cnt < total) {
            int id = MemoryUtil.memGetInt(ptr);
            ptr += 4;
            cnt++;
            String fmt = this.idToPrintfStringMap.get(id);
            types.clear();
            parsePrintfTypes(fmt, types);
            Object[] args = new Object[types.size()];
            for (int i = 0; i < types.size(); i++) {
                if (types.get(i) == 'd' || types.get(i) == 'i') {
                    args[i] = MemoryUtil.memGetInt(ptr);
                    ptr += 4;
                    cnt++;
                }
                if (types.get(i) == 'f') {
                    args[i] = Float.intBitsToFloat(MemoryUtil.memGetInt(ptr));
                    ptr += 4;
                    cnt++;
                }
            }
            this.callback.accept(String.format(fmt, args));
        }
    }

    public void download() {
        DownloadStream.INSTANCE.download(this.textBuffer, this::processResult);
        DownloadStream.INSTANCE.commit();
        nglClearNamedBufferSubData(this.textBuffer.id, GL_R32UI, 0, 4, GL_RED_INTEGER, GL_UNSIGNED_INT, 0);
    }

    public void free() {
        this.textBuffer.free();
    }

    @Override
    public String process(ShaderType type, String source) {
        return this.transformInject(source);
    }
}
