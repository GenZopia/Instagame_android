package com.genzopia.Instagame.utils

import android.util.Log
import com.genzopia.Instagame.BuildConfig
import org.json.JSONObject

object ProfilePhotoUtils {

    private const val WORKER_HOST = "file-upload-worker.genzopia.workers.dev"

    /**
     * Converts any stored profile_photo_url format into a loadable URL.
     *
     * Formats stored in Firebase:
     *   1. Bare R2 key:  "instagame/uid/file.jpg"             → gateway /profile/retrieve?profileId=...
     *   2. JSON blob:    {"success":true,"key":"instagame/..."} → gateway /media/file?key=...
     *   3. Worker URL:   "https://file-upload-worker.../?key=" → gateway /media/file?key=...
     *   4. Google photo: "https://lh3.googleusercontent.com/..." → pass through
     *   5. CDN URL:      "https://cdn.genzopia.com/..."        → pass through
     */
    @JvmStatic
    fun sanitize(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "-1") return null

        // 1. JSON blob
        if (raw.trimStart().startsWith("{")) {
            return try {
                val key = JSONObject(raw).optString("key", "")
                if (key.isNotEmpty()) toGatewayUrl(key) else null
            } catch (_: Exception) { null }
        }

        // 2. Worker URL — extract key and rewrite
        if (raw.contains(WORKER_HOST)) {
            val encoded = raw.substringAfter("?key=", "").substringBefore("&")
            val key = try {
                java.net.URLDecoder.decode(encoded, "UTF-8")
            } catch (_: Exception) {
                encoded
            }
            return if (key.isNotEmpty()) toGatewayUrl(key) else null
        }

        // 2b. Already a gateway media URL — pass through unchanged (avoids
        // double-sanitising a value the gateway already resolved).
        if (raw.contains("/media/file?key=") || raw.contains("/profile/retrieve")) {
            return raw
        }

        // 3. Bare R2 key (no scheme, no host) — convert to gateway media URL
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
            return toGatewayUrl(raw)
        }

        // 4 & 5. Already a full URL (Google photo, CDN, etc.) — pass through
        return raw
    }

    @JvmStatic
    fun toGatewayUrl(key: String): String {
        val base = BuildConfig.GATEWAY_BASE_URL.trimEnd('/')
        return "$base/media/file?key=${java.net.URLEncoder.encode(key, "UTF-8")}"
    }

    /**
     * Returns the gateway /profile/retrieve URL for a given userId.
     * This URL returns image bytes directly (proxied from worker or redirected from Google).
     * Safe to pass directly to Glide/Picasso.
     */
    @JvmStatic
    fun retrieveUrl(userId: String): String {
        val base = BuildConfig.GATEWAY_BASE_URL.trimEnd('/')
        return "$base/profile/retrieve?profileId=${java.net.URLEncoder.encode(userId, "UTF-8")}"
    }
}
