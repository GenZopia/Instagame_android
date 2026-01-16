# Design Document: MVVM Architecture Refactor

## Overview

This design outlines the refactoring of the InstaGame Android application from its current mixed architecture (Java/Kotlin, XML/Compose, scattered business logic) to a clean, optimized MVVM architecture using Jetpack Compose. The refactor will eliminate code duplication, remove unused code, consolidate event handling, optimize data loading through caching, and establish a clear, maintainable structure while preserving all existing functionality and UI design.

The refactor follows modern Android development best practices including:
- **Single source of truth** for UI state in ViewModels
- **Unidirectional data flow** from Repository → ViewModel → View
- **Reactive programming** using Kotlin Flows and StateFlow
- **Separation of concerns** with clear layer boundaries
- **Dependency injection** for loose coupling and testability

## Architecture

### Layer Overview

```
┌─────────────────────────────────────────────────────────┐
│                    UI Layer (Compose)                    │
│  - Composable functions                                  │
│  - Fragment containers                                   │
│  - UI state observation                                  │
│  - User input handling                                   │
└────────────────────┬────────────────────────────────────┘
                     │ observes state
                     │ sends events
┌────────────────────▼────────────────────────────────────┐
│                  ViewModel Layer                         │
│  - UI state management (StateFlow)                       │
│  - Business logic                                        │
│  - Event handling                                        │
│  - Repository coordination                               │
└────────────────────┬────────────────────────────────────┘
                     │ requests data
                     │ receives data
┌────────────────────▼────────────────────────────────────┐
│                  Repository Layer                        │
│  - Data source coordination                              │
│  - Caching strategy                                      │
│  - Data transformation                                   │
│  - Error handling                                        │
└────────────────────┬────────────────────────────────────┘
                     │ fetches from
┌────────────────────▼────────────────────────────────────┐
│                  Data Sources                            │
│  - Firebase Realtime Database                            │
│  - Firebase Firestore                                    │
│  - Firebase Auth                                         │
│  - Remote API (Cloudflare Workers)                       │
└─────────────────────────────────────────────────────────┘
```

### Package Structure

The refactored codebase will follow a feature-based package structure:

```
com.genzopia.Instagame/
├── common/                          # Shared utilities and components
│   ├── ui/                          # Reusable UI components
│   ├── navigation/                  # Navigation logic
│   ├── utils/                       # Utility functions
│   └── models/                      # Shared data models
│
├── features/
│   ├── auth/                        # Authentication feature
│   │   ├── data/                    # Auth data layer
│   │   │   ├── repository/          # AuthRepository
│   │   │   └── source/              # Firebase Auth source
│   │   ├── domain/                  # Auth business logic
│   │   │   └── models/              # User model
│   │   └── ui/                      # Auth UI layer
│   │       ├── login/               # Login screen
│   │       ├── register/            # Register screen
│   │       └── forgot/              # Forgot password screen
│   │
│   ├── home/                        # Home feed feature
│   │   ├── data/                    # Home data layer
│   │   │   ├── repository/          # HomeRepository
│   │   │   └── source/              # Firebase data source
│   │   ├── domain/                  # Home business logic
│   │   │   └── models/              # HomeVideoData model
│   │   └── ui/                      # Home UI layer
│   │       ├── HomeScreen.kt        # Compose UI
│   │       ├── HomeViewModel.kt     # ViewModel
│   │       └── HomeFragment.kt      # Fragment container
│   │
│   ├── reels/                       # Reels/Dashboard feature
│   │   ├── data/                    # Reels data layer
│   │   │   ├── repository/          # ReelRepository
│   │   │   ├── source/              # Firebase data source
│   │   │   └── paging/              # ReelPagingSource
│   │   ├── domain/                  # Reels business logic
│   │   │   └── models/              # ReelData model
│   │   └── ui/                      # Reels UI layer
│   │       ├── ReelScreen.kt        # Compose UI
│   │       ├── ReelViewModel.kt     # ViewModel
│   │       ├── VideoPlayer.kt       # Video player component
│   │       └── ReelFragment.kt      # Fragment container
│   │
│   ├── profile/                     # Profile feature
│   │   ├── data/                    # Profile data layer
│   │   │   ├── repository/          # ProfileRepository
│   │   │   └── source/              # Firebase data source
│   │   ├── domain/                  # Profile business logic
│   │   │   └── models/              # Profile models
│   │   └── ui/                      # Profile UI layer
│   │       ├── ProfileScreen.kt     # Compose UI
│   │       ├── ProfileViewModel.kt  # ViewModel
│   │       ├── EditProfileScreen.kt # Edit profile UI
│   │       └── ProfileFragment.kt   # Fragment container
│   │
│   ├── post/                        # Video upload feature
│   │   ├── data/                    # Post data layer
│   │   │   ├── repository/          # PostRepository
│   │   │   └── source/              # Upload service
│   │   ├── domain/                  # Post business logic
│   │   │   └── models/              # Upload models
│   │   └── ui/                      # Post UI layer
│   │       ├── PostScreen.kt        # Compose UI
│   │       ├── PostViewModel.kt     # ViewModel
│   │       ├── PreviewScreen.kt     # Preview UI
│   │       └── PostActivity.kt      # Activity container
│   │
│   ├── channel/                     # Channel/Creator feature
│   │   ├── data/                    # Channel data layer
│   │   │   ├── repository/          # ChannelRepository
│   │   │   └── source/              # Firebase data source
│   │   ├── domain/                  # Channel business logic
│   │   │   └── models/              # Channel models
│   │   └── ui/                      # Channel UI layer
│   │       ├── ChannelScreen.kt     # Compose UI
│   │       ├── ChannelViewModel.kt  # ViewModel
│   │       └── ChannelActivity.kt   # Activity container
│   │
│   └── comments/                    # Comments feature
│       ├── data/                    # Comments data layer
│       │   ├── repository/          # CommentsRepository
│       │   └── source/              # Firebase data source
│       ├── domain/                  # Comments business logic
│       │   └── models/              # Comment, Reply models
│       └── ui/                      # Comments UI layer
│           ├── CommentsSheet.kt     # Compose bottom sheet
│           ├── CommentsViewModel.kt # ViewModel
│           └── CommentsAdapter.kt   # RecyclerView adapter (if needed)
│
└── MainActivity.kt                  # Main activity with navigation
```

## Components and Interfaces

### 1. Repository Pattern

All data access will be centralized in Repository classes following this interface pattern:

```kotlin
interface Repository<T> {
    suspend fun fetch(id: String): Result<T>
    fun observe(id: String): Flow<T>
    suspend fun refresh()
    fun clearCache()
}
```

**Key Repositories:**

#### VideoRepository
```kotlin
class VideoRepository(
    private val firebaseSource: FirebaseVideoSource,
    private val cache: VideoCache
) {
    private val cachedVideos = MutableStateFlow<Map<String, VideoData>>(emptyMap())
    
    suspend fun getVideo(videoId: String): Result<VideoData> {
        // Check cache first
        cachedVideos.value[videoId]?.let { return Result.success(it) }
        
        // Fetch from Firebase
        return firebaseSource.fetchVideo(videoId).also { result ->
            result.onSuccess { video ->
                cachedVideos.update { it + (videoId to video) }
            }
        }
    }
    
    fun observeVideos(): Flow<List<VideoData>> {
        return cachedVideos.map { it.values.toList() }
    }
    
    suspend fun refreshVideos() {
        cachedVideos.value = emptyMap()
        // Trigger re-fetch
    }
}
```

#### UserRepository
```kotlin
class UserRepository(
    private val firebaseAuth: FirebaseAuth,
    private val firebaseSource: FirebaseUserSource,
    private val cache: UserCache
) {
    private val currentUser = MutableStateFlow<User?>(null)
    
    fun observeCurrentUser(): StateFlow<User?> = currentUser.asStateFlow()
    
    suspend fun login(email: String, password: String): Result<User>
    suspend fun register(email: String, password: String, username: String): Result<User>
    suspend fun updateProfile(updates: ProfileUpdates): Result<User>
    suspend fun logout()
}
```

#### ReelRepository
```kotlin
class ReelRepository(
    private val firebaseSource: FirebaseReelSource,
    private val cache: ReelCache
) {
    fun getReelsPagingSource(): ReelPagingSource {
        return ReelPagingSource(firebaseSource, cache)
    }
    
    suspend fun preloadReels(startIndex: Int, count: Int)
}
```

### 2. ViewModel Pattern

ViewModels will manage UI state and handle user events:

```kotlin
abstract class BaseViewModel<State, Event> : ViewModel() {
    protected abstract val _uiState: MutableStateFlow<State>
    val uiState: StateFlow<State> get() = _uiState.asStateFlow()
    
    abstract fun onEvent(event: Event)
}
```

**Example: HomeViewModel**

```kotlin
data class HomeUiState(
    val videos: List<HomeVideoData> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPlayingIndex: Int = -1
)

sealed class HomeEvent {
    data class VideoScrolled(val index: Int) : HomeEvent()
    data class VideoLiked(val videoId: String) : HomeEvent()
    data class VideoShared(val videoId: String) : HomeEvent()
    data class CommentClicked(val videoId: String) : HomeEvent()
    object Refresh : HomeEvent()
}

class HomeViewModel(
    private val videoRepository: VideoRepository,
    private val userRepository: UserRepository
) : BaseViewModel<HomeUiState, HomeEvent>() {
    
    override val _uiState = MutableStateFlow(HomeUiState())
    
    init {
        loadVideos()
    }
    
    override fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.VideoScrolled -> handleVideoScrolled(event.index)
            is HomeEvent.VideoLiked -> handleVideoLiked(event.videoId)
            is HomeEvent.VideoShared -> handleVideoShared(event.videoId)
            is HomeEvent.CommentClicked -> handleCommentClicked(event.videoId)
            HomeEvent.Refresh -> loadVideos()
        }
    }
    
    private fun loadVideos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            videoRepository.getHomeVideos()
                .onSuccess { videos ->
                    _uiState.update { it.copy(videos = videos, isLoading = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message, isLoading = false) }
                }
        }
    }
    
    private fun handleVideoScrolled(index: Int) {
        _uiState.update { it.copy(currentPlayingIndex = index) }
        // Trigger preloading
        viewModelScope.launch {
            videoRepository.preloadVideos(index - 2, index + 2)
        }
    }
}
```

### 3. Compose UI Pattern

All UI will be built with Jetpack Compose following this pattern:

```kotlin
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToProfile: (String) -> Unit,
    onNavigateToComments: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    HomeContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateToProfile = onNavigateToProfile,
        onNavigateToComments = onNavigateToComments
    )
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToComments: (String) -> Unit
) {
    when {
        uiState.isLoading -> LoadingIndicator()
        uiState.error != null -> ErrorMessage(uiState.error)
        else -> VideoList(
            videos = uiState.videos,
            currentPlayingIndex = uiState.currentPlayingIndex,
            onVideoScrolled = { index -> onEvent(HomeEvent.VideoScrolled(index)) },
            onVideoLiked = { videoId -> onEvent(HomeEvent.VideoLiked(videoId)) },
            onCommentClicked = { videoId -> onNavigateToComments(videoId) }
        )
    }
}
```

### 4. Navigation Pattern

Navigation will be centralized using sealed classes:

```kotlin
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Reels : Screen("reels")
    object Post : Screen("post")
    object Profile : Screen("profile")
    data class Channel(val userId: String) : Screen("channel/{userId}")
    data class Comments(val videoId: String) : Screen("comments/{videoId}")
}

sealed class NavigationEvent {
    data class NavigateToChannel(val userId: String) : NavigationEvent()
    data class NavigateToComments(val videoId: String) : NavigationEvent()
    object NavigateBack : NavigationEvent()
}
```

## Data Models

### Core Data Models

```kotlin
// User domain model
data class User(
    val id: String,
    val username: String,
    val email: String,
    val profileImageUrl: String?,
    val bio: String?,
    val website: String?,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val isVerified: Boolean = false
)

// Video domain model
data class VideoData(
    val id: String,
    val title: String,
    val description: String,
    val videoUrl: String,
    val thumbnailUrl: String,
    val uploaderId: String,
    val uploaderName: String,
    val uploaderProfileImage: String?,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val viewsCount: Int = 0,
    val uploadTimestamp: Long,
    val isLiked: Boolean = false
)

// Comment domain model
data class Comment(
    val id: String,
    val videoId: String,
    val userId: String,
    val username: String,
    val userProfileImage: String?,
    val text: String,
    val timestamp: Long,
    val likesCount: Int = 0,
    val repliesCount: Int = 0,
    val isLiked: Boolean = false
)

// Reply domain model
data class Reply(
    val id: String,
    val commentId: String,
    val userId: String,
    val username: String,
    val userProfileImage: String?,
    val text: String,
    val timestamp: Long,
    val likesCount: Int = 0,
    val isLiked: Boolean = false
)
```

### UI State Models

```kotlin
// Generic UI state wrapper
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

// Specific feature states
data class ProfileUiState(
    val user: User? = null,
    val videos: List<VideoData> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isFollowing: Boolean = false,
    val isOwnProfile: Boolean = false
)

data class ReelUiState(
    val currentIndex: Int = 0,
    val isPlaying: Boolean = true,
    val isMuted: Boolean = false,
    val showControls: Boolean = false
)
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

Based on the prework analysis, most acceptance criteria are about structural organization and architectural compliance rather than behavioral properties that can be tested with property-based testing. The testable criteria are primarily examples that verify specific structural outcomes of the refactor.

### Structural Verification Examples

The following are specific structural checks that verify the refactor was completed correctly:

**Example 1: Test directories removed**
Verify that `app/src/test` and `app/src/androidTest` directories do not exist after refactor.
**Validates: Requirements 1.2, 11.1, 11.2**

**Example 2: Compose implementations retained**
Verify that Compose-based fragments exist (HomeFragmentCompose, ReelComposeFragment) and XML-based equivalents are removed.
**Validates: Requirements 2.1, 2.3**

**Example 3: MVVM package structure**
Verify that feature packages contain `data/`, `ui/`, and `domain/` subpackages.
**Validates: Requirements 3.1, 7.1, 7.2**

**Example 4: Repository classes exist**
Verify that Repository classes exist for each domain: VideoRepository, UserRepository, CommentsRepository, GameRepository.
**Validates: Requirements 4.1**

**Example 5: Repository API design**
Verify that Repository classes expose methods returning `Flow<T>` or `LiveData<T>` types.
**Validates: Requirements 4.4**

**Example 6: ViewModel dependencies**
Verify that ViewModel classes have Repository constructor parameters and do not import Firebase classes directly.
**Validates: Requirements 4.5, 9.1**

**Example 7: Paging 3 usage**
Verify that PagingSource classes exist for paginated features (ReelPagingSource, HomePagingSource).
**Validates: Requirements 5.3**

**Example 8: Navigation sealed classes**
Verify that sealed classes or enums exist for navigation events.
**Validates: Requirements 6.4**

**Example 9: Dependency injection**
Verify that ViewModels use constructor injection and ViewModelProvider.Factory classes exist.
**Validates: Requirements 9.2, 9.3, 9.4**

**Example 10: StateFlow usage**
Verify that ViewModels expose `StateFlow<UiState>` properties for UI state.
**Validates: Requirements 10.1**

**Example 11: UI state data classes**
Verify that UI state classes are defined as Kotlin data classes.
**Validates: Requirements 10.3**

**Example 12: State modeling**
Verify that UI state classes include loading, success, and error states.
**Validates: Requirements 10.4**

**Example 13: Test dependencies removed**
Verify that `build.gradle.kts` does not contain test-related dependencies (JUnit, Espresso, etc.).
**Validates: Requirements 11.3**

**Example 14: Architecture documentation**
Verify that README.md contains an "Architecture" section describing MVVM structure.
**Validates: Requirements 12.1, 12.5**

## Error Handling

### Repository Error Handling

Repositories will use Kotlin's `Result` type for error handling:

```kotlin
sealed class DataError {
    data class Network(val message: String) : DataError()
    data class Firebase(val code: String, val message: String) : DataError()
    data class Cache(val message: String) : DataError()
    object NotFound : DataError()
    object Unauthorized : DataError()
}

suspend fun <T> safeFirebaseCall(
    call: suspend () -> T
): Result<T> {
    return try {
        Result.success(call())
    } catch (e: FirebaseException) {
        Result.failure(DataError.Firebase(e.code, e.message ?: "Unknown error"))
    } catch (e: IOException) {
        Result.failure(DataError.Network(e.message ?: "Network error"))
    } catch (e: Exception) {
        Result.failure(DataError.Cache(e.message ?: "Unknown error"))
    }
}
```

### ViewModel Error Handling

ViewModels will handle errors and update UI state:

```kotlin
private fun handleError(error: Throwable) {
    val errorMessage = when (error) {
        is DataError.Network -> "Network error. Please check your connection."
        is DataError.Firebase -> "Server error: ${error.message}"
        is DataError.NotFound -> "Content not found."
        is DataError.Unauthorized -> "Please log in to continue."
        else -> "An unexpected error occurred."
    }
    _uiState.update { it.copy(error = errorMessage, isLoading = false) }
}
```

### UI Error Handling

Compose UI will display errors consistently:

```kotlin
@Composable
fun ErrorMessage(
    message: String,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}
```

## Testing Strategy

### Unit Testing Approach

While the requirements specify removing test infrastructure, this section documents the testing approach that would be used if testing were included:

**Unit Tests** would verify:
- Repository caching logic
- ViewModel state transformations
- Data model transformations
- Navigation event handling
- Error handling logic

**Integration Tests** would verify:
- Repository + Firebase integration
- ViewModel + Repository integration
- End-to-end feature flows

**UI Tests** would verify:
- Compose UI rendering
- User interaction handling
- Navigation flows

### Manual Testing Checklist

Since automated tests are removed, manual testing should verify:

1. **Authentication Flow**
   - Login with email/password
   - Login with Google
   - Registration
   - Password reset
   - Logout

2. **Home Feed**
   - Videos load and display
   - Videos play automatically
   - Like/unlike functionality
   - Comment navigation
   - Profile navigation
   - Smooth scrolling

3. **Reels/Dashboard**
   - Vertical scrolling
   - Video preloading
   - Smooth transitions
   - Like/comment/share actions
   - Profile navigation

4. **Profile**
   - View own profile
   - View other profiles
   - Edit profile
   - Follow/unfollow
   - Video grid display

5. **Video Upload**
   - Record video
   - Select from gallery
   - Preview video
   - Add title/description
   - Upload progress
   - Background upload

6. **Comments**
   - View comments
   - Add comment
   - Reply to comment
   - Like comment
   - Delete own comment

7. **Navigation**
   - Bottom navigation
   - Deep links
   - Back navigation
   - State preservation

8. **Performance**
   - No redundant data loading
   - Smooth scrolling
   - Fast screen transitions
   - Memory usage
   - Battery usage

## Implementation Notes

### Migration Strategy

The refactor will follow this migration approach:

1. **Phase 1: Setup**
   - Create new package structure
   - Remove test directories
   - Remove unused files

2. **Phase 2: Data Layer**
   - Create Repository interfaces
   - Implement Repository classes
   - Add caching logic
   - Migrate Firebase calls

3. **Phase 3: Domain Layer**
   - Define domain models
   - Create use cases (if needed)
   - Define UI state models

4. **Phase 4: ViewModel Layer**
   - Create ViewModel classes
   - Implement state management
   - Add event handling
   - Inject repositories

5. **Phase 5: UI Layer**
   - Migrate to Compose
   - Remove XML layouts
   - Update navigation
   - Wire ViewModels

6. **Phase 6: Cleanup**
   - Remove duplicate code
   - Remove unused resources
   - Update documentation
   - Final verification

### Caching Strategy

**In-Memory Cache:**
- Use `MutableStateFlow` for reactive caching
- Cache videos, user profiles, comments
- Implement LRU eviction for memory management
- Cache TTL: 5 minutes for dynamic data, 30 minutes for static data

**Preloading Strategy:**
- Preload ±5 videos around current position
- Preload on scroll events
- Cancel preloading when user navigates away
- Use ExoPlayer's built-in caching

**Cache Invalidation:**
- Invalidate on user actions (like, comment, follow)
- Invalidate on pull-to-refresh
- Invalidate on app resume (if stale)
- Manual refresh option

### Dependency Injection

**Manual DI (No Hilt/Dagger):**

```kotlin
object RepositoryProvider {
    private val videoRepository: VideoRepository by lazy {
        VideoRepository(
            firebaseSource = FirebaseVideoSource(),
            cache = VideoCache()
        )
    }
    
    fun provideVideoRepository(): VideoRepository = videoRepository
}

class HomeViewModelFactory(
    private val videoRepository: VideoRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(videoRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
```

### Performance Optimizations

1. **Lazy Loading:** Load data only when needed
2. **Pagination:** Use Paging 3 for large lists
3. **Image Caching:** Continue using Glide with proper configuration
4. **Video Preloading:** Maintain existing ExoPlayer preloading
5. **State Hoisting:** Minimize recomposition in Compose
6. **Remember:** Use `remember` and `derivedStateOf` appropriately
7. **Immutable Collections:** Use immutable lists for UI state

### Files to Remove

**Test Files:**
- `app/src/test/` (entire directory)
- `app/src/androidTest/` (entire directory)

**Duplicate XML Implementations:**
- XML fragments that have Compose equivalents
- Corresponding layout files
- XML-based adapters replaced by Compose

**Unused Files (to be identified during analysis):**
- Unreferenced utility classes
- Unused resource files
- Deprecated code

**Old Architecture Files:**
- Direct Firebase calls in UI components
- Scattered business logic
- Duplicate event handlers

### Preserved Files

**Keep all:**
- MainActivity.java (convert to Kotlin, update navigation)
- SplashActivity.java (convert to Kotlin)
- Existing Compose implementations
- Firebase configuration files
- Resource files (drawables, strings, colors)
- Build configuration files
- ProGuard rules

## Summary

This design establishes a clean MVVM architecture for the InstaGame app with:

- **Clear separation of concerns** across Model, View, and ViewModel layers
- **Centralized data access** through Repository pattern with caching
- **Reactive state management** using StateFlow and Kotlin Flows
- **Modern UI** with Jetpack Compose (removing XML duplicates)
- **Optimized performance** through caching and preloading
- **Maintainable structure** with feature-based packages
- **Consistent patterns** for navigation, error handling, and dependency injection

The refactor preserves all existing functionality while establishing a foundation for future development and maintenance.
