package com.bomboflip.mod.flip;

import com.bomboflip.mod.config.BomboFlipConfig;
import com.bomboflip.mod.notify.FlipChatNotifier;
import com.google.gson.JsonObject;

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

            if (!config.enabled || !config.chatAlertsEnabled) return;

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

            if (price > config.budget) return;
            if (profit < config.minProfit) return;
            if (config.maxProfit > 0 && profit > config.maxProfit) return;
            if (demandTier < config.minDemandTier) return;

            for (String blockedWord : config.blacklist) {
                if (itemName.toLowerCase().contains(blockedWord.toLowerCase())) {
                    return;
                }
            }

            // Deduplication (Per session / server join)
            if (uuid != null) {
                if (!config.showAllFlips) {
                    if (announcedAuctions.put(uuid, Boolean.TRUE) != null) {
                        return;
                    }
                }
            }

            FlipChatNotifier.notifyFlip(itemName, price, profit, command, demandTier, salesPerDay);

        } catch (Exception e) {
            System.err.println("[BomboFlipper] Failed to parse incoming flip data: " + e.getMessage());
        }
    }
}