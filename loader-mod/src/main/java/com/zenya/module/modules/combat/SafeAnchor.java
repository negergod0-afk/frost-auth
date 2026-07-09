package com.zenya.module.modules.combat;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public final class SafeAnchor extends Module {
    private final Setting<Integer> switchDelay = new Setting<>("Switch Delay", 0, 0, 20);
    private final Setting<Integer> glowstoneDelay = new Setting<>("Glowstone Delay", 0, 0, 20);
    private final Setting<Boolean> autoExplode = new Setting<>("Auto Explode", false);
    private final Setting<Integer> explodeDelay = new Setting<>("Explode Delay", 0, 0, 20);
    private final Setting<Integer> totemSlot = new Setting<>("Totem Slot", 1, 1, 9);
    private final Setting<Boolean> autoFillTotem = new Setting<>("Auto Fill Totem", true);
    private final Setting<Boolean> placeShield = new Setting<>("Place Shield", false);
    private final Setting<Integer> shieldDelay = new Setting<>("Shield Delay", 0, 0, 20);

    private Phase phase = Phase.IDLE;
    private int delay;
    private int chargedWait;

    public SafeAnchor() {
        super("Safe Anchor", Category.COMBAT);
        setDescription("Safer anchor macro for PvP with glowstone fill, slot switching, and optional shielding.");
        addSetting(switchDelay);
        addSetting(glowstoneDelay);
        addSetting(autoExplode);
        addSetting(explodeDelay);
        addSetting(totemSlot);
        addSetting(autoFillTotem);
        addSetting(placeShield);
        addSetting(shieldDelay);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null || mc.interactionManager == null) return;
        if (isShieldOrFoodActive()) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult hit)) {
            reset();
            return;
        }
        if (!mc.world.getBlockState(hit.getBlockPos()).isOf(Blocks.RESPAWN_ANCHOR)) {
            reset();
            return;
        }

        mc.options.sneakKey.setPressed(false);
        tickFast(hit);
    }

    private void tickFast(BlockHitResult hit) {
        switch (phase) {
            case IDLE -> {
                boolean uncharged = isUncharged(hit.getBlockPos());
                boolean charged = isCharged(hit.getBlockPos());
                if (!uncharged && !charged) return;
                phase = needsFill() ? Phase.FILL_TOTEM : (charged ? Phase.SWITCH_TOTEM : Phase.PLACE_SHIELD);
                tickFast(hit);
            }
            case FILL_TOTEM -> {
                if (!needsFill()) {
                    delay = 0;
                    phase = isCharged(hit.getBlockPos()) ? Phase.SWITCH_TOTEM : Phase.PLACE_SHIELD;
                    tickFast(hit);
                    return;
                }

                int glowSlot = findInInventory(Items.GLOWSTONE);
                if (glowSlot == -1) {
                    delay = 0;
                    phase = isCharged(hit.getBlockPos()) ? Phase.SWITCH_TOTEM : Phase.PLACE_SHIELD;
                    return;
                }
                moveGlowstoneToTotemSlot(glowSlot);
            }
            case PLACE_SHIELD -> {
                if (!placeShield.getValue()) {
                    delay = 0;
                    phase = Phase.SWITCH_GLOW;
                    tickFast(hit);
                    return;
                }
                if (delay++ < shieldDelay.getValue()) return;
                delay = 0;
                doPlaceShield(hit);
                phase = Phase.SWITCH_GLOW;
                if (shieldDelay.getValue() <= 0) tickFast(hit);
            }
            case SWITCH_GLOW -> {
                if (!isHoldingGlowstone()) {
                    if (delay++ < switchDelay.getValue()) return;
                    delay = 0;
                    int slot = findInHotbar(Items.GLOWSTONE);
                    if (slot == -1) {
                        reset();
                        return;
                    }
                    sendSlot(slot);
                    if (switchDelay.getValue() > 0) {
                        phase = Phase.PLACE_GLOW;
                        return;
                    }
                }
                delay = 0;
                phase = Phase.PLACE_GLOW;
                tickFast(hit);
            }
            case PLACE_GLOW -> {
                if (delay++ < glowstoneDelay.getValue()) return;
                delay = 0;
                interactBlock(hit);
                mc.player.swingHand(Hand.MAIN_HAND);
                chargedWait = 0;
                phase = Phase.SWITCH_TOTEM;
            }
            case SWITCH_TOTEM -> {
                if (!isCharged(hit.getBlockPos())) {
                    if (++chargedWait > 10) {
                        chargedWait = 0;
                        reset();
                    }
                    return;
                }
                chargedWait = 0;
                if (needsFill() && findInInventory(Items.GLOWSTONE) != -1) {
                    phase = Phase.FILL_TOTEM;
                    tickFast(hit);
                    return;
                }

                int target = totemSlot.getValue() - 1;
                if (mc.player.getInventory().getSelectedSlot() != target) {
                    if (delay++ < switchDelay.getValue()) return;
                    delay = 0;
                    sendSlot(target);
                    if (switchDelay.getValue() > 0) {
                        phase = Phase.EXPLODE;
                        return;
                    }
                }
                delay = 0;
                if (autoExplode.getValue()) {
                    phase = Phase.EXPLODE;
                    tickFast(hit);
                } else {
                    reset();
                }
            }
            case EXPLODE -> {
                if (!isCharged(hit.getBlockPos())) {
                    reset();
                    return;
                }
                if (delay++ < explodeDelay.getValue()) return;
                delay = 0;
                interactBlock(hit);
                mc.player.swingHand(Hand.MAIN_HAND);
                reset();
            }
        }
    }

    private void doPlaceShield(BlockHitResult anchorHit) {
        BlockPos shieldPos = getShieldPos(anchorHit.getBlockPos());
        if (!mc.world.getBlockState(shieldPos).isAir()) return;

        Direction placeDir = null;
        BlockPos neighborPos = null;
        for (Direction dir : Direction.values()) {
            BlockPos candidate = shieldPos.offset(dir);
            if (!mc.world.getBlockState(candidate).isAir()) {
                placeDir = dir;
                neighborPos = candidate;
                break;
            }
        }
        if (placeDir == null || neighborPos == null) return;

        int shieldSlot = findShieldBlockSlot();
        if (shieldSlot == -1) return;

        int prevSlot = mc.player.getInventory().getSelectedSlot();
        sendSlot(shieldSlot);

        Direction hitFace = placeDir.getOpposite();
        Vec3d hitVec = Vec3d.ofCenter(neighborPos).add(Vec3d.of(hitFace.getVector()).multiply(0.5D));
        interactBlock(new BlockHitResult(hitVec, hitFace, neighborPos, false));
        mc.player.swingHand(Hand.MAIN_HAND);
        sendSlot(prevSlot);
    }

    private BlockPos getShieldPos(BlockPos anchorPos) {
        BlockPos playerPos = mc.player.getBlockPos();
        int dx = anchorPos.getX() - playerPos.getX();
        int dz = anchorPos.getZ() - playerPos.getZ();
        int sx = Integer.compare(dx, 0);
        int sz = Integer.compare(dz, 0);
        return Math.abs(dx) >= Math.abs(dz) ? playerPos.add(sx, 0, 0) : playerPos.add(0, 0, sz);
    }

    private int findShieldBlockSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (item instanceof BlockItem blockItem) {
                Block block = blockItem.getBlock();
                if (!block.getDefaultState().isAir()) return i;
            }
        }
        return -1;
    }

    private boolean isCharged(BlockPos pos) {
        var state = mc.world.getBlockState(pos);
        return state.isOf(Blocks.RESPAWN_ANCHOR) && state.get(RespawnAnchorBlock.CHARGES) > 0;
    }

    private boolean isUncharged(BlockPos pos) {
        var state = mc.world.getBlockState(pos);
        return state.isOf(Blocks.RESPAWN_ANCHOR) && state.get(RespawnAnchorBlock.CHARGES) == 0;
    }

    private boolean needsFill() {
        if (!autoFillTotem.getValue()) return false;
        int slot = totemSlot.getValue() - 1;
        return !mc.player.getInventory().getStack(slot).isOf(Items.GLOWSTONE);
    }

    private boolean isHoldingGlowstone() {
        return mc.player.getMainHandStack().isOf(Items.GLOWSTONE);
    }

    private int findInHotbar(Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        }
        return -1;
    }

    private int findInInventory(Item item) {
        int hotbar = findInHotbar(item);
        if (hotbar != -1) return hotbar;
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        }
        return -1;
    }

    private void moveGlowstoneToTotemSlot(int fromSlot) {
        int toSlot = totemSlot.getValue() - 1;
        int handlerSlot = fromSlot < 9 ? fromSlot + 36 : fromSlot;
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, handlerSlot, toSlot, SlotActionType.SWAP, mc.player);
    }

    private void sendSlot(int slot) {
        if (slot < 0 || slot > 8) return;
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        mc.player.getInventory().setSelectedSlot(slot);
    }

    private void interactBlock(BlockHitResult hit) {
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
    }

    private boolean isShieldOrFoodActive() {
        boolean isFood = mc.player.getMainHandStack().get(DataComponentTypes.FOOD) != null
                || mc.player.getOffHandStack().get(DataComponentTypes.FOOD) != null;
        boolean isShield = mc.player.getMainHandStack().isOf(Items.SHIELD)
                || mc.player.getOffHandStack().isOf(Items.SHIELD);
        boolean rightMouse = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        return (isFood || isShield) && rightMouse;
    }

    private void reset() {
        phase = Phase.IDLE;
        delay = 0;
        chargedWait = 0;
    }

    private enum Phase {
        IDLE,
        FILL_TOTEM,
        PLACE_SHIELD,
        SWITCH_GLOW,
        PLACE_GLOW,
        SWITCH_TOTEM,
        EXPLODE
    }
}
