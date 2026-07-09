package com.zenya.module.modules.render;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.modules.client.Friends;
import com.zenya.module.modules.client.ZenyaPlus;
import com.zenya.setting.Setting;
import com.zenya.utils.RenderUtils;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public final class PlayerESP extends Module {

    private static final double TRACER_START_DISTANCE = 150.0D;
    private static final double TRACER_END_DISTANCE = 24.0D;
    private static final double TRACER_BEHIND_MIN_SPREAD = 2.75D;

    private static final ArrayList<RenderData> RENDER_BUFFER = new ArrayList<>(64);

    private final Setting<Integer> alpha = new Setting<>("Alpha", 100, 1, 255);
    private final Setting<Double> range = new Setting<>("Range", 256.0, 16.0, 512.0);
    private final Setting<Boolean> tracers = new Setting<>("Tracers", false);
    private final Setting<Color> espColor = new Setting<>("ESP color", new Color(0, 100, 255));
    private final Setting<Color> tracerColor = new Setting<>("Tracer color", new Color(0, 100, 255));

    public PlayerESP() {
        super("Player ESP", Category.RENDER);
        setDescription("Draws coloured boxes and optional tracers around other players in the world, with separate friend colouring.");
        addSetting(alpha);
        addSetting(range);
        addSetting(tracers);
        addSetting(espColor);
        addSetting(tracerColor);
    }

    @Override
    public void onRender(MatrixStack matrices, float tickDelta) {
        final net.minecraft.client.MinecraftClient mc = this.mc;
        if (mc.world == null || mc.player == null) return;

        Camera cam = RenderUtils.getCamera();
        if (cam == null) return;

        int alphaValue = clampAlpha(alpha.getValue());
        if (alphaValue < 1) return;

        Vec3d camPos = RenderUtils.getCameraPos(cam);
        final double camX = camPos.x;
        final double camY = camPos.y;
        final double camZ = camPos.z;
        final double rangeVal = range.getValue();
        final double maxRangeSq = rangeVal * rangeVal;
        final boolean renderTracers = tracers.getValue();
        final Color fill = applyOpacity(espColor.getValue(), alphaValue);
        final Color tracer = renderTracers ? applyOpacity(tracerColor.getValue(), 255) : null;
        final boolean friendsEspColor = Friends.isEspColor();

        // Determine if we should use player position for tracers (F5/freecam mode)
        boolean usePlayerPosition = isNonFirstPersonView();
        Vec3d tracerOrigin = usePlayerPosition ? getPlayerHeadPosition(tickDelta) : Vec3d.ZERO;
        
        Vec3d cameraForward = renderTracers ? RenderUtils.getCameraForward(cam) : null;
        Vec3d cameraRight = renderTracers ? RenderUtils.getCameraRight(cam) : null;
        Vec3d cameraUp = renderTracers ? RenderUtils.getCameraUp(cameraForward, cameraRight) : null;
        Vec3d tracerStart = renderTracers
                ? (usePlayerPosition ? tracerOrigin : cameraForward.multiply(TRACER_START_DISTANCE))
                : null;

        // Cache friend-colored variants once per frame instead of per entity.
        Color friendFill = null, friendTracer = null;
        Color cachedFriendBase = null;

        final ArrayList<RenderData> renderData = RENDER_BUFFER;
        renderData.clear();
        final net.minecraft.entity.player.PlayerEntity self = mc.player;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == self || !player.isAlive() || player.isSpectator() || player.isInvisibleTo(self)) {
                continue;
            }
            boolean friend = friendsEspColor && Friends.isFriend(getFriendLookupName(player));

            Vec3d lerped = getLerpedPosCompat(player, tickDelta);
            double worldX = lerped.x;
            double worldY = lerped.y;
            double worldZ = lerped.z;
            double dx = worldX - camX;
            double dy = worldY - camY;
            double dz = worldZ - camZ;
            double distSq = (dx * dx) + (dy * dy) + (dz * dz);
            if (distSq > maxRangeSq) {
                continue;
            }

            Color curFill;
            Color curTracer;
            if (friend) {
                Color fc = Friends.getColor();
                // so we'd otherwise allocate 2 Colors per friend per frame for the same RGB.
                if (fc != cachedFriendBase || friendFill == null) {
                    cachedFriendBase = fc;
                    friendFill = applyOpacity(fc, alphaValue);
                    friendTracer = renderTracers ? applyOpacity(fc, 255) : null;
                }
                curFill = friendFill;
                curTracer = friendTracer;
            } else {
                curFill = fill;
                curTracer = tracer;
            }

            // Dynamic hitbox based on current pose (crouch/swim/etc).
            double halfWidth = player.getWidth() / 2.0D;
            double height = player.getHeight();

            // Visibility culling: distance check above is sufficient; avoid aggressive AABB visibility culling.
            boolean boxVisible = true;
            double tracerTargetY = dy + (player.getHeight() * 0.5D);
            renderData.add(new RenderData(dx, dy, dz, tracerTargetY, halfWidth, height, curFill, curTracer, boxVisible));
        }

        if (renderData.isEmpty()) {
            return;
        }

        matrices.push();
        RenderUtils.WorldBatch boxBatch = RenderUtils.beginWorldBatch(matrices);
        for (RenderData data : renderData) {
            if (!data.boxVisible) {
                continue;
            }
            boxBatch.renderFilledBox(
                    data.dx - data.halfWidth,
                    data.dy,
                    data.dz - data.halfWidth,
                    data.dx + data.halfWidth,
                    data.dy + data.height,
                    data.dz + data.halfWidth,
                    data.fill
            );
        }
        boxBatch.flush();

        if (renderTracers) {
            RenderUtils.WorldBatch tracerBatch = RenderUtils.beginWorldBatch(matrices);
            for (RenderData data : renderData) {
                if (data.tracer == null) {
                    continue;
                }
                Vec3d tracerEnd = RenderUtils.getSpreadTracerEnd(
                        data.dx,
                        data.tracerTargetY,
                        data.dz,
                        cameraForward,
                        cameraRight,
                        cameraUp,
                        TRACER_END_DISTANCE,
                        TRACER_BEHIND_MIN_SPREAD
                );
                // Convert tracerStart to camera-relative if using player position
                Vec3d finalTracerStart = usePlayerPosition 
                    ? new Vec3d(tracerStart.x - camX, tracerStart.y - camY, tracerStart.z - camZ)
                    : tracerStart;
                tracerBatch.renderLine(data.tracer, finalTracerStart, tracerEnd, ZenyaPlus.tracerLineWidth());
            }
            tracerBatch.flush();
        }

        matrices.pop();
    }

    private int clampAlpha(int value) {
        if (value < 1)   return 1;
        if (value > 255) return 255;
        return value;
    }

    private Color applyOpacity(Color base, int alphaValue) {
        int combinedAlpha = Math.max(0, Math.min(255, Math.round((base.getAlpha() / 255.0f) * alphaValue)));
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), combinedAlpha);
    }

    private Vec3d getLerpedPosCompat(PlayerEntity player, float tickDelta) {
        try {
            // Prefer native interpolation if present in this MC version.
            return player.getLerpedPos(tickDelta);
        } catch (Throwable ignored) {
            double worldX = MathHelper.lerp(tickDelta, player.lastRenderX, player.getX());
            double worldY = MathHelper.lerp(tickDelta, player.lastRenderY, player.getY());
            double worldZ = MathHelper.lerp(tickDelta, player.lastRenderZ, player.getZ());
            return new Vec3d(worldX, worldY, worldZ);
        }
    }

    private String getFriendLookupName(PlayerEntity player) {
        if (player == null) {
            return "";
        }
        try {
            Object profile = player.getGameProfile();
            if (profile != null) {
                // Different mappings/MC versions can expose the profile name differently.
                try {
                    Object v = profile.getClass().getMethod("getName").invoke(profile);
                    if (v instanceof String s && !s.isBlank()) return s;
                } catch (Throwable ignored) {}
                try {
                    Object v = profile.getClass().getMethod("name").invoke(profile);
                    if (v instanceof String s && !s.isBlank()) return s;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return player.getName().getString();
    }

    private boolean isNonFirstPersonView() {
        // Check if freecam is enabled
        if (Freecam.instance != null && Freecam.instance.isEnabled()) {
            return true;
        }
        // Check if perspective is not first person (F5 modes)
        if (mc.options != null) {
            try {
                return !mc.options.getPerspective().isFirstPerson();
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private Vec3d getPlayerHeadPosition(float tickDelta) {
        if (mc.player == null) {
            return Vec3d.ZERO;
        }
        Vec3d playerPos = getLerpedPosCompat(mc.player, tickDelta);
        // Add player eye height to get head position
        double eyeHeight = mc.player.getStandingEyeHeight();
        return new Vec3d(playerPos.x, playerPos.y + eyeHeight, playerPos.z);
    }

    private static final class RenderData {
        final double dx;
        final double dy;
        final double dz;
        final double tracerTargetY;
        final double halfWidth;
        final double height;
        final Color fill;
        final Color tracer;
        final boolean boxVisible;

        private RenderData(double dx, double dy, double dz, double tracerTargetY, double halfWidth, double height, Color fill, Color tracer, boolean boxVisible) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.tracerTargetY = tracerTargetY;
            this.halfWidth = halfWidth;
            this.height = height;
            this.fill = fill;
            this.tracer = tracer;
            this.boxVisible = boxVisible;
        }
    }
}
