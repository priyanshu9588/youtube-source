package dev.lavalink.youtube.cipher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fallback cipher extractor with multiple pattern strategies
 * This class attempts to extract cipher functions using various patterns
 * to handle different versions of YouTube's player scripts
 */
public class FallbackCipherExtractor {
    private static final Logger log = LoggerFactory.getLogger(FallbackCipherExtractor.class);
    
    private static final String VARIABLE_PART = "[a-zA-Z_\\$][a-zA-Z_0-9\\$]*";
    
    // Multiple signature function patterns to try
    private static final List<Pattern> SIG_FUNCTION_PATTERNS = new ArrayList<>();
    static {
        // Original pattern
        SIG_FUNCTION_PATTERNS.add(Pattern.compile(
            "function(?:\\s+" + VARIABLE_PART + ")?\\((" + VARIABLE_PART + ")\\)\\{" +
            VARIABLE_PART + "=" + VARIABLE_PART + ".*?\\(\\1,\\d+\\);return\\s*\\1.*};"
        ));
        
        // Pattern for variable assignment functions
        SIG_FUNCTION_PATTERNS.add(Pattern.compile(
            "(" + VARIABLE_PART + ")=function\\((" + VARIABLE_PART + ")\\)\\{" +
            "[^}]*split\\(\"\"\\)[^}]*return[^}]+\\};"
        ));
        
        // Pattern for newer YouTube scripts
        SIG_FUNCTION_PATTERNS.add(Pattern.compile(
            "(?:function\\s+)?(" + VARIABLE_PART + ")\\s*=\\s*function\\s*\\(\\s*(" + VARIABLE_PART + ")\\s*\\)\\s*\\{" +
            "[^}]*\\.split\\(\\s*\"\"\\s*\\)[^}]*return[^}]+\\}"
        ));
        
        // Pattern looking for characteristic split-reverse-join operations
        SIG_FUNCTION_PATTERNS.add(Pattern.compile(
            "(" + VARIABLE_PART + ")\\s*=\\s*function\\s*\\([^)]+\\)\\s*\\{" +
            "[^}]*split[^}]*reverse[^}]*join[^}]+\\}"
        ));
        
        // More flexible pattern
        SIG_FUNCTION_PATTERNS.add(Pattern.compile(
            "(?:var\\s+)?(" + VARIABLE_PART + ")\\s*=\\s*function\\s*\\(\\s*[^)]+\\s*\\)\\s*\\{" +
            "[^}]{0,500}split\\([^)]*\\)[^}]{0,500}\\}"
        ));
    }
    
    // Multiple n-function patterns
    private static final List<Pattern> N_FUNCTION_PATTERNS = new ArrayList<>();
    static {
        // Original pattern
        N_FUNCTION_PATTERNS.add(Pattern.compile(
            "function\\(\\s*(" + VARIABLE_PART + ")\\s*\\)\\s*\\{" +
            "var\\s*(" + VARIABLE_PART + ")=\\1\\[" + VARIABLE_PART + "\\[\\d+\\]\\]\\(" + VARIABLE_PART + "\\[\\d+\\]\\)" +
            ".*?catch\\(\\s*(\\w+)\\s*\\)\\s*\\{" +
            "\\s*return.*?\\+\\s*\\1\\s*}" +
            "\\s*return\\s*\\2\\[" + VARIABLE_PART + "\\[\\d+\\]\\]\\(" + VARIABLE_PART + "\\[\\d+\\]\\)};",
            Pattern.DOTALL
        ));
        
        // Simplified pattern for n-function
        N_FUNCTION_PATTERNS.add(Pattern.compile(
            "function\\s*\\([^)]+\\)\\s*\\{[^}]*enhanced_except_[^}]+\\}",
            Pattern.DOTALL
        ));
        
        // Alternative n-function pattern
        N_FUNCTION_PATTERNS.add(Pattern.compile(
            "(" + VARIABLE_PART + ")\\s*=\\s*function\\s*\\([^)]+\\)\\s*\\{" +
            "[^}]*_w8_[^}]+\\}",
            Pattern.DOTALL
        ));
    }
    
    /**
     * Try to extract signature function using multiple patterns
     */
    public static String extractSignatureFunction(String script) {
        for (Pattern pattern : SIG_FUNCTION_PATTERNS) {
            try {
                Matcher matcher = pattern.matcher(script);
                if (matcher.find()) {
                    String function = matcher.group(0);
                    log.debug("Found signature function with pattern: {}", pattern.pattern().substring(0, Math.min(50, pattern.pattern().length())));
                    return function;
                }
            } catch (Exception e) {
                log.debug("Pattern failed: {}", e.getMessage());
            }
        }
        
        // If all patterns fail, try to use Node.js to find the function
        return tryNodeJsExtraction(script, "signature");
    }
    
    /**
     * Try to extract n-function using multiple patterns
     */
    public static String extractNFunction(String script) {
        for (Pattern pattern : N_FUNCTION_PATTERNS) {
            try {
                Matcher matcher = pattern.matcher(script);
                if (matcher.find()) {
                    String function = matcher.group(0);
                    log.debug("Found n-function with pattern: {}", pattern.pattern().substring(0, Math.min(50, pattern.pattern().length())));
                    return function;
                }
            } catch (Exception e) {
                log.debug("Pattern failed: {}", e.getMessage());
            }
        }
        
        // If all patterns fail, try to use Node.js to find the function
        return tryNodeJsExtraction(script, "n_parameter");
    }
    
    /**
     * Use Node.js to help identify and extract cipher functions
     * This is a last resort when regex patterns fail
     */
    private static String tryNodeJsExtraction(String script, String type) {
        try {
            NodeJsCipherExecutor executor = new NodeJsCipherExecutor();
            
            // Try to execute the entire script and look for characteristic functions
            String testInput = "test123";
            String result = executor.executeOneShot("probe", testInput, null, null, script);
            
            // If we get a result, the script contains valid cipher functions
            if (result != null && !result.equals(testInput)) {
                log.info("Successfully identified cipher function using Node.js probe");
                // Return a placeholder - the actual function will be executed via Node.js
                return "nodejs_fallback";
            }
        } catch (IOException e) {
            log.debug("Node.js extraction failed: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Alternative approach: find functions by looking for known YouTube cipher operations
     */
    public static String findSignatureFunctionByOperations(String script) {
        // Look for functions that contain the characteristic operations: split, reverse, slice, splice
        Pattern operationsPattern = Pattern.compile(
            "function\\s*\\([^)]+\\)\\s*\\{[^}]*(?:split|reverse|slice|splice)[^}]+\\}",
            Pattern.DOTALL
        );
        
        Matcher matcher = operationsPattern.matcher(script);
        List<String> candidates = new ArrayList<>();
        
        while (matcher.find()) {
            String candidate = matcher.group(0);
            // Check if this looks like a signature function
            if (candidate.contains("split") && 
                (candidate.contains("reverse") || candidate.contains("slice") || candidate.contains("splice"))) {
                candidates.add(candidate);
            }
        }
        
        // Return the most likely candidate (usually the one with the most operations)
        return candidates.stream()
            .max((a, b) -> Integer.compare(countOperations(a), countOperations(b)))
            .orElse(null);
    }
    
    private static int countOperations(String function) {
        int count = 0;
        String[] operations = {"split", "reverse", "slice", "splice", "join"};
        for (String op : operations) {
            if (function.contains(op)) count++;
        }
        return count;
    }
}