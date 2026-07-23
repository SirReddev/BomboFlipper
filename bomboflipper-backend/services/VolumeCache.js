const axios = require('axios');
const fs = require('fs');
const path = require('path');
const logger = require('./Logger');

class VolumeCache {
    constructor() {
        this.cacheDir = path.join(__dirname, '../config');
        this.cacheFile = path.join(this.cacheDir, 'volume_cache.json');
        this.cache = new Map();
        this.queue = new Set();

        this.loadFromDisk();
        this.startBackgroundCrawler();
    }

    // Load saved volume data from local disk on boot
    loadFromDisk() {
        try {
            if (!fs.existsSync(this.cacheDir)) {
                fs.mkdirSync(this.cacheDir, { recursive: true });
            }

            if (fs.existsSync(this.cacheFile)) {
                const rawData = fs.readFileSync(this.cacheFile, 'utf8');
                const parsed = JSON.parse(rawData);
                for (const [key, value] of Object.entries(parsed)) {
                    this.cache.set(key, value);
                }
                logger.cache(`Loaded ${this.cache.size} cached volume records from local disk.`);
            }
        } catch (error) {
            logger.error(`Failed to load volume cache from disk: ${error.message}`);
        }
    }

    // Save current RAM cache to local disk
    saveToDisk() {
        try {
            const obj = Object.fromEntries(this.cache);
            fs.writeFileSync(this.cacheFile, JSON.stringify(obj, null, 2), 'utf8');
        } catch (error) {
            logger.error(`Failed to save volume cache to disk: ${error.message}`);
        }
    }

    // The FlippingEngine calls this. It is 100% instant.
    getVolume(itemTag) {
        if (!itemTag) return 0;

        if (this.cache.has(itemTag)) {
            const data = this.cache.get(itemTag);
            // Return cached volume if it's less than 48 hours old
            if (Date.now() - data.timestamp < 172800000) {
                return data.salesPerDay;
            }
        }

        // If not cached or too old, add to the crawler's to-do list
        this.queue.add(itemTag);

        // Return 0 (lowest/ANY tier) rather than a high number, so items
        // the background crawler hasn't gotten to yet show up as
        // "unconfirmed demand" instead of being mislabeled VERY HIGH -
        // guessing optimistically here could mislead you into buying
        // something that's actually illiquid.
        return 0;
    }

    // Runs silently in the background, 1 request every 2 seconds
    async startBackgroundCrawler() {
        let scannedCount = 0;

        setInterval(async () => {
            if (this.queue.size === 0) return;

            const itemTag = this.queue.values().next().value;
            this.queue.delete(itemTag);

            try {
                const response = await axios.get(`https://sky.coflnet.com/api/item/price/${itemTag}/analysis`, {
                    headers: { 'User-Agent': 'BomboFlipper-Crawler/1.0' }
                });

                const salesPerDay = response.data.salesPerDay || 0;

                this.cache.set(itemTag, {
                    salesPerDay: salesPerDay,
                    timestamp: Date.now()
                });

                scannedCount++;

                // Neatly report progress every 10 items and save to disk
                if (scannedCount % 10 === 0) {
                    logger.cache(`Background Crawler mapped 10 items. Remaining in queue: ${this.queue.size}`);
                    this.saveToDisk(); // Persist progress safely
                }

            } catch (error) {
                this.queue.add(itemTag); // Re-queue on failure
            }
        }, 2000);
    }
}

module.exports = new VolumeCache();