package com.zenya.module;

import com.zenya.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    private final String name;
    private final Category category;
    private boolean enabled;
    private int bind = 0;
    private boolean expanded = false;
    public boolean wasBindPressed = false;
    private final List<Setting<?>> settings = new ArrayList<>();
    private String description = "";
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    public Module(String name, Category category) {
        this.name = name;
        this.category = category;
        this.enabled = false;
    }

    protected void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public String getDescription() {
        return description;
    }

    public void addSetting(Setting<?> setting) {
        settings.add(setting);
    }

    public List<Setting<?>> getSettings() {
        return settings;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        if (name == null || name.isBlank()) {
            return "";
        }
        return switch (name.toLowerCase(java.util.Locale.ROOT).replace(" ", "")) {
            case "zenya+", "frost+", "ҥλl0+", "halo+" -> "Frost+";
            case "hud" -> "HUD";
            case "autodoublehand" -> "Auto Double Hand";
            case "autohitcrystal" -> "Auto Hit Crystal";
            case "activitydebug" -> "Activity Debug";
            case "antitrap" -> "Anti Trap";
            case "bonedropper", "bonedropperbot" -> "Bone Dropper";
            case "chunkfinder" -> "Chunk Finder";
            case "fakestats" -> "Fake Stats";
            case "fakepay" -> "Fake Pay";
            case "basemarker" -> "Base Marker";

            case "autolog" -> "Auto Log";
            case "freelook" -> "Freelook";
            case "homesetter" -> "Home Setter";
            case "nameprotect" -> "Name Protect";
            case "nametags" -> "Nametags";
            case "skinchanger", "skinprotect" -> "Skin Protect";
            case "swingspeed" -> "Swing Speed";
            case "tabdetector" -> "Tab Detector";
            case "weathernotifier" -> "Weather Notifier";
            case "amethystesp" -> "Amethyst ESP";
            case "deltasensor" -> "Delta Sensor";
            case "spotifyhud" -> "Spotify HUD";
            case "fullbright" -> "Full Bright";
            case "jumpcircles" -> "Jump Circles";
            case "norender" -> "No Render";
            case "spearswap" -> "Spear Swap";
            default -> splitCamelCase(name);
        };
    }

    private static String splitCamelCase(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        char prev = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            char next = i + 1 < value.length() ? value.charAt(i + 1) : 0;
            if (i > 0 && c != ' ' && (
                    Character.isUpperCase(c) && Character.isLowerCase(prev)
                            || Character.isUpperCase(c) && Character.isUpperCase(prev) && Character.isLowerCase(next)
                            || Character.isDigit(c) && !Character.isDigit(prev) && prev != ' '
            )) {
                out.append(' ');
            }
            out.append(c);
            prev = c;
        }
        return out.toString();
    }

    public Category getCategory() {
        return category;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        if (enabled) {
            onEnable();
            com.zenya.sound.SoundManager.playModuleEnable();
            com.zenya.module.modules.client.Hud.pushModuleNotification(this, true);
        } else {
            onDisable();
            com.zenya.sound.SoundManager.playModuleDisable();
            com.zenya.module.modules.client.Hud.pushModuleNotification(this, false);
        }
        com.zenya.module.ModuleManager.INSTANCE.saveConfig();
    }


    public boolean isEnabled() {
        return enabled;
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    /**
     * Called when the user's bound key is pressed.
     * Default behavior matches the old system: toggle enabled state.
     */
    public void onBindPressed() {
        toggle();
    }

    public int getBind() {
        return bind;
    }

    public void setBind(int bind) {
        this.bind = bind;
        com.zenya.module.ModuleManager.INSTANCE.saveConfig();
    }

    void applyBind(int bind) {
        this.bind = bind;
    }

    void applyEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        if (enabled) {
            onEnable();
        } else {
            onDisable();
        }
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public void onEnable() {}
    public void onDisable() {}
    public void onTick() {}
    public void onRender(MatrixStack matrices, float tickDelta) {}
    public void onWorldChange() {}
    public void onPacketReceive(net.minecraft.network.packet.Packet<?> packet) {}
    public boolean onPacketSend(net.minecraft.network.packet.Packet<?> packet) { return false; }
}
