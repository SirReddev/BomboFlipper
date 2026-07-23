const nbt = require('prismarine-nbt');

class PropertiesSelector {
    async extractProperties(base64ItemBytes) {
        if (!base64ItemBytes) return null;

        try {
            const buffer = Buffer.from(base64ItemBytes, 'base64');
            // Use the native promise directly instead of promisify
            const parsed = await nbt.parse(buffer);
            
            const itemData = parsed.parsed.value.i.value.value[0];
            if (!itemData || !itemData.tag || !itemData.tag.value) return null;

            const tag = itemData.tag.value;
            const extra = tag.ExtraAttributes ? tag.ExtraAttributes.value : {};

            return {
                skyblockId: extra.id ? extra.id.value : null,
                recombobulated: extra.rarity_upgrades ? extra.rarity_upgrades.value === 1 : false,
                dungeonStars: extra.upgrade_level ? extra.upgrade_level.value : 0,
                dungeonItemLevel: extra.dungeon_item_level ? extra.dungeon_item_level.value : 0,
                hotPotatoBooks: extra.hot_potato_count ? extra.hot_potato_book_count ? extra.hot_potato_book_count.value : extra.hot_potato_count.value : 0,
                artOfWar: extra.art_of_war ? true : false,
                enchantments: this.parseEnchantments(extra.enchantments),
                attributes: this.parseAttributes(extra.attributes),
                gemstones: this.parseGemstones(extra.gems),
                petInfo: extra.petInfo ? JSON.parse(extra.petInfo.value) : null,
                skin: extra.skin ? extra.skin.value : null
            };
        } catch (error) {
            return null;
        }
    }

    parseEnchantments(enchantsNbt) {
        if (!enchantsNbt || !enchantsNbt.value) return {};
        const enchants = {};
        for (const [key, val] of Object.entries(enchantsNbt.value)) {
            enchants[key.toLowerCase()] = val.value;
        }
        return enchants;
    }

    parseAttributes(attributesNbt) {
        if (!attributesNbt || !attributesNbt.value) return {};
        const attrs = {};
        for (const [key, val] of Object.entries(attributesNbt.value)) {
            attrs[key.toLowerCase()] = val.value;
        }
        return attrs;
    }

    parseGemstones(gemsNbt) {
        if (!gemsNbt || !gemsNbt.value) return [];
        const gemstones = [];
        for (const [key, val] of Object.entries(gemsNbt.value)) {
            if (typeof val.value === 'string') {
                gemstones.push({ slot: key, quality: val.value });
            } else if (val.value && val.value.quality) {
                gemstones.push({ slot: key, quality: val.value.quality.value });
            }
        }
        return gemstones;
    }
}

module.exports = new PropertiesSelector();