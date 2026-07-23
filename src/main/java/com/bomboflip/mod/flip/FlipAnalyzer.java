package com.bomboflip.mod.flip;

import com.bomboflip.mod.autobuy.FlipAutoBuyHandler;
import com.bomboflip.mod.config.BomboFlipConfig;
import com.bomboflip.mod.notify.FlipChatNotifier;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class FlipAnalyzer {

    private static final int MAX_TRACKED_AUCTIONS = 5000;

    private static final Map<String, Boolean> announcedAuctions =
            Collections.synchronizedMap(new LinkedHashMap<>(512, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_TRACKED_AUCTIONS;
                }
            });

    public static void clearAnnouncedAuctions() {
        announcedAuctions.clear();
        System.out.println("[BomboFlipper] Cleared announced auctions history on server rejoin.");
    }

    public static void processWebSocketFlip(JsonObject json) {
        try {
            BomboFlipConfig config = BomboFlipConfig.getInstance();

            if (!config.isEnabled() || !config.isChatAlertsEnabled()) return;

            String itemName = json.has("itemName") ? json.get("itemName").getAsString() : (json.has("item") ? json.get("item").getAsString() : "Unknown Item");
            long price = json.has("price") ? json.get("price").getAsLong() : 0;
            long profit = json.has("profit") ? json.get("profit").getAsLong() : 0;

            String uuid = json.has("uuid") ? json.get("uuid").getAsString() : (json.has("id") ? json.get("id").getAsString() : null);

            String command = json.has("command") ? json.get("command").getAsString() : "";
            if (command.isEmpty() && uuid != null && !uuid.isEmpty()) {
                command = "/viewauction " + uuid;
            }

            int demandTier = json.has("demandTier") ? json.get("demandTier").getAsInt() : 5;
            double salesPerDay = json.has("salesPerDay") ? json.get("salesPerDay").getAsDouble() : -1;

            if (price > config.getBudget()) return;
            if (profit < config.getMinProfit()) return;
            if (config.getMaxProfit() > 0 && profit > config.getMaxProfit()) return;
            if (demandTier < config.getMinDemandTier()) return;

            for (String blockedWord : config.getBlacklist()) {
                if (itemName.toLowerCase().contains(blockedWord.toLowerCase())) {
                    return;
                }
            }

            // Deduplication (Per session / server join)
            if (uuid != null) {
                if (!config.isShowAllFlips()) {
                    if (announcedAuctions.put(uuid, Boolean.TRUE) != null) {
                        return;
                    }
                }
            }

            long estimatedValue = json.has("estimatedValue") ? json.get("estimatedValue").getAsLong() : (price + profit);

            // If Full AFK is enabled, arm the auto-buy state machine and send /viewauction
            if (config.isFullAfk() && uuid != null && !uuid.isEmpty()) {
                // Arm the NEC-style state machine with the target resale price
                FlipAutoBuyHandler.prepareAutoBuy(estimatedValue);

                MinecraftClient client = MinecraftClient.getInstance();
                client.execute(() -> {
                    if (client.player != null) {
                        client.player.networkHandler.sendChatMessage("/viewauction " + uuid);
                    }
                });
            }

            FlipChatNotifier.notifyFlip(itemName, price, profit, estimatedValue, command, demandTier, salesPerDay, uuid);

        } catch (Exception e) {
            System.err.println("[BomboFlipper] Failed to parse incoming flip data: " + e.getMessage());
        }
    }
}