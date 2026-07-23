# Average Lowest Bin Implementation for BomboFlip

## Summary
Successfully implemented the average lowest bin price checking system from SkyBlocker into your BomboFlip mod. This replaces the local sampling-based profit calculation with API-provided historical averages, which is more accurate and reliable.

## Changes Made

### 1. New Files Created

#### AverageBinType.java
- Enum to represent which average to use (1-day, 3-day, or both)
- Includes API URLs and config value conversion
- Configurable via the `avgBinType` config option

#### AverageBinPriceCache.java
- Holds cached average bin prices from hysky.de API
- Supports both 1-day and 3-day averages
- Updates whenever new auction data is fetched
- Provides `getAveragePrice()` method with fallback logic

### 2. Modified Files

#### BomboFlipConfig.java
- Added `avgBinType` config field (default: "THREE_DAY")
- Allows users to choose between ONE_DAY, THREE_DAY, or BOTH averages

#### HypixelApiClient.java
- Added average cache instance
- Extended polling to fetch averages from hysky.de API alongside auction data
- Added `fetchAndCacheAverages()` to fetch both 1-day and 3-day averages
- Added `fetchAverageBins(String url)` to parse API responses
- Public `getAverageCache()` method to access cached data
- Errors are silently ignored so API failures don't interrupt the main polling

#### FlipAnalyzer.java
- Updated `analyze()` method signature to accept `AverageBinPriceCache`
- Now tries to get fair value from API averages first
- Falls back to local estimation if API data unavailable
- Uses Skyblock item ID to look up prices

#### AuctionEntry.java
- Added `cachedSkyblockId` transient field
- Added `skyblockId()` method to extract the Skyblock API ID from NBT data
- Used for looking up average prices

#### ItemBytesParser.java
- Added `extractSkyblockId(Map<String, Object> root)` method
- Extracts item ID from NBT data at `tag.i[0].tag.ExtraAttributes.id`

#### BomboFlipClient.java
- Updated `onFreshAuctionData()` to pass average cache to analyzer
- Changed: `analyzer.analyze(auctions, config, demandTracker, apiClient.getAverageCache())`

## How It Works

1. **Polling**: Each 60 seconds, the mod fetches:
   - Current auction data from Hypixel's API
   - Average bin prices from hysky.de API (1-day and 3-day)

2. **Price Lookup**: When analyzing flips:
   - Extracts Skyblock ID from item's NBT data
   - Looks up average price in cache using configured average type
   - Falls back to local sampling if no API data available
   - Calculates profit based on average (after AH tax)

3. **Configuration**: Users can control behavior via config:
   - `avgBinType`: "ONE_DAY", "THREE_DAY", or "BOTH" (3-day preferred, 1-day fallback)
   - Default is "THREE_DAY" - using 3-day historical average

## Benefits

✅ More accurate profit calculations based on historical data
✅ Reduces false positives from local sampling bias
✅ Professional-grade pricing like SkyBlocker
✅ Graceful fallback if API is unavailable
✅ Configurable to match user preferences
✅ No new dependencies required

## Testing Recommendations

1. Check that average prices are being fetched (look at logs)
2. Verify that flip detection still works with new calculation
3. Compare detected flips with manual AH browsing
4. Test fallback when API is temporarily unavailable
5. Adjust `avgBinType` config option to test different average sources

## Notes

- The hysky.de API endpoints used are free and publicly available
- API fetch failures are silent to avoid disrupting the main polling loop
- Item IDs that aren't found in the API will fall back to local estimation
- Config changes take effect immediately (no restart needed)

