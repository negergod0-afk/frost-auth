package com.zenya.module.modules.smps;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import com.zenya.utils.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sus Chunk Finder — scans loaded chunks for suspicious natural block patterns
 * that may indicate player-modified terrain (stashes, farms, etc).
 *
 * Detections:
 *   - Jungle vines: 34-50 stacked vine blocks tall
 *   - Cave vines: 7-50 stacked cave vine blocks
 *   - Tall kelp: 24-50 tall kelp plants
 *   - Sweet berries: 3-50 grown sweet berry bushes
 *   - Max sugar cane: 5-50 sugar cane at full height (age 15)
 *   - Cocoa beans: any cocoa beans detected
 *   - Pure amethyst: amethyst blocks OR large amethyst buds with NO budding amethyst in the chunk
 *
 * Overlay renders at Y=58 in the configured color (default red).
 * Chunks with >5 chests, >10 hoppers, OR >4 crafters get an additional purple flashing overlay.
 */
public final class SusChunkFinder extends Module {

    // ---- Settings -------------------------------------------------------
    private final Setting<Integer> sensitivity  = new Setting<>("Sensitivity",  5, 1, 20);
    private final Setting<Integer> simDistance  = new Setting<>("Sim Distance", 8, 1, 20);
    private final Setting<Color>   overlayColor = new Setting<>("Color", new Color(220, 30, 30, 160));
    private final Setting<Integer> alpha        = new Setting<>("Alpha", 160, 0, 255);
    private final Setting<Boolean> detectJungleVines = new Setting<>("Jungle Vines", true);
    private final Setting<Boolean> detectCaveVines = new Setting<>("Cave Vines", true);
    private final Setting<Boolean> detectKelp = new Setting<>("Kelp", true);
    private final Setting<Boolean> detectSweetBerries = new Setting<>("Sweet Berries", true);
    private final Setting<Boolean> detectSugarCane = new Setting<>("Sugar Cane", true);
    private final Setting<Boolean> detectCocoaBeans = new Setting<>("Cocoa Beans", true);
    private final Setting<Boolean> detectAmethyst = new Setting<>("Amethyst", true);

    // ---- State ----------------------------------------------------------
    private static final int CHUNKS_PER_TICK = 3;
    private static final int RESCAN_TICKS    = 400;

    /** Chunks flagged as suspicious with their reason. */
    private final Map<ChunkPos, ChunkHit> hits       = new ConcurrentHashMap<>();
    /** Chunks with chest/hopper/crafter overload → purple flashing. */
    private final Set<ChunkPos> storageOverload       = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> scanned               = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> queued                = ConcurrentHashMap.newKeySet();
    private final ArrayDeque<ChunkPos> scanQueue      = new ArrayDeque<>();
    private int rescanTimer = 0;

    // For purple flash animation
    private long flashTimer = 0;
    private boolean flashOn = false;

    public SusChunkFinder() {
        super("Sus Chunk Finder", Category.DONUT);
        setDescription("Finds suspicious chunks via natural block pattern analysis.");
        addSetting(sensitivity);
        addSetting(simDistance);
        addSetting(overlayColor);
        addSetting(alpha);
        addSetting(detectJungleVines);
        addSetting(detectCaveVines);
        addSetting(detectKelp);
        addSetting(detectSweetBerries);
        addSetting(detectSugarCane);
        addSetting(detectCocoaBeans);
        addSetting(detectAmethyst);
    }

    @Override
    public void onEnable() { clear(); }

    @Override
    public void onDisable() { clear(); }

    @Override
    public void onWorldChange() { clear(); }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;

        // Flash timer update (fast speed ~200ms)
        long now = System.currentTimeMillis();
        if (now - flashTimer > 200L) {
            flashOn = !flashOn;
            flashTimer = now;
        }

        if (++rescanTimer >= RESCAN_TICKS) {
            clear();
        }

        enqueueChunks();
        for (int i = 0; i < CHUNKS_PER_TICK; i++) {
            ChunkPos cp = scanQueue.poll();
            if (cp == null) break;
            queued.remove(cp);
            if (!scanned.add(cp)) continue;
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cp.x, cp.z, false);
            if (chunk != null) scanChunk(chunk, cp);
        }
    }

    @Override
    public void onRender(MatrixStack matrices, float tickDelta) {
        if (mc.world == null || mc.player == null) return;
        if (hits.isEmpty() && storageOverload.isEmpty()) return;

        Camera cam = RenderUtils.getCamera();
        if (cam == null) return;
        Vec3d cameraPos = RenderUtils.getCameraPos(cam);

        // Overlay Y is fixed at 58
        final double OVERLAY_Y = 58.0;

        // Calculate overlay dimensions from sensitivity
        int[] dims = overlayDims(sensitivity.getValue());
        int wChunks = dims[0];
        int hChunks = dims[1];

        RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(matrices);
        boolean rendered = false;

        Color baseColor = overlayColor.getValue();
        int alphaValue = Math.max(0, Math.min(255, alpha.getValue()));

        // Draw sus chunk overlays - optimiert für Performance
        for (Map.Entry<ChunkPos, ChunkHit> entry : hits.entrySet()) {
            try {
                ChunkPos origin = entry.getKey();
                double cx = origin.getStartX() + 8 - cameraPos.x;
                double cz = origin.getStartZ() + 8 - cameraPos.z;
                double ry = OVERLAY_Y - cameraPos.y;

                double halfW = wChunks * 8.0;
                double halfH = hChunks * 8.0;

                // Berechne Alpha basierend auf Slider (0-255)
                int fillAlpha = Math.max(20, alphaValue / 3);  // Mindestens 20 Alpha für Sichtbarkeit
                int outlineAlpha = Math.min(255, alphaValue + 20);

                Color fill = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), fillAlpha);
                Color outline = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), outlineAlpha);

                batch.renderFilledBox(cx - halfW, ry, cz - halfH, cx + halfW, ry + 0.15, cz + halfH, fill);
                batch.renderOutlineBox(cx - halfW, ry, cz - halfH, cx + halfW, ry + 0.20, cz + halfH, outline);
                rendered = true;
            } catch (Exception e) {
                com.zenya.ZenyaClient.LOGGER.warn("Error rendering sus chunk", e);
            }
        }

        // Draw purple flashing overlay für Chest/Hopper/Crafter overload
        if (flashOn) {
            try {
                Color purple = new Color(160, 0, 255, 100);
                Color purpleOutline = new Color(180, 0, 255, 180);
                for (ChunkPos cp : storageOverload) {
                    double cx = cp.getStartX() + 8 - cameraPos.x;
                    double cz = cp.getStartZ() + 8 - cameraPos.z;
                    double ry = OVERLAY_Y - cameraPos.y - 0.05;

                    batch.renderFilledBox(cx - 8, ry - 0.1, cz - 8, cx + 8, ry + 0.10, cz + 8, purple);
                    batch.renderOutlineBox(cx - 8, ry - 0.1, cz - 8, cx + 8, ry + 0.14, cz + 8, purpleOutline);
                    rendered = true;
                }
            } catch (Exception e) {
                com.zenya.ZenyaClient.LOGGER.warn("Error rendering storage overload", e);
            }
        }

        if (rendered) {
            try {
                batch.flush();
            } catch (Exception e) {
                com.zenya.ZenyaClient.LOGGER.warn("Error flushing render batch", e);
            }
        }
    }

    // ---- Scanning logic -------------------------------------------------

    private void enqueueChunks() {
        ChunkPos center = mc.player.getChunkPos();
        int radius = Math.min(simDistance.getValue(), viewDist());
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkPos cp = new ChunkPos(center.x + dx, center.z + dz);
                if (scanned.contains(cp) || !queued.add(cp)) continue;
                scanQueue.offer(cp);
            }
        }
    }

    private void scanChunk(WorldChunk chunk, ChunkPos cp) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        int startX = cp.getStartX();
        int startZ = cp.getStartZ();
        int bottom  = mc.world.getBottomY();
        int top     = mc.world.getTopYInclusive();

        // Storage counters
        int chestCount   = 0;
        int hopperCount  = 0;
        int crafterCount = 0;

        // Detection counters per column
        // We aggregate across the whole chunk for height-based detections
        int[] kelpHeights     = new int[16 * 16]; // indexed by lx*16+lz
        int[] caveVineHeights = new int[16 * 16];
        int[] vineHeights     = new int[16 * 16];
        int[] sugarHeights    = new int[16 * 16];
        int sweetBerryCount   = 0;
        int cocoaCount        = 0;
        int amethystCount     = 0;
        int largeAmethystBudCount = 0;
        boolean hasAnyBudding = false;

        // Count block entities (chests, hoppers, crafters)
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof ChestBlockEntity || be instanceof TrappedChestBlockEntity) chestCount++;
            else if (be instanceof HopperBlockEntity) hopperCount++;
            else {
                // Crafter detection by block type
                BlockPos bePos = be.getPos();
                Block block = chunk.getBlockState(bePos).getBlock();
                if (block == Blocks.CRAFTER) crafterCount++;
            }
        }

        // Scan all blocks
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int col = lx * 16 + lz;
                // Reset column height tracking
                int kelpRun = 0, caveVineRun = 0, vineRun = 0, sugarRun = 0;
                int maxKelp = 0, maxCaveVine = 0, maxVine = 0, maxSugar = 0;

                for (int y = bottom; y <= top; y++) {
                    m.set(startX + lx, y, startZ + lz);
                    BlockState state = chunk.getBlockState(m);
                    Block block = state.getBlock();

                    // --- Kelp (Blocks.KELP or KELP_PLANT) ---
                    if (block == Blocks.KELP || block == Blocks.KELP_PLANT) {
                        kelpRun++;
                        if (kelpRun > maxKelp) maxKelp = kelpRun;
                    } else {
                        kelpRun = 0;
                    }

                    // --- Cave Vines (CAVE_VINES or CAVE_VINES_PLANT) ---
                    if (block == Blocks.CAVE_VINES || block == Blocks.CAVE_VINES_PLANT) {
                        caveVineRun++;
                        if (caveVineRun > maxCaveVine) maxCaveVine = caveVineRun;
                    } else {
                        caveVineRun = 0;
                    }

                    // --- Jungle Vines (VINE) ---
                    if (block == Blocks.VINE) {
                        vineRun++;
                        if (vineRun > maxVine) maxVine = vineRun;
                    } else {
                        vineRun = 0;
                    }

                    // --- Sugar Cane (SUGAR_CANE) ---
                    if (block == Blocks.SUGAR_CANE) {
                        sugarRun++;
                        if (sugarRun > maxSugar) maxSugar = sugarRun;
                    } else {
                        sugarRun = 0;
                    }

                    // --- Sweet Berries (SWEET_BERRY_BUSH, age 3 = fully grown) ---
                    if (block == Blocks.SWEET_BERRY_BUSH && state.contains(Properties.AGE_3)) {
                        try {
                            int age = state.get(Properties.AGE_3);
                            if (age >= 3) sweetBerryCount++;
                        } catch (Exception ignored) {}
                    }

                    // --- Cocoa Beans ---
                    if (block == Blocks.COCOA) {
                        cocoaCount++;
                    }

                    // --- Amethyst ---
                    // Count pure amethyst blocks (not budding)
                    if (block == Blocks.AMETHYST_BLOCK) {
                        amethystCount++;
                    }
                    // Count large amethyst buds (fully grown, level 3)
                    if (block == Blocks.LARGE_AMETHYST_BUD) {
                        largeAmethystBudCount++;
                    }
                    // Check for budding amethyst blocks
                    if (block == Blocks.BUDDING_AMETHYST) {
                        hasAnyBudding = true;
                    }
                }

                kelpHeights[col]     = maxKelp;
                caveVineHeights[col] = maxCaveVine;
                vineHeights[col]     = maxVine;
                sugarHeights[col]    = maxSugar;
            }
        }

        // --- Evaluate detections ----
        String reason = null;

        // Jungle vines: 34-50 tall
        if (detectJungleVines.getValue()) {
            for (int h : vineHeights) {
                if (h >= 34 && h <= 50) { reason = "Jungle Vines (" + h + "tall)"; break; }
            }
        }

        // Cave vines: 7-50 tall
        if (reason == null && detectCaveVines.getValue()) {
            for (int h : caveVineHeights) {
                if (h >= 7 && h <= 50) { reason = "Cave Vines (" + h + "tall)"; break; }
            }
        }

        // Kelp: 24-50 tall
        if (reason == null && detectKelp.getValue()) {
            for (int h : kelpHeights) {
                if (h >= 24 && h <= 50) { reason = "Tall Kelp (" + h + "tall)"; break; }
            }
        }

        // Sweet berries: 3-50 grown bushes in chunk
        if (reason == null && detectSweetBerries.getValue() && sweetBerryCount >= 3 && sweetBerryCount <= 50) {
            reason = "Sweet Berries (" + sweetBerryCount + ")";
        }

        // Sugar cane: 5-50 tall blocks in column
        if (reason == null && detectSugarCane.getValue()) {
            for (int h : sugarHeights) {
                if (h >= 5 && h <= 50) { reason = "Sugar Cane (" + h + "tall)"; break; }
            }
        }

        // Cocoa beans: any detected
        if (reason == null && detectCocoaBeans.getValue() && cocoaCount > 0) {
            reason = "Cocoa Beans (" + cocoaCount + ")";
        }

        // Amethyst: detect amethyst blocks OR large amethyst buds, but only if NO budding amethyst exists
        // This flags farms where buds have been harvested or amethyst blocks placed without the budding source
        if (reason == null && detectAmethyst.getValue() && !hasAnyBudding) {
            int totalAmethyst = amethystCount + largeAmethystBudCount;
            if (totalAmethyst > 0) {
                reason = "Pure Amethyst (" + amethystCount + " blocks, " + largeAmethystBudCount + " large buds)";
            }
        }

        if (reason != null) {
            ChunkHit existing = hits.get(cp);
            if (existing == null) {
                hits.put(cp, new ChunkHit(cp, reason));
            }
        }

        // Storage overload check
        boolean overloaded = chestCount > 5 || hopperCount > 10 || crafterCount > 4;
        if (overloaded) {
            if (storageOverload.add(cp)) {
            }
        } else {
            storageOverload.remove(cp);
        }
    }

    // ---- Overlay size from sensitivity 1-20 ----------------------------
    // Sensitivity maps to overlay size in chunks (wChunks x hChunks)
    // Values are designed to feel like increasing detection radius
    private static int[] overlayDims(int sens) {
        return switch (sens) {
            case  1 -> new int[]{1, 2};
            case  2 -> new int[]{2, 2};
            case  3 -> new int[]{2, 3};
            case  4 -> new int[]{3, 3};
            case  5 -> new int[]{3, 4};
            case  6 -> new int[]{4, 4};
            case  7 -> new int[]{4, 5};
            case  8 -> new int[]{5, 5};
            case  9 -> new int[]{5, 6};
            case 10 -> new int[]{6, 6};
            case 11 -> new int[]{6, 7};
            case 12 -> new int[]{7, 7};
            case 13 -> new int[]{7, 8};
            case 14 -> new int[]{8, 8};
            case 15 -> new int[]{8, 9};
            case 16 -> new int[]{9, 9};
            case 17 -> new int[]{9, 10};
            case 18 -> new int[]{10, 10};
            case 19 -> new int[]{10, 11};
            default -> new int[]{11, 11};
        };
    }

    // ---- Helpers --------------------------------------------------------

    private void sendChat(String msg) {
        if (mc.player != null) {
            mc.player.sendMessage(net.minecraft.text.Text.literal("\u00a7c" + msg), false);
        }
    }

    private int viewDist() {
        return mc.options == null ? 8 : mc.options.getClampedViewDistance();
    }

    private void clear() {
        hits.clear();
        storageOverload.clear();
        scanned.clear();
        queued.clear();
        scanQueue.clear();
        rescanTimer = 0;
    }

    // Inner record
    private record ChunkHit(ChunkPos pos, String reason) {}
}
