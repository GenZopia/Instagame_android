package com.genzopia.Instagame.ui.home.compose

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Paging source for loading home feed videos from Firebase
 * @param showOnlyFollowed If true, queries videos per followed user (Instagram style)
 */
class HomePagingSource(
    private val showOnlyFollowed: Boolean = false
) : PagingSource<String, HomeVideoData>() {

    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val httpClient = OkHttpClient()
    
    companion object {
        private const val TAG = "HomePagingSource"
        private const val PAGE_SIZE = 10
        
        // Cache for signed URLs (video_id -> signed_url)
        private val urlCache = mutableMapOf<String, Pair<String, Long>>()
        private const val CACHE_DURATION = 3600000L // 1 hour in milliseconds
        
        // Cache for followed user IDs
        private var followedUserIdsCache: Set<String>? = null
        private var followedUserIdsCacheTime: Long = 0
        private const val FOLLOWED_USERS_CACHE_DURATION = 60000L // 1 minute

        // Cache for all followed users' videos (parsed data, not DataSnapshot)
        private var allFollowedVideosCache: List<HomeVideoData>? = null
        private var allFollowedVideosCacheTime: Long = 0
        private const val VIDEOS_CACHE_DURATION = 120000L // 2 minutes

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

        fun clearVideosCache() {
            allFollowedVideosCache = null
        }
    }
    
    /**
     * Fetch the list of user IDs that the current user follows
     */
    private suspend fun getFollowedUserIds(): Set<String> {
        if (followedUserIdsCache != null &&
            System.currentTimeMillis() - followedUserIdsCacheTime < FOLLOWED_USERS_CACHE_DURATION) {
            Log.d(TAG, "Using cached followed user IDs (${followedUserIdsCache!!.size} users)")
            return followedUserIdsCache!!
        }

        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Log.w(TAG, "No authenticated user - cannot fetch followed users")
            return emptySet()
        }

        Log.d(TAG, "Fetching followed users for current user: $currentUserId")

        return try {
            val snapshot = database.reference
                .child("users")
                .child(currentUserId)
                .child("following_list")
                .get()
                .await()

            if (!snapshot.exists()) {
                Log.w(TAG, "No following_list found for user $currentUserId")
                followedUserIdsCache = emptySet()
                followedUserIdsCacheTime = System.currentTimeMillis()
                return emptySet()
            }

            val followedIds = snapshot.children.mapNotNull {
                val userId = it.key
                Log.d(TAG, "  - Following user: $userId")
                userId
            }.toSet()

            followedUserIdsCache = followedIds
            followedUserIdsCacheTime = System.currentTimeMillis()

            Log.d(TAG, "Successfully fetched ${followedIds.size} followed users")
            followedIds
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching followed users for $currentUserId", e)
            emptySet()
        }
    }

    /**
     * Fetch ALL videos from ALL followed users.
     * Fetches the entire videos node, filters by user_id, parses, and caches as HomeVideoData.
     */
    private suspend fun fetchAllFollowedUsersVideos(followedUserIds: Set<String>): List<HomeVideoData> {
        // Check cache first
        if (allFollowedVideosCache != null &&
            System.currentTimeMillis() - allFollowedVideosCacheTime < VIDEOS_CACHE_DURATION) {
            Log.d(TAG, "Using cached followed videos (${allFollowedVideosCache!!.size} videos)")
            return allFollowedVideosCache!!
        }

        Log.d(TAG, "Fetching ALL videos and filtering for ${followedUserIds.size} followed users...")

        return try {
            // Fetch ALL videos in one go
            val snapshot = database.reference
                .child("videos")
                .get()
                .await()

            val totalVideos = snapshot.childrenCount
            Log.d(TAG, "Total videos in database: $totalVideos")

            // Filter and parse videos from followed users
            val followedVideos = mutableListOf<HomeVideoData>()
            for (videoSnapshot in snapshot.children) {
                val userId = videoSnapshot.child("user_id").getValue(String::class.java) ?: ""
                if (followedUserIds.contains(userId)) {
                    Log.d(TAG, "  ✓ Video ${videoSnapshot.key} from followed user $userId")
                    val video = parseVideoSnapshot(videoSnapshot)
                    if (video != null) {
                        followedVideos.add(video)
                    }
                }
            }

            // Sort by created_at descending (newest first)
            val sorted = followedVideos.sortedByDescending { it.timestamp }
            // If all timestamps are 0, sort by videoId descending as fallback
            val finalSorted = if (sorted.all { it.timestamp == 0L }) {
                followedVideos.sortedByDescending { it.videoId }
            } else {
                sorted
            }

            Log.d(TAG, "Found ${finalSorted.size} videos from followed users (out of $totalVideos total)")

            // Fetch signed URLs for all videos in parallel
            val videosWithUrls = withContext(Dispatchers.IO) {
                finalSorted.map { video ->
                    async {
                        try {
                            val signedUrl = fetchSignedUrl(video.videoId)
                            video.copy(videoUrl = signedUrl)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error fetching signed URL for ${video.videoId}", e)
                            video
                        }
                    }
                }.awaitAll()
            }

            // Cache the parsed results
            allFollowedVideosCache = videosWithUrls
            allFollowedVideosCacheTime = System.currentTimeMillis()

            videosWithUrls
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching all videos", e)
            emptyList()
        }
    }

    override suspend fun load(params: LoadParams<String>): LoadResult<String, HomeVideoData> {
        return try {
            val startKey = params.key
            
            Log.d(TAG, "========== LOADING PAGE ==========")
            Log.d(TAG, "startKey: $startKey, showOnlyFollowed: $showOnlyFollowed")

            if (showOnlyFollowed) {
                loadFollowedUsersVideos(startKey)
            } else {
                loadAllVideos(startKey)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error loading videos", e)
            LoadResult.Error(e)
        }
    }

    /**
     * Load videos from followed users (Instagram approach).
     * Fetches ALL videos from all followed users, then paginates locally.
     */
    private suspend fun loadFollowedUsersVideos(startKey: String?): LoadResult<String, HomeVideoData> {
        val followedUserIds = getFollowedUserIds()
        Log.d(TAG, "Following ${followedUserIds.size} users: ${followedUserIds.joinToString(", ")}")

        if (followedUserIds.isEmpty()) {
            Log.w(TAG, "No followed users found - returning empty list")
            return LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
        }

        // Fetch ALL videos from all followed users (already parsed with signed URLs)
        val allVideos = fetchAllFollowedUsersVideos(followedUserIds)

        if (allVideos.isEmpty()) {
            Log.d(TAG, "No videos found from followed users")
            return LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
        }

        // Find the starting index based on the key
        val startIndex = if (startKey == null) {
            0
        } else {
            val idx = allVideos.indexOfFirst { it.videoId == startKey }
            if (idx == -1) 0 else idx + 1  // Start after the key
        }

        Log.d(TAG, "Paginating: startIndex=$startIndex, total=${allVideos.size}")

        // Get the page of videos
        val pageVideos = allVideos.drop(startIndex).take(PAGE_SIZE)

        if (pageVideos.isEmpty()) {
            Log.d(TAG, "No more videos to show (startIndex=$startIndex >= total=${allVideos.size})")
            return LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
        }

        // Determine the nextKey
        val lastVideoId = pageVideos.lastOrNull()?.videoId
        val hasMore = startIndex + pageVideos.size < allVideos.size
        val nextKey = if (hasMore) lastVideoId else null

        Log.d(TAG, "========== RETURNING RESULTS ==========")
        Log.d(TAG, "Page videos: ${pageVideos.size}")
        Log.d(TAG, "Total available: ${allVideos.size}")
        Log.d(TAG, "Showing: ${startIndex + 1} to ${startIndex + pageVideos.size}")
        Log.d(TAG, "NextKey: $nextKey (hasMore: $hasMore)")
        Log.d(TAG, "======================================")

        return LoadResult.Page(
            data = pageVideos,
            prevKey = null,
            nextKey = nextKey
        )
    }

    /**
     * Load all videos (original behavior, no filtering).
     */
    private suspend fun loadAllVideos(startKey: String?): LoadResult<String, HomeVideoData> {
        val query = if (startKey == null) {
            database.reference.child("videos")
                .orderByKey()
                .limitToFirst(PAGE_SIZE)
        } else {
            database.reference.child("videos")
                .orderByKey()
                .startAfter(startKey)
                .limitToFirst(PAGE_SIZE)
        }

        val snapshot = query.get().await()

        if (!snapshot.exists()) {
            return LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
        }

        val videos = mutableListOf<HomeVideoData>()
        var lastKey: String? = null

        for (videoSnapshot in snapshot.children) {
            val videoId = videoSnapshot.key ?: continue
            lastKey = videoId

            try {
                val video = parseVideoSnapshot(videoSnapshot)
                if (video != null) {
                    videos.add(video)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing video $videoId", e)
            }
        }

        val videosWithUrls = withContext(Dispatchers.IO) {
            videos.map { video ->
                async {
                    try {
                        val signedUrl = fetchSignedUrl(video.videoId)
                        video.copy(videoUrl = signedUrl)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching signed URL for ${video.videoId}", e)
                        video
                    }
                }
            }.awaitAll()
        }

        return LoadResult.Page(
            data = videosWithUrls,
            prevKey = null,
            nextKey = if (videosWithUrls.size < PAGE_SIZE) null else lastKey
        )
    }
    
    override fun getRefreshKey(state: PagingState<String, HomeVideoData>): String? {
        return null
    }
    
    private suspend fun parseVideoSnapshot(snapshot: DataSnapshot): HomeVideoData? {
        val videoId = snapshot.key ?: return null
        
        val title = snapshot.child("video_title").getValue(String::class.java) ?: "Untitled"
        val description = snapshot.child("description").getValue(String::class.java) ?: ""
        val viewCount = snapshot.child("view_count").getValue(String::class.java) ?: "0"
        val likeCount = snapshot.child("like_count").getValue(String::class.java) ?: "0"
        val developerId = snapshot.child("user_id").getValue(String::class.java) ?: ""
        val gameId = snapshot.child("game_id").getValue(String::class.java) ?: ""
        val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
        
        // Fetch developer info from users node
        val (developerName, developerPhotoUrl) = if (developerId.isNotEmpty()) {
            fetchDeveloperInfo(developerId)
        } else {
            Pair("Unknown", null)
        }
        
        // Fetch game name if gameId exists
        val gameName = if (gameId.isNotEmpty()) {
            fetchGameName(gameId)
        } else {
            ""
        }
        
        return HomeVideoData(
            videoId = videoId,
            title = title,
            description = description,
            viewCount = viewCount,
            likeCount = likeCount,
            developerId = developerId,
            developerName = developerName,
            developerPhotoUrl = developerPhotoUrl,
            gameId = gameId,
            gameName = gameName,
            timestamp = timestamp
        )
    }
    
    private suspend fun fetchDeveloperInfo(userId: String): Pair<String, String?> {
        return try {
            val userSnapshot = database.reference.child("users").child(userId).get().await()
            val name = userSnapshot.child("full_name").getValue(String::class.java)
                ?: userSnapshot.child("name").getValue(String::class.java) 
                ?: userSnapshot.child("username").getValue(String::class.java) 
                ?: "User"
            val rawPhoto = userSnapshot.child("profile_photo_url").getValue(String::class.java)
                ?: userSnapshot.child("profile_image_url").getValue(String::class.java)
                ?: userSnapshot.child("photoUrl").getValue(String::class.java)
            val photoUrl = com.genzopia.Instagame.utils.ProfilePhotoUtils.sanitize(rawPhoto)
            
            Log.d(TAG, "Fetched developer info for $userId: name=$name, photoUrl=$photoUrl")
            Pair(name, photoUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching developer info for $userId", e)
            Pair("User", null)
        }
    }
    
    private suspend fun fetchGameName(gameId: String): String {
        return try {
            val gameSnapshot = database.reference.child("games").child(gameId).get().await()
            val gameName = gameSnapshot.child("name").getValue(String::class.java) 
                ?: gameSnapshot.child("game_name").getValue(String::class.java)
                ?: ""
            
            Log.d(TAG, "Fetched game name for $gameId: $gameName")
            gameName
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching game name for $gameId", e)
            ""
        }
    }
    
    private suspend fun fetchSignedUrl(videoId: String): String? {
        // Check cache first
        getCachedUrl(videoId)?.let { return it }
        
        return suspendCancellableCoroutine { continuation ->
            val url = "https://video-signer.genzopia.workers.dev/?path=video/$videoId"
            val request = Request.Builder().url(url).build()
            
            httpClient.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    Log.e(TAG, "Failed to fetch signed URL for $videoId", e)
                    continuation.resume(null)
                }
                
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    try {
                        val body = response.body?.string()
                        if (body != null) {
                            val json = JSONObject(body)
                            if (json.optBoolean("success")) {
                                val signedUrl = json.optString("url")
                                if (signedUrl.isNotEmpty()) {
                                    cacheUrl(videoId, signedUrl)
                                    continuation.resume(signedUrl)
                                } else {
                                    continuation.resume(null)
                                }
                            } else {
                                continuation.resume(null)
                            }
                        } else {
                            continuation.resume(null)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing signed URL response", e)
                        continuation.resume(null)
                    }
                }
            })
        }
    }
}
