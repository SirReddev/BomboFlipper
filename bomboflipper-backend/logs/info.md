# BomboFlipper Log Files Reference

### 1. `flips_[date].log` (Green Output)
* **What it stores:** Exclusively records the profitable items caught by your flipping engine that pass all profit and margin thresholds.
* **Expanded details:** Every time a profitable flip is found, it logs a formatted row containing the demand tier, padded item name, buy cost, projected profit, and estimated daily sales volume (e.g., `[Tier 5] Mythic Bouncy Leggings | Cost: 129,000 | Profit: 5,301,211 | Sales/Day: 999`).

### 2. `cache_[date].log` (Cyan Output)
* **What it stores:** Captures the activity of your background volume crawler as it builds and updates your demand database.
* **Expanded details:** It records individual mapping requests—showing the item ID being checked, its resolved sales-per-day, the calculated demand tier (1 through 5), and periodic queue progress metrics so you can track how fast your cache is filling up.

### 3. `system_[date].log` (White Output)
* **What it stores:** Standard informational messages regarding the server's operational loop.
* **Expanded details:** Records cycle summaries, such as when the engine initializes, how many total BIN items were evaluated during a cycle, how long the valuation took in milliseconds, and total active auctions retrieved from Hypixel.

### 4. `errors_[date].log` (Red Output)
* **What it stores:** Catches runtime exceptions and warning traces.
* **Expanded details:** Logs network timeouts, unhandled API fetch drops from CoflNet or Hypixel, or file write issues so you can easily debug unexpected crashes without digging through unrelated terminal logs.