package com.zenya.module.modules.combat;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import com.zenya.utils.KeyUtils;
import com.zenya.utils.MathUtils;
import com.zenya.utils.MouseSimulation;
import com.zenya.utils.TimerUtils;
import com.zenya.utils.WorldUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.item.ShieldItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public final class Triggerbot extends Module {
    private final Setting<Boolean> workInScreen = new Setting<>("Work In Screen", false);
    private final Setting<Boolean> whileUse = new Setting<>("While Use", false);
    private final Setting<Boolean> onLeftClick = new Setting<>("On Left Click", false);
    private final Setting<Boolean> allItems = new Setting<>("All Items", false);
    private final Setting<Integer> swordMinDelay = new Setting<>("Sword Min Delay", 540, 0, 1000);
    private final Setting<Integer> swordMaxDelay = new Setting<>("Sword Max Delay", 570, 0, 1000);
    private final Setting<Integer> axeMinDelay = new Setting<>("Axe Min Delay", 780, 0, 1000);
    private final Setting<Integer> axeMaxDelay = new Setting<>("Axe Max Delay", 820, 0, 1000);
    private final Setting<Boolean> checkShield = new Setting<>("Check Shield", false);
    private final Setting<Boolean> onlyCritSword = new Setting<>("Only Crit Sword", false);
    private final Setting<Boolean> onlyCritAxe = new Setting<>("Only Crit Axe", false);
    private final Setting<Boolean> prioritizeCrits = new Setting<>("Prioritize Crits", false);
    private final Setting<Integer> critPatienceMs = new Setting<>("Crit Patience Ms", 250, 0, 800);
    private final Setting<Float> critFallHeight = new Setting<>("Crit Fall Threshold", 0.0f, 0.0f, 1.0f);
    private final Setting<Boolean> swingHand = new Setting<>("Swing Hand", true);
    private final Setting<Boolean> cooldownCheck = new Setting<>("Cooldown Check", true);
    private final Setting<Float> cooldownPercent = new Setting<>("Cooldown %", 95.0f, 80.0f, 100.0f);
    private final Setting<Boolean> clickSimulation = new Setting<>("Click Simulation", true);
    private final Setting<Integer> clickHoldMs = new Setting<>("Click Hold Ms", 40, 10, 120);
    private final Setting<Boolean> strayBypass = new Setting<>("Stray Bypass", false);
    private final Setting<Boolean> allEntities = new Setting<>("All Entities", false);
    private final Setting<Boolean> useShield = new Setting<>("Use Shield", false);
    private final Setting<Integer> shieldTime = new Setting<>("Shield Time", 350, 100, 1000);
    private final Setting<Boolean> samePlayer = new Setting<>("Same Player", false);
    private final Setting<Boolean> whileAscending = new Setting<>("While Ascending", false);
    private final Setting<Float> missChance = new Setting<>("Miss Chance", 0.0f, 0.0f, 100.0f);
    private final Setting<Float> hitChance = new Setting<>("Hit Chance", 100.0f, 0.0f, 100.0f);
    private final Setting<Integer> reactionMinMs = new Setting<>("Reaction Min Ms", 50, 0, 300);
    private final Setting<Integer> reactionMaxMs = new Setting<>("Reaction Max Ms", 120, 0, 500);
    private final Setting<Integer> slotChangeCooldownMs = new Setting<>("Slot Change Cooldown", 100, 0, 500);
    private final Setting<Integer> jitterMs = new Setting<>("Jitter Ms", 15, 0, 50);

    private final TimerUtils timer = new TimerUtils();
    private final TimerUtils critWaitTimer = new TimerUtils();
    private final Random random = new Random();
    private int swordDelay;
    private int axeDelay;
    private boolean pendingCritHit = false;
    private Entity pendingTarget = null;
    private boolean pendingIsSword = false;
    private Entity currentTarget = null;
    private long targetAcquiredTime = 0;
    private int lastSlot = -1;
    private long lastSlotChangeTime = 0;
    private int reactionMs = 0;

    public Triggerbot() {
        super("Trigger Bot", Category.COMBAT);
        setDescription("Automatically hits players when looking at them");
        addSetting(workInScreen);
        addSetting(whileUse);
        addSetting(onLeftClick);
        addSetting(allItems);
        addSetting(swordMinDelay);
        addSetting(swordMaxDelay);
        addSetting(axeMinDelay);
        addSetting(axeMaxDelay);
        addSetting(checkShield);
        addSetting(onlyCritSword);
        addSetting(onlyCritAxe);
        addSetting(prioritizeCrits);
        addSetting(critPatienceMs);
        addSetting(critFallHeight);
        addSetting(swingHand);
        addSetting(cooldownCheck);
        addSetting(cooldownPercent);
        addSetting(clickSimulation);
        addSetting(clickHoldMs);
        addSetting(strayBypass);
        addSetting(allEntities);
        addSetting(useShield);
        addSetting(shieldTime);
        addSetting(samePlayer);
        addSetting(whileAscending);
        addSetting(missChance);
        addSetting(hitChance);
        addSetting(reactionMinMs);
        addSetting(reactionMaxMs);
        addSetting(slotChangeCooldownMs);
        addSetting(jitterMs);
    }

    @Override
    public void onEnable() {
        swordDelay = MathUtils.randomInt(swordMinDelay.getValue(), swordMaxDelay.getValue());
        axeDelay = MathUtils.randomInt(axeMinDelay.getValue(), axeMaxDelay.getValue());
        pendingCritHit = false;
        pendingTarget = null;
        currentTarget = null;
        lastSlot = mc.player != null ? mc.player.getInventory().getSelectedSlot() : -1;
    }

    @Override
    public void onDisable() {
        pendingCritHit = false;
        pendingTarget = null;
        currentTarget = null;
    }

    private boolean isCritWindow() {
        if (mc.player == null) return false;
        return !mc.player.isOnGround() && mc.player.getVelocity().y < 0.0 && mc.player.fallDistance > (double)critFallHeight.getValue() && !mc.player.isClimbing() && !mc.player.isTouchingWater() && !mc.player.hasVehicle() && mc.player.getControllingVehicle() == null;
    }

    private float getCooldown() {
        return mc.player.getAttackCooldownProgress(0.0f);
    }

    private boolean shouldHit(Entity entity) {
        if (entity == null) return false;
        if (!workInScreen.getValue() && mc.currentScreen != null) return false;
        if (onLeftClick.getValue() && !KeyUtils.isKeyPressed(GLFW.GLFW_MOUSE_BUTTON_LEFT)) return false;
        if (!whileUse.getValue()) {
            boolean rightHeld = GLFW.glfwGetMouseButton((long)mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
            if (rightHeld) {
                if (mc.player.getOffHandStack().getItem().getComponents().contains(DataComponentTypes.FOOD) || mc.player.getOffHandStack().getItem() instanceof ShieldItem) {
                    return false;
                }
            }
        }
        if (!whileAscending.getValue()) {
            boolean ascending = !mc.player.isOnGround() && mc.player.getVelocity().y > 0.0;
            boolean floating = !mc.player.isOnGround() && mc.player.fallDistance <= 0.0;
            if (ascending || floating) return false;
        }
        if (samePlayer.getValue() && entity != mc.player.getAttacking()) return false;
        if (entity instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) entity;
            if (checkShield.getValue() && player.isBlocking() && !WorldUtils.isShieldFacingAway(player)) return false;
        }
        boolean isPlayer = entity instanceof PlayerEntity;
        boolean isZombie = strayBypass.getValue() && entity instanceof net.minecraft.entity.mob.ZombieEntity;
        boolean isAny = allEntities.getValue();
        return isPlayer || isZombie || isAny;
    }

    private void hitEntity(Entity entity, boolean isSword) {
        if (useShield.getValue() && mc.player.getOffHandStack().getItem() == Items.SHIELD) {
            if (isSword && mc.player.isBlocking()) {
                MouseSimulation.mouseRelease(1);
            } else if (!isSword) {
                MouseSimulation.mouseClick(1, shieldTime.getValue());
            }
        }
        WorldUtils.hitEntity(entity, swingHand.getValue());
        if (clickSimulation.getValue()) {
            int hold = clickHoldMs.getValue() + random.nextInt(21) - 10;
            MouseSimulation.mouseClick(0, Math.max(10, hold));
        }
        if (isSword) {
            swordDelay = MathUtils.randomInt(swordMinDelay.getValue(), swordMaxDelay.getValue()) + random.nextInt(jitterMs.getValue() + 1);
        } else {
            axeDelay = MathUtils.randomInt(axeMinDelay.getValue(), axeMaxDelay.getValue()) + random.nextInt(jitterMs.getValue() + 1);
        }
        timer.reset();
        pendingCritHit = false;
        pendingTarget = null;
        currentTarget = null;
    }

    @Override
    public void onTick() {
        try {
            if (mc.player == null || mc.world == null) return;

            int currentSlot = mc.player.getInventory().getSelectedSlot();
            if (currentSlot != lastSlot) {
                lastSlotChangeTime = System.currentTimeMillis();
                lastSlot = currentSlot;
            }
            if (System.currentTimeMillis() - lastSlotChangeTime < slotChangeCooldownMs.getValue()) return;

            HitResult hitResult = mc.crosshairTarget;
            if (!(hitResult instanceof EntityHitResult)) {
                pendingCritHit = false;
                pendingTarget = null;
                currentTarget = null;
                return;
            }
            EntityHitResult hit = (EntityHitResult) hitResult;
            Entity entity = hit.getEntity();
            if (!shouldHit(entity)) {
                pendingCritHit = false;
                pendingTarget = null;
                currentTarget = null;
                return;
            }
            boolean isSword = allItems.getValue() || mc.player.getMainHandStack().isIn(ItemTags.SWORDS);
            boolean isAxe = !allItems.getValue() && mc.player.getMainHandStack().getItem() instanceof AxeItem;
            if (!isSword && !isAxe) return;

            if (cooldownCheck.getValue() && getCooldown() < cooldownPercent.getValue() / 100.0f) {
                if (pendingCritHit && pendingTarget != entity) {
                    pendingCritHit = false;
                    pendingTarget = null;
                }
                return;
            }

            if (entity != currentTarget) {
                currentTarget = entity;
                targetAcquiredTime = System.currentTimeMillis();
                reactionMs = MathUtils.randomInt(reactionMinMs.getValue(), reactionMaxMs.getValue());
                return;
            }
            if (System.currentTimeMillis() - targetAcquiredTime < reactionMs) return;

            int delay = isSword ? swordDelay : axeDelay;
            if (!timer.delay(delay)) return;

            if (isSword && onlyCritSword.getValue() && !isCritWindow()) return;
            if (!isSword && onlyCritAxe.getValue() && !isCritWindow()) return;

            if (MathUtils.randomInt(1, 100) > hitChance.getValue()) return;
            if (MathUtils.randomInt(1, 100) <= missChance.getValue()) return;

            if (prioritizeCrits.getValue() && !(isSword ? onlyCritSword.getValue() : onlyCritAxe.getValue())) {
                boolean inCritWindow = isCritWindow();
                if (inCritWindow) {
                    pendingCritHit = false;
                    pendingTarget = null;
                    hitEntity(entity, isSword);
                    return;
                }
                if (!pendingCritHit || pendingTarget != entity) {
                    pendingCritHit = true;
                    pendingTarget = entity;
                    pendingIsSword = isSword;
                    critWaitTimer.reset();
                    return;
                }
                int patience = critPatienceMs.getValue();
                if (patience > 0 && critWaitTimer.delay(patience)) {
                    pendingCritHit = false;
                    pendingTarget = null;
                    hitEntity(entity, isSword);
                }
                return;
            }
            hitEntity(entity, isSword);
        } catch (Exception ignored) {}
    }
}
