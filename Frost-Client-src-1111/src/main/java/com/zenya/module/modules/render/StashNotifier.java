package com.zenya.module.modules.render;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.modules.common.ClientModuleTools;
import com.zenya.setting.ModeSetting;
import com.zenya.setting.Setting;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashSet;
import java.util.Set;

public final class StashNotifier extends Module {
    private final Setting<Integer> minimumStorageCount = new Setting<>("Min Storage Count", 4, 1, 500);
    private final Setting<Integer> minimumDistance = new Setting<>("Min Distance", 0, 0, 10000);
    private final Setting<Boolean> criticalSpawner = new Setting<>("Critical Spawner", true);
    private final Setting<Boolean> disconnectOnFind = new Setting<>("Disconnect on Find", false);
    private final Setting<Boolean> notifications = new Setting<>("Notifications", true);
    private final ModeSetting notificationMode = new ModeSetting("Notification Mode", "Chat", "Chat", "Toast", "Both");
    private final Set<ChunkPos> processedChunks = new HashSet<>();

    public StashNotifier() {
        super("Stash Notifier", Category.RENDER);
        setDescription("Notifies when loaded chunks contain many storage blocks or a critical spawner.");
        addSetting(minimumStorageCount);
        addSetting(minimumDistance);
        addSetting(criticalSpawner);
        addSetting(disconnectOnFind);
        addSetting(notifications);
        addSetting(notificationMode);
    }

    @Override
    public void onEnable() {
        processedChunks.clear();
    }

    @Override
    public void onDisable() {
        processedChunks.clear();
    }

    @Override
    public void onWorldChange() {
        processedChunks.clear();
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        ChunkPos center = mc.player.getChunkPos();
        int view = ClientModuleTools.viewDistanceChunks();
        for (int cx = center.x - view; cx <= center.x + view; cx++) {
            for (int cz = center.z - view; cz <= center.z + view; cz++) {
                ChunkPos cp = new ChunkPos(cx, cz);
                if (!processedChunks.add(cp)) continue;
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz, false);
                if (chunk != null) scanChunk(chunk, cp);
            }
        }
    }

    private void scanChunk(WorldChunk chunk, ChunkPos chunkPos) {
        int storageCount = 0;
        boolean hasSpawner = false;
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (ClientModuleTools.isStorage(be)) storageCount++;
            if (be instanceof MobSpawnerBlockEntity) {
                hasSpawner = true;
                storageCount++;
            }
        }

        double dx = mc.player.getX() - chunkPos.getCenterX();
        double dz = mc.player.getZ() - chunkPos.getCenterZ();
        if (Math.sqrt(dx * dx + dz * dz) < minimumDistance.getValue()) return;

        boolean spawnerTriggered = criticalSpawner.getValue() && hasSpawner;
        if (storageCount < minimumStorageCount.getValue() && !spawnerTriggered) return;

        String msg = "Stash with \u00a7e" + storageCount + "\u00a7r storages at X: " + chunkPos.getCenterX()
                + " Y: " + mc.player.getBlockY() + " Z: " + chunkPos.getCenterZ()
                + (spawnerTriggered ? " \u00a7c(Spawner!)" : "");
        notify(msg);
        if (disconnectOnFind.getValue()) ClientModuleTools.disconnect("Stash found");
    }

    private void notify(String message) {
        if (!notifications.getValue()) return;
        if (notificationMode.is("Chat") || notificationMode.is("Both")) ClientModuleTools.chat("Stash Notifier", message);
        if ((notificationMode.is("Toast") || notificationMode.is("Both")) && mc.getToastManager() != null) {
            SystemToast.show(mc.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION, Text.literal("Stash Notifier"), Text.literal(message.replace("\u00a7e", "").replace("\u00a7r", "").replace("\u00a7c", "")));
        }
    }
}
