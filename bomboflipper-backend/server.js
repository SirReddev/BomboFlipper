const WebSocket = require('ws');
const axios = require('axios');
const bazaarService = require('./services/BazaarService'); // Import Bazaar Service
const flippingEngine = require('./services/FlippingEngine');

const PORT = process.env.PORT || 8080;
const wss = new WebSocket.Server({ port: PORT });
let connectedClients = [];

console.log(`================================────────────────────────`);
console.log(`[BomboFlipper Server] Initializing AH/Bazaar Engine on Port ${PORT}`);
console.log(`================================────────────────────────`);

wss.on('connection', (ws, req) => {
    const clientIp = req.socket.remoteAddress;
    console.log(`[WebSocket] Client connected from ${clientIp}`);
    connectedClients.push(ws);

    ws.send(JSON.stringify({
        type: "chatMessage",
        data: "§8[§bBomboFlipper Engine§8] §aConnected to AH/Bazaar Hybrid backend!"
    }));

    ws.on('message', (message) => {
        try {
            const parsed = JSON.parse(message);
            if (parsed.type === 'command') {
                console.log(`[WebSocket] Received client command: ${parsed.data}`);
            }
        } catch (e) {}
    });

    ws.on('close', () => {
        console.log(`[WebSocket] Client disconnected.`);
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

function broadcastDebugLog(message) {
    const payload = JSON.stringify({ type: "debugLog", message: message });
    connectedClients.forEach((client) => {
        if (client.readyState === WebSocket.OPEN) {
            client.send(payload);
        }
    });
}

async function runAuctionCheckCycle() {
    const cycleStartTime = Date.now();
    try {
        // 1. Fetch live upgrade prices from the Bazaar
        await bazaarService.updateBazaar();

        const bzKeys = Object.keys(bazaarService.products).length;
        broadcastDebugLog(`[BazaarService] Live prices updated for ${bzKeys} items.`);

        // 2. Fetch all pages from the Auction House
        const firstPageRes = await axios.get('https://api.hypixel.net/skyblock/auctions?page=0');
        const totalPages = firstPageRes.data.totalPages;
        let allAuctions = [...firstPageRes.data.auctions];

        const fetchMsg = `[AuctionCheckService] Fetching ${totalPages} AH pages...`;
        console.log(fetchMsg);
        broadcastDebugLog(fetchMsg);

        const pagePromises = [];
        for (let page = 1; page < totalPages; page++) {
            pagePromises.push(axios.get(`https://api.hypixel.net/skyblock/auctions?page=${page}`));
        }

        const pageResponses = await Promise.all(pagePromises);
        pageResponses.forEach((res) => {
            if (res.data && res.data.auctions) {
                allAuctions.push(...res.data.auctions);
            }
        });

        console.log(`[AuctionCheckService] Total active auctions retrieved: ${allAuctions.length}`);

        // 3. Pass auctions to Flipping Engine for valuation
        const evaluationResult = await flippingEngine.evaluateAuctions(allAuctions);

        const evalMsg = `[Engine] Evaluated ${evaluationResult.totalEvaluated} BIN items in ${Date.now() - cycleStartTime}ms. Flips Found: ${evaluationResult.flips.length}`;
        console.log(evalMsg);
        broadcastDebugLog(evalMsg);

        for (const flip of evaluationResult.flips) {
            console.log(`[FLIP DETECTED] ${flip.item} | Price: ${flip.price.toLocaleString()} | Value: ${flip.estimatedValue.toLocaleString()} | Profit: ${flip.profit.toLocaleString()}`);

            broadcastFlip({
                type: "flip",
                itemName: flip.item || flip.itemName || "Unknown Item",
                price: flip.price,
                profit: flip.profit,
                command: flip.command,
                uuid: flip.id || flip.uuid, // Coflnet usually provides the auction UUID under 'id'
                demandTier: flip.demandTier || 5,
                salesPerDay: flip.salesPerDay || -1,
                bytes: null
            });
        }

    } catch (error) {
        console.error('[AuctionCheckService] Error during auction check cycle:', error.message);
    }
}

setInterval(runAuctionCheckCycle, 60000);
runAuctionCheckCycle();