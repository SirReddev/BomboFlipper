const axios = require('axios');

class BazaarService {
    constructor() {
        this.products = {};
        this.lastUpdate = 0;
    }

    async updateBazaar() {
        // Cache for 60 seconds to respect Hypixel's rate limits
        if (Date.now() - this.lastUpdate < 60000) return;

        try {
            const response = await axios.get('https://api.hypixel.net/v2/skyblock/bazaar');
            if (response.data && response.data.products) {
                this.products = response.data.products;
                this.lastUpdate = Date.now();
                console.log(`[BazaarService] Live prices updated for ${Object.keys(this.products).length} items.`);
            }
        } catch (error) {
            console.error('[BazaarService] Failed to update Bazaar:', error.message);
        }
    }

    getPrice(productId) {
        if (this.products[productId] && this.products[productId].quick_status) {
            // buyPrice represents the "Insta-Buy" price (what it costs to buy the upgrade right now)
            return this.products[productId].quick_status.buyPrice;
        }
        return null;
    }

    getEnchantPrice(enchName, level) {
        if (!enchName || !level) return 0;
        const cleanName = enchName.toLowerCase();

        const candidateKeys = [
            `ENCHANTMENT_${cleanName.toUpperCase()}_${level}`,
            `ENCHANTMENT_ULTIMATE_${cleanName.toUpperCase()}_${level}`,
            `ENCHANTMENT_${cleanName.replace(/^ultimate_/, '').toUpperCase()}_${level}`,
            `ENCHANTMENT_ULTIMATE_${cleanName.replace(/^ultimate_/, '').toUpperCase()}_${level}`
        ];

        for (const key of candidateKeys) {
            const price = this.getPrice(key);
            if (price) return price;
        }

        // Fallback prices for major high-tier enchants if Bazaar product key varies
        const fallbackEnchantPrices = {
            "soul_eater_5": 15000000,
            "overload_5": 6000000,
            "one_for_all_1": 4000000,
            "legion_5": 20000000,
            "chimera_1": 20000000,
            "swarm_5": 5000000,
            "fatal_tempest_5": 15000000,
            "wisdom_5": 2000000
        };

        const fallbackKey = `${cleanName.replace(/^ultimate_/, '')}_${level}`;
        return fallbackEnchantPrices[fallbackKey] || 0;
    }

    getMasterStarPrice(starIndex) {
        const masterStarKeys = {
            1: "FIRST_MASTER_STAR",
            2: "SECOND_MASTER_STAR",
            3: "THIRD_MASTER_STAR",
            4: "FOURTH_MASTER_STAR",
            5: "FIFTH_MASTER_STAR"
        };
        const key = masterStarKeys[starIndex];
        return key ? (this.getPrice(key) || 0) : 0;
    }
}

module.exports = new BazaarService();