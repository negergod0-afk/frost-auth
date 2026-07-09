package com.zenya.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.entity.effect.StatusEffects;
import java.util.Objects;
import java.util.stream.Stream;

public final class WorldUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static boolean isDeadBodyNearby() {
        return mc.world.getPlayers().stream().filter(e -> e != mc.player).filter(e -> e.squaredDistanceTo((Entity)mc.player) <= 36.0).anyMatch(LivingEntity::isDead);
    }

    public static Entity findNearestEntity(PlayerEntity toPlayer, float range, boolean canSee) {
        float mr = Float.MAX_VALUE;
        Entity entity = null;
        for (Entity e : mc.world.getEntities()) {
            float d = e.distanceTo((Entity)toPlayer);
            if (e == toPlayer || !(d <= range) || mc.player.canSee(e) != canSee || !(d < mr)) continue;
            mr = d;
            entity = e;
        }
        return entity;
    }

    public static double distance(Vec3d fromVec, Vec3d toVec) {
        return Math.sqrt(Math.pow(toVec.z - fromVec.z, 2.0) + Math.pow(toVec.y - fromVec.y, 2.0) + Math.pow(toVec.x - fromVec.x, 2.0));
    }

    public static PlayerEntity findNearestPlayer(PlayerEntity toPlayer, float range, boolean canSee, boolean excludeFriends) {
        float minRange = Float.MAX_VALUE;
        PlayerEntity minPlayer = null;
        for (PlayerEntity player : mc.world.getPlayers()) {
            float dist = (float)WorldUtils.distance(new Vec3d(toPlayer.getX(), toPlayer.getY(), toPlayer.getZ()), new Vec3d(player.getX(), player.getY(), player.getZ()));
            if (player == toPlayer || !(dist <= range) || player.canSee((Entity)toPlayer) != canSee || !(dist < minRange)) continue;
            minRange = dist;
            minPlayer = player;
        }
        return minPlayer;
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
        return WorldUtils.rotationToVec(player.getYaw(), player.getPitch());
    }

    public static HitResult getHitResult(double range) {
        return WorldUtils.getHitResult((PlayerEntity)mc.player, false, mc.player.getYaw(), mc.player.getPitch(), range);
    }

    public static HitResult getHitResult(PlayerEntity entity, boolean ignoreInvisibles, float yaw, float pitch, double range) {
        if (entity == null || mc.world == null) {
            return null;
        }
        double d = range;
        Vec3d cameraPosVec = entity.getCameraPosVec(1.0f);
        Vec3d rotationVec = WorldUtils.rotationToVec(yaw, pitch);
        Vec3d target = cameraPosVec.add(rotationVec.z * d, rotationVec.y * d, rotationVec.x * d);
        HitResult result = mc.world.raycast(new RaycastContext(cameraPosVec, target, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, (Entity)entity));
        double e = d * d;
        d = range;
        if (result != null) {
            e = result.getPos().squaredDistanceTo(cameraPosVec);
        }
        EntityHitResult entityHitResult = ProjectileUtil.raycast((Entity)entity, (Vec3d)cameraPosVec, (Vec3d)(cameraPosVec.add(rotationVec.z * d, rotationVec.y * d, rotationVec.x * d)), (Box)(entity.getBoundingBox().stretch(rotationVec.multiply(d)).expand(1.0, 1.0, 1.0)), entityx -> !entityx.isSpectator() && entityx.isAlive() && (!entityx.isInvisible() || !ignoreInvisibles), (double)e);
        if (entityHitResult != null) {
            Vec3d vec3d4 = entityHitResult.getPos();
            double g = cameraPosVec.squaredDistanceTo(vec3d4);
            if (g < e || result == null) {
                result = entityHitResult;
            }
        }
        return result;
    }

    public static void interactBlock(BlockHitResult blockHit, boolean swingHand) {
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHit);
        if (result.isAccepted() && swingHand) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    public static Stream<WorldChunk> getLoadedChunks() {
        int dist = Math.max(2, mc.options.getViewDistance().getValue()) + 3;
        int diameter = dist * 2 + 1;
        ChunkPos center = mc.player.getChunkPos();
        ChunkPos min = new ChunkPos(center.x - dist, center.z - dist);
        ChunkPos max = new ChunkPos(center.x + dist, center.z + dist);
        return Stream.iterate(min, pos -> {
            int x = pos.x;
            int z = pos.z;
            if (++x > max.x) {
                x = min.x;
                ++z;
            }
            if (z > max.z) {
                throw new IllegalStateException("Stream limit didn't work.");
            }
            return new ChunkPos(x, z);
        }).limit((long)diameter * (long)diameter).filter(c -> mc.world.isChunkLoaded(c.x, c.z)).map(c -> mc.world.getChunk(c.x, c.z)).filter(Objects::nonNull);
    }

    public static boolean isShieldFacingAway(PlayerEntity player) {
        if (mc.player != null && player != null) {
            Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            Vec3d targetPos = new Vec3d(player.getX(), player.getY(), player.getZ());
            Vec3d directionToPlayer = playerPos.subtract(targetPos).normalize();
            float yaw = player.getYaw();
            float pitch = player.getPitch();
            Vec3d facingDirection = new Vec3d(-Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)), -Math.sin(Math.toRadians(pitch)), Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch))).normalize();
            double dotProduct = facingDirection.dotProduct(directionToPlayer);
            return dotProduct < 0.0;
        }
        return false;
    }

    public static boolean isWeapon(ItemStack itemStack) {
        return itemStack.isIn(ItemTags.SWORDS) || itemStack.getItem() instanceof AxeItem || itemStack.getItem() == Items.MACE;
    }

    public static boolean canCrit(PlayerEntity player, Entity target) {
        return player.getAttackCooldownProgress(0.5f) > 0.9f && player.fallDistance > 0.0 && !player.isOnGround() && !player.hasVehicle() && !player.isSprinting() && !player.hasStatusEffect(StatusEffects.BLINDNESS) && target instanceof LivingEntity;
    }

    public static void hitEntity(Entity entity, boolean swingHand) {
        mc.interactionManager.attackEntity((PlayerEntity)mc.player, entity);
        if (swingHand) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }
}
