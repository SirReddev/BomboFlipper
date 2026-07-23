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
                    .resolve("bomboflip.json")
                    .toFile();

            managed = new ManagedConfig<>(new ManagedConfigBuilder<>(file, MoulBomboConfig.class));
        }
        return managed;
    }

    public static void openConfigScreen() {
        GuiContext context = new GuiContext(new GuiElementComponent(getManaged().getEditor()));
        Screen previousScreen = MinecraftClient.getInstance().currentScreen;

        MinecraftClient.getInstance().setScreen(new MoulConfigScreenComponent(Text.empty(), context, previousScreen) {
            @Override
            public void removed() {
                super.removed();
                save();
            }
        });
    }

    public static void save() {
        if (managed != null) {
            try {
                managed.saveToFile();
            } catch (Exception e) {
                System.err.println("[BomboFlipper] Failed to save config to disk: " + e.getMessage());
            }
            BomboFlipConfig.syncWithMoul();

            BomboFlipConfig config = BomboFlipConfig.getInstance();
            if (config.debugMode) {
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                String msg = String.format(
                        "§8[§bBomboFlipper Debug§8] §7Config saved: Enabled=%b | Budget=%s | MinProfit=%s | DemandTier=%d | Blacklist=[%s]",
                        config.enabled,
                        formatNumber(config.budget),
                        formatNumber(config.minProfit),
                        config.minDemandTier,
                        String.join(", ", config.blacklist)
                );
                if (client.player != null) {
                    client.player.sendMessage(net.minecraft.text.Text.literal(msg), false);
                } else if (client.inGameHud != null) {
                    client.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal(msg));
                }
            }
        }
    }

    private static String formatNumber(long number) {
        if (number >= 1_000_000_000L) {
            return String.format("%.1fB", number / 1_000_000_000.0);
        } else if (number >= 1_000_000L) {
            return String.format("%.1fM", number / 1_000_000.0);
        } else if (number >= 1_000L) {
            return String.format("%.1fK", number / 1_000.0);
        }
        return String.valueOf(number);
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