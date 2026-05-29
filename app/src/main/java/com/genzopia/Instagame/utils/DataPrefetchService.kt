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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

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
        val signedUrl: String? = null,
        val developerName: String = "",
        val developerPhotoUrl: String? = null
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

    /**
     * Starts prefetching data in the background.
     *
     * [onComplete] is called on the main thread only after video metadata (titles, names)
     * has been fetched from Firebase — so the reel list is always populated before
     * navigation. URL resolution continues in the background after navigation.
     *
     * On slow connections the metadata fetch itself may be slow, but the splash
     * hard-timeout in SplashActivity still guarantees the user is never stuck forever.
     */
    fun startPrefetch(context: Context, onComplete: (() -> Unit)? = null) {
        Log.d(TAG, "startPrefetch: firing background jobs")
        scope.launch {
            // Run both in parallel; followedUsers is best-effort
            val followJob = launch {
                try { prefetchFollowedUsers() } catch (e: Exception) { Log.e(TAG, "followedUsers failed", e) }
            }
            // prefetchVideos now calls onComplete itself once metadata is ready
            try {
                prefetchVideos(context, 30, onComplete)
            } catch (e: Exception) {
                Log.e(TAG, "videos failed", e)
                // Still notify so the splash doesn't hang if prefetch crashes
                withContext(Dispatchers.Main) { onComplete?.invoke() }
            }
            followJob.join()
            Log.d(TAG, "Background prefetch done")
        }
    }

    // ── Video prefetch ────────────────────────────────────────────────────────

    /**
     * Phase 1 – fetch video metadata + developer info from Firebase in parallel.
     * Phase 2 – resolve signed URLs in parallel (network, can be slow).
     * Phase 3 – create ExoPlayers on the main thread (CPU only).
     *
     * [onMetadataReady] is called after Phase 1 so the splash can navigate as soon
     * as reel titles/names/photos are available, even if URLs are still resolving.
     */
    private suspend fun prefetchVideos(context: Context, count: Int, onMetadataReady: (() -> Unit)? = null) {
        Log.d(TAG, "Querying Firebase for $count videos (will filter to verified only)")
        val snapshot = database.reference.child("videos").orderByKey().limitToFirst(count).get().await()
        Log.d(TAG, "Got ${snapshot.childrenCount} videos from Firebase")

        // Collect raw video data first (no network calls yet)
        data class RawVideo(val index: Int, val videoId: String, val title: String, val userId: String, val gameId: String)
        val rawVideos = snapshot.children.mapIndexedNotNull { index, snap ->
            val videoId = snap.key ?: return@mapIndexedNotNull null
            // Only prefetch verified videos
            val isVerifiedRaw = snap.child("is_verified").value
            val isVerified = when (isVerifiedRaw) {
                is Boolean -> isVerifiedRaw
                is String  -> isVerifiedRaw.equals("true", ignoreCase = true)
                is Long    -> isVerifiedRaw == 1L
                is Int     -> isVerifiedRaw == 1
                else       -> false
            }
            if (!isVerified) {
                Log.d(TAG, "Prefetch: skipping unverified video $videoId")
                return@mapIndexedNotNull null
            }
            val title  = snap.child("video_title").getValue(String::class.java) ?: ""
            val userId = snap.child("user_id").getValue(String::class.java) ?: ""
            val gameId = snap.child("game_id").getValue(String::class.java) ?: ""
            RawVideo(index, videoId, title, userId, gameId)
        }

        // Phase 1: fetch developer info for all videos in parallel
        val entries: List<Triple<Int, String, VideoMetadata>> = coroutineScope {
            rawVideos.map { raw ->
                async {
                    val (devName, devPhoto) = if (raw.userId.isNotEmpty()) {
                        try {
                            val userSnap = database.reference.child("users").child(raw.userId).get().await()
                            val name = userSnap.child("full_name").getValue(String::class.java)
                                ?: userSnap.child("name").getValue(String::class.java)
                                ?: userSnap.child("username").getValue(String::class.java)
                                ?: "User"
                            val rawPhoto = userSnap.child("profile_photo_url").getValue(String::class.java)
                                ?: userSnap.child("profile_image_url").getValue(String::class.java)
                                ?: userSnap.child("photoUrl").getValue(String::class.java)
                            val photo = ProfilePhotoUtils.sanitize(rawPhoto)
                            Pair(name, photo)
                        } catch (e: Exception) {
                            Log.e(TAG, "fetchDeveloperInfo failed for ${raw.userId}", e)
                            Pair("User", null as String?)
                        }
                    } else {
                        Pair("User", null as String?)
                    }

                    val meta = VideoMetadata(
                        videoId = raw.videoId,
                        title = raw.title,
                        userId = raw.userId,
                        gameId = raw.gameId,
                        developerName = devName,
                        developerPhotoUrl = devPhoto
                    )
                    videoCache[raw.videoId] = meta
                    Triple(raw.index, raw.videoId, meta)
                }
            }.map { it.await() }
        }

        Log.d(TAG, "Phase 1 complete — ${entries.size} reels with developer info cached")

        // ── Phase 1 done: metadata is ready → unblock the splash screen ──────
        withContext(Dispatchers.Main) { onMetadataReady?.invoke() }

        // ── Phase 2: resolve URLs in parallel (continues after navigation) ───
        coroutineScope {
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

        // ── Phase 3: create ExoPlayers on main thread ─────────────────────────
        withContext(Dispatchers.Main) {
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
            firstVideoReady = true
            Log.d(TAG, "All ${playerPool.size} players buffering")
        }
    }

    private fun buildPlayer(context: Context, url: String): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2000, 30000, 100, 500)
            .build()
        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                repeatMode = ExoPlayer.REPEAT_MODE_ONE
                volume = 0f
                setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
                prepare()
            }
    }

    // ── URL resolution (HLS-first, persistent cache, 3 retries) ─────────────

    private suspend fun fetchSignedUrl(videoId: String): String? {
        signedUrlCache[videoId]?.let { return it }

        val R2 = "https://pub-0caba249d019456b9181ce1575ef825e.r2.dev"
        val base = resolveBasePath(videoId)
        val hlsDir = "${base}_hls"

        // Check persistent cache from ReelPagingSource — skip HEAD probing
        val cachedType = com.genzopia.Instagame.reelview.compose.ReelPagingSource.getCachedUrl(videoId)
        if (cachedType != null) {
            signedUrlCache[videoId] = cachedType
            return cachedType
        }

        repeat(3) { attempt ->
            try {
                checkHlsManifest(R2, hlsDir)?.let { name ->
                    val url = "$R2/$hlsDir/$name"
                    signedUrlCache[videoId] = url
                    Log.d(TAG, "[$videoId] HLS: $url")
                    return url
                }
                // Fall back to direct R2 MP4 URL
                val url = "$R2/$base.mp4"
                signedUrlCache[videoId] = url
                Log.d(TAG, "[$videoId] MP4: $url")
                return url
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
                val resp = httpClient.newCall(
                    Request.Builder().url("$r2Base/$hlsDir/$name").head().build()
                ).execute()
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
