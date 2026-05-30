package com.genzopia.Instagame.utils

import android.util.Log
import org.json.JSONObject

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
    private const val R2_BASE = "https://pub-22db73b8d33244d1a53831aed22cd78b.r2.dev"

    /**
     * Given a photo_id and file_ext, returns the direct R2 URL.
     */
    @JvmStatic
    fun resolveSync(photoId: String, fileExt: String): String {
        val url = "$R2_BASE/photo/$photoId.$fileExt"
        Log.d(TAG, "PhotoUrlResolver → $url")
        return url
    }
}
