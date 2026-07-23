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
        const rawBaseMap = new Map();
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

            const rarity = auction.tier || "COMMON";
            const itemGroupKey = `${skyblockId}|${rarity}`;

            const isClean = this.isCleanItem(extractedProps);
            const variantKey = this.getVariantKey(itemGroupKey, extractedProps);

            const processed = {
                uuid: auction.uuid,
                itemName: auction.item_name,
                startingBid: auction.starting_bid,
                skyblockId: skyblockId,
                rarity: rarity,
                itemGroupKey: itemGroupKey,
                properties: extractedProps,
                isClean: isClean,
                variantKey: variantKey
            };

            processedAuctions.push(processed);

            // Group into Raw Base Map (By SkyBlock ID only across all rarities)
            if (!rawBaseMap.has(skyblockId)) {
                rawBaseMap.set(skyblockId, []);
            }
            rawBaseMap.get(skyblockId).push(processed);

            // Group into All Listings Map (By SkyBlock ID + Rarity)
            if (!allListingsMap.has(itemGroupKey)) {
                allListingsMap.set(itemGroupKey, []);
            }
            allListingsMap.get(itemGroupKey).push(processed);

            // Group into Clean Listings Map (By SkyBlock ID + Rarity)
            if (isClean) {
                if (!cleanListingsMap.has(itemGroupKey)) {
                    cleanListingsMap.set(itemGroupKey, []);
                }
                cleanListingsMap.get(itemGroupKey).push(processed);
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
        for (const list of rawBaseMap.values()) {
            list.sort((a, b) => a.startingBid - b.startingBid);
        }
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

        // Step 2: Strict LBIN1 vs LBIN2 Evaluation with Crafting Cost & Rarity Scope Rules
        for (const item of processedAuctions) {
            // Comparable lists excluding the target item itself (Self-Exclusion)
            const otherClean = (cleanListingsMap.get(item.itemGroupKey) || []).filter(a => a.uuid !== item.uuid);
            const otherVariant = item.variantKey ? (variantListingsMap.get(item.variantKey) || []).filter(a => a.uuid !== item.uuid) : [];
            const otherAll = (allListingsMap.get(item.itemGroupKey) || []).filter(a => a.uuid !== item.uuid);

            if (otherAll.length === 0) continue; // Single listing on entire AH, cannot determine market floor safely

            const baseFloor = otherClean.length > 0 ? otherClean[0].startingBid : otherAll[0].startingBid;
            if (!baseFloor || baseFloor <= 0) continue;

            // Raw Base Floor across ALL rarities for the base item ID (e.g. 240k for SNIPER_BOW)
            const otherRawBase = (rawBaseMap.get(item.skyblockId) || []).filter(a => a.uuid !== item.uuid);
            const rawBaseFloor = otherRawBase.length > 0 ? otherRawBase[0].startingBid : baseFloor;

            // Calculate exact live crafting cost of applied upgrades
            const rawUpgradeBonus = this.calculateLiveUpgradeValue(item.properties);

            // Base floor multiplier scaling (cheap trash gear cannot be listed at 50x base floor)
            let baseFloorMultiplier = 2.0;
            if (rawBaseFloor < 1000000) {
                baseFloorMultiplier = 1.5; // Cheap gear (<1M) caps at 1.5x raw base floor + upgrades
            } else if (rawBaseFloor > 10000000) {
                baseFloorMultiplier = 2.5;
            }

            // HARD CEILING: Max market value cannot exceed (rawBaseFloor * multiplier) + rawUpgradeBonus
            const maxMarketValue = (rawBaseFloor * baseFloorMultiplier) + rawUpgradeBonus;

            // ABSOLUTE RULE 1: If item price exceeds max reasonable market value, REJECT IMMEDIATELY!
            if (item.startingBid >= maxMarketValue) {
                continue; // REJECT: Overpriced listing! (Rejects 3.0M Sniper Bow when 240k base is available, 2.7M Earthen Blade, etc.)
            }

            // ABSOLUTE RULE 1.5: Historical Median Manipulation Guard
            // Reject items whose raw price (minus upgrades) exceeds 3.0x their historical Coflnet median price!
            const historicalData = volumeCache.getHistoricalData(item.skyblockId, true);
            if (historicalData && historicalData.medianPrice > 0) {
                const rawItemPrice = item.startingBid - rawUpgradeBonus;
                if (rawItemPrice > historicalData.medianPrice * 3.0) {
                    continue; // REJECT: AH Manipulation / Market Cornering Scam! (Rejects 100M Titanium Minecart)
                }
            }

            let targetResalePrice = 0;

            if (!item.isClean && otherVariant.length > 0) {
                // PATH 1: Upgraded Variant LBIN Sniping
                const lowestOtherVariantBin = otherVariant[0].startingBid;

                // ABSOLUTE RULE 2: Item MUST be cheaper than the lowest identical variant listing on AH
                if (item.startingBid >= lowestOtherVariantBin) {
                    continue; // REJECT: Another identical/similar upgraded item is cheaper or equal
                }

                targetResalePrice = Math.min(lowestOtherVariantBin, maxMarketValue);

            } else if (item.isClean && otherClean.length > 0) {
                // PATH 2: Clean Item LBIN Sniping
                const lowestOtherCleanBin = otherClean[0].startingBid;

                // ABSOLUTE RULE 3: Clean item MUST be cheaper than the lowest clean listing on AH
                if (item.startingBid >= lowestOtherCleanBin) {
                    continue; // REJECT: Another clean item is cheaper or equal
                }

                targetResalePrice = lowestOtherCleanBin;

            } else if (!item.isClean && otherVariant.length === 0) {
                // PATH 3: Unique Upgraded Fallback (No identical variant on AH)
                let maxUpgradeCap = baseFloor * 0.50; // Max 50% above base floor
                if (baseFloor < 1000000) maxUpgradeCap = baseFloor * 0.30;
                else if (baseFloor > 20000000) maxUpgradeCap = rawUpgradeBonus * 0.70;

                const maxAllowedFallback = baseFloor + Math.min(rawUpgradeBonus, maxUpgradeCap);

                if (item.startingBid >= maxAllowedFallback) {
                    continue;
                }

                targetResalePrice = maxAllowedFallback;
            } else {
                continue;
            }

            // NEXT-LISTING RESALE CEILING RULE:
            // Target resale price can NEVER exceed the next lowest listing on AH + extra upgrade crafting cost
            // (Fixes 3.3M SA Helmet with next listing at 3.4M from claiming 3.4M fake profit!)
            const nextListingPrice = otherAll[0].startingBid;
            const maxNextResaleCeiling = nextListingPrice + rawUpgradeBonus;
            targetResalePrice = Math.min(targetResalePrice, maxNextResaleCeiling);

            // HISTORICAL MEDIAN RESALE BOUNDING RULE:
            // Target resale price cannot exceed 1.25x historical median price + raw upgrade crafting cost
            // (Fixes 5.5M Block Zapper when 3-day average is 4.25M and next listing is temporarily 6.9M!)
            if (historicalData && historicalData.medianPrice > 0) {
                const maxHistoricalResale = (historicalData.medianPrice * 1.25) + rawUpgradeBonus;
                targetResalePrice = Math.min(targetResalePrice, maxHistoricalResale);
            }

            // UNIVERSAL OUTLIER PRICE GAP CEILING:
            // If target resale price is > 2.5x higher than item buy price, cap it to 1.4x buyPrice
            if (targetResalePrice / item.startingBid > 2.5) {
                targetResalePrice = item.startingBid * 1.4;
            }

            const taxRate = this.calculateAHTax(targetResalePrice);
            const rawNetProfit = (targetResalePrice * (1 - taxRate)) - item.startingBid;
            // Apply 5% safety margin discount (from NEC formula) to account for listing creation fees and slight undercutting
            const estimatedNetProfit = rawNetProfit * 0.95;
            const marginRatio = estimatedNetProfit / item.startingBid;

            if (estimatedNetProfit >= this.minProfitThreshold && marginRatio >= this.minMarginRatio) {
                const salesPerDay = volumeCache.getVolume(item.skyblockId, true);
                const activeListingsCount = (allListingsMap.get(item.skyblockId) || []).length;

                let demandTier = 1; // 1 = ANY/LOW
                if (salesPerDay > 0) {
                    if (salesPerDay >= 100) demandTier = 5;      // VERY HIGH
                    else if (salesPerDay >= 40) demandTier = 4;  // HIGH
                    else if (salesPerDay >= 10) demandTier = 3;  // MEDIUM
                    else if (salesPerDay >= 3) demandTier = 2;   // LOW
                    else demandTier = 1;
                } else {
                    // Real-time supply fallback when salesPerDay is uncached
                    if (activeListingsCount >= 80) demandTier = 4;      // HIGH
                    else if (activeListingsCount >= 30) demandTier = 3;  // MEDIUM
                    else if (activeListingsCount >= 12) demandTier = 2;  // LOW
                    else demandTier = 1;                                 // LOW/ANY (e.g. 7 listings -> Tier 1)
                }

                // Penalize demand tier for high-priced upgraded variants (e.g. 51M Scorpion Foil vs 18.7M clean)
                const markupRatio = rawBaseFloor > 0 ? item.startingBid / rawBaseFloor : 1.0;
                if (markupRatio >= 2.5) {
                    demandTier = Math.max(1, demandTier - 2);
                } else if (markupRatio >= 1.8) {
                    demandTier = Math.max(1, demandTier - 1);
                }

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

        return {
            totalEvaluated: processedAuctions.length,
            flips: identifiedFlips
        };
    }

    calculateAHTax(price) {
        if (!price || price <= 0) return 0.01;
        if (price >= 100000000) return 0.025; // 2.5% tax over 100M
        if (price >= 10000000) return 0.020;  // 2.0% tax for 10M-100M
        return 0.010;                         // 1.0% tax under 10M
    }

    isCleanItem(props) {
        if (!props) return true;
        if (props.recombobulated) return false;
        if (props.dungeonStars > 0 || props.masterStars > 0) return false;
        if (props.hotPotatoBooks > 0) return false;
        if (props.artOfWar) return false;

        if (props.enchantments) {
            for (const [ench, level] of Object.entries(props.enchantments)) {
                const cleanEnch = ench.toLowerCase();
                if (level >= 5 || cleanEnch.includes("ultimate") || cleanEnch.includes("soul_eater") || cleanEnch.includes("overload") || cleanEnch.includes("one_for_all")) {
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
        const floor = props.dungeonFloor || 0;

        // Group by MAJOR ultimate/high enchants only
        const majorEnchants = [];
        if (props.enchantments) {
            const highValueList = ["soul_eater", "overload", "one_for_all", "duplex", "fatal_tempest", "inferno", "swarm", "chimera", "legion", "rend", "wisdom", "bank", "flash"];
            for (const [ench, level] of Object.entries(props.enchantments)) {
                const cleanEnch = ench.toLowerCase();
                if (cleanEnch.includes("ultimate") || highValueList.some(h => cleanEnch.includes(h))) {
                    majorEnchants.push(`${cleanEnch}:${level}`);
                }
            }
        }
        majorEnchants.sort();

        // Kuudra Armor attribute scoping (CRIMSON, AURORA, TERROR, HOLLOW, FERVOR)
        let attrStr = "";
        if (props.attributes) {
            const attrList = Object.entries(props.attributes)
                .map(([k, v]) => `${k}:${v}`)
                .sort()
                .join(',');
            if (attrList.length > 0) {
                attrStr = `|attr:${attrList}`;
            }
        }

        return `${skyblockId}|r:${recomb}|s:${stars}|m:${master}|f:${floor}|e:${majorEnchants.join(',')}${attrStr}`;
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