package com.genzopia.Instagame.reelview.compose

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.genzopia.Instagame.gateway.GatewayClient
import com.genzopia.Instagame.utils.DataPrefetchService

/**
 * Paging source for loading reels from the backend Gateway.
 *
 * All Firebase reads (developer info, game info, isLiked, isFollowing) and URL
 * resolution are now handled server-side — the single GET /reels response
 * contains every field needed by [ReelData].
 *
 * The DataPrefetchService cache and ExoPlayer pool logic are kept intact.
 */
class ReelPagingSource : PagingSource<String, ReelData>() {

    companion object {
        private const val TAG = "ReelPagingSource"
        private const val PAGE_SIZE = 20

        // In-memory signed URL cache retained for DataPrefetchService compatibility
        private val urlCache = mutableMapOf<String, Pair<String, Long>>()
        private const val CACHE_DURATION = 3600000L // 1 hour

        // Persistent HLS-vs-MP4 decision cache (kept for backward-compat with prefetch service)
        private var prefs: SharedPreferences? = null

        fun init(context: Context) {
            if (prefs == null) {
                prefs = context.applicationContext
                    .getSharedPreferences("reel_url_type_cache", Context.MODE_PRIVATE)
            }
        }

        fun getCachedUrl(videoId: String): String? {
            val cached = urlCache[videoId]
            return if (cached != null && System.currentTimeMillis() - cached.second < CACHE_DURATION) {
                cached.first
            } else {
                urlCache.remove(videoId)
                null
            }
        }

        fun cacheUrl(videoId: String, url: String) {
            urlCache[videoId] = Pair(url, System.currentTimeMillis())
        }
    }

    override suspend fun load(params: LoadParams<String>): LoadResult<String, ReelData> {
        return try {
            val cursor = params.key
            Log.d(TAG, "Loading reels page from gateway, cursor=$cursor")

            // For the first page check the prefetch cache for instant display
            if (cursor == null) {
                val prefetchedReels = tryLoadFromPrefetchCache()
                if (prefetchedReels.isNotEmpty()) {
                    Log.d(TAG, "Returning ${prefetchedReels.size} reels INSTANTLY from prefetch cache")
                    return LoadResult.Page(
                        data = prefetchedReels,
                        prevKey = null,
                        nextKey = prefetchedReels.lastOrNull()?.videoId
                    )
                }
                Log.d(TAG, "Prefetch cache empty, loading from gateway")
            }

            val response = GatewayClient.api.getReels(cursor = cursor, limit = PAGE_SIZE)

            if (!response.isSuccessful) {
                Log.e(TAG, "Gateway /reels returned HTTP ${response.code()}")
                return LoadResult.Error(Exception("Gateway error ${response.code()}"))
            }

            val body = response.body() ?: return LoadResult.Page(
                data = emptyList(), prevKey = null, nextKey = null
            )

            val reels = body.data.map { dto ->
                ReelData(
                    videoId        = dto.videoId,
                    videoUrl       = if (dto.hlsManifestUrl == null) dto.playbackUrl else null,
                    hlsManifestUrl = dto.hlsManifestUrl,
                    title          = dto.title,
                    description    = dto.description,
                    likeCount      = dto.likeCount,
                    developerId    = dto.developerId,
                    developerName  = dto.developerName,
                    developerPhotoUrl = dto.developerPhotoUrl,
                    gameId         = dto.gameId,
                    gameName       = dto.gameName,
                    gameImageUrl   = dto.gameImageUrl,
                    isLiked        = dto.isLiked,
                    isFollowing    = dto.isFollowing,
                    timestamp      = dto.timestamp
                )
            }

            Log.d(TAG, "Gateway returned ${reels.size} reels, nextCursor=${body.nextCursor}")

            LoadResult.Page(
                data    = reels,
                prevKey = null,
                nextKey = body.nextCursor
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading reels from gateway", e)
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<String, ReelData>): String? = null

    /**
     * Instant load from DataPrefetchService cache — zero network calls.
     * Kept intact so the splash-screen prefetch still delivers immediate content.
     */
    private fun tryLoadFromPrefetchCache(): List<ReelData> {
        val cached = DataPrefetchService.getAllCachedVideos()
        if (cached.isEmpty()) {
            Log.d(TAG, "Prefetch cache empty")
            return emptyList()
        }
        val reels = cached.values.map { meta ->
            val url = DataPrefetchService.getCachedSignedUrl(meta.videoId)
            val isHls = url?.contains(".m3u8") == true
            ReelData(
                videoId           = meta.videoId,
                videoUrl          = if (isHls || url == null) null else url,
                hlsManifestUrl    = if (isHls) url else null,
                title             = meta.title,
                developerId       = meta.userId,
                developerName     = meta.developerName,
                developerPhotoUrl = meta.developerPhotoUrl,
                gameId            = meta.gameId
            )
        }
        val withUrl = reels.count { it.videoUrl != null || it.hlsManifestUrl != null }
        Log.d(TAG, "Returning ${reels.size} reels from prefetch cache ($withUrl with URLs)")
        return reels
    }
}
