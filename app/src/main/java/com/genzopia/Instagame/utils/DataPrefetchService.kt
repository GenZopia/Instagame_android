package com.genzopia.Instagame.utils

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.genzopia.Instagame.features.home.domain.FollowedUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@UnstableApi
object DataPrefetchService {

    private const val TAG = "DataPrefetchService"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient()
    private val database = FirebaseDatabase.getInstance()

    private val videoCache = mutableMapOf<String, VideoMetadata>()
    private val signedUrlCache = mutableMapOf<String, String>()
    private var followedUsersCache: List<FollowedUser>? = null

    // Pool of pre-created ExoPlayers (all prefetched videos)
    private val playerPool = mutableMapOf<String, ExoPlayer>()
    private var preloadedPlayer: ExoPlayer? = null
    private var preloadedVideoId: String? = null

    @Volatile
    private var firstVideoReady = false

    data class VideoMetadata(
        val videoId: String,
        val title: String,
        val userId: String,
        val gameId: String,
        val signedUrl: String? = null
    )

    fun isFirstVideoReady(): Boolean = firstVideoReady

    fun getPreloadedPlayer(videoId: String): ExoPlayer? {
        playerPool[videoId]?.let { return it }
        return if (videoId == preloadedVideoId) preloadedPlayer else null
    }

    fun getPreloadedVideoId(): String? = preloadedVideoId

    fun clearPreloadedPlayer() {
        preloadedPlayer = null
        preloadedVideoId = null
    }

    fun removeFromPool(videoId: String) {
        playerPool.remove(videoId)
    }

    // ── Public entry point ────────────────────────────────────────────────────

    fun startPrefetch(context: Context, onComplete: (() -> Unit)? = null) {
        Log.d(TAG, "startPrefetch: firing background jobs")
        scope.launch {
            try { prefetchFollowedUsers() } catch (e: Exception) { Log.e(TAG, "followedUsers failed", e) }
            try { prefetchVideos(context, 10) } catch (e: Exception) { Log.e(TAG, "videos failed", e) }
            Log.d(TAG, "Background prefetch done")
        }
        // Return immediately — navigation is driven by splash animation, not data
        scope.launch(Dispatchers.Main) { onComplete?.invoke() }
    }

    // ── Video prefetch ────────────────────────────────────────────────────────

    private suspend fun prefetchVideos(context: Context, count: Int) {
        Log.d(TAG, "Querying Firebase for $count videos")
        val snapshot = database.reference.child("videos").orderByKey().limitToFirst(count).get().await()
        Log.d(TAG, "Got ${snapshot.childrenCount} videos from Firebase")

        val entries = snapshot.children.mapIndexedNotNull { index, snap ->
            val videoId = snap.key ?: return@mapIndexedNotNull null
            val title   = snap.child("video_title").getValue(String::class.java) ?: ""
            val userId  = snap.child("user_id").getValue(String::class.java) ?: ""
            val gameId  = snap.child("game_id").getValue(String::class.java) ?: ""
            Triple(index, videoId, VideoMetadata(videoId, title, userId, gameId))
        }

        // Resolve all URLs in parallel
        kotlinx.coroutines.coroutineScope {
            entries.map { (index, videoId, meta) ->
                launch {
                    try {
                        val url = fetchSignedUrl(videoId)
                        videoCache[videoId] = meta.copy(signedUrl = url)
                        Log.d(TAG, "[$index] URL ready: $videoId")
                    } catch (e: Exception) {
                        Log.e(TAG, "[$index] URL failed: $videoId", e)
                    }
                }
            }.forEach { it.join() }
        }

        // Create ExoPlayers on main thread
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            val appCtx = context.applicationContext
            entries.forEach { (index, videoId, _) ->
                val url = signedUrlCache[videoId] ?: return@forEach
                try {
                    val player = buildPlayer(appCtx, url)
                    playerPool[videoId] = player
                    if (index == 0) { preloadedVideoId = videoId; preloadedPlayer = player }
                    Log.d(TAG, "[$index] Player ready: $videoId")
                } catch (e: Exception) {
                    Log.e(TAG, "[$index] Player failed: $videoId", e)
                }
            }
            kotlinx.coroutines.delay(800)
            firstVideoReady = true
            Log.d(TAG, "All ${playerPool.size} players buffering")
        }
    }

    private fun buildPlayer(context: Context, url: String): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(5000, 30000, 500, 1000)
            .build()
        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                repeatMode = ExoPlayer.REPEAT_MODE_ONE
                volume = 0f
                prepare()
            }
    }

    // ── URL resolution (HLS-first, 3 retries) ────────────────────────────────

    private suspend fun fetchSignedUrl(videoId: String): String? {
        signedUrlCache[videoId]?.let { return it }

        val R2 = "https://pub-0caba249d019456b9181ce1575ef825e.r2.dev"
        val base = resolveBasePath(videoId)
        val hlsDir = "${base}_hls"

        repeat(3) { attempt ->
            try {
                checkHlsManifest(R2, hlsDir)?.let { name ->
                    val url = "$R2/$hlsDir/$name"
                    signedUrlCache[videoId] = url
                    Log.d(TAG, "[$videoId] HLS: $url")
                    return url
                }
                val resp = httpClient.newCall(Request.Builder()
                    .url("https://video-signer.genzopia.workers.dev/?path=video/$videoId")
                    .build()).execute()
                val body = resp.body?.string()
                resp.close()
                val json = body?.let { JSONObject(it) }
                if (json?.optBoolean("success") == true) {
                    val url = json.optString("url").takeIf { it.isNotEmpty() }
                    if (url != null) {
                        signedUrlCache[videoId] = url
                        Log.d(TAG, "[$videoId] MP4: $url")
                        return url
                    }
                }
                Log.w(TAG, "[$videoId] attempt ${attempt + 1} failed")
            } catch (e: Exception) {
                Log.e(TAG, "[$videoId] attempt ${attempt + 1} error: ${e.message}")
            }
            if (attempt < 2) kotlinx.coroutines.delay(3000)
        }
        Log.e(TAG, "[$videoId] all attempts failed")
        return null
    }

    private fun resolveBasePath(id: String): String {
        var v = id
        if (v.startsWith("video/")) return v.replace(Regex("\\.[^.]+$"), "")
        v = v.removeSuffix(".mp4").removeSuffix(".m3u8")
        return if (v.startsWith("video_")) "video/$v" else "video/video_$v"
    }

    private fun checkHlsManifest(r2Base: String, hlsDir: String): String? {
        for (name in listOf("master.m3u8", "1080p.m3u8", "playlist.m3u8", "index.m3u8")) {
            try {
                val resp = httpClient.newCall(Request.Builder().url("$r2Base/$hlsDir/$name").head().build()).execute()
                resp.close()
                if (resp.isSuccessful) return name
            } catch (_: Exception) {}
        }
        return null
    }

    // ── Followed users prefetch ───────────────────────────────────────────────

    private suspend fun prefetchFollowedUsers() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        try {
            val snap = database.reference.child("users").child(uid).child("following_list").get().await()
            val ids = snap.children.mapNotNull { it.key }
            if (ids.isEmpty()) { followedUsersCache = emptyList(); return }

            val users = mutableListOf<FollowedUser>()
            for (userId in ids) {
                try {
                    val u = database.reference.child("users").child(userId).get().await()
                    val name = u.child("full_name").getValue(String::class.java)
                        ?: u.child("username").getValue(String::class.java) ?: "User"
                    val photo = ProfilePhotoUtils.sanitize(
                        u.child("profile_photo_url").getValue(String::class.java)
                            ?: u.child("profile_image_url").getValue(String::class.java)
                    )
                    users.add(FollowedUser(userId, name, photo))
                } catch (e: Exception) { Log.e(TAG, "user $userId failed", e) }
            }
            followedUsersCache = users
            Log.d(TAG, "Prefetched ${users.size} followed users")
        } catch (e: Exception) {
            Log.e(TAG, "prefetchFollowedUsers error", e)
        }
    }

    // ── Public cache accessors ────────────────────────────────────────────────

    fun getCachedVideo(videoId: String): VideoMetadata? = videoCache[videoId]
    fun getAllCachedVideos(): Map<String, VideoMetadata> = videoCache.toMap()
    fun getCachedSignedUrl(videoId: String): String? = signedUrlCache[videoId]
    fun isVideoCached(videoId: String): Boolean = videoCache.containsKey(videoId)
    fun getCachedFollowedUsers(): List<FollowedUser>? = followedUsersCache

    fun clearCache() {
        videoCache.clear()
        signedUrlCache.clear()
        followedUsersCache = null
        firstVideoReady = false
        playerPool.values.forEach { it.release() }
        playerPool.clear()
        preloadedPlayer = null
        preloadedVideoId = null
        Log.d(TAG, "Cache cleared")
    }
}
