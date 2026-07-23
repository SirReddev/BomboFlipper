package com.bomboflip.mod;

import com.bomboflip.mod.api.CoflWebSocketClient;
import com.bomboflip.mod.config.BomboFlipConfig;
import com.bomboflip.mod.moul.MoulConfigIntegrator;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class BomboFlipClient implements ClientModInitializer {

    private static long nextAfkTickTime = 0;
    private static final java.util.Random random = new java.util.Random();

    @Override
    public void onInitializeClient() {
        System.out.println("[BomboFlipper] Initializing mod...");

        // 1. Load config file
        BomboFlipConfig.load();

        // Register Auto-Buy container handler
        com.bomboflip.mod.autobuy.FlipAutoBuyHandler.init();

        // Shutdown hook to guarantee config is saved on game close
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                MoulConfigIntegrator.save();
            } catch (Exception ignored) {}
        }));

        // Register server join event: clear announced auctions memory so active flips re-announce on join
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            com.bomboflip.mod.flip.FlipAnalyzer.clearAnnouncedAuctions();
        });

        // Anti-AFK Tick Handler (Runs subtle rotation packets when fullAfk is enabled)
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            if (!BomboFlipConfig.getInstance().isFullAfk()) return;

            long currentTime = System.currentTimeMillis();
            if (nextAfkTickTime == 0) {
                nextAfkTickTime = currentTime + (45000 + random.nextInt(45000)); // 45s-90s randomized interval
            }

            if (currentTime >= nextAfkTickTime) {
                // Micro yaw rotation (+- 0.5 deg)
                float deltaYaw = (random.nextFloat() - 0.5f) * 1.0f;
                client.player.setYaw(client.player.getYaw() + deltaYaw);
                nextAfkTickTime = currentTime + (45000 + random.nextInt(45000));
            }
        });

        // 2. Start the WebSocket connection statically in the background
        Thread webSocketThread = new Thread(() -> {
            try {
                CoflWebSocketClient.connect();
            } catch (Exception e) {
                System.err.println("[BomboFlipper] WebSocket connection threw an error:");
                e.printStackTrace();
            }
        });
        webSocketThread.setName("BomboFlip-WebSocket-Thread");
        webSocketThread.start();

        // 3. Register the client-side commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("bomboflipper")

                    // Base command: /bomboflipper (Opens the MoulConfig GUI)
                    .executes(context -> {
                        MinecraftClient client = MinecraftClient.getInstance();
                        client.execute(() -> {
                            MoulConfigIntegrator.openConfigScreen();
                        });
                        return 1;
                    })

                    // Sub-command: /bomboflipper enable <true/false>
                    .then(ClientCommandManager.literal("enable")
                            .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                    .executes(context -> {
                                        boolean val = BoolArgumentType.getBool(context, "value");
                                        BomboFlipConfig.getInstance().enabled = val;
                                        BomboFlipConfig.getInstance().save();

                                        context.getSource().sendFeedback(Text.literal("§a[BomboFlipper] Enabled set to: " + val));
                                        return 1;
                                    })
                            )
                    )

                    // Sub-command: /bomboflipper budget <amount>
                    .then(ClientCommandManager.literal("budget")
                            .then(ClientCommandManager.argument("amount", LongArgumentType.longArg(0))
                                    .executes(context -> {
                                        long val = LongArgumentType.getLong(context, "amount");
                                        BomboFlipConfig.getInstance().budget = val;
                                        BomboFlipConfig.getInstance().save();

                                        context.getSource().sendFeedback(Text.literal("§a[BomboFlipper] Budget set to: " + val));
                                        return 1;
                                    })
                            )
                    )

                    // Sub-command: /bomboflipper minProfit <amount>
                    .then(ClientCommandManager.literal("minProfit")
                            .then(ClientCommandManager.argument("amount", LongArgumentType.longArg(0))
                                    .executes(context -> {
                                        long val = LongArgumentType.getLong(context, "amount");
                                        BomboFlipConfig.getInstance().minProfit = val;
                                        BomboFlipConfig.getInstance().save();

                                        context.getSource().sendFeedback(Text.literal("§a[BomboFlipper] Minimum Profit set to: " + val));
                                        return 1;
                                    })
                            )
                    )

                    // Sub-command: /bomboflipper maxProfit <amount>
                    .then(ClientCommandManager.literal("maxProfit")
                            .then(ClientCommandManager.argument("amount", LongArgumentType.longArg(0))
                                    .executes(context -> {
                                        long val = LongArgumentType.getLong(context, "amount");
                                        BomboFlipConfig.getInstance().maxProfit = val;
                                        BomboFlipConfig.getInstance().save();

                                        context.getSource().sendFeedback(Text.literal("§a[BomboFlipper] Maximum Profit set to: " + val));
                                        return 1;
                                    })
                            )
                    )

                    // Sub-command: /bomboflipper minDemandTier <1-5>
                    .then(ClientCommandManager.literal("minDemandTier")
                            .then(ClientCommandManager.argument("tier", IntegerArgumentType.integer(1, 5))
                                    .executes(context -> {
                                        int val = IntegerArgumentType.getInteger(context, "tier");
                                        BomboFlipConfig.getInstance().minDemandTier = val;
                                        BomboFlipConfig.getInstance().save();

                                        context.getSource().sendFeedback(Text.literal("§a[BomboFlipper] Minimum Demand Tier set to: " + val));
                                        return 1;
                                    })
                            )
                    )

                    // Sub-command: /bomboflipper chatAlerts <true/false>
                    .then(ClientCommandManager.literal("chatAlerts")
                            .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                    .executes(context -> {
                                        boolean val = BoolArgumentType.getBool(context, "value");
                                        BomboFlipConfig.getInstance().chatAlertsEnabled = val;
                                        BomboFlipConfig.getInstance().save();

                                        context.getSource().sendFeedback(Text.literal("§a[BomboFlipper] Chat Alerts set to: " + val));
                                        return 1;
                                    })
                            )
                    )

                    // Sub-command: /bomboflipper soundAlerts <true/false>
                    .then(ClientCommandManager.literal("soundAlerts")
                            .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                    .executes(context -> {
                                        boolean val = BoolArgumentType.getBool(context, "value");
                                        BomboFlipConfig.getInstance().soundAlertsEnabled = val;
                                        BomboFlipConfig.getInstance().save();

                                        context.getSource().sendFeedback(Text.literal("§a[BomboFlipper] Sound Alerts set to: " + val));
                                        return 1;
                                    })
                            )
                    )

                    // Sub-command: /bomboflipper oneClickBuy <true/false>
                    .then(ClientCommandManager.literal("oneClickBuy")
                            .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                    .executes(context -> {
                                        boolean val = BoolArgumentType.getBool(context, "value");
                                        BomboFlipConfig.getInstance().oneClickBuy = val;
                                        BomboFlipConfig.getInstance().save();

                                        context.getSource().sendFeedback(Text.literal("§a[BomboFlipper] One-Click Buy set to: " + val));
                                        return 1;
                                    })
                            )
                    )

                    // Sub-command: /bomboflipper fullAfk <true/false>
                    .then(ClientCommandManager.literal("fullAfk")
                            .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                    .executes(context -> {
                                        boolean val = BoolArgumentType.getBool(context, "value");
                                        BomboFlipConfig.getInstance().fullAfk = val;
                                        BomboFlipConfig.getInstance().save();

                                        context.getSource().sendFeedback(Text.literal("§a[BomboFlipper] Full AFK set to: " + val));
                                        return 1;
                                    })
                            )
                    )

                    // Sub-command: /bomboflipper debugMode <true/false>
                    .then(ClientCommandManager.literal("debugMode")
                            .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                    .executes(context -> {
                                        boolean val = BoolArgumentType.getBool(context, "value");
                                        BomboFlipConfig.getInstance().debugMode = val;
                                        BomboFlipConfig.getInstance().save();

                                        context.getSource().sendFeedback(Text.literal("§a[BomboFlipper] Debug Mode set to: " + val));
                                        return 1;
                                    })
                            )
                    )

                    // Sub-command: /bomboflipper blacklist
                    .then(ClientCommandManager.literal("blacklist")
                            // /bomboflipper blacklist clear
                            .then(ClientCommandManager.literal("clear")
                                    .executes(context -> {
                                        BomboFlipConfig.getInstance().blacklist.clear();
                                        BomboFlipConfig.getInstance().save();

                                        context.getSource().sendFeedback(Text.literal("§a[BomboFlipper] Blacklist cleared."));
                                        return 1;
                                    })
                            )
                            // /bomboflipper blacklist add <item>
                            .then(ClientCommandManager.literal("add")
                                    .then(ClientCommandManager.argument("item", StringArgumentType.greedyString())
                                            .executes(context -> {
                                                String item = StringArgumentType.getString(context, "item");
                                                BomboFlipConfig.getInstance().blacklist.add(item);
                                                BomboFlipConfig.getInstance().save();

                                                context.getSource().sendFeedback(Text.literal("§a[BomboFlipper] Added '" + item + "' to blacklist."));
                                                return 1;
                                            })
                                    )
                            )
                            // /bomboflipper blacklist remove <item>
                            .then(ClientCommandManager.literal("remove")
                                    .then(ClientCommandManager.argument("item", StringArgumentType.greedyString())
                                            .executes(context -> {
                                                String item = StringArgumentType.getString(context, "item");
                                                if (BomboFlipConfig.getInstance().blacklist.remove(item)) {
                                                    context.getSource().sendFeedback(Text.literal("§a[BomboFlipper] Removed '" + item + "' from blacklist."));
                                                } else {
                                                    context.getSource().sendFeedback(Text.literal("§c[BomboFlipper] Item '" + item + "' not found in blacklist."));
                                                }
                                                BomboFlipConfig.getInstance().save();
                                                return 1;
                                            })
                                    )
                            )
                    )
            );
        });
    }
}