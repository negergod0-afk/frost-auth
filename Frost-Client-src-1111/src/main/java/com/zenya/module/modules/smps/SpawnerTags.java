package com.zenya.module.modules.smps;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import com.zenya.utils.renderer.ProjectionUtil;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SpawnerTags extends Module {
    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final double Y_OFFSET = 0.85D;
    private static SpawnerTags INSTANCE;

    private final Setting<Color> textColor = new Setting<>("Text Color", Color.WHITE);
    private final Setting<Color> backgroundColor = new Setting<>("Background", new Color(0, 0, 0, 120));
    private final Setting<Double> scale = new Setting<>("Scale", 1.15D, 0.35D, 4.0D);
    private final Setting<Integer> scanRadius = new Setting<>("Scan Radius", 32, 1, 32);

    private final Map<Long, SpawnerTag> spawners = new ConcurrentHashMap<>();
    private final List<SpawnerTag> renderList = new ArrayList<>();
    private int tickCounter;
    private int spawnerCount;

    public SpawnerTags() {
        super("SpawnerTags", Category.SMPS);
        INSTANCE = this;
        setDescription("Shows mob type nametags above nearby loaded spawners.");
        addSetting(textColor);
        addSetting(backgroundColor);
        addSetting(scale);
        addSetting(scanRadius);
    }

    @Override
    public void onEnable() {
        spawners.clear();
        renderList.clear();
        tickCounter = 0;
        spawnerCount = 0;
        scanAroundPlayer();
    }

    @Override
    public void onDisable() {
        spawners.clear();
        renderList.clear();
        spawnerCount = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) {
            spawners.clear();
            renderList.clear();
            spawnerCount = 0;
            return;
        }

        if (++tickCounter < SCAN_INTERVAL_TICKS) {
            return;
        }

        tickCounter = 0;
        scanAroundPlayer();
    }

    @Override
    public void onPacketReceive(Packet<?> packet) {
        if (mc.world == null) {
            return;
        }

        if (packet instanceof ChunkDataS2CPacket chunkData) {
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(chunkData.getChunkX(), chunkData.getChunkZ(), false);
            if (chunk != null) {
                scanChunk(chunk);
            }
            return;
        }

        if (packet instanceof ChunkDeltaUpdateS2CPacket deltaUpdate) {
            deltaUpdate.visitUpdates((pos, state) -> {
                if (state.isOf(Blocks.SPAWNER)) {
                    WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(new ChunkPos(pos).x, new ChunkPos(pos).z, false);
                    if (chunk != null) {
                        scanChunk(chunk);
                    }
                } else {
                    spawners.remove(pos.asLong());
                    spawnerCount = spawners.size();
                }
            });
            return;
        }

        if (packet instanceof BlockUpdateS2CPacket blockUpdate) {
            BlockPos pos = blockUpdate.getPos();
            if (blockUpdate.getState().isOf(Blocks.SPAWNER)) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(new ChunkPos(pos).x, new ChunkPos(pos).z, false);
                if (chunk != null) {
                    scanChunk(chunk);
                }
            } else {
                spawners.remove(pos.asLong());
                spawnerCount = spawners.size();
            }
        }
    }

    public static void renderHud(DrawContext context, float tickDelta) {
        SpawnerTags module = INSTANCE;
        if (module == null || !module.isEnabled()) {
            return;
        }
        module.renderHudInternal(context);
    }

    private void renderHudInternal(DrawContext context) {
        if (mc.player == null || mc.world == null || spawners.isEmpty()) {
            return;
        }

        renderList.clear();
        double maxDistanceSq = maxRenderDistanceSq();
        double playerX = mc.player.getX();
        double playerY = mc.player.getY();
        double playerZ = mc.player.getZ();

        for (SpawnerTag tag : spawners.values()) {
            if (!mc.world.getBlockState(tag.pos).isOf(Blocks.SPAWNER)) {
                spawners.remove(tag.key);
                continue;
            }

            double distanceSq = squaredDistance(tag.pos, playerX, playerY, playerZ);
            if (distanceSq > maxDistanceSq) {
                continue;
            }

            tag.distanceSq = distanceSq;
            renderList.add(tag);
        }

        if (renderList.isEmpty()) {
            spawnerCount = spawners.size();
            return;
        }

        renderList.sort(Comparator.comparingDouble(tag -> -tag.distanceSq));
        TextRenderer renderer = mc.textRenderer;
        float tagScale = scale.getValue().floatValue();
        int text = rgb(textColor.getValue());
        int background = argb(backgroundColor.getValue());

        for (SpawnerTag tag : renderList) {
            Vec3d pos = new Vec3d(tag.pos.getX() + 0.5D, tag.pos.getY() + Y_OFFSET, tag.pos.getZ() + 0.5D);
            Pair<Vec3d, Boolean> projection = ProjectionUtil.project(ProjectionUtil.modelViewMatrix, ProjectionUtil.projectionMatrix, pos);
            if (projection == null || !projection.getRight()) {
                continue;
            }

            Vec3d screen = projection.getLeft();
            if (screen.z < -1.0D || screen.z > 1.0D) {
                continue;
            }

            renderTag(context, renderer, tag.label, (float) screen.x, (float) screen.y, tagScale, text, background);
        }

        spawnerCount = spawners.size();
    }

    private void scanAroundPlayer() {
        if (mc.player == null || mc.world == null) {
            return;
        }

        ChunkPos center = mc.player.getChunkPos();
        int radius = scanRadius.getValue();
        Map<Long, SpawnerTag> found = new ConcurrentHashMap<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = center.x + dx;
                int chunkZ = center.z + dz;
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(chunkX, chunkZ, false);
                if (chunk != null) {
                    collectChunkSpawners(chunk, found);
                }
            }
        }

        spawners.clear();
        spawners.putAll(found);
        spawnerCount = spawners.size();
    }

    private void scanChunk(WorldChunk chunk) {
        if (chunk == null) {
            return;
        }

        long chunkKey = chunk.getPos().toLong();
        spawners.entrySet().removeIf(entry -> new ChunkPos(entry.getValue().pos).toLong() == chunkKey);
        collectChunkSpawners(chunk, spawners);
        spawnerCount = spawners.size();
    }

    private void collectChunkSpawners(WorldChunk chunk, Map<Long, SpawnerTag> output) {
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (!(blockEntity instanceof MobSpawnerBlockEntity spawner)) {
                continue;
            }

            BlockPos pos = blockEntity.getPos();
            if (mc.world == null || !mc.world.getBlockState(pos).isOf(Blocks.SPAWNER)) {
                continue;
            }

            String label = resolveSpawnerLabel(spawner, pos);
            if (label == null || label.isBlank()) {
                continue;
            }

            long key = pos.asLong();
            output.put(key, new SpawnerTag(key, pos, label));
        }
    }

    private String resolveSpawnerLabel(MobSpawnerBlockEntity spawner, BlockPos pos) {
        EntityType<?> type = readSpawnerEntityType(spawner, pos);
        if (type == null) {
            return null;
        }
        return formatMobName(type);
    }

    private EntityType<?> readSpawnerEntityType(MobSpawnerBlockEntity spawner, BlockPos pos) {
        if (mc.world == null) {
            return null;
        }

        try {
            Entity entity = spawner.getLogic().getRenderedEntity(mc.world, pos);
            return entity == null ? null : entity.getType();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String formatMobName(EntityType<?> type) {
        if (type == EntityType.ZOMBIFIED_PIGLIN) return "Zombie Piglin";
        if (type == EntityType.PIGLIN_BRUTE) return "Piglin Brute";
        if (type == EntityType.IRON_GOLEM) return "Iron Golem";
        if (type == EntityType.SKELETON_HORSE) return "Skeleton Horse";
        if (type == EntityType.ZOMBIE_HORSE) return "Zombie Horse";
        if (type == EntityType.CAVE_SPIDER) return "Cave Spider";
        if (type == EntityType.MAGMA_CUBE) return "Magma Cube";
        if (type == EntityType.WITHER_SKELETON) return "Wither Skeleton";
        if (type == EntityType.BLAZE) return "Blaze";
        if (type == EntityType.SILVERFISH) return "Silverfish";

        String name = type.getName().getString();
        if (name == null || name.isBlank()) {
            return type.toString();
        }

        return capitalizeWords(name);
    }

    private void renderTag(DrawContext context, TextRenderer renderer, String label, float x, float y, float tagScale, int textColor, int backgroundColor) {
        int textWidth = renderer.getWidth(label);
        int pad = 4;
        int width = textWidth + pad * 2;
        int height = 13;
        int left = Math.round(-width / 2.0F);
        int top = -height;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(tagScale, tagScale);
        context.fill(left, top, left + width, top + height, backgroundColor);
        context.drawText(renderer, label, left + pad, top + 2, textColor, true);
        context.getMatrices().popMatrix();
    }

    private double maxRenderDistanceSq() {
        double blocks = (mc.options.getClampedViewDistance() + 1) * 16.0D;
        return blocks * blocks;
    }

    private double squaredDistance(BlockPos pos, double playerX, double playerY, double playerZ) {
        double dx = pos.getX() + 0.5D - playerX;
        double dy = pos.getY() + 0.5D - playerY;
        double dz = pos.getZ() + 0.5D - playerZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private int rgb(Color color) {
        return 0xFF000000 | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
    }

    private int argb(Color color) {
        return (color.getAlpha() << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
    }

    private String capitalizeWords(String value) {
        String raw = value.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(raw.length());
        boolean cap = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            out.append(cap ? Character.toUpperCase(c) : c);
            cap = c == ' ' || c == '_' || c == '-';
        }
        return out.toString().replace('_', ' ').replace('-', ' ');
    }

    private static final class SpawnerTag {
        private final long key;
        private final BlockPos pos;
        private final String label;
        private double distanceSq;

        private SpawnerTag(long key, BlockPos pos, String label) {
            this.key = key;
            this.pos = pos.toImmutable();
            this.label = label;
        }
    }
}
