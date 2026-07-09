package com.zenya.module.modules.misc;

import com.zenya.module.Category;
import com.zenya.module.Module;

/**
 * Automatically holds the sprint key. Also disables the vanilla "Sprint Toggle" option while enabled
 * (via reflection for mapping/version compatibility).
 */
public final class Sprint extends Module {

    private boolean hadSprintToggled = false;
    private Object sprintToggleOption = null;
    private java.lang.reflect.Method sprintToggleGetValue = null;
    private java.lang.reflect.Method sprintToggleSetValue = null;

    public Sprint() {
        super("Sprint", Category.MISC);
        setDescription("Keeps the sprint key permanently pressed and disables the vanilla sprint toggle option so you always run.");
    }

    @Override
    public void onEnable() {
        if (mc == null || mc.options == null) {
            return;
        }
        resolveSprintToggleOption();
        hadSprintToggled = getSprintToggledOption();
        setSprintToggledOption(false);
    }

    @Override
    public void onDisable() {
        if (mc == null || mc.options == null) {
            return;
        }
        setSprintToggledOption(hadSprintToggled);
        try {
            mc.options.sprintKey.setPressed(false);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onTick() {
        if (mc == null || mc.player == null || mc.options == null) {
            return;
        }

        // Ensure vanilla sprint-toggled doesn't fight us.
        setSprintToggledOption(false);
        try {
            mc.options.sprintKey.setPressed(true);
        } catch (Throwable ignored) {}
    }

    private boolean getSprintToggledOption() {
        try {
            resolveSprintToggleOption();
            if (sprintToggleOption == null || sprintToggleGetValue == null) return false;
            Object v = sprintToggleGetValue.invoke(sprintToggleOption);
            return v instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void setSprintToggledOption(boolean value) {
        try {
            resolveSprintToggleOption();
            if (sprintToggleOption == null || sprintToggleSetValue == null) return;
            sprintToggleSetValue.invoke(sprintToggleOption, Boolean.valueOf(value));
        } catch (Throwable ignored) {
            // Some versions/mappings may not expose this option; holding sprintKey still works.
        }
    }

    private void resolveSprintToggleOption() {
        if (sprintToggleOption != null || mc == null || mc.options == null) {
            return;
        }
        try {
            sprintToggleOption = mc.options.getClass().getMethod("getSprintToggled").invoke(mc.options);
            if (sprintToggleOption == null) {
                return;
            }
            sprintToggleGetValue = sprintToggleOption.getClass().getMethod("getValue");
            sprintToggleSetValue = sprintToggleOption.getClass().getMethod("setValue", Object.class);
        } catch (Throwable ignored) {
            sprintToggleOption = null;
            sprintToggleGetValue = null;
            sprintToggleSetValue = null;
        }
    }
}
