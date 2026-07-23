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
        // Separates logs into 'flips' and 'cache' files
        return path.join(this.logDir, `${type}_${dateStr}.log`);
    }

    log(level, message) {
        const timestamp = new Date().toISOString().replace('T', ' ').substring(0, 19);
        const formattedMessage = `[${timestamp}] [${level}] ${message}`;

        let targetFile = 'system';

        // Console colors & file routing
        if (level === 'FLIP') {
            console.log(`\x1b[32m${formattedMessage}\x1b[0m`);      // Green
            targetFile = 'flips';
        } else if (level === 'ERROR') {
            console.error(`\x1b[31m${formattedMessage}\x1b[0m`); // Red
            targetFile = 'errors';
        } else if (level === 'CACHE') {
            console.log(`\x1b[36m${formattedMessage}\x1b[0m`);   // Cyan
            targetFile = 'cache';
        } else {
            console.log(`\x1b[37m${formattedMessage}\x1b[0m`);   // White
            targetFile = 'system';
        }

        // Append to the specific daily text file
        try {
            fs.appendFileSync(this.getLogFile(targetFile), formattedMessage + '\n');
        } catch (err) {
            // Fallback if file write fails
        }
    }

    info(msg) { this.log('INFO', msg); }
    flip(msg) { this.log('FLIP', msg); }
    error(msg) { this.log('ERROR', msg); }
    cache(msg) { this.log('CACHE', msg); }

    formatNumber(num) {
        return num.toLocaleString('en-US');
    }
}

module.exports = new Logger();