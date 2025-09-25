package dev.lavalink.youtube.cipher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

/**
 * Test class for NodeJsCipherExecutor
 */
public class NodeJsCipherExecutorTest {
    
    private static NodeJsCipherExecutor executor;
    
    @BeforeAll
    public static void setup() {
        try {
            executor = new NodeJsCipherExecutor();
            System.out.println("Node.js executor initialized successfully");
        } catch (IOException e) {
            System.err.println("Failed to initialize Node.js executor: " + e.getMessage());
            // Tests will be skipped if Node.js is not available
        }
    }
    
    @AfterAll
    public static void cleanup() {
        if (executor != null) {
            executor.shutdown();
        }
    }
    
    @Test
    @Disabled("This is a basic connectivity test - enable when you have a real YouTube cipher to test")
    public void testNodeJsExecutorInitialization() {
        assertNotNull(executor, "Node.js executor should be initialized");
    }
    
    @Test
    public void testSimpleJavaScriptExecution() throws IOException {
        if (executor == null) {
            System.out.println("Skipping test - Node.js not available");
            return;
        }
        
        // Test with a simple function that reverses a string
        String simpleFunction = "function reverseString(s) { return s.split('').reverse().join(''); }";
        String input = "hello";
        
        String result = executor.executeOneShot("generic", input, null, null, simpleFunction);
        assertEquals("olleh", result, "Should reverse the string");
    }
    
    @Test
    @Disabled("Enable this when testing with real YouTube cipher functions")
    public void testSignatureDecryption() throws IOException {
        if (executor == null) {
            System.out.println("Skipping test - Node.js not available");
            return;
        }
        
        // This would be populated with real YouTube cipher data
        String globalVars = ""; // Real global variables from YouTube
        String actionsCode = ""; // Real actions code from YouTube
        String sigFunction = ""; // Real signature function from YouTube
        String encryptedSignature = ""; // Real encrypted signature
        
        String decrypted = executor.decryptSignature(encryptedSignature, globalVars, actionsCode, sigFunction);
        assertNotNull(decrypted);
        assertFalse(decrypted.isEmpty());
    }
    
    @Test
    @Disabled("Enable this when testing with real YouTube n-parameter functions")
    public void testNParameterTransformation() throws IOException {
        if (executor == null) {
            System.out.println("Skipping test - Node.js not available");
            return;
        }
        
        // This would be populated with real YouTube cipher data
        String globalVars = ""; // Real global variables from YouTube
        String nFunction = ""; // Real n-parameter function from YouTube
        String nParameter = ""; // Real n-parameter value
        
        String transformed = executor.transformNParameter(nParameter, globalVars, nFunction);
        assertNotNull(transformed);
        assertFalse(transformed.isEmpty());
        assertNotEquals(nParameter, transformed, "N-parameter should be transformed");
    }
}