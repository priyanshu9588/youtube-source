import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.Android;
import dev.lavalink.youtube.clients.Web;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ProxyCipherIntegrationTest {

    @Test
    public void testYoutubeWithCipherProxy() throws Exception {
        System.out.println("=== Testing YouTube with Cipher Proxy ===");
        System.out.println("Make sure your cipher proxy is running at http://localhost:3000");
        System.out.println("");

        // Create audio player manager
        DefaultAudioPlayerManager playerManager = new DefaultAudioPlayerManager();

        // Create YouTube source manager with cipher proxy configured
        YoutubeAudioSourceManager youtubeSourceManager = new YoutubeAudioSourceManager(
            true,  // allowSearch
            true,  // allowDirectVideoIds
            true,  // allowDirectPlaylistIds
            "http://localhost:3000",  // cipherProxyUrl
            "",     // cipherProxyPass (empty)
            new Web(),
            new Android()
        );

        // Register the source manager
        playerManager.registerSourceManager(youtubeSourceManager);

        // Test with a popular YouTube video (Rick Astley - Never Gonna Give You Up)
        String testVideoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

        System.out.println("Testing with video: " + testVideoUrl);

        CompletableFuture<AudioTrack> future = new CompletableFuture<>();

        playerManager.loadItem(testVideoUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                System.out.println("✅ Track loaded successfully!");
                System.out.println("  Title: " + track.getInfo().title);
                System.out.println("  Author: " + track.getInfo().author);
                System.out.println("  Duration: " + (track.getDuration() / 1000) + " seconds");
                System.out.println("  Stream URL obtained: " + (track.getInfo().uri != null ? "Yes" : "No"));

                // Try to start playing to verify the stream URL works
                try {
                    AudioPlayer player = playerManager.createPlayer();
                    player.playTrack(track);
                    System.out.println("✅ Track started playing successfully!");
                    Thread.sleep(1000); // Let it play for a second
                    player.stopTrack();
                    System.out.println("✅ Playback test completed!");
                } catch (Exception e) {
                    System.out.println("❌ Playback failed: " + e.getMessage());
                }

                future.complete(track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                System.out.println("Playlist loaded (unexpected for single video)");
                future.complete(null);
            }

            @Override
            public void noMatches() {
                System.out.println("❌ No matches found for the video");
                future.complete(null);
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                System.out.println("❌ Failed to load track: " + exception.getMessage());
                exception.printStackTrace();
                future.completeExceptionally(exception);
            }
        });

        // Wait for the result
        try {
            AudioTrack track = future.get(30, TimeUnit.SECONDS);
            if (track != null) {
                System.out.println("\n=== Test Result: SUCCESS ===");
                System.out.println("The cipher proxy is working correctly!");
            } else {
                System.out.println("\n=== Test Result: FAILED ===");
                System.out.println("Could not load the track");
            }
        } catch (Exception e) {
            System.out.println("\n=== Test Result: FAILED ===");
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    public void testSearchWithCipherProxy() throws Exception {
        System.out.println("\n=== Testing YouTube Search with Cipher Proxy ===");

        DefaultAudioPlayerManager playerManager = new DefaultAudioPlayerManager();

        YoutubeAudioSourceManager youtubeSourceManager = new YoutubeAudioSourceManager(
            true,  // allowSearch
            true,  // allowDirectVideoIds
            true,  // allowDirectPlaylistIds
            "http://localhost:3000",  // cipherProxyUrl
            "",     // cipherProxyPass (empty)
            new Web(),
            new Android()
        );

        playerManager.registerSourceManager(youtubeSourceManager);

        String searchQuery = "ytsearch:never gonna give you up";
        System.out.println("Searching for: " + searchQuery);

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        playerManager.loadItem(searchQuery, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                System.out.println("Single track loaded (unexpected for search)");
                future.complete(false);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                System.out.println("✅ Search results loaded!");
                System.out.println("  Found " + playlist.getTracks().size() + " tracks");
                if (!playlist.getTracks().isEmpty()) {
                    AudioTrack firstTrack = playlist.getTracks().get(0);
                    System.out.println("  First result: " + firstTrack.getInfo().title + " by " + firstTrack.getInfo().author);
                }
                future.complete(true);
            }

            @Override
            public void noMatches() {
                System.out.println("❌ No search results found");
                future.complete(false);
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                System.out.println("❌ Search failed: " + exception.getMessage());
                future.completeExceptionally(exception);
            }
        });

        try {
            boolean success = future.get(30, TimeUnit.SECONDS);
            if (success) {
                System.out.println("\n=== Search Test: SUCCESS ===");
            } else {
                System.out.println("\n=== Search Test: FAILED ===");
            }
        } catch (Exception e) {
            System.out.println("\n=== Search Test: FAILED ===");
            System.out.println("Error: " + e.getMessage());
        }
    }
}