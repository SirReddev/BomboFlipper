const propertiesSelector = require('./PropertiesSelector');
const bazaarService = require('./BazaarService');
const volumeCache = require('./VolumeCache');
const logger = require('./Logger');

class FlippingEngine {
    constructor() {
        this.minProfitThreshold = 1000000;
        this.minMarginRatio = 0.10;

        // Depreciation ratios for applied upgrades
        this.upgradeDepreciation = {
            recombobulator: 0.50,
            hotPotato: 0.50,
            fumingPotato: 0.50,
            enchants: 0.40
        };
    }

    async evaluateAuctions(auctions) {
        const binAuctions = auctions.filter(a => a.bin === true);
        const lowestBinMap = new Map();
        const processedAuctions = [];

        // Step 1: Decode NBT and build the AH baseline index
        await Promise.all(binAuctions.map(async (auction) => {
            const extractedProps = await propertiesSelector.extractProperties(auction.item_bytes);

            const processed = {
                uuid: auction.uuid,
                itemName: auction.item_name,
                startingBid: auction.starting_bid,
                skyblockId: extractedProps && extractedProps.skyblockId ? extractedProps.skyblockId : this.fallbackSkyblockId(auction.item_name),
                properties: extractedProps
            };

            processedAuctions.push(processed);

            // We only track actual gear/weapons in the AH index now, skip books
            if (processed.skyblockId && processed.skyblockId !== "ENCHANTED_BOOK") {
                const currentLowest = lowestBinMap.get(processed.skyblockId) || Infinity;
                if (processed.startingBid < currentLowest) {
                    lowestBinMap.set(processed.skyblockId, processed.startingBid);
                }
            }
        }));

        const identifiedFlips = [];

        // Step 2: Evaluate items combining AH baseline + Bazaar upgrade values
        for (const item of processedAuctions) {
            if (!item.skyblockId || item.skyblockId === "ENCHANTED_BOOK") continue;

            const baseLowestBin = lowestBinMap.get(item.skyblockId);
            if (!baseLowestBin || baseLowestBin <= 0) continue;

            const propertyBonus = this.calculateLiveUpgradeValue(item.properties);

            const estimatedTrueValue = baseLowestBin + propertyBonus;
            const projectedProfit = estimatedTrueValue - item.startingBid;
            const marginRatio = projectedProfit / item.startingBid;

            if (projectedProfit >= this.minProfitThreshold && marginRatio >= this.minMarginRatio) {

                const salesPerDay = volumeCache.getVolume(item.skyblockId);

                let demandTier = 1; // ANY
                if (salesPerDay >= 100) demandTier = 5;      // VERY HIGH
                else if (salesPerDay >= 40) demandTier = 4;  // HIGH
                else if (salesPerDay >= 10) demandTier = 3;  // MEDIUM
                else if (salesPerDay >= 3) demandTier = 2;   // LOW

                let displayName = item.itemName;
                if (!displayName || !displayName.trim()) {
                    displayName = item.skyblockId.replace(/_/g, " ");
                    logger.info(`Missing item_name for auction ${item.uuid}, falling back to skyblockId: ${item.skyblockId}`);
                }

                identifiedFlips.push({
                    type: "flip",
                    item: displayName,
                    uuid: item.uuid,
                    price: item.startingBid,
                    estimatedValue: estimatedTrueValue,
                    profit: projectedProfit,
                    command: `/viewauction ${item.uuid}`,
                    demandTier: demandTier,
                    salesPerDay: salesPerDay
                });
            }
        }

        // --- ORGANIZED LOGGING ---
        logger.info(`Evaluated ${processedAuctions.length} BIN items. Flips Found: ${identifiedFlips.length}. Cache Queue: ${volumeCache.queue.size} pending.`);

        for (const flip of identifiedFlips) {
            // Safe fallback prevents 'undefined' string printing bugs
            const itemNameString = flip.item || "Unknown Item";
            const nameCol = (flip.item || "Unknown Item").padEnd(35).substring(0, 35);
            const priceCol = logger.formatNumber(flip.price).padEnd(12);
            const profitCol = logger.formatNumber(flip.profit).padEnd(11);
            const salesCol = logger.formatNumber(Math.round(flip.salesPerDay)).padEnd(5);

            logger.flip(`[Tier ${flip.demandTier}] ${nameCol} | Cost: ${priceCol} | Profit: ${profitCol} | Sales/Day: ${salesCol}`);
        }

        return {
            totalEvaluated: processedAuctions.length,
            flips: identifiedFlips
        };
    }

    calculateLiveUpgradeValue(properties) {
        if (!properties) return 0;
        let bonusValue = 0;

        // Pull Recombobulator directly from Bazaar
        if (properties.recombobulated) {
            const recombLivePrice = bazaarService.getPrice("RECOMBOBULATOR_3000") || 5000000;
            bonusValue += (recombLivePrice * this.upgradeDepreciation.recombobulator);
        }

        // Pull Potato Books directly from Bazaar
        if (properties.hotPotatoBooks > 0) {
            const hpb = Math.min(properties.hotPotatoBooks, 10);
            const fuming = Math.max(0, properties.hotPotatoBooks - 10);

            const hpbLivePrice = bazaarService.getPrice("HOT_POTATO_BOOK") || 30000;
            const fumingLivePrice = bazaarService.getPrice("FUMING_POTATO_BOOK") || 1000000;

            bonusValue += (hpb * hpbLivePrice * this.upgradeDepreciation.hotPotato);
            bonusValue += (fuming * fumingLivePrice * this.upgradeDepreciation.fumingPotato);
        }

        // Pull Enchantment Books directly from Bazaar
        for (const [ench, level] of Object.entries(properties.enchantments)) {
            // Hypixel Bazaar formats enchants like: ENCHANTMENT_SOUL_EATER_5
            const enchantKey = `ENCHANTMENT_${ench.toUpperCase()}_${level}`;
            const enchantLivePrice = bazaarService.getPrice(enchantKey);

            if (enchantLivePrice) {
                bonusValue += (enchantLivePrice * this.upgradeDepreciation.enchants);
            }
        }

        return Math.floor(bonusValue);
    }

    fallbackSkyblockId(rawName) {
        return rawName.replace(/[✪⍟]/g, "").trim().toUpperCase().replace(/\s+/g, "_");
    }
}

module.exports = new FlippingEngine();