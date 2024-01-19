package me.cortex.zenith.client.core.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import org.joml.Matrix4f;

public class DebugUtil {
    private static Matrix4f positionMatrix = new Matrix4f().identity();
    public static void setPositionMatrix(MatrixStack stack) {
        positionMatrix = new Matrix4f(stack.peek().getPositionMatrix());
    }
    public static void renderAABB(Box aabb, int colour) {
        renderAABB(aabb, (float)(colour>>>24)/255, (float)((colour>>>16)&0xFF)/255, (float)((colour>>>8)&0xFF)/255, (float)(colour&0xFF)/255);
    }

    public static void renderBox(int x, int y, int z, int size, float r, float g, float b) {
        renderAABB(new Box(x, y, z, x+size, y+size, z+size), r, g, b, 1.0f);
    }
    public static void renderAABB(Box aabb, float r, float g, float b, float a) {
        RenderSystem.setShaderFogEnd(9999999);
        RenderSystem.setShader(GameRenderer::getPositionProgram);
        RenderSystem.setShaderColor(r, g, b, a);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        //RenderSystem.depthMask(false);

        var tessellator = RenderSystem.renderThreadTesselator();
        var builder = tessellator.getBuffer();
        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);

        builder.vertex(positionMatrix, (float) aabb.minX, (float) aabb.minY, (float) aabb.minZ).next();
        builder.vertex(positionMatrix, (float) aabb.maxX, (float) aabb.minY, (float) aabb.minZ).next();
        builder.vertex(positionMatrix, (float) aabb.maxX, (float) aabb.minY, (float) aabb.maxZ).next();
        builder.vertex(positionMatrix, (float) aabb.minX, (float) aabb.minY, (float) aabb.maxZ).next();

        builder.vertex(positionMatrix, (float) aabb.minX, (float) aabb.maxY, (float) aabb.minZ).next();
        builder.vertex(positionMatrix, (float) aabb.maxX, (float) aabb.maxY, (float) aabb.minZ).next();
        builder.vertex(positionMatrix, (float) aabb.maxX, (float) aabb.maxY, (float) aabb.maxZ).next();
        builder.vertex(positionMatrix, (float) aabb.minX, (float) aabb.maxY, (float) aabb.maxZ).next();

        builder.vertex(positionMatrix, (float) aabb.minX, (float) aabb.minY, (float) aabb.minZ).next();
        builder.vertex(positionMatrix, (float) aabb.maxX, (float) aabb.minY, (float) aabb.minZ).next();
        builder.vertex(positionMatrix, (float) aabb.maxX, (float) aabb.maxY, (float) aabb.minZ).next();
        builder.vertex(positionMatrix, (float) aabb.minX, (float) aabb.maxY, (float) aabb.minZ).next();

        builder.vertex(positionMatrix, (float) aabb.minX, (float) aabb.minY, (float) aabb.maxZ).next();
        builder.vertex(positionMatrix, (float) aabb.maxX, (float) aabb.minY, (float) aabb.maxZ).next();
        builder.vertex(positionMatrix, (float) aabb.maxX, (float) aabb.maxY, (float) aabb.maxZ).next();
        builder.vertex(positionMatrix, (float) aabb.minX, (float) aabb.maxY, (float) aabb.maxZ).next();

        builder.vertex(positionMatrix, (float) aabb.minX, (float) aabb.minY, (float) aabb.minZ).next();
        builder.vertex(positionMatrix, (float) aabb.minX, (float) aabb.maxY, (float) aabb.minZ).next();
        builder.vertex(positionMatrix, (float) aabb.minX, (float) aabb.maxY, (float) aabb.maxZ).next();
        builder.vertex(positionMatrix, (float) aabb.minX, (float) aabb.minY, (float) aabb.maxZ).next();

        builder.vertex(positionMatrix, (float) aabb.maxX, (float) aabb.minY, (float) aabb.minZ).next();
        builder.vertex(positionMatrix, (float) aabb.maxX, (float) aabb.maxY, (float) aabb.minZ).next();
        builder.vertex(positionMatrix, (float) aabb.maxX, (float) aabb.maxY, (float) aabb.maxZ).next();
        builder.vertex(positionMatrix, (float) aabb.maxX, (float) aabb.minY, (float) aabb.maxZ).next();

        tessellator.draw();

        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1,1,1,1);
        RenderSystem.depthMask(true);
    }
}
