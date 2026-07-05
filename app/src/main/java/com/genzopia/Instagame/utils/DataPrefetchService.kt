package com.genzopia.Instagame.utils

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.genzopia.Instagame.features.home.domain.FollowedUser
import com.genzopia.Instagame.gateway.GatewayClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DataPrefetchService — prefetches video metadata and builds ExoPlayer pool.
 *
 * All data is now fetched via the Gateway (GET /videos/prefetch and GET /users/me/following).
 * No direct Firebase Realtime Database or CDN calls remain here.
 */
@UnstableApi
object DataPrefetchService {

    private const val TAG = "DataPrefetchService"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val videoCache     = mutableMapOf<String, VideoMetadata>()
    private val signedUrlCache = mutableMapOf<String, String>()
    private var followedUsersCache: List<FollowedUser>? = null

    private val playerPool       = mutableMapOf<String, ExoPlayer>()
    private var preloadedPlayer  : ExoPlayer? = null
    private var preloadedVideoId : String?    = null

    @Volatile private var firstVideoReady = false

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
    fun clearPreloadedPlayer() { preloadedPlayer = null; preloadedVideoId = null }
    fun removeFromPool(videoId: String) { playerPool.remove(videoId) }

    // ── Public entry point ────────────────────────────────────────────────

    fun startPrefetch(context: Context, onComplete: (() -> Unit)? = null) {
        Log.d(TAG, "startPrefetch: firing background jobs")
        scope.launch {
            val followJob = launch {
                try { prefetchFollowedUsers() }
                catch (e: Exception) { Log.e(TAG, "followedUsers prefetch failed", e) }
            }
            try {
                prefetchVideos(context, 30, onComplete)
            } catch (e: Exception) {
                Log.e(TAG, "videos prefetch failed", e)
                withContext(Dispatchers.Main) { onComplete?.invoke() }
            }
            followJob.join()
            Log.d(TAG, "Background prefetch done")
        }
    }

    // ── Video prefetch via gateway ────────────────────────────────────────

    private suspend fun prefetchVideos(
        context: Context,
        count: Int,
        onMetadataReady: (() -> Unit)? = null
    ) {
        Log.d(TAG, "Fetching $count videos from gateway /videos/prefetch")

        val response = withContext(Dispatchers.IO) {
            GatewayClient.api.getPrefetch(limit = count)
        }

        if (!response.isSuccessful || response.body() == null) {
            Log.e(TAG, "Gateway prefetch failed: HTTP ${response.code()}")
            withContext(Dispatchers.Main) { onMetadataReady?.invoke() }
            return
        }

        val items = response.body()!!.data
        Log.d(TAG, "Got ${items.size} videos from gateway")

        // Populate caches from the gateway response (metadata already enriched server-side)
        items.forEachIndexed { index, item ->
            val meta = VideoMetadata(
                videoId           = item.videoId,
                title             = item.title,
                userId            = item.userId,
                gameId            = item.gameId,
                developerName     = item.developerName,
                developerPhotoUrl = item.developerPhotoUrl,
                signedUrl         = item.playbackUrl ?: item.hlsManifestUrl
            )
            videoCache[item.videoId] = meta
            val url = item.hlsManifestUrl ?: item.playbackUrl
            if (url != null) signedUrlCache[item.videoId] = url
            Log.d(TAG, "[$index] cached ${item.videoId}")
        }

        Log.d(TAG, "Metadata ready — ${items.size} reels cached")

        // Notify splash screen: metadata is ready → unblock navigation
        withContext(Dispatchers.Main) { onMetadataReady?.invoke() }

        // Build ExoPlayer pool on main thread (CPU only, no network)
        withContext(Dispatchers.Main) {
            val appCtx = context.applicationContext
            items.forEachIndexed { index, item ->
                val url = signedUrlCache[item.videoId] ?: return@forEachIndexed
                try {
                    val player = buildPlayer(appCtx, url)
                    playerPool[item.videoId] = player
                    if (index == 0) { preloadedVideoId = item.videoId; preloadedPlayer = player }
                    Log.d(TAG, "[$index] Player ready: ${item.videoId}")
                } catch (e: Exception) {
                    Log.e(TAG, "[$index] Player failed: ${item.videoId}", e)
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
                repeatMode  = ExoPlayer.REPEAT_MODE_ONE
                volume      = 0f
                setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
                prepare()
            }
    }

    // ── Followed users via gateway ────────────────────────────────────────

    private suspend fun prefetchFollowedUsers() {
        Log.d(TAG, "Fetching following list from gateway /users/me/following")
        try {
            val response = withContext(Dispatchers.IO) {
                GatewayClient.api.getFollowing()
            }
            if (!response.isSuccessful || response.body() == null) {
                Log.e(TAG, "getFollowing failed: HTTP ${response.code()}")
                followedUsersCache = emptyList()
                return
            }
            followedUsersCache = response.body()!!.map { dto ->
                FollowedUser(
                    userId          = dto.userId,
                    fullName        = dto.full_name,
                    profilePhotoUrl = dto.profile_photo_url
                )
            }
            Log.d(TAG, "Prefetched ${followedUsersCache?.size} followed users")
        } catch (e: Exception) {
            Log.e(TAG, "prefetchFollowedUsers error", e)
            followedUsersCache = emptyList()
        }
    }

    // ── Public cache accessors ────────────────────────────────────────────

    fun getCachedVideo(videoId: String): VideoMetadata? = videoCache[videoId]
    fun getAllCachedVideos(): Map<String, VideoMetadata> = videoCache.toMap()
    fun getCachedSignedUrl(videoId: String): String? = signedUrlCache[videoId]
    fun isVideoCached(videoId: String): Boolean = videoCache.containsKey(videoId)
    fun getCachedFollowedUsers(): List<FollowedUser>? = followedUsersCache

    fun clearCache() {
        videoCache.clear()
        signedUrlCache.clear()
        followedUsersCache = null
        firstVideoReady    = false
        playerPool.values.forEach { it.release() }
        playerPool.clear()
        preloadedPlayer  = null
        preloadedVideoId = null
        Log.d(TAG, "Cache cleared")
    }
}
