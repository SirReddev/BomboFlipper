const propertiesSelector = require('./PropertiesSelector');
const bazaarService = require('./BazaarService');
const volumeCache = require('./VolumeCache');
const logger = require('./Logger');

class FlippingEngine {
    constructor() {
        this.minProfitThreshold = 1000000;
        this.minMarginRatio = 0.10;
        this.ahTaxFee = 0.015; // 1.5% Hypixel AH Listing Fee / Tax

        // Base depreciation ratios for applied upgrades
        this.upgradeDepreciation = {
            recombobulator: 0.50,
            hotPotato: 0.50,
            fumingPotato: 0.50,
            artOfWar: 0.50,
            masterStar: 0.50,
            enchants: 0.40
        };
    }

    async evaluateAuctions(auctions) {
        const binAuctions = auctions.filter(a => a.bin === true);
        const processedAuctions = [];

        // Maps to group auctions for dual-path comparison
        const cleanListingsMap = new Map();
        const variantListingsMap = new Map();
        const allListingsMap = new Map();

        // Step 1: Decode NBT and build multi-BIN indices
        await Promise.all(binAuctions.map(async (auction) => {
            const extractedProps = await propertiesSelector.extractProperties(auction.item_bytes);

            const skyblockId = extractedProps && extractedProps.skyblockId
                ? extractedProps.skyblockId
                : this.fallbackSkyblockId(auction.item_name);

            if (!skyblockId || skyblockId === "ENCHANTED_BOOK") return;

            const isClean = this.isCleanItem(extractedProps);
            const variantKey = this.getVariantKey(skyblockId, extractedProps);

            const processed = {
                uuid: auction.uuid,
                itemName: auction.item_name,
                startingBid: auction.starting_bid,
                skyblockId: skyblockId,
                properties: extractedProps,
                isClean: isClean,
                variantKey: variantKey
            };

            processedAuctions.push(processed);

            // Group into All Listings Map
            if (!allListingsMap.has(skyblockId)) {
                allListingsMap.set(skyblockId, []);
            }
            allListingsMap.get(skyblockId).push(processed);

            // Group into Clean Listings Map
            if (isClean) {
                if (!cleanListingsMap.has(skyblockId)) {
                    cleanListingsMap.set(skyblockId, []);
                }
                cleanListingsMap.get(skyblockId).push(processed);
            }

            // Group into Variant Listings Map
            if (!isClean && variantKey) {
                if (!variantListingsMap.has(variantKey)) {
                    variantListingsMap.set(variantKey, []);
                }
                variantListingsMap.get(variantKey).push(processed);
            }
        }));

        // Sort all maps by startingBid ascending
        for (const list of allListingsMap.values()) {
            list.sort((a, b) => a.startingBid - b.startingBid);
        }
        for (const list of cleanListingsMap.values()) {
            list.sort((a, b) => a.startingBid - b.startingBid);
        }
        for (const list of variantListingsMap.values()) {
            list.sort((a, b) => a.startingBid - b.startingBid);
        }

        const identifiedFlips = [];

        // Step 2: Dual-Path Evaluation with Self-Exclusion
        for (const item of processedAuctions) {
            // Comparable lists excluding the target item itself (Self-Exclusion)
            const otherClean = (cleanListingsMap.get(item.skyblockId) || []).filter(a => a.uuid !== item.uuid);
            const otherVariant = item.variantKey ? (variantListingsMap.get(item.variantKey) || []).filter(a => a.uuid !== item.uuid) : [];
            const otherAll = (allListingsMap.get(item.skyblockId) || []).filter(a => a.uuid !== item.uuid);

            let targetResalePrice = 0;
            let estimatedNetProfit = 0;

            if (!item.isClean && otherVariant.length > 0) {
                // PATH 1: Upgraded Variant Direct Comparison
                const lowestOtherVariantBin = otherVariant[0].startingBid;

                // CRITICAL FIX: If another identical upgraded item is already listed cheaper or equal, REJECT
                if (item.startingBid >= lowestOtherVariantBin) {
                    continue;
                }

                targetResalePrice = lowestOtherVariantBin;
                estimatedNetProfit = (targetResalePrice * (1 - this.ahTaxFee)) - item.startingBid;

            } else if (item.isClean && otherClean.length > 0) {
                // PATH 2: Clean Item Comparison (Item is compared against 2nd Lowest BIN)
                const lowestOtherCleanBin = otherClean[0].startingBid;

                if (item.startingBid >= lowestOtherCleanBin) {
                    continue;
                }

                targetResalePrice = lowestOtherCleanBin;
                estimatedNetProfit = (targetResalePrice * (1 - this.ahTaxFee)) - item.startingBid;

            } else if (!item.isClean && otherVariant.length === 0) {
                // PATH 3: Upgraded Fallback Valuation (No identical variant on AH)
                const baseFloor = otherClean.length > 0 ? otherClean[0].startingBid : (otherAll.length > 0 ? otherAll[0].startingBid : null);
                if (!baseFloor || baseFloor <= 0) continue;

                const rawUpgradeBonus = this.calculateLiveUpgradeValue(item.properties);

                // Scaled Upgrade Depreciation Cap relative to base item price
                let retentionCapRatio = 0.50;
                if (baseFloor < 1000000) {
                    retentionCapRatio = 0.25; // Low-tier gear caps upgrade credit at 25% of base price
                } else if (baseFloor > 10000000) {
                    retentionCapRatio = 0.75;
                }

                const scaledUpgradeBonus = Math.min(rawUpgradeBonus, baseFloor * retentionCapRatio);
                const estimatedTrueValue = baseFloor + scaledUpgradeBonus;

                if (item.startingBid >= estimatedTrueValue) {
                    continue;
                }

                targetResalePrice = estimatedTrueValue;
                estimatedNetProfit = (targetResalePrice * (1 - this.ahTaxFee)) - item.startingBid;
            } else {
                continue;
            }

            const marginRatio = estimatedNetProfit / item.startingBid;

            if (estimatedNetProfit >= this.minProfitThreshold && marginRatio >= this.minMarginRatio) {
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
                    estimatedValue: Math.floor(targetResalePrice),
                    profit: Math.floor(estimatedNetProfit),
                    command: `/viewauction ${item.uuid}`,
                    demandTier: demandTier,
                    salesPerDay: salesPerDay
                });
            }
        }

        // --- ORGANIZED LOGGING ---
        logger.info(`Evaluated ${processedAuctions.length} BIN items. Flips Found: ${identifiedFlips.length}. Cache Queue: ${volumeCache.queue.size} pending.`);

        for (const flip of identifiedFlips) {
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

    isCleanItem(props) {
        if (!props) return true;
        if (props.recombobulated) return false;
        if (props.dungeonStars > 0 || props.masterStars > 0) return false;
        if (props.hotPotatoBooks > 0) return false;
        if (props.artOfWar) return false;

        if (props.enchantments) {
            for (const [ench, level] of Object.entries(props.enchantments)) {
                if (level >= 5 || ench.toLowerCase().includes("ultimate") || ench.toLowerCase().includes("soul_eater") || ench.toLowerCase().includes("overload")) {
                    return false;
                }
            }
        }
        return true;
    }

    getVariantKey(skyblockId, props) {
        if (!props) return skyblockId;

        const recomb = props.recombobulated ? '1' : '0';
        const stars = props.dungeonStars || 0;
        const master = props.masterStars || 0;

        const keyEnchants = [];
        if (props.enchantments) {
            for (const [ench, level] of Object.entries(props.enchantments)) {
                if (level >= 5 || ench.toLowerCase().includes("ultimate") || ench.toLowerCase().includes("soul_eater") || ench.toLowerCase().includes("overload")) {
                    keyEnchants.push(`${ench.toLowerCase()}:${level}`);
                }
            }
        }
        keyEnchants.sort();

        return `${skyblockId}|r:${recomb}|s:${stars}|m:${master}|e:${keyEnchants.join(',')}`;
    }

    calculateLiveUpgradeValue(properties) {
        if (!properties) return 0;
        let bonusValue = 0;

        // Recombobulator 3000
        if (properties.recombobulated) {
            const recombLivePrice = bazaarService.getPrice("RECOMBOBULATOR_3000") || 5000000;
            bonusValue += (recombLivePrice * this.upgradeDepreciation.recombobulator);
        }

        // Hot & Fuming Potato Books
        if (properties.hotPotatoBooks > 0) {
            const hpb = Math.min(properties.hotPotatoBooks, 10);
            const fuming = Math.max(0, properties.hotPotatoBooks - 10);

            const hpbLivePrice = bazaarService.getPrice("HOT_POTATO_BOOK") || 30000;
            const fumingLivePrice = bazaarService.getPrice("FUMING_POTATO_BOOK") || 1000000;

            bonusValue += (hpb * hpbLivePrice * this.upgradeDepreciation.hotPotato);
            bonusValue += (fuming * fumingLivePrice * this.upgradeDepreciation.fumingPotato);
        }

        // Art of War
        if (properties.artOfWar) {
            const aowLivePrice = bazaarService.getPrice("THE_ART_OF_WAR") || 5000000;
            bonusValue += (aowLivePrice * this.upgradeDepreciation.artOfWar);
        }

        // Master Stars
        if (properties.masterStars > 0) {
            for (let i = 1; i <= Math.min(properties.masterStars, 5); i++) {
                const masterStarPrice = bazaarService.getMasterStarPrice(i);
                bonusValue += (masterStarPrice * this.upgradeDepreciation.masterStar);
            }
        }

        // Enchantment Books
        if (properties.enchantments) {
            for (const [ench, level] of Object.entries(properties.enchantments)) {
                const enchantLivePrice = bazaarService.getEnchantPrice(ench, level);
                if (enchantLivePrice) {
                    bonusValue += (enchantLivePrice * this.upgradeDepreciation.enchants);
                }
            }
        }

        return Math.floor(bonusValue);
    }

    fallbackSkyblockId(rawName) {
        return rawName.replace(/[✪⍟]/g, "").trim().toUpperCase().replace(/\s+/g, "_");
    }
}

module.exports = new FlippingEngine();