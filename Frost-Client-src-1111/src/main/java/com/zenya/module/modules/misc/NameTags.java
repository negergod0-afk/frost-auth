package com.zenya.module.modules.misc;

import com.zenya.gui.ClickGUI;
import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.modules.client.Friends;
import com.zenya.module.modules.client.ZenyaPlus;
import com.zenya.module.modules.render.Freecam;
import com.zenya.setting.Setting;
import com.zenya.utils.NametagRenderState;
import com.zenya.utils.renderer.ProjectionUtil;
import com.zenya.utils.renderer.RenderUtil;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class NameTags extends Module {

    public static NameTags instance;

    // Settings
    private final Setting<Integer>  range       = new Setting<>("Range",    96, 8, 256);
    private final Setting<Boolean>  self        = new Setting<>("Self",     false);
    private final Setting<Boolean>  showName    = new Setting<>("Name",     true);
    private final Setting<Boolean>  showHealth  = new Setting<>("Health",   true);
    private final Setting<Boolean>  showPing    = new Setting<>("Ping",     true);
    private final Setting<Boolean>  showArmor   = new Setting<>("Armor",    true);

    // Heart texture identifiers (vanilla GUI)
    private static final Identifier HEART_CONTAINER   = Identifier.ofVanilla("hud/heart/container");
    private static final Identifier HEART_FULL        = Identifier.ofVanilla("hud/heart/full");
    private static final Identifier HEART_HALF        = Identifier.ofVanilla("hud/heart/half");
    private static final Identifier HEART_ABS_FULL    = Identifier.ofVanilla("hud/heart/absorbing_full");
    private static final Identifier HEART_ABS_HALF    = Identifier.ofVanilla("hud/heart/absorbing_half");

    private static final int  HEART_SIZE    = 9;
    private static final int  HEART_GAP     = 8; // spacing between heart icons
    private static final int  ITEM_SIZE     = 16;
    private static final int  ITEM_GAP      = 2;
    private static final float TAG_SCALE    = 0.65F;

    public NameTags() {
        super("NameTags", Category.MISC);
        instance = this;
        addSetting(range);
        addSetting(self);
        addSetting(showName);
        addSetting(showHealth);
        addSetting(showPing);
        addSetting(showArmor);
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled() && mc != null && mc.player != null;
    }

    /** Called by mixins to decide whether to suppress vanilla nametag. */
    public static boolean shouldHideVanilla(LivingEntity entity) {
        return isActive() && instance.shouldRenderFor(entity);
    }

    public boolean shouldRenderFor(LivingEntity entity) {
        if (!entity.isAlive() || entity instanceof ArmorStandEntity || !(entity instanceof PlayerEntity)) {
            return false;
        }
        if (entity == mc.player) {
            return self.getValue() && (!mc.options.getPerspective().isFirstPerson()
                    || (Freecam.instance != null && Freecam.instance.isEnabled()));
        }
        return !entity.isInvisibleTo(mc.player);
    }

    public static void renderHud(DrawContext context, float tickDelta) {
        if (!isActive() || mc.world == null || mc.options.hudHidden || isMenuOpen()) {
            return;
        }

        NameTags module = instance;
        double limitSq = (double) module.range.getValue() * module.range.getValue();

        // Collect visible tags with their projected screen positions
        List<TagEntry> tags = new ArrayList<>();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!module.shouldRenderFor(player)) {
                continue;
            }

            double distSq = mc.player.squaredDistanceTo(player);
            if (distSq > limitSq) {
                continue;
            }

            Vec3d pos = getLerpedPos(player, tickDelta).add(0.0, player.getHeight() + 0.55, 0.0);
            Pair<Vec3d, Boolean> proj = ProjectionUtil.project(
                    ProjectionUtil.modelViewMatrix, ProjectionUtil.projectionMatrix, pos);

            if (proj == null || !proj.getRight()) {
                continue;
            }

            Vec3d screen = proj.getLeft();
            if (screen.z < -1.0 || screen.z > 1.0) {
                continue;
            }

            tags.add(new TagEntry(player, (float) screen.x, (float) screen.y, distSq));
        }

        // Render furthest first so closer ones draw on top
        tags.sort(Comparator.comparingDouble(TagEntry::distSq).reversed());

        Matrix3x2fStack matrices = context.getMatrices();

        for (TagEntry tag : tags) {
            matrices.pushMatrix();
            matrices.translate(tag.sx(), tag.sy());
            matrices.scale(TAG_SCALE, TAG_SCALE);

            module.renderTag(context, tag.player());

            matrices.popMatrix();
        }
    }

    private void renderTag(DrawContext context, PlayerEntity player) {
        // --- Build content ---
        Text nameText    = showName.getValue()   ? buildNameText(player) : null;
        HealthInfo hInfo = showHealth.getValue() ? buildHealthInfo(player) : null;
        List<ItemStack> items = showArmor.getValue() ? buildItemList(player) : List.of();

        int nameWidth   = nameText != null ? mc.textRenderer.getWidth(nameText) : 0;
        int healthWidth = hInfo != null ? hInfo.totalWidth() : 0;
        int itemsWidth  = items.isEmpty() ? 0 : items.size() * ITEM_SIZE + (items.size() - 1) * ITEM_GAP;

        int contentWidth  = Math.max(nameWidth, Math.max(healthWidth, items.isEmpty() ? 0 : itemsWidth + 4));
        int halfW = contentWidth / 2 + 6; // Nice 6px horizontal padding

        int padding = 6;
        int rowSpacing = 4;

        // Calculate total height
        int totalHeight = padding * 2;
        int activeRowsCount = 0;
        if (!items.isEmpty()) {
            totalHeight += (ITEM_SIZE + 4);
            activeRowsCount++;
        }
        if (hInfo != null) {
            totalHeight += HEART_SIZE;
            activeRowsCount++;
        }
        if (nameText != null) {
            totalHeight += mc.textRenderer.fontHeight;
            activeRowsCount++;
        }
        if (activeRowsCount > 1) {
            totalHeight += (activeRowsCount - 1) * rowSpacing;
        }

        int top = -totalHeight;

        // --- Background ---
        java.awt.Color accent = ZenyaPlus.getAccentColor();
        int accentARGB = (180 << 24) | (accent.getRed() << 16) | (accent.getGreen() << 8) | accent.getBlue();

        RenderUtil.drawRoundedRect(context, -halfW, top, halfW * 2, totalHeight, 4.0f, 0xB00B0D11, false);
        RenderUtil.drawOutline(context, -halfW, top, halfW * 2, totalHeight, 4.0f, 1.0f, accentARGB, false);

        // --- Render rows top to bottom ---
        int currentY = top + padding;

        // Items row
        if (!items.isEmpty()) {
            int startX = -(itemsWidth / 2);
            int iy = currentY + 2;
            for (int i = 0; i < items.size(); i++) {
                int ix = startX + i * (ITEM_SIZE + ITEM_GAP);
                // Glassy slot bg
                RenderUtil.drawRoundedRect(context, ix - 2, currentY, ITEM_SIZE + 4, ITEM_SIZE + 4, 3.0f, 0x800B0D11, false);
                RenderUtil.drawOutline(context, ix - 2, currentY, ITEM_SIZE + 4, ITEM_SIZE + 4, 3.0f, 0.75f, 0x40FFFFFF, false);
                context.drawItem(items.get(i), ix, iy);
                context.drawStackOverlay(mc.textRenderer, items.get(i), ix, iy, null);
            }
            currentY += (ITEM_SIZE + 4) + rowSpacing;
        }

        // Health row
        if (hInfo != null) {
            renderHearts(context, hInfo, currentY);
            currentY += HEART_SIZE + rowSpacing;
        }

        // Name row
        if (nameText != null) {
            context.drawText(mc.textRenderer, nameText, -(nameWidth / 2), currentY, 0xFFFFFFFF, false);
        }
    }

    private void renderHearts(DrawContext context, HealthInfo hInfo, int y) {
        int x = -(hInfo.totalWidth() / 2);

        // Containers first
        for (int i = 0; i < hInfo.baseHearts(); i++) {
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, HEART_CONTAINER,
                    x + i * HEART_GAP, y, HEART_SIZE, HEART_SIZE);
        }
        // Full hearts
        for (int i = 0; i < hInfo.fullHearts(); i++) {
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, HEART_FULL,
                    x + i * HEART_GAP, y, HEART_SIZE, HEART_SIZE);
        }
        // Half heart
        if (hInfo.halfHeart()) {
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, HEART_HALF,
                    x + hInfo.fullHearts() * HEART_GAP, y, HEART_SIZE, HEART_SIZE);
        }
        // Absorption hearts
        int absStart = x + hInfo.baseHearts() * HEART_GAP;
        int absIcons = hInfo.absFullHearts() + (hInfo.absHalfHeart() ? 1 : 0);
        for (int i = 0; i < absIcons; i++) {
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, HEART_CONTAINER,
                    absStart + i * HEART_GAP, y, HEART_SIZE, HEART_SIZE);
        }
        for (int i = 0; i < hInfo.absFullHearts(); i++) {
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, HEART_ABS_FULL,
                    absStart + i * HEART_GAP, y, HEART_SIZE, HEART_SIZE);
        }
        if (hInfo.absHalfHeart()) {
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, HEART_ABS_HALF,
                    absStart + hInfo.absFullHearts() * HEART_GAP, y, HEART_SIZE, HEART_SIZE);
        }
    }

    private Text buildNameText(PlayerEntity player) {
        MutableText text = Text.empty();
        String displayName = player.getName().getString();

        // Apply NameProtect
        if (NameProtect.instance != null && NameProtect.instance.isEnabled()
                && displayName.equals(mc.getSession().getUsername())) {
            displayName = NameProtect.instance.getFakeName();
        }

        boolean friend = Friends.isFriend(displayName);
        text.append(friend ? "§b" : "§f");
        text.append(displayName);

        if (showPing.getValue() && mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
            if (entry != null) {
                int latency = entry.getLatency();
                String pColor = latency < 75 ? "§a" : latency < 150 ? "§e" : "§c";
                text.append(" §7[" + pColor + latency + "ms§7]");
            }
        }

        return text;
    }

    private HealthInfo buildHealthInfo(PlayerEntity player) {
        float maxHp     = Math.max(1.0f, player.getMaxHealth());
        float curHp     = MathHelper.clamp(player.getHealth(), 0.0f, maxHp);
        float absorption = Math.max(0.0f, player.getAbsorptionAmount());

        int maxHearts       = Math.max(1, MathHelper.ceil(maxHp / 2.0f));
        int filledHalf      = MathHelper.clamp(Math.round(curHp), 0, maxHearts * 2);
        int fullHearts      = filledHalf / 2;
        boolean halfHeart   = (filledHalf & 1) != 0;

        int absHalf         = Math.max(0, Math.round(absorption));
        int absFullHearts   = absHalf / 2;
        boolean absHalfHeart= (absHalf & 1) != 0;

        int totalIcons  = maxHearts + absFullHearts + (absHalfHeart ? 1 : 0);
        int totalWidth  = (totalIcons - 1) * HEART_GAP + HEART_SIZE;

        return new HealthInfo(maxHearts, fullHearts, halfHeart, absFullHearts, absHalfHeart, totalWidth);
    }

    private List<ItemStack> buildItemList(PlayerEntity player) {
        List<ItemStack> out = new ArrayList<>();
        ItemStack main = player.getMainHandStack();
        if (!main.isEmpty()) out.add(main);
        // Armor: head, chest, legs, feet (reverse order for display top-to-bottom)
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack armor = player.getEquippedStack(slot);
            if (!armor.isEmpty()) out.add(armor);
        }
        ItemStack off = player.getOffHandStack();
        if (!off.isEmpty()) out.add(off);
        return out;
    }

    private static Vec3d getLerpedPos(PlayerEntity player, float tickDelta) {
        try {
            return player.getLerpedPos(tickDelta);
        } catch (Throwable ignored) {
            double x = MathHelper.lerp(tickDelta, player.lastRenderX, player.getX());
            double y = MathHelper.lerp(tickDelta, player.lastRenderY, player.getY());
            double z = MathHelper.lerp(tickDelta, player.lastRenderZ, player.getZ());
            return new Vec3d(x, y, z);
        }
    }

    private static boolean isMenuOpen() {
        return mc.currentScreen instanceof ClickGUI;
    }

    // --- Records ---

    private record TagEntry(PlayerEntity player, float sx, float sy, double distSq) {}

    private record HealthInfo(
            int baseHearts,
            int fullHearts,
            boolean halfHeart,
            int absFullHearts,
            boolean absHalfHeart,
            int totalWidth
    ) {}
}
