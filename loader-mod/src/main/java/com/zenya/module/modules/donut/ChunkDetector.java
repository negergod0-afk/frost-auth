package com.zenya.module.modules.donut;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.modules.common.ClientModuleTools;
import com.zenya.setting.Setting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.Color;
import java.util.Map;
import java.util.Set;

public final class ChunkDetector extends Module {
    private final Setting<Integer> scanRadius = new Setting<>("Scan Radius", 4, 1, 10);
    private final Setting<Integer> minScore = new Setting<>("Min Score", 3, 1, 40);
    private final Setting<Integer> beehiveWeight = new Setting<>("Beehive Weight", 3, 1, 10);
    private final Setting<Integer> amethystWeight = new Setting<>("Amethyst Weight", 1, 1, 10);
    private final Setting<Boolean> fillChunk = new Setting<>("Fill Chunk", false);

    private final Set<ChunkPos> scannedChunks = ClientModuleTools.chunkSet();
    private final Map<ChunkPos, ClientModuleTools.ChunkMark> flaggedChunks = ClientModuleTools.chunkMap();
    private int tickCounter;

    public ChunkDetector() {
        super("Chunk Detector", Category.DONUT);
        setDescription("Scores old grown chunks using full beehives and lit deep amethyst clusters.");
        addSetting(scanRadius);
        addSetting(minScore);
        addSetting(beehiveWeight);
        addSetting(amethystWeight);
        addSetting(fillChunk);
    }

    @Override
    public void onEnable() {
        scannedChunks.clear();
        flaggedChunks.clear();
        tickCounter = 0;
    }

    @Override
    public void onDisable() {
        scannedChunks.clear();
        flaggedChunks.clear();
    }

    @Override
    public void onWorldChange() {
        scannedChunks.clear();
        flaggedChunks.clear();
        tickCounter = 0;
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        if (++tickCounter % 60 != 0) return;

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
        if (mc.world == null) return;
        int beehives = 0;
        int amethyst = 0;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int top = Math.min(80, mc.world.getTopYInclusive());
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = cp.getStartX() + lx;
                int z = cp.getStartZ() + lz;
                for (int y = mc.world.getBottomY(); y <= top; y++) {
                    pos.set(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    Block block = state.getBlock();
                    if (ClientModuleTools.isBeeNest(block)
                            && state.contains(Properties.HONEY_LEVEL)
                            && state.get(Properties.HONEY_LEVEL) >= 5) {
                        beehives++;
                    } else if (y < 0
                            && ClientModuleTools.isAmethyst(block)
                            && mc.world.getLightLevel(LightType.BLOCK, pos) > 0) {
                        amethyst++;
                    }
                }
            }
        }

        int score = beehives * beehiveWeight.getValue() + amethyst * amethystWeight.getValue();
        if (score < minScore.getValue()) return;
        String label = "Old Chunk | B:" + beehives + " A:" + amethyst + " S:" + score;
        flaggedChunks.put(cp, new ClientModuleTools.ChunkMark(cp, label, new Color(180, 0, 255, 95)));
        ClientModuleTools.chat("Chunk Detector", label + " at " + cp.x + ", " + cp.z);
    }

    @Override
    public void onRender(MatrixStack matrices, float tickDelta) {
        ClientModuleTools.renderChunks(matrices, flaggedChunks.values(), fillChunk.getValue(), 320.0 * 320.0);
    }
}
