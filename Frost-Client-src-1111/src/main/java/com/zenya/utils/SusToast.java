package com.zenya.utils;

import com.zenya.module.modules.client.ZenyaPlus;
import com.zenya.utils.renderer.RenderUtil;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public final class SusToast implements Toast {
    private static final int WIDTH = 160;
    private static final int HEIGHT = 32;
    private static final long DURATION_MS = 5000L;

    private final Text title;
    private final Text description;
    private final ItemStack icon;

    private long startTime;
    private Visibility visibility = Visibility.SHOW;

    public SusToast(Text title, Text description, ItemStack icon) {
        this.title = title;
        this.description = description;
        this.icon = icon == null ? ItemStack.EMPTY : icon;
    }

    @Override
    public void draw(DrawContext context, TextRenderer textRenderer, long startTime) {
        if (this.startTime == 0L) {
            this.startTime = startTime;
        }

        int accent = ZenyaPlus.getAccentARGB();
        int bg = 0xF0121418;
        int border = (accent & 0x00FFFFFF) | 0xAA000000;

        RenderUtil.drawRoundedRect(context, 0, 0, WIDTH, HEIGHT, 6f, bg, false);
        RenderUtil.drawOutline(context, 0, 0, WIDTH, HEIGHT, 6f, 1f, border, false);
        context.fill(0, 0, WIDTH, 2, accent);

        if (!icon.isEmpty()) {
            context.drawItem(icon, 6, (HEIGHT - 16) / 2);
        }

        int textX = icon.isEmpty() ? 8 : 28;
        context.drawTextWithShadow(textRenderer, title, textX, 7, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, description, textX, 18, 0xFF9AA3B2);
    }

    @Override
    public Visibility getVisibility() {
        return visibility;
    }

    @Override
    public void update(ToastManager manager, long time) {
        if (startTime == 0L) {
            startTime = time;
        }
        if (time - startTime >= DURATION_MS) {
            visibility = Visibility.HIDE;
        }
    }
}
