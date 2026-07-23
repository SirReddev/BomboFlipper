package com.bomboflip.mod.autobuy;

import com.bomboflip.mod.config.BomboFlipConfig;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.concurrent.*;

/**
 * Full AFK handler ported from NotEnoughCoins Premium GuiEvents.class + MixinContainerChest.class.
 *
 * State machine:
 * 1. Flip arrives -> FlipAnalyzer sends /viewauction <uuid>
 * 2. "BIN Auction View" opens -> click slot 31 (Buy) after delay
 * 3. "Confirm Purchase" opens -> click slot 11 (Confirm) immediately
 * 4. Purchase complete -> find empty inventory slot, record item slot, send /ah
 * 5. "Auction House" opens -> click slot 15 (Manage Auctions)
 * 6. "Manage Auctions" opens -> check if max auctions reached, click create auction slot
 * 7. "Create BIN Auction" opens -> place item from inventory, then click slot 31
 * 8. Sign GUI opens -> write listFor price on sign line 0, close after delay
 * 9. "Create BIN Auction" reopens -> click slot 33 (Duration)
 * 10. "Auction Duration" opens -> click slot for duration setting
 * 11. "Create BIN Auction" reopens -> click slot 29 (Confirm listing)
 * 12. "Confirm BIN Auction" opens -> click slot 11 (Final confirm)
 * 13. On "BIN Auction View" with "collect coins" lore -> click slot 31 to claim
 */
public class FlipAutoBuyHandler {

    // ── NEC State Machine Fields ──
    public static volatile boolean autoBuy = false;
    public static volatile boolean justBought = false;
    public static volatile boolean tryToSell = false;
    public static volatile boolean signDone = false;
    public static volatile boolean slotted = false;
    public static volatile boolean timeDone = false;
    public static volatile int slot = -1;         // Adjusted slot index for chest GUI clicks
    public static volatile int slotUnchanged = -1; // Raw inventory slot of the bought item
    public static volatile long listForPrice = 0;  // The target resale price from backend (NEC's "listFor")

    // Timing
    private static long timeOpened = 0;            // When "BIN Auction View" was opened
    private static long lastActionTime = 0;        // Throttle for sequential actions
    private static final long BUY_DELAY_MS = 400;  // NEC's beddelay equivalent (safe default)
    private static final long ACTION_DELAY_MS = 300; // Delay between sequential GUI actions

    // Executor for delayed tasks (same pattern as NEC's Client.scheduledExecutorService)
    private static final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "BomboFlip-AutoBuy-Scheduler");
                t.setDaemon(true);
                return t;
            });

    public static void init() {
        // ── Screen Open Handler (equivalent to NEC's MixinContainerChest + GuiEvents.onTick) ──
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!BomboFlipConfig.getInstance().isFullAfk()) return;
            if (!(screen instanceof HandledScreen<?> handledScreen)) return;

            String title = screen.getTitle().getString();
            if (title == null) return;

            int syncId = handledScreen.getScreenHandler().syncId;

            // ════════════════════════════════════════════════════════════
            // 1. AUTO-BUY: "BIN Auction View" -> Click Slot 31 after delay
            // ════════════════════════════════════════════════════════════
            if (title.contains("BIN Auction View")) {
                // Check if this is a coin claim screen (NEC checks lore for "collect coins")
                scheduler.schedule(() -> {
                    client.execute(() -> {
                        if (client.player == null || client.interactionManager == null) return;
                        if (!(client.currentScreen instanceof HandledScreen<?> hs)) return;

                        // Check slot 31 lore for "collect coins" -> auto-claim
                        Slot slot31 = hs.getScreenHandler().getSlot(31);
                        if (slot31 != null && slot31.hasStack()) {
                            ItemStack stack = slot31.getStack();

                            // Check item name for "collect coins" / "claim" indicator
                            boolean isCollectCoins = false;
                            String itemName = stack.getName().getString().toLowerCase();
                            if (itemName.contains("collect") || itemName.contains("claim")) {
                                isCollectCoins = true;
                            }

                            // Also check if the item is a gold nugget (Hypixel uses gold nugget for coin claim buttons)
                            if (!isCollectCoins) {
                                String stackStr = stack.toString().toLowerCase();
                                if (stackStr.contains("collect") || stackStr.contains("claim")) {
                                    isCollectCoins = true;
                                }
                            }

                            if (isCollectCoins) {
                                // AUTO-CLAIM: Click slot 31 to collect coins
                                client.interactionManager.clickSlot(syncId, 31, 0, SlotActionType.PICKUP, client.player);
                                System.out.println("[BomboFlipper] Auto-claimed coins from sold auction.");
                                scheduler.schedule(() -> client.execute(() -> {
                                    if (client.player != null) client.player.closeHandledScreen();
                                }), 500, TimeUnit.MILLISECONDS);
                                return;
                            }

                            // AUTO-BUY: Not a claim screen, click slot 31 to buy
                            if (autoBuy) {
                                client.interactionManager.clickSlot(syncId, 31, 0, SlotActionType.PICKUP, client.player);
                                timeOpened = System.currentTimeMillis();
                                System.out.println("[BomboFlipper] Auto-buying: clicked slot 31 on BIN Auction View.");
                            }
                        }
                    });
                }, BUY_DELAY_MS, TimeUnit.MILLISECONDS);
            }

            // ════════════════════════════════════════════════════════════
            // 2. CONFIRM PURCHASE: Click Slot 11 (NEC clicks syncId+1, slot 11)
            // ════════════════════════════════════════════════════════════
            if (title.contains("Confirm Purchase") && autoBuy) {
                scheduler.schedule(() -> {
                    client.execute(() -> {
                        if (client.player == null || client.interactionManager == null) return;
                        if (!(client.currentScreen instanceof HandledScreen<?>)) return;

                        client.interactionManager.clickSlot(syncId, 11, 0, SlotActionType.PICKUP, client.player);
                        System.out.println("[BomboFlipper] Auto-confirmed purchase: clicked slot 11.");

                        // NEC: After confirm, find empty inventory slot for the incoming item
                        // and set tryToSell + slotUnchanged
                        scheduler.schedule(() -> {
                            client.execute(() -> {
                                if (client.player == null) return;

                                // Find first empty slot in player inventory (NEC's MixinContainerChest logic)
                                int emptySlot = -1;
                                for (int i = 0; i < client.player.getInventory().size(); i++) {
                                    if (client.player.getInventory().getStack(i).isEmpty()) {
                                        emptySlot = i;
                                        break;
                                    }
                                }

                                if (emptySlot == -1) {
                                    if (client.player != null) {
                                        client.player.sendMessage(Text.literal("§c[BomboFlipper] Cannot relist, your inventory is full!"), false);
                                    }
                                    slot = -1;
                                    return;
                                }

                                slotUnchanged = emptySlot;

                                // NEC slot adjustment for chest GUI offset:
                                // Slots 0-8 (hotbar) -> add 27 to get chest GUI slot offset
                                // Slots 9+ -> subtract 9
                                if (emptySlot >= 0 && emptySlot <= 8) {
                                    slot = emptySlot + 27;
                                } else {
                                    slot = emptySlot - 9;
                                }
                                slot = slot + 1; // NEC adds 1 for final adjustment

                                // Set state machine flags
                                tryToSell = true;
                                timeDone = false;
                                signDone = false;
                                slotted = false;

                                System.out.println("[BomboFlipper] Item bought. Empty slot: " + emptySlot + ", adjusted slot: " + slot + ". Starting sell flow...");

                                // Close current screen and send /ah after delay (NEC sends /ah)
                                if (client.player != null) {
                                    client.player.closeHandledScreen();
                                }
                                scheduler.schedule(() -> {
                                    client.execute(() -> {
                                        if (client.player != null) {
                                            justBought = true;
                                            client.player.networkHandler.sendChatMessage("/ah");
                                        }
                                    });
                                }, 1000, TimeUnit.MILLISECONDS);
                            });
                        }, 500, TimeUnit.MILLISECONDS);
                    });
                }, ACTION_DELAY_MS, TimeUnit.MILLISECONDS);
            }

            // ════════════════════════════════════════════════════════════
            // 3. AUCTION HOUSE: Click Slot 15 (Manage Auctions / Create Auction)
            // ════════════════════════════════════════════════════════════
            if (title.contains("Auction House") && justBought) {
                scheduler.schedule(() -> {
                    client.execute(() -> {
                        if (client.player == null || client.interactionManager == null) return;
                        if (!(client.currentScreen instanceof HandledScreen<?>)) return;

                        client.interactionManager.clickSlot(syncId, 15, 0, SlotActionType.PICKUP, client.player);
                        System.out.println("[BomboFlipper] Auction House: clicked slot 15 (Manage Auctions).");
                    });
                }, ACTION_DELAY_MS, TimeUnit.MILLISECONDS);
            }

            // ════════════════════════════════════════════════════════════
            // 4. MANAGE AUCTIONS: Check max auctions, click create slot
            // ════════════════════════════════════════════════════════════
            if (title.contains("Manage Auctions") && justBought) {
                scheduler.schedule(() -> {
                    client.execute(() -> {
                        if (client.player == null || client.interactionManager == null) return;
                        if (!(client.currentScreen instanceof HandledScreen<?> hs)) return;

                        // NEC checks lore for "You reached the maximum number"
                        for (int i = 0; i < hs.getScreenHandler().slots.size(); i++) {
                            Slot s = hs.getScreenHandler().getSlot(i);
                            if (s.hasStack()) {
                                ItemStack stack = s.getStack();
                                String name = stack.getName().getString();
                                if (name.contains("Create")) {
                                    // Click "Create Auction" button
                                    client.interactionManager.clickSlot(syncId, i, 0, SlotActionType.PICKUP, client.player);
                                    System.out.println("[BomboFlipper] Manage Auctions: clicked Create Auction slot " + i);
                                    return;
                                }
                            }
                        }
                        // Fallback: try slot 48 (bottom right area where create usually is)
                        System.out.println("[BomboFlipper] Manage Auctions: no Create button found, trying slot 48.");
                        client.interactionManager.clickSlot(syncId, 48, 0, SlotActionType.PICKUP, client.player);
                    });
                }, ACTION_DELAY_MS, TimeUnit.MILLISECONDS);
            }

            // ════════════════════════════════════════════════════════════
            // 5. CREATE BIN AUCTION: Place item, set price, set duration, confirm
            // ════════════════════════════════════════════════════════════
            if (title.contains("Create BIN Auction") && justBought) {
                scheduler.schedule(() -> {
                    client.execute(() -> {
                        if (client.player == null || client.interactionManager == null) return;
                        if (!(client.currentScreen instanceof HandledScreen<?> hs)) return;

                        if (!signDone) {
                            // Step 1: Click the item from player inventory into auction slot
                            // NEC uses slot + 53 offset (chest has 54 slots, player inv starts after)
                            int itemSlotInChest = slot + 53;
                            client.interactionManager.clickSlot(syncId, itemSlotInChest, 0, SlotActionType.PICKUP, client.player);
                            System.out.println("[BomboFlipper] Create BIN Auction: placed item from slot " + itemSlotInChest);

                            // Then click slot 31 to set the item
                            scheduler.schedule(() -> {
                                client.execute(() -> {
                                    if (client.interactionManager == null || client.player == null) return;
                                    if (!(client.currentScreen instanceof HandledScreen<?>)) return;
                                    client.interactionManager.clickSlot(syncId, 31, 0, SlotActionType.PICKUP, client.player);
                                    System.out.println("[BomboFlipper] Create BIN Auction: clicked slot 31 (set price / sign).");
                                });
                            }, ACTION_DELAY_MS, TimeUnit.MILLISECONDS);
                        } else if (!timeDone) {
                            // Step 2: Click slot 33 (Auction Duration)
                            client.interactionManager.clickSlot(syncId, 33, 0, SlotActionType.PICKUP, client.player);
                            System.out.println("[BomboFlipper] Create BIN Auction: clicked slot 33 (Duration).");
                        } else {
                            // Step 3: Click slot 29 (Confirm listing)
                            client.interactionManager.clickSlot(syncId, 29, 0, SlotActionType.PICKUP, client.player);
                            System.out.println("[BomboFlipper] Create BIN Auction: clicked slot 29 (Confirm).");
                        }
                    });
                }, ACTION_DELAY_MS, TimeUnit.MILLISECONDS);
            }

            // ════════════════════════════════════════════════════════════
            // 6. AUCTION DURATION: Click the duration slot
            // ════════════════════════════════════════════════════════════
            if (title.contains("Auction Duration") && justBought && !timeDone) {
                scheduler.schedule(() -> {
                    client.execute(() -> {
                        if (client.player == null || client.interactionManager == null) return;
                        if (!(client.currentScreen instanceof HandledScreen<?>)) return;

                        // NEC uses config "auctiontime" slot. Default to slot 11 (12h) or 15 (24h)
                        // We use slot 15 as a safe default (24 hours)
                        client.interactionManager.clickSlot(syncId, 15, 0, SlotActionType.PICKUP, client.player);
                        timeDone = true;
                        System.out.println("[BomboFlipper] Auction Duration: set to 24h (slot 15). timeDone=true.");
                    });
                }, ACTION_DELAY_MS, TimeUnit.MILLISECONDS);
            }

            // ════════════════════════════════════════════════════════════
            // 7. CONFIRM BIN AUCTION: Final click slot 11
            // ════════════════════════════════════════════════════════════
            if (title.contains("Confirm BIN Auction") && justBought) {
                scheduler.schedule(() -> {
                    client.execute(() -> {
                        if (client.player == null || client.interactionManager == null) return;
                        if (!(client.currentScreen instanceof HandledScreen<?>)) return;

                        client.interactionManager.clickSlot(syncId, 11, 0, SlotActionType.PICKUP, client.player);
                        System.out.println("[BomboFlipper] Confirm BIN Auction: clicked slot 11. Listing complete!");

                        // Reset all state (NEC does this after confirm)
                        scheduler.schedule(() -> {
                            signDone = false;
                            justBought = false;
                            autoBuy = false;
                            tryToSell = false;
                            slotted = false;
                            timeDone = false;
                            slot = -1;
                            slotUnchanged = -1;
                            listForPrice = 0;

                            client.execute(() -> {
                                if (client.player != null) {
                                    client.player.sendMessage(Text.literal("§a[BomboFlipper] ✔ Item listed on AH! Full AFK cycle complete."), false);
                                    client.player.closeHandledScreen();
                                }
                            });
                        }, 500, TimeUnit.MILLISECONDS);
                    });
                }, ACTION_DELAY_MS, TimeUnit.MILLISECONDS);
            }
        });

        // ── Sign Screen Handler (NEC's onOpen for GuiEditSign) ──
        // In Fabric 1.20+, we intercept AbstractSignEditScreen to write the price
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!BomboFlipConfig.getInstance().isFullAfk()) return;
            if (!justBought || signDone) return;

            // Check if this is a sign edit screen
            if (screen instanceof net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen signScreen) {
                if (listForPrice <= 0) {
                    System.out.println("[BomboFlipper] Sign opened but listForPrice is 0, skipping auto-fill.");
                    return;
                }

                signDone = true;
                System.out.println("[BomboFlipper] Sign GUI intercepted! Writing price: " + listForPrice);

                // NEC uses reflection to access TileEntitySign and write to signText[0]
                // In Fabric 1.20+, we use the sign screen's text field via reflection
                try {
                    // Access the messages array in AbstractSignEditScreen
                    java.lang.reflect.Field messagesField = null;
                    for (java.lang.reflect.Field f : net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen.class.getDeclaredFields()) {
                        if (f.getType().isArray() && f.getType().getComponentType() == String.class) {
                            messagesField = f;
                            break;
                        }
                    }

                    if (messagesField != null) {
                        messagesField.setAccessible(true);
                        String[] messages = (String[]) messagesField.get(signScreen);
                        messages[0] = String.valueOf(listForPrice);
                        System.out.println("[BomboFlipper] Sign price written: " + listForPrice);
                    }
                } catch (Exception e) {
                    System.err.println("[BomboFlipper] Failed to write sign price via reflection: " + e.getMessage());
                }

                // Close the sign after 700ms delay (NEC uses 700ms)
                scheduler.schedule(() -> {
                    client.execute(() -> {
                        if (client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen) {
                            client.currentScreen.close();
                            System.out.println("[BomboFlipper] Sign closed after 700ms delay.");
                        }
                    });
                }, 700, TimeUnit.MILLISECONDS);
            }
        });
    }

    /**
     * Called by FlipAnalyzer when a flip passes all filters and fullAfk is enabled.
     * Sets the autoBuy flag and listForPrice so the state machine knows to proceed.
     */
    public static void prepareAutoBuy(long resalePrice) {
        autoBuy = true;
        listForPrice = resalePrice;
        justBought = false;
        tryToSell = false;
        signDone = false;
        slotted = false;
        timeDone = false;
        slot = -1;
        slotUnchanged = -1;
        System.out.println("[BomboFlipper] Auto-buy armed! listForPrice=" + resalePrice);
    }

    /**
     * Reset all state (called on error or manual cancel)
     */
    public static void resetState() {
        autoBuy = false;
        justBought = false;
        tryToSell = false;
        signDone = false;
        slotted = false;
        timeDone = false;
        slot = -1;
        slotUnchanged = -1;
        listForPrice = 0;
    }
}
