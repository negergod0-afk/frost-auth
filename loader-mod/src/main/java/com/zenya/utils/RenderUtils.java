package com.zenya.utils;

import com.zenya.mixin.GameRendererAccessor;
import com.zenya.utils.renderer.ProjectionUtil;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Pair;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;

import java.util.LinkedHashMap;
import java.util.SequencedMap;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.Color;

public class RenderUtils {

    /** Last fog buffer captured by WorldRendererMixin at WorldRenderer.render entry.
     *  drawWithoutFog() restores this after temporarily swapping to NONE so ESP
     *  tracer lines aren't tinted by water/atmospheric fog. */
    public static GpuBufferSlice lastFogBuffer;

    private static final Matrix4f POSITION_PROJECTION_MATRIX = new Matrix4f();
    private static final FrustumIntersection FRUSTUM = new FrustumIntersection();
    private static boolean frustumReady;
    private static double frustumX;
    private static double frustumY;
    private static double frustumZ;

// Custom no-depth-test pipelines + render layers so ESPs render through walls.
    private static final RenderPipeline NO_DEPTH_FILLED_BOX_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation("pipeline/zenya_no_depth_filled_box")
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST) // Force always visible
                    .withDepthWrite(false)
                    .withCull(false)
                    .build());

    private static final RenderPipeline NO_DEPTH_LINES_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation("pipeline/zenya_no_depth_lines")
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST) // Force always visible
                    .withDepthWrite(false)
                    .build());

    // Used by ESPs to draw a rounded "orb" marker instead of a blocky cube.
    private static final float[][] ICOSPHERE_VERTS;
    // Outward-CCW winding so back-face culling renders only the front hemisphere.
    private static final int[][] ICOSPHERE_FACES = {
            {0, 8, 4},  {0, 2, 8},  {0, 10, 2}, {0, 6, 10}, {0, 4, 6},
            {3, 5, 9},  {3, 7, 5},  {3, 11, 7}, {3, 1, 11}, {3, 9, 1},
            {4, 1, 6},  {4, 9, 1},  {4, 8, 9},
            {8, 5, 9},  {8, 2, 5},
            {2, 7, 5},  {2, 10, 7},
            {10, 11, 7},{10, 6, 11},
            {6, 1, 11}
    };
    static {
        double phi = (1.0 + Math.sqrt(5.0)) / 2.0;
        double[][] raw = {
                {0, 1, phi}, {0, 1, -phi}, {0, -1, phi}, {0, -1, -phi},
                {1, phi, 0}, {1, -phi, 0}, {-1, phi, 0}, {-1, -phi, 0},
                {phi, 0, 1}, {phi, 0, -1}, {-phi, 0, 1}, {-phi, 0, -1}
        };
        ICOSPHERE_VERTS = new float[raw.length][3];
        double invLen = 1.0 / Math.sqrt(1.0 + phi * phi);
        for (int i = 0; i < raw.length; i++) {
            ICOSPHERE_VERTS[i][0] = (float) (raw[i][0] * invLen);
            ICOSPHERE_VERTS[i][1] = (float) (raw[i][1] * invLen);
            ICOSPHERE_VERTS[i][2] = (float) (raw[i][2] * invLen);
        }
    }
    private static final RenderLayer NO_DEPTH_FILLED_BOX = RenderLayer.of(
            "zenya_no_depth_filled_box",
            RenderSetup.builder(NO_DEPTH_FILLED_BOX_PIPELINE).translucent().build());
    /** Public accessor so other modules (HoleESP gradient) can render custom geometry through the same no-depth layer. */
    public static RenderLayer noDepthFilledBoxLayer() { return NO_DEPTH_FILLED_BOX; }
    private static final RenderLayer NO_DEPTH_LINES = RenderLayer.of(
            "zenya_no_depth_lines",
            RenderSetup.builder(NO_DEPTH_LINES_PIPELINE).build());

    public static final class WorldBatch {
        private final MatrixStack matrices;
        private final BufferAllocator allocator;
        private final VertexConsumerProvider.Immediate immediate;
        private boolean dirty;
        private boolean closed;

        private WorldBatch(MatrixStack matrices) {
            this.matrices = matrices;
            this.allocator = new BufferAllocator(512 * 1024);
            this.immediate = VertexConsumerProvider.immediate(this.allocator);
        }

        public void renderBox(double x1, double y1, double z1, double x2, double y2, double z2, Color color) {
            renderFilledBox(x1, y1, z1, x2, y2, z2, color);
        }

        public void renderFilledBox(double x1, double y1, double z1, double x2, double y2, double z2, Color color) {
            VertexConsumer fillConsumer = immediate.getBuffer(NO_DEPTH_FILLED_BOX);
            int argb = (color.getAlpha() << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();

            MatrixStack.Entry entry = matrices.peek();
            float fx1 = (float) x1;
            float fy1 = (float) y1;
            float fz1 = (float) z1;
            float fx2 = (float) x2;
            float fy2 = (float) y2;
            float fz2 = (float) z2;

            fillConsumer.vertex(entry, fx1, fy1, fz1).color(argb);
            fillConsumer.vertex(entry, fx2, fy1, fz1).color(argb);
            fillConsumer.vertex(entry, fx2, fy1, fz2).color(argb);
            fillConsumer.vertex(entry, fx1, fy1, fz2).color(argb);

            fillConsumer.vertex(entry, fx1, fy2, fz1).color(argb);
            fillConsumer.vertex(entry, fx1, fy2, fz2).color(argb);
            fillConsumer.vertex(entry, fx2, fy2, fz2).color(argb);
            fillConsumer.vertex(entry, fx2, fy2, fz1).color(argb);

            fillConsumer.vertex(entry, fx1, fy1, fz1).color(argb);
            fillConsumer.vertex(entry, fx1, fy2, fz1).color(argb);
            fillConsumer.vertex(entry, fx2, fy2, fz1).color(argb);
            fillConsumer.vertex(entry, fx2, fy1, fz1).color(argb);

            fillConsumer.vertex(entry, fx2, fy1, fz2).color(argb);
            fillConsumer.vertex(entry, fx2, fy2, fz2).color(argb);
            fillConsumer.vertex(entry, fx1, fy2, fz2).color(argb);
            fillConsumer.vertex(entry, fx1, fy1, fz2).color(argb);

            fillConsumer.vertex(entry, fx1, fy1, fz2).color(argb);
            fillConsumer.vertex(entry, fx1, fy2, fz2).color(argb);
            fillConsumer.vertex(entry, fx1, fy2, fz1).color(argb);
            fillConsumer.vertex(entry, fx1, fy1, fz1).color(argb);

            fillConsumer.vertex(entry, fx2, fy1, fz1).color(argb);
            fillConsumer.vertex(entry, fx2, fy2, fz1).color(argb);
            fillConsumer.vertex(entry, fx2, fy2, fz2).color(argb);
            fillConsumer.vertex(entry, fx2, fy1, fz2).color(argb);
            dirty = true;
        }

        public void renderOutlineBox(double x1, double y1, double z1, double x2, double y2, double z2, Color color) {
            double dX = x2 - x1;
            double dY = y2 - y1;
            double dZ = z2 - z1;
            double t = Math.min(0.02, Math.min(dX / 8.0, Math.min(dY / 8.0, dZ / 8.0)));
            if (t <= 0.0) return;
            
            // 4 bottom edges
            renderFilledBox(x1, y1, z1, x2, y1 + t, z1 + t, color);
            renderFilledBox(x1, y1, z2 - t, x2, y1 + t, z2, color);
            renderFilledBox(x1, y1, z1 + t, x1 + t, y1 + t, z2 - t, color);
            renderFilledBox(x2 - t, y1, z1 + t, x2, y1 + t, z2 - t, color);
            
            // 4 top edges
            renderFilledBox(x1, y2 - t, z1, x2, y2, z1 + t, color);
            renderFilledBox(x1, y2 - t, z2 - t, x2, y2, z2, color);
            renderFilledBox(x1, y2 - t, z1 + t, x1 + t, y2, z2 - t, color);
            renderFilledBox(x2 - t, y2 - t, z1 + t, x2, y2, z2 - t, color);
            
            // 4 vertical edges
            renderFilledBox(x1, y1 + t, z1, x1 + t, y2 - t, z1 + t, color);
            renderFilledBox(x2 - t, y1 + t, z1, x2, y2 - t, z1 + t, color);
            renderFilledBox(x1, y1 + t, z2 - t, x1 + t, y2 - t, z2 - t, color);
            renderFilledBox(x2 - t, y1 + t, z2 - t, x2, y2 - t, z2 - t, color);
        }

        public void renderSphere(double cx, double cy, double cz, double radius, Color color) {
            VertexConsumer fillConsumer = immediate.getBuffer(NO_DEPTH_FILLED_BOX);
            int argb = (color.getAlpha() << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
            MatrixStack.Entry entry = matrices.peek();
            float fcx = (float) cx, fcy = (float) cy, fcz = (float) cz, fr = (float) radius;
            for (int[] face : ICOSPHERE_FACES) {
                float[] a = ICOSPHERE_VERTS[face[0]];
                float[] b = ICOSPHERE_VERTS[face[1]];
                float[] c = ICOSPHERE_VERTS[face[2]];
                fillConsumer.vertex(entry, fcx + a[0] * fr, fcy + a[1] * fr, fcz + a[2] * fr).color(argb);
                fillConsumer.vertex(entry, fcx + b[0] * fr, fcy + b[1] * fr, fcz + b[2] * fr).color(argb);
                fillConsumer.vertex(entry, fcx + c[0] * fr, fcy + c[1] * fr, fcz + c[2] * fr).color(argb);
                fillConsumer.vertex(entry, fcx + c[0] * fr, fcy + c[1] * fr, fcz + c[2] * fr).color(argb);
            }
            dirty = true;
        }

        public void renderLine(Color color, Vec3d start, Vec3d end, float lineWidth) {
            VertexConsumer lineConsumer = immediate.getBuffer(NO_DEPTH_LINES);
            int argb = (color.getAlpha() << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();

            MatrixStack.Entry entry = matrices.peek();
            Vector3f normal = new Vector3f(
                    (float) (end.x - start.x),
                    (float) (end.y - start.y),
                    (float) (end.z - start.z)
            ).normalize();

            float width = Math.max(1.0f, lineWidth);
            lineConsumer.vertex(entry, (float) start.x, (float) start.y, (float) start.z).color(argb).normal(entry, normal).lineWidth(width);
            lineConsumer.vertex(entry, (float) end.x, (float) end.y, (float) end.z).color(argb).normal(entry, normal).lineWidth(width);
            dirty = true;
        }

        public void flush() {
            if (closed) return;
            try {
                if (dirty) {
                    boolean depthWasEnabled = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
                    org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
                    org.lwjgl.opengl.GL11.glDepthMask(false);
                    drawWithoutFog(immediate);
                    org.lwjgl.opengl.GL11.glDepthMask(true);
                    if (depthWasEnabled) {
                        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
                    }
                    dirty = false;
                }
            } finally {
                allocator.close();
                closed = true;
            }
        }
    }

    private static final class ReusableWorldBatch {
        private final BufferAllocator allocator = new BufferAllocator(512 * 1024); // 512 KiB
        private final VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(this.allocator);
        private MatrixStack matrices;
        private boolean dirty;

        void begin(MatrixStack matrices) {
            this.matrices = matrices;
            this.dirty = false;
        }

        void renderBox(double x1, double y1, double z1, double x2, double y2, double z2, Color color) {
            renderFilledBox(x1, y1, z1, x2, y2, z2, color);
        }

        void renderFilledBox(double x1, double y1, double z1, double x2, double y2, double z2, Color color) {
            VertexConsumer fillConsumer = immediate.getBuffer(NO_DEPTH_FILLED_BOX);
            int argb = (color.getAlpha() << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();

            MatrixStack.Entry entry = matrices.peek();
            float fx1 = (float) x1;
            float fy1 = (float) y1;
            float fz1 = (float) z1;
            float fx2 = (float) x2;
            float fy2 = (float) y2;
            float fz2 = (float) z2;

            fillConsumer.vertex(entry, fx1, fy1, fz1).color(argb);
            fillConsumer.vertex(entry, fx2, fy1, fz1).color(argb);
            fillConsumer.vertex(entry, fx2, fy1, fz2).color(argb);
            fillConsumer.vertex(entry, fx1, fy1, fz2).color(argb);

            fillConsumer.vertex(entry, fx1, fy2, fz1).color(argb);
            fillConsumer.vertex(entry, fx1, fy2, fz2).color(argb);
            fillConsumer.vertex(entry, fx2, fy2, fz2).color(argb);
            fillConsumer.vertex(entry, fx2, fy2, fz1).color(argb);

            fillConsumer.vertex(entry, fx1, fy1, fz1).color(argb);
            fillConsumer.vertex(entry, fx1, fy2, fz1).color(argb);
            fillConsumer.vertex(entry, fx2, fy2, fz1).color(argb);
            fillConsumer.vertex(entry, fx2, fy1, fz1).color(argb);

            fillConsumer.vertex(entry, fx2, fy1, fz2).color(argb);
            fillConsumer.vertex(entry, fx2, fy2, fz2).color(argb);
            fillConsumer.vertex(entry, fx1, fy2, fz2).color(argb);
            fillConsumer.vertex(entry, fx1, fy1, fz2).color(argb);

            fillConsumer.vertex(entry, fx1, fy1, fz2).color(argb);
            fillConsumer.vertex(entry, fx1, fy2, fz2).color(argb);
            fillConsumer.vertex(entry, fx1, fy2, fz1).color(argb);
            fillConsumer.vertex(entry, fx1, fy1, fz1).color(argb);

            fillConsumer.vertex(entry, fx2, fy1, fz1).color(argb);
            fillConsumer.vertex(entry, fx2, fy2, fz1).color(argb);
            fillConsumer.vertex(entry, fx2, fy2, fz2).color(argb);
            fillConsumer.vertex(entry, fx2, fy1, fz2).color(argb);
            dirty = true;
        }

        void renderOutlineBox(double x1, double y1, double z1, double x2, double y2, double z2, Color color) {
            double dX = x2 - x1;
            double dY = y2 - y1;
            double dZ = z2 - z1;
            double t = Math.min(0.02, Math.min(dX / 8.0, Math.min(dY / 8.0, dZ / 8.0)));
            if (t <= 0.0) return;
            
            // 4 bottom edges
            renderFilledBox(x1, y1, z1, x2, y1 + t, z1 + t, color);
            renderFilledBox(x1, y1, z2 - t, x2, y1 + t, z2, color);
            renderFilledBox(x1, y1, z1 + t, x1 + t, y1 + t, z2 - t, color);
            renderFilledBox(x2 - t, y1, z1 + t, x2, y1 + t, z2 - t, color);
            
            // 4 top edges
            renderFilledBox(x1, y2 - t, z1, x2, y2, z1 + t, color);
            renderFilledBox(x1, y2 - t, z2 - t, x2, y2, z2, color);
            renderFilledBox(x1, y2 - t, z1 + t, x1 + t, y2, z2 - t, color);
            renderFilledBox(x2 - t, y2 - t, z1 + t, x2, y2, z2 - t, color);
            
            // 4 vertical edges
            renderFilledBox(x1, y1 + t, z1, x1 + t, y2 - t, z1 + t, color);
            renderFilledBox(x2 - t, y1 + t, z1, x2, y2 - t, z1 + t, color);
            renderFilledBox(x1, y1 + t, z2 - t, x1 + t, y2 - t, z2 - t, color);
            renderFilledBox(x2 - t, y1 + t, z2 - t, x2, y2 - t, z2 - t, color);
        }

        void renderSphere(double cx, double cy, double cz, double radius, Color color) {
            VertexConsumer fillConsumer = immediate.getBuffer(NO_DEPTH_FILLED_BOX);
            int argb = (color.getAlpha() << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
            MatrixStack.Entry entry = matrices.peek();
            float fcx = (float) cx, fcy = (float) cy, fcz = (float) cz, fr = (float) radius;
            for (int[] face : ICOSPHERE_FACES) {
                float[] a = ICOSPHERE_VERTS[face[0]];
                float[] b = ICOSPHERE_VERTS[face[1]];
                float[] c = ICOSPHERE_VERTS[face[2]];
                fillConsumer.vertex(entry, fcx + a[0] * fr, fcy + a[1] * fr, fcz + a[2] * fr).color(argb);
                fillConsumer.vertex(entry, fcx + b[0] * fr, fcy + b[1] * fr, fcz + b[2] * fr).color(argb);
                fillConsumer.vertex(entry, fcx + c[0] * fr, fcy + c[1] * fr, fcz + c[2] * fr).color(argb);
                fillConsumer.vertex(entry, fcx + c[0] * fr, fcy + c[1] * fr, fcz + c[2] * fr).color(argb);
            }
            dirty = true;
        }

        void renderLine(Color color, Vec3d start, Vec3d end, float lineWidth) {
            VertexConsumer lineConsumer = immediate.getBuffer(NO_DEPTH_LINES);
            int argb = (color.getAlpha() << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();

            MatrixStack.Entry entry = matrices.peek();
            Vector3f normal = new Vector3f(
                    (float) (end.x - start.x),
                    (float) (end.y - start.y),
                    (float) (end.z - start.z)
            ).normalize();

            float width = Math.max(1.0f, lineWidth);
            lineConsumer.vertex(entry, (float) start.x, (float) start.y, (float) start.z).color(argb).normal(entry, normal).lineWidth(width);
            lineConsumer.vertex(entry, (float) end.x, (float) end.y, (float) end.z).color(argb).normal(entry, normal).lineWidth(width);
            dirty = true;
        }

        void flush() {
            if (!dirty) return;
            boolean depthWasEnabled = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            org.lwjgl.opengl.GL11.glDepthMask(false);
            drawWithoutFog(immediate);
            org.lwjgl.opengl.GL11.glDepthMask(true);
            if (depthWasEnabled) {
                org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            }
            dirty = false;
        }
    }

    private static void drawWithoutFog(VertexConsumerProvider.Immediate immediate) {
        GpuBufferSlice savedFog = RenderSystem.getShaderFog();
        try {
            // Force disable fog at the RenderSystem level
            RenderSystem.setShaderFog(null);
            
            // Try to set fog to NONE to clear any water/environmental fog uniforms
            try {
                if (MinecraftClient.getInstance().gameRenderer instanceof GameRendererAccessor gra) {
                    FogRenderer fr = gra.zenya$getFogRenderer();
                    if (fr != null) {
                        GpuBufferSlice noneFog = fr.getFogBuffer(FogRenderer.FogType.NONE);
                        if (noneFog != null) {
                            RenderSystem.setShaderFog(noneFog);
                        }
                    }
                }
            } catch (Throwable ignored) {}

            // Draw with fog disabled
            immediate.draw();
        } finally {
            if (savedFog != null) {
                RenderSystem.setShaderFog(savedFog);
            }
        }
    }

    private static final ThreadLocal<ReusableWorldBatch> REUSABLE_BATCH =
            ThreadLocal.withInitial(ReusableWorldBatch::new);

    public static WorldBatch beginWorldBatch(MatrixStack matrices) {
        return new WorldBatch(matrices);
    }

    public static void updateFrustum(Matrix4f positionMatrix, Matrix4f projectionMatrix, Vec3d cameraPos) {
        if (positionMatrix == null || projectionMatrix == null || cameraPos == null) {
            frustumReady = false;
            return;
        }

        projectionMatrix.mul(positionMatrix, POSITION_PROJECTION_MATRIX);
        FRUSTUM.set(POSITION_PROJECTION_MATRIX);
        frustumX = cameraPos.x;
        frustumY = cameraPos.y;
        frustumZ = cameraPos.z;
        frustumReady = true;
    }

    public static boolean isWorldBoxVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (!frustumReady) {
            return true;
        }

        float relMinX = (float) (minX - frustumX);
        float relMinY = (float) (minY - frustumY);
        float relMinZ = (float) (minZ - frustumZ);
        float relMaxX = (float) (maxX - frustumX);
        float relMaxY = (float) (maxY - frustumY);
        float relMaxZ = (float) (maxZ - frustumZ);
        int result = FRUSTUM.intersectAab(relMinX, relMinY, relMinZ, relMaxX, relMaxY, relMaxZ);
        return result == FrustumIntersection.INTERSECT || result == FrustumIntersection.INSIDE;
    }

    public static Camera getCamera() {
        return MinecraftClient.getInstance().gameRenderer.getCamera();
    }

    private static java.lang.reflect.Method cameraPosMethod;
    public static Vec3d getCameraPos(Camera camera) {
        if (cameraPosMethod == null) {
            for (java.lang.reflect.Method m : Camera.class.getMethods()) {
                if (m.getReturnType() == Vec3d.class && m.getParameterCount() == 0) {
                    cameraPosMethod = m;
                    break;
                }
            }
        }
        try {
            return (Vec3d) cameraPosMethod.invoke(camera);
        } catch (Exception e) {
            return MinecraftClient.getInstance().player.getCameraPosVec(1.0f);
        }
    }

    public static void renderFilledBox(MatrixStack matrices, double x1, double y1, double z1, double x2, double y2, double z2, Color color) {
        ReusableWorldBatch batch = REUSABLE_BATCH.get();
        batch.begin(matrices);
        batch.renderFilledBox(x1, y1, z1, x2, y2, z2, color);
        batch.flush();
    }

    public static void renderOutlineBox(MatrixStack matrices, double x1, double y1, double z1, double x2, double y2, double z2, Color color) {
        ReusableWorldBatch batch = REUSABLE_BATCH.get();
        batch.begin(matrices);
        batch.renderOutlineBox(x1, y1, z1, x2, y2, z2, color);
        batch.flush();
    }

    public static void renderLine(MatrixStack matrices, Color color, Vec3d start, Vec3d end) {
        renderLine(matrices, color, start, end, 2.0f);
    }

    public static void renderLine(MatrixStack matrices, Color color, Vec3d start, Vec3d end, float lineWidth) {
        ReusableWorldBatch batch = REUSABLE_BATCH.get();
        batch.begin(matrices);
        batch.renderLine(color, start, end, lineWidth);
        batch.flush();
    }

    public static Vec3d getCameraForward(Camera camera) {
        return new Vec3d(0.0D, 0.0D, 1.0D)
                .rotateX(-(float) Math.toRadians(camera.getPitch()))
                .rotateY(-(float) Math.toRadians(camera.getYaw()))
                .normalize();
    }

    public static Vec3d getCameraRight(Camera camera) {
        return new Vec3d(1.0D, 0.0D, 0.0D)
                .rotateY(-(float) Math.toRadians(camera.getYaw()))
                .normalize();
    }

    public static Vec3d getCameraUp(Vec3d cameraForward, Vec3d cameraRight) {
        return cameraForward.crossProduct(cameraRight).normalize();
    }

    public static Vec3d getSpreadTracerEnd(
            double targetX,
            double targetY,
            double targetZ,
            Vec3d cameraForward,
            Vec3d cameraRight,
            Vec3d cameraUp,
            double endDistance,
            double behindMinSpread
    ) {
        double rightAmount = (targetX * cameraRight.x) + (targetY * cameraRight.y) + (targetZ * cameraRight.z);
        double upAmount = (targetX * cameraUp.x) + (targetY * cameraUp.y) + (targetZ * cameraUp.z);
        double forwardAmount = (targetX * cameraForward.x) + (targetY * cameraForward.y) + (targetZ * cameraForward.z);
        double safeForward = Math.max(Math.abs(forwardAmount), 0.25D);
        double projectedRight = rightAmount / safeForward;
        double projectedUp = upAmount / safeForward;

        if (forwardAmount <= 0.0D) {
            double projectedLength = Math.hypot(projectedRight, projectedUp);
            if (projectedLength < behindMinSpread) {
                if (projectedLength < 1.0E-4D) {
                    projectedRight = behindMinSpread;
                    projectedUp = 0.0D;
                } else {
                    double scale = behindMinSpread / projectedLength;
                    projectedRight *= scale;
                    projectedUp *= scale;
                }
            }
        }

        return cameraForward
                .add(cameraRight.multiply(projectedRight))
                .add(cameraUp.multiply(projectedUp))
                .normalize()
                .multiply(endDistance);
    }

    public static Vec3d getSpreadTracerEnd(
            Vec3d target,
            Vec3d cameraForward,
            Vec3d cameraRight,
            Vec3d cameraUp,
            double endDistance,
            double behindMinSpread
    ) {
        return getSpreadTracerEnd(target.x, target.y, target.z, cameraForward, cameraRight, cameraUp, endDistance, behindMinSpread);
    }

    public static Vec3d getClampedTracerEnd(
            Vec3d cameraRelativeTarget,
            Vec3d worldTarget,
            Vec3d cameraForward,
            Vec3d cameraRight,
            Vec3d cameraUp,
            double projectionDistance
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        Pair<Vec3d, Boolean> projection = ProjectionUtil.project(
                ProjectionUtil.modelViewMatrix,
                ProjectionUtil.projectionMatrix,
                worldTarget
        );
        if (projection != null && projection.getRight()) {
            Vec3d screenPos = projection.getLeft();
            if (screenPos.x >= 0.0D && screenPos.x <= screenWidth && screenPos.y >= 0.0D && screenPos.y <= screenHeight) {
                return cameraRelativeTarget;
            }
        }

        Vector3f edgePoint = ProjectionUtil.projectWithClamp(
                ProjectionUtil.modelViewMatrix,
                ProjectionUtil.projectionMatrix,
                worldTarget
        );
        if (edgePoint == null) {
            return cameraRelativeTarget;
        }

        return getRayToScreenPoint(edgePoint.x, edgePoint.y, screenWidth, screenHeight, cameraForward, cameraRight, cameraUp)
                .multiply(projectionDistance);
    }

    private static Vec3d getRayToScreenPoint(
            float screenX,
            float screenY,
            int screenWidth,
            int screenHeight,
            Vec3d cameraForward,
            Vec3d cameraRight,
            Vec3d cameraUp
    ) {
        double ndcX = (screenX / screenWidth) * 2.0D - 1.0D;
        double ndcY = 1.0D - (screenY / screenHeight) * 2.0D;

        double aspect = ProjectionUtil.projectionMatrix.m11() / ProjectionUtil.projectionMatrix.m00();
        double tanHalfFov = 1.0D / ProjectionUtil.projectionMatrix.m11();

        return cameraForward
                .add(cameraRight.multiply(ndcX * aspect * tanHalfFov))
                .add(cameraUp.multiply(ndcY * tanHalfFov))
                .normalize();
    }
}
