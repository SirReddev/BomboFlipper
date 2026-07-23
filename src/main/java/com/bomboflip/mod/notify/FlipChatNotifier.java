package com.bomboflip.mod.notify;

import com.bomboflip.mod.config.BomboFlipConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class FlipChatNotifier {

    public static void notifyFlip(String itemName, long price, long profit, long estimatedValue, String command, int demandTier, double salesPerDay, String uuid) {
        if (command == null || command.isEmpty()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();

        // 1. Build main chat notification line
        MutableText prefix = Text.literal("[BomboFlip] ").formatted(Formatting.GOLD, Formatting.BOLD);
        MutableText itemText = Text.literal(itemName).formatted(Formatting.AQUA);
        MutableText priceText = Text.literal(" | Price: " + formatNumber(price)).formatted(Formatting.GRAY);
        MutableText profitText = Text.literal(" | Profit: " + formatNumber(profit)).formatted(Formatting.GREEN, Formatting.BOLD);

        String salesStr = salesPerDay >= 0 ? String.format("%.1f/d", salesPerDay) : "?/d";
        MutableText demandText = Text.literal(" | T" + demandTier + " (" + salesStr + ")").formatted(getDemandColor(demandTier));

        MutableText mainMessage = prefix.append(itemText).append(priceText).append(profitText).append(demandText);

        // Add the Click to Buy button
        MutableText clickToBuy = Text.literal(" [CLICK TO BUY]")
                .formatted(Formatting.RED, Formatting.BOLD)
                .styled(style -> style.withClickEvent(new ClickEvent.RunCommand(command)));

        mainMessage.append(clickToBuy);

        // 2. Build Debug Detail Line (only shown when Debug Mode is ON)
        MutableText debugLine = null;
        if (BomboFlipConfig.getInstance().debugMode) {
            double marginPct = price > 0 ? ((double) profit / price) * 100.0 : 0.0;
            String uuidStr = (uuid != null && !uuid.isEmpty()) ? uuid : "N/A";

            debugLine = Text.literal("  └─ [DEBUG] ").formatted(Formatting.DARK_AQUA, Formatting.BOLD)
                    .append(Text.literal("Resale: ").formatted(Formatting.GRAY))
                    .append(Text.literal(formatNumber(estimatedValue)).formatted(Formatting.YELLOW))
                    .append(Text.literal(" | Margin: ").formatted(Formatting.GRAY))
                    .append(Text.literal(String.format("+%.1f%%", marginPct)).formatted(Formatting.GREEN))
                    .append(Text.literal(" | UUID: ").formatted(Formatting.GRAY))
                    .append(Text.literal(uuidStr).formatted(Formatting.DARK_GRAY));
        }

        final MutableText extraDebug = debugLine;

        // 3. Push chat messages on main client thread
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(mainMessage, false);
                if (extraDebug != null) {
                    client.player.sendMessage(extraDebug, false);
                }
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