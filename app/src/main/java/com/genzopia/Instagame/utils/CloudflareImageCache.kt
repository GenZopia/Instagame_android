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
 * Handles caching of profile images from Cloudflare Worker API
 * Fetches image on first load, stores in SharedPreferences and local cache
 * On subsequent loads, uses cached data
 */
object CloudflareImageCache {

    private const val TAG = "CloudflareImageCache"
    private const val PREFS_NAME = "CloudflareImageCache"
    private const val KEY_PROFILE_IMAGE_URL = "cached_profile_image_url"
    private const val KEY_PROFILE_IMAGE_PATH = "cached_profile_image_path"
    
    interface ImageCacheCallback {
        fun onSuccess(localFilePath: String)
        fun onFailure(message: String)
    }

    /**
     * Fetch profile image with caching logic
     * 1. Check SharedPreferences for cached URL and local file
     * 2. If cache exists and file is valid, return immediately
     * 3. If cache is empty, fetch from Cloudflare API with x-api-key header
     * 4. Store in cache for future use
     */
    fun fetchProfileImage(
        context: Context,
        userId: String,
        remoteKey: String?,
        callback: ImageCacheCallback
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Check cache first
        val cachedPath = prefs.getString(KEY_PROFILE_IMAGE_PATH, null)
        if (cachedPath != null) {
            val cachedFile = File(cachedPath)
            if (cachedFile.exists() && cachedFile.length() > 0) {
                Log.d(TAG, "Using cached profile image: $cachedPath")
                callback.onSuccess(cachedPath)
                return
            }
        }

        // Cache is empty or invalid, fetch from API
        if (remoteKey.isNullOrEmpty()) {
            callback.onFailure("No remote image key provided")
            return
        }

        Log.d(TAG, "Fetching profile image from Cloudflare API: $remoteKey")
        downloadImageFromCloudflare(context, remoteKey, prefs, callback)
    }

    private fun downloadImageFromCloudflare(
        context: Context,
        remoteKey: String,
        prefs: SharedPreferences,
        callback: ImageCacheCallback
    ) {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url("https://file-upload-worker.genzopia.workers.dev/?key=$remoteKey")
            .addHeader("x-api-key", BuildConfig.FILE_UPLOAD_API_KEY)
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
                        Log.e(TAG, "Download failed with code: ${it.code}")
                        callback.onFailure("Failed to download: ${it.code}")
                        return
                    }

                    val imageBytes = it.body?.bytes()
                    if (imageBytes == null || imageBytes.isEmpty()) {
                        callback.onFailure("Downloaded image is empty")
                        return
                    }

                    // Save to local file
                    val imageDir = File(context.cacheDir, "profile_images")
                    if (!imageDir.exists()) imageDir.mkdirs()
                    
                    val ext = remoteKey.substringAfterLast('.', "jpg")
                    val localFile = File(imageDir, "profile_${System.currentTimeMillis()}.$ext")
                    
                    try {
                        FileOutputStream(localFile).use { fos ->
                            fos.write(imageBytes)
                        }
                        
                        // Save to cache
                        val downloadUrl = "https://file-upload-worker.genzopia.workers.dev/?key=$remoteKey"
                        prefs.edit()
                            .putString(KEY_PROFILE_IMAGE_URL, downloadUrl)
                            .putString(KEY_PROFILE_IMAGE_PATH, localFile.absolutePath)
                            .apply()
                        
                        Log.d(TAG, "Profile image cached successfully: ${localFile.absolutePath}")
                        callback.onSuccess(localFile.absolutePath)
                        
                    } catch (e: IOException) {
                        Log.e(TAG, "Failed to save image to cache: ${e.message}")
                        callback.onFailure("Failed to cache image: ${e.message}")
                    }
                }
            }
        })
    }

    /**
     * Clear cached profile image
     * Call this when user logs out or updates profile picture
     */
    fun clearCache(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedPath = prefs.getString(KEY_PROFILE_IMAGE_PATH, null)
        
        if (cachedPath != null) {
            val file = File(cachedPath)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Deleted cached profile image")
            }
        }
        
        prefs.edit().clear().apply()
        Log.d(TAG, "Cache cleared")
    }

    /**
     * Get cached image URL without downloading
     * Returns null if no cache exists
     */
    fun getCachedImagePath(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedPath = prefs.getString(KEY_PROFILE_IMAGE_PATH, null)
        
        if (cachedPath != null) {
            val file = File(cachedPath)
            if (file.exists() && file.length() > 0) {
                return cachedPath
            }
        }
        return null
    }
}
