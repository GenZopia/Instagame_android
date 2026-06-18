package com.genzopia.Instagame.utils

import android.util.Log
import com.genzopia.Instagame.BuildConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

/**
 * Manages Firebase Remote Config for force-update version checking.
 * Requirements: 2.1, 2.2, 2.5, 5.1, 5.2, 5.3, 5.4, 5.5, 7.3
 */
class RemoteConfigManager {

    companion object {
        private const val TAG = "RemoteConfigManager"
        private const val KEY_MIN_ANDROID_VERSION = "min_android_version"
        private const val KEY_FORCE_UPDATE_ENABLED = "force_update_enabled"
        private const val CACHE_EXPIRATION_SECONDS = 3600L // 1 hour

        /** Default minimum version matches current build — no forced update by default. */
        private val DEFAULTS = mapOf(
            KEY_MIN_ANDROID_VERSION to "${BuildConfig.VERSION_CODE}.0",
            KEY_FORCE_UPDATE_ENABLED to false
        )
    }

    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    init {
        val settings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(CACHE_EXPIRATION_SECONDS)
            .build()
        remoteConfig.setConfigSettingsAsync(settings)
        remoteConfig.setDefaultsAsync(DEFAULTS)
    }

    /**
     * Fetches the latest config from Firebase and activates it.
     * On failure, cached/default values are used so the app continues normally.
     * Req 2.1, 2.5, 5.4, 7.3
     */
    fun fetchConfig(onComplete: (Boolean) -> Unit) {
        remoteConfig.fetchAndActivate()
            .addOnSuccessListener { updated ->
                Log.d(TAG, "Remote Config fetched (updated=$updated)")
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Remote Config fetch failed — using cached/default values: ${e.message}", e)
                // Req 2.5: allow app to continue on failure
                onComplete(false)
            }
    }

    /**
     * Returns true if a force update is required.
     * Returns false if the force_update_enabled flag is off or if version is sufficient.
     * Req 2.2, 5.3
     */
    fun isUpdateRequired(): Boolean {
        if (!isForceUpdateEnabled()) return false
        return BuildConfig.VERSION_CODE < getMinVersionCode()
    }

    /**
     * Checks whether the force update feature flag is enabled.
     * Req 5.3
     */
    fun isForceUpdateEnabled(): Boolean =
        remoteConfig.getBoolean(KEY_FORCE_UPDATE_ENABLED)

    /**
     * Returns the minimum required version code parsed from the Remote Config string.
     * On invalid/missing value returns 0 (no update required). Req 5.5
     */
    fun getMinVersionCode(): Int =
        parseVersionString(remoteConfig.getString(KEY_MIN_ANDROID_VERSION))

    /**
     * Parses a version string like "5.0", "10.2.1" into a comparable integer.
     * Strategy: major * 1_000_000 + minor * 1_000 + patch
     * "5.0"     → 5_000_000
     * "10.2.1"  → 10_002_001
     * Returns 0 on parse failure so missing/invalid config never forces an update. Req 5.5
     */
    fun parseVersionString(version: String): Int {
        if (version.isBlank()) return 0
        return try {
            val parts = version.trim().split(".").map { it.toInt() }
            val major = parts.getOrElse(0) { 0 }
            val minor = parts.getOrElse(1) { 0 }
            val patch = parts.getOrElse(2) { 0 }
            major * 1_000_000 + minor * 1_000 + patch
        } catch (e: NumberFormatException) {
            Log.w(TAG, "Invalid version string '$version' — treating as no update required")
            0
        }
    }
}
