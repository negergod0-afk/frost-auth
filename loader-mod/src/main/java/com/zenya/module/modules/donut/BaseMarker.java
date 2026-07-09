package com.zenya.module.modules.donut;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.utils.RenderUtils;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.Color;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BaseMarker extends Module {

    private static final int MARKER_COLOR = 0x5500FF00;

    private final Set<ChunkPos> markedChunks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> scannedChunks = ConcurrentHashMap.newKeySet();
    private long lastScan = 0L;

    public BaseMarker() {
        super("BaseMarker", Category.DONUT);
        setDescription("Highlights chunks with large storage setups.");
    }

    @Override
    public void onEnable() {
        markedChunks.clear();
        scannedChunks.clear();
    }

    @Override
    public void onDisable() {
        markedChunks.clear();
        scannedChunks.clear();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastScan < 1500L) {
            return;
        }
        lastScan = now;

        ChunkPos center = mc.player.getChunkPos();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                ChunkPos cp = new ChunkPos(center.x + dx, center.z + dz);
                if (!scannedChunks.add(cp)) {
                    continue;
                }
                scanChunk(cp);
            }
        }
    }

    private void scanChunk(ChunkPos cp) {
        if (mc.world == null) {
            return;
        }
        WorldChunk chunk;
        try {
            chunk = mc.world.getChunk(cp.x, cp.z);
        } catch (Exception ignored) {
            return;
        }
        if (chunk == null) {
            return;
        }

        int chests = 0;
        int spawners = 0;
        int hoppers = 0;

        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof ChestBlockEntity
                    || be instanceof TrappedChestBlockEntity
                    || be instanceof BarrelBlockEntity
                    || be instanceof ShulkerBoxBlockEntity) {
                chests++;
            } else if (be instanceof MobSpawnerBlockEntity) {
                spawners++;
            } else if (be instanceof HopperBlockEntity) {
                hoppers++;
            }
        }

        if (chests >= 20 || spawners >= 1 || hoppers >= 5) {
            if (!markedChunks.contains(cp)) {
                markedChunks.add(cp);
                try {
                    if (mc.player != null) {
                        mc.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, 1.0f);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void onRender(MatrixStack matrices, float tickDelta) {
        if (!isEnabled() || mc.player == null || markedChunks.isEmpty()) {
            return;
        }

        Camera cam = RenderUtils.getCamera();
        if (cam == null) {
            return;
        }

        Vec3d cameraPos = RenderUtils.getCameraPos(cam);
        Color color = new Color(MARKER_COLOR, true);
        RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(matrices);

        for (ChunkPos cp : markedChunks) {
            if (cameraPos.squaredDistanceTo(cp.getCenterX(), 64.0, cp.getCenterZ()) > 90000) {
                continue;
            }
            double rx = cp.getStartX() - cameraPos.x;
            double rz = cp.getStartZ() - cameraPos.z;
            double ry = 30.0 - cameraPos.y;
            batch.renderFilledBox(rx, ry, rz, rx + 16.0, ry + 0.12, rz + 16.0, color);
        }

        batch.flush();
    }
}
