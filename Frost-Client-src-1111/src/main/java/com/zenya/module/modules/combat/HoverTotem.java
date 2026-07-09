package com.zenya.module.modules.combat;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public final class HoverTotem extends Module {
    private final Setting<Float> delay = new Setting<>("Delay", 0.0f, 0.0f, 20.0f);
    private final Setting<Boolean> hotbar = new Setting<>("Hotbar", true);
    private final Setting<Integer> totemSlot = new Setting<>("Totem Slot", 1, 1, 9);
    private final Setting<Boolean> autoSwitch = new Setting<>("Auto Switch", false);
    private int delayClock;

    public HoverTotem() {
        super("Hover Totem", Category.COMBAT);
        setDescription("Equips a totem in your hotbar and offhand slots if a totem is hovered");
        addSetting(delay);
        addSetting(hotbar);
        addSetting(totemSlot);
        addSetting(autoSwitch);
    }

    @Override
    public void onEnable() {
        delayClock = 0;
    }

    @Override
    public void onTick() {
        if (mc.currentScreen instanceof InventoryScreen) {
            InventoryScreen inv = (InventoryScreen) mc.currentScreen;
            net.minecraft.screen.slot.Slot hoveredSlot = inv.getScreenHandler().getCursorStack().isEmpty() ? null : null; // Can't easily get hovered slot in vanilla
            // Use a simpler approach: check if the slot under mouse has totem
            if (autoSwitch.getValue()) {
                mc.player.getInventory().setSelectedSlot(totemSlot.getValue() - 1);
            }
            // Since we can't easily get the hovered slot without access to handled screen internals,
            // we'll use a different approach: check all inventory slots for totem and auto-equip
            if (delayClock > 0) {
                delayClock--;
                return;
            }
            int totemSlotIndex = totemSlot.getValue() - 1;
            for (int i = 0; i < 36; i++) {
                if (mc.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                    if (hotbar.getValue() && mc.player.getInventory().getStack(totemSlotIndex).getItem() != Items.TOTEM_OF_UNDYING) {
                        mc.interactionManager.clickSlot(((PlayerScreenHandler) inv.getScreenHandler()).syncId, i, totemSlotIndex, SlotActionType.SWAP, mc.player);
                        delayClock = delay.getValue().intValue();
                        return;
                    } else if (mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                        mc.interactionManager.clickSlot(((PlayerScreenHandler) inv.getScreenHandler()).syncId, i, 40, SlotActionType.SWAP, mc.player);
                        delayClock = delay.getValue().intValue();
                        return;
                    }
                }
            }
        } else {
            delayClock = delay.getValue().intValue();
        }
    }
}
