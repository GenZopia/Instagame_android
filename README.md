# 🎮 InstaGame - Social Gaming Video Platform

A modern Android social media app for gaming content creators, featuring Instagram-style reels, video uploads, user profiles, and game discovery.


📺 **Video Demo:** [Watch on YouTube](https://youtu.be/mD-h1P5sqF4?si=d89luoe9j3gTS7KY)

---

## ✨ Features

- **🎬 Reels/Shorts** - TikTok/Instagram-style vertical video feed with smooth scrolling and preloading
- **🏠 Home Feed** - Personalized feed showing videos from followed creators
- **👤 User Profiles** - View and edit profile with bio, website, followers/following stats
- **📤 Video Upload** - Record or select videos with background upload service
- **🔐 Authentication** - Email/password and Google Sign-In support
- **🎮 Game Discovery** - Browse and discover games linked to video content
- **💬 Comments** - Nested comment system with replies
- **❤️ Likes & Views** - Track engagement on videos
- **📱 Channel Pages** - View creator channels with their videos and games

---

## 🛠️ Tech Stack

| Category | Technology |
|----------|------------|
| Language | Kotlin & Java |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 36 |
| Backend | Firebase (Auth, Realtime Database, Firestore) |
| File Storage | Custom Cloudflare Workers (see Storage section below) |
| Video Player | ExoPlayer 2.19.1 |
| Image Loading | Glide 4.12.0 |
| Camera | CameraX 1.4.2 |
| UI Components | Material Design, Lottie Animations, Shimmer |
| Networking | OkHttp 4.12.0 |
| Background Tasks | WorkManager |

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
│   │   │   ├── User.java                  # User data model
│   │   │   ├── AvatarAdapter.kt           # Avatar selection grid
│   │   │   └── AvatarBottomSheetFragment.kt
│   │   │
│   │   ├── ui/                            # 📱 Main UI Screens
│   │   │   ├── home/
│   │   │   │   └── HomeFragment.java      # Following feed with videos
│   │   │   ├── dashboard/
│   │   │   │   └── DashboardFragment.java # Reels/Shorts view
│   │   │   ├── notifications/
│   │   │   │   └── NotificationsFragment.java # Opens post activity
│   │   │   ├── profile/
│   │   │   │   ├── ProfileFragment.java   # User profile page
│   │   │   │   ├── EditProfileActivity.java
│   │   │   │   └── FullScreenImageActivity.java
│   │   │   └── components/
│   │   │       └── VideoDetailsBottomSheet.java
│   │   │
│   │   ├── reelview/                      # 🎬 Reels System
│   │   │   ├── ReelAdapter.java           # Vertical video adapter
│   │   │   ├── ReelItem.java              # Reel data model
│   │   │   ├── ReelRepository.java        # Firebase data fetching
│   │   │   └── VideoPreloadManager.java   # Smart video preloading
│   │   │
│   │   ├── Post/                          # 📤 Video Upload
│   │   │   ├── Post_mainactivity.java     # Upload screen with tabs
│   │   │   ├── VideoPreviewActivity.java  # Preview before upload
│   │   │   ├── VideoUploadInfoActivity.java # Add title, description
│   │   │   ├── VideoUploadForegroundService.java # Background upload
│   │   │   ├── VideosFragment.java        # Gallery video picker
│   │   │   ├── ShortsFragment.java        # Camera recording
│   │   │   ├── FileUploader.java          # Upload handler
│   │   │   └── FileUtils.java             # File path utilities
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
│   │   │   ├── data/                      # Comment repository
│   │   │   ├── models/                    # Comment data models
│   │   │   └── ui/                        # Comment UI components
│   │   │
│   │   ├── vertical_recylerview_custom/   # 📜 Custom Video List
│   │   │   ├── HomeAdapter.java           # Home feed adapter
│   │   │   ├── VideoItem.java             # Video data model
│   │   │   ├── VideoViewHolder.java       # Video item view
│   │   │   ├── PlayerManager.java         # ExoPlayer management
│   │   │   ├── TempStorage.java           # Temporary data holder
│   │   │   └── profile_recyclerview/      # Profile video grid
│   │   │
│   │   ├── utils/                         # 🔧 Utilities
│   │   │   ├── VideoNavigationManager.java # Deep link handling
│   │   │   └── ViewCountManager.java      # View tracking
│   │   │
│   │   └── webgl_gameloading/             # 🎮 Game Integration
│   │       ├── Game_mode.java             # WebGL game loader
│   │       └── MyApplication.java         # App initialization
│   │
│   └── res/
│       ├── layout/                        # XML layouts
│       ├── navigation/                    # Navigation graphs
│       ├── drawable/                      # Icons & backgrounds
│       ├── anim/                          # Animations
│       ├── values/                        # Strings, colors, themes
│       └── raw/                           # Lottie animations
│
├── build.gradle.kts                       # App-level dependencies
└── proguard-rules.pro                     # ProGuard configuration

gradle/
└── libs.versions.toml                     # Version catalog
```

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 36
- Firebase project with:
  - Authentication (Email/Password + Google)
  - Realtime Database
  - Cloud Firestore
  - Cloud Storage

### Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd Genzcrop
   ```

2. **Firebase Configuration**
   - Create a Firebase project at [Firebase Console](https://console.firebase.google.com)
   - Download `google-services.json` and place it in `app/`
   - Enable Authentication methods (Email/Password, Google)
   - Set up Realtime Database and Storage rules

3. **Storage Configuration** (See [Storage Architecture](#-storage-architecture) section)
   
   This project uses custom Cloudflare Workers for file storage. You need to either:
   - Set up your own Cloudflare Workers + R2 storage, OR
   - Replace with Firebase Storage or another storage solution
   
   Add your API key to `gradle.properties`:
   ```properties
   file_upload_api_key=YOUR_API_KEY_HERE
   ```

4. **Build & Run**
   ```bash
   ./gradlew clean build
   ./gradlew installDebug
   ```

---

## 📱 App Navigation

```
┌─────────────────────────────────────────┐
│              Bottom Navigation          │
├──────────┬──────────┬─────────┬─────────┤
│   Home   │  Reels   │  Post   │ Profile │
│  (Feed)  │ (Shorts) │ (Upload)│  (You)  │
└──────────┴──────────┴─────────┴─────────┘
```

- **Home** - Videos from creators you follow
- **Reels** - Discover all videos in TikTok-style feed
- **Post** - Record or upload new videos
- **Profile** - Your profile, videos, games, and settings

---

## ☁️ Storage Architecture

This project uses **custom Cloudflare Workers** for file storage instead of Firebase Storage. You'll need to set up your own storage solution to run this project.

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

## 🎬 Video Preloading System

The app uses an optimized video preloading system for smooth, Instagram-like playback:

- Preloads ±5 videos around current position
- Zero black screen transitions
- Automatic memory cleanup
- Supports HLS (.m3u8) and MP4 formats

See [REELVIEW_OPTIMIZATION.md](REELVIEW_OPTIMIZATION.md) for technical details.

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
implementation("com.google.firebase:firebase-storage-ktx")

// Video
implementation("com.google.android.exoplayer:exoplayer:2.19.1")
implementation("androidx.camera:camera-core:1.4.2")

// UI
implementation("com.github.bumptech.glide:glide:4.12.0")
implementation("com.airbnb.android:lottie:6.4.0")
implementation("com.facebook.shimmer:shimmer:0.5.0")
implementation("de.hdodenhof:circleimageview:3.1.0")
```

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

This project is proprietary. All rights reserved.

---

## 📞 Contact

For questions or support, please open an issue or reach out through the project's communication channels.
