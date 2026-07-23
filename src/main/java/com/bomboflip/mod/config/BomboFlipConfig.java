package com.bomboflip.mod.config;

import com.bomboflip.mod.moul.MoulBomboConfig;
import com.bomboflip.mod.moul.MoulConfigIntegrator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Runtime config that ALWAYS reads live from the MoulConfig GUI instance.
 * This guarantees that GUI changes take effect immediately — no stale cache.
 */
public class BomboFlipConfig {

    private static BomboFlipConfig instance;

    // Singleton instance getter
    public static BomboFlipConfig getInstance() {
        if (instance == null) {
            instance = new BomboFlipConfig();
        }
        return instance;
    }

    // ── Live Getters (always read from MoulConfig GUI instance) ──

    private MoulBomboConfig gui() {
        return MoulConfigIntegrator.getManaged().getInstance();
    }

    public boolean isEnabled() {
        MoulBomboConfig g = gui();
        return g != null ? g.general.enabled : true;
    }

    public boolean isOneClickBuy() {
        MoulBomboConfig g = gui();
        return g != null ? g.general.oneClickBuy : true;
    }

    public boolean isFullAfk() {
        MoulBomboConfig g = gui();
        return g != null ? g.general.fullAfk : false;
    }

    public long getBudget() {
        MoulBomboConfig g = gui();
        return g != null ? MoulConfigIntegrator.parseNumber(g.general.budget, 100000000L) : 100000000L;
    }

    public long getMinProfit() {
        MoulBomboConfig g = gui();
        return g != null ? MoulConfigIntegrator.parseNumber(g.general.minProfit, 500000L) : 500000L;
    }

    public long getMaxProfit() {
        MoulBomboConfig g = gui();
        return g != null ? MoulConfigIntegrator.parseNumber(g.general.maxProfit, 100000000L) : 100000000L;
    }

    public int getMinDemandTier() {
        MoulBomboConfig g = gui();
        return g != null ? g.filters.minDemandTier : 1;
    }

    public boolean isChatAlertsEnabled() {
        MoulBomboConfig g = gui();
        return g != null ? g.alerts.chatAlertsEnabled : true;
    }

    public boolean isSoundAlertsEnabled() {
        MoulBomboConfig g = gui();
        return g != null ? g.alerts.soundAlertsEnabled : true;
    }

    public boolean isDebugMode() {
        MoulBomboConfig g = gui();
        return g != null ? g.alerts.debugMode : false;
    }

    public boolean isShowAllFlips() {
        MoulBomboConfig g = gui();
        return g != null ? g.alerts.showAllFlips : false;
    }

    public List<String> getBlacklist() {
        MoulBomboConfig g = gui();
        List<String> list = new ArrayList<>();
        if (g != null && g.filters.blacklist != null) {
            for (String s : g.filters.blacklist.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) list.add(t);
            }
        }
        return list;
    }

    // ── Legacy field accessors (for backwards compat with commands) ──

    public boolean enabled = true;
    public boolean oneClickBuy = true;
    public boolean fullAfk = false;
    public long budget = 100000000L;
    public long minProfit = 500000L;
    public long maxProfit = 50000000L;
    public int minDemandTier = 1;
    public boolean chatAlertsEnabled = true;
    public boolean soundAlertsEnabled = true;
    public boolean debugMode = false;
    public boolean showAllFlips = false;
    public List<String> blacklist = new ArrayList<>(Arrays.asList("Skin", "Dye", "Rune"));

    // Load from JSON
    public static void load() {
        getInstance();
    }

    public static void syncWithMoul() {
        // No-op: we now read live from MoulConfig
    }

    // Save (push command-set values to GUI and persist)
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
            gui.filters.blacklist = "Skin, Dye, Rune, Travel Scroll, Furniture, Cake, Minion Skin, Pet Skin, Firework, Banner, Balloon, Bucket, Gauntlet, Blossom Cloak, Bits Talisman";
            gui.alerts.chatAlertsEnabled = true;
            gui.alerts.soundAlertsEnabled = true;
            gui.alerts.debugMode = false;
            gui.alerts.showAllFlips = false;

            MoulConfigIntegrator.save();
        }
    }
}