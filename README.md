# Tunaro

## Overview

Tunaro is a music tracking and note-taking app that integrates with Spotify. It allows users to access their Spotify playlists and enhance their music listening experience by adding personal notes, creating custom song snippets, and organizing their listening history. The app leverages the Spotify API to retrieve user playlists and track information, but extends the functionality with personalized annotation features that aren't available in the standard Spotify app.

### Current Features

- **Spotify Integration**: Connects to user's Spotify account to access playlists and control playback
- **Playlist Management**: Browse and sort playlists, with ability to archive playlists
- **Song Playback**: Play songs directly within the app using Spotify's playback SDK
- **Notes System**: Add custom notes to songs (e.g., "First Listened", "Favourite Part", "Rating")
- **Song Snippets**: Create and save specific sections of songs with custom start/end times
- **Library View**: Access all songs that have notes in a searchable library
- **Persistent Storage**: Local database storage for notes, snippets, and archived playlists
- **Search Functionality**: Search within playlists or library for specific songs
- **Sort Options**: Sort songs by various criteria (Date Added, Title, Length, Artist)
- **Playback Controls**: Global playback bar that persists across app navigation
- **Listening History Tracking**: Automatic recording of when songs are played with background sync from Spotify
- **Favorite Playlists**: Mark playlists as favorites for quick access
- **Data Export/Import**: Backup and restore your notes and snippets using JSON export functionality

## Technical Details

### Tech Stack

**Language**: Java (~7,040 lines across 35 files)

**Android Framework**:
- AndroidX with Material Design Components
- Navigation Framework
- WorkManager for background tasks
- ViewBinding for type-safe view references

**Spotify Integration**:
- Spotify Web API (via `spotify-web-api-java` v8.4.1)
- Spotify App Remote SDK (v0.8.0) for playback control
- Spotify Auth SDK (v1.2.5) for authentication

**Key Libraries**:
- **Database**: SQLite with custom DatabaseHelper
- **HTTP Client**: OkHttp3
- **JSON**: Gson for serialization
- **Image Loading**: Glide
- **Async/Concurrency**: CompletableFuture, ExecutorService
- **UI Animations**: Shimmer (Facebook)

**Build System**: Gradle with Kotlin DSL

### Project Structure

```
app/src/main/java/com/ca/tunaro/
├── activities/      # 7 main screens (Home, Playlists, Library, SongView, etc.)
├── adapters/        # RecyclerView adapters for displaying lists
├── callbacks/       # Swipe gesture callbacks for item interactions
├── database/        # SQLite data access layer (DatabaseHelper)
├── fragments/       # UI fragments for Notes and Snippets
├── interfaces/      # RecyclerView item click listeners
├── managers/        # PlaybackManager singleton for playback state
├── models/          # Data models (Song, Playlist, Note, Snippet)
├── workers/         # Background sync worker for listening history
└── utils/           # Helper utilities
```

### Architecture

The app follows **MVC (Model-View-Controller)** architecture with several design patterns:

- **Singleton Pattern**: PlaybackManager and MainActivity ensure centralized state management for playback and Spotify API access
- **Observer Pattern**: PlaybackListener interface allows multiple components to subscribe to playback state changes
- **Repository Pattern**: DatabaseHelper provides a centralized data access layer with CRUD operations
- **Async Operations**: CompletableFuture pattern for non-blocking Spotify API calls and database operations

**Key Components**:
- `MainActivity.java`: Entry point with Spotify OAuth authentication
- `PlaybackManager.java`: Manages playback state, listen tracking, and snippet playback
- `DatabaseHelper.java`: Central data access for all local storage operations
- `HomeActivity.java`: Main navigation hub
- `SongView.java`: Detailed song view with notes and snippets management

### Database Schema

The app uses **SQLite** (TunaroDB v7) with five tables:

1. **song_notes**: User annotations with UUID, song ID, note type, content, and timestamps
2. **song_snippets**: Time-based song snippets with start/end times and ranking inclusion flags
3. **favourite_playlists**: IDs of playlists marked as favorites
4. **archived_playlists**: IDs of archived playlists
5. **listen_history**: UTC timestamped listening events with duplicate prevention

Additional storage via SharedPreferences for Spotify tokens and sync cursor tracking.

### Planned/Potential Future Features

- **Song Recommendations**: Option to play recommended songs after a specified song or continue with playlist
- **Play from Oldest Listened**: Option to play songs from a playlist in reverse chronological order based on when you last listened to them, letting you refresh your memory on songs you haven't heard in a while
- **Improved Ratings System**: More comprehensive rating system for songs and snippets
- **Rankings View**: Currently a placeholder tab that will likely show ranked songs/snippets
- **Statistics and Analysis**: Track listening habits and provide insights
- **Social Sharing**: Share playlists, notes and snippets with friends
- **Snippet Collections**: Group snippets across different songs into collections
- **Song Tags**: Group songs using tags
- **Custom Playback Queues**: Create custom queues based on notes and snippets
- **Improved UI/UX**: Enhanced visual design and user experience


## Setup

To setup on your own machine:

1. Clone the [repository](https://github.com/Chris-1010/Tunaro.git)
2. Have at least gradle v8.5 installed and configure the project structure to use your installed version
3. Run the `install_dependencies.sh` script found at the root of the project to install required Spotify API dependencies.
4. There's a template xml file for secrets in `/app/src/main/res/values`. Replace the template values with yours (if you don't have any, they can be obtained from [Spotify's Developer Dashboard](https://developer.spotify.com/dashboard))
5. Launch Android Virtual Device (AVD) or connect physical device and run app
6. Authenticate with Spotify when the app opens

