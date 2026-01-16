# Implementation Plan: MVVM Architecture Refactor

## Overview

This implementation plan breaks down the MVVM architecture refactor into discrete, manageable tasks. Each task builds on previous work to incrementally transform the codebase from its current state to a clean MVVM architecture with Jetpack Compose, while preserving all functionality.

The refactor follows a bottom-up approach: Data Layer → Domain Layer → ViewModel Layer → UI Layer → Cleanup.

## Tasks

- [x] 1. Initial cleanup and setup
  - Remove all test directories and files
  - Remove test dependencies from build.gradle.kts
  - Create new feature-based package structure
  - _Requirements: 1.2, 11.1, 11.2, 11.3, 7.1_

- [x] 2. Create common infrastructure
  - [x] 2.1 Create common package structure
    - Create `common/ui/`, `common/navigation/`, `common/utils/`, `common/models/` packages
    - _Requirements: 7.3_

  - [x] 2.2 Create base classes and interfaces
    - Create `BaseViewModel` abstract class
    - Create `Repository` interface
    - Create `UiState` sealed class
    - Create `DataError` sealed class
    - _Requirements: 3.1, 4.1, 10.3, 10.4_

  - [x] 2.3 Create navigation infrastructure
    - Create `Screen` sealed class for navigation routes
    - Create `NavigationEvent` sealed class
    - _Requirements: 6.4_

  - [x] 2.4 Create dependency injection infrastructure
    - Create `RepositoryProvider` object
    - Create base `ViewModelFactory` class
    - _Requirements: 9.2, 9.3, 9.4_

- [x] 3. Refactor Authentication feature
  - [x] 3.1 Create auth package structure
    - Create `features/auth/data/`, `features/auth/domain/`, `features/auth/ui/` packages
    - _Requirements: 7.1, 7.2_

  - [x] 3.2 Create auth data layer
    - Create `User` domain model
    - Create `FirebaseAuthSource` class
    - Create `UserRepository` with caching
    - _Requirements: 3.4, 4.1, 4.4, 5.1_

  - [x] 3.3 Create auth ViewModels
    - Create `LoginViewModel` with StateFlow
    - Create `RegisterViewModel` with StateFlow
    - Create `ForgotPasswordViewModel` with StateFlow
    - Inject `UserRepository` via constructor
    - _Requirements: 3.2, 9.1, 10.1_

  - [x] 3.4 Migrate auth UI to Compose
    - Convert `LoginActivity` to use Compose
    - Convert `RegisterActivity` to use Compose
    - Convert `ForgotPassword` to use Compose
    - Remove XML layouts for auth screens
    - _Requirements: 2.1, 2.2, 3.3_

- [x] 4. Refactor Home feature
  - [x] 4.1 Create home package structure
    - Create `features/home/data/`, `features/home/domain/`, `features/home/ui/` packages
    - Move existing `HomeFragmentCompose.kt` to new location
    - _Requirements: 7.1, 7.2_

  - [x] 4.2 Create home data layer
    - Create `HomeVideoData` domain model
    - Create `FirebaseHomeSource` class
    - Create `VideoRepository` with caching
    - Implement cache invalidation strategy
    - _Requirements: 3.4, 4.1, 4.2, 4.4, 5.1, 5.2_

  - [x] 4.3 Refactor HomeViewModel
    - Update `HomeViewModel` to use `VideoRepository`
    - Implement `HomeUiState` data class
    - Implement `HomeEvent` sealed class
    - Remove direct Firebase calls
    - Add preloading logic
    - _Requirements: 3.2, 4.5, 5.5, 6.1, 10.1, 10.3_

  - [x] 4.4 Update HomeScreen Compose UI
    - Update `HomeScreen.kt` to use new state/events
    - Consolidate event handlers
    - Remove duplicate onClick listeners
    - _Requirements: 3.3, 6.1, 6.2_

  - [x] 4.5 Remove old home implementations
    - Identify and remove XML-based home fragments (if any)
    - Remove corresponding layout files
    - Remove old adapter classes (if replaced)
    - _Requirements: 1.1, 2.1, 2.2, 2.3_

- [x] 5. Refactor Reels/Dashboard feature
  - [x] 5.1 Create reels package structure
    - Create `features/reels/data/`, `features/reels/domain/`, `features/reels/ui/` packages
    - Move existing Compose files to new location
    - _Requirements: 7.1, 7.2_

  - [x] 5.2 Create reels data layer
    - Create `ReelData` domain model
    - Create `FirebaseReelSource` class
    - Create `ReelRepository` with caching
    - Update `ReelPagingSource` to use repository
    - _Requirements: 3.4, 4.1, 4.4, 5.3_

  - [x] 5.3 Refactor ReelViewModel
    - Update `ReelViewModel` to use `ReelRepository`
    - Implement `ReelUiState` data class
    - Implement `ReelEvent` sealed class
    - Add video preloading coordination
    - _Requirements: 3.2, 4.5, 5.5, 10.1, 10.3_

  - [x] 5.4 Update ReelScreen Compose UI
    - Update `ReelScreen.kt` to use new state/events
    - Update `VideoPlayer.kt` component
    - Consolidate event handlers
    - _Requirements: 3.3, 6.1_

  - [x] 5.5 Remove old dashboard implementations
    - Remove XML-based dashboard fragments (if any)
    - Remove corresponding layout files
    - Remove old reel adapter classes (if replaced)
    - _Requirements: 1.1, 2.1, 2.2, 2.3_

- [x] 6. Checkpoint - Verify core features working
  - Ensure all tests pass, ask the user if questions arise.
  - Verify authentication flow works
  - Verify home feed loads and plays videos
  - Verify reels scroll and play smoothly

- [x] 7. Refactor Profile feature
  - [x] 7.1 Create profile package structure
    - Create `features/profile/data/`, `features/profile/domain/`, `features/profile/ui/` packages
    - _Requirements: 7.1, 7.2_

  - [x] 7.2 Create profile data layer
    - Create `Profile` domain model
    - Create `FirebaseProfileSource` class
    - Create `ProfileRepository` with caching
    - _Requirements: 3.4, 4.1, 4.4, 5.1_

  - [x] 7.3 Create profile ViewModels
    - Create `ProfileViewModel` with StateFlow
    - Create `EditProfileViewModel` with StateFlow
    - Inject `ProfileRepository` and `UserRepository`
    - _Requirements: 3.2, 9.1, 10.1_

  - [x] 7.4 Migrate profile UI to Compose
    - Convert `ProfileFragment` to use Compose
    - Convert `EditProfileActivity` to use Compose
    - Remove XML layouts for profile screens
    - _Requirements: 2.1, 2.2, 3.3_

- [x] 8. Refactor Post/Upload feature
  - [x] 8.1 Create post package structure
    - Create `features/post/data/`, `features/post/domain/`, `features/post/ui/` packages
    - _Requirements: 7.1, 7.2_

  - [x] 8.2 Create post data layer
    - Create upload domain models
    - Create `UploadService` class
    - Create `PostRepository`
    - _Requirements: 3.4, 4.1_

  - [x] 8.3 Create post ViewModels
    - Create `PostViewModel` with StateFlow
    - Create `PreviewViewModel` with StateFlow
    - Create `UploadInfoViewModel` with StateFlow
    - _Requirements: 3.2, 10.1_

  - [x] 8.4 Migrate post UI to Compose
    - Convert `Post_mainactivity` to use Compose
    - Convert `VideoPreviewActivity` to use Compose
    - Convert `VideoUploadInfoActivity` to use Compose
    - Keep `VideoUploadForegroundService` as-is
    - Remove XML layouts for post screens
    - _Requirements: 2.1, 2.2, 3.3_

- [x] 9. Refactor Channel feature
  - [x] 9.1 Create channel package structure
    - Create `features/channel/data/`, `features/channel/domain/`, `features/channel/ui/` packages
    - _Requirements: 7.1, 7.2_

  - [x] 9.2 Create channel data layer
    - Create channel domain models
    - Create `FirebaseChannelSource` class
    - Create `ChannelRepository` with caching
    - _Requirements: 3.4, 4.1, 4.4, 5.1_

  - [x] 9.3 Create channel ViewModels
    - Create `ChannelViewModel` with StateFlow
    - Create `VideoDetailViewModel` with StateFlow
    - _Requirements: 3.2, 10.1_

  - [x] 9.4 Migrate channel UI to Compose
    - Convert `ChannelActivity` to use Compose
    - Convert `VideoDetailActivity` to use Compose
    - Convert channel fragments to Compose
    - Remove XML layouts for channel screens
    - _Requirements: 2.1, 2.2, 3.3_

- [x] 10. Refactor Comments feature
  - [x] 10.1 Create comments package structure
    - Create `features/comments/data/`, `features/comments/domain/`, `features/comments/ui/` packages
    - Move existing models to domain package
    - _Requirements: 7.1, 7.2_

  - [x] 10.2 Refactor comments data layer
    - Update `Comment` and `Reply` models
    - Refactor `CommentsRepository` with caching
    - _Requirements: 3.4, 4.1, 4.4, 5.1_

  - [x] 10.3 Create comments ViewModel
    - Create `CommentsViewModel` with StateFlow
    - Implement `CommentsUiState` data class
    - Implement `CommentsEvent` sealed class
    - _Requirements: 3.2, 10.1, 10.3_

  - [x] 10.4 Migrate comments UI to Compose
    - Convert `CommentsBottomSheet` to full Compose
    - Remove `CommentsBottomSheetFragment` if redundant
    - Update `CommentsAdapter` or replace with Compose LazyColumn
    - _Requirements: 2.1, 3.3_

- [x] 11. Checkpoint - Verify all features working
  - Ensure all tests pass, ask the user if questions arise.
  - Verify all features work end-to-end
  - Verify no crashes or errors
  - Verify smooth navigation

- [x] 12. Update MainActivity and navigation
  - [x] 12.1 Convert MainActivity to Kotlin
    - Convert `MainActivity.java` to `MainActivity.kt`
    - Update navigation setup for Compose
    - _Requirements: 8.5_

  - [x] 12.2 Update navigation graph
    - Update navigation references to point to Compose implementations
    - Remove references to deleted XML fragments
    - _Requirements: 2.4_

  - [x] 12.3 Update SplashActivity
    - Convert `SplashActivity.java` to Kotlin
    - Simplify if possible
    - _Requirements: 8.1_

- [x] 13. Remove unused code and resources
  - [x] 13.1 Analyze and remove unused classes
    - Identify unreferenced utility classes
    - Remove old adapter classes
    - Remove old ViewHolder classes
    - Remove `TempStorage` and similar temporary classes
    - _Requirements: 1.1, 1.4_

  - [x] 13.2 Remove unused XML layouts
    - Remove layout files for deleted fragments/activities
    - Remove unused custom view layouts
    - _Requirements: 1.3, 2.2_

  - [x] 13.3 Remove unused resources
    - Remove unreferenced drawable files
    - Remove unreferenced string resources
    - Remove unreferenced color resources
    - _Requirements: 1.3_

  - [x] 13.4 Remove old vertical_recylerview_custom package
    - Remove `HomeAdapter.java` (replaced by Compose)
    - Remove `VideoViewHolder.java` (replaced by Compose)
    - Remove `PlayerManager.java` (if replaced)
    - Keep only if still needed for specific features
    - _Requirements: 1.1, 2.1_

- [x] 14. Optimize and consolidate
  - [x] 14.1 Consolidate duplicate event handlers
    - Verify no duplicate onClick listeners across files
    - Ensure event handlers defined once per feature
    - _Requirements: 6.1, 6.2, 6.5_

  - [x] 14.2 Verify caching implementation
    - Verify repositories cache data correctly
    - Verify no redundant network calls
    - Test cache invalidation
    - _Requirements: 5.1, 5.2, 5.4_

  - [x] 14.3 Verify dependency injection
    - Verify all ViewModels use constructor injection
    - Verify no hard-coded dependencies
    - Verify ViewModelFactory classes work correctly
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_

- [x] 15. Documentation and final cleanup
  - [x] 15.1 Update README.md
    - Add Architecture section describing MVVM structure
    - Update project structure documentation
    - Document data flow (Repository → ViewModel → View)
    - _Requirements: 12.1, 12.2, 12.5_

  - [x] 15.2 Add package documentation
    - Add package-info.kt or README files for main packages
    - Document naming conventions
    - _Requirements: 12.1, 12.4_

  - [x] 15.3 Code cleanup
    - Remove commented-out code
    - Fix any remaining warnings
    - Format code consistently
    - _Requirements: 1.1_

  - [x] 15.4 Final verification
    - Build project successfully
    - Verify no compilation errors
    - Verify no lint errors
    - Run through manual testing checklist
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7_

- [x] 16. Final checkpoint - Complete refactor verification
  - Ensure all tests pass, ask the user if questions arise.
  - Verify all features work correctly
  - Verify performance is maintained or improved
  - Verify no memory leaks
  - Verify smooth user experience

## Notes

- Each task should be completed and verified before moving to the next
- Preserve all existing functionality - no feature should break
- Keep UI design exactly as it is - only change the underlying architecture
- Test each feature after refactoring to ensure it still works
- Commit changes frequently to allow rollback if needed
- Focus on one feature at a time to minimize risk
- The refactor is complete when all tasks are done and the app works identically to before, but with clean MVVM architecture
