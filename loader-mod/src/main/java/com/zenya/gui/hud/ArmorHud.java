package com.zenya.gui.hud;

import com.zenya.module.modules.client.ZenyaPlus;
import com.zenya.utils.renderer.RenderUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

public final class ArmorHud {

    public static final ArmorHud INSTANCE = new ArmorHud();

    private static final float CARD_RADIUS = 10.0f;
    private static final int ITEM_SIZE = 16;
    private static final int ITEM_SPACING = 4;
    private static final int PADDING = 8;
    private static final int DURABILITY_BAR_HEIGHT = 3;
    private static final int DURABILITY_BAR_WIDTH = ITEM_SIZE;
    private static final int DURABILITY_BAR_SPACING = 2;
    
    // Green color for durability bar
    private static final int DURABILITY_COLOR = 0xFF00FF00;
    private static final int DURABILITY_BG_COLOR = 0xFF333333;

    private ArmorHud() {}

    public void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;
        if (client.getDebugHud().shouldShowDebugHud()) return;
        if (client.currentScreen instanceof com.zenya.gui.ClickGUI) return;

        // Get armor pieces
        ItemStack helmet = client.player.getEquippedStack(EquipmentSlot.HEAD);
        ItemStack chestplate = client.player.getEquippedStack(EquipmentSlot.CHEST);
        ItemStack leggings = client.player.getEquippedStack(EquipmentSlot.LEGS);
        ItemStack boots = client.player.getEquippedStack(EquipmentSlot.FEET);

        // Check if player has any armor
        if (helmet.isEmpty() && chestplate.isEmpty() && leggings.isEmpty() && boots.isEmpty()) {
            return;
        }

        // Calculate dimensions
        int totalWidth = PADDING * 2 + ITEM_SIZE * 4 + ITEM_SPACING * 3;
        int totalHeight = PADDING * 2 + ITEM_SIZE + DURABILITY_BAR_SPACING + DURABILITY_BAR_HEIGHT;

        // Position (centered horizontally above hotbar)
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        int x = (screenWidth - totalWidth) / 2;
        int y = screenHeight - 90; // Above hotbar

        // Get background color from ZenyaPlus settings
        int backgroundColor = ZenyaPlus.getBackgroundARGB();

        // Draw background card
        RenderUtil.drawRoundedRect(context, x, y, totalWidth, totalHeight, CARD_RADIUS, backgroundColor, false);
        
        // Draw subtle outline
        RenderUtil.drawOutline(context, x, y, totalWidth, totalHeight, CARD_RADIUS, 1.0f, 0xFF2A2A2A, false);

        // Render armor pieces from left to right: boots, leggings, chestplate, helmet
        ItemStack[] armorPieces = {boots, leggings, chestplate, helmet};
        
        for (int i = 0; i < armorPieces.length; i++) {
            ItemStack armor = armorPieces[i];
            int itemX = x + PADDING + i * (ITEM_SIZE + ITEM_SPACING);
            int itemY = y + PADDING;

            // Draw armor item
            if (!armor.isEmpty()) {
                context.drawItem(armor, itemX, itemY);

                // Draw durability bar if item is damageable
                if (armor.isDamageable()) {
                    int maxDurability = armor.getMaxDamage();
                    int currentDurability = maxDurability - armor.getDamage();
                    float durabilityPercent = (float) currentDurability / (float) maxDurability;

                    int barX = itemX;
                    int barY = itemY + ITEM_SIZE + DURABILITY_BAR_SPACING;
                    int barWidth = Math.round(DURABILITY_BAR_WIDTH * durabilityPercent);

                    // Draw background bar
                    RenderUtil.drawRoundedRect(context, barX, barY, DURABILITY_BAR_WIDTH, DURABILITY_BAR_HEIGHT, 1.5f, DURABILITY_BG_COLOR, false);

                    // Draw filled bar based on durability percentage
                    if (barWidth > 0) {
                        // Color transitions from red to yellow to green based on durability
                        int barColor = getDurabilityColor(durabilityPercent);
                        RenderUtil.drawRoundedRect(context, barX, barY, barWidth, DURABILITY_BAR_HEIGHT, 1.5f, barColor, false);
                    }
                }
            }
        }
    }

    private int getDurabilityColor(float percent) {
        // Red (low) -> Yellow (medium) -> Green (high)
        if (percent > 0.5f) {
            // Green to Yellow (0.5 to 1.0)
            float t = (percent - 0.5f) * 2.0f; // 0 to 1
            int r = (int) (255 * (1.0f - t));
            int g = 255;
            return 0xFF000000 | (r << 16) | (g << 8);
        } else {
            // Red to Yellow (0.0 to 0.5)
            float t = percent * 2.0f; // 0 to 1
            int r = 255;
            int g = (int) (255 * t);
            return 0xFF000000 | (r << 16) | (g << 8);
        }
    }
}
