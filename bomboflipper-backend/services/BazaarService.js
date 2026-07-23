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
}

module.exports = new BazaarService();