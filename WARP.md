# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Project Overview

This is `youtube-source`, a rewritten YouTube source manager for Lavaplayer that provides robustness by leveraging multiple InnerTube clients. It serves as a replacement for Lavaplayer's deprecated built-in YouTube source.

The project consists of three main modules:
- **`common`**: Base source manager for Lavaplayer 1.x 
- **`v2`**: Extended support for Lavaplayer 2.x with additional features like thumbnails
- **`plugin`**: Lavalink plugin implementation

## Development Commands

### Building
```bash
# Build all modules
./gradlew build

# Build specific module
./gradlew :common:build
./gradlew :v2:build  
./gradlew :plugin:build

# Clean and rebuild
./gradlew clean build
```

### Testing
```bash
# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :common:test

# Run specific test class
./gradlew :common:test --tests "SignatureCipherManagerTest"

# Run tests with verbose output
./gradlew test --info
```

### Publishing
```bash
# Publish to local Maven repository
./gradlew publishToMavenLocal

# Publish to remote repository (requires credentials)
./gradlew publish
```

## Core Architecture

### Client System
The project uses a **multi-client architecture** where different YouTube InnerTube clients are tried in sequence until one succeeds:

- **`Client`** interface: Defines common operations (load video, search, playlists, etc.)
- **`NonMusicClient`**: Base class for youtube.com clients  
- **`MusicClient`**: Base class for music.youtube.com clients
- **`StreamingNonMusicClient`**: For clients that support streaming formats

**Key Clients:**
- `Web`: Primary web client with Opus support
- `WebEmbedded`: Embedded player client (age-restriction bypass)
- `Music`: YouTube Music search support
- `AndroidVr`: Alternative Android client  
- `MWeb`: Mobile web client

### Signature Cipher System
Critical for decrypting YouTube's obfuscated stream URLs:

- **`SignatureCipherManager`**: Manages cipher extraction and caching
- **`SignatureCipher`**: Represents a parsed cipher with JavaScript functions
- Uses **Rhino JavaScript engine** to execute YouTube's cipher algorithms
- Automatically extracts and caches cipher scripts from YouTube's player

**Key Components:**
- Signature transformation function (for `s` parameter)
- N-parameter transformation function (for `n` parameter)  
- Global variables extraction from player script
- Actions object containing transformation operations

### Track Loading Pipeline
1. **Router** determines request type (video, playlist, search)
2. **Client selection** based on capabilities and request type
3. **Format loading** retrieves available stream formats
4. **Cipher resolution** decrypts stream URLs if needed
5. **Stream playback** using resolved URLs

## Key Files and Directories

```
├── common/src/main/java/dev/lavalink/youtube/
│   ├── YoutubeAudioSourceManager.java      # Main source manager
│   ├── clients/                            # All client implementations
│   │   ├── skeleton/                       # Base classes
│   │   ├── Web.java, Music.java, etc.     # Concrete clients
│   ├── cipher/                             # Signature decryption
│   │   ├── SignatureCipherManager.java     # Cipher management
│   │   └── SignatureCipher.java            # Individual cipher
│   ├── track/                              # Track implementations
│   └── http/                               # OAuth and HTTP handling
├── v2/                                     # Lavaplayer 2.x extensions
├── plugin/                                 # Lavalink plugin
└── Python scripts/                         # Cipher analysis tools
```

## Development Guidelines

### Client Development
- Extend appropriate base class (`NonMusicClient`, `MusicClient`, etc.)
- Override required methods for your client's capabilities
- Implement proper JSON path extraction for your client's response format
- Add client identifier and configuration

### Testing Cipher Changes
The project includes Python analysis scripts for debugging YouTube's cipher:
```bash
python3 analyze_script.py      # General script analysis
python3 deep_analysis.py       # Deep cipher function analysis  
python3 analyze_functions.py   # Function-specific analysis
```

Use `SignatureCipherManagerTest` to validate cipher functionality:
```bash
./gradlew :common:test --tests "SignatureCipherManagerTest.testCurrentYoutubeScript"
```

### Adding New Clients
1. Create new client class extending appropriate base
2. Implement required interface methods
3. Add client to default client list in `YoutubeAudioSourceManager`
4. Update documentation with client capabilities
5. Add tests for client-specific functionality

### OAuth Integration
For clients supporting OAuth (TV, TVHTML5EMBEDDED):
- Use `YoutubeOauth2Handler` for token management
- Implement `supportsOAuth()` to return true
- Handle OAuth tokens in request contexts

### Format Selection
The system automatically selects the best available format:
- Prefers Opus formats when available
- Falls back to other audio codecs requiring transcoding
- Handles both static files and livestreams

## Common Issues

### Cipher Extraction Failures
If signature cipher extraction fails:
1. Check `SignatureCipherManager` regex patterns against current YouTube player
2. Use Python analysis scripts to identify new patterns
3. Update extraction patterns accordingly
4. Test with current YouTube script

### Client Failures
When a client stops working:
1. Check if YouTube changed the response JSON structure
2. Update JSON path extraction methods
3. Verify client configuration (version, headers)
4. Consider if client needs OAuth authentication

### Rate Limiting
If experiencing rate limits:
1. Implement IP rotation using `YoutubeIpRotatorSetup`
2. Use OAuth authentication for higher limits
3. Configure appropriate client options to disable unused features
4. Implement backoff strategies

## Debugging

### Enable Debug Logging
Add to application.properties or logback configuration:
```
logging.level.dev.lavalink.youtube=DEBUG
```

### Cipher Script Dumping
Failed cipher scripts are automatically dumped to temporary files when parsing fails. Check logs for file locations.

### Client Information
Exceptions include client information via `ClientInformation.create(client)` to identify which client caused issues.
