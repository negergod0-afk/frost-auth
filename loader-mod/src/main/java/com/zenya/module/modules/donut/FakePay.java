package com.zenya.module.modules.donut;

import com.zenya.module.Category;
import com.zenya.module.Module;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ChatCommandSignedC2SPacket;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class FakePay extends Module {

    public static FakePay INSTANCE;

    public FakePay() {
        super("FakePay", Category.DONUT);
        setDescription("Fakes paying money to other players when you run /pay.");
        INSTANCE = this;
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (!isEnabled()) {
            return false;
        }
        if (packet instanceof ChatCommandSignedC2SPacket cmd) {
            return intercept(cmd.command());
        }
        if (packet instanceof ChatMessageC2SPacket chat) {
            return intercept(chat.chatMessage());
        }
        return false;
    }

    public static boolean intercept(String rawInput) {
        if (INSTANCE == null || !INSTANCE.isEnabled()) {
            return false;
        }
        if (rawInput == null || rawInput.isBlank()) {
            return false;
        }

        String message = rawInput.trim();
        if (message.startsWith("/")) {
            message = message.substring(1);
        }

        String[] parts = message.split("\\s+");
        if (parts.length < 3 || !parts[0].equalsIgnoreCase("pay")) {
            return false;
        }

        String target = parts[1];
        String amount = parts[2].replace(",", "").replace("$", "");
        showFakeConfirmation(target, amount);
        return true;
    }

    private static void showFakeConfirmation(String target, String amount) {
        if (mc.player == null) {
            return;
        }
        mc.execute(() -> {
            if (mc.player == null) {
                return;
            }
            mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.0f);
            Text msg = Text.literal("You paid ")
                    .formatted(Formatting.GRAY)
                    .append(Text.literal(target).formatted(Formatting.GREEN))
                    .append(Text.literal(" $" + amount).formatted(Formatting.GOLD));
            mc.inGameHud.getChatHud().addMessage(msg);
        });
    }
}
