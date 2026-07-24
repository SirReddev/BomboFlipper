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

    public int getAutoBuyDelay() {
        MoulBomboConfig g = gui();
        return g != null ? g.general.autoBuyDelay : 400;
    }

    public int getRelistDelay() {
        MoulBomboConfig g = gui();
        return g != null ? g.general.relistDelay : 600;
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

    // ── Live Setters (commands write directly to MoulConfig GUI instance) ──

    public void setEnabled(boolean val) {
        MoulBomboConfig g = gui();
        if (g != null) g.general.enabled = val;
    }

    public void setOneClickBuy(boolean val) {
        MoulBomboConfig g = gui();
        if (g != null) g.general.oneClickBuy = val;
    }

    public void setFullAfk(boolean val) {
        MoulBomboConfig g = gui();
        if (g != null) g.general.fullAfk = val;
    }

    public void setBudget(long val) {
        MoulBomboConfig g = gui();
        if (g != null) g.general.budget = String.valueOf(val);
    }

    public void setMinProfit(long val) {
        MoulBomboConfig g = gui();
        if (g != null) g.general.minProfit = String.valueOf(val);
    }

    public void setMaxProfit(long val) {
        MoulBomboConfig g = gui();
        if (g != null) g.general.maxProfit = String.valueOf(val);
    }

    public void setMinDemandTier(int val) {
        MoulBomboConfig g = gui();
        if (g != null) g.filters.minDemandTier = val;
    }

    public void setChatAlertsEnabled(boolean val) {
        MoulBomboConfig g = gui();
        if (g != null) g.alerts.chatAlertsEnabled = val;
    }

    public void setSoundAlertsEnabled(boolean val) {
        MoulBomboConfig g = gui();
        if (g != null) g.alerts.soundAlertsEnabled = val;
    }

    public void setDebugMode(boolean val) {
        MoulBomboConfig g = gui();
        if (g != null) g.alerts.debugMode = val;
    }

    public void setShowAllFlips(boolean val) {
        MoulBomboConfig g = gui();
        if (g != null) g.alerts.showAllFlips = val;
    }

    public void setBlacklist(List<String> list) {
        MoulBomboConfig g = gui();
        if (g != null) g.filters.blacklist = String.join(", ", list);
    }

    public void addToBlacklist(String item) {
        List<String> bl = getBlacklist();
        if (!bl.contains(item)) {
            bl.add(item);
            setBlacklist(bl);
        }
    }

    public void removeFromBlacklist(String item) {
        List<String> bl = getBlacklist();
        if (bl.remove(item)) {
            setBlacklist(bl);
        }
    }

    // Load from JSON
    public static void load() {
        getInstance();
        MoulConfigIntegrator.getManaged(); // Force init on main thread to prevent async ServiceLoader crashes
    }

    public static void syncWithMoul() {
        // No-op
    }

    // Save (persist current GUI fields to disk)
    public void save() {
        MoulConfigIntegrator.save();
    }

    public void resetToDefaults() {
        MoulBomboConfig gui = MoulConfigIntegrator.getManaged().getInstance();
        if (gui != null) {
            gui.general.enabled = true;
            gui.general.oneClickBuy = true;
            gui.general.fullAfk = false;
            gui.general.autoBuyDelay = 400;
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