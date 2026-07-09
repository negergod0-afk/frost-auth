package com.zenya.module.modules.render;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.utils.RenderUtils;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public final class PistonESP extends Module {

    private static final Color PISTON_COLOR = new Color(173, 216, 230, 150); // Light Blue

    public PistonESP() {
        super("Piston ESP", Category.RENDER);
        setDescription("Highlights moving and non-moving pistons with a light blue color.");
    }

    @Override
    public void onRender(MatrixStack matrices, float tickDelta) {
        if (mc.world == null || mc.player == null) return;

        Camera cam = RenderUtils.getCamera();
        if (cam == null) return;

        Vec3d camPos = RenderUtils.getCameraPos(cam);
        final double camPosX = camPos.x, camPosY = camPos.y, camPosZ = camPos.z;

        List<BlockPos> pistons = new ArrayList<>();

        // Scan for moving pistons (BlockEntities)
        int viewDist = mc.options.getClampedViewDistance();
        int centerX = mc.player.getChunkPos().x;
        int centerZ = mc.player.getChunkPos().z;

        for (int cx = -viewDist; cx <= viewDist; cx++) {
            for (int cz = -viewDist; cz <= viewDist; cz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(centerX + cx, centerZ + cz, false);
                if (chunk == null) continue;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof PistonBlockEntity) {
                        pistons.add(be.getPos());
                    }
                }
            }
        }

        if (pistons.isEmpty()) return;

        RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(matrices);
        for (BlockPos pos : pistons) {
            double x = pos.getX() - camPosX;
            double y = pos.getY() - camPosY;
            double z = pos.getZ() - camPosZ;
            
            batch.renderFilledBox(x + 0.05, y + 0.05, z + 0.05, x + 0.95, y + 0.95, z + 0.95, PISTON_COLOR);
            batch.renderOutlineBox(x + 0.05, y + 0.05, z + 0.05, x + 0.95, y + 0.95, z + 0.95, PISTON_COLOR);
        }
        batch.flush();
    }
}
