import dev.lavalink.youtube.cipher.NodeJsRunner;
import java.io.IOException;

public class test_nodejs_runner {
    public static void main(String[] args) {
        try {
            // Test simple function execution
            String jsCode = "function testFunc(input) { return 'Hello ' + input; }";
            String result = NodeJsRunner.execute(jsCode, "testFunc", "World");
            System.out.println("Test 1 - Simple function: " + result);
            
            // Test function with array manipulation (similar to signature functions)
            jsCode = "function transformArray(input) { " +
                     "  var arr = input.split('');" +
                     "  arr.reverse();" + 
                     "  return arr.join('');" +
                     "}";
            result = NodeJsRunner.execute(jsCode, "transformArray", "test123");
            System.out.println("Test 2 - Array manipulation: " + result);
            
            // Test with multiple parameters
            jsCode = "function concat(a, b) { return a + b; }";
            result = NodeJsRunner.execute(jsCode, "concat", "Hello", "World");
            System.out.println("Test 3 - Multiple parameters: " + result);
            
            System.out.println("\nAll tests passed successfully!");
            
        } catch (IOException e) {
            System.err.println("Error executing Node.js: " + e.getMessage());
            e.printStackTrace();
        }
    }
}