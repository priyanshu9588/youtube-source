import java.io.*;
import java.util.regex.*;
import java.net.*;

public class test_extraction {
    public static void main(String[] args) throws Exception {
        // Download current YouTube player script
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
        
        // Test signature function patterns
        String[] sigPatterns = {
            "encodeURIComponent\\(([a-zA-Z0-9$_]{2,})\\(",
            "\\b([a-zA-Z0-9$_]{2,})\\s*=\\s*function\\(\\s*a\\s*\\)\\s*\\{\\s*a\\s*=\\s*a\\.split\\(\\s*\"\"\\s*\\)"
        };
        
        System.out.println("\n=== Testing Signature Patterns ===");
        for (String pattern : sigPatterns) {
            System.out.println("Pattern: " + pattern);
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(script);
            int count = 0;
            while (m.find() && count < 3) {
                for (int i = 1; i <= m.groupCount(); i++) {
                    String group = m.group(i);
                    if (group != null) {
                        // Check if this is a function
                        if (script.contains(group + "=function") || script.contains("function " + group)) {
                            System.out.println("  Found function: " + group);
                            count++;
                            break;
                        }
                    }
                }
            }
            if (count == 0) {
                System.out.println("  No matches found");
            }
        }
        
        // Test n-function patterns  
        String[] nPatterns = {
            "\\b([a-zA-Z0-9$_]+)\\s*=\\s*function\\([^)]*\\)\\s*\\{[^}]*enhanced_except[^}]*\\}",
            "([a-zA-Z0-9$_]+)\\s*=\\s*function\\(([a-zA-Z0-9_$]+)\\)[^{]*\\{[^}]*try[^}]*catch[^}]*return[^}]*\\+[^}]*\\2[^}]*\\}"
        };
        
        System.out.println("\n=== Testing N-Transform Patterns ===");
        for (String pattern : nPatterns) {
            System.out.println("Pattern: " + pattern.substring(0, Math.min(pattern.length(), 50)) + "...");
            try {
                Pattern p = Pattern.compile(pattern, Pattern.DOTALL);
                Matcher m = p.matcher(script);
                if (m.find()) {
                    System.out.println("  Found: " + m.group(1));
                } else {
                    System.out.println("  No match");
                }
            } catch (Exception e) {
                System.out.println("  Error: " + e.getMessage());
            }
        }
    }
}