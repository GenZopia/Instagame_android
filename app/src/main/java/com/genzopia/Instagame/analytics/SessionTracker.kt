package com.genzopia.Instagame.analytics

import android.os.Handler
import android.os.Looper
import java.util.UUID

/**
 * SessionTracker — tracks the lifecycle of a single app session.
 *
 * Session ends ONLY when the user truly leaves the app (home button, recents,
 * lock screen). Opening Game_mode, GameDetailSheet, or any in-app Activity
 * must NOT end the session.
 *
 * The key mechanism: ProcessLifecycleOwner.onStop fires even during
 * Activity-to-Activity transitions. We use a 700 ms grace window — if
 * onStart fires within that window, the "background" was just a transition
 * and we cancel the end-session. Only if the app stays backgrounded longer
 * than the grace window do we treat it as a real background event.
 */
object SessionTracker {

    private var sessionId: String = UUID.randomUUID().toString()
    private var sessionStartMs: Long = System.currentTimeMillis()
    private var backgroundedAtMs: Long = 0L
    private var currentScreen: String = "splash"

    private var screensVisited: Int = 0
    private var reelsWatched: Int = 0
    private var gamesPlayed: Int = 0

    // Grace window: if the app comes back to foreground within this many ms
    // after going to background, we treat it as an Activity transition (not
    // a real background) and suppress the app_backgrounded / session_ended events.
    private const val BACKGROUND_GRACE_MS = 700L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var backgroundRunnable: Runnable? = null
    private var isReallyBackgrounded = false

    fun onAppCreated() {
        sessionId = UUID.randomUUID().toString()
        sessionStartMs = System.currentTimeMillis()
        InstagameAnalytics.trackSessionStarted(sessionId)
    }

    fun onScreenChanged(screenName: String) {
        currentScreen = screenName
        screensVisited++
    }

    fun onReelWatched() {
        reelsWatched++
        InstagameAnalytics.incrementUserProperty("total_reels_watched")
    }

    fun onGamePlayed() {
        gamesPlayed++
    }

    /**
     * Called by ProcessLifecycleOwner.onStop.
     * We don't fire app_backgrounded immediately — we wait BACKGROUND_GRACE_MS
     * to see if onAppForegrounded cancels it (Activity transition case).
     */
    fun onAppBackgrounded() {
        backgroundedAtMs = System.currentTimeMillis()
        isReallyBackgrounded = false

        // Cancel any previously pending runnable
        backgroundRunnable?.let { mainHandler.removeCallbacks(it) }

        backgroundRunnable = Runnable {
            // Grace window elapsed without a foreground call — this is real
            isReallyBackgrounded = true
            val sessionDurationMs = System.currentTimeMillis() - sessionStartMs
            InstagameAnalytics.trackAppBackgrounded(currentScreen, sessionDurationMs)
            InstagameAnalytics.flushEvents()
        }.also {
            mainHandler.postDelayed(it, BACKGROUND_GRACE_MS)
        }
    }

    /**
     * Called by ProcessLifecycleOwner.onStart.
     * If we're still in the grace window, cancel the pending background event —
     * this was just an Activity transition, not a real background.
     */
    fun onAppForegrounded() {
        val pending = backgroundRunnable
        if (pending != null) {
            mainHandler.removeCallbacks(pending)
            backgroundRunnable = null
        }

        if (isReallyBackgrounded && backgroundedAtMs > 0L) {
            // App was truly backgrounded and came back — track time away
            val timeAwayMs = System.currentTimeMillis() - backgroundedAtMs
            InstagameAnalytics.trackAppForegrounded(timeAwayMs)
            isReallyBackgrounded = false
            backgroundedAtMs = 0L
        }
        // If not really backgrounded, this was just an Activity transition — do nothing
    }

    /**
     * Call this only when the user explicitly logs out or the app process is
     * truly ending. Do NOT call on every background event.
     */
    fun onSessionEnded() {
        backgroundRunnable?.let { mainHandler.removeCallbacks(it) }
        backgroundRunnable = null
        val sessionDurationMs = System.currentTimeMillis() - sessionStartMs
        InstagameAnalytics.trackSessionEnded(
            sessionDurationMs = sessionDurationMs,
            screensVisited = screensVisited,
            reelsWatched = reelsWatched,
            gamesPlayed = gamesPlayed
        )
    }

    fun getCurrentScreen(): String = currentScreen
    fun getSessionDurationMs(): Long = System.currentTimeMillis() - sessionStartMs
}
