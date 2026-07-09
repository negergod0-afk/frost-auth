package com.zenya.module.modules.render;

import com.zenya.module.Category;
import com.zenya.module.Module;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

public class Freecam extends Module {

    private static final int MIN_SCROLL_SPEED = 1;
    private static final int MAX_SCROLL_SPEED = 20;
    private static final int SCROLL_SPEED_STEP = 1;

    public final Vector3d currentPosition = new Vector3d();
    public final Vector3d previousPosition = new Vector3d();
    private final Vector3d velocity = new Vector3d();

    public float yaw;
    public float pitch;
    public float previousYaw;
    public float previousPitch;

    public final com.zenya.setting.Setting<Integer> speed = new com.zenya.setting.Setting<>("Speed", 1, MIN_SCROLL_SPEED, MAX_SCROLL_SPEED);
    public final com.zenya.setting.Setting<Double> lookSpeed = new com.zenya.setting.Setting<>("Look Speed", 1.0, 0.1, 5.0);
    private boolean smoothing = true;
    private float currentSpeed;

    // WalkOn removed — player stays still while camera moves.
    public net.minecraft.util.PlayerInput capturedInput;
    public net.minecraft.util.math.Vec2f capturedMovement;

    private Perspective savedPerspective;
    private boolean savedChunkCullingEnabled;
    private long lastFrameTime;

    private float savedPlayerYaw;
    private float savedPlayerPitch;

    public static Freecam instance;

    public Freecam() {
        super("Freecam", Category.RENDER);
        setDescription("Detaches the camera from your player so you can fly around and look at the world while your body stays in place.");
        instance = this;
        addSetting(speed);
        addSetting(lookSpeed);
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null) {
            this.toggle();
            return;
        }

        savedPerspective = mc.options.getPerspective();
        savedChunkCullingEnabled = mc.chunkCullingEnabled;
        mc.chunkCullingEnabled = false;

        savedPlayerYaw = mc.player.getYaw();
        savedPlayerPitch = mc.player.getPitch();

        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();

        Vec3d eyePos = mc.player.getCameraPosVec(1.0f);
        currentPosition.set(eyePos.x, eyePos.y, eyePos.z);
        previousPosition.set(eyePos.x, eyePos.y, eyePos.z);

        previousYaw = yaw;
        previousPitch = pitch;

        lastFrameTime = System.currentTimeMillis();
        velocity.set(0, 0, 0);
        currentSpeed = getConfiguredSpeed();
        captureLivePlayerInput();
        if (capturedInput == null || !hasAnyMovement(capturedInput)) {
            mc.player.setVelocity(0.0, mc.player.getVelocity().y, 0.0);
        }
    }

    private void captureLivePlayerInput() {
        boolean fwd    = mc.options.forwardKey.isPressed();
        boolean back   = mc.options.backKey.isPressed();
        boolean left   = mc.options.leftKey.isPressed();
        boolean right  = mc.options.rightKey.isPressed();
        boolean jump   = mc.options.jumpKey.isPressed();
        boolean sneak  = mc.options.sneakKey.isPressed();
        boolean sprint = mc.options.sprintKey.isPressed();
        capturedInput = new net.minecraft.util.PlayerInput(fwd, back, left, right, jump, sneak, sprint);
        float f = fwd == back ? 0f : (fwd ? 1f : -1f);
        float g = left == right ? 0f : (left ? 1f : -1f);
        capturedMovement = new net.minecraft.util.math.Vec2f(g, f);
    }

    private static boolean hasAnyMovement(net.minecraft.util.PlayerInput in) {
        return in.forward() || in.backward() || in.left() || in.right() || in.jump() || in.sneak();
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            mc.player.setYaw(savedPlayerYaw);
            mc.player.setPitch(savedPlayerPitch);
            mc.player.setHeadYaw(savedPlayerYaw);
            mc.player.setBodyYaw(savedPlayerYaw);
        }
        if (savedPerspective != null) {
            mc.options.setPerspective(savedPerspective);
        } else {
            mc.options.setPerspective(Perspective.FIRST_PERSON);
        }
        mc.chunkCullingEnabled = savedChunkCullingEnabled;
        capturedInput = null;
        capturedMovement = null;
        velocity.set(0, 0, 0);
        currentSpeed = getConfiguredSpeed();
    }

    @Override
    public void onTick() {
        if (mc.player == null) {
            return;
        }

        
        mc.player.setYaw(savedPlayerYaw);
        mc.player.setPitch(savedPlayerPitch);
        mc.player.setHeadYaw(savedPlayerYaw);
        mc.player.setBodyYaw(savedPlayerYaw);
    }

    public void updateCameraMovement() {
        if (mc.player == null)
            return;

        previousPosition.set(currentPosition);
        previousYaw = yaw;
        previousPitch = pitch;

        long currentTime = System.currentTimeMillis();
        float deltaTime = (float) (currentTime - lastFrameTime) / 1000.0f;
        lastFrameTime = currentTime;

        deltaTime = Math.min(deltaTime, 0.1f);
        if (deltaTime < 0.001f)
            deltaTime = 0.016f;

        // Always sync currentSpeed from the setting so slider changes take effect live.
        currentSpeed = getConfiguredSpeed();

        float yawRad = (float) Math.toRadians(yaw);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double rightX = -Math.cos(yawRad);
        double rightZ = -Math.sin(yawRad);

        double moveX = 0.0, moveY = 0.0, moveZ = 0.0;
        double moveSpeed = currentSpeed * 0.5;

        if (mc.options != null && mc.options.sprintKey.isPressed()) {
            moveSpeed *= 2.0;
        }

        if (mc.options.forwardKey.isPressed()) {
            moveX += forwardX * moveSpeed;
            moveZ += forwardZ * moveSpeed;
        }
        if (mc.options.backKey.isPressed()) {
            moveX -= forwardX * moveSpeed;
            moveZ -= forwardZ * moveSpeed;
        }
        if (mc.options.rightKey.isPressed()) {
            moveX += rightX * moveSpeed;
            moveZ += rightZ * moveSpeed;
        }
        if (mc.options.leftKey.isPressed()) {
            moveX -= rightX * moveSpeed;
            moveZ -= rightZ * moveSpeed;
        }
        if (mc.options.jumpKey.isPressed()) {
            moveY += moveSpeed;
        }
        if (mc.options.sneakKey.isPressed()) {
            moveY -= moveSpeed;
        }

        double multiplier = 2.0;

        if (smoothing) {
            double lerpFactor = 1.0 - Math.pow(0.001, deltaTime);
            velocity.x = MathHelper.lerp(lerpFactor, velocity.x, moveX * multiplier);
            velocity.y = MathHelper.lerp(lerpFactor, velocity.y, moveY * multiplier);
            velocity.z = MathHelper.lerp(lerpFactor, velocity.z, moveZ * multiplier);
        } else {
            velocity.set(moveX * multiplier, moveY * multiplier, moveZ * multiplier);
        }

        currentPosition.x += velocity.x * (double) deltaTime;
        currentPosition.y += velocity.y * (double) deltaTime;
        currentPosition.z += velocity.z * (double) deltaTime;
    }

    



    public void onScrollWheel(double scrollDelta) {
        int newVal = speed.getValue() + (int) Math.signum(scrollDelta);
        speed.setValue(MathHelper.clamp(newVal, MIN_SCROLL_SPEED, MAX_SCROLL_SPEED));
    }

    public void updateRotation(double deltaYaw, double deltaPitch) {
        yaw += (float) deltaYaw;
        pitch += (float) deltaPitch;
        yaw = MathHelper.wrapDegrees(yaw);
        pitch = MathHelper.clamp(pitch, -90.0f, 90.0f);
    }

    public double getInterpolatedX(float partialTicks) {
        return MathHelper.lerp((double) partialTicks, previousPosition.x, currentPosition.x);
    }

    public double getInterpolatedY(float partialTicks) {
        return MathHelper.lerp((double) partialTicks, previousPosition.y, currentPosition.y);
    }

    public double getInterpolatedZ(float partialTicks) {
        return MathHelper.lerp((double) partialTicks, previousPosition.z, currentPosition.z);
    }

    public float getInterpolatedYaw(float partialTicks) {
        return MathHelper.lerp(partialTicks, previousYaw, yaw);
    }

    public float getInterpolatedPitch(float partialTicks) {
        return MathHelper.lerp(partialTicks, previousPitch, pitch);
    }

    public float getLookSensitivity() {
        return (float) (0.5f * lookSpeed.getValue());
    }

    public void adjustSpeed(double scrollAmount) {
        if (scrollAmount == 0.0) return;
        int newVal = speed.getValue() + (int) Math.signum(scrollAmount) * SCROLL_SPEED_STEP;
        speed.setValue(MathHelper.clamp(newVal, MIN_SCROLL_SPEED, MAX_SCROLL_SPEED));
    }

    private float getConfiguredSpeed() {
        return MathHelper.clamp(speed.getValue().floatValue(), (float) MIN_SCROLL_SPEED, (float) MAX_SCROLL_SPEED);
    }
}
