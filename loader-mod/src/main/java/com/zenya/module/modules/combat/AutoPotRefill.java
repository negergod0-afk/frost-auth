package com.zenya.module.modules.combat;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import com.zenya.utils.InventoryUtils;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public final class AutoPotRefill extends Module {
    private final Setting<Integer> delay = new Setting<>("Delay", 0, 0, 20);
    private final Setting<Integer> healthThreshold = new Setting<>("Health %", 50, 1, 100);
    private final Setting<Boolean> autoOpen = new Setting<>("Auto Open", true);

    private int delayClock = 0;

    public AutoPotRefill() {
        super("Auto Pot Refill", Category.COMBAT);
        addSetting(delay);
        addSetting(healthThreshold);
        addSetting(autoOpen);
    }

    @Override
    public void onEnable() {
        delayClock = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        float healthPercent = (mc.player.getHealth() / mc.player.getMaxHealth()) * 100.0f;
        if (healthPercent > healthThreshold.getValue()) return;

        if (autoOpen.getValue() && !(mc.currentScreen instanceof InventoryScreen)) {
            mc.setScreen(new InventoryScreen(mc.player));
        }
        if (!(mc.currentScreen instanceof InventoryScreen)) return;

        if (delayClock > 0) {
            delayClock--;
            return;
        }

        InventoryScreen inv = (InventoryScreen) mc.currentScreen;
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.POTION) {
                int emptySlot = -1;
                for (int j = 0; j < 9; j++) {
                    if (mc.player.getInventory().getStack(j).isEmpty()) {
                        emptySlot = j;
                        break;
                    }
                }
                if (emptySlot == -1) emptySlot = mc.player.getInventory().getSelectedSlot();
                mc.interactionManager.clickSlot(((PlayerScreenHandler) inv.getScreenHandler()).syncId, i, emptySlot, SlotActionType.SWAP, mc.player);
                delayClock = delay.getValue();
                return;
            }
        }
    }
}
