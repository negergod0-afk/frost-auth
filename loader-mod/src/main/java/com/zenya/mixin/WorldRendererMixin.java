package com.zenya.mixin;

import com.zenya.module.ModuleManager;
import com.zenya.module.modules.render.NoRender;
import com.zenya.utils.RenderUtils;
import com.zenya.utils.renderer.ProjectionUtil;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    private static final Matrix4f capturedMatrix = new Matrix4f();
    private static boolean hasCapturedMatrix;
    private static float capturedTickDelta = 1.0f;

    @Inject(method = "render", at = @At("HEAD"))
    private void captureRenderState(
            ObjectAllocator allocator,
            RenderTickCounter tickCounter,
            boolean renderBlockOutline,
            Camera camera,
            Matrix4f positionMatrix,
            Matrix4f projectionMatrix,
            Matrix4f frustumMatrix,
            GpuBufferSlice fog,
            Vector4f fogColor,
            boolean renderTicking,
            CallbackInfo ci
    ) {
        capturedMatrix.set(positionMatrix);
        ProjectionUtil.modelViewMatrix.set(positionMatrix);
        ProjectionUtil.positionMatrix.set(positionMatrix);
        ProjectionUtil.projectionMatrix.set(projectionMatrix);
        RenderUtils.updateFrustum(positionMatrix, projectionMatrix, camera.getCameraPos());
        hasCapturedMatrix = true;
        capturedTickDelta = tickCounter.getTickProgress(false);
        RenderUtils.lastFogBuffer = fog;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRender(CallbackInfo ci) {
        if (hasCapturedMatrix) {
            MatrixStack matrices = new MatrixStack();
            // Clear translation columns/rows from the view matrix so we only get rotation.
            // This allows modules to use their manual (world - camera) coordinates without double-offsetting.
            Matrix4f rotationOnly = new Matrix4f(capturedMatrix);
            rotationOnly.setTranslation(0, 0, 0);
            
            matrices.multiplyPositionMatrix(rotationOnly);
            ModuleManager.INSTANCE.onRender(matrices, capturedTickDelta);
        }
    }

    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
    private void zenya$skipWeatherPass(
            net.minecraft.client.render.FrameGraphBuilder frameGraphBuilder,
            GpuBufferSlice fog,
            CallbackInfo ci
    ) {
        if (NoRender.hideAllPrecipitation()) {
            ci.cancel();
        }
    }
}               
