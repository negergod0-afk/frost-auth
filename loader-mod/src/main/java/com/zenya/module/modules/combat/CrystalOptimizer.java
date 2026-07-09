package com.zenya.module.modules.combat;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import com.zenya.utils.BlockUtils;
import com.zenya.utils.CrystalUtils;
import com.zenya.utils.InventoryUtils;
import com.zenya.utils.MouseSimulation;
import com.zenya.utils.WorldUtils;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public final class CrystalOptimizer extends Module {
    private final Setting<Float> range = new Setting<>("Range", 5.0f, 1.0f, 6.0f);
    private final Setting<Boolean> autoSwitch = new Setting<>("Auto Switch", true);
    private final Setting<Boolean> swingHand = new Setting<>("Swing Hand", true);
    private final Setting<Boolean> clickSimulation = new Setting<>("Click Simulation", true);

    public CrystalOptimizer() {
        super("Crystal Optimizer", Category.COMBAT);
        addSetting(range);
        addSetting(autoSwitch);
        addSetting(swingHand);
        addSetting(clickSimulation);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;

        PlayerEntity target = WorldUtils.findNearestPlayer((PlayerEntity) mc.player, range.getValue(), true, true);
        if (target == null) return;

        if (mc.player.getMainHandStack().getItem() != Items.END_CRYSTAL) {
            if (autoSwitch.getValue()) {
                if (!InventoryUtils.switchToHotbar(Items.END_CRYSTAL)) return;
            } else {
                return;
            }
        }

        HitResult hitResult = mc.crosshairTarget;
        if (hitResult instanceof BlockHitResult && hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult hit = (BlockHitResult) hitResult;
            if ((BlockUtils.isBlockAt(hit.getBlockPos(), Blocks.OBSIDIAN) || BlockUtils.isBlockAt(hit.getBlockPos(), Blocks.BEDROCK)) && CrystalUtils.isPlaceable(hit.getBlockPos())) {
                if (clickSimulation.getValue()) MouseSimulation.mouseClick(1);
                WorldUtils.interactBlock(hit, swingHand.getValue());
            }
        }
    }
}
