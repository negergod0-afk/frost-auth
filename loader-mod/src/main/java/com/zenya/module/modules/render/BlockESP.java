package com.zenya.module.modules.render;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.BlocksSetting;
import com.zenya.setting.Setting;
import com.zenya.utils.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockESP extends Module {

    private static final int RESCAN_INTERVAL_TICKS = 40;
    private static final int CHUNKS_PER_TICK = 6;
    private static final double BOX_INSET = 0.0625;

    private final BlocksSetting blocks = new BlocksSetting("Blocks");
    private final Setting<Integer> opacity = new Setting<>("Alpha", 110, 1, 255);
    private final Setting<Boolean> esp = new Setting<>("ESP", true);

    private final Map<Long, Set<BlockPos>> cachedBlocks = new ConcurrentHashMap<>();
    private final Map<BlockPos, Block> posTypeMap = new ConcurrentHashMap<>();
    private final ArrayDeque<Long> scanQueue = new ArrayDeque<>();
    private final Set<Long> queuedChunks = new HashSet<>();
    private final Object queueLock = new Object();

    private volatile Set<Block> targets = Collections.emptySet();
    private long lastBlocksVersion = -1L;
    private int tickCounter = 0;
    private boolean fullRescanRequested = true;
    private ChunkPos lastCenterChunk;
    private int lastChunkRadius = -1;

    public BlockESP() {
        super("Block ESP", Category.RENDER);
        setDescription("Renders coloured boxes around any block types you select in nearby loaded chunks.");
        addSetting(blocks);
        addSetting(opacity);
        addSetting(esp);
    }

    @Override
    public void toggle() {
        setEnabled(!isEnabled());
        // No toggle notification / sound for Block ESP
    }

    public Color getCustomBlockColor(Block block) {
        return blocks.getColor(block);
    }

    public void setCustomBlockColor(Block block, Color color) {
        blocks.setColor(block, color);
    }

    public Map<Block, Color> getColorMap() {
        return blocks.getColors();
    }

    @Override
    public void onEnable() {
        clearCaches();
        lastBlocksVersion = -1L;
        fullRescanRequested = true;
        tickCounter = 0;
        lastCenterChunk = null;
        lastChunkRadius = -1;
    }

    @Override
    public void onDisable() {
        clearCaches();
        lastCenterChunk = null;
        lastChunkRadius = -1;
    }

    @Override
    public void onWorldChange() {
        clearCaches();
        fullRescanRequested = true;
        tickCounter = 0;
        lastCenterChunk = null;
        lastChunkRadius = -1;
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) {
            return;
        }

        updateTargets();
        if (targets.isEmpty()) {
            clearCaches();
            return;
        }

        tickCounter++;
        ChunkPos currentChunk = mc.player.getChunkPos();
        int currentChunkRadius = getChunkRadius();
        boolean forceRescan = fullRescanRequested || tickCounter % RESCAN_INTERVAL_TICKS == 0;
        if (forceRescan || lastCenterChunk == null || !lastCenterChunk.equals(currentChunk) || lastChunkRadius != currentChunkRadius) {
            rebuildLoadedChunkQueue(forceRescan);
            fullRescanRequested = false;
            lastCenterChunk = currentChunk;
            lastChunkRadius = currentChunkRadius;
        }

        for (int i = 0; i < CHUNKS_PER_TICK; i++) {
            Long chunkKey;
            synchronized (queueLock) {
                chunkKey = scanQueue.poll();
                if (chunkKey != null) {
                    queuedChunks.remove(chunkKey);
                }
            }
            if (chunkKey == null) {
                break;
            }
            int chunkX = ChunkPos.getPackedX(chunkKey);
            int chunkZ = ChunkPos.getPackedZ(chunkKey);
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(chunkX, chunkZ, false);
            if (chunk != null) {
                scanChunk(chunk);
            }
        }
    }

    @Override
    public void onPacketReceive(Packet<?> packet) {
        if (mc.world == null) {
            return;
        }

        if (packet instanceof ChunkDataS2CPacket chunkData) {
            queueChunk(ChunkPos.toLong(chunkData.getChunkX(), chunkData.getChunkZ()), true);
            return;
        }

        if (packet instanceof ChunkDeltaUpdateS2CPacket deltaUpdate) {
            deltaUpdate.visitUpdates((pos, state) -> queueChunk(new ChunkPos(pos).toLong(), true));
            return;
        }

        if (packet instanceof BlockUpdateS2CPacket blockUpdate) {
            queueChunk(new ChunkPos(blockUpdate.getPos()).toLong(), true);
        }
    }

    @Override
    public void onRender(MatrixStack matrices, float tickDelta) {
        if (mc.world == null || mc.player == null || cachedBlocks.isEmpty()) {
            return;
        }
        if (!esp.getValue()) {
            return;
        }
        final Set<Block> localTargets = this.targets;
        if (localTargets.isEmpty()) {
            return;
        }

        Camera cam = RenderUtils.getCamera();
        if (cam == null) {
            return;
        }

        final int alpha = Math.max(1, Math.min(255, opacity.getValue()));
        Vec3d camPos = RenderUtils.getCameraPos(cam);
        final double camPosX = camPos.x, camPosY = camPos.y, camPosZ = camPos.z;
        final double maxDistanceSq = getMaxRenderDistanceSq();
        final double playerX = mc.player.getX(), playerY = mc.player.getY(), playerZ = mc.player.getZ();
        
        RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(matrices);
        boolean rendered = false;

        Block lastBlock = null;
        Color lastColor = null;

        for (Set<BlockPos> positions : cachedBlocks.values()) {
            for (BlockPos pos : positions) {
                if (pos.getSquaredDistance(playerX, playerY, playerZ) > maxDistanceSq) {
                    continue;
                }

                Block block = posTypeMap.get(pos);
                if (block == null) {
                    BlockState state = mc.world.getBlockState(pos);
                    block = state.getBlock();
                    posTypeMap.put(pos, block);
                }
                
                if (!localTargets.contains(block)) {
                    continue;
                }

                if (block != lastBlock || lastColor == null) {
                    lastBlock = block;
                    lastColor = getBlockColor(block, alpha);
                }
                
                Color color = lastColor;
                double x = pos.getX() - camPosX;
                double y = pos.getY() - camPosY;
                double z = pos.getZ() - camPosZ;

                // Render filled box
                batch.renderFilledBox(
                        x + BOX_INSET,
                        y + BOX_INSET,
                        z + BOX_INSET,
                        x + 1.0 - BOX_INSET,
                        y + 1.0 - BOX_INSET,
                        z + 1.0 - BOX_INSET,
                        color
                );
                
                // Render outline with slightly higher opacity
                Color outlineColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.min(255, color.getAlpha() + 60));
                batch.renderOutlineBox(
                        x + BOX_INSET,
                        y + BOX_INSET,
                        z + BOX_INSET,
                        x + 1.0 - BOX_INSET,
                        y + 1.0 - BOX_INSET,
                        z + 1.0 - BOX_INSET,
                        outlineColor
                );
                rendered = true;
            }
        }

        if (rendered) {
            batch.flush();
        }
    }

    private void updateTargets() {
        long version = blocks.getVersion();
        if (version == lastBlocksVersion) {
            return;
        }

        lastBlocksVersion = version;
        // Snapshot: only blocks explicitly selected by the user.
        targets = Set.copyOf(blocks.getSelectedBlocks());
        clearCaches();
        fullRescanRequested = true;
    }

    public boolean isSelected(Block block) {
        updateTargets();
        return blocks.contains(block);
    }

    public int getSelectedCount() {
        updateTargets();
        return blocks.size();
    }

    private void rebuildLoadedChunkQueue(boolean forceRescan) {
        if (mc.world == null || mc.player == null) {
            return;
        }

        int viewDist = getChunkRadius();
        ChunkPos center = mc.player.getChunkPos();
        List<WorldChunk> loadedChunks = new ArrayList<>();
        Set<Long> loadedChunkKeys = new HashSet<>();
        for (int x = -viewDist; x <= viewDist; x++) {
            for (int z = -viewDist; z <= viewDist; z++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(center.x + x, center.z + z, false);
                if (chunk != null) {
                    loadedChunks.add(chunk);
                    loadedChunkKeys.add(chunk.getPos().toLong());
                }
            }
        }

        loadedChunks.sort(Comparator.comparingInt(chunk -> getChunkDistanceSq(center, chunk.getPos())));

        synchronized (queueLock) {
            scanQueue.removeIf(chunkKey -> !loadedChunkKeys.contains(chunkKey));
            queuedChunks.retainAll(loadedChunkKeys);
            for (WorldChunk chunk : loadedChunks) {
                long chunkKey = chunk.getPos().toLong();
                boolean shouldQueue = forceRescan || !cachedBlocks.containsKey(chunkKey);
                if (shouldQueue && queuedChunks.add(chunkKey)) {
                    scanQueue.addLast(chunkKey);
                }
            }
        }

        pruneOutOfRange(center, viewDist);
    }

    private void queueChunk(long chunkKey, boolean prioritized) {
        synchronized (queueLock) {
            if (prioritized && queuedChunks.contains(chunkKey)) {
                scanQueue.remove(chunkKey);
                scanQueue.addFirst(chunkKey);
                return;
            }

            if (!queuedChunks.add(chunkKey)) {
                return;
            }

            if (prioritized) {
                scanQueue.addFirst(chunkKey);
            } else {
                scanQueue.add(chunkKey);
            }
        }
    }

    private void scanChunk(WorldChunk chunk) {
        Set<Block> localTargets = this.targets;
        if (localTargets.isEmpty()) {
            return;
        }
        int worldBottom = mc.world.getBottomY();
        int worldTopExclusive = mc.world.getBottomY() + mc.world.getHeight();
        int minSection = mc.world.getBottomSectionCoord();

        ChunkPos chunkPos = chunk.getPos();
        long chunkKey = chunkPos.toLong();
        Set<BlockPos> oldSet = cachedBlocks.get(chunkKey);
        Set<BlockPos> newSet = new HashSet<>();

        ChunkSection[] sections = chunk.getSectionArray();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            ChunkSection section = sections[sectionIndex];
            if (section == null || section.isEmpty()) {
                continue;
            }

            int sectionYBase = (minSection + sectionIndex) * 16;
            if (sectionYBase + 16 <= worldBottom || sectionYBase >= worldTopExclusive) {
                continue;
            }

            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localY = 0; localY < 16; localY++) {
                        BlockState state = section.getBlockState(localX, localY, localZ);
                        Block block = state.getBlock();
                        if (!localTargets.contains(block)) {
                            continue;
                        }

                        BlockPos pos = new BlockPos(chunkPos.getStartX() + localX, sectionYBase + localY, chunkPos.getStartZ() + localZ);
                        newSet.add(pos);
                        posTypeMap.put(pos, block);
                        posTypeMap.put(pos, block);
                    }
                }
            }
        }

        if (oldSet != null) {
            for (BlockPos pos : oldSet) {
                if (!newSet.contains(pos)) {
                    posTypeMap.remove(pos);
                }
            }
        }

        if (newSet.isEmpty()) {
            removeChunkCache(chunkKey);
            return;
        }

        cachedBlocks.put(chunkKey, newSet);
    }

    private int getChunkDistanceSq(ChunkPos origin, ChunkPos target) {
        int dx = target.x - origin.x;
        int dz = target.z - origin.z;
        return dx * dx + dz * dz;
    }

    private int getChunkRadius() {
        return mc.options.getClampedViewDistance();
    }

    private double getMaxRenderDistanceSq() {
        double maxDistance = (getChunkRadius() * 16.0D) + 16.0D;
        return maxDistance * maxDistance;
    }

    private void pruneOutOfRange(ChunkPos center, int chunkRadius) {
        List<Long> toRemove = new ArrayList<>();
        for (Long chunkKey : cachedBlocks.keySet()) {
            ChunkPos chunkPos = new ChunkPos(ChunkPos.getPackedX(chunkKey), ChunkPos.getPackedZ(chunkKey));
            if (Math.abs(chunkPos.x - center.x) > chunkRadius || Math.abs(chunkPos.z - center.z) > chunkRadius) {
                toRemove.add(chunkKey);
            }
        }

        for (Long chunkKey : toRemove) {
            removeChunkCache(chunkKey);
        }
    }

    private void removeChunkCache(long chunkKey) {
        Set<BlockPos> removed = cachedBlocks.remove(chunkKey);
        if (removed == null) {
            return;
        }
        for (BlockPos pos : removed) {
            posTypeMap.remove(pos);
        }
    }

    private Color getBlockColor(Block block, int alphaValue) {
        Color custom = blocks.getColor(block);
        if (custom != null) {
            return new Color(custom.getRed(), custom.getGreen(), custom.getBlue(), alphaValue);
        }
        return new Color(255, 255, 0, alphaValue);
    }

    private void clearCaches() {
        cachedBlocks.clear();
        posTypeMap.clear();
        synchronized (queueLock) {
            scanQueue.clear();
            queuedChunks.clear();
        }
    }
}
