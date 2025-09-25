#!/usr/bin/env node

/**
 * YouTube Cipher Executor
 * This Node.js script executes YouTube's signature and n-parameter transformation functions
 * Based on the approach used by pytubefix
 */

const vm = require('vm');
const readline = require('readline');

// Create a readline interface to receive input from Java
const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    terminal: false
});

// Process input line by line
rl.on('line', (input) => {
    try {
        const request = JSON.parse(input);
        const result = executeFunction(request);
        console.log(JSON.stringify({ success: true, result }));
    } catch (error) {
        console.log(JSON.stringify({ 
            success: false, 
            error: error.message,
            stack: error.stack 
        }));
    }
});

/**
 * Execute a YouTube cipher function
 * @param {Object} request - The request object containing:
 *   - type: 'signature' or 'n_parameter'
 *   - playerCode: The full YouTube player JavaScript code
 *   - functionCode: The specific function code to execute
 *   - input: The input value to transform
 *   - globalVars: Global variables code (optional)
 *   - actionsCode: Actions/helper functions code (optional)
 */
function executeFunction(request) {
    const { type, playerCode, functionCode, input, globalVars, actionsCode } = request;
    
    // Create a sandboxed context for execution
    const sandbox = {
        console: console,
        _yt_player: {},
        _exposed: {},
        window: {},
        document: {
            location: { 
                href: 'https://www.youtube.com',
                protocol: 'https:',
                hostname: 'www.youtube.com'
            }
        },
        navigator: {
            userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
        },
        setTimeout: setTimeout,
        setInterval: setInterval,
        clearTimeout: clearTimeout,
        clearInterval: clearInterval,
        String: String,
        Array: Array,
        Object: Object,
        Number: Number,
        Boolean: Boolean,
        Math: Math,
        Date: Date,
        RegExp: RegExp,
        JSON: JSON,
        parseInt: parseInt,
        parseFloat: parseFloat,
        encodeURIComponent: encodeURIComponent,
        decodeURIComponent: decodeURIComponent,
        btoa: (str) => Buffer.from(str).toString('base64'),
        atob: (b64) => Buffer.from(b64, 'base64').toString()
    };
    
    // Create VM context
    const context = vm.createContext(sandbox);
    
    try {
        // Execute the full player code if provided (for complex cases)
        if (playerCode) {
            // Wrap the player code to expose functions
            const wrappedCode = `
                (function(_yt_player) {
                    ${playerCode}
                    // Make functions available in _exposed
                    if (typeof _yt_player === 'object') {
                        for (let key in _yt_player) {
                            _exposed[key] = _yt_player[key];
                        }
                    }
                })(_yt_player);
            `;
            vm.runInContext(wrappedCode, context, { timeout: 5000 });
        }
        
        // Execute global variables if provided
        if (globalVars) {
            vm.runInContext(globalVars, context, { timeout: 1000 });
        }
        
        // Execute actions/helper code if provided
        if (actionsCode) {
            vm.runInContext(actionsCode, context, { timeout: 1000 });
        }
        
        // Execute the specific function
        if (type === 'signature') {
            // For signature decryption
            const sigCode = `
                ${functionCode};
                var decrypt_sig = ${getFunctionName(functionCode)};
                decrypt_sig("${input}");
            `;
            const result = vm.runInContext(sigCode, context, { timeout: 1000 });
            return result;
        } else if (type === 'n_parameter') {
            // For n-parameter transformation
            const nCode = `
                ${functionCode};
                var decrypt_nsig = ${getFunctionName(functionCode)};
                decrypt_nsig("${input}");
            `;
            const result = vm.runInContext(nCode, context, { timeout: 1000 });
            return result;
        } else {
            // Generic function execution
            const code = `
                ${functionCode};
                var targetFunc = ${getFunctionName(functionCode)};
                targetFunc("${input}");
            `;
            const result = vm.runInContext(code, context, { timeout: 1000 });
            return result;
        }
    } catch (error) {
        // Try alternative approach - direct evaluation
        try {
            const alternativeCode = `
                (function() {
                    ${globalVars || ''}
                    ${actionsCode || ''}
                    ${functionCode}
                    var func = ${getFunctionName(functionCode)};
                    return func("${input}");
                })();
            `;
            const result = vm.runInContext(alternativeCode, context, { timeout: 2000 });
            return result;
        } catch (altError) {
            // If both approaches fail, throw the original error
            throw error;
        }
    }
}

/**
 * Extract function name from function code
 * @param {string} functionCode - The function code
 * @returns {string} The function name
 */
function getFunctionName(functionCode) {
    // Try to extract function name from various patterns
    const patterns = [
        /function\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\s*\(/,
        /var\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\s*=\s*function/,
        /([a-zA-Z_$][a-zA-Z0-9_$]*)\s*=\s*function/,
        /const\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\s*=\s*function/,
        /let\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\s*=\s*function/
    ];
    
    for (const pattern of patterns) {
        const match = functionCode.match(pattern);
        if (match && match[1]) {
            return match[1];
        }
    }
    
    // If no function name found, try to use the function directly
    return '(' + functionCode + ')';
}

// Handle process termination gracefully
process.on('SIGTERM', () => {
    process.exit(0);
});

process.on('SIGINT', () => {
    process.exit(0);
});