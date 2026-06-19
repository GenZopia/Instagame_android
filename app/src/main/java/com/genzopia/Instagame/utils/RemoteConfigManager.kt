package com.genzopia.Instagame.utils

import android.util.Log
import com.genzopia.Instagame.BuildConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

/**
 * Manages Firebase Remote Config for force-update version checking.
 * Requirements: 2.1, 2.2, 2.5, 5.1, 5.2, 5.3, 5.4, 5.5, 7.3
 *
 * Version gating is based on versionCode (a plain monotonically increasing
 * integer set in build.gradle), NOT versionName. The Remote Config value
 * for KEY_MIN_ANDROID_VERSION must be a plain integer string (e.g. "6"),
 * matching BuildConfig.VERSION_CODE — not a dotted "x.y" string.
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
            .setMinimumFetchIntervalInSeconds(CACHE_EXPIRATION_SECONDS) // 3600 = 1 hour
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
     * Returns the minimum required versionCode parsed from the Remote Config string.
     * Expects a plain integer string (e.g. "6"), matching BuildConfig.VERSION_CODE.
     * On invalid/missing value returns 0 (no update required). Req 5.5
     */
    fun getMinVersionCode(): Int =
        parseVersionCode(remoteConfig.getString(KEY_MIN_ANDROID_VERSION))

    /**
     * Returns the raw, unparsed min-version string from Remote Config — for display
     * purposes only (e.g. in the force-update dialog message). Do NOT use this for
     * comparisons; use getMinVersionCode() instead.
     */
    fun getMinVersionString(): String =
        remoteConfig.getString(KEY_MIN_ANDROID_VERSION)

    /**
     * Parses a plain integer versionCode string, e.g. "6" -> 6.
     * Returns 0 on parse failure (missing/invalid config never forces an update). Req 5.5
     */
    private fun parseVersionCode(value: String): Int {
        if (value.isBlank()) return 0
        val intPart = value.trim().substringBefore(".")
        return intPart.toIntOrNull() ?: run {
            Log.w(TAG, "Invalid min_android_version '$value' — could not parse versionCode, treating as no update required")
            0
        }
    }
}