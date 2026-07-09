package com.zenya.module.modules.smps;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import com.zenya.utils.RenderUtils;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SeedChunkFinder extends Module {
    private final Setting<Color> sideColor = new Setting<>("Side Color", new Color(255, 150, 0, 75));
    private final Setting<Color> lineColor = new Setting<>("Line Color", new Color(255, 150, 0, 255));
    private final Setting<Boolean> filledEsp = new Setting<>("Filled ESP", true);
    private final Setting<Color> espColor = new Setting<>("ESP Color", new Color(0, 120, 255, 100));

    private final Map<ChunkPos, DetectionType> modifiedChunks = new LinkedHashMap<>();

    public SeedChunkFinder() {
        super("Seed Chunk Finder", Category.SMPS);
        setDescription("Detects player-modified chunks with rotated deepslate.");
        addSetting(sideColor);
        addSetting(lineColor);
        addSetting(filledEsp);
        addSetting(espColor);
    }

    @Override
    public void onEnable() {
        modifiedChunks.clear();
    }

    @Override
    public void onPacketReceive(Packet<?> packet) {
        if (mc.player == null || mc.world == null) return;

        if (packet instanceof ChunkDataS2CPacket chunkPacket) {
            ChunkPos pos = new ChunkPos(chunkPacket.getChunkX(), chunkPacket.getChunkZ());
            if (modifiedChunks.containsKey(pos)) return;

            mc.execute(() -> {
                WorldChunk chunk = mc.world.getChunk(pos.x, pos.z);
                if (chunk != null) {
                    checkChunkForRotatedDeepslate(chunk, pos);
                }
            });
        } else if (packet instanceof BlockUpdateS2CPacket blockPacket) {
            BlockPos blockPos = blockPacket.getPos();
            ChunkPos chunkPos = new ChunkPos(blockPos);
            if (modifiedChunks.containsKey(chunkPos)) return;

            if (blockPos.getY() <= 16) {
                if (isRotatedDeepslate(blockPacket.getState())) {
                    flagChunk(chunkPos, DetectionType.DEEPSLATE);
                }
            }
        }
    }

    private void checkChunkForRotatedDeepslate(WorldChunk chunk, ChunkPos pos) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y <= 16; y++) {
                    BlockPos blockPos = new ChunkPos(pos.x, pos.z).getBlockPos(x, y, z);
                    if (isRotatedDeepslate(chunk.getBlockState(blockPos))) {
                        flagChunk(pos, DetectionType.DEEPSLATE);
                        return;
                    }
                }
            }
        }
    }

    private void flagChunk(ChunkPos pos, DetectionType type) {
        if (!modifiedChunks.containsKey(pos)) {
            modifiedChunks.put(pos, type);
            notify(pos, type.name());
        }
    }

    private void notify(ChunkPos pos, String type) {
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, 1.0f);
                if (mc.getToastManager() != null) {
                    SystemToast.show(
                            mc.getToastManager(),
                            SystemToast.Type.PERIODIC_NOTIFICATION,
                            Text.literal("Deep Seed Chunk Finder"),
                            Text.literal("Rotated deepslate at X: " + (pos.x * 16 + 8) + " Z: " + (pos.z * 16 + 8)));
                }
            }
        });
    }

    private boolean isRotatedDeepslate(net.minecraft.block.BlockState state) {
        if (state.getBlock() == Blocks.DEEPSLATE && state.contains(Properties.AXIS)) {
            return state.get(Properties.AXIS) != net.minecraft.util.math.Direction.Axis.Y;
        }
        return false;
    }

    @Override
    public void onRender(MatrixStack matrices, float tickDelta) {
        if (modifiedChunks.isEmpty()) return;

        Camera camera = RenderUtils.getCamera();
        if (camera == null) return;

        Vec3d cameraPos = RenderUtils.getCameraPos(camera);
        RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(matrices);
        boolean rendered = false;

        for (Map.Entry<ChunkPos, DetectionType> entry : modifiedChunks.entrySet()) {
            ChunkPos pos = entry.getKey();
            DetectionType type = entry.getValue();
            
            double x = pos.x * 16 + 8 - cameraPos.x;
            double z = pos.z * 16 + 8 - cameraPos.z;
            double y1 = -64 - cameraPos.y;
            double y2 = 320 - cameraPos.y;
            
            Color color = type.color;
            if (filledEsp.getValue()) {
                batch.renderFilledBox(x - 0.1, y1, z - 0.1, x + 0.1, y2, z + 0.1, espColor.getValue());
            }
            batch.renderOutlineBox(x - 0.1, y1, z - 0.1, x + 0.1, y2, z + 0.1, lineColor.getValue());
            rendered = true;
        }

        if (rendered) batch.flush();
    }

    private enum DetectionType {
        DEEPSLATE(new Color(255, 0, 0, 150));

        final Color color;

        DetectionType(Color color) {
            this.color = color;
        }
    }
}
