package com.zenya.module.modules.donut;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.modules.common.ClientModuleTools;
import com.zenya.setting.Setting;

public final class ChunkReload extends Module {
    private final Setting<Double> triggerY = new Setting<>("Trigger Y", -1.0D, -64.0D, 64.0D);
    private final Setting<Integer> normalRenderDistance = new Setting<>("Normal Render Distance", 32, 2, 64);
    private final Setting<Integer> lowRenderDistance = new Setting<>("Low Render Distance", 2, 2, 16);
    private final Setting<Integer> reloadDelay = new Setting<>("Reload Delay Ticks", 20, 5, 100);
    private final Setting<Integer> cooldownSeconds = new Setting<>("Cooldown Seconds", 10, 1, 60);

    private State state = State.IDLE;
    private int tickCounter;
    private long lastReloadTime;
    private boolean hasTriggered;

    public ChunkReload() {
        super("Chunk Reload", Category.DONUT);
        setDescription("Temporarily drops render distance to force a chunk reload.");
        addSetting(triggerY);
        addSetting(normalRenderDistance);
        addSetting(lowRenderDistance);
        addSetting(reloadDelay);
        addSetting(cooldownSeconds);
    }

    @Override
    public void onEnable() {
        state = State.IDLE;
        tickCounter = 0;
        hasTriggered = false;
    }

    @Override
    public void onDisable() {
        setRenderDistance(normalRenderDistance.getValue());
        state = State.IDLE;
        hasTriggered = false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.options == null) return;

        boolean belowTrigger = mc.player.getY() <= triggerY.getValue();
        long now = System.currentTimeMillis();
        long cooldownMs = cooldownSeconds.getValue() * 1000L;

        switch (state) {
            case IDLE -> {
                if (belowTrigger && !hasTriggered && now - lastReloadTime >= cooldownMs) {
                    hasTriggered = true;
                    state = State.DROPPED;
                    tickCounter = 0;
                    setRenderDistance(lowRenderDistance.getValue());
                    ClientModuleTools.chat("Chunk Reload", "Dropping render distance to " + lowRenderDistance.getValue() + " chunks.");
                }
                if (!belowTrigger) {
                    hasTriggered = false;
                }
            }
            case DROPPED -> {
                if (++tickCounter >= reloadDelay.getValue()) {
                    state = State.RESTORING;
                    tickCounter = 0;
                    setRenderDistance(normalRenderDistance.getValue());
                    lastReloadTime = now;
                    ClientModuleTools.chat("Chunk Reload", "Chunks reloaded. Render distance restored to " + normalRenderDistance.getValue() + ".");
                }
            }
            case RESTORING -> {
                state = State.IDLE;
                hasTriggered = false;
            }
        }
    }

    private void setRenderDistance(int chunks) {
        if (mc.options != null) {
            mc.options.getViewDistance().setValue(chunks);
        }
    }

    private enum State {
        IDLE,
        DROPPED,
        RESTORING
    }
}
