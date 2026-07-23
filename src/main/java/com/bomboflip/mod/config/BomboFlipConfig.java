package com.bomboflip.mod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BomboFlipConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "bomboflip.json");

    private static BomboFlipConfig instance;

    // --- YOUR ACTIVE SETTINGS ---
    public boolean enabled = true;
    public long budget = 100000000L; // 100m default
    public long minProfit = 500000L; // 500k default
    public long maxProfit = 50000000L; // 50m default
    public int minDemandTier = 3; // 1-5 scale
    public boolean chatAlertsEnabled = true;
    public boolean soundAlertsEnabled = true;
    public boolean debugMode = false;

    /**
     * The backend re-evaluates the whole AH on every poll and re-broadcasts
     * a "flip" message for every auction still profitable - including ones
     * already announced last cycle. False (default) = only announce a given
     * auction UUID once. True = re-announce it every time it's re-broadcast
     * (floods chat, but useful for debugging the backend feed).
     */
    public boolean showAllFlips = false;

    public List<String> blacklist = new ArrayList<>(Arrays.asList("Skin", "Dye", "Rune"));

    // Singleton instance getter
    public static BomboFlipConfig getInstance() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    // Load from JSON
    public static void load() {
        if (FILE.exists()) {
            try (FileReader reader = new FileReader(FILE)) {
                instance = GSON.fromJson(reader, BomboFlipConfig.class);
            } catch (Exception e) {
                System.err.println("[BomboFlipper] Failed to load config, generating new one...");
                instance = new BomboFlipConfig();
            }
        } else {
            instance = new BomboFlipConfig();
        }
        // Save automatically handles migrating old configs by stripping removed variables
        // and adding missing default ones.
        instance.save();
    }

    // Save to JSON
    public void save() {
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(this, writer);
        } catch (Exception e) {
            System.err.println("[BomboFlipper] Failed to save config!");
        }

        // Bi-directional sync: push changes to MoulConfig GUI instance
        try {
            com.bomboflip.mod.moul.MoulConfigIntegrator.pullFromRuntimeConfig();
        } catch (Exception ignored) {}
    }
}