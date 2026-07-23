const WebSocket = require('ws');
const axios = require('axios');
const bazaarService = require('./services/BazaarService');
const flippingEngine = require('./services/FlippingEngine');
const volumeCache = require('./services/VolumeCache');
const logger = require('./services/Logger');

const PORT = process.env.PORT || 8080;
const wss = new WebSocket.Server({ port: PORT });
let connectedClients = [];

logger.banner(`BomboFlipper Hybrid AH/Bazaar Engine Running on Port ${PORT}`);

wss.on('connection', (ws, req) => {
    const clientIp = req.socket.remoteAddress;
    logger.success(`WebSocket client connected from ${clientIp}`);
    connectedClients.push(ws);

    ws.send(JSON.stringify({
        type: "chatMessage",
        data: "§8[§bBomboFlipper Engine§8] §aConnected to AH/Bazaar Hybrid backend!"
    }));

    ws.on('message', (message) => {
        try {
            const parsed = JSON.parse(message);
            if (parsed.type === 'command') {
                logger.info(`Received client command: ${parsed.data}`);
            }
        } catch (e) {}
    });

    ws.on('close', () => {
        logger.info(`WebSocket client disconnected.`);
        connectedClients = connectedClients.filter(c => c !== ws);
    });
});

function broadcastFlip(flipPayload) {
    const message = JSON.stringify(flipPayload);
    connectedClients.forEach((client) => {
        if (client.readyState === WebSocket.OPEN) {
            client.send(message);
        }
    });
}

async function fetchAllAHPages(totalPages, firstPageData) {
    const allAuctions = [];
    if (firstPageData && firstPageData.auctions) {
        allAuctions.push(...firstPageData.auctions);
    }
    
    // Blast all remaining pages with high concurrency
    const chunkSize = 50; 

    for (let i = 1; i < totalPages; i += chunkSize) {
        const batchPromises = [];
        for (let page = i; page < Math.min(i + chunkSize, totalPages); page++) {
            batchPromises.push(
                axios.get(`https://api.hypixel.net/skyblock/auctions?page=${page}`, { timeout: 8000 })
                     .catch(err => null)
            );
        }
        const responses = await Promise.all(batchPromises);
        for (const res of responses) {
            if (res && res.data && res.data.auctions) {
                allAuctions.push(...res.data.auctions);
            }
        }
    }
    return allAuctions;
}

async function runAuctionCheckCycle(firstPageData) {
    const cycleStartTime = Date.now();
    logger.cycle(`New Hypixel API update detected! Starting hyper-scan...`);

    try {
        // 1. Fetch live upgrade prices from the Bazaar
        await bazaarService.updateBazaar();
        const bzKeys = Object.keys(bazaarService.products).length;

        // 2. We already have total pages from the polling check
        const totalPages = firstPageData.totalPages || 0;

        if (totalPages === 0) {
            logger.error(`Hypixel AH API returned 0 total pages.`);
            return;
        }

        // 3. Fetch all remaining pages in high-concurrency batches
        const allAuctions = await fetchAllAHPages(totalPages, firstPageData);
        logger.info(`[AuctionCheck] Downloaded ${totalPages} AH pages (${logger.formatNumber(allAuctions.length)} total active auctions) in ${Date.now() - cycleStartTime}ms.`);

        // 4. Pass auctions to Flipping Engine for valuation
        const evaluationResult = await flippingEngine.evaluateAuctions(allAuctions);
        const durationMs = Date.now() - cycleStartTime;

        // Print neat ASCII Table for flips
        logger.printFlipTable(evaluationResult.flips);

        const vStatus = volumeCache.getStatus();
        logger.info(`[VolumeCache Status] ${logger.formatNumber(vStatus.cachedCount)} records saved on disk | ${vStatus.pendingQueue} items queued.`);

        logger.success(`[SCAN COMPLETE] Evaluated ${logger.formatNumber(evaluationResult.totalEvaluated)} BIN items in ${durationMs}ms | ${evaluationResult.flips.length} Flips Found.`);

        for (const flip of evaluationResult.flips) {
            broadcastFlip({
                type: "flip",
                itemName: flip.item || flip.itemName || "Unknown Item",
                price: flip.price,
                profit: flip.profit,
                command: flip.command,
                uuid: flip.id || flip.uuid,
                demandTier: flip.demandTier !== undefined ? flip.demandTier : 1,
                salesPerDay: flip.salesPerDay !== undefined ? flip.salesPerDay : 0,
                bytes: null
            });
        }

    } catch (error) {
        logger.error(`Error during auction check cycle: ${error.message}`);
    }
}

// --- High Speed Polling Logic ---
let lastUpdatedTime = 0;
let isScanning = false;

async function pollHypixelAPI() {
    if (isScanning) return;

    try {
        const res = await axios.get('https://api.hypixel.net/skyblock/auctions?page=0', { timeout: 3000 });
        if (res.data && res.data.lastUpdated) {
            if (res.data.lastUpdated !== lastUpdatedTime) {
                // First boot
                if (lastUpdatedTime === 0) {
                    logger.info(`[Poller] Initialized to Hypixel API clock: ${res.data.lastUpdated}`);
                } 
                lastUpdatedTime = res.data.lastUpdated;
                isScanning = true;
                await runAuctionCheckCycle(res.data);
                isScanning = false;
            }
        }
    } catch (e) {
        // Ignore timeout / network errors during silent polling
    }
}

// Poll Hypixel every 1 second (1000ms) to detect the exact millisecond the AH refreshes
setInterval(pollHypixelAPI, 1000);
pollHypixelAPI();