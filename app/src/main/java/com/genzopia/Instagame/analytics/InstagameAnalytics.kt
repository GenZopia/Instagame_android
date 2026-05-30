package com.genzopia.Instagame.analytics

import android.app.Application
import android.util.Log
import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.amplitude.android.events.Identify
import com.genzopia.Instagame.BuildConfig

/**
 * InstagameAnalytics — single source of truth for all Amplitude tracking.
 *
 * Two funnels:
 *  1. USER JOURNEY  — acquisition → auth → onboarding → engagement → game play
 *  2. USER EXPERIENCE — splash load time, video load time, watch depth,
 *                       game session duration, buffering, errors
 *
 * Anonymous → Identified stitching:
 *  - Before login: events fire with Amplitude's auto-generated device_id
 *  - On login/register: identifyUser() calls setUserId(uid) which merges
 *    all prior anonymous events to the real user profile retroactively
 */
object InstagameAnalytics {

    private const val TAG = "InstagameAnalytics"
    private var amplitude: Amplitude? = null
    private var isInitialized = false

    // ── Init ──────────────────────────────────────────────────────────────────

    fun init(application: Application) {
        if (isInitialized) return
        try {
            amplitude = Amplitude(
                Configuration(
                    apiKey = BuildConfig.AMPLITUDE_API_KEY,
                    context = application,
                    flushEventsOnClose = true,
                    flushQueueSize = 10,
                    flushIntervalMillis = 30_000,
                    minTimeBetweenSessionsMillis = 30_000, // 30s — session ends quickly after app closes
                    optOut = false
                )
            )
            isInitialized = true
            Log.d(TAG, "Amplitude initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Amplitude init failed: ${e.message}")
        }
    }

    // ── Identity ──────────────────────────────────────────────────────────────

    fun identifyUser(
        uid: String,
        name: String,
        email: String,
        profilePhotoUrl: String?,
        registrationMethod: String = "unknown"
    ) {
        amplitude?.setUserId(uid)

        val publicAvatarUrl = toPublicR2Url(profilePhotoUrl)

        // ── Debug: log every field so you can verify in Logcat ───────────────
        Log.d(TAG, "=== identifyUser ===")
        Log.d(TAG, "  uid               = $uid")
        Log.d(TAG, "  name              = '$name'")
        Log.d(TAG, "  email             = '$email'")
        Log.d(TAG, "  raw photoUrl      = '$profilePhotoUrl'")
        Log.d(TAG, "  public avatarUrl  = '$publicAvatarUrl'")
        Log.d(TAG, "  method            = $registrationMethod")
        Log.d(TAG, "====================")

        val identify = Identify()
            .set("\$name", name)
            .set("\$email", email)
            .set("\$avatar", publicAvatarUrl)
            .set("name", name)
            .set("email", email)
            .set("profile_photo_url", profilePhotoUrl ?: "")
            .set("registration_method", registrationMethod)
            .setOnce("first_seen_at", System.currentTimeMillis())
            .setOnce("registration_method_first", registrationMethod)
        amplitude?.identify(identify)
        amplitude?.flush() // force-send immediately so it shows up right away
        Log.d(TAG, "identifyUser sent and flushed for uid=$uid")
    }

    /**
     * Builds the avatar URL to pass to Amplitude's $avatar property.
     *
     * Profile photos are uploaded to R2 at path: instagame/{uid}/{photoId}.{ext}
     * The worker URL format: https://file-upload-worker.genzopia.workers.dev/?key=instagame/uid/file.jpg
     *
     * We pass the worker URL directly — Amplitude will attempt to load it.
     * If the worker requires auth and blocks it, fall back to the public R2 CDN.
     *
     * Public R2 CDN (no auth needed):
     *   https://pub-0caba249d019456b9181ce1575ef825e.r2.dev/instagame/uid/file.jpg
     */
    private fun toPublicR2Url(url: String?): String {
        if (url.isNullOrBlank()) return ""
        val workerBase = "https://file-upload-worker.genzopia.workers.dev/?key="
        val publicBase = "https://pub-22db73b8d33244d1a53831aed22cd78b.r2.dev/"
        val key = when {
            url.startsWith(workerBase) -> url.removePrefix(workerBase)
            else -> return url  // Google photo or other public URL — use as-is
        }
        // Fix duplicate filename segment: "a/b/file.jpg/file.jpg" → "a/b/file.jpg"
        val parts = key.split("/")
        val cleanKey = if (parts.size >= 2 && parts.last() == parts[parts.size - 2]) {
            parts.dropLast(1).joinToString("/")
        } else {
            key
        }
        return publicBase + cleanKey
    }

    fun incrementUserProperty(property: String, amount: Double = 1.0) {
        val identify = Identify().add(property, amount)
        amplitude?.identify(identify)
    }

    fun setUserProperty(key: String, value: String) {
        val identify = Identify().set(key, value)
        amplitude?.identify(identify)
    }

    fun clearIdentity() {
        amplitude?.setUserId(null)
        amplitude?.reset()
        Log.d(TAG, "User identity cleared")
    }

    /** Set userId immediately without a full identify call — use on app start for returning users. */
    fun setUserId(uid: String) {
        amplitude?.setUserId(uid)
        Log.d(TAG, "UserId set: $uid")
    }

    /** Flush all queued events immediately — call when app goes to background. */
    fun flushEvents() {
        amplitude?.flush()
        Log.d(TAG, "Events flushed")
    }

    private fun track(eventName: String, properties: Map<String, Any?> = emptyMap()) {
        if (!isInitialized) {
            Log.w(TAG, "track() before init — dropped: $eventName")
            return
        }
        try {
            val clean = properties.filterValues { it != null }.mapValues { it.value!! }
            amplitude?.track(eventName, clean)
            Log.d(TAG, "Tracked: $eventName | $clean")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to track $eventName: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FUNNEL 1 — USER JOURNEY
    // ─────────────────────────────────────────────────────────────────────────

    // Splash
    fun trackAppOpened(source: String, deepLinkVideoId: String? = null) =
        track("app_opened", mapOf(
            "source" to source,                    // "cold_start" | "warm_start" | "deep_link"
            "deep_link_video_id" to deepLinkVideoId
        ))

    fun trackSplashCompleted(durationMs: Long, prefetchSuccess: Boolean) =
        track("splash_completed", mapOf(
            "duration_ms" to durationMs,
            "prefetch_success" to prefetchSuccess
        ))

    // Auth
    fun trackLoginScreenViewed() = track("login_screen_viewed")

    fun trackLoginMethodSelected(method: String) =
        track("login_method_selected", mapOf("method" to method)) // "email" | "google"

    fun trackLoginAttempted(method: String) =
        track("login_attempted", mapOf("method" to method))

    fun trackLoginSuccess(method: String, uid: String, name: String) =
        track("login_success", mapOf("method" to method, "uid" to uid, "name" to name))

    fun trackLoginFailed(method: String, errorMessage: String) =
        track("login_failed", mapOf("method" to method, "error_message" to errorMessage))

    fun trackForgotPasswordTapped() = track("forgot_password_tapped")

    fun trackRegisterScreenViewed() = track("register_screen_viewed")

    fun trackRegisterPhotoSelected(source: String) =
        track("register_photo_selected", mapOf("source" to source)) // "gallery" | "camera"

    fun trackRegisterAttempted() = track("register_attempted")

    fun trackRegisterSuccess(uid: String, name: String) =
        track("register_success", mapOf("uid" to uid, "name" to name))

    fun trackRegisterFailed(errorMessage: String) =
        track("register_failed", mapOf("error_message" to errorMessage))

    fun trackProfileCompletionViewed(missingFields: List<String>) =
        track("profile_completion_viewed", mapOf(
            "missing_fields" to missingFields.joinToString(",")
        ))

    fun trackProfileCompletionSubmitted(fieldsFilled: List<String>) =
        track("profile_completion_submitted", mapOf(
            "fields_filled" to fieldsFilled.joinToString(",")
        ))

    fun trackPrivacyPolicyViewed() = track("privacy_policy_viewed")
    fun trackPrivacyPolicyAccepted() = track("privacy_policy_accepted")

    // Onboarding Tutorial
    fun trackOnboardingStarted() = track("onboarding_started")

    fun trackOnboardingStepCompleted(stepNumber: Int, stepName: String) =
        track("onboarding_step_completed", mapOf(
            "step_number" to stepNumber,
            "step_name" to stepName
        ))

    fun trackOnboardingScrollPerformed() = track("onboarding_scroll_performed")

    fun trackOnboardingCompleted(durationMs: Long) =
        track("onboarding_completed", mapOf("duration_ms" to durationMs))

    fun trackOnboardingSkipped(atStep: Int) =
        track("onboarding_skipped", mapOf("at_step" to atStep))

    // Navigation
    fun trackBottomNavTapped(tab: String, previousTab: String) =
        track("bottom_nav_tapped", mapOf("tab" to tab, "previous_tab" to previousTab))

    // Home Feed
    fun trackHomeScreenViewed(followedUsersCount: Int, gamesCount: Int) =
        track("home_screen_viewed", mapOf(
            "followed_users_count" to followedUsersCount,
            "games_count" to gamesCount
        ))

    fun trackFollowingUserTapped(targetUid: String, targetName: String) =
        track("following_user_tapped", mapOf(
            "target_uid" to targetUid,
            "target_name" to targetName
        ))

    fun trackHomeGameCardTapped(gameId: String, gameName: String, positionInList: Int) =
        track("home_game_card_tapped", mapOf(
            "game_id" to gameId,
            "game_name" to gameName,
            "position_in_list" to positionInList
        ))

    fun trackGameDetailSheetOpened(gameId: String, gameName: String, source: String) =
        track("game_detail_sheet_opened", mapOf(
            "game_id" to gameId,
            "game_name" to gameName,
            "source" to source          // "home_card" | "channel_games"
        ))

    fun trackGameDetailSheetDismissed(gameId: String, gameName: String, didPlay: Boolean) =
        track("game_detail_sheet_dismissed", mapOf(
            "game_id" to gameId,
            "game_name" to gameName,
            "did_play" to didPlay       // false = user bailed without playing
        ))

    fun trackGameDetailDeveloperTapped(gameId: String, gameName: String, developerId: String, developerName: String) =
        track("game_detail_developer_tapped", mapOf(
            "game_id" to gameId,
            "game_name" to gameName,
            "developer_id" to developerId,
            "developer_name" to developerName
        ))

    fun trackHomeSearchUsed(query: String, resultsCount: Int) =
        track("home_search_used", mapOf("query" to query, "results_count" to resultsCount))

    // Reel Feed
    fun trackReelFeedOpened(source: String) =
        track("reel_feed_opened", mapOf("source" to source))

    fun trackReelViewed(
        videoId: String,
        videoTitle: String,
        reelIndex: Int,
        developerId: String,
        developerName: String,
        gameId: String,
        gameName: String
    ) = track("reel_viewed", mapOf(
        "video_id" to videoId,
        "video_title" to videoTitle,
        "reel_index" to reelIndex,
        "developer_id" to developerId,
        "developer_name" to developerName,
        "game_id" to gameId,
        "game_name" to gameName
    ))

    fun trackReelSwiped(fromIndex: Int, toIndex: Int) =
        track("reel_swiped", mapOf(
            "from_index" to fromIndex,
            "to_index" to toIndex,
            "direction" to if (toIndex > fromIndex) "up" else "down"
        ))

    fun trackReelLiked(videoId: String, videoTitle: String, developerId: String, gameId: String) =
        track("reel_liked", mapOf(
            "video_id" to videoId,
            "video_title" to videoTitle,
            "developer_id" to developerId,
            "game_id" to gameId
        ))

    fun trackReelUnliked(videoId: String, videoTitle: String) =
        track("reel_unliked", mapOf("video_id" to videoId, "video_title" to videoTitle))

    fun trackReelDoubleTapGameLaunch(videoId: String, gameId: String, gameName: String) =
        track("reel_double_tap_game_launch", mapOf(
            "video_id" to videoId,
            "game_id" to gameId,
            "game_name" to gameName
        ))

    fun trackReelCommentOpened(videoId: String, videoTitle: String) =
        track("reel_comment_opened", mapOf("video_id" to videoId, "video_title" to videoTitle))

    fun trackReelShareTapped(videoId: String, gameId: String) =
        track("reel_share_tapped", mapOf("video_id" to videoId, "game_id" to gameId))

    fun trackReelProfilePhotoTapped(videoId: String, developerId: String, developerName: String) =
        track("reel_profile_photo_tapped", mapOf(
            "video_id" to videoId,
            "developer_id" to developerId,
            "developer_name" to developerName
        ))

    fun trackReelFollowTapped(developerId: String, developerName: String, action: String) =
        track("reel_follow_tapped", mapOf(
            "developer_id" to developerId,
            "developer_name" to developerName,
            "action" to action  // "follow" | "unfollow"
        ))

    // Comments
    fun trackCommentSheetOpened(videoId: String) =
        track("comment_sheet_opened", mapOf("video_id" to videoId))

    fun trackCommentPosted(videoId: String, commentLength: Int) =
        track("comment_posted", mapOf("video_id" to videoId, "comment_length" to commentLength))

    fun trackCommentReplyPosted(videoId: String, parentCommentId: String) =
        track("comment_reply_posted", mapOf(
            "video_id" to videoId,
            "parent_comment_id" to parentCommentId
        ))

    // Channel
    fun trackChannelViewed(developerId: String, developerName: String, source: String) =
        track("channel_viewed", mapOf(
            "developer_id" to developerId,
            "developer_name" to developerName,
            "source" to source  // "reel_profile_photo" | "reel_name_tap" | "home"
        ))

    fun trackChannelTabSwitched(developerId: String, tab: String) =
        track("channel_tab_switched", mapOf("developer_id" to developerId, "tab" to tab))

    fun trackChannelFollowTapped(developerId: String, developerName: String, action: String) =
        track("channel_follow_tapped", mapOf(
            "developer_id" to developerId,
            "developer_name" to developerName,
            "action" to action
        ))

    fun trackChannelGameTapped(developerId: String, gameId: String, gameName: String) =
        track("channel_game_tapped", mapOf(
            "developer_id" to developerId,
            "game_id" to gameId,
            "game_name" to gameName
        ))

    fun trackChannelVideoTapped(developerId: String, videoId: String, videoTitle: String) =
        track("channel_video_tapped", mapOf(
            "developer_id" to developerId,
            "video_id" to videoId,
            "video_title" to videoTitle
        ))

    // User Profile
    fun trackProfileScreenViewed() = track("profile_screen_viewed")
    fun trackProfilePhotoTapped() = track("profile_photo_tapped")
    fun trackProfilePhotoFullscreenOpened() = track("profile_photo_fullscreen_opened")
    fun trackProfileTabSwitched(tab: String) =
        track("profile_tab_switched", mapOf("tab" to tab))
    fun trackEditProfileOpened() = track("edit_profile_opened")
    fun trackProfileBioExpanded() = track("profile_bio_expanded")
    fun trackProfileMenuOpened() = track("profile_menu_opened")
    fun trackLogoutTapped() = track("logout_tapped")
    fun trackLogoutCompleted(sessionDurationMs: Long) =
        track("logout_completed", mapOf("session_duration_ms" to sessionDurationMs))

    // Session
    fun trackSessionStarted(sessionId: String) =
        track("session_started", mapOf("session_id" to sessionId))

    fun trackAppBackgrounded(currentScreen: String, sessionDurationMs: Long) =
        track("app_backgrounded", mapOf(
            "current_screen" to currentScreen,
            "session_duration_ms" to sessionDurationMs
        ))

    fun trackAppForegrounded(timeAwayMs: Long) =
        track("app_foregrounded", mapOf("time_away_ms" to timeAwayMs))

    fun trackSessionEnded(
        sessionDurationMs: Long,
        screensVisited: Int,
        reelsWatched: Int,
        gamesPlayed: Int
    ) = track("session_ended", mapOf(
        "session_duration_ms" to sessionDurationMs,
        "screens_visited" to screensVisited,
        "reels_watched" to reelsWatched,
        "games_played" to gamesPlayed
    ))

    // Deep link
    fun trackDeepLinkOpened(videoId: String, resolved: Boolean) =
        track("deep_link_opened", mapOf("video_id" to videoId, "resolved" to resolved))

    // ─────────────────────────────────────────────────────────────────────────
    // FUNNEL 2 — USER EXPERIENCE (performance + depth metrics)
    // ─────────────────────────────────────────────────────────────────────────

    fun trackSplashLoadTime(
        animationDurationMs: Long,
        prefetchDurationMs: Long,
        totalDurationMs: Long
    ) = track("perf_splash_load_time", mapOf(
        "animation_duration_ms" to animationDurationMs,
        "prefetch_duration_ms" to prefetchDurationMs,
        "total_duration_ms" to totalDurationMs
    ))

    /** Time from reel becoming active to first video frame rendered. */
    fun trackVideoLoadTime(
        videoId: String,
        videoTitle: String,
        loadDurationMs: Long,
        wasPreloaded: Boolean
    ) = track("perf_video_load_time", mapOf(
        "video_id" to videoId,
        "video_title" to videoTitle,
        "load_duration_ms" to loadDurationMs,
        "was_preloaded" to wasPreloaded
    ))

    /** Fired when user leaves a reel — captures watch depth. */
    fun trackVideoWatchTime(
        videoId: String,
        videoTitle: String,
        developerName: String,
        gameName: String,
        watchDurationMs: Long,
        videoDurationMs: Long,
        completionPercent: Int
    ) = track("video_watch_time", mapOf(
        "video_id" to videoId,
        "video_title" to videoTitle,
        "developer_name" to developerName,
        "game_name" to gameName,
        "watch_duration_ms" to watchDurationMs,
        "video_duration_ms" to videoDurationMs,
        "completion_percent" to completionPercent
    ))

    fun trackVideoBufferingOccurred(videoId: String, videoTitle: String, bufferDurationMs: Long) =
        track("perf_video_buffering", mapOf(
            "video_id" to videoId,
            "video_title" to videoTitle,
            "buffer_duration_ms" to bufferDurationMs
        ))

    fun trackVideoPlaybackError(videoId: String, videoTitle: String, errorType: String) =
        track("perf_video_playback_error", mapOf(
            "video_id" to videoId,
            "video_title" to videoTitle,
            "error_type" to errorType
        ))

    fun trackGameLaunchInitiated(gameId: String, gameName: String, source: String) =
        track("game_launch_initiated", mapOf(
            "game_id" to gameId,
            "game_name" to gameName,
            "source" to source  // "reel_double_tap" | "channel_games" | "home_card"
        ))

    fun trackGameUrlFetchStarted(gameId: String, gameName: String) =
        track("perf_game_url_fetch_started", mapOf(
            "game_id" to gameId,
            "game_name" to gameName
        ))

    fun trackGameUrlFetchSuccess(
        gameId: String,
        gameName: String,
        durationMs: Long,
        urlType: String   // "direct" | "signed"
    ) = track("perf_game_url_fetch_success", mapOf(
        "game_id" to gameId,
        "game_name" to gameName,
        "duration_ms" to durationMs,
        "url_type" to urlType
    ))

    fun trackGameUrlFetchFailed(gameId: String, gameName: String, error: String) =
        track("perf_game_url_fetch_failed", mapOf(
            "game_id" to gameId,
            "game_name" to gameName,
            "error" to error
        ))

    /** Fired when WebView onPageFinished fires — game is fully loaded and playable. */
    fun trackGameLoaded(
        gameId: String,
        gameName: String,
        orientation: String,
        loadDurationMs: Long
    ) = track("game_loaded", mapOf(
        "game_id" to gameId,
        "game_name" to gameName,
        "orientation" to orientation,
        "load_duration_ms" to loadDurationMs
    ))

    /** Fired in Game_mode.onDestroy() — full play session length. */
    fun trackGameSessionEnded(gameId: String, gameName: String, sessionDurationMs: Long, exitMethod: String = "unknown") {
        track("game_session_ended", mapOf(
            "game_id" to gameId,
            "game_name" to gameName,
            "session_duration_ms" to sessionDurationMs,
            "exit_method" to exitMethod   // "back_button" | "system" | "unknown"
        ))
        incrementUserProperty("total_games_played")
    }

    fun trackGameBackPressed(gameId: String, gameName: String, sessionDurationMs: Long) =
        track("game_back_pressed", mapOf(
            "game_id" to gameId,
            "game_name" to gameName,
            "session_duration_ms" to sessionDurationMs
        ))

    // ─────────────────────────────────────────────────────────────────────────
    // UPLOAD FUNNEL
    // ─────────────────────────────────────────────────────────────────────────

    fun trackUploadScreenViewed() = track("upload_screen_viewed")

    fun trackUploadStarted(gameId: String, gameName: String) =
        track("upload_started", mapOf(
            "game_id" to gameId,
            "game_name" to gameName
        ))

    fun trackUploadCompleted() {
        track("upload_completed")
        incrementUserProperty("total_videos_uploaded")
    }

    fun trackUploadFailed(reason: String) =
        track("upload_failed", mapOf("reason" to reason)) // "upload_error" | "cancelled"
}
