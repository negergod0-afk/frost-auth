package com.zenya.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListEntry.class)
public abstract class PlayerListEntryMixin {

    @Shadow
    public abstract GameProfile getProfile();

    @Inject(method = "getSkinTextures", at = @At("HEAD"), cancellable = true)
    private void zenya$overrideListEntrySkin(CallbackInfoReturnable<SkinTextures> cir) {
        GameProfile profile = getProfile();
        if (profile == null || profile.id() == null) {
            return;
        }

        SkinTextures override = com.zenya.module.modules.misc.SkinChanger.getOverrideSkin(profile.id());
        if (override != null) {
            cir.setReturnValue(override);
        }
    }
}
