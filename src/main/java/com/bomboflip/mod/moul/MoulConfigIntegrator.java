package com.bomboflip.mod.moul;

import com.bomboflip.mod.config.BomboFlipConfig;
import io.github.notenoughupdates.moulconfig.gui.GuiContext;
import io.github.notenoughupdates.moulconfig.gui.GuiElementComponent;
import io.github.notenoughupdates.moulconfig.managed.ManagedConfig;
import io.github.notenoughupdates.moulconfig.managed.ManagedConfigBuilder;
import io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public final class MoulConfigIntegrator {

    private static ManagedConfig<MoulBomboConfig> managed;

    private MoulConfigIntegrator() {
    }

    public static synchronized ManagedConfig<MoulBomboConfig> getManaged() {
        if (managed == null) {
            var file = FabricLoader.getInstance()
                    .getConfigDir()
                    .resolve("bomboflip_gui.json")
                    .toFile();

            managed = new ManagedConfig<>(new ManagedConfigBuilder<>(file, MoulBomboConfig.class));
        }
        return managed;
    }

    public static void openConfigScreen() {
        pullFromRuntimeConfig();

        GuiContext context = new GuiContext(new GuiElementComponent(getManaged().getEditor()));
        Screen previousScreen = MinecraftClient.getInstance().currentScreen;

        MinecraftClient.getInstance().setScreen(new MoulConfigScreenComponent(Text.empty(), context, previousScreen) {
            @Override
            public void removed() {
                super.removed();
                // Backup save trigger when the screen is closed
                pushToRuntimeConfig();
            }
        });
    }

    public static void pullFromRuntimeConfig() {
        BomboFlipConfig runtime = BomboFlipConfig.getInstance();
        MoulBomboConfig gui = getManaged().getInstance();
        if (gui == null || runtime == null) return;

        gui.general.enabled = runtime.enabled;
        gui.general.budget = String.valueOf(runtime.budget);
        gui.general.minProfit = String.valueOf(runtime.minProfit);
        gui.general.maxProfit = String.valueOf(runtime.maxProfit);
        gui.filters.blacklist = String.join(", ", runtime.blacklist);
        gui.filters.minDemandTier = runtime.minDemandTier;
        gui.alerts.chatAlertsEnabled = runtime.chatAlertsEnabled;
        gui.alerts.soundAlertsEnabled = runtime.soundAlertsEnabled;
        gui.alerts.debugMode = runtime.debugMode;
        gui.alerts.showAllFlips = runtime.showAllFlips;
    }

    public static void pushToRuntimeConfig() {
        MoulBomboConfig gui = getManaged().getInstance();
        BomboFlipConfig runtime = BomboFlipConfig.getInstance();
        if (gui == null || runtime == null) return;

        runtime.enabled = gui.general.enabled;

        runtime.budget = parseNumber(gui.general.budget, runtime.budget);
        runtime.minProfit = parseNumber(gui.general.minProfit, runtime.minProfit);
        runtime.maxProfit = parseNumber(gui.general.maxProfit, runtime.maxProfit);

        runtime.blacklist.clear();
        if (gui.filters.blacklist != null) {
            for (String entry : gui.filters.blacklist.split(",")) {
                String trimmed = entry.trim();
                if (!trimmed.isEmpty()) {
                    runtime.blacklist.add(trimmed);
                }
            }
        }

        runtime.minDemandTier = gui.filters.minDemandTier;
        runtime.chatAlertsEnabled = gui.alerts.chatAlertsEnabled;
        runtime.soundAlertsEnabled = gui.alerts.soundAlertsEnabled;
        runtime.debugMode = gui.alerts.debugMode;
        runtime.showAllFlips = gui.alerts.showAllFlips;

        // Save runtime JSON config to disk
        runtime.save();

        // Save Managed GUI config to disk
        try {
            getManaged().saveToFile();
        } catch (Exception e) {
            System.err.println("[BomboFlipper] Failed to save GUI config to disk: " + e.getMessage());
        }
    }

    public static long parseNumber(String input, long fallback) {
        if (input == null || input.trim().isEmpty()) return fallback;
        String clean = input.trim().toLowerCase().replaceAll("[,_\\s]", "");
        try {
            if (clean.endsWith("m")) {
                return (long) (Double.parseDouble(clean.substring(0, clean.length() - 1)) * 1_000_000);
            } else if (clean.endsWith("k")) {
                return (long) (Double.parseDouble(clean.substring(0, clean.length() - 1)) * 1_000);
            } else if (clean.endsWith("b")) {
                return (long) (Double.parseDouble(clean.substring(0, clean.length() - 1)) * 1_000_000_000L);
            }
            return Long.parseLong(clean);
        } catch (Exception e) {
            return fallback;
        }
    }
}