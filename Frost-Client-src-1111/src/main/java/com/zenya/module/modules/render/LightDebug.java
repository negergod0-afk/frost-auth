package com.zenya.module.modules.render;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.modules.common.ClientModuleTools;
import com.zenya.setting.Setting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.Color;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class LightDebug extends Module {
    private static final int MIN_Y = -64;
    private static final int MAX_Y = -35;

    private final Setting<Integer> red = new Setting<>("Red", 255, 0, 255);
    private final Setting<Integer> green = new Setting<>("Green", 255, 0, 255);
    private final Setting<Integer> blue = new Setting<>("Blue", 0, 0, 255);
    private final Setting<Integer> alpha = new Setting<>("Alpha", 100, 0, 255);
    private final Setting<Integer> minBlockLight = new Setting<>("Min Block Light", 5, 1, 15);
    private final List<ClientModuleTools.BlockMark> litBlocks = new CopyOnWriteArrayList<>();
    private final Set<ChunkPos> scannedChunks = ConcurrentHashMap.newKeySet();
    private int rescanTimer;

    public LightDebug() {
        super("Light Debug", Category.RENDER);
        setDescription("Highlights suspicious block light from Y=-64 to Y=-35.");
        addSetting(red);
        addSetting(green);
        addSetting(blue);
        addSetting(alpha);
        addSetting(minBlockLight);
    }

    @Override
    public void onEnable() {
        litBlocks.clear();
        scannedChunks.clear();
        rescanTimer = 0;
    }

    @Override
    public void onDisable() {
        litBlocks.clear();
        scannedChunks.clear();
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        if (++rescanTimer >= 400) {
            litBlocks.clear();
            scannedChunks.clear();
            rescanTimer = 0;
        }
        ChunkPos center = mc.player.getChunkPos();
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                ChunkPos cp = new ChunkPos(center.x + dx, center.z + dz);
                if (!scannedChunks.add(cp)) continue;
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cp.x, cp.z, false);
                if (chunk != null) scanChunk(chunk, cp);
            }
        }
    }

    private void scanChunk(WorldChunk chunk, ChunkPos cp) {
        Color color = new Color(red.getValue(), green.getValue(), blue.getValue(), alpha.getValue());
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int y = MIN_Y; y <= MAX_Y; y++) {
                    pos.set(cp.getStartX() + lx, y, cp.getStartZ() + lz);
                    if (!ClientModuleTools.hasBlockLight(pos, minBlockLight.getValue())) continue;
                    if (!ClientModuleTools.hasExposedFace(pos)) continue;
                    litBlocks.add(new ClientModuleTools.BlockMark(pos.toImmutable(), "Light", color));
                }
            }
        }
    }

    @Override
    public void onRender(MatrixStack matrices, float tickDelta) {
        ClientModuleTools.renderBlocks(matrices, litBlocks, 260.0 * 260.0);
    }
}
