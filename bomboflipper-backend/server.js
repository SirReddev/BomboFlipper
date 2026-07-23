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

async function fetchAllAHPages(totalPages) {
    const allAuctions = [];
    const chunkSize = 15;

    for (let i = 0; i < totalPages; i += chunkSize) {
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

async function runAuctionCheckCycle() {
    const cycleStartTime = Date.now();
    logger.cycle(`Starting Auction House & Bazaar scan cycle...`);

    try {
        // 1. Fetch live upgrade prices from the Bazaar
        await bazaarService.updateBazaar();
        const bzKeys = Object.keys(bazaarService.products).length;
        logger.info(`[BazaarService] Live prices updated for ${bzKeys} items.`);

        // 2. Fetch total page count
        const firstPageRes = await axios.get('https://api.hypixel.net/skyblock/auctions?page=0', { timeout: 8000 });
        const totalPages = firstPageRes.data ? firstPageRes.data.totalPages : 0;

        if (totalPages === 0) {
            logger.error(`Hypixel AH API returned 0 total pages.`);
            return;
        }

        // 3. Fetch all pages in chunked parallel batches (15 pages at a time)
        const allAuctions = await fetchAllAHPages(totalPages);
        logger.info(`[AuctionCheck] Downloaded ${totalPages} AH pages (${logger.formatNumber(allAuctions.length)} total active auctions).`);

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

setInterval(runAuctionCheckCycle, 60000);
runAuctionCheckCycle();