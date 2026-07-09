package com.zenya.mixin;

import com.zenya.gui.FrostOptionsScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects a "❄ Frost Client ▶" button into the vanilla Minecraft Options screen.
 * Clicking it opens {@link FrostOptionsScreen} where the GUI keybind can be changed.
 */
@Mixin(GameOptionsScreen.class)
public abstract class GameOptionsScreenMixin {

    @Shadow protected abstract <T extends Element & Drawable & Selectable> T addDrawableChild(T drawableElement);

    @Shadow public int height;

    @Inject(method = "init", at = @At("TAIL"))
    private void zenya$addFrostButton(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Screen self = (Screen) (Object) this;

        ButtonWidget frostBtn = ButtonWidget.builder(
                Text.literal("§b❄ §fFrost Client §7▶"),
                btn -> mc.setScreen(new FrostOptionsScreen(self))
        )
                .dimensions(4, this.height - 26, 150, 20)
                .build();

        this.addDrawableChild(frostBtn);
    }
}
