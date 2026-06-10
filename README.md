# 🎮 InstaGame - Social Gaming Video Platform

A modern Android social media app for gaming content creators, featuring Instagram-style reels, video uploads, user profiles, and game discovery.

📺 **Video Demo:** [Watch on YouTube](https://youtu.be/mD-h1P5sqF4?si=d89luoe9j3gTS7KY)

---

## 📋 Table of Contents

- [Features](#-features)
- [Tech Stack](#-tech-stack)
- [Architecture](#-architecture)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
- [Configuration](#-configuration)
- [App Navigation](#-app-navigation)
- [Storage Architecture](#-storage-architecture)
- [Video Processing Pipeline](#-video-processing-pipeline)
- [Video Preloading System](#-video-preloading-system)
- [Codec Error Handling](#-codec-error-handling)
- [Permissions](#-permissions)
- [Key Dependencies](#-key-dependencies)
- [Testing](#-testing)
- [Deployment](#-deployment)
- [Troubleshooting](#-troubleshooting)
- [Contributing](#-contributing)
- [License](#-license)
- [Contact](#-contact)

---

## ✨ Features

- **🎬 Reels/Shorts** — TikTok/Instagram-style vertical video feed with smooth scrolling and preloading
- **🏠 Home Feed** — Personalized feed showing videos from followed creators
- **👤 User Profiles** — View and edit profile with bio, website, followers/following stats
- **📤 Video Upload** — Record or select videos with background upload service
- **🔐 Authentication** — Email/password and Google Sign-In support
- **🎮 Game Discovery** — Browse and discover games linked to video content
- **💬 Comments** — Nested comment system with replies
- **❤️ Likes & Views** — Track engagement on videos
- **📱 Channel Pages** — View creator channels with their videos and games
- **🌐 WebGL Game Playing** — Play browser-based games directly in the app
- **📊 Amplitude Analytics** — User behavior tracking and analytics
- **🔍 Game Search Engine** — Search and discover games

---

## 🛠️ Tech Stack

| Category | Technology |
|----------|------------|
| **Language** | Kotlin & Java |
| **Min SDK** | 24 (Android 7.0) |
| **Target SDK** | 36 |
| **UI Framework** | Jetpack Compose + Material 3 |
| **Backend** | Firebase (Auth, Realtime Database, Firestore) |
| **File Storage** | Custom Cloudflare Workers + R2 |
| **Video Player** | Media3 ExoPlayer |
| **Image Loading** | Glide 4.12.0 + Coil (Compose) |
| **Camera** | CameraX |
| **UI Components** | Material Design, Lottie Animations, Shimmer |
| **Networking** | OkHttp 5.x, Retrofit 3.x |
| **Background Tasks** | WorkManager |
| **Analytics** | Amplitude |
| **State Management** | ViewModel + LiveData |
| **Build System** | Gradle with Version Catalog (libs.versions.toml) |

---

## 🏗️ Architecture

The app follows a **hybrid architecture** combining traditional Activity/Fragment patterns with Jetpack Compose for newer screens.

### Architecture Layers

```
┌─────────────────────────────────────────────────────────┐
│                     UI Layer                              │
│  Activities / Fragments / Compose Screens / ViewModels   │
├─────────────────────────────────────────────────────────┤
│                   Domain Layer                            │
│  Repository Interfaces / Data Models / Use Cases         │
├─────────────────────────────────────────────────────────┤
│                     Data Layer                            │
│  Firebase Auth / RTDB / Firestore / Cloudflare Workers   │
└─────────────────────────────────────────────────────────┘
```

### Key Architecture Principles

- **Repository Pattern**: Data access centralized in repositories (e.g., `FollowingRepository`, `CommentsRepository`, `ReelRepository`)
- **ViewModel + StateFlow**: Modern screens use ViewModel with Compose state management
- **Paging 3**: Infinite scrolling for home feed and reels
- **Service Layer**: Background operations via Foreground Service and WorkManager

### Navigation

The app uses a **bottom navigation** pattern with 4 main tabs, implemented via `NavigationComponent`:

| Tab | Screen | Type |
|-----|--------|------|
| 🏠 Home | Feed from followed creators | Fragment (XML) + Compose |
| 🎬 Reels | TikTok-style video feed | Compose |
| ➕ Post | Upload video / Record | Activity |
| 👤 Profile | User profile & settings | Fragment (XML) |

---

## 📁 Project Structure

```
app/
├── src/main/
│   ├── java/com/genzopia/Instagame/
│   │   ├── MainActivity.java              # Main entry with bottom navigation
│   │   ├── SplashActivity.java            # App splash screen
│   │   │
│   │   ├── LoginActivities/               # 🔐 Authentication
│   │   │   ├── LoginActivity.java         # Email & Google sign-in
│   │   │   ├── RegisterActivity.kt        # New user registration
│   │   │   ├── ForgotPassword.kt          # Password reset
│   │   │   ├── ProfileCompletionActivity.kt # Post-registration setup
│   │   │   ├── User.java                  # User data model
│   │   │   ├── AvatarAdapter.kt           # Avatar selection grid
│   │   │   ├── AvatarBottomSheetFragment.kt
│   │   │   ├── PrivacyPolicyActivity.kt
│   │   │   └── Firebase_login_realtimeDatabase.java
│   │   │
│   │   ├── features/                      # ⚡ Feature Modules (Compose)
│   │   │   ├── auth/                      # Auth feature
│   │   │   │   ├── ui/                    # Login/Register screens
│   │   │   │   ├── domain/                # User domain models
│   │   │   │   └── data/                  # Auth data sources
│   │   │   └── home/                      # Home feature
│   │   │       ├── ui/                    # Home composables
│   │   │       ├── domain/                # Home domain models
│   │   │       └── data/                  # Home data sources
│   │   │
│   │   ├── ui/                            # 📱 Main UI Screens
│   │   │   ├── home/                      # HomeFragment (Java)
│   │   │   ├── dashboard/                 # DashboardFragment (Reels)
│   │   │   ├── notifications/             # NotificationsFragment
│   │   │   ├── profile/                   # ProfileFragment, EditProfile
│   │   │   └── components/                # Shared UI components
│   │   │
│   │   ├── reelview/compose/              # 🎬 Reels System (Compose)
│   │   │   ├── ReelScreen.kt              # Main reel composable
│   │   │   ├── ReelViewModel.kt           # Reel state management
│   │   │   ├── ReelData.kt                # Reel data models
│   │   │   ├── ReelComposeFragment.kt     # Fragment wrapper
│   │   │   └── ReelPagingSource.kt        # Paging data source
│   │   │
│   │   ├── vertical_recylerview_custom/   # 📜 Custom Video List
│   │   │   ├── HomeAdapter.java           # Home feed adapter
│   │   │   ├── VideoItem.java             # Video data model
│   │   │   ├── VideoViewHolder.java       # Video item view
│   │   │   ├── PlayerManager.java         # ExoPlayer management
│   │   │   └── profile_recyclerview/      # Profile video grid
│   │   │
│   │   ├── Post/                          # 📤 Video Upload
│   │   │   ├── Post_mainactivity.java     # Upload screen with tabs
│   │   │   ├── VideoPreviewActivity.java  # Preview before upload
│   │   │   ├── VideoUploadInfoActivity.java # Add title, description
│   │   │   ├── VideoUploadForegroundService.java # Background upload
│   │   │   ├── VideosFragment.java        # Gallery video picker
│   │   │   ├── ShortsFragment.java        # Camera recording
│   │   │   ├── FileUploader.java          # Upload handler
│   │   │   ├── FileUtils.java             # File path utilities
│   │   │   └── VideosAdapter_gallery.java
│   │   │
│   │   ├── channel_view/                  # 👤 Creator Channels
│   │   │   ├── ChannelActivity.java       # Channel page
│   │   │   ├── VideoDetailActivity.java   # Single video view
│   │   │   └── Fragment/                  # Channel tabs
│   │   │       ├── GamesFragment/         # Creator's games
│   │   │       ├── VideosFragment/        # Creator's videos
│   │   │       └── DetailFragment/        # Creator info
│   │   │
│   │   ├── comments/                      # 💬 Comments System
│   │   │   ├── ui/                        # Comment UI components
│   │   │   ├── data/                      # Comment repository
│   │   │   └── models/                    # Comment data models
│   │   │
│   │   ├── onboarding/                    # 🎯 Onboarding Tutorial
│   │   │   ├── OnboardingTutorialHost.kt  # Tutorial host
│   │   │   ├── OnboardingOverlay.kt       # Tutorial overlay UI
│   │   │   ├── TutorialController.kt      # Tutorial state manager
│   │   │   └── ScrollHintArrow.kt         # Visual hints
│   │   │
│   │   ├── webgl_gameloading/             # 🎮 Game Integration
│   │   │   ├── Game_mode.java             # WebGL game loader
│   │   │   └── MyApplication.java         # App initialization
│   │   │
│   │   ├── analytics/                     # 📊 Analytics
│   │   │   ├── InstagameAnalytics.kt      # Analytics wrapper
│   │   │   └── SessionTracker.kt          # Session tracking
│   │   │
│   │   ├── common/                        # 🔧 Shared Components
│   │   │   ├── BaseActivity.java          # Base activity class
│   │   │   ├── navigation/                # Navigation routes
│   │   │   ├── ui/                        # Shared UI components
│   │   │   ├── utils/                     # Repository provider
│   │   │   └── models/                    # Shared models (UiState)
│   │   │
│   │   ├── utils/                         # 🔧 Utilities
│   │   │   ├── VideoNavigationManager.java # Deep link handling
│   │   │   ├── ViewCountManager.java      # View tracking
│   │   │   ├── GameSearchEngine.kt        # Game search
│   │   │   ├── DataPrefetchService.kt     # Data prefetching
│   │   │   ├── ProfilePhotoUtils.kt       # Photo utilities
│   │   │   └── AppReadyManager.kt         # App readiness
│   │   │
│   │   ├── glide/                         # 🖼️ Glide Module
│   │   │   └── InstagameGlideModule.java
│   │   │
│   │   ├── VideoPlayer.kt                 # Video player helper
│   │   └── VideoHlsConverter.kt           # HLS conversion
│   │
│   └── res/
│       ├── layout/                        # XML layouts
│       ├── navigation/                    # Navigation graphs
│       ├── drawable/                      # Icons & backgrounds
│       ├── anim/                          # Animations
│       ├── values/                        # Strings, colors, themes
│       ├── font/                          # Custom fonts
│       └── xml/                           # Backup rules, file paths
│
├── build.gradle.kts                       # App-level dependencies
├── proguard-rules.pro                     # ProGuard configuration
└── google-services.json                   # Firebase configuration

gradle/
└── libs.versions.toml                     # Version catalog

# Documentation
├── README.md                              # This file
├── REELVIEW_OPTIMIZATION.md               # Video preloading system
├── videoprocessor.md                      # Video processing service
├── enhanced_codec_error_handling_summary.md
├── final_optimization_summary.md
└── .kiro/specs/                           # Architecture specs
```

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or newer
- **JDK** 17+
- **Android SDK** 36
- **Firebase project** with:
  - Authentication (Email/Password + Google)
  - Realtime Database
  - Cloud Firestore
  - Cloud Storage

### Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd Instagame_android
   ```

2. **Firebase Configuration**
   - Create a Firebase project at [Firebase Console](https://console.firebase.google.com)
   - Download `google-services.json` and place it in `app/`
   - Enable Authentication methods (Email/Password, Google)
   - Set up Realtime Database and Storage rules
   - The existing `google-services.json` is pre-configured for the `instagame-452906` project

3. **Configure API Keys in `gradle.properties`**
   ```properties
   file_upload_api_key=YOUR_API_KEY_HERE
   video_processor_api_key=YOUR_VIDEO_PROCESSOR_API_KEY
   amplitude_api_key=YOUR_AMPLITUDE_API_KEY
   ```

4. **Storage Configuration**
   
   This project uses **custom Cloudflare Workers** for file storage. You need to either:
   - Set up your own Cloudflare Workers + R2 storage, OR
   - Replace with Firebase Storage or another storage solution
   
   See the [Storage Architecture](#-storage-architecture) section for details.

5. **Build & Run**
   ```bash
   # Clean build
   ./gradlew clean assembleDebug
   
   # Install on connected device/emulator
   ./gradlew installDebug
   ```

---

## ⚙️ Configuration

### Environment Variables / Properties

All configuration is managed through `gradle.properties`:

| Property | Required | Description |
|----------|----------|-------------|
| `file_upload_api_key` | Yes | API key for Cloudflare file upload worker |
| `video_processor_api_key` | Yes | API key for video processing service |
| `amplitude_api_key` | Yes | Amplitude analytics API key |

### Build Configuration

Key settings in `app/build.gradle.kts`:

```kotlin
android {
    compileSdk = 36
    defaultConfig {
        applicationId = "com.genzopia.Instagame"
        minSdk = 24
        targetSdk = 36
        versionCode = 5
        versionName = "5.0"
    }
}
```

### Firebase Security Rules

**Realtime Database:**
```json
{
  "rules": {
    ".read": "auth != null",
    ".write": "auth != null",
    "videos": {
      ".indexOn": ["timestamp", "userId", "category"]
    }
  }
}
```

---

## 📱 App Navigation

```
┌─────────────────────────────────────────────────┐
│              Bottom Navigation                    │
├──────────┬──────────┬──────────┬─────────────────┤
│   Home   │  Reels   │  Upload  │    Profile      │
│  (Feed)  │ (Shorts) │ (Record) │    (You)        │
└──────────┴──────────┴──────────┴─────────────────┘
```

- **Home** — Videos from creators you follow with game discovery
- **Reels** — Discover all videos in TikTok-style vertical feed
- **Upload** — Record new videos or select from gallery
- **Profile** — Your profile, videos, games, and settings

### Additional Screens

| Screen | Access | Description |
|--------|--------|-------------|
| Login | Splash → Unauthenticated | Email/password + Google sign-in |
| Register | Login → Register | New user sign-up with avatar selection |
| Forgot Password | Login → Forgot | Password reset flow |
| Channel | Tap creator name | Creator's videos, games, info |
| Video Detail | Tap video | Full video with details |
| Game Play | Tap game | WebGL game in WebView |
| Comments | Tap comment icon | Nested comment thread |
| Edit Profile | Profile → Edit | Update bio, photo, website |

---

## ☁️ Storage Architecture

This project uses **custom Cloudflare Workers** for file storage instead of Firebase Storage.

### Current Cloudflare Workers

| Worker | URL | Auth | Purpose | Used In |
|--------|-----|------|---------|---------|
| **file-upload-worker** | `file-upload-worker.genzopia.workers.dev` | 🔐 API Key | Profile photo upload/delete | `RegisterActivity.kt` |
| **file-uploader** | `file-uploader.genzopia.workers.dev` | ❌ Open | Video file uploads | `FileUploader.java` |
| **video-signer** | `video-signer.genzopia.workers.dev` | ❌ Open | Generate signed video URLs | `ReelRepository.java`, `HomeFragment.java`, `DashboardFragment.java`, `VideoDetailActivity.java`, `VideoAdapter.java` |
| **link-signer** | `link-signer.genzopia.workers.dev` | ❌ Open | Generate signed game URLs | `Game_mode.java` |

### Files That Use Storage

| File | Storage Usage |
|------|---------------|
| `LoginActivities/RegisterActivity.kt` | Profile photo upload (requires API key) |
| `Post/FileUploader.java` | Video uploads to cloud storage |
| `reelview/ReelRepository.java` | Fetches signed video URLs for playback |
| `ui/home/HomeFragment.java` | Fetches signed video URLs for home feed |
| `ui/dashboard/DashboardFragment.java` | Fetches signed video URLs for reels |
| `channel_view/VideoDetailActivity.java` | Fetches signed video URL for detail view |
| `channel_view/Fragment/VideosFragment/VideoAdapter.java` | Fetches signed video URLs in channel |
| `webgl_gameloading/Game_mode.java` | Fetches signed game URLs |

### To Configure Your Own Storage

**Option 1: Use Firebase Storage**
- Replace Cloudflare Worker URLs with Firebase Storage upload/download logic
- Update `RegisterActivity.kt` to use `FirebaseStorage.getInstance()`
- Update `FileUploader.java` to upload to Firebase Storage
- Generate download URLs using Firebase's `getDownloadUrl()`

**Option 2: Set Up Your Own Cloudflare Workers**
- Create Cloudflare Workers with R2 storage bucket
- Deploy workers for file upload and URL signing
- Update the worker URLs in the code
- Set your API key in `gradle.properties`

---

## 🎬 Video Processing Pipeline

Videos go through a multi-stage processing pipeline after upload:

```
Upload → Cloudflare R2 → Video Processor → HLS Conversion → Firebase Update
```

1. **Upload**: User records/selects video → uploaded to Cloudflare R2 via `FileUploader.java`
2. **Processing**: Video sent to the [Video Processing Service](videoprocessor.md) — a Spring Boot application
3. **HLS Conversion**: FFmpeg converts video to adaptive bitrate HLS (multiple quality levels)
4. **Firebase Update**: On completion, Firebase RTDB entry is patched with `isHLSConverted: true`

### Adaptive Streaming Quality Profiles

| Profile | Resolution | Video Bitrate |
|---------|------------|---------------|
| 1440p | 2560×1440 | 14,000 Kbps |
| 1080p | 1920×1080 | 8,000 Kbps |
| 720p | 1280×720 | 5,000 Kbps |
| 480p | 854×480 | 2,500 Kbps |
| 360p | 640×360 | 1,200 Kbps |
| 240p | 426×240 | 700 Kbps |

See [videoprocessor.md](videoprocessor.md) for complete API documentation and deployment instructions.

---

## 🎬 Video Preloading System

The app uses an **optimized video preloading system** for smooth, Instagram-like playback.

### Key Features

- Preloads ±5 videos around current position
- Zero black screen transitions
- Automatic memory cleanup
- Supports HLS (.m3u8) and MP4 formats
- Adaptive preload range based on device capabilities

### Performance Metrics

| Metric | Before | After |
|--------|--------|-------|
| Transition Time | 200-500ms | < 50ms |
| Black Screen | Yes | Eliminated |
| Memory Usage | ~192MB (OOM risk) | ~60-80MB |

### Emulator Optimization

The system automatically detects emulators and applies optimized settings:
- Preload range: 0 (disabled)
- Player cache: 1 instance (89% reduction)
- Buffer limits: 5s duration, 1MB size (90% reduction)
- Software codec fallback for emulator codec issues

See [REELVIEW_OPTIMIZATION.md](REELVIEW_OPTIMIZATION.md) and [final_optimization_summary.md](final_optimization_summary.md) for complete technical details.

---

## 🛡️ Codec Error Handling

The app includes a **comprehensive codec error handling system** that ensures smooth video playback across all devices.

### Multi-Layer Recovery

1. **Detection**: Multi-pattern codec error detection (Decoder init failed, MediaCodec, goldfish, etc.)
2. **Software Fallback**: `SoftwareCodecSelector` prioritizes software codecs over hardware
3. **Progressive Fallback**: Uses `ProgressiveMediaSource` for maximum compatibility
4. **Graceful Degradation**: Shows thumbnail with error state if all else fails
5. **Resource Management**: Tracks problematic videos to avoid repeated failures

See [enhanced_codec_error_handling_summary.md](enhanced_codec_error_handling_summary.md) for complete details.

---

## 🔒 Permissions

| Permission | Purpose |
|------------|---------|
| `INTERNET` | Network access |
| `CAMERA` | Video recording |
| `RECORD_AUDIO` | Audio capture |
| `READ_MEDIA_VIDEO` | Gallery access (Android 13+) |
| `READ_MEDIA_IMAGES` | Image access (Android 13+) |
| `FOREGROUND_SERVICE` | Background uploads |
| `POST_NOTIFICATIONS` | Upload progress notifications |

---

## 📦 Key Dependencies

```kotlin
// Firebase
implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
implementation("com.google.firebase:firebase-auth-ktx")
implementation("com.google.firebase:firebase-database")
implementation("com.google.firebase:firebase-firestore")

// Media3 ExoPlayer
implementation("androidx.media3:media3-exoplayer:1.10.1")
implementation("androidx.media3:media3-ui:1.10.1")
implementation("androidx.media3:media3-exoplayer-hls:1.10.1")

// Jetpack Compose
implementation(platform("androidx.compose:compose-bom:2024.09.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.activity:activity-compose")

// Image Loading
implementation("com.github.bumptech.glide:glide:4.12.0")
implementation("io.coil-kt:coil-compose:2.7.0")

// UI Components
implementation("com.airbnb.android:lottie:6.7.1")
implementation("com.facebook.shimmer:shimmer:0.5.0")
implementation("de.hdodenhof:circleimageview:3.1.0")

// Networking
implementation("com.squareup.okhttp3:okhttp:5.3.2")
implementation("com.squareup.retrofit2:retrofit:3.0.0")

// Analytics
implementation("com.amplitude:analytics-android:1.21.1")
```

For the complete list, see [gradle/libs.versions.toml](gradle/libs.versions.toml).

---

## 🧪 Testing

Run unit tests:
```bash
./gradlew test
```

Run instrumented tests:
```bash
./gradlew connectedAndroidTest
```

Run lint checks:
```bash
./gradlew lint
```

The project uses:
- **JUnit 4** — Unit testing
- **Robolectric** — Android unit testing
- **Espresso** — UI testing
- **Mockito** — Mocking

---

## 🚢 Deployment

### Building a Release APK

```bash
# Build release APK
./gradlew assembleRelease

# Build app bundle for Play Store
./gradlew bundleRelease
```

The APK can be found at `app/build/outputs/apk/release/`.

### CI/CD (GitHub Actions)

The project is configured for GitHub Actions. The workflow:
1. Checks out code
2. Sets up JDK 17
3. Runs lint checks
4. Builds debug APK

Create `.github/workflows/android.yml`:
```yaml
name: Android CI
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - run: ./gradlew assembleDebug
```

### Play Store Deployment

1. Generate a signed release APK/Bundle
2. Upload to Google Play Console
3. Configure app signing
4. Submit for review

---

## 🔍 Troubleshooting

### Build Issues

| Issue | Solution |
|-------|----------|
| `Failed to find Build Tools` | Install Android SDK Build-Tools 36 via SDK Manager |
| `Kotlin version mismatch` | Run `./gradlew --refresh-dependencies` |
| `google-services.json not found` | Download from Firebase Console and place in `app/` |
| `AGP version incompatible` | Update Android Studio to latest stable version |

### Runtime Issues

| Issue | Solution |
|-------|----------|
| Black screen in reels | Enable software codec fallback in `VideoPreloadManager` |
| Video not playing | Check Cloudflare Worker URLs and API keys |
| Login not working | Verify Firebase Authentication is enabled (Email + Google) |
| Game not loading | Check `link-signer` Worker URL |
| Analytics not tracking | Verify `amplitude_api_key` in `gradle.properties` |

### Emulator Issues

| Issue | Solution |
|-------|----------|
| Codec errors (`c2.goldfish`) | Emulator has limited codec support — software fallback is automatic |
| OOM crashes | Emulator memory constraints handled by automatic detection |
| Slow playback | Reduce preload range or check network speed |

---

## 🤝 Contributing

We welcome contributions! Here's how you can help:

### Development Process

1. **Fork the repository**
2. **Create a feature branch**
   ```bash
   git checkout -b feature/amazing-feature
   ```
3. **Commit your changes**
   ```bash
   git commit -m 'Add amazing feature'
   ```
4. **Push to your branch**
   ```bash
   git push origin feature/amazing-feature
   ```
5. **Open a Pull Request**

### Coding Standards

- **Kotlin**: Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- **Java**: Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- **Naming**: Use `camelCase` for methods/variables, `PascalCase` for classes
- **XML**: Use `snake_case` for resource IDs
- **Compose**: Use `@Composable` annotations with meaningful names

### Pull Request Guidelines

- Keep PRs focused on a single feature/fix
- Include a clear description of changes
- Update documentation when adding features
- Ensure all tests pass
- Follow existing code style

### Reporting Issues

- Use the GitHub Issues tracker
- Include steps to reproduce
- Mention device/OS version
- Attach logs if possible

---

## 📄 License

This project is proprietary. All rights reserved.

---

## 📞 Contact

For questions, support, or contributions:
- **Open an Issue** on [GitHub](https://github.com/GenZopia/Instagame_android/issues)
- **Watch Demo** on [YouTube](https://youtu.be/mD-h1P5sqF4?si=d89luoe9j3gTS7KY)

---

## 🙏 Acknowledgments

- **Icons**: Material Design Icons
- **Animations**: Lottie by Airbnb
- **Video Player**: ExoPlayer by Google
- **Images**: Glide by Google
- **Backend**: Firebase by Google
- **Storage**: Cloudflare Workers + R2
