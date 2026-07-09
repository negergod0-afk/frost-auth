package com.zenya.mixin;

import com.zenya.utils.NameProtectUtil;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(TextRenderer.class)
public class TextRendererMixin {

    @ModifyVariable(
            method = "prepare(Ljava/lang/String;FFIZI)Lnet/minecraft/client/font/TextRenderer$GlyphDrawable;",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private String zenya$replacePreparedString(String text) {
        return (NameProtectUtil.replace(text));
    }

    @ModifyVariable(
            method = "prepare(Lnet/minecraft/text/OrderedText;FFIZZI)Lnet/minecraft/client/font/TextRenderer$GlyphDrawable;",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private OrderedText zenya$replacePreparedOrderedText(OrderedText orderedText) {
        return (NameProtectUtil.replace(orderedText));
    }

    @ModifyVariable(
            method = "drawWithOutline(Lnet/minecraft/text/OrderedText;FFIILorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private OrderedText zenya$replaceOutlinedOrderedText(OrderedText orderedText) {
        return (NameProtectUtil.replace(orderedText));
    }

    @ModifyVariable(
            method = "getWidth(Ljava/lang/String;)I",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private String zenya$replaceWidthString(String text) {
        return (NameProtectUtil.replace(text));
    }

    @ModifyVariable(
            method = "getWidth(Lnet/minecraft/text/StringVisitable;)I",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private StringVisitable zenya$replaceWidthVisitable(StringVisitable visitable) {
        return (NameProtectUtil.replace(visitable));
    }

    @ModifyVariable(
            method = "getWidth(Lnet/minecraft/text/OrderedText;)I",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private OrderedText zenya$replaceWidthOrderedText(OrderedText orderedText) {
        return (NameProtectUtil.replace(orderedText));
    }
}
