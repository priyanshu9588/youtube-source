import dev.lavalink.youtube.cipher.JavaScriptExtractor;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TestExtractor {
    public static void main(String[] args) throws Exception {
        String scriptPath = "/var/folders/5z/1cdjzwb9515f_ft40n2thyq40000gn/T/lavaplayer-yt-player-script11404048700466387682.js";
        String script = new String(Files.readAllBytes(Paths.get(scriptPath)));
        
        JavaScriptExtractor extractor = new JavaScriptExtractor(script);
        
        System.out.println("Looking for signature function...");
        JavaScriptExtractor.SignatureFunctionInfo sigInfo = extractor.findSignatureFunction();
        if (sigInfo != null) {
            System.out.println("Found signature function: " + sigInfo.functionName);
            System.out.println("Function body length: " + sigInfo.functionBody.length());
            System.out.println("First 200 chars: " + sigInfo.functionBody.substring(0, Math.min(200, sigInfo.functionBody.length())));
            if (sigInfo.helperObjectBody != null) {
                System.out.println("Helper object body length: " + sigInfo.helperObjectBody.length());
            }
        } else {
            System.out.println("No signature function found!");
        }
        
        System.out.println("\nLooking for n-function...");
        JavaScriptExtractor.NFunctionInfo nInfo = extractor.findNFunction();
        if (nInfo != null) {
            System.out.println("Found n-function: " + nInfo.functionName);
            System.out.println("Function body length: " + nInfo.functionBody.length());
            System.out.println("First 200 chars: " + nInfo.functionBody.substring(0, Math.min(200, nInfo.functionBody.length())));
        } else {
            System.out.println("No n-function found!");
        }
    }
}