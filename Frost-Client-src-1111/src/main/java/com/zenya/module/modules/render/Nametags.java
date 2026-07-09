package com.zenya.module.modules.render;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import com.zenya.utils.renderer.ProjectionUtil;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Pair;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Nametags extends Module {
    private static Nametags INSTANCE;

    private final Setting<Integer> range = new Setting<>("Range", 96, 8, 256);
    private final Setting<Boolean> showOffhand = new Setting<>("Offhand", true);
    private final Setting<Boolean> hideVanilla = new Setting<>("Hide Vanilla", true);

    public Nametags() {
        super("Nametags", Category.RENDER);
        INSTANCE = this;
        addSetting(range);
        addSetting(showOffhand);
        addSetting(hideVanilla);
    }

    public static boolean isActive() {
        return INSTANCE != null && INSTANCE.isEnabled();
    }

    public static boolean shouldShowCustomNametag(Entity entity) {
        return isActive() && entity instanceof PlayerEntity;
    }

    public static boolean shouldHideDefaultNametag(Entity entity) {
        return isActive() && INSTANCE.hideVanilla.getValue() && entity instanceof PlayerEntity;
    }

    public static Object getInstance() {
        return INSTANCE;
    }

    public static void renderHud(DrawContext context, float tickDelta) {
        if (INSTANCE == null || !INSTANCE.isEnabled()) return;
        INSTANCE.renderHudInternal(context, tickDelta);
    }

    private void renderHudInternal(DrawContext context, float tickDelta) {
        if (mc.world == null || mc.player == null) return;

        double maxDistanceSq = range.getValue() * range.getValue();
        List<PlayerTag> tags = new ArrayList<>();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator() || player.isInvisibleTo(mc.player)) {
                continue;
            }

            double distanceSq = mc.player.squaredDistanceTo(player);
            if (distanceSq > maxDistanceSq) {
                continue;
            }

            Vec3d pos = getLerpedPos(player, tickDelta).add(0.0D, player.getHeight() + 0.55D, 0.0D);
            Pair<Vec3d, Boolean> projection = ProjectionUtil.project(ProjectionUtil.modelViewMatrix, ProjectionUtil.projectionMatrix, pos);
            if (projection == null || !projection.getRight()) {
                continue;
            }

            Vec3d screen = projection.getLeft();
            if (screen.z < -1.0D || screen.z > 1.0D) {
                continue;
            }

            tags.add(new PlayerTag(player, (float) screen.x, (float) screen.y, distanceSq));
        }

        tags.sort(Comparator.comparingDouble(PlayerTag::distanceSq).reversed());
        for (PlayerTag tag : tags) {
            renderTag(context, tag.player(), tag.x(), tag.y());
        }
    }

    private Vec3d getLerpedPos(PlayerEntity player, float tickDelta) {
        try {
            return player.getLerpedPos(tickDelta);
        } catch (Throwable ignored) {
            double x = MathHelper.lerp(tickDelta, player.lastRenderX, player.getX());
            double y = MathHelper.lerp(tickDelta, player.lastRenderY, player.getY());
            double z = MathHelper.lerp(tickDelta, player.lastRenderZ, player.getZ());
            return new Vec3d(x, y, z);
        }
    }

    private void renderTag(DrawContext context, PlayerEntity player, float x, float y) {
        TextRenderer renderer = mc.textRenderer;
        String name = player.getName().getString();
        float health = Math.max(0.0F, player.getHealth());
        float absorption = Math.max(0.0F, player.getAbsorptionAmount());
        float maxHealth = Math.max(1.0F, player.getMaxHealth());
        float totalHealth = health + absorption;
        float healthRatio = Math.min(1.0F, totalHealth / Math.max(maxHealth, totalHealth));

        String healthText = Integer.toString(MathHelper.ceil(totalHealth));
        int nameWidth = renderer.getWidth(name);
        int healthWidth = renderer.getWidth(healthText);
        int barWidth = 48;
        int tagWidth = Math.max(barWidth, nameWidth + healthWidth + 8);
        int left = Math.round(x - tagWidth / 2.0F);
        int top = Math.round(y - 24.0F);

        context.getMatrices().pushMatrix();
        context.fill(left - 3, top - 3, left + tagWidth + 3, top + 16, 0xA0000000);
        context.drawText(renderer, Text.literal(name), left, top, 0xFFFFFFFF, true);
        context.drawText(renderer, Text.literal(healthText), left + tagWidth - healthWidth, top, healthColor(healthRatio), true);

        int barTop = top + 11;
        context.fill(left, barTop, left + tagWidth, barTop + 4, 0xE0303030);
        context.fill(left, barTop, left + Math.round(tagWidth * healthRatio), barTop + 4, healthColor(healthRatio));

        if (showOffhand.getValue()) {
            ItemStack offhand = player.getOffHandStack();
            if (!offhand.isEmpty()) {
                int itemX = left + tagWidth + 6;
                int itemY = top - 2;
                context.fill(itemX - 2, itemY - 2, itemX + 18, itemY + 18, 0xA0000000);
                context.drawItem(offhand, itemX, itemY);
            }
        }
        context.getMatrices().popMatrix();
    }

    private int healthColor(float ratio) {
        if (ratio > 0.66F) return 0xFF38D66B;
        if (ratio > 0.33F) return 0xFFFFC83D;
        return 0xFFFF4444;
    }

    private record PlayerTag(PlayerEntity player, float x, float y, double distanceSq) {
    }
}
