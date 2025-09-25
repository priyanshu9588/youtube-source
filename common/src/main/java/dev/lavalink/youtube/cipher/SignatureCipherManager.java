package dev.lavalink.youtube.cipher;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.YoutubeSource;
import dev.lavalink.youtube.cipher.ScriptExtractionException.ExtractionFailureType;
import dev.lavalink.youtube.track.format.StreamFormat;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mozilla.javascript.engine.RhinoScriptEngineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static com.sedmelluq.discord.lavaplayer.tools.ExceptionTools.throwWithDebugInfo;

/**
 * Handles parsing and caching of signature ciphers
 */
@SuppressWarnings({"RegExpRedundantEscape", "RegExpUnnecessaryNonCapturingGroup"})
public class SignatureCipherManager {
  private static final Logger log = LoggerFactory.getLogger(SignatureCipherManager.class);

  private static final String VARIABLE_PART = "[a-zA-Z_\\$][a-zA-Z_0-9\\$]*";
  private static final String VARIABLE_PART_OBJECT_DECLARATION = "[\"']?[a-zA-Z_\\$][a-zA-Z_0-9\\$]*[\"']?";

  private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("(signatureTimestamp|sts):(\\d+)");

  private static final Pattern GLOBAL_VARS_PATTERN = Pattern.compile(
      "('use\\s*strict';)?" +
          "(?<code>var\\s*(?<varname>[a-zA-Z0-9_$]+)\\s*=\\s*" +
          "(?<value>(?:\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"|'[^'\\\\]*(?:\\\\.[^'\\\\]*)*')" +
          "\\.split\\((?:\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"|'[^'\\\\]*(?:\\\\.[^'\\\\]*)*')\\)" +
          "|\\[(?:(?:\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"|'[^'\\\\]*(?:\\\\.[^'\\\\]*)*')\\s*,?\\s*)*\\]" +
          "|\"[^\"]*\"\\.split\\(\"[^\"]*\"\\)))"
  );

  private static final Pattern ACTIONS_PATTERN = Pattern.compile(
      "var\\s+([$A-Za-z0-9_]+)\\s*=\\s*\\{" +
          "\\s*" + VARIABLE_PART_OBJECT_DECLARATION + "\\s*:\\s*function\\s*\\([^)]*\\)\\s*\\{[^{}]*(?:\\{[^{}]*}[^{}]*)*}\\s*," +
          "\\s*" + VARIABLE_PART_OBJECT_DECLARATION + "\\s*:\\s*function\\s*\\([^)]*\\)\\s*\\{[^{}]*(?:\\{[^{}]*}[^{}]*)*}\\s*," +
          "\\s*" + VARIABLE_PART_OBJECT_DECLARATION + "\\s*:\\s*function\\s*\\([^)]*\\)\\s*\\{[^{}]*(?:\\{[^{}]*}[^{}]*)*}\\s*};");

  // Updated pattern for newer YouTube player scripts (as of 2025)
  // The signature function now has a different structure
  private static final Pattern SIG_FUNCTION_PATTERN = Pattern.compile(
      // Try the old pattern first
      "(?:" +
      "function(?:\\s+" + VARIABLE_PART + ")?\\((" + VARIABLE_PART + ")\\)\\{" +
          VARIABLE_PART + "=" + VARIABLE_PART + ".*?\\(\\1,\\d+\\);return\\s*\\1.*};" +
      "|" +
      // New pattern: func(a){...;something(a,number);...;return a}
      "([a-zA-Z_$][a-zA-Z_0-9$]*)=function\\(([a-zA-Z])\\)\\{[^}]*\\(\\2,\\d+\\)[^}]*return[^}]*\\2[^}]*\\}" +
      ")"
  );

  private static final Pattern N_FUNCTION_PATTERN = Pattern.compile(
      "function\\(\\s*(" + VARIABLE_PART + ")\\s*\\)\\s*\\{" +
          "var\\s*(" + VARIABLE_PART + ")=\\1\\[" + VARIABLE_PART + "\\[\\d+\\]\\]\\(" + VARIABLE_PART + "\\[\\d+\\]\\)" +
          ".*?catch\\(\\s*(\\w+)\\s*\\)\\s*\\{" +
          "\\s*return.*?\\+\\s*\\1\\s*}" +
          "\\s*return\\s*\\2\\[" + VARIABLE_PART + "\\[\\d+\\]\\]\\(" + VARIABLE_PART + "\\[\\d+\\]\\)};",
      Pattern.DOTALL
  );

  // old?
  private static final Pattern functionPatternOld = Pattern.compile(
      "function\\(\\s*(\\w+)\\s*\\)\\s*\\{" +
          "var\\s*(\\w+)=\\1\\[" + VARIABLE_PART + "\\[\\d+\\]\\]\\(" + VARIABLE_PART + "\\[\\d+\\]\\)" +
          ".*?catch\\(\\s*(\\w+)\\s*\\)\\s*\\{" +
          "\\s*return.*?\\+\\s*\\1\\s*}" +
          "\\s*return\\s*\\2\\[" + VARIABLE_PART + "\\[\\d+\\]\\]\\(" + VARIABLE_PART + "\\[\\d+\\]\\)};",
      Pattern.DOTALL);

  private final ConcurrentMap<String, SignatureCipher> cipherCache;
  private final Set<String> dumpedScriptUrls;
  private final ScriptEngine scriptEngine;
  private final Object cipherLoadLock;

  protected volatile CachedPlayerScript cachedPlayerScript;

  /**
   * Create a new signature cipher manager
   */
  public SignatureCipherManager() {
    this.cipherCache = new ConcurrentHashMap<>();
    this.dumpedScriptUrls = new HashSet<>();
    this.scriptEngine = new RhinoScriptEngineFactory().getScriptEngine();
    this.cipherLoadLock = new Object();
  }

  /**
   * Produces a valid playback URL for the specified track
   *
   * @param httpInterface HTTP interface to use
   * @param playerScript  Address of the script which is used to decipher signatures
   * @param format        The track for which to get the URL
   * @return Valid playback URL
   * @throws IOException On network IO error
   */
  @NotNull
  public URI resolveFormatUrl(@NotNull HttpInterface httpInterface,
                              @NotNull String playerScript,
                              @NotNull StreamFormat format) throws IOException {
    String signature = format.getSignature();
    String nParameter = format.getNParameter();
    URI initialUrl = format.getUrl();

    URIBuilder uri = new URIBuilder(initialUrl);
    SignatureCipher cipher = getCipherScript(httpInterface, playerScript);

    if (!DataFormatTools.isNullOrEmpty(signature)) {
      try {
        uri.setParameter(format.getSignatureKey(), cipher.apply(signature, scriptEngine));
      } catch (ScriptException | NoSuchMethodException e) {
        // Log the failure but still add the original signature
        log.warn("Failed to transform signature, using original: {}", e.getMessage());
        uri.setParameter(format.getSignatureKey(), signature);
        // Dump the script only once to avoid spam
        if (!dumpedScriptUrls.contains(playerScript + "-sig")) {
          dumpProblematicScript(cipherCache.get(playerScript).rawScript, playerScript, "Can't transform s parameter " + signature);
          dumpedScriptUrls.add(playerScript + "-sig");
        }
      }
    }
      

    if (!DataFormatTools.isNullOrEmpty(nParameter)) {
      try {
        String transformed = cipher.transform(nParameter, scriptEngine);
        String logMessage = null;

        if (transformed == null) {
          logMessage = "Transformed n parameter is null, n function possibly faulty";
        } else if (nParameter.equals(transformed)) {
          logMessage = "Transformed n parameter is the same as input, n function possibly short-circuited";
        } else if (transformed.startsWith("enhanced_except_") || transformed.endsWith("_w8_" + nParameter)) {
          logMessage = "N function did not complete due to exception";
        }

        if (logMessage != null) {
            log.warn("{} (in: {}, out: {}, player script: {}, source version: {})",
                logMessage, nParameter, transformed, playerScript, YoutubeSource.VERSION);
            // Still add the original n parameter even if transformation failed
            uri.setParameter("n", nParameter);
        } else {
            uri.setParameter("n", transformed);
        }
      } catch (Exception e) {
        // URLs can still be played without a resolved n parameter, though they may be throttled
        // Add the original n parameter as fallback
        log.debug("Failed to transform n parameter, using original: {}", e.getMessage());
        uri.setParameter("n", nParameter);
        // Only dump script on first failure to avoid spam
        if (cipherCache.get(playerScript) != null && !dumpedScriptUrls.contains(playerScript + "-nfunc")) {
          dumpProblematicScript(cipherCache.get(playerScript).rawScript, playerScript, "Can't transform n parameter");
          dumpedScriptUrls.add(playerScript + "-nfunc");
        }
      }
    }

    try {
      return uri.build(); // setParameter("ratebypass", "yes")  -- legacy parameter that will give 403 if tampered with.
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  private CachedPlayerScript getPlayerScript(@NotNull HttpInterface httpInterface) {
    synchronized (cipherLoadLock) {
      try (CloseableHttpResponse response = httpInterface.execute(new HttpGet("https://www.youtube.com/embed/"))) {
        HttpClientTools.assertSuccessWithContent(response, "fetch player script (embed)");

        String responseText = EntityUtils.toString(response.getEntity());
        String scriptUrl = DataFormatTools.extractBetween(responseText, "\"jsUrl\":\"", "\"");

        if (scriptUrl == null) {
          throw throwWithDebugInfo(log, null, "no jsUrl found", "html", responseText);
        }

        return (cachedPlayerScript = new CachedPlayerScript(scriptUrl));
      } catch (IOException e) {
        throw ExceptionTools.toRuntimeException(e);
      }
    }
  }

  public CachedPlayerScript getCachedPlayerScript(@NotNull HttpInterface httpInterface) {
    if (cachedPlayerScript == null || System.currentTimeMillis() >= cachedPlayerScript.expireTimestampMs) {
      synchronized (cipherLoadLock) {
        if (cachedPlayerScript == null || System.currentTimeMillis() >= cachedPlayerScript.expireTimestampMs) {
          return getPlayerScript(httpInterface);
        }
      }
    }

    return cachedPlayerScript;
  }

  public SignatureCipher getCipherScript(@NotNull HttpInterface httpInterface,
                                         @NotNull String cipherScriptUrl) throws IOException {
    SignatureCipher cipherKey = cipherCache.get(cipherScriptUrl);

    if (cipherKey == null) {
      synchronized (cipherLoadLock) {
        log.debug("Parsing player script {}", cipherScriptUrl);

        try (CloseableHttpResponse response = httpInterface.execute(new HttpGet(parseTokenScriptUrl(cipherScriptUrl)))) {
          int statusCode = response.getStatusLine().getStatusCode();

          if (!HttpClientTools.isSuccessWithContent(statusCode)) {
            throw new IOException("Received non-success response code " + statusCode + " from script url " +
                cipherScriptUrl + " ( " + parseTokenScriptUrl(cipherScriptUrl) + " )");
          }

          cipherKey = extractFromScript(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8), cipherScriptUrl);
          cipherCache.put(cipherScriptUrl, cipherKey);
        }
      }
    }

    return cipherKey;
  }

  private List<String> getQuotedFunctions(@Nullable String... functionNames) {
    return Stream.of(functionNames)
        .filter(Objects::nonNull)
        .map(Pattern::quote)
        .collect(Collectors.toList());
  }

  private void dumpProblematicScript(@NotNull String script, @NotNull String sourceUrl,
                                     @NotNull String issue) {
    if (!dumpedScriptUrls.add(sourceUrl)) {
      return;
    }

    try {
      Path path = Files.createTempFile("lavaplayer-yt-player-script", ".js");
      Files.write(path, script.getBytes(StandardCharsets.UTF_8));

      log.error("Problematic YouTube player script {} detected (issue detected with script: {}). Dumped to {} (Source version: {})",
          sourceUrl, issue, path.toAbsolutePath(), YoutubeSource.VERSION);
    } catch (Exception e) {
      log.error("Failed to dump problematic YouTube player script {} (issue detected with script: {})", sourceUrl, issue);
    }
  }

  private SignatureCipher extractFromScript(@NotNull String script, @NotNull String sourceUrl) {
    // First, try using the new JavaScript extractor
    JavaScriptExtractor extractor = new JavaScriptExtractor(script);
    
    // Extract timestamp
    Matcher scriptTimestamp = TIMESTAMP_PATTERN.matcher(script);
    if (!scriptTimestamp.find()) {
      // Try alternative timestamp patterns for newer scripts
      Pattern altTimestampPattern = Pattern.compile("\"sts\"\\s*:\\s*(\\d+)");
      Matcher altTimestamp = altTimestampPattern.matcher(script);
      if (altTimestamp.find()) {
        scriptTimestamp = altTimestamp;
      } else {
        scriptExtractionFailed(script, sourceUrl, ExtractionFailureType.TIMESTAMP_NOT_FOUND);
      }
    }
    
    // For modern YouTube scripts, extract the Y array which contains string constants
    String yArrayDef = "";
    Pattern yArrayPattern = Pattern.compile("var\\s+Y\\s*=\\s*\"([^\"]+)\"\\s*\\.\\s*split\\s*\\(\\s*\"([^\"]+)\"\\s*\\)");
    Matcher yArrayMatcher = yArrayPattern.matcher(script);
    if (yArrayMatcher.find()) {
      // Reconstruct the Y array definition as JavaScript
      String yContent = yArrayMatcher.group(1);
      String delimiter = yArrayMatcher.group(2);
      // Create JavaScript array definition
      StringBuilder yArray = new StringBuilder("var Y = [");
      String[] elements = yContent.split(Pattern.quote(delimiter));
      for (int i = 0; i < elements.length; i++) {
        if (i > 0) yArray.append(",");
        yArray.append("\"").append(elements[i]).append("\"");
      }
      yArray.append("];");
      yArrayDef = yArray.toString();
      log.debug("Extracted Y array with {} elements", elements.length);
    }

    // Try to extract signature function using JavaScript extractor
    String sigFunction = "";
    String sigActions = "";
    String nFunction = "";
    String globalVars = "";
    
    JavaScriptExtractor.SignatureFunctionInfo sigInfo = extractor.findSignatureFunction();
    JavaScriptExtractor.NFunctionInfo nInfo = extractor.findNFunction();
    
    if (sigInfo != null) {
      log.debug("Found signature function via JavaScript extractor: {}", sigInfo.functionName);
      sigFunction = sigInfo.functionBody;
      if (sigInfo.helperObjectBody != null) {
        sigActions = sigInfo.helperObjectBody;
      }
      
      // For EP function, we need to extract Co and Hj helpers
      if (sigInfo.functionName.equals("EP")) {
        log.debug("EP function detected, extracting Co and Hj helpers");
        
        // Extract Co object - it contains nested functions so we need proper brace matching
        Pattern coPattern = Pattern.compile("Co\\s*=\\s*\\{");
        Matcher coMatcher = coPattern.matcher(script);
        if (coMatcher.find()) {
          int start = coMatcher.start();
          int braceCount = 0;
          int pos = coMatcher.end() - 1; // Start at the opening brace
          
          while (pos < script.length()) {
            char c = script.charAt(pos);
            if (c == '{') {
              braceCount++;
            } else if (c == '}') {
              braceCount--;
              if (braceCount == 0) {
                String coObject = script.substring(start, pos + 1);
                sigActions = (sigActions != null ? sigActions + ";" : "") + coObject;
                log.debug("Added Co object to actions (length: {})", coObject.length());
                break;
              }
            }
            pos++;
          }
        }
        
        // Extract Hj function if used
        if (sigFunction.contains("Hj(")) {
          Pattern hjPattern = Pattern.compile("Hj\\s*=\\s*function\\([^)]+\\)\\{");
          Matcher hjMatcher = hjPattern.matcher(script);
          if (hjMatcher.find()) {
            int start = hjMatcher.start();
            int braceCount = 0;
            int pos = hjMatcher.end() - 1; // Start at the opening brace
            
            while (pos < script.length()) {
              char c = script.charAt(pos);
              if (c == '{') {
                braceCount++;
              } else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                  String hjFunction = script.substring(start, pos + 1);
                  sigActions = sigActions + ";" + hjFunction;
                  log.debug("Added Hj function to actions (length: {})", hjFunction.length());
                  break;
                }
              }
              pos++;
            }
          }
        }
      }
    }
    
    if (nInfo != null) {
      log.debug("Found n-function via JavaScript extractor: {}", nInfo.functionName);
      nFunction = nInfo.functionBody;
    }
    
    // If JavaScript extractor didn't find functions, fall back to regex patterns
    if (sigFunction.isEmpty() || nFunction.isEmpty()) {
      log.debug("JavaScript extractor found: sig={}, n={}", !sigFunction.isEmpty(), !nFunction.isEmpty());
      log.debug("Falling back to regex patterns");
      
      // Extract global variables using regex as fallback
      Matcher globalVarsMatcher = GLOBAL_VARS_PATTERN.matcher(script);
      if (globalVarsMatcher.find()) {
        globalVars = globalVarsMatcher.group("code");
      } else {
        // Try to find any variable declarations with arrays
        Pattern altGlobalPattern = Pattern.compile("var\\s+([a-zA-Z_$][a-zA-Z_$0-9]*)\\s*=\\s*\\[[^\\]]+\\]");
        Matcher altGlobal = altGlobalPattern.matcher(script);
        if (altGlobal.find()) {
          globalVars = altGlobal.group(0);
        }
      }
      
      // Only use regex fallback if JavaScript extractor didn't find signature function
      if (sigFunction.isEmpty()) {
        // Try to find actions object - be more flexible
        Matcher sigActionsMatcher = ACTIONS_PATTERN.matcher(script);
        
        if (sigActionsMatcher.find()) {
          sigActions = sigActionsMatcher.group(0);
        } else {
          // Try alternative pattern for transformation object
          Pattern altActionsPattern = Pattern.compile(
            "(?:var\\s+)?([a-zA-Z_$][a-zA-Z_$0-9]{0,2})\\s*=\\s*\\{[^}]*(?:splice|reverse)[^}]*\\}"
          );
          Matcher altActions = altActionsPattern.matcher(script);
          if (altActions.find()) {
            sigActions = altActions.group(0);
          } else {
            // For newer scripts, actions might be defined differently
            log.warn("Could not find actions object in script: {}", sourceUrl);
          }
        }
        
        // Try multiple patterns for signature function
        Matcher sigFunctionMatcher = SIG_FUNCTION_PATTERN.matcher(script);
        
        if (!sigFunctionMatcher.find()) {
          // Try alternative patterns
          Pattern[] altSigPatterns = {
            // Pattern with split and join
            Pattern.compile("([a-zA-Z_$][a-zA-Z_$0-9]*)=function\\(([a-zA-Z])\\)\\{[^}]*\\2\\.split\\([^}]*\\2\\.join\\([^}]*\\}"),
            // Pattern with array manipulation  
            Pattern.compile("([a-zA-Z_$][a-zA-Z_$0-9]*)=function\\(([a-zA-Z])\\)\\{[^}]*\\2\\[[^]]+\\][^}]*return[^}]*\\2[^}]*\\}"),
            // Most flexible - any function that takes param and returns it
            Pattern.compile("([a-zA-Z_$][a-zA-Z_$0-9]{1,3})=function\\(([a-zA-Z])\\)\\{[^}]{20,}return[^}]*\\2[^}]*\\}")
          };
          
          for (Pattern pattern : altSigPatterns) {
            Matcher altMatcher = pattern.matcher(script);
            if (altMatcher.find()) {
              sigFunctionMatcher = altMatcher;
              sigFunction = altMatcher.group(0);
              break;
            }
          }
          
          if (sigFunction.isEmpty()) {
            scriptExtractionFailed(script, sourceUrl, ExtractionFailureType.DECIPHER_FUNCTION_NOT_FOUND);
          }
        } else {
          sigFunction = sigFunctionMatcher.group(0);
        }
      }
      
      // Only use regex fallback if JavaScript extractor didn't find n-function
      if (nFunction.isEmpty()) {
        // Try to find n-function with multiple patterns
        Matcher nFunctionMatcher = N_FUNCTION_PATTERN.matcher(script);
        
        if (!nFunctionMatcher.find()) {
          // Try alternative patterns - be more specific about what an n-function looks like
          Pattern[] altNPatterns = {
            // Pattern with enhanced_except explicitly  
            Pattern.compile("([a-zA-Z_$][a-zA-Z_$0-9]{0,3})=function\\(([a-zA-Z])\\)\\{[^}]*enhanced_except[^}]*\\+[^}]*\\2[^}]*\\}", Pattern.DOTALL),
            // Pattern that returns something with the parameter concatenated
            Pattern.compile("([a-zA-Z_$][a-zA-Z_$0-9]{0,3})=function\\(([a-zA-Z])\\)\\{[^}]*catch[^}]*return[^}]*\\+\\s*\\2[^}]*\\}", Pattern.DOTALL),
            // Pattern with variable assignment and array indexing (more typical n-function)
            Pattern.compile("([a-zA-Z_$][a-zA-Z_$0-9]{0,3})=function\\(([a-zA-Z])\\)\\{[^}]*var\\s+[a-zA-Z_$][^}]*\\2\\[[^]]+\\][^}]*catch[^}]*\\}", Pattern.DOTALL)
          };
          
          for (Pattern pattern : altNPatterns) {
            Matcher altMatcher = pattern.matcher(script);
            if (altMatcher.find()) {
              nFunctionMatcher = altMatcher;
              nFunction = altMatcher.group(0);
              break;
            }
          }
          
          if (nFunction.isEmpty()) {
            // N-function might not exist or be different in newer versions
            // Use a passthrough function that doesn't transform the parameter
            log.warn("Could not find n-function in script: {}, using passthrough", sourceUrl);
            nFunction = "function(a){return a}"; // Passthrough function
          }
        } else {
          nFunction = nFunctionMatcher.group(0);
        }
      }
    }

    String timestamp = scriptTimestamp.group(scriptTimestamp.groupCount());
    
    // Handle n-function parameter extraction more carefully
    String nfParameterName = "";
    Pattern paramPattern = Pattern.compile("function\\s*\\(([a-zA-Z])\\)");
    Matcher paramMatcher = paramPattern.matcher(nFunction);
    if (paramMatcher.find()) {
      nfParameterName = paramMatcher.group(1);
      // Remove short-circuit that prevents n challenge transformation
      nFunction = nFunction.replaceAll("if\\s*\\(typeof\\s*[^\\s()]+\\s*===?.*?\\)return " + Pattern.quote(nfParameterName) + "\\s*;?", "");
    }
    
    // Combine global variables with Y array if it exists
    String combinedGlobals = globalVars;
    if (!yArrayDef.isEmpty()) {
      combinedGlobals = yArrayDef + ";" + globalVars;
    }

    return new SignatureCipher(timestamp, combinedGlobals, sigActions, sigFunction, nFunction, script);
  }

  private void scriptExtractionFailed(String script, String sourceUrl, ExtractionFailureType failureType) {
    dumpProblematicScript(script, sourceUrl, "must find " + failureType.friendlyName);
    throw new ScriptExtractionException("Must find " + failureType.friendlyName + " from script: " + sourceUrl, failureType);
  }

  private static String extractDollarEscapedFirstGroup(@NotNull Pattern pattern, @NotNull String text) {
    Matcher matcher = pattern.matcher(text);
    return matcher.find() ? matcher.group(1).replace("$", "\\$") : null;
  }

  private static URI parseTokenScriptUrl(@NotNull String urlString) {
    try {
      if (urlString.startsWith("//")) {
        return new URI("https:" + urlString);
      } else if (urlString.startsWith("/")) {
        return new URI("https://www.youtube.com" + urlString);
      } else {
        return new URI(urlString);
      }
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  public static class CachedPlayerScript {
    public final String url;
    public final long expireTimestampMs;

    protected CachedPlayerScript(@NotNull String url) {
      this.url = url;
      this.expireTimestampMs = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1);
    }
  }
}
