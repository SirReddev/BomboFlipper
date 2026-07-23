const axios = require('axios');

class GemPriceService {
    constructor() {
        this.cookieCoinPrice = 10000000; 
        this.gemPerCookie = 325;         
        this.lastUpdate = 0;
    }

    async updateBazaarPrices() {
        if (Date.now() - this.lastUpdate < 300000) return;

        try {
            const response = await axios.get('https://api.hypixel.net/v2/skyblock/bazaar');
            if (response.data && response.data.products && response.data.products['BOOSTER_COOKIE']) {
                const cookieData = response.data.products['BOOSTER_COOKIE'].quick_status;
                this.cookieCoinPrice = cookieData.sellPrice || cookieData.buyPrice || 10000000;
                this.lastUpdate = Date.now();
            }
        } catch (error) {}
    }

    getCoinsPerGem() {
        return this.cookieCoinPrice / this.gemPerCookie;
    }

    getSkinValueFromGems(gemCost) {
        return Math.floor(gemCost * this.getCoinsPerGem());
    }
}

module.exports = new GemPriceService();