package com.genzopia.Instagame

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

class VideoHlsConverter {

    companion object {

        private const val TAG = "VideoHlsConverter"
        private const val BASE_URL = "https://video-processor-531675723135.asia-south1.run.app/"
        // Key is stored in gradle.properties (git-ignored) and injected via BuildConfig.
        private val API_KEY get() = com.genzopia.Instagame.BuildConfig.VIDEO_PROCESSOR_API_KEY
        private const val MAX_POLL_ATTEMPTS = 60 // 5 min max (60 * 5s)

        private val api: VideoApi by lazy {
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(VideoApi::class.java)
        }

        /** Java-callable blocking wrapper — call from a background thread only. */
        @JvmStatic
        fun triggerConversion(videoId: String): String? = runBlocking {
            VideoHlsConverter().convertToHls(videoId)
        }
    }

    interface VideoApi {

        @POST("api/videos/process")
        suspend fun processVideo(
            @Header("X-API-Key") apiKey: String,
            @Body body: ProcessRequest
        ): ProcessResponse

        @GET("api/videos/status/{taskId}")
        suspend fun getStatus(
            @Header("X-API-Key") apiKey: String,
            @Path("taskId") taskId: String
        ): StatusResponse
    }

    data class ProcessRequest(val r2ObjectKey: String)

    data class ProcessResponse(val id: String, val status: String, val message: String)

    data class StatusResponse(val status: String, val hlsManifestKey: String?)

    suspend fun convertToHls(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val r2ObjectKey = "video/$videoId.mp4"
            Log.d(TAG, "Starting HLS conversion for $videoId")

            val response = api.processVideo(API_KEY, ProcessRequest(r2ObjectKey))
            val taskId = response.id
            Log.d(TAG, "Task created: $taskId")

            repeat(MAX_POLL_ATTEMPTS) {
                val status = api.getStatus(API_KEY, taskId)
                Log.d(TAG, "Status = ${status.status}")

                when (status.status) {
                    "COMPLETED" -> {
                        Log.d(TAG, "HLS ready: ${status.hlsManifestKey}")
                        return@withContext status.hlsManifestKey
                    }
                    "FAILED" -> {
                        Log.e(TAG, "Conversion failed for $videoId")
                        return@withContext null
                    }
                }
                delay(5000)
            }

            Log.e(TAG, "Conversion timed out for $videoId")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error converting $videoId", e)
            null
        }
    }
}
