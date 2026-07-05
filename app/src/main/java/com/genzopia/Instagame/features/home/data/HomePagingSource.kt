package com.genzopia.Instagame.ui.home.compose

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.genzopia.Instagame.gateway.GatewayClient

/**
 * Paging source for the home feed — now backed by the gateway GET /reels endpoint.
 * All Firebase reads are handled server-side.
 */
class HomePagingSource : PagingSource<String, HomeVideoData>() {

    companion object {
        private const val TAG = "HomePagingSource"
        private const val PAGE_SIZE = 10

        fun clearVideosCache() {} // no-op — gateway handles caching
    }

    override suspend fun load(params: LoadParams<String>): LoadResult<String, HomeVideoData> {
        return try {
            val cursor = params.key
            val resp = GatewayClient.api.getReels(cursor, PAGE_SIZE)
            if (!resp.isSuccessful || resp.body() == null) {
                return LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
            }
            val body = resp.body()!!
            val videos = body.data.map { r ->
                HomeVideoData(
                    videoId = r.videoId,
                    title = r.title,
                    description = r.description,
                    viewCount = r.viewCount,
                    likeCount = r.likeCount,
                    developerId = r.developerId,
                    developerName = r.developerName,
                    developerPhotoUrl = r.developerPhotoUrl,
                    gameId = r.gameId,
                    gameName = r.gameName,
                    videoUrl = r.playbackUrl,
                    timestamp = r.timestamp
                )
            }
            LoadResult.Page(
                data = videos,
                prevKey = null,
                nextKey = body.nextCursor
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading home feed", e)
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<String, HomeVideoData>): String? = null
}
