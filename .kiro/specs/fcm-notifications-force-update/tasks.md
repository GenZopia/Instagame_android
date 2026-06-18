# Implementation Plan

- [ ] 1. Add Firebase dependencies and update manifest
  - Add Firebase Messaging and Remote Config dependencies to build.gradle.kts
  - Declare InstagameFCMService in AndroidManifest.xml
  - Add default notification channel metadata to manifest
  - _Requirements: 4.1, 4.2_

- [ ] 2. Implement NotificationPermissionManager
  - [ ] 2.1 Create NotificationPermissionManager class with SharedPreferences
    - Implement permission state checking across Android versions
    - Add methods for checking if permission should be requested
    - Add methods for storing rejection timestamps
    - Add 30-day interval calculation logic
    - _Requirements: 3.1, 3.3, 3.4, 3.5_
  
  - [ ] 2.2 Write property test for permission retry interval
    - **Property 11: 30-day retry interval**
    - **Validates: Requirements 3.4**
  
  - [ ] 2.3 Write property test for timestamp update on rejection
    - **Property 12: Timestamp update on retry rejection**
    - **Validates: Requirements 3.5**
  
  - [ ] 2.4 Implement permission request methods
    - Add requestPermission() with Activity parameter
    - Add handlePermissionResult() callback
    - Add permanently denied flag handling
    - _Requirements: 3.1, 3.2, 3.3_
  
  - [ ] 2.5 Write property test for permission state tracking
    - **Property 10: Rejection timestamp storage**
    - **Validates: Requirements 3.3**

- [ ] 3. Integrate permission requests in LoginActivity
  - [ ] 3.1 Add permission request in onCreate after successful sign-in
    - Initialize NotificationPermissionManager
    - Check if permission should be requested
    - Request permission if needed (Android 13+)
    - Handle permission result callback
    - _Requirements: 3.1_
  
  - [ ] 3.2 Write property test for sign-in permission logic
    - **Property 8: Permission request at sign-in**
    - **Validates: Requirements 3.1**
  
  - [ ] 3.3 Add onRequestPermissionsResult handler
    - Handle permission grant/deny results
    - Trigger FCM token registration on grant
    - Store rejection timestamp on deny
    - _Requirements: 3.3, 4.5_
  
  - [ ] 3.4 Write property test for token registration after grant
    - **Property 16: Token registration after permission grant**
    - **Validates: Requirements 4.5**

- [ ] 4. Integrate permission requests in HomeFragment
  - [ ] 4.1 Add permission check in onResume
    - Check if permission was rejected at sign-in
    - Check if 30 days have passed since last rejection
    - Request permission again if eligible
    - _Requirements: 3.2, 3.4_
  
  - [ ] 4.2 Write property test for Home Fragment retry logic
    - **Property 9: Permission retry at Home**
    - **Validates: Requirements 3.2**

- [ ] 5. Implement FCMTokenManager
  - [ ] 5.1 Create FCMTokenManager class
    - Add method to get current FCM token
    - Add method to register token with Firebase Realtime Database
    - Add method to update token in user's profile
    - Add SharedPreferences for token caching
    - _Requirements: 4.1, 4.2_
  
  - [ ] 5.2 Write property test for token database sync
    - **Property 13: FCM token database sync**
    - **Validates: Requirements 4.2**
  
  - [ ] 5.3 Integrate token registration on app start
    - Call registerToken() in MainActivity onCreate
    - Only register if permission is granted
    - Handle registration failures gracefully
    - _Requirements: 4.1, 4.5_

- [ ] 6. Implement InstagameFCMService
  - [ ] 6.1 Create InstagameFCMService extending FirebaseMessagingService
    - Override onMessageReceived for data payload handling
    - Override onNewToken for token refresh handling
    - Add createNotificationChannel method (Android 8.0+)
    - _Requirements: 1.1, 4.2, 4.3, 4.4_
  
  - [ ] 6.2 Implement notification display logic
    - Extract data payload fields (title, body, imageUrl, action, targetId)
    - Build NotificationCompat notification
    - Create PendingIntent for notification tap
    - Handle foreground and background delivery
    - _Requirements: 1.3, 4.3, 4.4_
  
  - [ ] 6.3 Write property test for foreground notification display
    - **Property 14: Foreground notification display**
    - **Validates: Requirements 4.3**
  
  - [ ] 6.4 Write property test for background notification display
    - **Property 15: Background notification display**
    - **Validates: Requirements 4.4**
  
  - [ ] 6.5 Implement image download and processing
    - Use Glide to download image from URL
    - Handle network failures gracefully
    - Scale image to fit notification (any aspect ratio)
    - Use BigPictureStyle for rich notifications
    - _Requirements: 1.1, 1.2_
  
  - [ ] 6.6 Write property test for image display
    - **Property 1: Notification image display**
    - **Validates: Requirements 1.1**
  
  - [ ] 6.7 Write property test for aspect ratio handling
    - **Property 2: Aspect ratio handling**
    - **Validates: Requirements 1.2**
  
  - [ ] 6.8 Implement graceful image failure handling
    - Catch image download exceptions
    - Display text-only notification if image fails
    - Log failures for monitoring
    - _Requirements: 1.4_
  
  - [ ] 6.9 Write property test for graceful image failure
    - **Property 4: Graceful image failure**
    - **Validates: Requirements 1.4**
  
  - [ ] 6.10 Write property test for text content preservation
    - **Property 3: Text content preservation**
    - **Validates: Requirements 1.3**

- [ ] 7. Implement notification routing
  - [ ] 7.1 Create notification click handling
    - Build PendingIntent with action and targetId extras
    - Handle open_game action
    - Handle open_profile action
    - Handle open_video action
    - Handle open_home action
    - _Requirements: 1.5_
  
  - [ ] 7.2 Write property test for notification routing
    - **Property 5: Notification routing**
    - **Validates: Requirements 1.5**
  
  - [ ] 7.3 Update MainActivity to handle notification intents
    - Check for notification extras in onCreate and onNewIntent
    - Route to appropriate fragment/activity based on action
    - Pass targetId to destination screen
    - _Requirements: 1.5_

- [ ] 8. Implement RemoteConfigManager
  - [ ] 8.1 Create RemoteConfigManager class
    - Initialize Firebase Remote Config instance
    - Set default values for min_android_version and force_update_enabled
    - Set cache expiration to 1 hour
    - _Requirements: 2.1, 5.1, 5.2_
  
  - [ ] 8.2 Implement config fetching logic
    - Add fetchConfig() with async callback
    - Handle fetch success and failure
    - Use cached values on failure
    - Activate fetched values
    - _Requirements: 2.1, 2.5_
  
  - [ ] 8.3 Write property test for Remote Config fallback
    - **Property 7: Remote Config fallback**
    - **Validates: Requirements 2.5**
  
  - [ ] 8.4 Write property test for cached values usage
    - **Property 19: Remote Config cached values**
    - **Validates: Requirements 7.3**
  
  - [ ] 8.5 Implement version comparison logic
    - Parse version string from Remote Config (e.g., "5.0" to version code)
    - Compare with BuildConfig.VERSION_CODE
    - Return true if update required
    - Handle invalid version strings gracefully
    - _Requirements: 2.2, 5.5_
  
  - [ ] 8.6 Write property test for version comparison
    - **Property 6: Version comparison logic**
    - **Validates: Requirements 2.2**
  
  - [ ] 8.7 Write property test for invalid config handling
    - **Property 18: Invalid config handling**
    - **Validates: Requirements 5.5**
  
  - [ ] 8.8 Implement force update flag logic
    - Add isForceUpdateEnabled() method
    - Return false if config fetch failed
    - Skip version check if flag is disabled
    - _Requirements: 5.3_
  
  - [ ] 8.9 Write property test for force update flag behavior
    - **Property 17: Force update flag behavior**
    - **Validates: Requirements 5.3**

- [ ] 9. Implement ForceUpdateDialog
  - [ ] 9.1 Create ForceUpdateDialog DialogFragment
    - Create newInstance factory method with minimum version parameter
    - Build non-dismissible AlertDialog
    - Set title and message with version requirement
    - Add "Update Now" button
    - _Requirements: 2.2, 2.3_
  
  - [ ] 9.2 Implement Play Store navigation
    - Create openPlayStore() method
    - Use market:// URI intent
    - Fallback to web URL if Play Store not installed
    - Handle ActivityNotFoundException
    - _Requirements: 2.4_
  
  - [ ] 9.3 Prevent dialog dismissal
    - Override onCreateDialog with setCancelable(false)
    - Override onResume to handle back button
    - Prevent outside touch dismissal
    - _Requirements: 2.3_

- [ ] 10. Integrate force update check in SplashActivity
  - [ ] 10.1 Add Remote Config initialization
    - Initialize RemoteConfigManager in onCreate
    - Fetch config asynchronously
    - Don't block splash animation or data prefetch
    - _Requirements: 2.1_
  
  - [ ] 10.2 Add version check logic
    - Check isUpdateRequired() after config fetch
    - Show ForceUpdateDialog if update required
    - Proceed to login/main if no update needed
    - Continue normally if fetch fails
    - _Requirements: 2.2, 2.5_
  
  - [ ] 10.3 Handle force update flow
    - Block navigation to next screen if update required
    - Keep dialog visible until user goes to Play Store
    - Re-check version when user returns from Play Store
    - _Requirements: 2.3, 2.4_

- [ ] 11. Add error handling and logging
  - [ ] 11.1 Add exception handling throughout FCM service
    - Wrap image download in try-catch
    - Wrap notification building in try-catch
    - Handle OutOfMemoryError for large images
    - Log all errors with relevant details
    - _Requirements: 7.1, 7.2, 7.5_
  
  - [ ] 11.2 Write property test for exception resilience
    - **Property 20: Exception resilience**
    - **Validates: Requirements 7.5**
  
  - [ ] 11.3 Add error handling in RemoteConfigManager
    - Handle network failures with retry logic
    - Handle parse errors with default values
    - Handle timeouts gracefully
    - Log all errors for monitoring
    - _Requirements: 7.3_
  
  - [ ] 11.4 Add error handling in token registration
    - Handle network failures with WorkManager retry
    - Handle null user cases
    - Handle database write failures
    - Queue updates for later retry
    - _Requirements: 7.1_

- [ ] 12. Test FCM integration end-to-end
  - Test sending data payload from Firebase Console
  - Verify notification displays with image in foreground
  - Verify notification displays with image in background
  - Test notification tap routing to correct screens
  - Test image failure fallback (invalid URL)
  - Test permission request flow at sign-in and Home Fragment
  - Test force update dialog display and Play Store navigation
  - _Requirements: All_

- [ ] 13. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.
