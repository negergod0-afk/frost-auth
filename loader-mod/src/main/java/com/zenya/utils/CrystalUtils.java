package com.zenya.utils;

import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Box;
import java.util.List;

public final class CrystalUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static boolean isCrystalPos(BlockPos block) {
        return isObsidianOrBedrock(block) && isPlaceable(block);
    }

    public static boolean isPlaceable(BlockPos block) {
        BlockPos up = block.up();
        if (!mc.world.isAir(up)) {
            return false;
        }
        double d = up.getX();
        double e = up.getY();
        double f = up.getZ();
        List list = mc.world.getOtherEntities(null, new Box(d, e, f, d + 1.0, e + 2.0, f + 1.0));
        return list.isEmpty();
    }

    public static boolean isObsidianOrBedrock(BlockPos pos) {
        return mc.world.getBlockState(pos).isOf(Blocks.OBSIDIAN) || mc.world.getBlockState(pos).isOf(Blocks.BEDROCK);
    }
}
