package com.genzopia.Instagame.reelview.compose

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.genzopia.Instagame.utils.DataPrefetchService
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
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
        private const val PAGE_SIZE = 5
        private const val FETCH_SIZE = 20  // fetch more to account for unverified videos being filtered out
        
        // In-memory signed URL cache (video_id -> url, timestamp)
        private val urlCache = mutableMapOf<String, Pair<String, Long>>()
        private const val CACHE_DURATION = 3600000L // 1 hour

        // Option 3: Persistent cache for HLS vs MP4 decision
        // Key: "url_type_<videoId>" → "hls:<manifestUrl>" or "mp4"
        // This survives app restarts — no HEAD probing on repeat visits
        private var prefs: SharedPreferences? = null

        fun init(context: Context) {
            if (prefs == null) {
                prefs = context.applicationContext
                    .getSharedPreferences("reel_url_type_cache", Context.MODE_PRIVATE)
            }
        }

        private fun getCachedUrlType(videoId: String): String? = prefs?.getString("url_type_$videoId", null)

        private fun cacheUrlType(videoId: String, value: String) {
            prefs?.edit()?.putString("url_type_$videoId", value)?.apply()
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

    @OptIn(UnstableApi::class)
    override suspend fun load(params: LoadParams<String>): LoadResult<String, ReelData> {
        return try {
            val startKey = params.key
            
            Log.d(TAG, "Loading reels page with startKey: $startKey")
            
            // For first page, try to use prefetched data for instant display
            if (startKey == null) {
                val prefetchedReels = tryLoadFromPrefetchCache()
                if (prefetchedReels.isNotEmpty()) {
                    Log.d(TAG, "Returning ${prefetchedReels.size} reels INSTANTLY from cache")
                    return LoadResult.Page(
                        data = prefetchedReels,
                        prevKey = null,
                        nextKey = prefetchedReels.lastOrNull()?.videoId
                    )
                }
                Log.d(TAG, "Cache empty, loading from Firebase")
            }
            
            // Query Firebase for videos — fetch more than PAGE_SIZE to account for unverified ones
            val query = if (startKey == null) {
                database.reference.child("videos")
                    .orderByKey()
                    .limitToFirst(FETCH_SIZE)
            } else {
                database.reference.child("videos")
                    .orderByKey()
                    .startAfter(startKey)
                    .limitToFirst(FETCH_SIZE)
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
            var rawCount = 0

            // Parse all videos first — lastKey tracks ALL fetched keys for correct pagination cursor
            for (videoSnapshot in snapshot.children) {
                val videoId = videoSnapshot.key ?: continue
                lastKey = videoId  // always advance cursor, even for unverified videos
                rawCount++

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
            
            Log.d(TAG, "Fetched $rawCount raw videos, ${reelsWithUrls.size} verified, nextKey: $lastKey")

            LoadResult.Page(
                data = reelsWithUrls,
                prevKey = null,
                // Stop pagination only when Firebase returned fewer items than we asked for
                nextKey = if (rawCount < FETCH_SIZE) null else lastKey
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

        // Only show verified videos
        val isVerifiedRaw = snapshot.child("is_verified").value
        val isVerified = when (isVerifiedRaw) {
            is Boolean -> isVerifiedRaw
            is String  -> isVerifiedRaw.equals("true", ignoreCase = true)
            is Long    -> isVerifiedRaw == 1L
            is Int     -> isVerifiedRaw == 1
            else       -> false
        }
        if (!isVerified) {
            Log.d(TAG, "Skipping unverified video: $videoId")
            return null
        }

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
        
        // Fetch game name and image if gameId exists
        val (gameName, gameImageUrl) = if (gameId.isNotEmpty()) {
            fetchGameInfo(gameId)
        } else {
            Pair("", "")
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
            gameImageUrl = gameImageUrl,
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
    
    private suspend fun fetchGameInfo(gameId: String): Pair<String, String> {
        return try {
            val gameSnapshot = database.reference.child("games").child(gameId).get().await()
            val gameName = gameSnapshot.child("game_name").getValue(String::class.java)
                ?: gameSnapshot.child("name").getValue(String::class.java) ?: ""
            val photoId = gameSnapshot.child("photo_id").getValue(String::class.java) ?: ""
            val imageUrl = if (photoId.isNotEmpty()) {
                try {
                    val photoSnap = database.reference.child("photos").child(photoId).get().await()
                    val fileExt = photoSnap.child("file_ext").getValue(String::class.java)
                        ?: photoSnap.child("file_name").getValue(String::class.java)
                            ?.substringAfterLast('.', "jpg") ?: "jpg"
                    withContext(Dispatchers.IO) {
                        com.genzopia.Instagame.utils.PhotoUrlResolver.resolveSync(photoId, fileExt) ?: ""
                    }
                } catch (e: Exception) { "" }
            } else ""
            Pair(gameName, imageUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching game info for $gameId", e)
            Pair("", "")
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
        // Check in-memory cache first
        getCachedUrl(videoId)?.let { return Pair(it, null) }

        return withContext(Dispatchers.IO) {
            try {
                val R2_PUBLIC = "https://cdn.genzopia.com"
                val basePath = resolveVideoBasePath(videoId)
                val hlsDir = "${basePath}_hls"

                // Option 3: check persistent cache — skip HEAD probing entirely if we've seen this video before
                val cachedType = getCachedUrlType(videoId)
                if (cachedType != null) {
                    if (cachedType.startsWith("hls:")) {
                        val hlsUrl = cachedType.removePrefix("hls:")
                        Log.d(TAG, "Video $videoId → HLS (persistent cache): $hlsUrl")
                        return@withContext Pair(null, hlsUrl)
                    } else {
                        // "mp4" — go straight to direct URL, no HEAD check
                        val mp4Url = fetchSignerUrl(videoId)
                        Log.d(TAG, "Video $videoId → MP4 (persistent cache): $mp4Url")
                        cacheUrl(videoId, mp4Url)
                        return@withContext Pair(mp4Url, null)
                    }
                }

                // First time seeing this video — do the HEAD check once, then persist the result
                val manifestName = checkHlsManifest(R2_PUBLIC, hlsDir)
                if (manifestName != null) {
                    val hlsUrl = "$R2_PUBLIC/$hlsDir/$manifestName"
                    cacheUrlType(videoId, "hls:$hlsUrl") // persist so we never HEAD-check again
                    Log.d(TAG, "Video $videoId → HLS (discovered): $hlsUrl")
                    Pair(null, hlsUrl)
                } else {
                    cacheUrlType(videoId, "mp4") // persist: no HLS exists for this video
                    val mp4Url = fetchSignerUrl(videoId)
                    Log.d(TAG, "Video $videoId → MP4 (discovered): $mp4Url")
                    cacheUrl(videoId, mp4Url)
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

    /** Build a direct R2 MP4 URL without signing */
    private fun fetchSignerUrl(videoId: String): String {
        val base = resolveVideoBasePath(videoId)
        return "https://cdn.genzopia.com/$base.mp4"
    }
    
    /**
     * Instant load from in-memory prefetch cache — zero network calls.
     * Returns ReelData built entirely from DataPrefetchService caches.
     * Reels whose signed URL hasn't resolved yet are still included (videoUrl = null)
     * so titles and metadata are always visible; the player will resolve the URL lazily.
     */
    @OptIn(UnstableApi::class)
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
                videoId = meta.videoId,
                videoUrl = if (isHls || url == null) null else url,
                hlsManifestUrl = if (isHls) url else null,
                title = meta.title,
                developerId = meta.userId,
                developerName = meta.developerName,
                developerPhotoUrl = meta.developerPhotoUrl,
                gameId = meta.gameId
            )
        }
        val withUrl = reels.count { it.videoUrl != null || it.hlsManifestUrl != null }
        Log.d(TAG, "Returning ${reels.size} reels from prefetch cache ($withUrl with URLs, 0 network calls)")
        return reels
    }
}
