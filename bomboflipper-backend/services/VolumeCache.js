const axios = require('axios');
const fs = require('fs');
const path = require('path');
const logger = require('./Logger');

class VolumeCache {
    constructor() {
        this.cacheDir = path.join(__dirname, '../config');
        this.cacheFile = path.join(this.cacheDir, 'volume_cache.json');
        this.cache = new Map();
        
        // Dual-queue system: High Priority for flip candidates, Normal for general items
        this.priorityQueue = new Set();
        this.queue = new Set();
        this.failedAttempts = new Map();

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

    // Instant lookup with auto-queueing
    getVolume(itemTag, isFlipCandidate = false) {
        if (!itemTag) return 0;

        if (this.cache.has(itemTag)) {
            const data = this.cache.get(itemTag);
            // Cache valid for 48 hours
            if (Date.now() - data.timestamp < 172800000) {
                return data.salesPerDay;
            }
        }

        // Add to appropriate queue
        if (isFlipCandidate) {
            this.priorityQueue.add(itemTag);
        } else if (!this.cache.has(itemTag)) {
            this.queue.add(itemTag);
        }

        return 0; // Default fallback for uncached items
    }

    getStatus() {
        return {
            cachedCount: this.cache.size,
            pendingQueue: this.queue.size + this.priorityQueue.size,
            priorityPending: this.priorityQueue.size
        };
    }

    // Smart background crawler: processes priority queue first, 1 request every 1.5 seconds
    async startBackgroundCrawler() {
        let processedSinceSave = 0;

        setInterval(async () => {
            let itemTag = null;

            // Process priority items first
            if (this.priorityQueue.size > 0) {
                itemTag = this.priorityQueue.values().next().value;
                this.priorityQueue.delete(itemTag);
            } else if (this.queue.size > 0) {
                itemTag = this.queue.values().next().value;
                this.queue.delete(itemTag);
            }

            if (!itemTag) return;

            try {
                const response = await axios.get(`https://sky.coflnet.com/api/item/price/${itemTag}/analysis`, {
                    headers: { 'User-Agent': 'BomboFlipper-Crawler/2.0' },
                    timeout: 5000
                });

                const salesPerDay = response.data && typeof response.data.salesPerDay === 'number'
                    ? response.data.salesPerDay
                    : 0;

                this.cache.set(itemTag, {
                    salesPerDay: salesPerDay,
                    timestamp: Date.now()
                });

                this.failedAttempts.delete(itemTag);
                processedSinceSave++;

                // Persist cache to disk every 15 new entries
                if (processedSinceSave >= 15) {
                    this.saveToDisk();
                    processedSinceSave = 0;
                }

            } catch (error) {
                const fails = (this.failedAttempts.get(itemTag) || 0) + 1;
                this.failedAttempts.set(itemTag, fails);

                // Retry up to 3 times, then stop queueing broken item tags
                if (fails <= 3) {
                    this.queue.add(itemTag);
                }
            }
        }, 1500);
    }
}

module.exports = new VolumeCache();