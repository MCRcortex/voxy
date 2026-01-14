package me.cortex.voxy.client.core.model;

import it.unimi.dsi.fastutil.bytes.ByteArrayFIFOQueue;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.jellysquid.mods.sodium.client.util.color.ColorSRGB;
import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;

import static me.cortex.voxy.client.core.model.ModelFactory.LAYERS;
import static me.cortex.voxy.client.core.model.ModelFactory.MODEL_TEXTURE_SIZE;

public class MipGen {
    static {
        if (MODEL_TEXTURE_SIZE>16) throw new IllegalStateException("TODO: THIS MUST BE UPDATED, IT CURRENTLY ASSUMES 16 OR SMALLER SIZE");
    }
    private static final short[] SCRATCH = new short[MODEL_TEXTURE_SIZE*MODEL_TEXTURE_SIZE];
    private static final ByteArrayFIFOQueue QUEUE = new ByteArrayFIFOQueue(MODEL_TEXTURE_SIZE*MODEL_TEXTURE_SIZE);

    private static long getOffset(int bx, int by, int i) {
        bx += i&(MODEL_TEXTURE_SIZE-1);
        by += i/MODEL_TEXTURE_SIZE;
        return bx+by*MODEL_TEXTURE_SIZE*3;
    }

    private static void solidify(long baseAddr, byte msk) {
        for (int idx = 0; idx < 6; idx++) {
            if (((msk>>idx)&1)==0) continue;
            int bx = (idx>>1)*MODEL_TEXTURE_SIZE;
            int by = (idx&1)*MODEL_TEXTURE_SIZE;
            long cAddr = baseAddr + (long)(bx+by*MODEL_TEXTURE_SIZE*3)*4;
            Arrays.fill(SCRATCH, (short) -1);
            for (int y = 0; y<MODEL_TEXTURE_SIZE;y++) {
                for (int x = 0; x<MODEL_TEXTURE_SIZE;x++) {
                    int colour = MemoryUtil.memGetInt(cAddr+(x+y*MODEL_TEXTURE_SIZE*3)*4);
                    if ((colour&0xFF000000)!=0) {
                        int pos = x+y*MODEL_TEXTURE_SIZE;
                        SCRATCH[pos] = ((short)pos);
                        QUEUE.enqueue((byte) pos);
                    }
                }
            }

            while (!QUEUE.isEmpty()) {
                int pos = Byte.toUnsignedInt(QUEUE.dequeueByte());
                int x = pos&(MODEL_TEXTURE_SIZE-1);
                int y = pos/MODEL_TEXTURE_SIZE;//this better be turned into a bitshift
                short newVal = (short) (SCRATCH[pos]+(short) 0x0100);
                for (int D = 3; D!=-1; D--) {
                    int d = 2*(D&1)-1;
                    int x2 = x+(((D&2)==2)?d:0);
                    int y2 = y+(((D&2)==0)?d:0);
                    if (x2<0||x2>=MODEL_TEXTURE_SIZE||y2<0||y2>=MODEL_TEXTURE_SIZE) continue;
                    int pos2 = x2+y2*MODEL_TEXTURE_SIZE;
                    if ((newVal&0xFF00)<(SCRATCH[pos2]&0xFF00)) {
                        SCRATCH[pos2] = newVal;
                        QUEUE.enqueue((byte) pos2);
                    }
                }
            }

            for (int i = 0; i < MODEL_TEXTURE_SIZE*MODEL_TEXTURE_SIZE; i++) {
                int d = Short.toUnsignedInt(SCRATCH[i]);
                if ((d&0xFF00)!=0) {
                    int c = MemoryUtil.memGetInt(baseAddr+getOffset(bx, by, d&0xFF)*4)&0x00FFFFFF;
                    MemoryUtil.memPutInt(baseAddr+getOffset(bx, by, i)*4, c);
                }
            }
        }
    }

    public static void putTextures(boolean darkened, ColourDepthTextureData[] textures, MemoryBuffer into) {
        //if (MODEL_TEXTURE_SIZE != 16) {throw new IllegalStateException("THIS METHOD MUST BE REDONE IF THIS CONST CHANGES");}

        //TODO: need to use a write mask to see what pixels must be used to contribute to mipping
        // as in, using the depth/stencil info, check if pixel was written to, if so, use that pixel when blending, else dont

        final long addr = into.address;
        final int LENGTH_B = MODEL_TEXTURE_SIZE*3;
        byte solidMsk = 0;
        for (int i = 0; i < 6; i++) {
            int x = (i>>1)*MODEL_TEXTURE_SIZE;
            int y = (i&1)*MODEL_TEXTURE_SIZE;
            int j = 0;
            boolean anyTransparent = false;
            for (int t : textures[i].colour()) {
                int o = ((y+(j>>LAYERS))*LENGTH_B + ((j&(MODEL_TEXTURE_SIZE-1))+x))*4; j++;//LAYERS here is just cause faster
                //t = ((t&0xFF000000)==0)?0x00_FF_00_FF:t;//great for testing
                MemoryUtil.memPutInt(addr+o, t);
                anyTransparent |= ((t&0xFF000000)==0);
            }
            solidMsk |= (anyTransparent?1:0)<<i;
        }

        if (!darkened) {
            solidify(addr, solidMsk);
        }


        //Mip the scratch
        long dAddr = addr;
        for (int i = 0; i < LAYERS-1; i++) {
            long sAddr = dAddr;
            dAddr += (MODEL_TEXTURE_SIZE*MODEL_TEXTURE_SIZE*3*2*4)>>(i<<1);//is.. i*2 because shrink both MODEL_TEXTURE_SIZE by >>i so is 2*i total shift
            int width = (MODEL_TEXTURE_SIZE*3)>>(i+1);
            int sWidth = (MODEL_TEXTURE_SIZE*3)>>i;
            int height = (MODEL_TEXTURE_SIZE*2)>>(i+1);
            //TODO: OPTIMZIE THIS
            for (int px = 0; px < width; px++) {
                for (int py = 0; py < height; py++) {
                    long bp = sAddr + (px*2 + py*2*sWidth)*4;
                    int C00 = MemoryUtil.memGetInt(bp);
                    int C01 = MemoryUtil.memGetInt(bp+sWidth*4);
                    int C10 = MemoryUtil.memGetInt(bp+4);
                    int C11 = MemoryUtil.memGetInt(bp+sWidth*4+4);
                    MemoryUtil.memPutInt(dAddr + (px+py*width) * 4L, TextureUtils.mipColours(darkened, C00, C01, C10, C11));
                }
            }
        }

        /*
         */
    }

    public static void generateMipmaps(long[] textures, int size) {

    }
}
