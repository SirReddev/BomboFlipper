package com.bomboflip.mod.moul;

import com.google.gson.annotations.Expose;
import io.github.notenoughupdates.moulconfig.annotations.*;
import io.github.notenoughupdates.moulconfig.Config;
import io.github.notenoughupdates.moulconfig.common.text.StructuredText;

public class MoulBomboConfig extends Config {

    @Override
    public StructuredText getTitle() {
        return StructuredText.of("BomboFlipper Config");
    }

    @Expose
    @Category(name = "General", desc = "Master settings")
    public General general = new General();

    @Expose
    @Category(name = "Filters", desc = "Filtering and matching settings")
    public Filters filters = new Filters();

    @Expose
    @Category(name = "Alerts", desc = "Notification settings")
    public Alerts alerts = new Alerts();

    public static class General {
        @Expose
        @ConfigOption(name = "Enabled", desc = "Master toggle")
        @ConfigEditorBoolean
        public boolean enabled = true;

        @Expose
        @ConfigOption(name = "One-Click Buy", desc = "Enable instant one-click buying buttons in chat")
        @ConfigEditorBoolean
        public boolean oneClickBuy = true;

        @Expose
        @ConfigOption(name = "Full AFK", desc = "OFF by default. Master NotEnoughCoins mode: Auto-buys flips, claims sold coins, and lists items at target profit")
        @ConfigEditorBoolean
        public boolean fullAfk = false;

        @Expose
        @ConfigOption(name = "Budget", desc = "Max spend per flip")
        @ConfigEditorText
        public String budget = "100000000";

        @Expose
        @ConfigOption(name = "Min profit", desc = "Minimum profit to display")
        @ConfigEditorText
        public String minProfit = "500000";

        @Expose
        @ConfigOption(name = "Max profit", desc = "Ignore suspiciously huge profits")
        @ConfigEditorText
        public String maxProfit = "100000000";

        @Expose
        @ConfigOption(name = "Reset to Defaults", desc = "Click to restore all recommended default settings")
        @ConfigEditorButton
        public Runnable resetDefaults = () -> {
            com.bomboflip.mod.config.BomboFlipConfig.getInstance().resetToDefaults();
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            String msg = "§8[§bBomboFlipper§8] §aReset all settings to recommended defaults!";
            if (client.player != null) {
                client.player.sendMessage(net.minecraft.text.Text.literal(msg), false);
            }
        };
    }

    public static class Filters {
        @Expose
        @ConfigOption(name = "Blacklist", desc = "Comma separated names to ignore")
        @ConfigEditorText
        public String blacklist = "Skin, Dye, Rune, Travel Scroll, Furniture, Cake, Minion Skin, Pet Skin, Firework, Banner, Balloon, Bucket";

        @Expose
        @ConfigOption(
                name = "Min demand tier",
                desc = "Minimum demand tier to show\n" +
                        "1 = ANY\n" +
                        "2 = LOW\n" +
                        "3 = MEDIUM\n" +
                        "4 = HIGH\n" +
                        "5 = VERY HIGH"
        )
        @ConfigEditorSlider(minValue = 1, maxValue = 5, minStep = 1)
        public int minDemandTier = 2;
    }

    public static class Alerts {
        @Expose
        @ConfigOption(name = "Chat alerts", desc = "Show flips in chat")
        @ConfigEditorBoolean
        public boolean chatAlertsEnabled = true;

        @Expose
        @ConfigOption(name = "Sound alerts", desc = "Play a sound for new flips")
        @ConfigEditorBoolean
        public boolean soundAlertsEnabled = true;

        @Expose
        @ConfigOption(name = "Debug mode", desc = "Show debug information")
        @ConfigEditorBoolean
        public boolean debugMode = false;

        @Expose
        @ConfigOption(
                name = "Show all flips",
                desc = "OFF (default): only announce each auction once.\n" +
                        "ON: re-announce a flip every time the backend re-sends it " +
                        "while it's still listed (spams chat, useful for debugging)."
        )
        @ConfigEditorBoolean
        public boolean showAllFlips = false;

        @Expose
        @ConfigOption(name = "Test Connection", desc = "Click to test WebSocket connection status")
        @ConfigEditorButton
        public Runnable testConnection = () -> {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();

            boolean isOnline = com.bomboflip.mod.api.CoflWebSocketClient.isServerConnected();

            String statusColor = isOnline ? "§a" : "§c";
            String statusText = isOnline ? "CONNECTED" : "DISCONNECTED";

            String message = "§8[§bBomboFlipper§8] §7Server Status: " + statusColor + statusText;

            if (client.player != null) {
                client.player.sendMessage(net.minecraft.text.Text.literal(message), false);
            } else if (client.inGameHud != null) {
                client.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal(message));
            }
        };
    }

    // --- CRITICAL FIX: Intercept MoulConfig's native save event ---
    @Override
    public void saveNow() {
        MoulConfigIntegrator.save();
    }
}