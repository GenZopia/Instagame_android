package com.genzopia.Instagame.utils

import android.util.Log
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.net.URLEncoder

/**
 * Sanitizes a profile_photo_url that may have been stored as a raw worker JSON response
 * e.g. {"success":true,"key":"instagame/uid/file.jpg"} → proper access URL via file-upload-worker
 */
object ProfilePhotoUtils {

    private const val WORKER_BASE = "https://file-upload-worker.genzopia.workers.dev/?key="

    @JvmStatic
    fun sanitize(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "-1") return null
        if (raw.trimStart().startsWith("{")) {
            return try {
                val obj = JSONObject(raw)
                val key = obj.optString("key", "")
                if (key.isNotEmpty()) "$WORKER_BASE$key" else null
            } catch (_: Exception) {
                null
            }
        }
        return raw
    }
}

/**
 * Resolves game/photo thumbnail URLs using the video-signer worker,
 * exactly as the web version does in getSignedPhotoUrl().
 *
 * Flow:
 *  1. Read photo_id from game node
 *  2. Read file_ext from /photos/{photo_id} in Firebase
 *  3. Call https://video-signer.genzopia.workers.dev/?path=photo/{photo_id}.{ext}
 *  4. Parse {"success":true,"url":"..."} → use the signed URL directly (no auth header needed)
 */
object PhotoUrlResolver {

    private const val TAG = "profile_photo"
    private const val SIGNER_BASE = "https://video-signer.genzopia.workers.dev/?path="

    /**
     * Given a photo_id and file_ext, returns a fresh signed URL.
     * Must be called off the main thread.
     */
    @JvmStatic
    fun resolveSync(photoId: String, fileExt: String): String? {
        val path = "photo/$photoId.$fileExt"
        val workerUrl = "$SIGNER_BASE${URLEncoder.encode(path, "UTF-8")}"
        Log.d(TAG, "PhotoUrlResolver → $workerUrl")
        return try {
            val text = URL(workerUrl).readText()
            val json = JSONObject(text)
            if (json.optBoolean("success") && json.has("url")) {
                val url = json.getString("url")
                Log.d(TAG, "PhotoUrlResolver ← signed url ok for $photoId")
                url
            } else {
                Log.e(TAG, "PhotoUrlResolver ← failure for $photoId: $text")
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "PhotoUrlResolver ← network error for $photoId", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "PhotoUrlResolver ← error for $photoId", e)
            null
        }
    }
}
