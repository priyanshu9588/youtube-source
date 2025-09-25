package dev.lavalink.youtube.cipher;

import org.jetbrains.annotations.NotNull;
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
  
  // Singleton Node.js executor instance
  private static NodeJsCipherExecutor nodeExecutor;
  private static boolean useNodeJs = true;
  
  static {
    try {
      nodeExecutor = new NodeJsCipherExecutor();
      log.info("Node.js cipher executor initialized - using Node.js for YouTube decryption");
    } catch (IOException e) {
      log.warn("Failed to initialize Node.js executor, falling back to Rhino: {}", e.getMessage());
      useNodeJs = false;
    }
  }

  public final String timestamp;
  public final String globalVars;
  public final String sigActions;
  public final String sigFunction;
  public final String nFunction;
  public final String rawScript;

  public SignatureCipher(@NotNull String timestamp,
                         @NotNull String globalVars,
                         @NotNull String sigActions,
                         @NotNull String sigFunction,
                         @NotNull String nFunction,
                         @NotNull String rawScript) {
    this.timestamp = timestamp;
    this.globalVars = globalVars;
    this.sigActions = sigActions;
    this.sigFunction = sigFunction;
    this.nFunction = nFunction;
    this.rawScript = rawScript;
  }

  /**
   * @param text Text to apply the cipher on
   * @return The result of the cipher on the input text
   */
  public String apply(@NotNull String text,
                      @NotNull ScriptEngine scriptEngine) throws ScriptException, NoSuchMethodException {
    String transformed;
    
    // Try Node.js executor first if available
    if (useNodeJs && nodeExecutor != null) {
      try {
        transformed = nodeExecutor.decryptSignature(text, globalVars, sigActions, sigFunction);
        if (transformed != null && !transformed.isEmpty()) {
          log.debug("Successfully decrypted signature using Node.js");
          return transformed;
        }
      } catch (IOException e) {
        log.warn("Node.js decryption failed, falling back to Rhino: {}", e.getMessage());
      }
    }
    
    // Fallback to Rhino
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
    String transformed;
    
    // Try Node.js executor first if available
    if (useNodeJs && nodeExecutor != null) {
      try {
        transformed = nodeExecutor.transformNParameter(text, globalVars, nFunction);
        if (transformed != null && !transformed.isEmpty()) {
          log.debug("Successfully transformed n-parameter using Node.js");
          return transformed;
        }
      } catch (IOException e) {
        log.warn("Node.js n-parameter transformation failed, falling back to Rhino: {}", e.getMessage());
      }
    }
    
    // Fallback to Rhino
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
