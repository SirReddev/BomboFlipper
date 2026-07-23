package com.bomboflip.mod.autobuy;

import com.bomboflip.mod.config.BomboFlipConfig;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.SlotActionType;

public class FlipAutoBuyHandler {

    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!BomboFlipConfig.getInstance().fullAfk) return;
            if (!(screen instanceof HandledScreen<?> handledScreen)) return;

            String title = screen.getTitle().getString();
            if (title == null) return;

            int syncId = handledScreen.getScreenHandler().syncId;

            // 1. AUTO BUY: Handle BIN Auction View & Confirm Purchase (Slot 31)
            if (title.contains("BIN Auction View") || title.contains("Confirm Purchase")) {
                client.execute(() -> {
                    if (client.interactionManager != null && client.player != null) {
                        client.interactionManager.clickSlot(syncId, 31, 0, SlotActionType.PICKUP, client.player);
                    }
                });
            }

            // 2. FULL AFK AUTO CLAIM & AUTO SELL (Manage Auctions & Auctions Browser)
            if (title.contains("Manage Auctions")) {
                // Click claim all / create auction slot 31
                client.execute(() -> {
                    if (client.interactionManager != null && client.player != null) {
                        client.interactionManager.clickSlot(syncId, 31, 0, SlotActionType.PICKUP, client.player);
                    }
                });
            } else if (title.contains("Auctions Browser")) {
                // Click Create Auction slot 15
                client.execute(() -> {
                    if (client.interactionManager != null && client.player != null) {
                        client.interactionManager.clickSlot(syncId, 15, 0, SlotActionType.PICKUP, client.player);
                    }
                });
            }
        });
    }
}
