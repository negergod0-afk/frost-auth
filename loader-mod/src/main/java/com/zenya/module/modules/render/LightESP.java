package com.zenya.module.modules.render;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import com.zenya.utils.RenderUtils;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LightESP extends Module {
    private final Setting<Integer> scanRadius = new Setting<>("Scan Radius", 10, 1, 16);
    private final Setting<Integer> minY = new Setting<>("Min Y", -63, -64, 320);
    private final Setting<Integer> maxY = new Setting<>("Max Y", -30, -64, 320);
    private final Setting<Integer> alpha = new Setting<>("Alpha", 80, 1, 255);
    private final Setting<Color> color = new Setting<>("Color", new Color(255, 255, 0, 255));

    private final Map<ChunkPos, List<LightBlock>> lightBlocks = new ConcurrentHashMap<>();
    private ChunkPos lastPlayerChunk;
    private int scanTimer;

    public LightESP() {
        super("Light ESP", Category.RENDER);
        setDescription("Highlights block light sources in nearby loaded chunks for underground base finding.");
        addSetting(scanRadius);
        addSetting(minY);
        addSetting(maxY);
        addSetting(alpha);
        addSetting(color);
    }

    @Override
    public void onEnable() {
        lightBlocks.clear();
        scanTimer = 0;
        lastPlayerChunk = mc.player == null ? null : mc.player.getChunkPos();
        scanAllChunks();
    }

    @Override
    public void onDisable() {
        lightBlocks.clear();
        lastPlayerChunk = null;
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) {
            lightBlocks.clear();
            return;
        }

        scanTimer++;
        ChunkPos currentChunk = mc.player.getChunkPos();
        boolean movedChunks = lastPlayerChunk == null || !lastPlayerChunk.equals(currentChunk);
        if (movedChunks || scanTimer >= 20) {
            scanAllChunks();
            lastPlayerChunk = currentChunk;
            scanTimer = 0;
        }
    }

    private void scanAllChunks() {
        if (mc.world == null || mc.player == null) {
            return;
        }

        ChunkPos center = mc.player.getChunkPos();
        int radius = scanRadius.getValue();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(center.x + dx, center.z + dz, false);
                if (chunk != null) {
                    scanChunk(chunk);
                }
            }
        }
        lightBlocks.keySet().removeIf(chunkPos ->
                Math.abs(chunkPos.x - center.x) > radius || Math.abs(chunkPos.z - center.z) > radius);
    }

    private void scanChunk(WorldChunk chunk) {
        if (mc.world == null || chunk == null) {
            return;
        }

        ChunkPos chunkPos = chunk.getPos();
        int startX = chunkPos.getStartX();
        int startZ = chunkPos.getStartZ();
        int fromY = Math.min(minY.getValue(), maxY.getValue());
        int toY = Math.max(minY.getValue(), maxY.getValue());
        int worldBottom = mc.world.getBottomY();
        int worldTop = mc.world.getBottomY() + mc.world.getHeight() - 1;
        fromY = Math.max(fromY, worldBottom);
        toY = Math.min(toY, worldTop);

        List<LightBlock> found = new ArrayList<>();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int y = fromY; y <= toY; y++) {
                    pos.set(startX + lx, y, startZ + lz);
                    int lightLevel = mc.world.getLightLevel(LightType.BLOCK, pos);
                    if (lightLevel > 0) {
                        found.add(new LightBlock(pos.toImmutable(), lightLevel));
                    }
                }
            }
        }

        if (found.isEmpty()) {
            lightBlocks.remove(chunkPos);
        } else {
            lightBlocks.put(chunkPos, found);
        }
    }

    @Override
    public void onRender(MatrixStack matrices, float tickDelta) {
        if (mc.world == null || mc.player == null || lightBlocks.isEmpty()) {
            return;
        }

        Camera camera = RenderUtils.getCamera();
        if (camera == null) {
            return;
        }

        Vec3d camPos = RenderUtils.getCameraPos(camera);
        RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(matrices);
        boolean rendered = false;
        for (List<LightBlock> blocks : lightBlocks.values()) {
            for (LightBlock block : blocks) {
                BlockPos pos = block.pos();
                double x = pos.getX() - camPos.x;
                double y = pos.getY() - camPos.y;
                double z = pos.getZ() - camPos.z;
                batch.renderFilledBox(x, y, z, x + 1.0, y + 1.0, z + 1.0, interpolateColor(block.lightLevel()));
                rendered = true;
            }
        }
        if (rendered) {
            batch.flush();
        }
    }

    private Color interpolateColor(int lightLevel) {
        float t = Math.max(0.0f, Math.min(1.0f, (lightLevel - 1) / 14.0f));
        Color target = color.getValue();
        int red = Math.round(19 + (target.getRed() - 19) * t);
        int green = Math.round(19 + (target.getGreen() - 19) * t);
        int blue = Math.round(50 + (target.getBlue() - 50) * t);
        return new Color(red, green, blue, Math.max(1, Math.min(255, alpha.getValue())));
    }

    private record LightBlock(BlockPos pos, int lightLevel) {
    }
}
