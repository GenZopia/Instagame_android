package com.genzopia.Instagame.utils

import android.util.Log
import com.genzopia.Instagame.BuildConfig
import com.genzopia.Instagame.gateway.AppConfigResponse
import com.genzopia.Instagame.gateway.GatewayClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Fetches app config (force/smooth update version thresholds) from the Gateway.
 * Firebase Remote Config SDK is no longer used — the gateway reads it server-side.
 */
class RemoteConfigManager {

    companion object {
        private const val TAG = "RemoteConfigManager"
    }

    private var config: AppConfigResponse = AppConfigResponse()

    fun fetchConfig(onComplete: (Boolean) -> Unit) {
        GatewayClient.callApi.getAppConfig().enqueue(object : Callback<AppConfigResponse> {
            override fun onResponse(call: Call<AppConfigResponse>, response: Response<AppConfigResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    config = response.body()!!
                    Log.d(TAG, "✅ Config fetched — raw: force='${config.force_popup_minimum_version}' smooth='${config.smooth_popup_minimum_version}'")
                    Log.d(TAG, "   parsed: forceMin=${parseVersionCode(config.force_popup_minimum_version)} smoothMin=${parseVersionCode(config.smooth_popup_minimum_version)} appVersionCode=${BuildConfig.VERSION_CODE}")
                    Log.d(TAG, "   result: isForceRequired=${isForceUpdateRequired()} isSmoothAvailable=${isSmoothUpdateAvailable()}")
                    onComplete(true)
                } else {
                    Log.w(TAG, "❌ Config fetch failed HTTP ${response.code()} — using defaults (force='${config.force_popup_minimum_version}' smooth='${config.smooth_popup_minimum_version}')")
                    onComplete(false)
                }
            }

            override fun onFailure(call: Call<AppConfigResponse>, t: Throwable) {
                Log.e(TAG, "❌ Config fetch failed: ${t.message} — using defaults")
                onComplete(false)
            }
        })
    }

    fun isForceUpdateRequired(): Boolean {
        val min = parseVersionCode(config.force_popup_minimum_version)
        return min > 0 && BuildConfig.VERSION_CODE < min
    }

    fun isSmoothUpdateAvailable(): Boolean {
        val min = parseVersionCode(config.smooth_popup_minimum_version)
        return min > 0 && BuildConfig.VERSION_CODE < min
    }

    fun getForceMinVersionString(): String = config.force_popup_minimum_version
    fun getSmoothMinVersionString(): String = config.smooth_popup_minimum_version

    private fun parseVersionCode(value: String): Int {
        if (value.isBlank()) return 0
        return value.trim().substringBefore(".").toIntOrNull() ?: 0
    }
}
