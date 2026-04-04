package com.genzopia.Instagame.utils

import org.json.JSONObject

/**
 * Sanitizes a profile_photo_url that may have been stored as a raw worker JSON response
 * e.g. {"success":true,"key":"instagame/uid/file.jpg"} → proper access URL
 */
object ProfilePhotoUtils {

    private const val WORKER_BASE = "https://file-upload-worker.genzopia.workers.dev/?key="

    @JvmStatic
    fun sanitize(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "-1") return null
        // If it looks like a JSON object, try to extract the key
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
