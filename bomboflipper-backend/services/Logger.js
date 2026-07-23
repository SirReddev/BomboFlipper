const fs = require('fs');
const path = require('path');

class Logger {
    constructor() {
        this.logDir = path.join(__dirname, '../logs');
        if (!fs.existsSync(this.logDir)) {
            fs.mkdirSync(this.logDir, { recursive: true });
        }
    }

    getLogFile(type) {
        const dateStr = new Date().toISOString().split('T')[0];
        return path.join(this.logDir, `${type}_${dateStr}.log`);
    }

    log(level, message) {
        const timestamp = new Date().toLocaleTimeString('en-US', { hour12: false });
        const rawMessage = `[${timestamp}] [${level}] ${message}`;

        let colorMessage = rawMessage;
        let targetFile = 'system';

        if (level === 'FLIP') {
            colorMessage = `\x1b[1;\x1b[32m[${timestamp}] 🎯 ${message}\x1b[0m`; // Bold Green
            targetFile = 'flips';
        } else if (level === 'CYCLE') {
            colorMessage = `\x1b[1;\x1b[33m[${timestamp}] ⚡ ${message}\x1b[0m`; // Bold Yellow
            targetFile = 'system';
        } else if (level === 'ERROR') {
            colorMessage = `\x1b[1;\x1b[31m[${timestamp}] ❌ ${message}\x1b[0m`; // Bold Red
            targetFile = 'errors';
        } else if (level === 'CACHE') {
            colorMessage = `\x1b[90m[${timestamp}] [CACHE] ${message}\x1b[0m`; // Dim Gray
            targetFile = 'cache';
        } else if (level === 'SUCCESS') {
            colorMessage = `\x1b[1;\x1b[36m[${timestamp}] ✅ ${message}\x1b[0m`; // Bold Cyan
            targetFile = 'system';
        } else {
            colorMessage = `\x1b[37m[${timestamp}] [INFO] ${message}\x1b[0m`; // White
            targetFile = 'system';
        }

        console.log(colorMessage);

        try {
            // Strip ANSI color codes before saving to text log file
            const cleanText = rawMessage.replace(/\x1B\[[0-9;]*[mK]/g, '') + '\n';
            fs.appendFileSync(this.getLogFile(targetFile), cleanText);
        } catch (err) {}
    }

    info(msg) { this.log('INFO', msg); }
    flip(msg) { this.log('FLIP', msg); }
    cycle(msg) { this.log('CYCLE', msg); }
    success(msg) { this.log('SUCCESS', msg); }
    error(msg) { this.log('ERROR', msg); }
    cache(msg) { this.log('CACHE', msg); }

    banner(text) {
        console.log(`\n\x1b[1;\x1b[34m=======================================================================================\x1b[0m`);
        console.log(`\x1b[1;\x1b[33m 🚀 ${text}\x1b[0m`);
        console.log(`\x1b[1;\x1b[34m=======================================================================================\x1b[0m`);
    }

    divider() {
        console.log(`\x1b[90m---------------------------------------------------------------------------------------\x1b[0m`);
    }

    printFlipTable(flips) {
        if (!flips || flips.length === 0) return;

        console.log(`\n\x1b[1;\x1b[32m 🎯 IDENTIFIED PROFITABLE FLIPS (${flips.length})\x1b[0m`);
        console.log(`\x1b[32m┌──────┬────────────────────────────────────┬──────────────┬──────────────┬───────────┐\x1b[0m`);
        console.log(`\x1b[32m│ TIER │ ITEM NAME                          │ BUY PRICE    │ EST PROFIT   │ SALES/DAY │\x1b[0m`);
        console.log(`\x1b[32m├──────┼────────────────────────────────────┼──────────────┼──────────────┼───────────┤\x1b[0m`);

        for (const flip of flips) {
            const tierStr = `[T${flip.demandTier}]`.padEnd(4);
            const nameCol = (flip.item || "Unknown Item").padEnd(34).substring(0, 34);
            const priceCol = this.formatNumber(flip.price).padEnd(12);
            const profitCol = this.formatNumber(flip.profit).padEnd(12);
            const salesCol = (flip.salesPerDay > 0 ? `${Math.round(flip.salesPerDay)} /day` : "N/A").padEnd(9);

            console.log(`\x1b[1;\x1b[32m│ ${tierStr} │ ${nameCol} │ ${priceCol} │ +${profitCol} │ ${salesCol} │\x1b[0m`);
        }

        console.log(`\x1b[32m└──────┴────────────────────────────────────┴──────────────┴──────────────┴───────────┘\x1b[0m\n`);
    }

    formatNumber(num) {
        return num.toLocaleString('en-US');
    }
}

module.exports = new Logger();