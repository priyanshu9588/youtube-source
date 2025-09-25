import dev.lavalink.youtube.cipher.NodeJsRunner;
import java.io.*;
import java.net.*;

public class test_nodejs_actual {
    public static void main(String[] args) throws Exception {
        // Download the actual YouTube script
        URL url = new URL("https://www.youtube.com/s/player/377ca75b/player_ias.vflset/en_GB/base.js");
        BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
        StringBuilder scriptBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            scriptBuilder.append(line).append("\n");
        }
        reader.close();
        String script = scriptBuilder.toString();
        System.out.println("Script downloaded, size: " + script.length());
        
        // Test with the actual signature function
        String sigFunction = "l5r";
        String testSignature = "2aq0aqSyOoJXtK73m-uME_jv7-pT15gOFC02RFkGMqWpzEICs69VdbwQ0LDp1v7j8xx92efCJlYFYb1sUkkBSPOlPmXgIARw8JQ0qOAOAA";
        
        try {
            System.out.println("\nTesting signature transformation...");
            System.out.println("Function: " + sigFunction);
            System.out.println("Input: " + testSignature.substring(0, 50) + "...");
            
            String result = NodeJsRunner.execute(script, sigFunction, testSignature);
            System.out.println("Output: " + (result != null ? result.substring(0, Math.min(50, result.length())) + "..." : "null"));
            
            if (result != null && !result.equals(testSignature)) {
                System.out.println("✓ Signature was transformed successfully!");
            } else {
                System.out.println("✗ Signature transformation failed");
            }
        } catch (Exception e) {
            System.out.println("✗ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}