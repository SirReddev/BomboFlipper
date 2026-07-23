package com.bomboflip.mod.config;

import com.bomboflip.mod.moul.MoulBomboConfig;
import com.bomboflip.mod.moul.MoulConfigIntegrator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BomboFlipConfig {

    private static BomboFlipConfig instance;

    // --- YOUR ACTIVE SETTINGS ---
    public boolean enabled = true;
    public boolean oneClickBuy = true;
    public boolean fullAfk = false;
    public long budget = 100000000L; // 100m default
    public long minProfit = 500000L; // 500k default
    public long maxProfit = 50000000L; // 50m default
    public int minDemandTier = 1; // 1-5 scale
    public boolean chatAlertsEnabled = true;
    public boolean soundAlertsEnabled = true;
    public boolean debugMode = false;
    public boolean showAllFlips = false;
    public List<String> blacklist = new ArrayList<>(Arrays.asList("Skin", "Dye", "Rune"));

    // Singleton instance getter
    public static BomboFlipConfig getInstance() {
        if (instance == null) {
            instance = new BomboFlipConfig();
            syncWithMoul();
        }
        return instance;
    }

    // Load from JSON
    public static void load() {
        getInstance();
        syncWithMoul();
    }

    public static void syncWithMoul() {
        if (instance == null) instance = new BomboFlipConfig();
        MoulBomboConfig gui = MoulConfigIntegrator.getManaged().getInstance();
        if (gui == null) return;

        instance.enabled = gui.general.enabled;
        instance.oneClickBuy = gui.general.oneClickBuy;
        instance.fullAfk = gui.general.fullAfk;
        instance.budget = MoulConfigIntegrator.parseNumber(gui.general.budget, instance.budget);
        instance.minProfit = MoulConfigIntegrator.parseNumber(gui.general.minProfit, instance.minProfit);
        instance.maxProfit = MoulConfigIntegrator.parseNumber(gui.general.maxProfit, instance.maxProfit);
        instance.minDemandTier = gui.filters.minDemandTier;
        instance.chatAlertsEnabled = gui.alerts.chatAlertsEnabled;
        instance.soundAlertsEnabled = gui.alerts.soundAlertsEnabled;
        instance.debugMode = gui.alerts.debugMode;
        instance.showAllFlips = gui.alerts.showAllFlips;

        instance.blacklist.clear();
        if (gui.filters.blacklist != null) {
            for (String s : gui.filters.blacklist.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) instance.blacklist.add(t);
            }
        }
    }

    // Save to JSON
    public void save() {
        MoulBomboConfig gui = MoulConfigIntegrator.getManaged().getInstance();
        if (gui != null) {
            gui.general.enabled = this.enabled;
            gui.general.oneClickBuy = this.oneClickBuy;
            gui.general.fullAfk = this.fullAfk;
            gui.general.budget = String.valueOf(this.budget);
            gui.general.minProfit = String.valueOf(this.minProfit);
            gui.general.maxProfit = String.valueOf(this.maxProfit);
            gui.filters.minDemandTier = this.minDemandTier;
            gui.filters.blacklist = String.join(", ", this.blacklist);
            gui.alerts.chatAlertsEnabled = this.chatAlertsEnabled;
            gui.alerts.soundAlertsEnabled = this.soundAlertsEnabled;
            gui.alerts.debugMode = this.debugMode;
            gui.alerts.showAllFlips = this.showAllFlips;

            MoulConfigIntegrator.save();
        }
    }

    public void resetToDefaults() {
        MoulBomboConfig gui = MoulConfigIntegrator.getManaged().getInstance();
        if (gui != null) {
            gui.general.enabled = true;
            gui.general.oneClickBuy = true;
            gui.general.fullAfk = false;
            gui.general.budget = "100000000";
            gui.general.minProfit = "500000";
            gui.general.maxProfit = "100000000";
            gui.filters.minDemandTier = 2;
            gui.filters.blacklist = "Skin, Dye, Rune, Travel Scroll, Furniture, Cake, Minion Skin, Pet Skin, Firework, Banner, Balloon, Bucket";
            gui.alerts.chatAlertsEnabled = true;
            gui.alerts.soundAlertsEnabled = true;
            gui.alerts.debugMode = false;
            gui.alerts.showAllFlips = false;

            MoulConfigIntegrator.save();
        }
    }
}