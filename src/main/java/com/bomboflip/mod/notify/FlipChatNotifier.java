package com.bomboflip.mod.notify;

import com.bomboflip.mod.config.BomboFlipConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class FlipChatNotifier {

    public static void notifyFlip(String itemName, long price, long profit, String command, int demandTier, double salesPerDay) {
        if (command == null || command.isEmpty()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();

        // 1. Build the base clean chat message
        MutableText prefix = Text.literal("[BomboFlip] ").formatted(Formatting.GOLD, Formatting.BOLD);
        MutableText itemText = Text.literal(itemName).formatted(Formatting.AQUA);
        MutableText priceText = Text.literal(" | Price: " + formatNumber(price)).formatted(Formatting.GRAY);
        MutableText profitText = Text.literal(" | Profit: " + formatNumber(profit)).formatted(Formatting.GREEN, Formatting.BOLD);

        MutableText fullMessage = prefix.append(itemText).append(priceText).append(profitText);

        // 2. ONLY add Demand & Sales Info if Debug Mode is ON
        if (BomboFlipConfig.getInstance().debugMode) {
            String salesStr = salesPerDay >= 0 ? String.format("%.1f/d", salesPerDay) : "?/d";
            MutableText demandText = Text.literal(" | T" + demandTier + " (" + salesStr + ")").formatted(getDemandColor(demandTier));
            fullMessage.append(demandText);
        }

        // 3. Add the Click to Buy button
        MutableText clickToBuy = Text.literal(" [CLICK TO BUY]")
                .formatted(Formatting.RED, Formatting.BOLD)
                .styled(style -> style.withClickEvent(new ClickEvent.RunCommand(command)));

        fullMessage.append(clickToBuy);

        // 4. Push the chat message back to the Main Minecraft Thread safely
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(fullMessage, false);
            }
        });
    }

    private static Formatting getDemandColor(int tier) {
        return switch (tier) {
            case 1 -> Formatting.DARK_RED;
            case 2 -> Formatting.RED;
            case 3 -> Formatting.YELLOW;
            case 4 -> Formatting.GREEN;
            case 5 -> Formatting.DARK_GREEN;
            default -> Formatting.GRAY;
        };
    }

    private static String formatNumber(long number) {
        if (number >= 1_000_000) {
            return String.format("%.1fm", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.1fk", number / 1_000.0);
        }
        return String.valueOf(number);
    }
}