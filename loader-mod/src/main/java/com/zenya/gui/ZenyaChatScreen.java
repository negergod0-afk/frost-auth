package com.zenya.gui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;

public class ZenyaChatScreen extends ChatScreen {

    public ZenyaChatScreen(String originalChatText) {
        super(originalChatText, false);
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        // Allow dragging HUD elements - return false to let the chat screen process the click
        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Allow scrolling through chat while HUD elements are visible
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
    }
}
