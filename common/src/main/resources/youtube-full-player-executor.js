#!/usr/bin/env node

/**
 * YouTube Full Player Script Executor
 * This script can execute the entire YouTube player script and extract/execute cipher functions
 * It doesn't rely on regex patterns to extract functions
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
        const result = processRequest(request);
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
 * Process a request
 */
function processRequest(request) {
    const { type, playerScript, input } = request;
    
    if (type === 'extract_and_execute') {
        return extractAndExecute(playerScript, input);
    } else if (type === 'find_cipher_functions') {
        return findCipherFunctions(playerScript);
    } else {
        throw new Error('Unknown request type: ' + type);
    }
}

/**
 * Extract and execute cipher functions from the full player script
 */
function extractAndExecute(playerScript, input) {
    // Create a sandboxed context
    const sandbox = createSandbox();
    const context = vm.createContext(sandbox);
    
    try {
        // Execute the entire player script
        vm.runInContext(playerScript, context, { timeout: 5000 });
        
        // Try to find and execute signature decryption
        const sigResult = trySignatureDecryption(context, input.signature);
        
        // Try to find and execute n-parameter transformation
        const nResult = tryNParameterTransform(context, input.nParameter);
        
        return {
            signature: sigResult,
            nParameter: nResult
        };
    } catch (error) {
        // If direct execution fails, try pattern matching
        return fallbackPatternMatching(playerScript, input);
    }
}

/**
 * Try to find cipher functions by probing the context
 */
function findCipherFunctions(playerScript) {
    const sandbox = createSandbox();
    const context = vm.createContext(sandbox);
    
    try {
        // Execute the player script
        vm.runInContext(playerScript, context, { timeout: 5000 });
        
        // Look for functions that might be cipher functions
        const functions = [];
        for (const key in context) {
            if (typeof context[key] === 'function') {
                const funcStr = context[key].toString();
                if (looksLikeCipherFunction(funcStr)) {
                    functions.push({
                        name: key,
                        code: funcStr,
                        type: detectFunctionType(funcStr)
                    });
                }
            }
        }
        
        return functions;
    } catch (error) {
        return [];
    }
}

/**
 * Try to decrypt signature using various methods
 */
function trySignatureDecryption(context, encryptedSig) {
    if (!encryptedSig) return null;
    
    // Look for functions that transform strings and return strings
    for (const key in context) {
        if (typeof context[key] === 'function') {
            try {
                const result = context[key](encryptedSig);
                // Check if the result looks like a valid decrypted signature
                if (typeof result === 'string' && result !== encryptedSig && result.length > 0) {
                    // Additional validation: signature functions usually shuffle characters
                    if (hasSameCharacters(encryptedSig, result)) {
                        return result;
                    }
                }
            } catch (e) {
                // Function failed, try next
            }
        }
    }
    
    return null;
}

/**
 * Try to transform n-parameter using various methods
 */
function tryNParameterTransform(context, nParam) {
    if (!nParam) return null;
    
    // Look for functions that might be n-parameter transformers
    for (const key in context) {
        if (typeof context[key] === 'function') {
            try {
                const result = context[key](nParam);
                // N-parameter functions typically return different values
                if (typeof result === 'string' && result !== nParam && result.length > 0) {
                    // Check for known n-parameter transformation patterns
                    if (!result.includes('enhanced_except_') && !result.endsWith('_w8_' + nParam)) {
                        return result;
                    }
                }
            } catch (e) {
                // Function failed, try next
            }
        }
    }
    
    return null;
}

/**
 * Fallback: use pattern matching to find and execute functions
 */
function fallbackPatternMatching(playerScript, input) {
    const result = {
        signature: null,
        nParameter: null
    };
    
    // Pattern for signature functions
    const sigPatterns = [
        /function\s*\([^)]+\)\s*\{[^}]*split\s*\(\s*["']\s*["']\s*\)[^}]*\}/g,
        /[a-zA-Z_$][a-zA-Z0-9_$]*\s*=\s*function\s*\([^)]+\)\s*\{[^}]*split[^}]*\}/g
    ];
    
    for (const pattern of sigPatterns) {
        const matches = playerScript.match(pattern);
        if (matches) {
            for (const funcCode of matches) {
                try {
                    const sandbox = createSandbox();
                    const context = vm.createContext(sandbox);
                    vm.runInContext(funcCode, context, { timeout: 1000 });
                    
                    // Try to execute the function
                    const funcName = extractFunctionName(funcCode);
                    if (funcName && context[funcName]) {
                        const decrypted = context[funcName](input.signature);
                        if (decrypted && decrypted !== input.signature) {
                            result.signature = decrypted;
                            break;
                        }
                    }
                } catch (e) {
                    // Try next function
                }
            }
        }
        if (result.signature) break;
    }
    
    // Pattern for n-parameter functions
    const nPatterns = [
        /function\s*\([^)]+\)\s*\{[^}]*enhanced_except[^}]*\}/g,
        /function\s*\([^)]+\)\s*\{[^}]*_w8_[^}]*\}/g
    ];
    
    for (const pattern of nPatterns) {
        const matches = playerScript.match(pattern);
        if (matches) {
            for (const funcCode of matches) {
                try {
                    const sandbox = createSandbox();
                    const context = vm.createContext(sandbox);
                    vm.runInContext(funcCode, context, { timeout: 1000 });
                    
                    // Try to execute the function
                    const funcName = extractFunctionName(funcCode);
                    if (funcName && context[funcName]) {
                        const transformed = context[funcName](input.nParameter);
                        if (transformed && transformed !== input.nParameter) {
                            result.nParameter = transformed;
                            break;
                        }
                    }
                } catch (e) {
                    // Try next function
                }
            }
        }
        if (result.nParameter) break;
    }
    
    return result;
}

/**
 * Create a sandboxed context for safe execution
 */
function createSandbox() {
    return {
        console: console,
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
        atob: (b64) => Buffer.from(b64, 'base64').toString(),
        Error: Error,
        TypeError: TypeError
    };
}

/**
 * Check if a function looks like a cipher function
 */
function looksLikeCipherFunction(funcStr) {
    // Signature functions typically have split, reverse, slice, or splice
    const sigOps = ['split', 'reverse', 'slice', 'splice', 'join'];
    const hasSigOps = sigOps.some(op => funcStr.includes(op));
    
    // N-parameter functions have specific patterns
    const hasNPatterns = funcStr.includes('enhanced_except') || 
                         funcStr.includes('_w8_') ||
                         funcStr.includes('try') && funcStr.includes('catch');
    
    return hasSigOps || hasNPatterns;
}

/**
 * Detect the type of cipher function
 */
function detectFunctionType(funcStr) {
    if (funcStr.includes('split') && (funcStr.includes('reverse') || funcStr.includes('slice'))) {
        return 'signature';
    }
    if (funcStr.includes('enhanced_except') || funcStr.includes('_w8_')) {
        return 'n_parameter';
    }
    return 'unknown';
}

/**
 * Check if two strings have the same characters (for signature validation)
 */
function hasSameCharacters(str1, str2) {
    if (str1.length !== str2.length) return false;
    const chars1 = str1.split('').sort().join('');
    const chars2 = str2.split('').sort().join('');
    return chars1 === chars2;
}

/**
 * Extract function name from function code
 */
function extractFunctionName(funcCode) {
    const patterns = [
        /function\s+([a-zA-Z_$][a-zA-Z0-9_$]*)/,
        /([a-zA-Z_$][a-zA-Z0-9_$]*)\s*=\s*function/,
        /var\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\s*=\s*function/
    ];
    
    for (const pattern of patterns) {
        const match = funcCode.match(pattern);
        if (match) return match[1];
    }
    
    return null;
}

// Handle process termination gracefully
process.on('SIGTERM', () => {
    process.exit(0);
});

process.on('SIGINT', () => {
    process.exit(0);
});