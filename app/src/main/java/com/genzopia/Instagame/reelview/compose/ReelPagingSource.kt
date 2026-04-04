package com.genzopia.Instagame.reelview.compose

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.genzopia.Instagame.utils.DataPrefetchService
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Paging source for loading reels from Firebase with signed URLs
 */
class ReelPagingSource : PagingSource<String, ReelData>() {
    
    private val database = FirebaseDatabase.getInstance()
    private val httpClient = OkHttpClient()
    
    companion object {
        private const val TAG = "ReelPagingSource"
        private const val PAGE_SIZE = 5  // Reduced from 10 for faster initial load
        
        // Cache for signed URLs (video_id -> signed_url)
        private val urlCache = mutableMapOf<String, Pair<String, Long>>()
        private const val CACHE_DURATION = 3600000L // 1 hour in milliseconds
        
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

    @OptIn(UnstableApi::class)
    override suspend fun load(params: LoadParams<String>): LoadResult<String, ReelData> {
        return try {
            val startKey = params.key
            
            Log.d(TAG, "Loading reels page with startKey: $startKey")
            
            // For first page, try to use prefetched data for instant display
            if (startKey == null) {
                Log.d(TAG, "First page load - checking prefetch cache")
                val prefetchedReels = tryLoadFromPrefetchCache()
                if (prefetchedReels.isNotEmpty()) {
                    Log.d(TAG, "Returning ${prefetchedReels.size} prefetched reels INSTANTLY")
                    return LoadResult.Page(
                        data = prefetchedReels,
                        prevKey = null,
                        nextKey = prefetchedReels.lastOrNull()?.videoId
                    )
                } else {
                    Log.d(TAG, "No prefetched data available, loading from Firebase")
                }
            }
            
            // Query Firebase for videos
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
            
            // Fetch videos from Firebase
            val snapshot = query.get().await()
            
            if (!snapshot.exists()) {
                Log.d(TAG, "No more reels found")
                return LoadResult.Page(
                    data = emptyList(),
                    prevKey = null,
                    nextKey = null
                )
            }
            
            val reels = mutableListOf<ReelData>()
            var lastKey: String? = null
            
            // Parse all videos first
            for (videoSnapshot in snapshot.children) {
                val videoId = videoSnapshot.key ?: continue
                lastKey = videoId
                
                try {
                    val reel = parseVideoSnapshot(videoSnapshot)
                    if (reel != null) {
                        reels.add(reel)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing reel $videoId", e)
                }
            }
            
            // Fetch signed URLs in parallel, checking prefetch cache first
            val reelsWithUrls = withContext(Dispatchers.IO) {
                reels.map { reel ->
                    async {
                        try {
                            val cachedUrl = DataPrefetchService.getCachedSignedUrl(reel.videoId)
                            if (cachedUrl != null) {
                                Log.d(TAG, "Using prefetched URL for reel ${reel.videoId}")
                                reel.copy(videoUrl = cachedUrl)
                            } else {
                                val (mp4Url, hlsUrl) = fetchSignedUrl(reel.videoId)
                                reel.copy(videoUrl = mp4Url, hlsManifestUrl = hlsUrl)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error fetching signed URL for reel ${reel.videoId}", e)
                            reel
                        }
                    }
                }.map { it.await() }
            }
            
            Log.d(TAG, "Loaded ${reelsWithUrls.size} reels, nextKey: $lastKey")
            
            LoadResult.Page(
                data = reelsWithUrls,
                prevKey = null,
                nextKey = if (reelsWithUrls.size < PAGE_SIZE) null else lastKey
            )

            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading reels", e)
            LoadResult.Error(e)
        }
    }
    
    override fun getRefreshKey(state: PagingState<String, ReelData>): String? {
        // Return null to always start from the beginning on refresh
        return null
    }
    
    private suspend fun parseVideoSnapshot(snapshot: DataSnapshot): ReelData? {
        val videoId = snapshot.key ?: return null
        
        val title = snapshot.child("video_title").getValue(String::class.java) ?: "Untitled"
        val description = snapshot.child("description").getValue(String::class.java) ?: ""
        val likeCount = snapshot.child("like_count").getValue(String::class.java) ?: "0"
        val developerId = snapshot.child("user_id").getValue(String::class.java) ?: ""
        val gameId = snapshot.child("game_id").getValue(String::class.java) ?: ""
        
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
        
        // Check if current user is following this developer
        val isFollowing = if (developerId.isNotEmpty()) {
            checkIfFollowing(developerId)
        } else {
            false
        }
        
        // Check if current user has liked this video
        val isLiked = checkIfLiked(videoId)
        
        return ReelData(
            videoId = videoId,
            title = title,
            description = description,
            likeCount = likeCount,
            developerId = developerId,
            developerName = developerName,
            developerPhotoUrl = developerPhotoUrl,
            gameId = gameId,
            gameName = gameName,
            isFollowing = isFollowing,
            isLiked = isLiked
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
    
    private suspend fun checkIfFollowing(developerId: String): Boolean {
        return try {
            val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (currentUserId == null || currentUserId == developerId) {
                // Not logged in or it's the user's own video
                return false
            }
            
            val followingSnapshot = database.reference
                .child("users")
                .child(currentUserId)
                .child("following_list")
                .child(developerId)
                .get()
                .await()
            
            val isFollowing = followingSnapshot.exists() && followingSnapshot.getValue(Boolean::class.java) == true
            Log.d(TAG, "User $currentUserId following $developerId: $isFollowing")
            isFollowing
        } catch (e: Exception) {
            Log.e(TAG, "Error checking follow status for $developerId", e)
            false
        }
    }
    
    private suspend fun checkIfLiked(videoId: String): Boolean {
        return try {
            val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (currentUserId == null) {
                return false
            }
            
            val likedSnapshot = database.reference
                .child("users")
                .child(currentUserId)
                .child("liked_videos")
                .child(videoId)
                .get()
                .await()
            
            val isLiked = likedSnapshot.exists() && likedSnapshot.getValue(Boolean::class.java) == true
            Log.d(TAG, "User $currentUserId liked video $videoId: $isLiked")
            isLiked
        } catch (e: Exception) {
            Log.e(TAG, "Error checking like status for $videoId", e)
            false
        }
    }
    
    private suspend fun fetchSignedUrl(videoId: String): Pair<String?, String?> {
        // Returns Pair(mp4Url, hlsManifestUrl)
        // Check cache first
        getCachedUrl(videoId)?.let { return Pair(it, null) }

        return withContext(Dispatchers.IO) {
            try {
                // Mirror web's getSignedVideoUrlWithType():
                // 1. Resolve base path: video/video_xxx  (strip extensions, add prefix)
                val basePath = resolveVideoBasePath(videoId)
                val hlsDir   = "${basePath}_hls"

                // 2. Check if HLS manifest exists on the public R2 bucket (same as web HEAD check)
                val R2_PUBLIC = "https://pub-0caba249d019456b9181ce1575ef825e.r2.dev"
                val manifestName = checkHlsManifest(R2_PUBLIC, hlsDir)

                if (manifestName != null) {
                    // HLS exists — build manifest URL directly from public R2
                    val hlsUrl = "$R2_PUBLIC/$hlsDir/$manifestName"
                    Log.d(TAG, "Video $videoId → HLS: $hlsUrl")
                    Pair(null, hlsUrl)
                } else {
                    // No HLS — get signed MP4 URL from the signer worker
                    val mp4Url = fetchSignerUrl(videoId)
                    Log.d(TAG, "Video $videoId → MP4: $mp4Url")
                    if (mp4Url != null) cacheUrl(videoId, mp4Url)
                    Pair(mp4Url, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving URL for $videoId", e)
                Pair(null, null)
            }
        }
    }

    /** Mirrors web resolveVideoBasePath() */
    private fun resolveVideoBasePath(videoIdOrKey: String): String {
        var id = videoIdOrKey
        if (id.startsWith("video/")) return id.replace(Regex("\\.[^.]+$"), "")
        id = id.removeSuffix(".mp4").removeSuffix(".m3u8")
        return if (id.startsWith("video_")) "video/$id" else "video/video_$id"
    }

    /** HEAD-check the public R2 bucket for HLS manifests, returns manifest name or null */
    private fun checkHlsManifest(r2Base: String, hlsDir: String): String? {
        val manifests = listOf("master.m3u8", "1080p.m3u8", "playlist.m3u8", "index.m3u8")
        for (name in manifests) {
            try {
                val req = Request.Builder()
                    .url("$r2Base/$hlsDir/$name")
                    .head()
                    .build()
                val resp = httpClient.newCall(req).execute()
                resp.close()
                if (resp.isSuccessful) {
                    Log.d(TAG, "HLS manifest found: $hlsDir/$name")
                    return name
                }
            } catch (_: Exception) { /* try next */ }
        }
        return null
    }

    /** Call video-signer worker for a plain signed MP4 URL */
    private fun fetchSignerUrl(videoId: String): String? {
        return try {
            val url = "https://video-signer.genzopia.workers.dev/?path=video/$videoId"
            val resp = httpClient.newCall(Request.Builder().url(url).build()).execute()
            val body = resp.body?.string() ?: return null
            resp.close()
            val json = JSONObject(body)
            if (json.optBoolean("success")) json.optString("url").takeIf { it.isNotEmpty() }
            else null
        } catch (e: Exception) {
            Log.e(TAG, "Signer error for $videoId", e)
            null
        }
    }
    
    /**
     * Try to load reels from prefetch cache for instant display
     */
    private suspend fun tryLoadFromPrefetchCache(): List<ReelData> {
        return try {
            // First, check if we have prefetched videos
            val cachedVideos = DataPrefetchService.getAllCachedVideos()
            
            if (cachedVideos.isEmpty()) {
                Log.d(TAG, "No prefetched videos found")
                return emptyList()
            }
            
            Log.d(TAG, "Found ${cachedVideos.size} prefetched videos")
            
            // Get the first 3 videos from Firebase to get full metadata
            // This is still needed but should be fast since we're only getting metadata
            val snapshot = database.reference.child("videos")
                .orderByKey()
                .limitToFirst(3)
                .get()
                .await()
            
            if (!snapshot.exists()) {
                Log.d(TAG, "No videos in Firebase")
                return emptyList()
            }
            
            val reels = mutableListOf<ReelData>()
            
            // Process videos sequentially for the first page to ensure order
            for (videoSnapshot in snapshot.children) {
                val videoId = videoSnapshot.key ?: continue
                
                try {
                    // Parse the video data
                    val reel = parseVideoSnapshot(videoSnapshot)
                    if (reel != null) {
                        // Use prefetched URL if available
                        val signedUrl = DataPrefetchService.getCachedSignedUrl(videoId)
                        
                        if (signedUrl != null) {
                            reels.add(reel.copy(videoUrl = signedUrl))
                            Log.d(TAG, "✓ Using prefetched URL for video $videoId")
                        } else {
                            // If not prefetched, fetch it now (shouldn't happen for first 3)
                            Log.w(TAG, "⚠ Video $videoId not prefetched, fetching now")
                            val (mp4Url, hlsUrl) = fetchSignedUrl(videoId)
                            reels.add(reel.copy(videoUrl = mp4Url, hlsManifestUrl = hlsUrl))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading reel $videoId", e)
                }
            }
            
            Log.d(TAG, "✓✓✓ Returning ${reels.size} reels from prefetch cache INSTANTLY")
            reels
        } catch (e: Exception) {
            Log.e(TAG, "Error loading from prefetch cache", e)
            emptyList()
        }
    }
}
