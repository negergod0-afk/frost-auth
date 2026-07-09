package com.zenya.module.modules.donut;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.modules.common.ClientModuleTools;
import com.zenya.setting.Setting;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.Color;
import java.util.Map;

public final class BaseChunk extends Module {
    private final Setting<Integer> scanRadius = new Setting<>("Scan Radius", 4, 1, 12);
    private final Setting<Integer> minInhabitedHours = new Setting<>("Min Inhabited Hours", 12, 1, 72);
    private final Setting<Integer> playerRadius = new Setting<>("Player Radius", 6, 1, 24);
    private final Setting<Boolean> requireNearbyPlayer = new Setting<>("Require Nearby Player", true);
    private final Setting<Boolean> fillChunk = new Setting<>("Fill Chunk", false);

    private final Map<ChunkPos, ClientModuleTools.ChunkMark> flaggedChunks = ClientModuleTools.chunkMap();
    private int tickCounter;

    public BaseChunk() {
        super("Base Chunk", Category.DONUT);
        setDescription("Flags highly inhabited chunks, optionally only while another player is nearby.");
        addSetting(scanRadius);
        addSetting(minInhabitedHours);
        addSetting(playerRadius);
        addSetting(requireNearbyPlayer);
        addSetting(fillChunk);
    }

    @Override
    public void onEnable() {
        flaggedChunks.clear();
        tickCounter = 0;
    }

    @Override
    public void onDisable() {
        flaggedChunks.clear();
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        if (++tickCounter % 100 != 0) return;
        if (requireNearbyPlayer.getValue() && !hasNearbyPlayer()) {
            flaggedChunks.clear();
            return;
        }

        flaggedChunks.clear();
        ChunkPos center = mc.player.getChunkPos();
        long threshold = minInhabitedHours.getValue() * 60L * 60L * 20L;
        int radius = scanRadius.getValue();
        for (int cx = center.x - radius; cx <= center.x + radius; cx++) {
            for (int cz = center.z - radius; cz <= center.z + radius; cz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz, false);
                if (chunk == null || chunk.getInhabitedTime() < threshold) continue;
                ChunkPos cp = new ChunkPos(cx, cz);
                double hours = chunk.getInhabitedTime() / 72000.0;
                String label = String.format(java.util.Locale.ROOT, "Base? %.1fh loaded", hours);
                flaggedChunks.put(cp, new ClientModuleTools.ChunkMark(cp, label, new Color(255, 40, 40, 90)));
                ClientModuleTools.chat("Base Chunk", label + " at " + cx + ", " + cz);
            }
        }
    }

    private boolean hasNearbyPlayer() {
        if (mc.world == null || mc.player == null) return false;
        ChunkPos mine = mc.player.getChunkPos();
        int radius = playerRadius.getValue();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            ChunkPos other = player.getChunkPos();
            if (Math.abs(other.x - mine.x) <= radius && Math.abs(other.z - mine.z) <= radius) {
                return true;
            }
        }

        if (mc.getNetworkHandler() == null) return false;
        for (PlayerListEntry ignored : mc.getNetworkHandler().getPlayerList()) {
            return false;
        }
        return false;
    }

    @Override
    public void onRender(MatrixStack matrices, float tickDelta) {
        ClientModuleTools.renderChunks(matrices, flaggedChunks.values(), fillChunk.getValue(), 350.0 * 350.0);
    }
}
