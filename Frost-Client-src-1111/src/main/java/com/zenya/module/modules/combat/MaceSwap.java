package com.zenya.module.modules.combat;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public final class MaceSwap extends Module {
    private final Setting<Boolean> switchBack = new Setting<>("Switch Back", true);
    private final Setting<Integer> switchBackDelay = new Setting<>("Switch Back Delay", 1, 0, 20);
    private final Setting<Boolean> ignoreEndCrystals = new Setting<>("Ignore End Crystals", false);
    private int previousSlot = -1;
    private int switchBackClock = 0;

    public MaceSwap() {
        super("Mace Swap", Category.COMBAT);
        setDescription("Switches to a mace when attacking");
        addSetting(switchBack);
        addSetting(switchBackDelay);
        addSetting(ignoreEndCrystals);
    }

    @Override
    public void onEnable() {
        previousSlot = -1;
        switchBackClock = 0;
    }

    @Override
    public void onDisable() {
        previousSlot = -1;
        switchBackClock = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (switchBackClock <= 0) return;
        switchBackClock--;
        if (switchBackClock == 0 && previousSlot != -1) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
            previousSlot = -1;
        }
    }

    @Override
    public boolean onPacketSend(net.minecraft.network.packet.Packet<?> packet) {
        if (mc.player == null || mc.world == null) return false;
        Entity target = getTargetEntity();
        if (target == null) return false;
        if (ignoreEndCrystals.getValue() && target instanceof EndCrystalEntity) return false;
        int maceSlot = findMaceSlot();
        if (maceSlot == -1) return false;
        previousSlot = switchBack.getValue() ? mc.player.getInventory().getSelectedSlot() : -1;
        mc.player.getInventory().setSelectedSlot(maceSlot);
        switchBackClock = switchBack.getValue() && previousSlot != -1 ? switchBackDelay.getValue() : 0;
        return false;
    }

    private Entity getTargetEntity() {
        HitResult hitResult = mc.crosshairTarget;
        if (!(hitResult instanceof EntityHitResult)) return null;
        if (mc.crosshairTarget.getType() != HitResult.Type.ENTITY) return null;
        return ((EntityHitResult) hitResult).getEntity();
    }

    private int findMaceSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; ++i) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.MACE) return i;
        }
        return -1;
    }
}
