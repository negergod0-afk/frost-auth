package com.zenya.module.modules.common;

import com.zenya.utils.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.SmokerBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.Heightmap;

import java.awt.Color;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientModuleTools {
    private ClientModuleTools() {}

    public record ChunkMark(ChunkPos pos, String label, Color color) {}
    public record BlockMark(BlockPos pos, String label, Color color) {}

    public static void chat(String module, String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("\u00a7b[" + module + "]\u00a7r " + message), false);
        }
    }

    public static void toast(String title, String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getToastManager() != null) {
            SystemToast.show(mc.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION, Text.literal(title), Text.literal(message));
        }
    }

    public static void command(String command) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() != null && command != null && !command.isBlank()) {
            mc.getNetworkHandler().sendChatCommand(command.startsWith("/") ? command.substring(1) : command);
        }
    }

    public static void clickSlot(ScreenHandler handler, Slot slot, SlotActionType action) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.interactionManager == null || mc.player == null || handler == null || slot == null) return;
        mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, action, mc.player);
    }

    public static int viewDistanceChunks() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.options == null ? 8 : mc.options.getClampedViewDistance();
    }

    public static double parseCompactNumber(String input, double fallback) {
        if (input == null || input.isBlank()) return fallback;
        String value = input.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[,$\\s]", "");
        double multiplier = 1.0;
        if (value.endsWith("k")) {
            multiplier = 1_000.0;
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("m")) {
            multiplier = 1_000_000.0;
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("b")) {
            multiplier = 1_000_000_000.0;
            value = value.substring(0, value.length() - 1);
        }
        try {
            return Double.parseDouble(value) * multiplier;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public static boolean itemMatches(ItemStack stack, String target) {
        if (stack == null || stack.isEmpty() || target == null || target.isBlank()) return false;
        String needle = target.toLowerCase(java.util.Locale.ROOT);
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return stack.getName().getString().toLowerCase(java.util.Locale.ROOT).contains(needle)
                || (id != null && id.toString().toLowerCase(java.util.Locale.ROOT).contains(needle));
    }

    public static boolean isConfirmItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String name = stack.getName().getString().toLowerCase(java.util.Locale.ROOT);
        Identifier id = Registries.ITEM.getId(stack.getItem());
        String itemId = id == null ? "" : id.toString();
        return name.contains("confirm")
                || name.contains("accept")
                || itemId.contains("lime_stained_glass")
                || itemId.contains("green_stained_glass");
    }

    public static boolean isStorage(BlockEntity be) {
        return be instanceof ChestBlockEntity
                || be instanceof TrappedChestBlockEntity
                || be instanceof EnderChestBlockEntity
                || be instanceof ShulkerBoxBlockEntity
                || be instanceof FurnaceBlockEntity
                || be instanceof BlastFurnaceBlockEntity
                || be instanceof SmokerBlockEntity
                || be instanceof BarrelBlockEntity
                || be instanceof HopperBlockEntity;
    }

    public static boolean isStorageOrSpawner(BlockEntity be) {
        return isStorage(be) || be instanceof MobSpawnerBlockEntity;
    }

    public static boolean isPiston(Block block) {
        return block == Blocks.PISTON || block == Blocks.STICKY_PISTON || block == Blocks.PISTON_HEAD || block == Blocks.MOVING_PISTON;
    }

    public static boolean isAmethyst(Block block) {
        return block == Blocks.AMETHYST_CLUSTER || block == Blocks.BUDDING_AMETHYST;
    }

    public static boolean isBeeNest(Block block) {
        return block == Blocks.BEEHIVE || block == Blocks.BEE_NEST;
    }

    public static boolean isStorageBlock(Block block) {
        return block == Blocks.CHEST
                || block == Blocks.TRAPPED_CHEST
                || block == Blocks.ENDER_CHEST
                || block == Blocks.BARREL
                || block == Blocks.HOPPER
                || block == Blocks.FURNACE
                || block == Blocks.BLAST_FURNACE
                || block == Blocks.SMOKER
                || block instanceof net.minecraft.block.ShulkerBoxBlock;
    }

    public static boolean isRotatedDeepslate(BlockState state) {
        Block block = state.getBlock();
        if (block != Blocks.DEEPSLATE
                && block != Blocks.POLISHED_DEEPSLATE
                && block != Blocks.DEEPSLATE_BRICKS
                && block != Blocks.DEEPSLATE_TILES
                && block != Blocks.CHISELED_DEEPSLATE) {
            return false;
        }
        if (!state.contains(net.minecraft.state.property.Properties.AXIS)) return false;
        return state.get(net.minecraft.state.property.Properties.AXIS) != Direction.Axis.Y;
    }

    public static int surfaceY(int x, int z) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return 64;
        return mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
    }

    public static boolean hasBlockLight(BlockPos pos, int minLight) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;
        int block = mc.world.getLightLevel(LightType.BLOCK, pos);
        int sky = mc.world.getLightLevel(LightType.SKY, pos);
        return block >= minLight && block > sky;
    }

    public static boolean hasExposedFace(BlockPos pos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;
        for (Direction dir : Direction.values()) {
            if (!mc.world.getBlockState(pos.offset(dir)).isOpaqueFullCube()) {
                return true;
            }
        }
        return false;
    }

    public static void renderChunks(MatrixStack matrices, Iterable<ChunkMark> chunks, boolean fill, double maxDistanceSq) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        Camera cam = RenderUtils.getCamera();
        if (cam == null) return;
        Vec3d cameraPos = RenderUtils.getCameraPos(cam);
        RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(matrices);
        boolean rendered = false;
        for (ChunkMark mark : chunks) {
            ChunkPos cp = mark.pos();
            if (cameraPos.squaredDistanceTo(cp.getCenterX(), 64.0, cp.getCenterZ()) > maxDistanceSq) continue;
            double x = cp.getStartX() - cameraPos.x;
            double z = cp.getStartZ() - cameraPos.z;
            double y = surfaceY(cp.getCenterX(), cp.getCenterZ()) - cameraPos.y + 0.05;
            if (fill) {
                batch.renderFilledBox(x, y, z, x + 16.0, y + 0.18, z + 16.0, mark.color());
            }
            batch.renderOutlineBox(x, y, z, x + 16.0, y + 0.22, z + 16.0, outline(mark.color()));
            rendered = true;
        }
        if (rendered) batch.flush();
    }

    public static void renderBlocks(MatrixStack matrices, Iterable<BlockMark> blocks, double maxDistanceSq) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        Camera cam = RenderUtils.getCamera();
        if (cam == null) return;
        Vec3d cameraPos = RenderUtils.getCameraPos(cam);
        RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(matrices);
        boolean rendered = false;
        for (BlockMark mark : blocks) {
            BlockPos pos = mark.pos();
            if (cameraPos.squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ()) > maxDistanceSq) continue;
            double x = pos.getX() - cameraPos.x;
            double y = pos.getY() - cameraPos.y;
            double z = pos.getZ() - cameraPos.z;
            batch.renderFilledBox(x + 0.05, y + 0.05, z + 0.05, x + 0.95, y + 0.95, z + 0.95, mark.color());
            batch.renderOutlineBox(x + 0.02, y + 0.02, z + 0.02, x + 0.98, y + 0.98, z + 0.98, outline(mark.color()));
            rendered = true;
        }
        if (rendered) batch.flush();
    }

    public static Map<ChunkPos, ChunkMark> chunkMap() {
        return new ConcurrentHashMap<>();
    }

    public static Set<ChunkPos> chunkSet() {
        return ConcurrentHashMap.newKeySet();
    }

    private static Color outline(Color color) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.min(255, color.getAlpha() + 80));
    }

    public static void disconnect(String reason) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player != null && mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null) {
            mc.getNetworkHandler().getConnection().disconnect(Text.literal(reason));
        }
    }
}
