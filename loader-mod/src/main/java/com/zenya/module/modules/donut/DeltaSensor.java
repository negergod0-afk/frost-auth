package com.zenya.module.modules.donut;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import com.zenya.utils.RenderUtils;
import com.zenya.utils.renderer.ProjectionUtil;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Pair;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class DeltaSensor extends Module {
    private static final double TAG_Y = 65.1D;
    private static DeltaSensor INSTANCE;

    private final Setting<Integer> sensitivity  = new Setting<>("Sensitivity",   3, 1, 20);
    private final Setting<Integer> simDistance  = new Setting<>("Sim Distance",  16, 1, 32);
    private final Setting<Color>   fillColor    = new Setting<>("Fill Color",    new Color(60, 180, 80, 40));
    private final Setting<Integer> fillAlpha    = new Setting<>("Fill Alpha",    40, 0, 255);
    private final Set<ChunkPos> highlightedChunks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> chestChunks       = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> bigChestChunks    = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> reportedChunks    = ConcurrentHashMap.newKeySet();
    private final Map<ChunkPos, String> probabilityTags = new ConcurrentHashMap<>();
    private int tickCounter;

    public DeltaSensor() {
        super("Delta Sensor", Category.DONUT);
        INSTANCE = this;
        addSetting(sensitivity);
        addSetting(simDistance);
        addSetting(fillColor);
        addSetting(fillAlpha);
    }

    @Override
    public void onEnable() {
        highlightedChunks.clear();
        chestChunks.clear();
        bigChestChunks.clear();
        reportedChunks.clear();
        probabilityTags.clear();
        tickCounter = 0;
    }

    @Override
    public void onDisable() {
        highlightedChunks.clear();
        chestChunks.clear();
        bigChestChunks.clear();
        reportedChunks.clear();
        probabilityTags.clear();
    }

    @Override
    public void onWorldChange() {
        highlightedChunks.clear();
        chestChunks.clear();
        bigChestChunks.clear();
        reportedChunks.clear();
        probabilityTags.clear();
        tickCounter = 0;
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;

        ChunkPos playerChunk = mc.player.getChunkPos();
        // Scale the simDistance value down a bit so we don't have massive chunk updates
        int radius = Math.min(12, (simDistance.getValue() / 2) + 1);

        highlightedChunks.removeIf(c ->
                Math.abs(c.x - playerChunk.x) > radius + 2 || Math.abs(c.z - playerChunk.z) > radius + 2);
        chestChunks.removeIf(c ->
                Math.abs(c.x - playerChunk.x) > radius + 2 || Math.abs(c.z - playerChunk.z) > radius + 2);
        bigChestChunks.removeIf(c ->
                Math.abs(c.x - playerChunk.x) > radius + 2 || Math.abs(c.z - playerChunk.z) > radius + 2);
        probabilityTags.keySet().removeIf(c ->
                Math.abs(c.x - playerChunk.x) > radius + 2 || Math.abs(c.z - playerChunk.z) > radius + 2);

        if (++tickCounter % 20 != 0) return;

        ArrayList<ChunkPos>  positions = new ArrayList<>();
        ArrayList<WorldChunk> chunks    = new ArrayList<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkPos cp    = new ChunkPos(playerChunk.x + dx, playerChunk.z + dz);
                WorldChunk wc  = mc.world.getChunkManager().getWorldChunk(cp.x, cp.z, false);
                if (wc == null || wc.isEmpty()) continue;
                positions.add(cp);
                chunks.add(wc);
            }
        }

        int threshold = 15; // default threshold for amethyst scan
        HashSet<ChunkPos> found = new HashSet<>();
        HashSet<ChunkPos> chestFound = new HashSet<>();
        HashSet<ChunkPos> bigChestFound = new HashSet<>();
        try {
            for (int i = 0; i < positions.size(); i++) {
                ChunkPos cp = positions.get(i);
                WorldChunk chunk = chunks.get(i);
                if (countAmethystBlocks(chunk) >= threshold) found.add(cp);
                int chests = countChests(chunk);
                if (chests >= 10) chestFound.add(cp);
                if (chests >= 20) bigChestFound.add(cp);
            }

            int newDetections = 0;
            for (ChunkPos cp : found) {
                if (reportedChunks.add(cp)) newDetections++;
            }
            if (newDetections > 0 && mc.player != null) {
                final int totalFound = found.size();
                final String chance = totalFound >= 3 ? "High (90%)" : (totalFound == 2 ? "Medium (60%)" : "Low (30%)");
                mc.player.sendMessage(net.minecraft.text.Text.literal("\u00a7b[Delta Sensor] \u00a7fDetected! Base Probability: \u00a7e" + chance), false);
            }
            final int totalFound = found.size();
            final String chanceLabel = totalFound >= 3 ? "90%" : (totalFound == 2 ? "60%" : "30%");

            highlightedChunks.clear();
            highlightedChunks.addAll(found);
            probabilityTags.clear();
            for (ChunkPos cp : found) {
                probabilityTags.put(cp, chanceLabel);
            }
            chestChunks.clear();
            chestChunks.addAll(chestFound);
            bigChestChunks.clear();
            bigChestChunks.addAll(bigChestFound);
        } catch (Exception ignored) {
        }
    }

    private static int countAmethystBlocks(WorldChunk chunk) {
        int count = 0;
        ChunkSection[] sections = chunk.getSectionArray();
        int bottomY = chunk.getBottomY();
        for (int si = 0; si < sections.length && bottomY + si * 16 <= 32; si++) {
            ChunkSection sec = sections[si];
            if (sec == null || sec.isEmpty() || !sec.hasAny(DeltaSensor::isAmethystBlock)) continue;
            for (int x = 0; x < 16; x++)
                for (int y = 0; y < 16; y++)
                    for (int z = 0; z < 16; z++)
                        if (isAmethystBlock(sec.getBlockState(x, y, z))) count++;
        }
        return count;
    }

    private static int countChests(WorldChunk chunk) {
        int count = 0;
        ChunkSection[] sections = chunk.getSectionArray();
        int bottomY = chunk.getBottomY();
        for (int si = 0; si < sections.length; si++) {
            int baseY = bottomY + si * 16;
            if (baseY >= 0) break;
            ChunkSection sec = sections[si];
            if (sec == null || sec.isEmpty()) continue;
            if (!sec.hasAny(s -> s.isOf(Blocks.CHEST) || s.isOf(Blocks.TRAPPED_CHEST))) continue;
            int maxY = Math.min(15, -baseY - 1);
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++)
                    for (int y = 0; y <= maxY; y++) {
                        BlockState s = sec.getBlockState(x, y, z);
                        if (s.isOf(Blocks.CHEST) || s.isOf(Blocks.TRAPPED_CHEST)) count++;
                    }
        }
        return count;
    }

    private static boolean isAmethystBlock(BlockState s) {
        return s.isOf(Blocks.AMETHYST_CLUSTER)
            || s.isOf(Blocks.LARGE_AMETHYST_BUD)
            || s.isOf(Blocks.MEDIUM_AMETHYST_BUD)
            || s.isOf(Blocks.SMALL_AMETHYST_BUD)
            || s.isOf(Blocks.BUDDING_AMETHYST)
            || s.isOf(Blocks.AMETHYST_BLOCK);
    }

    private static Set<ChunkPos> buildShapeChunks(ChunkPos origin, int width, int depth) {
        HashSet<ChunkPos> result = new HashSet<>();
        int minDx = -(width / 2);
        int maxDx = minDx + width - 1;
        int minDz = -(depth / 2);
        int maxDz = minDz + depth - 1;
        for (int dx = minDx; dx <= maxDx; dx++) {
            for (int dz = minDz; dz <= maxDz; dz++) {
                result.add(new ChunkPos(origin.x + dx, origin.z + dz));
            }
        }
        return result;
    }

    private static int randomShapeDimension(ChunkPos origin, int sensitivity, int salt) {
        int clamped = Math.max(1, sensitivity);
        if (clamped <= 1) return 1;
        int hash = origin.x * 73428767 ^ origin.z * 912931 ^ clamped * 31 ^ salt;
        return clamped - Math.floorMod(hash, 2);
    }

    @Override
    public void onRender(MatrixStack matrices, float tickDelta) {
        if (mc.world == null || mc.player == null) return;
        if (highlightedChunks.isEmpty() && chestChunks.isEmpty() && bigChestChunks.isEmpty()) return;

        Camera camera = RenderUtils.getCamera();
        if (camera == null) return;
        Vec3d camPos = RenderUtils.getCameraPos(camera);

        double minY = 64.0 - camPos.y;
        double maxY = minY + 0.15;

        Color base    = fillColor.getValue();
        int   a       = Math.max(0, Math.min(255, fillAlpha.getValue()));
        Color fill    = new Color(base.getRed(), base.getGreen(), base.getBlue(), a);
        int shapeSensitivity = Math.max(1, sensitivity.getValue());

        HashSet<ChunkPos> renderChunks = new HashSet<>();
        List<ChunkPos> origins = new ArrayList<>(highlightedChunks);

        for (ChunkPos origin : origins) {
            int width = randomShapeDimension(origin, shapeSensitivity, 17);
            int depth = randomShapeDimension(origin, shapeSensitivity, 53);
            renderChunks.addAll(buildShapeChunks(origin, width, depth));
        }

        // If exactly 2 detections, also highlight the midpoint chunk
        if (origins.size() == 2) {
            ChunkPos a1 = origins.get(0);
            ChunkPos a2 = origins.get(1);
            int midX = (a1.x + a2.x) / 2;
            int midZ = (a1.z + a2.z) / 2;
            ChunkPos midpoint = new ChunkPos(midX, midZ);
            int width = randomShapeDimension(midpoint, shapeSensitivity, 17);
            int depth = randomShapeDimension(midpoint, shapeSensitivity, 53);
            renderChunks.addAll(buildShapeChunks(midpoint, width, depth));
        }

        matrices.push();
        RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(matrices);
        
        // Expanded shape chunks (normal rendering)
        for (ChunkPos cp : renderChunks) {
            double x1 = (cp.x << 4) - camPos.x;
            double z1 = (cp.z << 4) - camPos.z;
            batch.renderFilledBox(x1, minY, z1, x1 + 16.0, maxY, z1 + 16.0, fill);
        }
        
        // 20+ storage blinker.
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        if (blink && !bigChestChunks.isEmpty()) {
            ChunkPos playerPos = mc.player.getChunkPos();
            Color redBlink = new Color(255, 0, 0, 100);
            for (ChunkPos cp : bigChestChunks) {
                if (Math.abs(cp.x - playerPos.x) <= 2 && Math.abs(cp.z - playerPos.z) <= 2) {
                    continue; // Disappear if around 2 chunks away
                }
                double x1 = (cp.x << 4) - camPos.x;
                double z1 = (cp.z << 4) - camPos.z;
                batch.renderFilledBox(x1, minY, z1, x1 + 16.0, maxY, z1 + 16.0, redBlink);
            }
        }

        batch.flush();
        matrices.pop();
    }

    public static void renderHud(DrawContext context, float tickDelta) {
        DeltaSensor module = INSTANCE;
        if (module == null || !module.isEnabled()) {
            return;
        }
        module.renderHudInternal(context);
    }

    private void renderHudInternal(DrawContext context) {
        if (mc.world == null || mc.player == null || probabilityTags.isEmpty()) {
            return;
        }

        TextRenderer renderer = mc.textRenderer;
        int textColor = 0xFFFFFFFF;
        int backgroundColor = 0xA0000000;

        List<Map.Entry<ChunkPos, String>> tags = new ArrayList<>(probabilityTags.entrySet());
        tags.sort(Comparator.comparingDouble(entry -> -distanceSq(entry.getKey())));
        for (Map.Entry<ChunkPos, String> entry : tags) {
            ChunkPos chunk = entry.getKey();
            Vec3d worldPos = new Vec3d((chunk.x << 4) + 8.0D, TAG_Y, (chunk.z << 4) + 8.0D);
            Pair<Vec3d, Boolean> projection = ProjectionUtil.project(ProjectionUtil.modelViewMatrix, ProjectionUtil.projectionMatrix, worldPos);
            if (projection == null || !projection.getRight()) {
                continue;
            }

            Vec3d screen = projection.getLeft();
            if (screen.z < -1.0D || screen.z > 1.0D) {
                continue;
            }

            renderTag(context, renderer, entry.getValue(), (float) screen.x, (float) screen.y, textColor, backgroundColor);
        }
    }

    private double distanceSq(ChunkPos chunk) {
        if (mc.player == null) {
            return 0.0D;
        }
        double x = (chunk.x << 4) + 8.0D - mc.player.getX();
        double z = (chunk.z << 4) + 8.0D - mc.player.getZ();
        return x * x + z * z;
    }

    private void renderTag(DrawContext context, TextRenderer renderer, String label, float x, float y, int textColor, int backgroundColor) {
        int textWidth = renderer.getWidth(label);
        int pad = 4;
        int width = textWidth + pad * 2;
        int height = 13;
        int left = Math.round(-width / 2.0F);
        int top = -height - 3;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.fill(left, top, left + width, top + height, backgroundColor);
        context.drawText(renderer, label, left + pad, top + 2, textColor, true);
        context.getMatrices().popMatrix();
    }
}
