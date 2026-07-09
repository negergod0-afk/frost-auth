package com.zenya.gui;

import com.zenya.module.modules.client.ZenyaPlus;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * "Frost Client" settings page reachable from the vanilla Minecraft Options screen.
 * Exposes a single keybind: the key used to open the Frost Client ClickGUI.
 */
public class FrostOptionsScreen extends Screen {

    private final Screen parent;

    /** True when we are waiting for the user to press a key to rebind. */
    private boolean awaitingBind = false;

    private ButtonWidget bindButton;

    public FrostOptionsScreen(Screen parent) {
        super(Text.literal("Frost Client Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;

        // ── Keybind row ──────────────────────────────────────────────────────────
        bindButton = ButtonWidget.builder(
                buildBindLabel(),
                btn -> {
                    awaitingBind = !awaitingBind;
                    updateBindLabel();
                })
                .dimensions(cx - 155, cy - 20, 310, 20)
                .build();
        addDrawableChild(bindButton);

        // ── Done button ──────────────────────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.done"),
                btn -> {
                    awaitingBind = false;
                    client.setScreen(parent);
                })
                .dimensions(cx - 100, cy + 30, 200, 20)
                .build());
    }

    // ── Rendering ────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Dark blurred-style background
        context.fillGradient(0, 0, width, height, 0xC0101010, 0xD0060606);

        int cx = width / 2;
        int cy = height / 2;

        // Panel background
        context.fill(cx - 170, cy - 44, cx + 170, cy + 58, 0xAA151515);
        context.fill(cx - 170, cy - 44, cx + 170, cy - 43, 0xFF3B82F6); // accent top border

        // Title
        context.drawCenteredTextWithShadow(textRenderer, title, cx, cy - 60, 0xFFFFFFFF);

        // Section label above the button
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("§7Frost Client GUI Keybind"),
                cx,
                cy - 38,
                0xFFAAAAAA
        );

        super.render(context, mouseX, mouseY, delta);

        // Hint text shown while waiting for a key
        if (awaitingBind) {
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.literal("§ePress any key to bind  •  §cESC to clear"),
                    cx, cy + 6, 0xFFFFFFFF
            );
        }
    }

    // ── Input handling ───────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(KeyInput input) {
        if (awaitingBind) {
            int keyCode = input.key();
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                ZenyaPlus.setMenuBind(0);
            } else {
                ZenyaPlus.setMenuBind(keyCode);
            }
            awaitingBind = false;
            updateBindLabel();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (awaitingBind) {
            // Cancel on any click outside the bind button
            awaitingBind = false;
            updateBindLabel();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private void updateBindLabel() {
        if (bindButton != null) {
            bindButton.setMessage(buildBindLabel());
        }
    }

    private Text buildBindLabel() {
        if (awaitingBind) {
            return Text.literal("§e> Press a key... <");
        }
        int bind = ZenyaPlus.getMenuBind();
        String keyName;
        if (bind == 0) {
            keyName = "§8[unbound]";
        } else {
            String raw = GLFW.glfwGetKeyName(bind, 0);
            if (raw == null || raw.isBlank()) {
                keyName = friendlyKeyName(bind);
            } else {
                keyName = raw.toUpperCase();
            }
        }
        return Text.literal("§fFrost GUI Bind: §b" + keyName);
    }

    /** Human-readable names for GLFW keys that have no printable character. */
    private static String friendlyKeyName(int key) {
        return switch (key) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT   -> "Right Shift";
            case GLFW.GLFW_KEY_LEFT_SHIFT    -> "Left Shift";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "Right Ctrl";
            case GLFW.GLFW_KEY_LEFT_CONTROL  -> "Left Ctrl";
            case GLFW.GLFW_KEY_RIGHT_ALT     -> "Right Alt";
            case GLFW.GLFW_KEY_LEFT_ALT      -> "Left Alt";
            case GLFW.GLFW_KEY_F1            -> "F1";
            case GLFW.GLFW_KEY_F2            -> "F2";
            case GLFW.GLFW_KEY_F3            -> "F3";
            case GLFW.GLFW_KEY_F4            -> "F4";
            case GLFW.GLFW_KEY_F5            -> "F5";
            case GLFW.GLFW_KEY_F6            -> "F6";
            case GLFW.GLFW_KEY_F7            -> "F7";
            case GLFW.GLFW_KEY_F8            -> "F8";
            case GLFW.GLFW_KEY_F9            -> "F9";
            case GLFW.GLFW_KEY_F10           -> "F10";
            case GLFW.GLFW_KEY_F11           -> "F11";
            case GLFW.GLFW_KEY_F12           -> "F12";
            case GLFW.GLFW_KEY_INSERT        -> "Insert";
            case GLFW.GLFW_KEY_DELETE        -> "Delete";
            case GLFW.GLFW_KEY_HOME          -> "Home";
            case GLFW.GLFW_KEY_END           -> "End";
            case GLFW.GLFW_KEY_PAGE_UP       -> "Page Up";
            case GLFW.GLFW_KEY_PAGE_DOWN     -> "Page Down";
            case GLFW.GLFW_KEY_UP            -> "Up Arrow";
            case GLFW.GLFW_KEY_DOWN          -> "Down Arrow";
            case GLFW.GLFW_KEY_LEFT          -> "Left Arrow";
            case GLFW.GLFW_KEY_RIGHT         -> "Right Arrow";
            case GLFW.GLFW_KEY_BACKSPACE     -> "Backspace";
            case GLFW.GLFW_KEY_TAB           -> "Tab";
            case GLFW.GLFW_KEY_CAPS_LOCK     -> "Caps Lock";
            case GLFW.GLFW_KEY_ENTER         -> "Enter";
            case GLFW.GLFW_KEY_KP_ENTER      -> "Numpad Enter";
            default -> "Key " + key;
        };
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
