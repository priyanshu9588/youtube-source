package dev.lavalink.youtube.cipher;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A sophisticated JavaScript function extractor that uses parsing techniques
 * similar to yt-dlp's approach, but adapted for Java.
 */
public class JavaScriptExtractor {
    private static final Logger log = LoggerFactory.getLogger(JavaScriptExtractor.class);
    
    // JavaScript identifier pattern
    private static final String JS_IDENTIFIER = "[a-zA-Z_$][a-zA-Z0-9_$]*";
    
    // Pattern to match function declarations
    private static final Pattern FUNCTION_PATTERN = Pattern.compile(
        "(?:" +
        "function\\s+(" + JS_IDENTIFIER + ")\\s*\\([^)]*\\)" +  // function name(params)
        "|" +
        "(" + JS_IDENTIFIER + ")\\s*=\\s*function\\s*\\([^)]*\\)" +  // name = function(params)
        "|" +
        "(?:var|let|const)\\s+(" + JS_IDENTIFIER + ")\\s*=\\s*function\\s*\\([^)]*\\)" +  // var name = function(params)
        ")"
    );
    
    private final String script;
    private Map<String, String> functionBodies;
    private Map<String, Set<String>> functionCalls;
    
    public JavaScriptExtractor(@NotNull String script) {
        this.script = script;
        this.functionBodies = new HashMap<>();
        this.functionCalls = new HashMap<>();
        extractFunctions();
    }
    
    /**
     * Extract all functions from the script
     */
    private void extractFunctions() {
        Matcher matcher = FUNCTION_PATTERN.matcher(script);
        
        while (matcher.find()) {
            String funcName = null;
            for (int i = 1; i <= matcher.groupCount(); i++) {
                if (matcher.group(i) != null) {
                    funcName = matcher.group(i);
                    break;
                }
            }
            
            if (funcName != null) {
                int start = matcher.end();
                String body = extractFunctionBody(start);
                if (body != null) {
                    functionBodies.put(funcName, body);
                    extractFunctionCalls(funcName, body);
                }
            }
        }
    }
    
    /**
     * Extract the body of a function starting from the given position
     */
    @Nullable
    private String extractFunctionBody(int startPos) {
        // Find the opening brace
        int braceStart = script.indexOf('{', startPos);
        if (braceStart == -1) return null;
        
        // Count braces to find the matching closing brace
        int braceCount = 1;
        int pos = braceStart + 1;
        boolean inString = false;
        boolean inRegex = false;
        char stringDelim = 0;
        
        while (pos < script.length() && braceCount > 0) {
            char c = script.charAt(pos);
            char prev = pos > 0 ? script.charAt(pos - 1) : 0;
            
            // Handle string literals
            if (!inRegex && (c == '"' || c == '\'' || c == '`') && prev != '\\') {
                if (!inString) {
                    inString = true;
                    stringDelim = c;
                } else if (c == stringDelim) {
                    inString = false;
                }
            }
            
            // Handle regex literals
            if (!inString && c == '/' && prev != '\\') {
                // Simple regex detection (not perfect but good enough)
                if (!inRegex && isRegexStart(pos)) {
                    inRegex = true;
                } else if (inRegex) {
                    inRegex = false;
                }
            }
            
            // Count braces only outside strings and regex
            if (!inString && !inRegex) {
                if (c == '{') {
                    braceCount++;
                } else if (c == '}') {
                    braceCount--;
                }
            }
            
            pos++;
        }
        
        if (braceCount == 0) {
            return script.substring(braceStart, pos);
        }
        return null;
    }
    
    /**
     * Check if a '/' at the given position starts a regex
     */
    private boolean isRegexStart(int pos) {
        // Simple heuristic: if preceded by =, (, [, {, ,, ;, !, :, &, |, ^, return, it's likely a regex
        if (pos == 0) return true;
        
        int checkPos = pos - 1;
        while (checkPos >= 0 && Character.isWhitespace(script.charAt(checkPos))) {
            checkPos--;
        }
        
        if (checkPos < 0) return true;
        
        char prev = script.charAt(checkPos);
        return "=([{,;!:&|^\n".indexOf(prev) != -1 || 
               script.substring(Math.max(0, checkPos - 5), checkPos + 1).endsWith("return");
    }
    
    /**
     * Extract function calls from a function body
     */
    private void extractFunctionCalls(@NotNull String funcName, @NotNull String body) {
        Set<String> calls = new HashSet<>();
        
        // Pattern to match function calls
        Pattern callPattern = Pattern.compile("(" + JS_IDENTIFIER + ")\\s*\\(");
        Matcher matcher = callPattern.matcher(body);
        
        while (matcher.find()) {
            String calledFunc = matcher.group(1);
            // Exclude common JavaScript built-ins
            if (!isBuiltInFunction(calledFunc)) {
                calls.add(calledFunc);
            }
        }
        
        functionCalls.put(funcName, calls);
    }
    
    /**
     * Check if a function name is a JavaScript built-in
     */
    private boolean isBuiltInFunction(@NotNull String name) {
        Set<String> builtIns = new HashSet<>(Arrays.asList(
            "parseInt", "parseFloat", "isNaN", "isFinite", "eval",
            "String", "Number", "Boolean", "Array", "Object", "Function",
            "Math", "Date", "RegExp", "Error", "JSON",
            "console", "alert", "prompt", "confirm",
            "setTimeout", "setInterval", "clearTimeout", "clearInterval",
            "encodeURI", "encodeURIComponent", "decodeURI", "decodeURIComponent"
        ));
        return builtIns.contains(name);
    }
    
    /**
     * Find the signature transformation function
     * This function typically:
     * 1. Takes a single parameter
     * 2. Splits it into an array (or is already an array)
     * 3. Performs transformations (reverse, splice, swap)
     * 4. Returns the result (joined or as array)
     * In modern YouTube players, often called like: EP(4, decodeURIComponent(I.s))
     */
    @Nullable
    public SignatureFunctionInfo findSignatureFunction() {
        log.debug("Searching for signature function among {} functions", functionBodies.size());
        
        // First, try to find by looking for decodeURIComponent pattern
        // Pattern like: EP(4, decodeURIComponent(I.s))
        Pattern decodePattern = Pattern.compile(
            "(" + JS_IDENTIFIER + ")\\(\\d+,\\s*decodeURIComponent\\([^)]+\\.s\\)\\)"
        );
        
        Matcher decodeMatcher = decodePattern.matcher(script);
        if (decodeMatcher.find()) {
            String sigFuncName = decodeMatcher.group(1);
            log.debug("Found signature function via decodeURIComponent pattern: {}", sigFuncName);
            
            String funcBody = functionBodies.get(sigFuncName);
            if (funcBody != null) {
                // Look for helper objects - in modern scripts it might use Y array references
                String helperObject = findTransformationHelper(funcBody);
                if (helperObject != null) {
                    log.debug("Found transformation helper: {}", helperObject);
                    String helperBody = findObjectDefinition(helperObject);
                    if (helperBody != null) {
                        return new SignatureFunctionInfo(sigFuncName, funcBody, helperObject, helperBody);
                    }
                }
                // Even without helper, return the function
                return new SignatureFunctionInfo(sigFuncName, funcBody, null, null);
            }
        }
        
        // Fallback to heuristic-based search
        for (Map.Entry<String, String> entry : functionBodies.entrySet()) {
            String funcName = entry.getKey();
            String body = entry.getValue();
            
            // Check if this looks like a signature function
            if (looksLikeSignatureFunction(funcName, body)) {
                log.debug("Found potential signature function by heuristics: {}", funcName);
                
                // Find the transformation helper object
                String helperObject = findTransformationHelper(body);
                if (helperObject != null) {
                    log.debug("Found transformation helper: {}", helperObject);
                    String helperBody = findObjectDefinition(helperObject);
                    if (helperBody != null) {
                        return new SignatureFunctionInfo(funcName, body, helperObject, helperBody);
                    }
                }
                
                // Even without helper, might still work
                return new SignatureFunctionInfo(funcName, body, null, null);
            }
        }
        
        return null;
    }
    
    /**
     * Check if a function looks like a signature transformation function
     */
    private boolean looksLikeSignatureFunction(@NotNull String funcName, @NotNull String body) {
        // Exclude functions that are clearly NOT signature functions
        if (body.contains("performance.now()") ||
            body.contains("requestIdleCallback") ||
            body.contains("setTimeout") ||
            body.contains("setInterval") ||
            body.contains("Promise") ||
            body.contains("async") ||
            body.contains("await") ||
            body.contains("switch(R.X)") ||
            body.contains(".lB(") ||
            body.contains("RN(") ||
            body.contains("g.J7(") ||
            body.contains("void 0") && body.length() > 300) {
            return false;
        }
        
        // Heuristics for signature function:
        // 1. Has a single parameter
        // 2. Contains array operations or split/join
        // 3. Returns something
        // 4. May call a helper function with (param, number)
        // 5. Should be relatively short (< 500 chars typically)
        
        // Check for single parameter
        Pattern paramPattern = Pattern.compile("function\\s*\\(\\s*(" + JS_IDENTIFIER + ")\\s*\\)");
        Matcher paramMatcher = paramPattern.matcher(body);
        if (!paramMatcher.find()) return false;
        
        String param = paramMatcher.group(1);
        
        // Check for array operations or transformations
        boolean hasArrayOps = body.contains(".split(") || body.contains(".join(") ||
                              body.contains(".reverse(") || body.contains(".splice(");
        
        // Check for calls with (param, number) pattern - helper object methods
        Pattern callPattern = Pattern.compile("(" + JS_IDENTIFIER + ")\\s*\\(\\s*" + Pattern.quote(param) + "\\s*,\\s*\\d+\\s*\\)");
        boolean hasParamCall = callPattern.matcher(body).find();
        
        // Also check for object method calls on the parameter like: XX.YY(param, number)
        Pattern objectCallPattern = Pattern.compile(JS_IDENTIFIER + "\\." + JS_IDENTIFIER + "\\(\\s*" + Pattern.quote(param) + "\\s*,\\s*\\d+\\s*\\)");
        boolean hasObjectCall = objectCallPattern.matcher(body).find();
        
        // Check for return statement
        boolean hasReturn = body.contains("return ");
        
        // Signature functions are typically short and focused
        boolean reasonableLength = body.length() < 800;
        
        return hasReturn && reasonableLength && (hasArrayOps || hasParamCall || hasObjectCall);
    }
    
    /**
     * Find the transformation helper object used in a function
     */
    @Nullable
    private String findTransformationHelper(@NotNull String body) {
        // Look for pattern like: obj.method(param, number) or obj[prop](param, number)
        Pattern helperPattern = Pattern.compile(
            "(" + JS_IDENTIFIER + ")(?:\\.(" + JS_IDENTIFIER + ")|\\[.*?\\])\\s*\\([^,)]+,\\s*\\d+\\s*\\)"
        );
        
        Matcher matcher = helperPattern.matcher(body);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }
    
    /**
     * Find the definition of an object
     */
    @Nullable
    private String findObjectDefinition(@NotNull String objectName) {
        // Look for: var obj = {...} or obj = {...}
        String pattern = "(?:var\\s+|let\\s+|const\\s+)?" + Pattern.quote(objectName) + "\\s*=\\s*\\{";
        Pattern objPattern = Pattern.compile(pattern);
        Matcher matcher = objPattern.matcher(script);
        
        if (matcher.find()) {
            int start = matcher.end() - 1; // Include the opening brace
            String body = extractObjectBody(start);
            return body;
        }
        
        return null;
    }
    
    /**
     * Extract an object literal body
     */
    @Nullable
    private String extractObjectBody(int startPos) {
        // Similar to extractFunctionBody but for object literals
        return extractFunctionBody(startPos - 1); // Reuse the brace matching logic
    }
    
    /**
     * Find the n-parameter transformation function
     * This function typically:
     * 1. Takes a single parameter
     * 2. Has try-catch block
     * 3. Returns modified parameter or concatenated result
     */
    @Nullable
    public NFunctionInfo findNFunction() {
        log.debug("Searching for n-function among {} functions", functionBodies.size());
        
        for (Map.Entry<String, String> entry : functionBodies.entrySet()) {
            String funcName = entry.getKey();
            String body = entry.getValue();
            
            if (looksLikeNFunction(funcName, body)) {
                log.debug("Found potential n-function: {}", funcName);
                return new NFunctionInfo(funcName, body);
            }
        }
        
        return null;
    }
    
    /**
     * Check if a function looks like an n-parameter transformation function
     */
    private boolean looksLikeNFunction(@NotNull String funcName, @NotNull String body) {
        // Heuristics for n-function:
        // 1. Has try-catch block
        // 2. Contains "enhanced_except" or similar error handling
        // 3. Returns something with the parameter
        
        boolean hasTryCatch = body.contains("try") && body.contains("catch");
        boolean hasEnhancedExcept = body.contains("enhanced_except") || 
                                    body.contains("_w8_");
        boolean hasReturn = body.contains("return");
        
        // Also check for array indexing patterns common in n-function
        Pattern arrayPattern = Pattern.compile("\\[[^\\]]+\\]\\s*\\([^)]+\\)");
        boolean hasArrayCall = arrayPattern.matcher(body).find();
        
        return hasTryCatch && hasReturn && (hasEnhancedExcept || hasArrayCall);
    }
    
    /**
     * Get all extracted functions (for debugging)
     */
    public Map<String, String> getFunctionBodies() {
        return new HashMap<>(functionBodies);
    }
    
    /**
     * Get function dependency graph (for analysis)
     */
    public Map<String, Set<String>> getFunctionCalls() {
        return new HashMap<>(functionCalls);
    }
    
    /**
     * Information about a signature transformation function
     */
    public static class SignatureFunctionInfo {
        public final String functionName;
        public final String functionBody;
        public final String helperObjectName;
        public final String helperObjectBody;
        
        public SignatureFunctionInfo(String functionName, String functionBody,
                                     String helperObjectName, String helperObjectBody) {
            this.functionName = functionName;
            this.functionBody = functionBody;
            this.helperObjectName = helperObjectName;
            this.helperObjectBody = helperObjectBody;
        }
    }
    
    /**
     * Information about an n-parameter transformation function
     */
    public static class NFunctionInfo {
        public final String functionName;
        public final String functionBody;
        
        public NFunctionInfo(String functionName, String functionBody) {
            this.functionName = functionName;
            this.functionBody = functionBody;
        }
    }
}