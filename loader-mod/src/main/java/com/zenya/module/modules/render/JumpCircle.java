package com.zenya.module.modules.render;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import com.zenya.utils.RenderUtils;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public final class JumpCircle extends Module {

    private final Setting<Color> circleColor = new Setting<>("Color", new Color(255, 255, 255, 200));
    private final Setting<Integer> fadeTime = new Setting<>("Fade Time", 1200, 100, 3000);
    private final Setting<Float> maxSize = new Setting<>("Max Size", 1.8f, 0.5f, 4.0f);
    private final Setting<Float> lineWidth = new Setting<>("Line Width", 4.0f, 1.0f, 10.0f);

    private final List<Circle> circles = new ArrayList<>();
    private boolean wasJumping = false;

    public JumpCircle() {
        super("Jump Circle", Category.RENDER);
        setDescription("Displays a clean expanding circle when you jump.");
        addSetting(circleColor);
        addSetting(fadeTime);
        addSetting(maxSize);
        addSetting(lineWidth);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        boolean isJumping = (!mc.player.isOnGround() && mc.player.getVelocity().y > 0.0);
        if (isJumping && !wasJumping) {
            circles.add(new Circle(new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()), System.currentTimeMillis()));
        }
        wasJumping = mc.player.getVelocity().y > 0.0 && !mc.player.isOnGround();

        long now = System.currentTimeMillis();
        circles.removeIf(c -> now - c.startTime > fadeTime.getValue());
    }

    @Override
    public void onRender(MatrixStack matrices, float tickDelta) {
        if (circles.isEmpty() || mc.player == null) return;
        Camera camera = RenderUtils.getCamera();
        if (camera == null) return;
        Vec3d cameraPos = RenderUtils.getCameraPos(camera);

        RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(matrices);
        long now = System.currentTimeMillis();

        // 64 segments = much smoother circle than default 30
        final int SEGMENTS = 64;

        for (Circle c : circles) {
            float age = (now - c.startTime) / (float) fadeTime.getValue();
            if (age > 1f) continue;

            float radius = easeOutExpo(age) * maxSize.getValue();
            // Exponential fade-out for a cleaner finish
            float alpha = (float) Math.pow(1.0f - age, 1.5f);

            Color col = circleColor.getValue();
            Color renderCol = new Color(col.getRed(), col.getGreen(), col.getBlue(), (int)(col.getAlpha() * alpha));
            float width = lineWidth.getValue();

            for (int i = 0; i < SEGMENTS; i++) {
                double a1 = i * Math.PI * 2.0 / SEGMENTS;
                double a2 = (i + 1) * Math.PI * 2.0 / SEGMENTS;

                Vec3d p1 = new Vec3d(c.pos.x + Math.sin(a1) * radius, c.pos.y, c.pos.z + Math.cos(a1) * radius);
                Vec3d p2 = new Vec3d(c.pos.x + Math.sin(a2) * radius, c.pos.y, c.pos.z + Math.cos(a2) * radius);

                batch.renderLine(renderCol, p1.subtract(cameraPos), p2.subtract(cameraPos), width);
            }
        }
        batch.flush();
    }

    private float easeOutExpo(float x) {
        return x == 1f ? 1f : 1f - (float)Math.pow(2.0, -10.0 * x);
    }

    private static class Circle {
        final Vec3d pos;
        final long startTime;
        Circle(Vec3d pos, long startTime) {
            this.pos = pos;
            this.startTime = startTime;
        }
    }
}
