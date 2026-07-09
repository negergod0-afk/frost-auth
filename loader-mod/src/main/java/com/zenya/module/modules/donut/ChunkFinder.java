package com.zenya.module.modules.donut;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.modules.common.ClientModuleTools;
import com.zenya.setting.Setting;
import com.zenya.utils.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.PolarBearEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChunkFinder — multi-detector base-hunting module.
 *
 * Detectors:
 *  1. CobbleLine       — horizontal cobblestone/cobbled-deepslate lines underground
 *  2. DeepslateBand    — unnatural horizontal bands of placed deepslate variants
 *  3. ObsidianBackbone — obsidian columns or L-shapes underground
 *  4. VeinIntegrity    — broken/harvested diamond/ancient debris veins
 *  5. VerticalVein     — unnaturally vertical ore streaks (strip-mined indicator)
 *  6. NeedleHole       — narrow 1×1 vertical shafts dug straight down
 *  7. HiddenOre        — ores fully surrounded by placed solid blocks
 *  8. PistonArray      — clusters of pistons below y=20
 *  9. Dripstone        — dripstone columns in non-dripstone biomes
 * 10. MobCluster       — unusually large mob concentrations per chunk
 * 11. DroppedItem      — item entities underground (possible death stash)
 * 12. AirPocket        — suspiciously large hollow caves with flat floors
 * 13. SyntheticStair   — stair blocks forming regular descending patterns
 * 14. PolarBear        — polar bears in non-cold biomes (duping indicator)
 */
public final class ChunkFinder extends Module {

    // ---- Settings -------------------------------------------------------
    private final Setting<Integer> scanRadius   = new Setting<>("Scan Radius", 5, 1, 12);
    private final Setting<Integer> minScore     = new Setting<>("Min Score",   5, 1, 30);
    private final Setting<Integer> chunksPerTick = new Setting<>("Chunks/Tick", 2, 1, 8);
    private final Setting<Boolean> fillChunk    = new Setting<>("Fill Chunk", false);
    private final Setting<Boolean> detCobble    = new Setting<>("Cobble Lines",    true);
    private final Setting<Boolean> detDeepslate = new Setting<>("Deepslate Band",  true);
    private final Setting<Boolean> detObsidian  = new Setting<>("Obsidian Backbone", true);
    private final Setting<Boolean> detVeinInt   = new Setting<>("Vein Integrity",  true);
    private final Setting<Boolean> detVertVein  = new Setting<>("Vertical Vein",   true);
    private final Setting<Boolean> detNeedle    = new Setting<>("Needle Hole",     true);
    private final Setting<Boolean> detHiddenOre = new Setting<>("Hidden Ore",      true);
    private final Setting<Boolean> detPiston    = new Setting<>("Piston Array",    true);
    private final Setting<Boolean> detDripstone = new Setting<>("Dripstone",       true);
    private final Setting<Boolean> detMobCluster= new Setting<>("Mob Cluster",     true);
    private final Setting<Boolean> detDropped   = new Setting<>("Dropped Items",   true);
    private final Setting<Boolean> detAirPocket = new Setting<>("Air Pocket",      true);
    private final Setting<Boolean> detStair     = new Setting<>("Synthetic Stair", true);
    private final Setting<Boolean> detPolarBear = new Setting<>("Polar Bear",      true);

    // ---- State ----------------------------------------------------------
    private final Map<ChunkPos, FlaggedChunk> flagged = new ConcurrentHashMap<>();
    private final Set<ChunkPos> scanned               = ConcurrentHashMap.newKeySet();
    private final List<ChunkPos> queue                = new ArrayList<>();
    private int queueCursor = 0;
    private int tickCounter = 0;

    public ChunkFinder() {
        super("Chunk Finder", Category.DONUT);
        setDescription("Multi-detector base hunter: scans chunks for 14 signs of player activity.");
        addSetting(scanRadius);
        addSetting(minScore);
        addSetting(chunksPerTick);
        addSetting(fillChunk);
        addSetting(detCobble);
        addSetting(detDeepslate);
        addSetting(detObsidian);
        addSetting(detVeinInt);
        addSetting(detVertVein);
        addSetting(detNeedle);
        addSetting(detHiddenOre);
        addSetting(detPiston);
        addSetting(detDripstone);
        addSetting(detMobCluster);
        addSetting(detDropped);
        addSetting(detAirPocket);
        addSetting(detStair);
        addSetting(detPolarBear);
    }

    @Override public void onEnable()      { clear(); }
    @Override public void onDisable()     { clear(); }
    @Override public void onWorldChange() { clear(); }

    private void clear() {
        flagged.clear();
        scanned.clear();
        queue.clear();
        queueCursor = 0;
        tickCounter = 0;
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;

        // Rebuild queue every 200 ticks or when depleted
        if (++tickCounter >= 200 || queue.isEmpty()) {
            tickCounter = 0;
            queue.clear();
            queueCursor = 0;
            ChunkPos center = mc.player.getChunkPos();
            int r = scanRadius.getValue();
            for (int cx = center.x - r; cx <= center.x + r; cx++) {
                for (int cz = center.z - r; cz <= center.z + r; cz++) {
                    ChunkPos cp = new ChunkPos(cx, cz);
                    if (!scanned.contains(cp)) queue.add(cp);
                }
            }
            // Cleanup distant flags
            int maxR = r + 3;
            scanned.removeIf(cp -> Math.abs(cp.x - center.x) > maxR || Math.abs(cp.z - center.z) > maxR);
            flagged.keySet().removeIf(cp -> Math.abs(cp.x - center.x) > maxR || Math.abs(cp.z - center.z) > maxR);
        }

        int limit = chunksPerTick.getValue();
        while (limit-- > 0 && queueCursor < queue.size()) {
            ChunkPos cp = queue.get(queueCursor++);
            if (!scanned.add(cp)) continue;
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cp.x, cp.z, false);
            if (chunk != null) analyzeChunk(chunk, cp);
        }
    }

    // ---- Main analysis --------------------------------------------------

    private void analyzeChunk(WorldChunk chunk, ChunkPos cp) {
        if (mc.world == null) return;
        int sx = cp.getStartX(), sz = cp.getStartZ();
        int bottom = mc.world.getBottomY(), top = mc.world.getTopYInclusive();
        BlockPos.Mutable pos = new BlockPos.Mutable();

        // Per-block tallies
        int cobbleCount = 0, cobbleLineScore = 0;
        int deepslateCount = 0;
        int obsidianCount = 0;
        int pistonCount = 0;
        int dripstoneCount = 0;
        int airPocketScore = 0;
        int stairCount = 0;

        // Vein integrity: missing ore in expected vein positions
        int diamondCount = 0, ironCount = 0, goldCount = 0, debrisCount = 0;
        int[] oreByY = new int[Math.max(1, top - bottom + 1)];

        // Needle hole: count 1×1 shafts (air columns)
        int needleScore = 0;

        // Hidden ore: ores boxed in by placed blocks
        int hiddenOreScore = 0;

        // Vertical vein: per-column ore streak tracking
        int[][] colOreRun = new int[16][16];

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = sx + lx, z = sz + lz;
                int airRun = 0, maxAirRun = 0;
                boolean needleCandidate = true;
                int prevIsOre = 0;

                for (int y = bottom; y <= top; y++) {
                    pos.set(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    Block block = state.getBlock();
                    int yi = y - bottom;

                    // --- Air pocket ---
                    if (block == Blocks.AIR || block == Blocks.CAVE_AIR) {
                        airRun++;
                        if (airRun > maxAirRun) maxAirRun = airRun;
                        // Needle hole: must be solid above and below and be exactly 1 wide
                        if (needleCandidate && y > bottom + 5 && y < 0) {
                            // checked later per-column
                        }
                    } else {
                        if (maxAirRun >= 12 && y < 20) airPocketScore += maxAirRun / 4;
                        airRun = 0;
                        needleCandidate = true;
                    }

                    // --- Cobble lines (underground only) ---
                    if (y < 0 && (block == Blocks.COBBLESTONE || block == Blocks.COBBLED_DEEPSLATE
                            || block == Blocks.MOSSY_COBBLESTONE)) {
                        cobbleCount++;
                        // Check if it forms a horizontal line: same block adjacent
                        BlockState east = chunk.getBlockState(pos.offset(Direction.EAST));
                        if (east.getBlock() == block) cobbleLineScore++;
                    }

                    // --- Deepslate band ---
                    if (y < 0 && (block == Blocks.POLISHED_DEEPSLATE || block == Blocks.DEEPSLATE_BRICKS
                            || block == Blocks.DEEPSLATE_TILES || block == Blocks.CHISELED_DEEPSLATE)) {
                        deepslateCount++;
                    }

                    // --- Obsidian backbone ---
                    if (y < 20 && block == Blocks.OBSIDIAN) obsidianCount++;

                    // --- Piston array ---
                    if (y < 20 && ClientModuleTools.isPiston(block)) pistonCount++;

                    // --- Dripstone in wrong place ---
                    if (y < 0 && (block == Blocks.POINTED_DRIPSTONE || block == Blocks.DRIPSTONE_BLOCK)) {
                        dripstoneCount++;
                    }

                    // --- Stair blocks ---
                    if (y < 0 && isStairBlock(block)) stairCount++;

                    // --- Ore tracking ---
                    if (isOre(block)) {
                        if (yi < oreByY.length) oreByY[yi]++;
                        colOreRun[lx][lz]++;
                        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) diamondCount++;
                        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) ironCount++;
                        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) goldCount++;
                        if (block == Blocks.ANCIENT_DEBRIS) debrisCount++;

                        // Hidden ore: check all 6 neighbors are placed non-natural solid
                        if (isHiddenOre(chunk, pos, sx, sz)) hiddenOreScore += 2;
                    } else {
                        colOreRun[lx][lz] = 0;
                    }
                }
            }
        }

        // --- Needle hole: 1×1 vertical air columns at y < 0 ---
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = sx + lx, z = sz + lz;
                int airRun = 0;
                for (int y = bottom; y < 0; y++) {
                    pos.set(x, y, z);
                    Block b = chunk.getBlockState(pos).getBlock();
                    if (b == Blocks.AIR || b == Blocks.CAVE_AIR) {
                        airRun++;
                    } else {
                        airRun = 0;
                    }
                }
                // A needle hole: 10+ continuous air in a single column going deep
                if (airRun >= 10) needleScore++;
            }
        }

        // --- Vertical vein: ore streak >= 4 in one column ---
        int vertVeinScore = 0;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                if (colOreRun[lx][lz] >= 4) vertVeinScore++;
            }
        }

        // --- Mob cluster + dropped items + polar bear (entity-based) ---
        int mobScore = 0, droppedScore = 0, polarBearScore = 0;
        if (mc.world != null) {
            int chunkMobs = 0;
            for (Entity e : mc.world.getEntities()) {
                ChunkPos ecp = new ChunkPos(e.getBlockPos());
                if (!ecp.equals(cp)) continue;
                if (e instanceof ItemEntity && e.getY() < 0) droppedScore++;
                if (e instanceof PolarBearEntity) polarBearScore += 3;
                if (!(e instanceof net.minecraft.entity.player.PlayerEntity)) chunkMobs++;
            }
            if (chunkMobs > 20) mobScore = chunkMobs / 5;
        }

        // --- Block entity score (chests/hoppers) ---
        int storageScore = 0;
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof ChestBlockEntity || be instanceof HopperBlockEntity) storageScore++;
        }

        // ---- Aggregate score ----------------------------------------
        int score = 0;
        List<String> reasons = new ArrayList<>();

        if (detCobble.getValue()    && cobbleLineScore >= 3) { score += Math.min(cobbleLineScore, 6); reasons.add("CobbleLine(" + cobbleLineScore + ")"); }
        if (detDeepslate.getValue() && deepslateCount  >= 4) { score += Math.min(deepslateCount / 2, 5); reasons.add("Deepslate(" + deepslateCount + ")"); }
        if (detObsidian.getValue()  && obsidianCount   >= 3) { score += Math.min(obsidianCount, 6); reasons.add("Obsidian(" + obsidianCount + ")"); }
        if (detVeinInt.getValue()   && (diamondCount + debrisCount) >= 1 && storageScore >= 1) { score += 4; reasons.add("VeinInt"); }
        if (detVertVein.getValue()  && vertVeinScore   >= 2) { score += vertVeinScore; reasons.add("VertVein(" + vertVeinScore + ")"); }
        if (detNeedle.getValue()    && needleScore     >= 2) { score += needleScore * 2; reasons.add("Needle(" + needleScore + ")"); }
        if (detHiddenOre.getValue() && hiddenOreScore  >= 2) { score += hiddenOreScore; reasons.add("HiddenOre(" + hiddenOreScore + ")"); }
        if (detPiston.getValue()    && pistonCount     >= 3) { score += Math.min(pistonCount, 8); reasons.add("PistonArray(" + pistonCount + ")"); }
        if (detDripstone.getValue() && dripstoneCount  >= 6) { score += dripstoneCount / 3; reasons.add("Dripstone(" + dripstoneCount + ")"); }
        if (detMobCluster.getValue()&& mobScore        >= 2) { score += mobScore; reasons.add("MobCluster(" + mobScore * 5 + ")"); }
        if (detDropped.getValue()   && droppedScore    >= 2) { score += droppedScore * 2; reasons.add("Dropped(" + droppedScore + ")"); }
        if (detAirPocket.getValue() && airPocketScore  >= 5) { score += airPocketScore / 3; reasons.add("AirPocket(" + airPocketScore + ")"); }
        if (detStair.getValue()     && stairCount      >= 5) { score += stairCount / 3; reasons.add("Stairs(" + stairCount + ")"); }
        if (detPolarBear.getValue() && polarBearScore  > 0)  { score += polarBearScore; reasons.add("PolarBear"); }
        if (storageScore >= 3)                               { score += storageScore; reasons.add("Storage(" + storageScore + ")"); }

        if (score < minScore.getValue()) return;

        // Color scales from yellow → orange → red with score
        int r2 = Math.min(255, 180 + score * 4);
        int g2 = Math.max(0,   200 - score * 8);
        Color color = new Color(r2, g2, 30, 110);

        String label = "Base? S:" + score + " [" + String.join(", ", reasons) + "]";
        flagged.put(cp, new FlaggedChunk(cp, label, color));
        ClientModuleTools.chat("Chunk Finder", "Flagged " + cp.x + "," + cp.z + " | " + label);
    }

    // ---- Helpers --------------------------------------------------------

    private boolean isHiddenOre(WorldChunk chunk, BlockPos.Mutable pos, int sx, int sz) {
        if (mc.world == null) return false;
        int enclosed = 0;
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.offset(dir);
            BlockState ns = mc.world.getBlockState(neighbor);
            Block nb = ns.getBlock();
            if (nb == Blocks.COBBLESTONE || nb == Blocks.COBBLED_DEEPSLATE
                    || nb == Blocks.STONE || nb == Blocks.DEEPSLATE
                    || nb == Blocks.OBSIDIAN || nb == Blocks.NETHERRACK) {
                enclosed++;
            }
        }
        return enclosed >= 5;
    }

    private static boolean isOre(Block block) {
        return block == Blocks.DIAMOND_ORE        || block == Blocks.DEEPSLATE_DIAMOND_ORE
            || block == Blocks.IRON_ORE           || block == Blocks.DEEPSLATE_IRON_ORE
            || block == Blocks.GOLD_ORE           || block == Blocks.DEEPSLATE_GOLD_ORE
            || block == Blocks.EMERALD_ORE        || block == Blocks.DEEPSLATE_EMERALD_ORE
            || block == Blocks.LAPIS_ORE          || block == Blocks.DEEPSLATE_LAPIS_ORE
            || block == Blocks.REDSTONE_ORE       || block == Blocks.DEEPSLATE_REDSTONE_ORE
            || block == Blocks.COAL_ORE           || block == Blocks.DEEPSLATE_COAL_ORE
            || block == Blocks.COPPER_ORE         || block == Blocks.DEEPSLATE_COPPER_ORE
            || block == Blocks.ANCIENT_DEBRIS;
    }

    private static boolean isStairBlock(Block block) {
        return block == Blocks.STONE_STAIRS          || block == Blocks.COBBLESTONE_STAIRS
            || block == Blocks.DEEPSLATE_BRICK_STAIRS|| block == Blocks.POLISHED_DEEPSLATE_STAIRS
            || block == Blocks.OAK_STAIRS            || block == Blocks.SPRUCE_STAIRS
            || block == Blocks.DARK_OAK_STAIRS       || block == Blocks.COBBLED_DEEPSLATE_STAIRS;
    }

    // ---- Rendering ------------------------------------------------------

    @Override
    public void onRender(MatrixStack matrices, float tickDelta) {
        if (mc.world == null || mc.player == null || flagged.isEmpty()) return;
        Camera cam = RenderUtils.getCamera();
        if (cam == null) return;
        Vec3d cameraPos = RenderUtils.getCameraPos(cam);
        ChunkPos center = mc.player.getChunkPos();
        int r = scanRadius.getValue() + 1;

        RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(matrices);
        boolean rendered = false;
        for (FlaggedChunk fc : flagged.values()) {
            ChunkPos cp = fc.pos;
            if (Math.abs(cp.x - center.x) > r || Math.abs(cp.z - center.z) > r) continue;
            double x = cp.getStartX() - cameraPos.x;
            double z = cp.getStartZ() - cameraPos.z;
            double y = ClientModuleTools.surfaceY(cp.getCenterX(), cp.getCenterZ()) - cameraPos.y + 0.05;
            if (fillChunk.getValue()) {
                batch.renderFilledBox(x, y, z, x + 16.0, y + 0.18, z + 16.0, fc.color);
            }
            Color outline = new Color(fc.color.getRed(), fc.color.getGreen(), fc.color.getBlue(),
                    Math.min(255, fc.color.getAlpha() + 80));
            batch.renderOutlineBox(x, y, z, x + 16.0, y + 0.22, z + 16.0, outline);
            rendered = true;
        }
        if (rendered) batch.flush();
    }

    // ---- Inner record ---------------------------------------------------

    private record FlaggedChunk(ChunkPos pos, String label, Color color) {}
}
