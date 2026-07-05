package com.genzopia.Instagame.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.genzopia.Instagame.BuildConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Handles local caching of profile images.
 * Images are fetched via the Gateway media proxy (/media/file?key=...)
 * so the Cloudflare worker API key never reaches the client.
 */
object CloudflareImageCache {

    private const val TAG = "CloudflareImageCache"
    private const val PREFS_NAME = "CloudflareImageCache"
    private const val KEY_PROFILE_IMAGE_URL  = "cached_profile_image_url"
    private const val KEY_PROFILE_IMAGE_PATH = "cached_profile_image_path"

    interface ImageCacheCallback {
        fun onSuccess(localFilePath: String)
        fun onFailure(message: String)
    }

    fun fetchProfileImage(
        context: Context,
        userId: String,
        remoteKey: String?,
        callback: ImageCacheCallback
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Return cached file if still valid
        val cachedPath = prefs.getString(KEY_PROFILE_IMAGE_PATH, null)
        if (cachedPath != null) {
            val file = File(cachedPath)
            if (file.exists() && file.length() > 0) {
                Log.d(TAG, "Using cached profile image: $cachedPath")
                callback.onSuccess(cachedPath)
                return
            }
        }

        if (remoteKey.isNullOrEmpty()) {
            callback.onFailure("No remote image key provided")
            return
        }

        Log.d(TAG, "Fetching profile image via gateway for key: $remoteKey")
        downloadViaGateway(context, remoteKey, prefs, callback)
    }

    private fun downloadViaGateway(
        context: Context,
        remoteKey: String,
        prefs: SharedPreferences,
        callback: ImageCacheCallback
    ) {
        val gatewayBase = BuildConfig.GATEWAY_BASE_URL.trimEnd('/')
        val gatewayUrl  = "$gatewayBase/media/file?key=${android.net.Uri.encode(remoteKey)}"

        val client = OkHttpClient.Builder()
            .addInterceptor(com.genzopia.Instagame.gateway.GatewayAuthInterceptor())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(gatewayUrl)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to download image: ${e.message}")
                callback.onFailure(e.message ?: "Network error")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        callback.onFailure("Failed to download: ${it.code}")
                        return
                    }

                    val imageBytes = it.body?.bytes()
                    if (imageBytes == null || imageBytes.isEmpty()) {
                        callback.onFailure("Downloaded image is empty")
                        return
                    }

                    val imageDir = File(context.cacheDir, "profile_images").also { d -> d.mkdirs() }
                    val ext = remoteKey.substringAfterLast('.', "jpg")
                    val localFile = File(imageDir, "profile_${System.currentTimeMillis()}.$ext")

                    try {
                        FileOutputStream(localFile).use { fos -> fos.write(imageBytes) }

                        prefs.edit()
                            .putString(KEY_PROFILE_IMAGE_URL, gatewayUrl)
                            .putString(KEY_PROFILE_IMAGE_PATH, localFile.absolutePath)
                            .apply()

                        Log.d(TAG, "Profile image cached: ${localFile.absolutePath}")
                        callback.onSuccess(localFile.absolutePath)
                    } catch (e: IOException) {
                        callback.onFailure("Failed to cache image: ${e.message}")
                    }
                }
            }
        })
    }

    fun clearCache(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_PROFILE_IMAGE_PATH, null)?.let {
            val file = File(it)
            if (file.exists()) file.delete()
        }
        prefs.edit().clear().apply()
        Log.d(TAG, "Cache cleared")
    }

    fun getCachedImagePath(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val path = prefs.getString(KEY_PROFILE_IMAGE_PATH, null) ?: return null
        val file = File(path)
        return if (file.exists() && file.length() > 0) path else null
    }
}
