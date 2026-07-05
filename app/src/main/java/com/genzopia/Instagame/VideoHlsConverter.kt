package com.genzopia.Instagame

import android.util.Log
import com.genzopia.Instagame.gateway.GatewayClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * VideoHlsConverter — triggers and polls HLS conversion via the Gateway.
 *
 * The GCP Cloud Run URL and API key never reach the client; all calls are
 * proxied through POST /videos/{videoId}/hls-convert and
 * GET /videos/{videoId}/hls-status/{taskId}.
 */
class VideoHlsConverter {

    companion object {
        private const val TAG = "VideoHlsConverter"
        private const val MAX_POLL_ATTEMPTS = 60 // 5 min max (60 × 5 s)

        /** Java-callable blocking wrapper — call from a background thread only. */
        @JvmStatic
        fun triggerConversion(videoId: String): String? = runBlocking {
            VideoHlsConverter().convertToHls(videoId)
        }
    }

    suspend fun convertToHls(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Requesting HLS conversion for $videoId via gateway")

            val convertResp = GatewayClient.api.triggerHlsConversion(videoId)
            if (!convertResp.isSuccessful || convertResp.body() == null) {
                Log.e(TAG, "triggerHlsConversion failed: HTTP ${convertResp.code()}")
                return@withContext null
            }

            val taskId = convertResp.body()!!.taskId
            Log.d(TAG, "HLS task created: $taskId")

            repeat(MAX_POLL_ATTEMPTS) {
                val statusResp = GatewayClient.api.getHlsStatus(videoId, taskId)
                if (!statusResp.isSuccessful || statusResp.body() == null) {
                    Log.e(TAG, "getHlsStatus failed: HTTP ${statusResp.code()}")
                    return@withContext null
                }

                val body = statusResp.body()!!
                Log.d(TAG, "HLS status = ${body.status}")

                when (body.status) {
                    "COMPLETED" -> {
                        Log.d(TAG, "HLS ready: ${body.hlsManifestKey}")
                        return@withContext body.hlsManifestKey
                    }
                    "FAILED" -> {
                        Log.e(TAG, "HLS conversion failed for $videoId")
                        return@withContext null
                    }
                }
                delay(5_000)
            }

            Log.e(TAG, "HLS conversion timed out for $videoId")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error during HLS conversion for $videoId", e)
            null
        }
    }
}
