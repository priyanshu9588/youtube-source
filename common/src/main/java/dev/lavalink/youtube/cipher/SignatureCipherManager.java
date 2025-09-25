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

  private static final Pattern SIG_FUNCTION_PATTERN = Pattern.compile(
      "function(?:\\s+" + VARIABLE_PART + ")?\\((" + VARIABLE_PART + ")\\)\\{" +
          VARIABLE_PART + "=" + VARIABLE_PART + ".*?\\(\\1,\\d+\\);return\\s*\\1.*};"
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
        dumpProblematicScript(cipherCache.get(playerScript).rawScript, playerScript, "Can't transform s parameter " + signature);
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
        }

        uri.setParameter("n", transformed);
      } catch (ScriptException | NoSuchMethodException e) {
        // URLs can still be played without a resolved n parameter. It just means they're
        // throttled. But we shouldn't throw an exception anyway as it's not really fatal.
        dumpProblematicScript(cipherCache.get(playerScript).rawScript, playerScript, "Can't transform n parameter " + nParameter + " with " + cipher.nFunction + " n function");
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
    Matcher scriptTimestamp = TIMESTAMP_PATTERN.matcher(script);

    if (!scriptTimestamp.find()) {
      scriptExtractionFailed(script, sourceUrl, ExtractionFailureType.TIMESTAMP_NOT_FOUND);
    }

    String timestamp = scriptTimestamp.group(2);
    
    // Try to extract function names for Node.js execution
    String sigFunctionName = extractSignatureFunctionName(script);
    String nFunctionName = extractNFunctionName(script);
    
    // Extract parameter values if needed
    String sigParamValue = extractSigParamValue(script, sigFunctionName);
    String nParamValues = extractNParamValues(script, nFunctionName);
    
    // Check if we should use Node.js based on successful function name extraction
    boolean useNodeJs = sigFunctionName != null && nFunctionName != null;
    
    if (useNodeJs) {
      log.debug("Using Node.js runner with sig function: {} and n function: {}", sigFunctionName, nFunctionName);
      // For Node.js, we don't need to extract the individual parts, just the function names
      return new SignatureCipher(timestamp, "", "", "", sigFunctionName, sigParamValue,
                                "", nFunctionName, nParamValues, script, true);
    } else {
      log.debug("Falling back to Rhino extraction");
      // Fall back to traditional extraction for Rhino
      Matcher globalVarsMatcher = GLOBAL_VARS_PATTERN.matcher(script);

      if (!globalVarsMatcher.find()) {
        scriptExtractionFailed(script, sourceUrl, ExtractionFailureType.VARIABLES_NOT_FOUND);
      }

      Matcher sigActionsMatcher = ACTIONS_PATTERN.matcher(script);

      if (!sigActionsMatcher.find()) {
        scriptExtractionFailed(script, sourceUrl, ExtractionFailureType.SIG_ACTIONS_NOT_FOUND);
      }

      Matcher sigFunctionMatcher = SIG_FUNCTION_PATTERN.matcher(script);

      if (!sigFunctionMatcher.find()) {
        scriptExtractionFailed(script, sourceUrl, ExtractionFailureType.DECIPHER_FUNCTION_NOT_FOUND);
      }

      Matcher nFunctionMatcher = N_FUNCTION_PATTERN.matcher(script);

      if (!nFunctionMatcher.find()) {
        scriptExtractionFailed(script, sourceUrl, ExtractionFailureType.N_FUNCTION_NOT_FOUND);
      }

      String globalVars = globalVarsMatcher.group("code");
      String sigActions = sigActionsMatcher.group(0);
      String sigFunction = sigFunctionMatcher.group(0);
      String nFunction = nFunctionMatcher.group(0);

      String nfParameterName = DataFormatTools.extractBetween(nFunction, "(", ")");
      // Remove short-circuit that prevents n challenge transformation
      nFunction = nFunction.replaceAll("if\\s*\\(typeof\\s*[^\\s()]+\\s*===?.*?\\)return " + nfParameterName + "\\s*;?", "");

      return new SignatureCipher(timestamp, globalVars, sigActions, sigFunction, nFunction, script);
    }
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
  
  /**
   * Extract the signature function name from the script
   */
  private String extractSignatureFunctionName(@NotNull String script) {
    // Patterns similar to pytubefix's get_sig_function_name
    String[] patterns = {
        // Pattern 1: Look for function with split operation
        "(?P<sig>[a-zA-Z0-9_$]+)\\s*=\\s*function\\(\\s*(?P<arg>[a-zA-Z0-9_$]+)\\s*\\)\\s*\\{\\s*(?P=arg)\\s*=\\s*(?P=arg)\\.split\\(\\s*[a-zA-Z0-9_\\$\\\"\\[\\]]+\\s*\\)\\s*;\\s*[^}]+;\\s*return\\s+(?P=arg)\\.join\\(\\s*[a-zA-Z0-9_\\$\\\"\\[\\]]+\\s*\\)",
        "(?:\\b|[^a-zA-Z0-9_$])([a-zA-Z0-9_$]{2,})\\s*=\\s*function\\(\\s*a\\s*\\)\\s*\\{\\s*a\\s*=\\s*a\\.split\\(\\s*\\\"\\\"\\s*\\)",
        "\\b([a-zA-Z0-9_$]+)&&\\(\\1=([a-zA-Z0-9_$]{2,})\\(decodeURIComponent\\(\\1\\)\\)",
        "\\b[cs]\\s*&&\\s*[adf]\\.set\\([^,]+\\s*,\\s*encodeURIComponent\\s*\\(\\s*([a-zA-Z0-9$]+)\\(",
        "\\b[a-zA-Z0-9]+\\s*&&\\s*[a-zA-Z0-9]+\\.set\\([^,]+\\s*,\\s*encodeURIComponent\\s*\\(\\s*([a-zA-Z0-9$]+)\\(",
        "\\bm=([a-zA-Z0-9$]{2,})\\(decodeURIComponent\\(h\\.s\\)\\)"
    };
    
    for (String pattern : patterns) {
      Pattern p = Pattern.compile(pattern);
      Matcher m = p.matcher(script);
      if (m.find()) {
        String funcName = m.groupCount() >= 2 ? m.group(2) : m.group(1);
        log.debug("Found signature function name: {}", funcName);
        return funcName;
      }
    }
    
    log.debug("Could not find signature function name");
    return null;
  }
  
  /**
   * Extract the n-transform function name from the script
   */
  private String extractNFunctionName(@NotNull String script) {
    // Try to find the function based on the global array (similar to pytubefix)
    Matcher globalObjMatcher = GLOBAL_VARS_PATTERN.matcher(script);
    
    if (globalObjMatcher.find()) {
      String varname = globalObjMatcher.group("varname");
      log.debug("Found global variable: {}", varname);
      
      // Look for functions that use this variable and match the n-transform pattern
      String pattern = String.format(
          "(?xs)\n" +
          "[;\\n](?:\n" +
          "  (?:function\\s+)|\n" +
          "  (?:var\\s+)?\n" +
          ")([a-zA-Z0-9_$]+)\\s*(?:|=\\s*function\\s*)\n" +
          "\\(([a-zA-Z0-9_$]+)\\)\\s*\\{\n" +
          "(?:(?!\\};(?![\\]\\)])).)+ \n" +
          "\\}\\s*catch\\(\\s*[a-zA-Z0-9_$]+\\s*\\)\\s*\n" +
          "\\{\\s*return\\s+%s\\[\\d+\\]\\s*\\+\\s*\\2\\s*\\}\\s*return\\s+[^}]+\\}[;\\n]",
          Pattern.quote(varname)
      );
      
      Pattern p = Pattern.compile(pattern);
      Matcher m = p.matcher(script);
      if (m.find()) {
        String funcName = m.group(1);
        log.debug("Found n-transform function name: {}", funcName);
        return funcName;
      }
    }
    
    // Fallback: try simpler patterns
    String[] patterns = {
        "([a-zA-Z0-9_$]+)\\s*=\\s*function\\([^)]+\\)\\s*\\{[^}]*enhanced_except_[^}]*\\}",
        "function\\s+([a-zA-Z0-9_$]+)\\([^)]+\\)\\s*\\{[^}]*_w8_[^}]*\\}"
    };
    
    for (String pattern : patterns) {
      Pattern p = Pattern.compile(pattern, Pattern.DOTALL);
      Matcher m = p.matcher(script);
      if (m.find()) {
        String funcName = m.group(1);
        log.debug("Found n-transform function name (fallback): {}", funcName);
        return funcName;
      }
    }
    
    log.debug("Could not find n-transform function name");
    return null;
  }
  
  /**
   * Extract parameter value for signature function if needed
   */
  private String extractSigParamValue(@NotNull String script, String functionName) {
    if (functionName == null) return null;
    
    // Check if the function takes two parameters
    String pattern = functionName + "\\s*=\\s*function\\s*\\(\\s*([a-zA-Z0-9_$]+)\\s*,\\s*([a-zA-Z0-9_$]+)\\s*\\)";
    Pattern p = Pattern.compile(pattern);
    Matcher m = p.matcher(script);
    
    if (m.find()) {
      // Function takes two parameters, need to find what value to pass as first parameter
      // This would require more complex analysis of the call sites
      log.debug("Signature function takes two parameters, parameter extraction needed");
      // For now, return null and let it try without parameter
      return null;
    }
    
    return null;
  }
  
  /**
   * Extract parameter values for n-transform function if needed
   */
  private String extractNParamValues(@NotNull String script, String functionName) {
    if (functionName == null) return null;
    
    // Look for array indices that might be used as parameters
    // This is a simplified version - in practice, you might need more sophisticated extraction
    String pattern = functionName + "\\s*\\([^,]+,\\s*([^)]+)\\)";
    Pattern p = Pattern.compile(pattern);
    Matcher m = p.matcher(script);
    
    if (m.find()) {
      log.debug("N-transform function might need parameter values");
      // Return a comma-separated list of potential parameter values to try
      // These are common values seen in YouTube's implementation
      return "0,1,2,3,4,5,6,7,8,9";
    }
    
    return null;
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
