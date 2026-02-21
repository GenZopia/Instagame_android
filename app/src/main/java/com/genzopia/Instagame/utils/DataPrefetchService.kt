package com.genzopia.Instagame.utils

import android.content.Context
import android.util.Log
import com.genzopia.Instagame.features.home.domain.FollowedUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Service to prefetch video data during splash screen
 * Reduces loading time when user reaches home/reels
 */
object DataPrefetchService {
    
    private const val TAG = "DataPrefetchService"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient()
    private val database = FirebaseDatabase.getInstance()
    
    // Cache for prefetched data
    private val videoCache = mutableMapOf<String, VideoMetadata>()
    private val signedUrlCache = mutableMapOf<String, String>()
    private var followedUsersCache: List<FollowedUser>? = null
    
    data class VideoMetadata(
        val videoId: String,
        val title: String,
        val userId: String,
        val gameId: String,
        val signedUrl: String? = null
    )
    
    /**
     * Start prefetching data during splash screen
     */
    fun startPrefetch(context: Context) {
        Log.d(TAG, "Starting data prefetch...")
        
        scope.launch {
            try {
                // Prefetch followed users for stories bar
                prefetchFollowedUsers()
                
                // Prefetch first batch of videos
                prefetchVideos(5)
                
                // Prefetch first batch of reels
                prefetchReels(3)
                
                Log.d(TAG, "Prefetch completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error during prefetch", e)
            }
        }
    }
    
    /**
     * Prefetch videos for home feed
     */
    private suspend fun prefetchVideos(count: Int) {
        try {
            val snapshot = database.reference
                .child("videos")
                .orderByKey()
                .limitToFirst(count)
                .get()
                .await()
            
            for (videoSnapshot in snapshot.children) {
                val videoId = videoSnapshot.key ?: continue
                val title = videoSnapshot.child("video_title").getValue(String::class.java) ?: ""
                val userId = videoSnapshot.child("user_id").getValue(String::class.java) ?: ""
                val gameId = videoSnapshot.child("game_id").getValue(String::class.java) ?: ""
                
                // Prefetch signed URL
                val signedUrl = fetchSignedUrl(videoId)
                
                videoCache[videoId] = VideoMetadata(
                    videoId = videoId,
                    title = title,
                    userId = userId,
                    gameId = gameId,
                    signedUrl = signedUrl
                )
                
                Log.d(TAG, "Prefetched video: $videoId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error prefetching videos", e)
        }
    }
    
    /**
     * Prefetch reels for dashboard
     */
    private suspend fun prefetchReels(count: Int) {
        try {
            val snapshot = database.reference
                .child("videos")
                .orderByKey()
                .limitToFirst(count)
                .get()
                .await()
            
            for (reelSnapshot in snapshot.children) {
                val videoId = reelSnapshot.key ?: continue
                val title = reelSnapshot.child("video_title").getValue(String::class.java) ?: ""
                val userId = reelSnapshot.child("user_id").getValue(String::class.java) ?: ""
                val gameId = reelSnapshot.child("game_id").getValue(String::class.java) ?: ""
                
                // Prefetch signed URL
                val signedUrl = fetchSignedUrl(videoId)
                
                videoCache[videoId] = VideoMetadata(
                    videoId = videoId,
                    title = title,
                    userId = userId,
                    gameId = gameId,
                    signedUrl = signedUrl
                )
                
                Log.d(TAG, "Prefetched reel: $videoId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error prefetching reels", e)
        }
    }
    
    /**
     * Fetch signed URL for video
     */
    private suspend fun fetchSignedUrl(videoId: String): String? {
        // Check cache first
        signedUrlCache[videoId]?.let { return it }
        
        return try {
            val url = "https://video-signer.genzopia.workers.dev/?path=video/$videoId"
            val request = Request.Builder().url(url).build()
            
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()
            
            if (body != null) {
                val json = JSONObject(body)
                if (json.optBoolean("success")) {
                    val signedUrl = json.optString("url")
                    signedUrlCache[videoId] = signedUrl
                    return signedUrl
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching signed URL for $videoId", e)
            null
        }
    }
    
    /**
     * Get cached video metadata
     */
    fun getCachedVideo(videoId: String): VideoMetadata? {
        return videoCache[videoId]
    }
    
    /**
     * Get cached signed URL
     */
    fun getCachedSignedUrl(videoId: String): String? {
        return signedUrlCache[videoId]
    }
    
    /**
     * Check if video is cached
     */
    fun isVideoCached(videoId: String): Boolean {
        return videoCache.containsKey(videoId)
    }
    
    /**
     * Clear cache
     */
    fun clearCache() {
        videoCache.clear()
        signedUrlCache.clear()
        followedUsersCache = null
        Log.d(TAG, "Cache cleared")
    }

    /**
     * Prefetch followed users for the stories bar
     */
    private suspend fun prefetchFollowedUsers() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        try {
            val followsSnapshot = database.reference
                .child("follows")
                .child(currentUserId)
                .get()
                .await()

            val followedIds = followsSnapshot.children.mapNotNull { it.key }
            if (followedIds.isEmpty()) {
                followedUsersCache = emptyList()
                return
            }

            val users = mutableListOf<FollowedUser>()
            for (userId in followedIds) {
                try {
                    val userSnapshot = database.reference
                        .child("users")
                        .child(userId)
                        .get()
                        .await()

                    val fullName = userSnapshot.child("full_name").getValue(String::class.java)
                        ?: userSnapshot.child("name").getValue(String::class.java)
                        ?: userSnapshot.child("username").getValue(String::class.java)
                        ?: "User"

                    val profilePhotoUrl = userSnapshot.child("profile_photo_url").getValue(String::class.java)
                        ?: userSnapshot.child("profile_image_url").getValue(String::class.java)
                        ?: userSnapshot.child("photoUrl").getValue(String::class.java)

                    users.add(FollowedUser(userId, fullName, profilePhotoUrl))
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching user $userId", e)
                }
            }

            followedUsersCache = users
            Log.d(TAG, "Prefetched ${users.size} followed users")
        } catch (e: Exception) {
            Log.e(TAG, "Error prefetching followed users", e)
        }
    }

    /**
     * Get cached followed users (null = not yet loaded)
     */
    fun getCachedFollowedUsers(): List<FollowedUser>? {
        return followedUsersCache
    }
}
