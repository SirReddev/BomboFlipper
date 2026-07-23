![BomboFlipper Banner](src/main/resources/assets/bomboflip/textures/gui/banner.png)

# BomboFlipper
A high-performance, hybrid Hypixel Skyblock Auction House sniper and valuation engine for Minecraft 1.21 (Fabric).

BomboFlipper uses a split-architecture design to guarantee zero client lag. By offloading heavy NBT decoding, entire Auction House indexing, and live Bazaar price calculations to a background Node.js service, your client stays at peak FPS while profitable flips are beamed instantly to your chat via a local WebSocket.

---

## 🛠️ How It Works (Split Architecture)

BomboFlipper operates in two distinct parts working together in real time:

1. **The Backend Engine (Node.js):**
    * **Bazaar Sync:** Fetches real-time prices from the Hypixel Bazaar API every 60 seconds.
    * **AH Scan & Indexing:** Pulls all active BIN auctions from Hypixel and establishes a baseline **Lowest BIN (LBIN)** for every clean item type.
    * **NBT Upgrade Valuation:** Extracts item properties (Recombobulators, Hot/Fuming Potato Books, Enchants) and calculates their live value using real-time Bazaar upgrade costs.
    * **Volume & Demand Analysis:** Evaluates daily sales volume via volume caching and assigns each item a **Demand Tier (1–5)**.
    * **WebSocket Broadcast:** Pushes qualifying flips over `ws://127.0.0.1:8080`.

2. **The Client Mod (Fabric 1.21):**
    * Connects to the local WebSocket service.
    * Applies client-side filters (Budget, Min/Max Profit, Minimum Demand Tier, Blacklist).
    * Deduplicates incoming auctions using a bounded LRU cache so you never see duplicate alerts for the same listing.
    * Formats the flip into interactive, click-to-buy chat alerts.

---

## 💎 Live Bazaar Upgrade Valuation Engine

Standard flipping mods often miss lucrative flips or output false positives because they only look at raw LBIN without accounting for applied item modifications. BomboFlipper solves this by combining Auction House baselines with live Bazaar pricing for applied upgrades.

### How True Estimated Value is Calculated:
$$\text{Estimated Value} = \text{Base LBIN} + \text{Live Upgrade Value}$$

The backend dynamically checks the Bazaar for current upgrade costs and applies depreciation multipliers to simulate true market resale value:

* **Recombobulators:** Adds **50%** of live Bazaar `RECOMBOBULATOR_3000` price.
* **Hot Potato Books (1–10):** Adds **50%** of live Bazaar `HOT_POTATO_BOOK` price per book.
* **Fuming Potato Books (11–15):** Adds **50%** of live Bazaar `FUMING_POTATO_BOOK` price per book.
* **Applied Enchantments:** Looks up the exact tier in the Bazaar (e.g., `ENCHANTMENT_SOUL_EATER_5`) and adds **40%** of its live market value.

### Profit Qualification Requirements:
* **Projected Profit:** $(\text{Estimated Value} - \text{Price}) \ge \text{minProfit}$ (Default: 1,000,000 coins)
* **Margin Ratio:** $\frac{\text{Projected Profit}}{\text{Price}} \ge 10\%$

---

## 📊 Demand Tier System

To prevent you from buying "fake flips" on items that take weeks to sell, BomboFlipper calculates average sales per day (`salesPerDay`) and categorizes every item into a Demand Tier:

| Tier | Demand Level | Daily Sales Volume |
| :--- | :--- | :--- |
| **Tier 5** | Very High | $\ge 100$ sales / day |
| **Tier 4** | High | $\ge 40$ sales / day |
| **Tier 3** | Medium | $\ge 10$ sales / day |
| **Tier 2** | Low | $\ge 3$ sales / day |
| **Tier 1** | Any | $< 3$ sales / day |

You can set your minimum allowed demand tier in-game via `/bomboflipper minDemandTier <1-5>` or in the GUI.

---

## ✨ Key Features

* **Zero Client Overhead:** All HTTP fetching, GZIP decompression, and JSON parsing happen in Node.js.
* **Smart Bazaar-AH Hybrid Math:** Automatically detects underpriced items packed with expensive Bazaar upgrades.
* **Auction Deduplication:** Bounded LRU cache ensures auction UUIDs are only announced once per session.
* **MoulConfig GUI & Live Commands:** Change settings visually or via commands; settings automatically sync seamlessly.
* **Developer Debug Bridge:** Toggle `/bomboflipper debugMode true` to view backend engine status, auction cycle runtimes, and detailed demand stats (`Tier` + `Sales/Day`) directly in Minecraft chat.

---

## 📥 Installation & Setup

### Step 1: Launch the Backend (Node.js)
1. Install [Node.js](https://nodejs.org/) (v18 or higher recommended).
2. Open your terminal in the `bomboflipper-backend` directory.
3. Run `npm install` to install required dependencies (`ws`, `axios`).
4. Run `node server.js` to start the local engine on port `8080`. Leave this console open while playing.

### Step 2: Install the Minecraft Mod
1. Install [Fabric Loader](https://fabricmc.net/) for Minecraft 1.21.
2. Place the compiled `bomboflip.jar` and `moulconfig.jar` into your `.minecraft/mods` folder.
3. Launch Minecraft and join Hypixel Skyblock!

---

## ⚙️ Command Reference

Commands allow instant adjustments to your filters without opening menus.

### Core Commands
* `/bomboflipper` - Opens the visual MoulConfig menu.
* `/bomboflipper enable <true/false>` - Enables or disables the sniper.
* `/bomboflipper chatAlerts <true/false>` - Toggle chat notification display.
* `/bomboflipper soundAlerts <true/false>` - Toggle alert sound playback.

### Financial & Quality Filters
* `/bomboflipper budget <amount>` - Set maximum purchase budget (e.g., `50000000`).
* `/bomboflipper minProfit <amount>` - Set minimum profit threshold (e.g., `1000000`).
* `/bomboflipper maxProfit <amount>` - Set maximum profit limit to avoid bait (Set `0` for unlimited).
* `/bomboflipper minDemandTier <1-5>` - Only notify flips meeting or exceeding this demand tier.

### Blacklist Management
* `/bomboflipper blacklist add <item>` - Blacklist items containing specific keywords.
* `/bomboflipper blacklist remove <item>` - Remove a keyword from your blacklist.
* `/bomboflipper blacklist clear` - Clear all blacklisted keywords.

### Developer Debug Mode
* `/bomboflipper debugMode <true/false>` - Toggle live backend logs, demand tier ratings, and sales/day metrics in chat.
