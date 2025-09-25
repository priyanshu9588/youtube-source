package dev.lavalink.youtube.cipher;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.*;

/**
 * Executes YouTube cipher functions using Node.js
 * This approach is based on pytubefix's solution which uses Node.js
 * to properly execute YouTube's complex JavaScript cipher functions
 */
public class NodeJsCipherExecutor {
    private static final Logger log = LoggerFactory.getLogger(NodeJsCipherExecutor.class);
    private static final int PROCESS_TIMEOUT_SECONDS = 10;
    
    private Process nodeProcess;
    private BufferedWriter processWriter;
    private BufferedReader processReader;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final String scriptPath;
    private boolean isInitialized = false;
    
    public NodeJsCipherExecutor() throws IOException {
        this.scriptPath = extractNodeScript();
        initializeNodeProcess();
    }
    
    /**
     * Extract the Node.js script from resources to a temporary file
     */
    private String extractNodeScript() throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("youtube-cipher-executor.js")) {
            if (is == null) {
                throw new IOException("youtube-cipher-executor.js not found in resources");
            }
            
            Path tempFile = Files.createTempFile("youtube-cipher-executor", ".js");
            tempFile.toFile().deleteOnExit();
            Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
            
            log.debug("Extracted Node.js script to: {}", tempFile);
            return tempFile.toString();
        }
    }
    
    /**
     * Initialize the Node.js process
     */
    private synchronized void initializeNodeProcess() throws IOException {
        if (isInitialized && nodeProcess != null && nodeProcess.isAlive()) {
            return;
        }
        
        try {
            // Check if Node.js is available
            ProcessBuilder checkNode = new ProcessBuilder("node", "--version");
            Process checkProcess = checkNode.start();
            if (!checkProcess.waitFor(5, TimeUnit.SECONDS) || checkProcess.exitValue() != 0) {
                throw new IOException("Node.js is not available. Please install Node.js to use YouTube decryption.");
            }
            
            // Start the Node.js process
            ProcessBuilder pb = new ProcessBuilder("node", scriptPath);
            pb.redirectErrorStream(false);
            nodeProcess = pb.start();
            
            processWriter = new BufferedWriter(new OutputStreamWriter(nodeProcess.getOutputStream(), StandardCharsets.UTF_8));
            processReader = new BufferedReader(new InputStreamReader(nodeProcess.getInputStream(), StandardCharsets.UTF_8));
            
            // Start error stream reader to prevent blocking
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(nodeProcess.getErrorStream()));
            executor.submit(() -> {
                String line;
                try {
                    while ((line = errorReader.readLine()) != null) {
                        log.warn("Node.js error output: {}", line);
                    }
                } catch (IOException e) {
                    // Process terminated
                }
            });
            
            isInitialized = true;
            log.info("Node.js cipher executor initialized successfully");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while checking Node.js availability", e);
        }
    }
    
    /**
     * Execute a signature decryption function
     */
    public String decryptSignature(String signature, String globalVars, String actionsCode, String functionCode) throws IOException {
        JsonObject request = new JsonObject();
        request.put("type", "signature");
        request.put("input", signature);
        request.put("globalVars", globalVars);
        request.put("actionsCode", actionsCode);
        request.put("functionCode", functionCode);
        
        return executeRequest(request);
    }
    
    /**
     * Execute an n-parameter transformation function
     */
    public String transformNParameter(String nParam, String globalVars, String functionCode) throws IOException {
        JsonObject request = new JsonObject();
        request.put("type", "n_parameter");
        request.put("input", nParam);
        request.put("globalVars", globalVars);
        request.put("functionCode", functionCode);
        
        return executeRequest(request);
    }
    
    /**
     * Execute a request to the Node.js process
     */
    private synchronized String executeRequest(JsonObject request) throws IOException {
        // Ensure process is initialized
        if (!isInitialized || nodeProcess == null || !nodeProcess.isAlive()) {
            log.info("Reinitializing Node.js process");
            cleanup();
            initializeNodeProcess();
        }
        
        try {
            // Send request to Node.js process
            String requestJson = JsonWriter.string(request);
            processWriter.write(requestJson);
            processWriter.newLine();
            processWriter.flush();
            
            // Read response with timeout
            Future<String> future = executor.submit(() -> processReader.readLine());
            String response = future.get(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            if (response == null) {
                throw new IOException("Node.js process returned null response");
            }
            
            // Parse response
            JsonObject responseObj = JsonParser.object().from(response);
            if (responseObj.getBoolean("success")) {
                return responseObj.getString("result");
            } else {
                String error = responseObj.getString("error");
                String stack = responseObj.has("stack") ? responseObj.getString("stack") : "";
                throw new IOException("Node.js execution failed: " + error + "\n" + stack);
            }
            
        } catch (JsonParserException e) {
            throw new IOException("Failed to parse Node.js response", e);
        } catch (TimeoutException e) {
            log.error("Node.js process timed out");
            cleanup();
            throw new IOException("Node.js process timed out", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for Node.js response", e);
        } catch (ExecutionException e) {
            throw new IOException("Error executing Node.js request", e.getCause());
        }
    }
    
    /**
     * Alternative method using a new process for each request (more reliable but slower)
     */
    public String executeOneShot(String type, String input, String globalVars, String actionsCode, String functionCode) throws IOException {
        JsonObject request = new JsonObject();
        request.put("type", type);
        request.put("input", input);
        if (globalVars != null) request.put("globalVars", globalVars);
        if (actionsCode != null) request.put("actionsCode", actionsCode);
        if (functionCode != null) request.put("functionCode", functionCode);
        
        String requestJson = JsonWriter.string(request);
        
        try {
            ProcessBuilder pb = new ProcessBuilder("node", scriptPath);
            Process process = pb.start();
            
            // Send request
            try (BufferedWriter bwriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                bwriter.write(requestJson);
                bwriter.newLine();
                bwriter.flush();
            }
            
            // Read response
            boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Node.js process timed out");
            }
            
            String response;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                response = reader.readLine();
            }
            
            if (response == null) {
                // Read error output
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    StringBuilder errorOutput = new StringBuilder();
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                    throw new IOException("Node.js process failed: " + errorOutput);
                }
            }
            
            // Parse response
            try {
                JsonObject responseObj = JsonParser.object().from(response);
                if (responseObj.getBoolean("success")) {
                    return responseObj.getString("result");
                } else {
                    String error = responseObj.getString("error");
                    throw new IOException("Node.js execution failed: " + error);
                }
            } catch (JsonParserException e) {
                throw new IOException("Failed to parse Node.js response: " + response, e);
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while executing Node.js", e);
        }
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        isInitialized = false;
        
        if (processWriter != null) {
            try {
                processWriter.close();
            } catch (IOException e) {
                log.warn("Error closing process writer", e);
            }
        }
        
        if (processReader != null) {
            try {
                processReader.close();
            } catch (IOException e) {
                log.warn("Error closing process reader", e);
            }
        }
        
        if (nodeProcess != null) {
            nodeProcess.destroy();
            try {
                if (!nodeProcess.waitFor(5, TimeUnit.SECONDS)) {
                    nodeProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                nodeProcess.destroyForcibly();
            }
        }
    }
    
    /**
     * Ensure cleanup when no longer needed
     */
    public void shutdown() {
        cleanup();
        executor.shutdown();
    }
}