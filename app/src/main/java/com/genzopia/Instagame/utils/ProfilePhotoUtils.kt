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
 *     with x-api-key header
 *  4. Parse {"success":true,"url":"..."} → return the signed URL
 */
object PhotoUrlResolver {

    private const val TAG = "PhotoUrlResolver"
    private const val R2_BASE = "https://cdn.genzopia.com"

    /**
     * Builds the direct URL for a game/photo thumbnail from file-upload-worker.
     * Both workers (file-upload-worker and the link-bucket worker) share the same
     * R2 bucket, so photos stored at photo/{photo_id}.{ext} are accessible via:
     *   https://file-upload-worker.genzopia.workers.dev/?key=photo/{photo_id}.{ext}
     *
     * The x-api-key header is injected automatically by InstagameGlideModule and
     * MyApplication's Coil interceptor — no manual auth needed here.
     *
     * This replaces the old video-signer approach which required a network round-trip
     * just to get a signed URL. The file-upload-worker serves the file directly.
     *
     * Can be called from any thread — no network I/O.
     */
    @JvmStatic
    fun resolveSync(photoId: String, fileExt: String): String {
        val url = "$R2_BASE/photo/$photoId.$fileExt"
        Log.d(TAG, "PhotoUrlResolver → $url")
        return url
    }
}
