package com.zenya.module.modules.combat;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public final class AutoTotem extends Module {
    private final Setting<Float> delay = new Setting<>("Delay", 1.0f, 0.0f, 5.0f);
    private int delayCounter;

    public AutoTotem() {
        super("Auto Totem", Category.COMBAT);
        setDescription("Automatically moves a totem of undying into your off-hand whenever you're holding nothing there.");
        this.addSetting(this.delay);
    }

    @Override
    public void onEnable() {
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (this.mc.player == null || this.mc.interactionManager == null) {
            return;
        }

        
        
        
        if (this.mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
            this.delayCounter = this.delay.getValue().intValue();
            return;
        }

        if (this.delayCounter > 0) {
            --this.delayCounter;
            return;
        }

        final int slot = this.findItemSlot(Items.TOTEM_OF_UNDYING);
        if (slot == -1) {
            return;
        }

        this.mc.interactionManager.clickSlot(
                this.mc.player.currentScreenHandler.syncId, 
                convertSlotIndex(slot), 
                40,
                SlotActionType.SWAP, 
                this.mc.player
        );
        this.delayCounter = this.delay.getValue().intValue();
    }

    public int findItemSlot(final Item item) {
        if (this.mc.player == null) {
            return -1;
        }
        for (int i = 0; i < 36; ++i) {
            if (this.mc.player.getInventory().getStack(i).isOf(item)) {
                return i;
            }
        }
        return -1;
    }

    private static int convertSlotIndex(final int slotIndex) {
        if (slotIndex < 9) {
            return 36 + slotIndex;
        }
        return slotIndex;
    }
}
