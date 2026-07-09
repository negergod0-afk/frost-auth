package com.zenya.utils.rotation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class RotationUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static Vec3d getPlayerPos(PlayerEntity player) {
        return new Vec3d(player.getX(), player.getY(), player.getZ());
    }

    public static Vec3d rotationToVec(float yaw, float pitch) {
        float f = pitch * ((float)Math.PI / 180);
        float g = -yaw * ((float)Math.PI / 180);
        float h = MathHelper.sin((double)g);
        float i = MathHelper.cos((double)g);
        float j = MathHelper.sin((double)f);
        float k = MathHelper.cos((double)f);
        return new Vec3d((double)(i * j), (double)(-k), (double)(h * j));
    }

    public static Vec3d rotationToVec(PlayerEntity player) {
        return RotationUtils.rotationToVec(player.getYaw(), player.getPitch());
    }

    public static Rotation diff(Rotation rotation1, Rotation rotation2) {
        double yaw = Math.abs(Math.max(rotation1.yaw(), rotation2.yaw()) - Math.min(rotation1.yaw(), rotation2.yaw()));
        double pitch = Math.abs(Math.max(rotation1.pitch(), rotation2.pitch()) - Math.min(rotation1.pitch(), rotation2.pitch()));
        return new Rotation(yaw, pitch);
    }

    public static Rotation lerp(Rotation from, Rotation to, double speed) {
        return new Rotation(MathHelper.lerp((float)speed, (float)from.yaw(), (float)to.yaw()), MathHelper.lerp((float)speed, (float)from.pitch(), (float)to.pitch()));
    }

    public static double distance(Rotation rotation1, Rotation rotation2) {
        Rotation diff = RotationUtils.diff(rotation1, rotation2);
        return diff.yaw() + diff.pitch();
    }

    public static Vec3d getRotationVec() {
        return RotationUtils.rotationToVec((PlayerEntity)mc.player);
    }

    public static Rotation getDirection(Entity entity, Vec3d vec) {
        double dx = vec.x - entity.getX();
        double dy = vec.y - entity.getY();
        double dz = vec.z - entity.getZ();
        double dist = MathHelper.sqrt((float)(dx * dx + dz * dz));
        return new Rotation(MathHelper.wrapDegrees((double)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0)), -MathHelper.wrapDegrees((double)Math.toDegrees(Math.atan2(dy, dist))));
    }

    public static double getAngleToRotation(Rotation rotation) {
        double currentYaw = MathHelper.wrapDegrees((float)mc.player.getYaw());
        double currentPitch = MathHelper.wrapDegrees((float)mc.player.getPitch());
        double diffYaw = MathHelper.wrapDegrees((double)(currentYaw - rotation.yaw()));
        double diffPitch = MathHelper.wrapDegrees((double)(currentPitch - rotation.pitch()));
        return Math.sqrt(diffYaw * diffYaw + diffPitch * diffPitch);
    }

    public static float easeOutBackDegrees(float start, float end, float speed) {
        double c1 = 1.70158;
        double c3 = 2.70158;
        double x = 1.0 - Math.pow(1.0 - (double)speed, 3.0);
        return start + MathHelper.wrapDegrees((float)(end - start)) * (float)(1.0 + c3 * Math.pow(x - 1.0, 3.0) + c1 * Math.pow(x - 1.0, 2.0));
    }
}
