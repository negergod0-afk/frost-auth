package com.zenya.module.modules.client;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.ModuleManager;
import com.zenya.setting.Setting;
import com.zenya.utils.renderer.RenderUtil;
import com.zenya.gui.ClickGUI;
import com.zenya.utils.ZenyaFont;
import com.zenya.utils.TickRateUtil;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;

public final class Hud extends Module {

    private static Hud INSTANCE;

    private static final List<Module> ENABLED_BUFFER = new ArrayList<>(64);
    private static final List<NotificationEntry> NOTIFICATIONS = new ArrayList<>(8);

    private static int CARD_BG          = 0xF2000000;
    private static int PILL_BG          = 0xF2000000;
    private static final int TEXT_PRIMARY     = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY   = 0xFFCFCFCF;
    private static final float CARD_RADIUS    = 10f;

    public enum HudElement {
        WATERMARK    ("Frost+"),
        PING         ("Ping"),
        XYZ          ("XYZ"),
        TPS          ("TPS"),
        BPS          ("BPS"),
        MODULE_LIST  ("Module List"),
        NOTIFICATIONS("Notifications"),
        COMPASS      ("Compass"),
        TOTEM_COUNTER("Totem Counter"),
        KEYSTROKES   ("Keystrokes"),
        ARMOR_HUD    ("Armor HUD");

        public final String label;
        HudElement(String label) { this.label = label; }
    }

    private static final EnumMap<HudElement, int[]> positions = new EnumMap<>(HudElement.class);
    private static final EnumMap<HudElement, Boolean> dragging = new EnumMap<>(HudElement.class);
    private static int dragX, dragY;
    private static net.minecraft.client.gui.screen.Screen lastScreen;

    public static int[] getElementPos(HudElement el) {
        return positions.computeIfAbsent(el, k -> defaultPos(k));
    }

    public static void setElementPos(HudElement el, int x, int y) {
        positions.put(el, new int[]{x, y});
    }

    private static int[] defaultPos(HudElement el) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int W = mc != null ? mc.getWindow().getScaledWidth() : 800;
        int H = mc != null ? mc.getWindow().getScaledHeight() : 480;
        return switch (el) {
            case WATERMARK      -> new int[]{8, 8};
            case PING           -> new int[]{8, 30};
            case XYZ            -> new int[]{8, 52};
            case TPS            -> new int[]{8, 74};
            case BPS            -> new int[]{8, 96};
            case MODULE_LIST    -> new int[]{W - 8, 8};
            case NOTIFICATIONS  -> new int[]{W - 176, H - 56};
            case COMPASS        -> new int[]{W / 2 - 25, 30};
            case TOTEM_COUNTER  -> new int[]{W / 2 + 30, 30};
            case KEYSTROKES     -> new int[]{W / 2 - 60, H - 80};
            case ARMOR_HUD      -> new int[]{W / 2 - 50, H - 90};
        };
    }

    private final Setting<Boolean> watermark    = new Setting<>("Watermark",     true);
    private final Setting<Boolean> showPing     = new Setting<>("Ping Counter",  true);
    private final Setting<Boolean> showXyz      = new Setting<>("XYZ Coords",    true);
    private final Setting<Boolean> showTps      = new Setting<>("TPS",           true);
    private final Setting<Boolean> showBps      = new Setting<>("BPS",           true);
    private final Setting<Boolean> moduleList   = new Setting<>("Module List",   true);
    private final Setting<Boolean> notifications = new Setting<>("Notifications", true);
    private final Setting<Integer> notificationTime = new Setting<>("Notification Time", 2, 1, 5);
    private final Setting<Boolean> showCompass  = new Setting<>("Compass",       true);
    private final Setting<Boolean> compassPlayers = new Setting<>("Compass Players", true);
    private final Setting<Boolean> compassMobs    = new Setting<>("Compass Mobs",    true);
    private final Setting<Integer> compassSize    = new Setting<>("Compass Size",    35, 15, 100);
    private final Setting<Boolean> totemCounter = new Setting<>("Totem Counter", true);
    private final Setting<Boolean> keystrokes   = new Setting<>("Keystrokes",   true);
    private final Setting<Boolean> armorHud     = new Setting<>("Armor HUD",     true);

    public Hud() {
        super("Hud", Category.CLIENT);
        setDescription("Draws the on-screen overlay. Move elements by opening Chat.");
        addSetting(watermark);
        addSetting(showPing);
        addSetting(showXyz);
        addSetting(showTps);
        addSetting(showBps);
        addSetting(moduleList);
        addSetting(notifications);
        addSetting(notificationTime);
        addSetting(showCompass);
        addSetting(compassPlayers);
        addSetting(compassMobs);
        addSetting(compassSize);
        addSetting(totemCounter);
        addSetting(keystrokes);
        addSetting(armorHud);
        INSTANCE = this;
    }

    public static String moduleListLayout() {
        return "Top";
    }

    public static boolean showModuleList() { 
        return INSTANCE != null && INSTANCE.isEnabled() && INSTANCE.moduleList.getValue(); 
    }

    public static boolean hideVanillaPotionEffects() {
        return INSTANCE != null && INSTANCE.isEnabled();
    }

    public static void renderHud(DrawContext context) {
        if (INSTANCE == null || !INSTANCE.isEnabled()) return;
        final MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        if (mc.getDebugHud().shouldShowDebugHud()) return;

        // Update background colors from ZenyaPlus setting
        CARD_BG = ZenyaPlus.getBackgroundARGB();
        PILL_BG = ZenyaPlus.getBackgroundARGB();

        final TextRenderer tr = mc.textRenderer;
        final int accent = ZenyaPlus.getAccentARGB();
        boolean isEditing = mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen
                         || mc.currentScreen instanceof ClickGUI;

        if (INSTANCE.watermark.getValue()) {
            renderComponent(context, tr, HudElement.WATERMARK, "Frost", " +", accent, isEditing);
        }
        if (INSTANCE.showPing.getValue()) {
            renderComponent(context, tr, HudElement.PING, "Ping: ", getPing(mc) + "ms", accent, isEditing);
        }
        if (INSTANCE.showXyz.getValue()) {
            String coords = String.format("%.1f %.1f %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
            renderComponent(context, tr, HudElement.XYZ, "XYZ: ", coords, accent, isEditing);
        }
        if (INSTANCE.showTps.getValue()) {
            renderComponent(context, tr, HudElement.TPS, "TPS: ", String.format("%.1f", TickRateUtil.INSTANCE.getTPS()), accent, isEditing);
        }
        if (INSTANCE.showBps.getValue()) {
            double deltaX = mc.player.getX() - mc.player.lastRenderX;
            double deltaZ = mc.player.getZ() - mc.player.lastRenderZ;
            double speed = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ) * 20;
            renderComponent(context, tr, HudElement.BPS, "BPS: ", String.format("%.1f", speed), accent, isEditing);
        }
        if (INSTANCE.moduleList.getValue()) {
            drawModuleList(context, tr, isEditing);
        }
        if (INSTANCE.notifications.getValue()) {
            drawNotifications(context, tr, isEditing);
        }
        if (INSTANCE.showCompass.getValue()) {
            drawCompass(context, tr, isEditing);
        }
        if (INSTANCE.totemCounter.getValue()) {
            drawTotemCounter(context, tr, isEditing);
        }
        if (INSTANCE.keystrokes.getValue()) {
            drawKeystrokes(context, tr, isEditing);
        }
        if (INSTANCE.armorHud.getValue()) {
            drawArmorHud(context, tr, isEditing);
        }

        // Save when screen closes
        if (lastScreen != null && mc.currentScreen == null) {
            if (lastScreen instanceof net.minecraft.client.gui.screen.ChatScreen || lastScreen instanceof ClickGUI) {
                ModuleManager.INSTANCE.saveConfig();
            }
        }
        lastScreen = mc.currentScreen;
    }

    public static void pushModuleNotification(Module module, boolean enabled) {
        if (module == null) return;
        long now = System.currentTimeMillis();
        long lifetime = notificationLifetimeMs();
        String title = module.getDisplayName();
        NOTIFICATIONS.removeIf(entry -> entry.title.equals(title) || now - entry.createdAt > lifetime);
        NOTIFICATIONS.add(0, new NotificationEntry(title, enabled ? "has been enabled" : "has been disabled", moduleIcon(module), now));
        while (NOTIFICATIONS.size() > 4) {
            NOTIFICATIONS.remove(NOTIFICATIONS.size() - 1);
        }
    }

    private static long notificationLifetimeMs() {
        int seconds = INSTANCE == null ? 2 : INSTANCE.notificationTime.getValue();
        return Math.max(1, Math.min(5, seconds)) * 1000L;
    }

    private static int getPing(MinecraftClient mc) {
        try {
            if (mc.getNetworkHandler() != null) {
                var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
                if (entry != null) return entry.getLatency();
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private static void renderComponent(DrawContext context, TextRenderer tr, HudElement el, String label, String value, int accent, boolean isEditing) {
        int[] pos = getElementPos(el);
        int labelW = ZenyaFont.width(tr, label);
        int valueW = ZenyaFont.width(tr, value);
        int bw = labelW + valueW + 20;
        int bh = 18;

        if (isEditing) {
            handleDrag(el, bw, bh);
            RenderUtil.drawOutline(context, pos[0], pos[1], bw, bh, 6f, 1f, 0x884A9CFF, false);
        }

        // Apply rainbow color if enabled
        boolean rainbow = Themes.isRainbow();
        int rainbowColor = rainbow ? Themes.rainbowAt(el.ordinal(), 0.05f) : accent;
        int finalAccent = rainbow ? rainbowColor : accent;

        card(context, pos[0], pos[1], bw, bh, finalAccent);
        ZenyaFont.draw(context, tr, label, pos[0] + 10, pos[1] + 5, TEXT_PRIMARY, false);
        ZenyaFont.draw(context, tr, value, pos[0] + 10 + labelW, pos[1] + 5, finalAccent, false);
    }

    private static void handleDrag(HudElement el, int w, int h) {
        MinecraftClient mc = MinecraftClient.getInstance();
        double mx = mc.mouse.getX() * (double) mc.getWindow().getScaledWidth() / (double) mc.getWindow().getWidth();
        double my = mc.mouse.getY() * (double) mc.getWindow().getScaledHeight() / (double) mc.getWindow().getHeight();

        int[] pos = getElementPos(el);
        boolean hovered = mx >= pos[0] && mx <= pos[0] + w && my >= pos[1] && my <= pos[1] + h;

        if (org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            if (hovered && !isAnyDragging()) {
                dragging.put(el, true);
                dragX = (int) mx - pos[0];
                dragY = (int) my - pos[1];
            }
            if (dragging.getOrDefault(el, false)) {
                pos[0] = (int) mx - dragX;
                pos[1] = (int) my - dragY;
            }
        } else {
            if (dragging.getOrDefault(el, false)) {
                // Just dropped
                ModuleManager.INSTANCE.saveConfig();
            }
            dragging.put(el, false);
        }
    }

    private static boolean isAnyDragging() {
        for (boolean b : dragging.values()) if (b) return true;
        return false;
    }

    private static void drawModuleList(DrawContext context, TextRenderer tr, boolean isEditing) {
        int[] pos = getElementPos(HudElement.MODULE_LIST);
        ENABLED_BUFFER.clear();
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (m.isEnabled() && m.getCategory() != Category.CLIENT) ENABLED_BUFFER.add(m);
        }
        
        int rowH = 14, gap = 2;
        int accent = ZenyaPlus.getAccentARGB();
        boolean rainbow = Themes.isRainbow();

        MinecraftClient mc = MinecraftClient.getInstance();
        int W = mc != null ? mc.getWindow().getScaledWidth() : 800;
        boolean onRight = pos[0] > W / 2;

        if (ENABLED_BUFFER.isEmpty()) {
            if (isEditing) {
                int bw = 80, bh = 18;
                handleDrag(HudElement.MODULE_LIST, bw, bh);
                RenderUtil.drawOutline(context, pos[0] - (onRight ? bw : 0), pos[1], bw, bh, 6f, 1f, 0x884A9CFF, false);
                ZenyaFont.draw(context, tr, "[Module List]", pos[0] - (onRight ? bw : 0) + 4, pos[1] + 5, 0x88FFFFFF, false);
            }
            return;
        }

        ENABLED_BUFFER.sort(Comparator.comparingInt((Module m) -> ZenyaFont.width(tr, m.getName())).reversed());
        int widest = ZenyaFont.width(tr, ENABLED_BUFFER.get(0).getName()) + 12;
        int totalH = ENABLED_BUFFER.size() * rowH + (ENABLED_BUFFER.size() - 1) * gap;

        if (isEditing) {
            handleDrag(HudElement.MODULE_LIST, widest, totalH);
            RenderUtil.drawOutline(context, pos[0] - (onRight ? widest : 0), pos[1], widest, totalH, 6f, 1f, 0x884A9CFF, false);
        }

        int ey = pos[1];
        int index = 0;
        for (Module m : ENABLED_BUFFER) {
            String label = m.getName();
            int textW = ZenyaFont.width(tr, label);
            int bw = textW + 12;
            int bx = onRight ? pos[0] - bw : pos[0];
            int col = rainbow ? Themes.rainbowAt(index, 0.05f) : accent;

            // Sleek card row background with rounded corners
            int rowBg = 0xCF0B0E14;
            if (ZenyaPlus.blurBackgroundEnabled()) {
                RenderUtil.drawBlur(context, bx, ey, bw, rowH, 4f, 1.5f, false);
            }
            RenderUtil.drawRoundedRect(context, bx, ey, bw, rowH, 4f, rowBg, false);

            if (onRight) {
                RenderUtil.drawRoundedRect(context, pos[0] - 2, ey, 2, rowH, 1f, 0f, 1f, 0f, false, col);
                ZenyaFont.draw(context, tr, label, bx + 4, ey + (rowH - tr.fontHeight) / 2 + 1, col, false);
            } else {
                RenderUtil.drawRoundedRect(context, pos[0], ey, 2, rowH, 1f, 0f, 1f, 0f, false, col);
                ZenyaFont.draw(context, tr, label, bx + 6, ey + (rowH - tr.fontHeight) / 2 + 1, col, false);
            }

            ey += rowH + gap;
            index++;
        }
    }

    private static void drawNotifications(DrawContext context, TextRenderer tr, boolean isEditing) {
        int[] pos = getElementPos(HudElement.NOTIFICATIONS);
        long now = System.currentTimeMillis();
        long lifetime = notificationLifetimeMs();
        NOTIFICATIONS.removeIf(entry -> now - entry.createdAt > lifetime);

        int width = 164;
        int rowH = 36;
        int gap = 5;
        int height = Math.max(rowH, NOTIFICATIONS.size() * rowH + Math.max(0, NOTIFICATIONS.size() - 1) * gap);

        if (isEditing) {
            handleDrag(HudElement.NOTIFICATIONS, width, height);
            RenderUtil.drawOutline(context, pos[0], pos[1], width, height, 6f, 1f, 0x884A9CFF, false);
            if (NOTIFICATIONS.isEmpty()) {
                card(context, pos[0], pos[1], width, rowH);
                ZenyaFont.draw(context, tr, "[Notifications]", pos[0] + 12, pos[1] + 15, 0x88FFFFFF, false);
                return;
            }
        }

        if (NOTIFICATIONS.isEmpty()) {
            return;
        }

        boolean rainbow = Themes.isRainbow();
        int y = pos[1];
        int index = 0;
        for (NotificationEntry entry : NOTIFICATIONS) {
            long age = now - entry.createdAt;
            float in = clamp01(age / 180.0f);
            float out = clamp01((lifetime - age) / 220.0f);
            float alpha = easeOutCubic(Math.min(in, out));
            int slide = Math.round((1.0f - alpha) * 14.0f);
            int x = pos[0] + slide;

            int bg = multiplyAlpha(CARD_BG, alpha);
            int accentColor = rainbow ? Themes.rainbowAt(index, 0.05f) : ZenyaPlus.getAccentARGB();
            int primary = multiplyAlpha(TEXT_PRIMARY, alpha);
            int secondary = multiplyAlpha(TEXT_SECONDARY, alpha);
            int accent = multiplyAlpha(accentColor, alpha);

            RenderUtil.drawRoundedRect(context, x, y, width, rowH, CARD_RADIUS, bg, false);
            
            RenderUtil.drawRoundedRect(context, x + 8, y + 8, 20, 20, 6f, multiplyAlpha(0x1FFFFFFF, alpha), false);
            context.drawItem(new ItemStack(entry.icon), x + 10, y + 10);

            ZenyaFont.draw(context, tr, entry.title, x + 36, y + 7, primary, false);
            ZenyaFont.draw(context, tr, entry.message, x + 36, y + 20, entry.message.endsWith("enabled") ? accent : secondary, false);
            y += rowH + gap;
            index++;
        }
    }

    private static net.minecraft.item.Item moduleIcon(Module module) {
        String name = module.getName().toLowerCase(java.util.Locale.ROOT).replace(" ", "");
        return switch (name) {
            case "freelook" -> Items.ENDER_EYE;
            case "freecam", "cameratweaks" -> Items.SPYGLASS;
            case "storageesp", "stashnotifier" -> Items.CHEST;
            case "blockesp", "lightesp" -> Items.GLOWSTONE_DUST;
            case "playeresp", "nametags" -> Items.PLAYER_HEAD;
            case "voidesp" -> Items.OBSIDIAN;
            case "fullbright" -> Items.TORCH;
            case "hud" -> Items.MAP;
            case "configmanager" -> Items.WRITABLE_BOOK;
            case "themes", "themechanger", "frost+", "zenya+" -> Items.NETHER_STAR;
            case "autototem", "hovertotem", "autoinvtotem" -> Items.TOTEM_OF_UNDYING;
            case "triggerbot", "aimassist" -> Items.CROSSBOW;
            case "autocrystal", "autohitcrystal", "crystaloptimizer" -> Items.END_CRYSTAL;
            case "anchormacro", "safeanchor", "doubleanchor" -> Items.RESPAWN_ANCHOR;
            case "elytraswap" -> Items.ELYTRA;
            case "shieldbreaker" -> Items.SHIELD;
            case "automace", "maceswap" -> Items.MACE;
            case "swingspeed" -> Items.DIAMOND_SWORD;
            case "automine" -> Items.DIAMOND_PICKAXE;
            case "fastbridge" -> Items.BRICKS;
            case "tridentfly" -> Items.TRIDENT;
            case "autofireworks" -> Items.FIREWORK_ROCKET;
            case "autotool" -> Items.IRON_PICKAXE;
            case "autolog" -> Items.OAK_DOOR;
            case "sprint" -> Items.LEATHER_BOOTS;
            case "weathernotifier" -> Items.WATER_BUCKET;
            case "spotifyhud" -> Items.MUSIC_DISC_13;
            case "spawnertags" -> Items.IRON_BARS;
            case "playerchunkfinder", "baseBlocksdetection", "chunkfinder", "chunkreload", "deltasensor" -> Items.COMPASS;
            case "fakestats" -> Items.NAME_TAG;
            case "fakepay" -> Items.EMERALD;
            case "regionmap" -> Items.FILLED_MAP;
            case "autorelog" -> Items.CLOCK;
            case "antitrap" -> Items.IRON_BARS;
            case "spawnerfinder" -> Items.ECHO_SHARD;
            case "amethystesp" -> Items.AMETHYST_SHARD;
            default -> switch (module.getCategory()) {
                case COMBAT -> Items.DIAMOND_SWORD;
                case RENDER -> Items.SPYGLASS;
                case MISC -> Items.COMPASS;
                case DONUT -> Items.AMETHYST_SHARD;
                case SMPS -> Items.CHEST;
                case CLIENT -> Items.NETHER_STAR;
            };
        };
    }

    private static void card(DrawContext ctx, int x, int y, int w, int h) {
        card(ctx, x, y, w, h, ZenyaPlus.getAccentARGB());
    }

    private static void card(DrawContext ctx, int x, int y, int w, int h, int accent) {
        float radius = Math.min(CARD_RADIUS, h * 0.5f);
        if (ZenyaPlus.blurBackgroundEnabled()) {
            RenderUtil.drawBlur(ctx, x, y, w, h, radius, 3.0f, false);
        }
        RenderUtil.drawRoundedRect(ctx, x, y, w, h, radius, CARD_BG, false);
    }

    private static void drawCompass(DrawContext context, TextRenderer tr, boolean isEditing) {
        int[] pos = getElementPos(HudElement.COMPASS);
        int radius = INSTANCE.compassSize.getValue();
        int d = radius * 2;
        int cx = pos[0] + radius;
        int cy = pos[1] + radius;

        if (isEditing) {
            handleDrag(HudElement.COMPASS, d, d);
            RenderUtil.drawOutline(context, pos[0], pos[1], d, d, 6f, 1f, 0x884A9CFF, false);
        }

        boolean rainbow = Themes.isRainbow();
        int accent = rainbow ? Themes.rainbowAt(0, 0.05f) : ZenyaPlus.getAccentARGB();
        
        if (ZenyaPlus.blurBackgroundEnabled()) {
            RenderUtil.drawBlur(context, pos[0], pos[1], d, d, radius, 3.0f, false);
        }
        RenderUtil.drawRoundedRect(context, pos[0], pos[1], d, d, radius, CARD_BG, false);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        float yaw = mc.player.getYaw();
        
        // Entities (Players & Mobs)
        if ((INSTANCE.compassPlayers.getValue() || INSTANCE.compassMobs.getValue()) && mc.world != null) {
            for (net.minecraft.entity.Entity entity : mc.world.getEntities()) {
                if (entity == mc.player) continue;
                boolean isPlayer = entity instanceof net.minecraft.entity.player.PlayerEntity;
                boolean isMob = entity instanceof net.minecraft.entity.mob.MobEntity;
                if ((isPlayer && INSTANCE.compassPlayers.getValue()) || (isMob && INSTANCE.compassMobs.getValue())) {
                    double dx = entity.getX() - mc.player.getX();
                    double dz = entity.getZ() - mc.player.getZ();
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist > 80) continue; // radar max range
                    
                    double entityAngle = Math.toDegrees(Math.atan2(dz, dx)) - 90;
                    double rad = Math.toRadians(entityAngle - yaw);
                    
                    double scale = (dist / 80.0) * (radius - 6);
                    float ex = (float)(cx + Math.sin(rad) * scale);
                    float ey = (float)(cy - Math.cos(rad) * scale);
                    
                    int dotColor = isPlayer ? 0xFFFF4444 : 0xFF44FF44;
                    RenderUtil.drawRoundedRect(context, ex - 2f, ey - 2f, 4, 4, 2f, dotColor, false);
                }
            }
        }
        
        float[] angles = {0, 90, 180, -90};
        String[] labels = {"S", "W", "N", "E"};

        for (int i = 0; i < 4; i++) {
            float a = angles[i] - yaw;
            double rad = Math.toRadians(a);
            
            int innerR = radius - 8;
            String label = labels[i];
            int tw = ZenyaFont.width(tr, label);
            int th = tr.fontHeight;
            
            int color = (i == 2) ? accent : TEXT_PRIMARY;
            if (rainbow && i != 2) {
                color = Themes.rainbowAt(i + 1, 0.05f);
            }
            
            float tx = (float)(cx + Math.sin(rad) * innerR) - tw / 2f;
            float ty = (float)(cy - Math.cos(rad) * innerR) - th / 2f;
            
            ZenyaFont.draw(context, tr, label, (int)tx, (int)ty, color, false);
        }

        RenderUtil.drawRoundedRect(context, cx - 2f, cy - 2f, 4, 4, 2f, accent, false);
    }
    
    private static void drawTotemCounter(DrawContext context, TextRenderer tr, boolean isEditing) {
        int[] pos = getElementPos(HudElement.TOTEM_COUNTER);
        
        MinecraftClient mc = MinecraftClient.getInstance();
        int totems = 0;
        if (mc.player != null) {
            for (int i = 0; i < mc.player.getInventory().size(); i++) {
                if (mc.player.getInventory().getStack(i).getItem() == net.minecraft.item.Items.TOTEM_OF_UNDYING) {
                    totems += mc.player.getInventory().getStack(i).getCount();
                }
            }
            if (mc.player.getOffHandStack().getItem() == net.minecraft.item.Items.TOTEM_OF_UNDYING) {
                totems += mc.player.getOffHandStack().getCount();
            }
        }
        
        String text = "Totems: " + totems;
        int textW = ZenyaFont.width(tr, text);
        int totemSize = 16;
        int pad = 10;
        int w = pad + totemSize + 6 + textW + pad;
        int h = 24;
        
        if (isEditing) {
            handleDrag(HudElement.TOTEM_COUNTER, w, h);
            RenderUtil.drawOutline(context, pos[0], pos[1], w, h, 6f, 1f, 0x884A9CFF, false);
        }
        
        boolean rainbow = Themes.isRainbow();
        int accent = rainbow ? Themes.rainbowAt(0, 0.05f) : ZenyaPlus.getAccentARGB();
        
        card(context, pos[0], pos[1], w, h, accent);
        
        context.drawItem(new ItemStack(net.minecraft.item.Items.TOTEM_OF_UNDYING), pos[0] + pad, pos[1] + (h - totemSize) / 2);
        ZenyaFont.draw(context, tr, text, pos[0] + pad + totemSize + 6, pos[1] + (h - tr.fontHeight) / 2 + 1, rainbow ? accent : TEXT_PRIMARY, false);
    }

    private static void drawKeystrokes(DrawContext context, TextRenderer tr, boolean isEditing) {
        int[] pos = getElementPos(HudElement.KEYSTROKES);
        MinecraftClient mc = MinecraftClient.getInstance();
        
        // Key dimensions
        int keyW = 24;
        int keyH = 24;
        int gap = 4;
        int spaceW = keyW * 3 + gap * 2;
        int spaceH = keyH;
        
        // Calculate total dimensions
        int totalW = keyW * 3 + gap * 2; // WASD width
        int totalH = keyH * 2 + gap + spaceH + gap + 20; // WASD + space + click counters
        
        if (isEditing) {
            handleDrag(HudElement.KEYSTROKES, totalW, totalH);
            RenderUtil.drawOutline(context, pos[0], pos[1], totalW, totalH, 6f, 1f, 0x884A9CFF, false);
        }
        
        boolean rainbow = Themes.isRainbow();
        int accent = rainbow ? Themes.rainbowAt(0, 0.05f) : ZenyaPlus.getAccentARGB();
        
        // Get key states using Minecraft's Input system
        boolean wPressed = mc.options.forwardKey.isPressed();
        boolean aPressed = mc.options.leftKey.isPressed();
        boolean sPressed = mc.options.backKey.isPressed();
        boolean dPressed = mc.options.rightKey.isPressed();
        boolean spacePressed = mc.options.jumpKey.isPressed();
        
        boolean lmbPressed = mc.options.attackKey.isPressed();
        boolean rmbPressed = mc.options.useKey.isPressed();
        
        // Draw WASD keys
        int startY = pos[1];
        
        // W key (top center)
        int wX = pos[0] + keyW + gap;
        int wY = startY;
        drawKey(context, tr, "W", wX, wY, keyW, keyH, wPressed, accent);
        
        // A key (middle left)
        int aX = pos[0];
        int aY = startY + keyH + gap;
        drawKey(context, tr, "A", aX, aY, keyW, keyH, aPressed, accent);
        
        // S key (middle center)
        int sX = pos[0] + keyW + gap;
        int sY = startY + keyH + gap;
        drawKey(context, tr, "S", sX, sY, keyW, keyH, sPressed, accent);
        
        // D key (middle right)
        int dX = pos[0] + keyW * 2 + gap * 2;
        int dY = startY + keyH + gap;
        drawKey(context, tr, "D", dX, dY, keyW, keyH, dPressed, accent);
        
        // Space bar (bottom)
        int spaceX = pos[0];
        int spaceY = startY + keyH * 2 + gap * 2;
        drawKey(context, tr, "SPACE", spaceX, spaceY, spaceW, spaceH, spacePressed, accent);
        
        // CPS counters
        int clickY = spaceY + spaceH + gap;
        int clickW = (totalW - gap) / 2;
        
        // LMB CPS counter
        int lmbCPS = getLmbCPS();
        String lmbText = "LMB: " + lmbCPS;
        drawClickCounter(context, tr, lmbText, pos[0], clickY, clickW, 16, lmbPressed, accent);
        
        // RMB CPS counter
        int rmbCPS = getRmbCPS();
        String rmbText = "RMB: " + rmbCPS;
        drawClickCounter(context, tr, rmbText, pos[0] + clickW + gap, clickY, clickW, 16, rmbPressed, accent);
    }
    
    private static void drawKey(DrawContext context, TextRenderer tr, String label, int x, int y, int w, int h, boolean pressed, int accent) {
        float radius = 6f;
        int bgColor = pressed ? accent : CARD_BG;
        int textColor = pressed ? 0xFFFFFFFF : TEXT_PRIMARY;
        
        RenderUtil.drawRoundedRect(context, x, y, w, h, radius, bgColor, false);
        
        int textW = ZenyaFont.width(tr, label);
        ZenyaFont.draw(context, tr, label, x + (w - textW) / 2, y + (h - tr.fontHeight) / 2 + 1, textColor, false);
    }
    
    private static void drawClickCounter(DrawContext context, TextRenderer tr, String label, int x, int y, int w, int h, boolean pressed, int accent) {
        float radius = 6f;
        int bgColor = pressed ? accent : CARD_BG;
        int textColor = pressed ? 0xFFFFFFFF : TEXT_PRIMARY;
        
        RenderUtil.drawRoundedRect(context, x, y, w, h, radius, bgColor, false);
        
        int textW = ZenyaFont.width(tr, label);
        ZenyaFont.draw(context, tr, label, x + (w - textW) / 2, y + (h - tr.fontHeight) / 2 + 1, textColor, false);
    }
    
    // CPS Tracking
    private static final List<Long> lmbClickTimes = new ArrayList<>();
    private static final List<Long> rmbClickTimes = new ArrayList<>();
    private static boolean lmbWasPressed = false;
    private static boolean rmbWasPressed = false;
    
    private static int getLmbCPS() {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean nowPressed = mc.options.attackKey.isPressed();
        long now = System.currentTimeMillis();
        
        // Register new click
        if (nowPressed && !lmbWasPressed) {
            lmbClickTimes.add(now);
        }
        lmbWasPressed = nowPressed;
        
        // Remove clicks older than 1 second
        lmbClickTimes.removeIf(time -> now - time > 1000);
        
        // Calculate CPS (capped at 20)
        int cps = lmbClickTimes.size();
        return Math.min(cps, 20);
    }
    
    private static int getRmbCPS() {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean nowPressed = mc.options.useKey.isPressed();
        long now = System.currentTimeMillis();
        
        // Register new click
        if (nowPressed && !rmbWasPressed) {
            rmbClickTimes.add(now);
        }
        rmbWasPressed = nowPressed;
        
        // Remove clicks older than 1 second
        rmbClickTimes.removeIf(time -> now - time > 1000);
        
        // Calculate CPS (capped at 20)
        int cps = rmbClickTimes.size();
        return Math.min(cps, 20);
    }

    private static void drawArmorHud(DrawContext context, TextRenderer tr, boolean isEditing) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        
        int[] pos = getElementPos(HudElement.ARMOR_HUD);
        
        // Get armor pieces
        ItemStack helmet = mc.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD);
        ItemStack chestplate = mc.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST);
        ItemStack leggings = mc.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS);
        ItemStack boots = mc.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET);
        
        // Constants
        int ITEM_SIZE = 16;
        int ITEM_SPACING = 4;
        int PADDING = 8;
        int DURABILITY_BAR_HEIGHT = 3;
        int DURABILITY_BAR_WIDTH = ITEM_SIZE;
        int DURABILITY_BAR_SPACING = 2;
        int DURABILITY_BG_COLOR = 0xFF333333;
        
        // Calculate dimensions
        int totalWidth = PADDING * 2 + ITEM_SIZE * 4 + ITEM_SPACING * 3;
        int totalHeight = PADDING * 2 + ITEM_SIZE + DURABILITY_BAR_SPACING + DURABILITY_BAR_HEIGHT;
        
        // Check if player has any armor for placeholder
        boolean hasArmor = !helmet.isEmpty() || !chestplate.isEmpty() || !leggings.isEmpty() || !boots.isEmpty();
        
        if (isEditing) {
            handleDrag(HudElement.ARMOR_HUD, totalWidth, totalHeight);
            RenderUtil.drawOutline(context, pos[0], pos[1], totalWidth, totalHeight, 6f, 1f, 0x884A9CFF, false);
            if (!hasArmor) {
                card(context, pos[0], pos[1], totalWidth, totalHeight);
                ZenyaFont.draw(context, tr, "[Armor HUD]", pos[0] + 18, pos[1] + 15, 0x88FFFFFF, false);
                return;
            }
        }
        
        if (!hasArmor && !isEditing) {
            return;
        }
        
        boolean rainbow = Themes.isRainbow();
        int accent = rainbow ? Themes.rainbowAt(0, 0.05f) : ZenyaPlus.getAccentARGB();
        
        // Draw background card
        card(context, pos[0], pos[1], totalWidth, totalHeight, accent);
        
        // Render armor pieces from left to right: boots, leggings, chestplate, helmet
        ItemStack[] armorPieces = {boots, leggings, chestplate, helmet};
        
        for (int i = 0; i < armorPieces.length; i++) {
            ItemStack armor = armorPieces[i];
            int itemX = pos[0] + PADDING + i * (ITEM_SIZE + ITEM_SPACING);
            int itemY = pos[1] + PADDING;
            
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
    
    private static int getDurabilityColor(float percent) {
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

    private static int multiplyAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * clamp01(alpha));
        return (a << 24) | (color & 0xFFFFFF);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float easeOutCubic(float value) {
        float t = clamp01(value);
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    private record NotificationEntry(String title, String message, net.minecraft.item.Item icon, long createdAt) {}
}
