package com.zenya.module.modules.donut;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.modules.common.ClientModuleTools;
import com.zenya.setting.Setting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.Color;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BaseBlocksDetection extends Module {
    private static final int TYPE_SPAWNER = 0;
    private static final int TYPE_PISTON = 1;
    private static final int TYPE_DEEPSLATE = 2;
    private static final int TYPE_STORAGE = 3;

    private final Setting<Integer> scanRadius = new Setting<>("Scan Radius", 4, 1, 10);
    private final Setting<Boolean> scanSpawners = new Setting<>("Spawners", true);
    private final Setting<Boolean> scanPistons = new Setting<>("Pistons", true);
    private final Setting<Boolean> scanDeepslate = new Setting<>("Rotated Deepslate", true);
    private final Setting<Boolean> scanStorage = new Setting<>("Storage", true);

    private final Set<ChunkPos> scannedChunks = ClientModuleTools.chunkSet();
    private final Map<BlockPos, ClientModuleTools.BlockMark> foundBlocks = new ConcurrentHashMap<>();
    private int tickCounter;

    public BaseBlocksDetection() {
        super("Base Blocks Detection", Category.DONUT);
        setDescription("Scans loaded chunks for spawners, pistons, rotated deepslate, and storage blocks.");
        addSetting(scanRadius);
        addSetting(scanSpawners);
        addSetting(scanPistons);
        addSetting(scanDeepslate);
        addSetting(scanStorage);
    }

    @Override
    public void onEnable() {
        scannedChunks.clear();
        foundBlocks.clear();
        tickCounter = 0;
    }

    @Override
    public void onDisable() {
        scannedChunks.clear();
        foundBlocks.clear();
    }

    @Override
    public void onWorldChange() {
        scannedChunks.clear();
        foundBlocks.clear();
        tickCounter = 0;
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        if (++tickCounter % 60 != 0) return;
        cleanupDistant();
        ChunkPos center = mc.player.getChunkPos();
        int radius = scanRadius.getValue();
        for (int cx = center.x - radius; cx <= center.x + radius; cx++) {
            for (int cz = center.z - radius; cz <= center.z + radius; cz++) {
                ChunkPos cp = new ChunkPos(cx, cz);
                if (!scannedChunks.add(cp)) continue;
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz, false);
                if (chunk != null) scanChunk(chunk, cp);
            }
        }
    }

    private void scanChunk(WorldChunk chunk, ChunkPos cp) {
        if (scanStorage.getValue() || scanSpawners.getValue()) {
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (scanSpawners.getValue() && be instanceof MobSpawnerBlockEntity) {
                    put(be.getPos(), TYPE_SPAWNER);
                } else if (scanStorage.getValue() && ClientModuleTools.isStorage(be)) {
                    put(be.getPos(), TYPE_STORAGE);
                }
            }
        }

        if (!scanPistons.getValue() && !scanDeepslate.getValue() && !scanStorage.getValue()) return;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int top = mc.world == null ? 320 : mc.world.getTopYInclusive();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int y = chunk.getBottomY(); y <= top; y++) {
                    pos.set(cp.getStartX() + lx, y, cp.getStartZ() + lz);
                    var state = chunk.getBlockState(pos);
                    Block block = state.getBlock();
                    if (scanPistons.getValue() && ClientModuleTools.isPiston(block)) {
                        put(pos.toImmutable(), TYPE_PISTON);
                    } else if (scanDeepslate.getValue() && ClientModuleTools.isRotatedDeepslate(state)) {
                        put(pos.toImmutable(), TYPE_DEEPSLATE);
                    } else if (scanStorage.getValue() && ClientModuleTools.isStorageBlock(block)) {
                        put(pos.toImmutable(), TYPE_STORAGE);
                    } else if (scanSpawners.getValue() && block == Blocks.SPAWNER) {
                        put(pos.toImmutable(), TYPE_SPAWNER);
                    }
                }
            }
        }
    }

    private void put(BlockPos pos, int type) {
        Color color = switch (type) {
            case TYPE_PISTON -> new Color(255, 165, 0, 165);
            case TYPE_DEEPSLATE -> new Color(100, 120, 255, 150);
            case TYPE_STORAGE -> new Color(0, 255, 100, 145);
            default -> new Color(255, 0, 0, 170);
        };
        String label = switch (type) {
            case TYPE_PISTON -> "Piston";
            case TYPE_DEEPSLATE -> "Deepslate";
            case TYPE_STORAGE -> "Storage";
            default -> "Spawner";
        };
        foundBlocks.put(pos, new ClientModuleTools.BlockMark(pos, label, color));
    }

    private void cleanupDistant() {
        if (mc.player == null) return;
        ChunkPos center = mc.player.getChunkPos();
        int radius = scanRadius.getValue() + 2;
        scannedChunks.removeIf(cp -> Math.abs(cp.x - center.x) > radius || Math.abs(cp.z - center.z) > radius);
        foundBlocks.keySet().removeIf(pos -> {
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;
            return Math.abs(cx - center.x) > radius || Math.abs(cz - center.z) > radius;
        });
    }

    @Override
    public void onRender(MatrixStack matrices, float tickDelta) {
        ClientModuleTools.renderBlocks(matrices, foundBlocks.values(), 320.0 * 320.0);
    }
}
