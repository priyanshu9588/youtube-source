package dev.lavalink.youtube.cipher;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Executes JavaScript code using Node.js process instead of built-in script engine.
 * This approach is more reliable for YouTube's complex cipher functions.
 */
public class NodeJsRunner {
    private static final Logger log = LoggerFactory.getLogger(NodeJsRunner.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int TIMEOUT_SECONDS = 5;
    
    private static Path runnerScriptPath;
    
    static {
        try {
            // Create the runner.js file in a temporary location
            runnerScriptPath = Files.createTempFile("yt-cipher-runner", ".js");
            Files.write(runnerScriptPath, getRunnerScript().getBytes(StandardCharsets.UTF_8));
            runnerScriptPath.toFile().deleteOnExit();
            log.debug("Created Node.js runner script at: {}", runnerScriptPath);
        } catch (IOException e) {
            log.error("Failed to create Node.js runner script", e);
        }
    }
    
    /**
     * Execute a JavaScript function using Node.js
     * 
     * @param jsCode The full JavaScript code containing the function
     * @param functionName The name of the function to execute
     * @param args The arguments to pass to the function
     * @return The result of the function execution
     * @throws IOException If there's an error executing the Node.js process
     */
    @Nullable
    public static String execute(@NotNull String jsCode, @NotNull String functionName, @NotNull Object... args) throws IOException {
        if (runnerScriptPath == null) {
            throw new IOException("Node.js runner script not initialized");
        }
        
        try {
            // Prepare the input JSON
            Map<String, Object> input = new HashMap<>();
            input.put("js_code", jsCode);
            input.put("func_name", functionName);
            input.put("args", Arrays.asList(args));
            
            String inputJson = objectMapper.writeValueAsString(input);
            
            // Execute Node.js with the runner script
            ProcessBuilder pb = new ProcessBuilder("node", runnerScriptPath.toString());
            Process process = pb.start();
            
            try (OutputStream os = process.getOutputStream();
                 OutputStreamWriter writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                writer.write(inputJson);
                writer.flush();
            }
            
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Node.js execution timed out");
            }
            
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String error = readStream(process.getErrorStream());
                throw new IOException("Node.js execution failed with exit code " + exitCode + ": " + error);
            }
            
            String output = readStream(process.getInputStream());
            
            // Parse the JSON response
            Map<String, Object> response = objectMapper.readValue(output, Map.class);
            if (response.containsKey("error")) {
                throw new IOException("JavaScript execution error: " + response.get("error"));
            }
            
            Object result = response.get("result");
            return result != null ? result.toString() : null;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Node.js execution interrupted", e);
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Error executing Node.js", e);
        }
    }
    
    private static String readStream(InputStream stream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }
    
    private static String getRunnerScript() {
        return "const readline = require('readline');\n" +
               "const vm = require('vm');\n" +
               "\n" +
               "const rl = readline.createInterface({\n" +
               "    input: process.stdin,\n" +
               "    output: process.stdout,\n" +
               "    terminal: false\n" +
               "});\n" +
               "\n" +
               "let input = '';\n" +
               "\n" +
               "rl.on('line', (line) => {\n" +
               "    input += line;\n" +
               "});\n" +
               "\n" +
               "rl.on('close', () => {\n" +
               "    try {\n" +
               "        const data = JSON.parse(input);\n" +
               "        const { js_code, func_name, args } = data;\n" +
               "        \n" +
               "        // Create a new context with the JavaScript code\n" +
               "        const context = vm.createContext({});\n" +
               "        \n" +
               "        // Execute the JavaScript code in the context\n" +
               "        vm.runInContext(js_code, context);\n" +
               "        \n" +
               "        // Get the function from the context\n" +
               "        const func = context[func_name];\n" +
               "        \n" +
               "        if (typeof func !== 'function') {\n" +
               "            console.log(JSON.stringify({ error: `${func_name} is not a function` }));\n" +
               "            process.exit(1);\n" +
               "        }\n" +
               "        \n" +
               "        // Execute the function with the provided arguments\n" +
               "        const result = func.apply(null, args);\n" +
               "        \n" +
               "        // Return the result as JSON\n" +
               "        console.log(JSON.stringify({ result: result }));\n" +
               "        \n" +
               "    } catch (error) {\n" +
               "        console.log(JSON.stringify({ error: error.message || error.toString() }));\n" +
               "        process.exit(1);\n" +
               "    }\n" +
               "});\n";
    }
}