package com.genzopia.Instagame.utils

import android.util.Log
import com.genzopia.Instagame.BuildConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

/**
 * Manages Firebase Remote Config for force and smooth update version checking.
 *
 * Remote Config keys (both are plain integer versionCode strings, e.g. "6"):
 *   force_popup_minimum_version  — if current versionCode < this, user MUST update (non-dismissible)
 *   smooth_popup_minimum_version — if current versionCode < this, user is nudged to update (dismissible)
 *
 * Set both to "0" (or leave unset) to disable the respective popup.
 */
class RemoteConfigManager {

    companion object {
        private const val TAG = "RemoteConfigManager"
        const val KEY_FORCE_MIN_VERSION  = "force_popup_minimum_version"
        const val KEY_SMOOTH_MIN_VERSION = "smooth_popup_minimum_version"
        private const val CACHE_EXPIRATION_SECONDS = 3600L

        private val DEFAULTS = mapOf(
            KEY_FORCE_MIN_VERSION  to "0",
            KEY_SMOOTH_MIN_VERSION to "0"
        )
    }

    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    init {
        remoteConfig.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(CACHE_EXPIRATION_SECONDS)
                .build()
        )
        remoteConfig.setDefaultsAsync(DEFAULTS)
    }

    /** Fetches and activates latest config. Falls back to cached/defaults on failure. */
    fun fetchConfig(onComplete: (Boolean) -> Unit) {
        remoteConfig.fetchAndActivate()
            .addOnSuccessListener { updated ->
                Log.d(TAG, "Remote Config fetched (updated=$updated)")
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Remote Config fetch failed — using cached/default: ${e.message}")
                onComplete(false)
            }
    }

    /** Returns true if a force update is required (user cannot skip). */
    fun isForceUpdateRequired(): Boolean {
        val minVersion = parseVersionCode(remoteConfig.getString(KEY_FORCE_MIN_VERSION))
        return minVersion > 0 && BuildConfig.VERSION_CODE < minVersion
    }

    /** Returns true if a smooth (optional) update nudge should be shown. */
    fun isSmoothUpdateAvailable(): Boolean {
        val minVersion = parseVersionCode(remoteConfig.getString(KEY_SMOOTH_MIN_VERSION))
        return minVersion > 0 && BuildConfig.VERSION_CODE < minVersion
    }

    /** Raw version string for display in dialogs. */
    fun getForceMinVersionString(): String = remoteConfig.getString(KEY_FORCE_MIN_VERSION)
    fun getSmoothMinVersionString(): String = remoteConfig.getString(KEY_SMOOTH_MIN_VERSION)

    private fun parseVersionCode(value: String): Int {
        if (value.isBlank()) return 0
        return value.trim().substringBefore(".").toIntOrNull() ?: run {
            Log.w(TAG, "Invalid version value '$value' — treating as no update required")
            0
        }
    }
}
