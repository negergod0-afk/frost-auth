package com.zenya.module.modules.render;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import com.zenya.utils.RenderUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.DropperBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.SmokerBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.chunk.ChunkManager;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public final class StorageESP extends Module {
    private static final String CHEST = "chest";
    private static final String TRAPPED_CHEST = "trapped_chest";
    private static final String ENDER_CHEST = "ender_chest";
    private static final String SPAWNER = "spawner";
    private static final String SHULKER_BOX = "shulker_box";
    private static final String FURNACE = "furnace";
    private static final String BARREL = "barrel";
    private static final String DISPENSER = "dispenser";
    private static final String HOPPER = "hopper";
    private static final String PISTON = "piston";
    private static final String STICKY_PISTON = "sticky_piston";

    private static final double TRACER_START_DISTANCE = 150.0D;
    private static final double TRACER_END_DISTANCE = 24.0D;
    private static final double TRACER_BEHIND_MIN_SPREAD = 2.75D;

    private static final Color CHEST_COLOR = rgb(156, 91, 0);
    private static final Color TRAPPED_COLOR = rgb(200, 91, 0);
    private static final Color ENDER_COLOR = rgb(117, 0, 255);
    private static final Color SPAWNER_COLOR = rgb(138, 126, 166);
    private static final Color SHULKER_COLOR = rgb(134, 0, 158);
    private static final Color FURNACE_COLOR = rgb(125, 125, 125);
    private static final Color BARREL_COLOR = rgb(255, 140, 140);
    private static final Color DISPENSER_COLOR = rgb(100, 100, 100);
    private static final Color HOPPER_COLOR = rgb(144, 238, 144);
    private static final Color PISTON_COLOR = rgb(50, 205, 50);
    private static final Color PISTON_STATIONARY_COLOR = rgb(173, 255, 47);

    private final Setting<Integer> espRendering = new Setting<>("ESP Rendering", 64, 16, 512);

    private final Setting<Integer> alpha = new Setting<>("Alpha", 125, 0, 255);
    private final Setting<Boolean> tracers = new Setting<>("Tracers", false);
    private final Setting<Boolean> fill = new Setting<>("Fill", true);
    private final Setting<Boolean> chests = new Setting<>("Chests", true);
    private final Setting<Boolean> enderChests = new Setting<>("Ender Chests", true);
    private final Setting<Boolean> spawners = new Setting<>("Spawners", true);
    private final Setting<Boolean> shulkerBoxes = new Setting<>("Shulker Boxes", true);
    private final Setting<Boolean> furnaces = new Setting<>("Furnaces", true);
    private final Setting<Boolean> barrels = new Setting<>("Barrels", true);
    private final Setting<Boolean> dispensers = new Setting<>("Dispensers", false);
    private final Setting<Boolean> hoppers = new Setting<>("Hoppers", false);
    private final Setting<Boolean> pistons = new Setting<>("Pistons", true);

    private final ArrayList<RenderBox> renderBoxes = new ArrayList<>(256);
    private final ArrayList<Tracer> renderTracers = new ArrayList<>(256);

    public StorageESP() {
        super("Storage ESP", Category.RENDER);
        setDescription("Renders storage blocks through walls using their real shapes.");
        addSetting(espRendering);
        addSetting(alpha);
        addSetting(tracers);
        addSetting(fill);
        addSetting(chests);
        addSetting(enderChests);
        addSetting(spawners);
        addSetting(shulkerBoxes);
        addSetting(furnaces);
        addSetting(barrels);
        addSetting(dispensers);
        addSetting(hoppers);
        addSetting(pistons);
    }

    @Override
    public void onWorldChange() {
        renderBoxes.clear();
        renderTracers.clear();
    }

    @Override
    public void onRender(MatrixStack matrices, float tickDelta) {
        if (mc.world == null || mc.player == null) return;
        if (!fill.getValue() && !tracers.getValue()) return;

        Camera camera = RenderUtils.getCamera();
        if (camera == null) return;

        Vec3d cameraPos = RenderUtils.getCameraPos(camera);
        renderBoxes.clear();
        renderTracers.clear();

        int viewDistance = espRendering.getValue() / 16;
        double maxDistance = viewDistance * 16.0D;
        double maxDistanceSq = maxDistance * maxDistance;
        ChunkPos center = mc.player.getChunkPos();
        HashSet<Long> seen = new HashSet<>(256);
        ChunkManager chunkManager = mc.world.getChunkManager();

        for (int cx = center.x - viewDistance; cx <= center.x + viewDistance; cx++) {
            for (int cz = center.z - viewDistance; cz <= center.z + viewDistance; cz++) {
                WorldChunk chunk = chunkManager.getWorldChunk(cx, cz, false);
                if (chunk == null) continue;
                collectChunk(chunk, cameraPos, maxDistanceSq, seen);
            }
        }

        if (renderBoxes.isEmpty() && renderTracers.isEmpty()) return;

        if (!renderBoxes.isEmpty()) {
            RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(matrices);
            for (RenderBox box : renderBoxes) {
                if (fill.getValue() && box.fillColor.getAlpha() > 0) {
                    double cx = (box.minX + box.maxX) / 2.0;
                    double cy = (box.minY + box.maxY) / 2.0;
                    double cz = (box.minZ + box.maxZ) / 2.0;
                    double dx = Math.max(0.001, (box.maxX - box.minX) / 2.0 - 0.03);
                    double dy = Math.max(0.001, (box.maxY - box.minY) / 2.0 - 0.03);
                    double dz = Math.max(0.001, (box.maxZ - box.minZ) / 2.0 - 0.03);
                    batch.renderFilledBox(cx - dx, cy - dy, cz - dz, cx + dx, cy + dy, cz + dz, box.fillColor);
                }
            }
            batch.flush();
        }

        if (tracers.getValue() && fill.getValue() && !renderTracers.isEmpty()) {
            // Determine if we should use player position for tracers (F5/freecam mode)
            boolean usePlayerPosition = isNonFirstPersonView();
            Vec3d tracerOrigin = usePlayerPosition ? getPlayerHeadPosition(tickDelta) : Vec3d.ZERO;
            
            Vec3d cameraForward = RenderUtils.getCameraForward(camera);
            Vec3d cameraRight = RenderUtils.getCameraRight(camera);
            Vec3d cameraUp = RenderUtils.getCameraUp(cameraForward, cameraRight);
            Vec3d tracerStart = usePlayerPosition ? tracerOrigin : cameraForward.multiply(TRACER_START_DISTANCE);
            
            RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(matrices);
            for (Tracer tracer : renderTracers) {
                Vec3d tracerEnd = RenderUtils.getSpreadTracerEnd(
                    tracer.target.x,
                    tracer.target.y,
                    tracer.target.z,
                    cameraForward,
                    cameraRight,
                    cameraUp,
                    TRACER_END_DISTANCE,
                    TRACER_BEHIND_MIN_SPREAD
                );
                // Convert tracerStart to camera-relative if using player position
                Vec3d finalTracerStart = usePlayerPosition 
                    ? new Vec3d(tracerStart.x - cameraPos.x, tracerStart.y - cameraPos.y, tracerStart.z - cameraPos.z)
                    : tracerStart;
                batch.renderLine(tracer.color, finalTracerStart, tracerEnd, com.zenya.module.modules.client.ZenyaPlus.tracerLineWidth());
            }
            batch.flush();
        }
    }

    private void collectChunk(WorldChunk chunk, Vec3d cameraPos, double maxDistanceSq, HashSet<Long> seen) {
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            BlockPos pos = blockEntity.getPos();
            long key = pos.asLong();
            if (!seen.add(key)) continue;

            double centerX = pos.getX() + 0.5D - cameraPos.x;
            double centerY = pos.getY() + 0.5D - cameraPos.y;
            double centerZ = pos.getZ() + 0.5D - cameraPos.z;
            if (centerX * centerX + centerY * centerY + centerZ * centerZ > maxDistanceSq) continue;

            String type = classify(blockEntity);
            if (type == null || !shouldHighlight(type)) continue;

            Color base = baseColor(type);
            
            // Check if piston is stationary and use light lime green
            if (type.equals(PISTON) || type.equals(STICKY_PISTON)) {
                BlockState state = mc.world.getBlockState(pos);
                if (state != null && !state.get(net.minecraft.state.property.Properties.EXTENDED)) {
                    base = PISTON_STATIONARY_COLOR;
                }
            }
            
            Color fillColor = withAlpha(base, alpha.getValue());
            Color outlineColor = withAlpha(base, 255);
            BlockState state = blockEntity.getCachedState();
            VoxelShape shape = state.getOutlineShape(mc.world, pos);
            List<Box> boxes = shape == null ? List.of() : shape.getBoundingBoxes();

            if (boxes.isEmpty() || type.equals(HOPPER)) {
                addBox(new Box(pos).expand(-0.02D), cameraPos, fillColor, outlineColor);
            } else {
                for (Box part : boxes) {
                    Box b = part.offset(pos).expand(-0.02D);
                    if (b.maxX > b.minX && b.maxY > b.minY && b.maxZ > b.minZ) {
                        addBox(b, cameraPos, fillColor, outlineColor);
                    }
                }
            }

            renderTracers.add(new Tracer(new Vec3d(centerX, centerY, centerZ), outlineColor));
        }
    }

    private void addBox(Box box, Vec3d cameraPos, Color fillColor, Color outlineColor) {
        renderBoxes.add(new RenderBox(
                box.minX - cameraPos.x,
                box.minY - cameraPos.y,
                box.minZ - cameraPos.z,
                box.maxX - cameraPos.x,
                box.maxY - cameraPos.y,
                box.maxZ - cameraPos.z,
                fillColor,
                outlineColor
        ));
    }

    private String classify(BlockEntity blockEntity) {
        if (blockEntity instanceof TrappedChestBlockEntity) return TRAPPED_CHEST;
        if (blockEntity instanceof ChestBlockEntity) return CHEST;
        if (blockEntity instanceof EnderChestBlockEntity) return ENDER_CHEST;
        if (blockEntity instanceof MobSpawnerBlockEntity) return SPAWNER;
        if (blockEntity instanceof ShulkerBoxBlockEntity) return SHULKER_BOX;
        if (blockEntity instanceof FurnaceBlockEntity
                || blockEntity instanceof BlastFurnaceBlockEntity
                || blockEntity instanceof SmokerBlockEntity) return FURNACE;
        if (blockEntity instanceof BarrelBlockEntity) return BARREL;
        if (blockEntity instanceof DispenserBlockEntity || blockEntity instanceof DropperBlockEntity) return DISPENSER;
        if (blockEntity instanceof HopperBlockEntity) return HOPPER;
        if (blockEntity instanceof PistonBlockEntity) {
            BlockPos pos = blockEntity.getPos();
            BlockState state = mc.world.getBlockState(pos);
            if (state.getBlock() == Blocks.PISTON) return PISTON;
            if (state.getBlock() == Blocks.STICKY_PISTON) return STICKY_PISTON;
        }
        return null;
    }

    private boolean shouldHighlight(String type) {
        return switch (type) {
            case CHEST, TRAPPED_CHEST -> chests.getValue();
            case ENDER_CHEST -> enderChests.getValue();
            case SPAWNER -> spawners.getValue();
            case SHULKER_BOX -> shulkerBoxes.getValue();
            case FURNACE -> furnaces.getValue();
            case BARREL -> barrels.getValue();
            case DISPENSER -> dispensers.getValue();
            case HOPPER -> hoppers.getValue();
            case PISTON, STICKY_PISTON -> pistons.getValue();
            default -> false;
        };
    }

    private Color baseColor(String type) {
        return switch (type) {
            case TRAPPED_CHEST -> TRAPPED_COLOR;
            case ENDER_CHEST -> ENDER_COLOR;
            case SPAWNER -> SPAWNER_COLOR;
            case SHULKER_BOX -> SHULKER_COLOR;
            case FURNACE -> FURNACE_COLOR;
            case BARREL -> BARREL_COLOR;
            case DISPENSER -> DISPENSER_COLOR;
            case HOPPER -> HOPPER_COLOR;
            case PISTON, STICKY_PISTON -> PISTON_COLOR;
            default -> CHEST_COLOR;
        };
    }

    private static Color rgb(int red, int green, int blue) {
        return new Color(red, green, blue, 255);
    }

    private static Color withAlpha(Color base, int alpha) {
        int clamped = Math.max(0, Math.min(255, alpha));
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), clamped);
    }

    private boolean isNonFirstPersonView() {
        // Check if freecam is enabled
        if (Freecam.instance != null && Freecam.instance.isEnabled()) {
            return true;
        }
        // Check if perspective is not first person (F5 modes)
        if (mc.options != null) {
            try {
                return !mc.options.getPerspective().isFirstPerson();
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private Vec3d getPlayerHeadPosition(float tickDelta) {
        if (mc.player == null) {
            return Vec3d.ZERO;
        }
        try {
            Vec3d playerPos = mc.player.getLerpedPos(tickDelta);
            double eyeHeight = mc.player.getStandingEyeHeight();
            return new Vec3d(playerPos.x, playerPos.y + eyeHeight, playerPos.z);
        } catch (Throwable ignored) {
            double worldX = net.minecraft.util.math.MathHelper.lerp(tickDelta, mc.player.lastRenderX, mc.player.getX());
            double worldY = net.minecraft.util.math.MathHelper.lerp(tickDelta, mc.player.lastRenderY, mc.player.getY());
            double worldZ = net.minecraft.util.math.MathHelper.lerp(tickDelta, mc.player.lastRenderZ, mc.player.getZ());
            double eyeHeight = mc.player.getStandingEyeHeight();
            return new Vec3d(worldX, worldY + eyeHeight, worldZ);
        }
    }

    private record RenderBox(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            Color fillColor,
            Color outlineColor
    ) {}

    private record Tracer(Vec3d target, Color color) {}
}
