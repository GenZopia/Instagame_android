package com.genzopia.Instagame.analytics

import java.util.UUID

/**
 * SessionTracker — tracks the lifecycle of a single app session.
 *
 * Tracks:
 *  - Session start time and unique session ID
 *  - Current screen name (for app_backgrounded "where did they leave")
 *  - Counts of screens visited, reels watched, games played
 *  - Background/foreground timestamps for time-away calculation
 *
 * Usage: singleton, call from BaseActivity or MyApplication lifecycle callbacks.
 */
object SessionTracker {

    private var sessionId: String = UUID.randomUUID().toString()
    private var sessionStartMs: Long = System.currentTimeMillis()
    private var backgroundedAtMs: Long = 0L
    private var currentScreen: String = "splash"

    private var screensVisited: Int = 0
    private var reelsWatched: Int = 0
    private var gamesPlayed: Int = 0

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

    fun onAppBackgrounded() {
        backgroundedAtMs = System.currentTimeMillis()
        val sessionDurationMs = backgroundedAtMs - sessionStartMs
        InstagameAnalytics.trackAppBackgrounded(currentScreen, sessionDurationMs)
    }

    fun onAppForegrounded() {
        if (backgroundedAtMs > 0L) {
            val timeAwayMs = System.currentTimeMillis() - backgroundedAtMs
            InstagameAnalytics.trackAppForegrounded(timeAwayMs)
            backgroundedAtMs = 0L
        }
    }

    fun onSessionEnded() {
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
