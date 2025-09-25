package dev.lavalink.youtube.cipher;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.IOException;

/**
 * Describes one signature cipher
 */
public class SignatureCipher {
  private static final Logger log = LoggerFactory.getLogger(SignatureCipher.class);

  public final String timestamp;
  public final String globalVars;
  public final String sigActions;
  public final String sigFunction;
  public final String sigFunctionName;
  public final String sigParamValue;
  public final String nFunction;
  public final String nFunctionName;
  public final String nParamValues;
  public final String rawScript;
  
  private final boolean useNodeJs;

  // Legacy constructor for compatibility
  public SignatureCipher(@NotNull String timestamp,
                         @NotNull String globalVars,
                         @NotNull String sigActions,
                         @NotNull String sigFunction,
                         @NotNull String nFunction,
                         @NotNull String rawScript) {
    this(timestamp, globalVars, sigActions, sigFunction, null, null,
         nFunction, null, null, rawScript, false);
  }
  
  // New constructor with Node.js support
  public SignatureCipher(@NotNull String timestamp,
                         @NotNull String globalVars,
                         @NotNull String sigActions,
                         @NotNull String sigFunction,
                         @Nullable String sigFunctionName,
                         @Nullable String sigParamValue,
                         @NotNull String nFunction,
                         @Nullable String nFunctionName,
                         @Nullable String nParamValues,
                         @NotNull String rawScript,
                         boolean useNodeJs) {
    this.timestamp = timestamp;
    this.globalVars = globalVars;
    this.sigActions = sigActions;
    this.sigFunction = sigFunction;
    this.sigFunctionName = sigFunctionName;
    this.sigParamValue = sigParamValue;
    this.nFunction = nFunction;
    this.nFunctionName = nFunctionName;
    this.nParamValues = nParamValues;
    this.rawScript = rawScript;
    this.useNodeJs = useNodeJs;
  }

  /**
   * @param text Text to apply the cipher on
   * @return The result of the cipher on the input text
   */
  public String apply(@NotNull String text,
                      @NotNull ScriptEngine scriptEngine) throws ScriptException, NoSuchMethodException {
    if (useNodeJs && sigFunctionName != null) {
      try {
        // Use Node.js runner for better compatibility
        if (sigParamValue != null) {
          return NodeJsRunner.execute(rawScript, sigFunctionName, sigParamValue, text);
        } else {
          return NodeJsRunner.execute(rawScript, sigFunctionName, text);
        }
      } catch (IOException e) {
        log.warn("Failed to execute signature function with Node.js, falling back to Rhino", e);
      }
    }
    
    // Fallback to Rhino engine
    String transformed;
    scriptEngine.eval(globalVars + ";" + sigActions + ";decrypt_sig=" + sigFunction);
    transformed = (String) ((Invocable) scriptEngine).invokeFunction("decrypt_sig", text);
    return transformed;
  }

//  /**
//   * @param text Text to apply the cipher on
//   * @return The result of the cipher on the input text
//   */
//  public String apply(@NotNull String text) {
//    StringBuilder builder = new StringBuilder(text);
//
//    for (CipherOperation operation : operations) {
//      switch (operation.type) {
//        case SWAP:
//          int position = operation.parameter % text.length();
//          char temp = builder.charAt(0);
//          builder.setCharAt(0, builder.charAt(position));
//          builder.setCharAt(position, temp);
//          break;
//        case REVERSE:
//          builder.reverse();
//          break;
//        case SLICE:
//        case SPLICE:
//          builder.delete(0, operation.parameter);
//          break;
//        default:
//          throw new IllegalStateException("All branches should be covered");
//      }
//    }
//
//    return builder.toString();
//  }

  /**
   * @param text         Text to transform
   * @param scriptEngine JavaScript engine to execute function
   * @return The result of the n parameter transformation
   */
  public String transform(@NotNull String text, @NotNull ScriptEngine scriptEngine)
      throws ScriptException, NoSuchMethodException {
    if (useNodeJs && nFunctionName != null) {
      try {
        // Use Node.js runner for better compatibility
        if (nParamValues != null) {
          // Try each parameter value until one works
          String[] params = nParamValues.split(",");
          for (String param : params) {
            try {
              String result = NodeJsRunner.execute(rawScript, nFunctionName, param.trim(), text);
              if (result != null && !result.equals(text)) {
                return result;
              }
            } catch (IOException ignored) {
              // Try next parameter
            }
          }
          // If all parameters failed, try without parameter
          return NodeJsRunner.execute(rawScript, nFunctionName, text);
        } else {
          return NodeJsRunner.execute(rawScript, nFunctionName, text);
        }
      } catch (IOException e) {
        log.warn("Failed to execute nsig function with Node.js, falling back to Rhino", e);
      }
    }
    
    // Fallback to Rhino engine
    String transformed;
    scriptEngine.eval(globalVars + ";decrypt_nsig=" + nFunction);
    transformed = (String) ((Invocable) scriptEngine).invokeFunction("decrypt_nsig", text);

    return transformed;
  }

//  /**
//   * @param operation The operation to add to this cipher
//   */
//  public void addOperation(@NotNull CipherOperation operation) {
//    operations.add(operation);
//  }
//
//  /**
//   * @return True if the cipher contains no operations.
//   */
//  public boolean isEmpty() {
//    return operations.isEmpty();
//  }
}
