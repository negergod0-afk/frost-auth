package com.zenya.module.modules.smps;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.modules.common.ClientModuleTools;
import com.zenya.setting.Setting;
import com.zenya.utils.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PlayerChunkFinder extends Module {
    private final Setting<Boolean> detectUnnaturalBlocks = new Setting<>("Detect Unnatural Blocks", true);
    private final Setting<Integer> renderRadius = new Setting<>("Grid Radius", 8, 1, 32);
    private final Setting<Double> renderY = new Setting<>("Render Y", -60.0D, -64.0D, 320.0D);
    private final Setting<Boolean> filledEsp = new Setting<>("Filled ESP", true);
    private final Setting<Color> sideColor = new Setting<>("Side Color", new Color(255, 82, 82, 80));
    private final Setting<Color> lineColor = new Setting<>("Line Color", new Color(255, 82, 82, 255));

    private final Map<ChunkPos, String> modifiedChunks = new LinkedHashMap<>();

    public PlayerChunkFinder() {
        super("Player Chunk Finder", Category.SMPS);
        setDescription("Detects underground player-modified chunks.");
        addSetting(detectUnnaturalBlocks);
        addSetting(renderRadius);
        addSetting(renderY);
        addSetting(filledEsp);
        addSetting(sideColor);
        addSetting(lineColor);
    }

    @Override
    public void onEnable() {
        modifiedChunks.clear();
    }

    @Override
    public void onDisable() {
        modifiedChunks.clear();
    }

    @Override
    public void onPacketReceive(Packet<?> packet) {
        if (!(packet instanceof ChunkDataS2CPacket chunkPacket) || mc.world == null || mc.player == null) return;

        ChunkPos cp = new ChunkPos(chunkPacket.getChunkX(), chunkPacket.getChunkZ());
        if (modifiedChunks.containsKey(cp)) return;

        WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cp.x, cp.z, false);
        if (chunk == null) return;

        String reason = scanChunk(cp);
        if (reason == null) return;

        modifiedChunks.put(cp, reason);
        mc.player.sendMessage(Text.literal("\u00a7b[Player Chunk Finder]\u00a7r Modified chunk at "
                + cp.x + ", " + cp.z + " \u00a77(" + reason + ")"), false);
    }

    @Override
    public void onRender(MatrixStack matrices, float tickDelta) {
        if (mc.world == null || mc.player == null || modifiedChunks.isEmpty()) return;

        Camera camera = RenderUtils.getCamera();
        if (camera == null) return;

        Vec3d cameraPos = RenderUtils.getCameraPos(camera);
        int radius = renderRadius.getValue();
        int px = mc.player.getChunkPos().x;
        int pz = mc.player.getChunkPos().z;
        double y = renderY.getValue() - cameraPos.y;

        RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(matrices);
        boolean rendered = false;
        for (ChunkPos cp : modifiedChunks.keySet()) {
            if (Math.abs(cp.x - px) > radius || Math.abs(cp.z - pz) > radius) continue;

            double x = cp.getStartX() - cameraPos.x;
            double z = cp.getStartZ() - cameraPos.z;
            if (filledEsp.getValue()) {
                batch.renderFilledBox(x, y, z, x + 16.0D, y + 0.12D, z + 16.0D, sideColor.getValue());
            }
            batch.renderOutlineBox(x, y, z, x + 16.0D, y + 0.12D, z + 16.0D, lineColor.getValue());
            rendered = true;
        }
        if (rendered) batch.flush();
    }

    private String scanChunk(ChunkPos cp) {
        int startX = cp.getStartX();
        int startZ = cp.getStartZ();
        int minY = Math.max(mc.world.getBottomY(), -64);
        int maxY = Math.min(mc.world.getBottomY() + mc.world.getHeight() - 1, 0);

        for (int x = startX; x < startX + 16; x++) {
            for (int z = startZ; z < startZ + 16; z++) {
                BlockPos.Mutable pos = new BlockPos.Mutable(x, minY, z);
                for (int y = minY; y < maxY; y++) {
                    pos.setY(y);
                    BlockState state = mc.world.getBlockState(pos);
                    if (ClientModuleTools.isRotatedDeepslate(state)) {
                        return "MODIFIED_DEEPSLATE";
                    }
                    if (detectUnnaturalBlocks.getValue() && isUnnaturalUndergroundBlock(state.getBlock())) {
                        return "UNNATURAL";
                    }
                }
            }
        }

        return null;
    }

    private static boolean isUnnaturalUndergroundBlock(Block block) {
        return block == Blocks.COBBLESTONE
                || block == Blocks.COBBLED_DEEPSLATE
                || block == Blocks.OAK_PLANKS
                || block == Blocks.SPRUCE_PLANKS
                || block == Blocks.BIRCH_PLANKS
                || block == Blocks.JUNGLE_PLANKS
                || block == Blocks.ACACIA_PLANKS
                || block == Blocks.DARK_OAK_PLANKS
                || block == Blocks.MANGROVE_PLANKS
                || block == Blocks.CHERRY_PLANKS
                || block == Blocks.BAMBOO_PLANKS
                || block == Blocks.CRIMSON_PLANKS
                || block == Blocks.WARPED_PLANKS
                || block == Blocks.TORCH
                || block == Blocks.WALL_TORCH
                || block == Blocks.LADDER
                || block == Blocks.RAIL
                || block == Blocks.CRAFTING_TABLE
                || block == Blocks.FURNACE
                || block == Blocks.BLAST_FURNACE
                || block == Blocks.SMOKER
                || block == Blocks.CHEST
                || block == Blocks.TRAPPED_CHEST
                || block == Blocks.BARREL
                || block == Blocks.GLASS;
    }
}
