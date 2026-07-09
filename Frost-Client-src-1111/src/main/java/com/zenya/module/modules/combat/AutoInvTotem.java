package com.zenya.module.modules.combat;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.ModeSetting;
import com.zenya.setting.Setting;
import com.zenya.utils.InventoryUtils;
import com.zenya.utils.TimerUtils;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public final class AutoInvTotem extends Module {
    private final ModeSetting mode = new ModeSetting("Mode", "Blatant", "Blatant", "Random", "Legit");
    private final Setting<Float> delay = new Setting<>("Delay", 0.0f, 0.0f, 20.0f);
    private final Setting<Boolean> hotbar = new Setting<>("Hotbar", true);
    private final Setting<Integer> totemSlot = new Setting<>("Totem Slot", 1, 1, 9);
    private final Setting<Boolean> autoSwitch = new Setting<>("Auto Switch", false);
    private final Setting<Boolean> forceTotem = new Setting<>("Force Totem", false);
    private final Setting<Boolean> autoOpen = new Setting<>("Auto Open", false);
    private final Setting<Float> stayOpenFor = new Setting<>("Stay Open For", 0.0f, 0.0f, 20.0f);

    int openClock = -1;
    int closeClock = -1;
    TimerUtils openTimer = new TimerUtils();
    TimerUtils closeTimer = new TimerUtils();

    public AutoInvTotem() {
        super("AutoInvTotem", Category.COMBAT);
        setDescription("Automatically equips a totem in your offhand and main hand if empty");
        addSetting(mode);
        addSetting(delay);
        addSetting(hotbar);
        addSetting(totemSlot);
        addSetting(autoSwitch);
        addSetting(forceTotem);
        addSetting(autoOpen);
        addSetting(stayOpenFor);
    }

    @Override
    public void onEnable() {
        openClock = -1;
        closeClock = -1;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        if (shouldOpen() && autoOpen.getValue()) {
            mc.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen((PlayerEntity)mc.player));
        }
        if (!(mc.currentScreen instanceof InventoryScreen)) {
            openClock = -1;
            closeClock = -1;
            return;
        }
        if (openClock == -1) openClock = delay.getValue().intValue();
        if (closeClock == -1) closeClock = stayOpenFor.getValue().intValue();
        if (openClock > 0) {
            openClock--;
            return;
        }
        PlayerInventory inventory = mc.player.getInventory();
        if (autoSwitch.getValue()) {
            inventory.setSelectedSlot(totemSlot.getValue() - 1);
        }
        if (openClock <= 0) {
            if (inventory.getStack(40).getItem() != Items.TOTEM_OF_UNDYING) {
                int slot = mode.is("Blatant") ? InventoryUtils.findFirstTotemSlot() : (mode.is("Random") ? InventoryUtils.findRandomTotemSlot() : InventoryUtils.findFirstTotemSlot());
                if (slot != -1) {
                    mc.interactionManager.clickSlot(((PlayerScreenHandler)((net.minecraft.client.gui.screen.ingame.InventoryScreen)mc.currentScreen).getScreenHandler()).syncId, slot, 40, SlotActionType.SWAP, (PlayerEntity)mc.player);
                    return;
                }
            }
            if (hotbar.getValue() && (inventory.getStack(totemSlot.getValue() - 1).isEmpty() || forceTotem.getValue() && inventory.getStack(totemSlot.getValue() - 1).getItem() != Items.TOTEM_OF_UNDYING)) {
                int slot = mode.is("Blatant") ? InventoryUtils.findFirstTotemSlot() : (mode.is("Random") ? InventoryUtils.findRandomTotemSlot() : InventoryUtils.findFirstTotemSlot());
                if (slot != -1) {
                    mc.interactionManager.clickSlot(((PlayerScreenHandler)((net.minecraft.client.gui.screen.ingame.InventoryScreen)mc.currentScreen).getScreenHandler()).syncId, slot, inventory.getSelectedSlot(), SlotActionType.SWAP, (PlayerEntity)mc.player);
                    return;
                }
            }
            if (isDone() && autoOpen.getValue()) {
                if (closeClock != 0) {
                    closeClock--;
                    return;
                }
                mc.currentScreen.close();
                closeClock = stayOpenFor.getValue().intValue();
            }
        }
    }

    public boolean isDone() {
        if (mc.player == null) return false;
        if (hotbar.getValue()) {
            return mc.player.getInventory().getStack(totemSlot.getValue() - 1).getItem() == Items.TOTEM_OF_UNDYING && mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING && mc.currentScreen instanceof InventoryScreen;
        }
        return mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING && mc.currentScreen instanceof InventoryScreen;
    }

    public boolean shouldOpen() {
        if (mc.player == null) return false;
        if (hotbar.getValue()) {
            return (mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING || mc.player.getInventory().getStack(totemSlot.getValue() - 1).getItem() != Items.TOTEM_OF_UNDYING) && !(mc.currentScreen instanceof InventoryScreen) && InventoryUtils.countItemInInventory(item -> item == Items.TOTEM_OF_UNDYING) != 0;
        }
        return mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING && !(mc.currentScreen instanceof InventoryScreen) && InventoryUtils.countItemInInventory(item -> item == Items.TOTEM_OF_UNDYING) != 0;
    }
}
