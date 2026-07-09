package com.zenya.utils;

import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.Box;
import com.zenya.utils.rotation.Rotation;
import com.zenya.utils.rotation.RotationUtils;
import java.util.List;
import java.util.stream.Stream;

public final class BlockUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static boolean isBlockAt(BlockPos pos, Block block) {
        return mc.world.getBlockState(pos).getBlock() == block;
    }

    public static void lookAtBlock(BlockPos pos) {
        Rotation rotation = RotationUtils.getDirection((Entity)mc.player, pos.toCenterPos());
        mc.player.setPitch((float)rotation.pitch());
        mc.player.setYaw((float)rotation.yaw());
    }

    public static boolean isChargedAnchor(BlockPos pos) {
        if (BlockUtils.isBlockAt(pos, Blocks.RESPAWN_ANCHOR)) {
            return (Integer)mc.world.getBlockState(pos).get((Property)RespawnAnchorBlock.CHARGES) != 0;
        }
        return false;
    }

    public static boolean isEmptyAnchor(BlockPos pos) {
        if (BlockUtils.isBlockAt(pos, Blocks.RESPAWN_ANCHOR)) {
            return (Integer)mc.world.getBlockState(pos).get((Property)RespawnAnchorBlock.CHARGES) == 0;
        }
        return false;
    }

    public static boolean canPlaceAbove(BlockPos block) {
        BlockPos up = block.up();
        if (!mc.world.isAir(up)) {
            return false;
        }
        double x = up.getX();
        double y = up.getY();
        double z = up.getZ();
        List<Entity> list = mc.world.getOtherEntities(null, new Box(x, y, z, x + 1.0, y + 1.0, z + 1.0));
        list.removeIf(entity -> entity instanceof ItemEntity);
        return list.isEmpty();
    }

    public static Stream<BlockPos> iterateBetween(BlockPos from, BlockPos to) {
        BlockPos min = new BlockPos(Math.min(from.getX(), to.getX()), Math.min(from.getY(), to.getY()), Math.min(from.getZ(), to.getZ()));
        BlockPos max = new BlockPos(Math.max(from.getX(), to.getX()), Math.max(from.getY(), to.getY()), Math.max(from.getZ(), to.getZ()));
        Stream<BlockPos> stream = Stream.iterate(min, pos -> {
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            if (++x > max.getX()) {
                x = min.getX();
                ++y;
            }
            if (y > max.getY()) {
                y = min.getY();
                ++z;
            }
            if (z > max.getZ()) {
                throw new IllegalStateException("Stream limit didn't work.");
            }
            return new BlockPos(x, y, z);
        });
        int limit = (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
        return stream.limit(limit);
    }
}
