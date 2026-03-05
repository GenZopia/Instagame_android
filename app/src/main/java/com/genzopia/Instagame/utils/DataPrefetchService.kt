package com.genzopia.Instagame.utils

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
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
@UnstableApi
object DataPrefetchService {
    
    private const val TAG = "DataPrefetchService"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient()
    private val database = FirebaseDatabase.getInstance()
    
    // Cache for prefetched data
    private val videoCache = mutableMapOf<String, VideoMetadata>()
    private val signedUrlCache = mutableMapOf<String, String>()
    private var followedUsersCache: List<FollowedUser>? = null
    
    // Pre-initialized ExoPlayer for first video
    private var preloadedPlayer: ExoPlayer? = null
    private var preloadedVideoId: String? = null
    
    // Callback for prefetch completion
    private var onPrefetchComplete: (() -> Unit)? = null
    
    // Track if initial videos are ready for instant playback
    @Volatile
    private var firstVideoReady = false
    
    data class VideoMetadata(
        val videoId: String,
        val title: String,
        val userId: String,
        val gameId: String,
        val signedUrl: String? = null
    )
    
    /**
     * Check if first video is ready for instant playback
     */
    fun isFirstVideoReady(): Boolean = firstVideoReady
    
    /**
     * Get the preloaded player for the first video
     */
    fun getPreloadedPlayer(videoId: String): ExoPlayer? {
        Log.d(TAG, "getPreloadedPlayer called with videoId: $videoId")
        Log.d(TAG, "preloadedVideoId: $preloadedVideoId")
        Log.d(TAG, "Match: ${videoId == preloadedVideoId}")
        
        return if (videoId == preloadedVideoId && preloadedPlayer != null) {
            Log.d(TAG, "Returning preloaded player for video: $videoId")
            preloadedPlayer
        } else {
            Log.d(TAG, "No preloaded player match for video: $videoId")
            null
        }
    }
    
    /**
     * Get the first preloaded video ID
     */
    fun getPreloadedVideoId(): String? = preloadedVideoId
    
    /**
     * Clear the preloaded player reference (called when player is taken over by ViewModel)
     */
    fun clearPreloadedPlayer() {
        preloadedPlayer = null
        preloadedVideoId = null
    }
    
    /**
     * Start prefetching data during splash screen
     */
    fun startPrefetch(context: Context, onComplete: (() -> Unit)? = null) {
        Log.d(TAG, "Starting data prefetch...")
        onPrefetchComplete = onComplete
        
        scope.launch {
            try {
                Log.d(TAG, "Fetching followed users...")
                // Prefetch followed users for stories bar
                prefetchFollowedUsers()
                
                Log.d(TAG, "Fetching videos...")
                // Prefetch first batch of videos (more videos for instant display)
                prefetchVideos(context, 10)
                
                Log.d(TAG, "Prefetch completed successfully - invoking callback")
                
                // Notify completion on main thread
                launch(Dispatchers.Main) {
                    onPrefetchComplete?.invoke()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during prefetch", e)
                // Still notify completion even on error to prevent infinite loading
                launch(Dispatchers.Main) {
                    onPrefetchComplete?.invoke()
                }
            }
        }
    }
    
    /**
     * Prefetch videos for home feed and pre-initialize first video player
     */
    private suspend fun prefetchVideos(context: Context, count: Int) {
        try {
            Log.d(TAG, "Querying Firebase for $count videos...")
            val snapshot = database.reference
                .child("videos")
                .orderByKey()
                .limitToFirst(count)
                .get()
                .await()
            
            Log.d(TAG, "Firebase query complete, found ${snapshot.childrenCount} videos")
            
            var firstVideoId: String? = null
            var firstVideoUrl: String? = null
            
            // Fetch all signed URLs in parallel for faster prefetch
            val videoJobs = snapshot.children.mapIndexed { index, videoSnapshot ->
                val videoId = videoSnapshot.key ?: return@mapIndexed null
                val title = videoSnapshot.child("video_title").getValue(String::class.java) ?: ""
                val userId = videoSnapshot.child("user_id").getValue(String::class.java) ?: ""
                val gameId = videoSnapshot.child("game_id").getValue(String::class.java) ?: ""
                
                Pair(index, Triple(videoId, title, userId to gameId))
            }.filterNotNull()
            
            // Fetch signed URLs in parallel using coroutineScope
            kotlinx.coroutines.coroutineScope {
                val jobs = videoJobs.map { (index, data) ->
                    val (videoId, title, userGame) = data
                    launch {
                        try {
                            Log.d(TAG, "Fetching signed URL for video: $videoId")
                            val signedUrl = fetchSignedUrl(videoId)
                            
                            videoCache[videoId] = VideoMetadata(
                                videoId = videoId,
                                title = title,
                                userId = userGame.first,
                                gameId = userGame.second,
                                signedUrl = signedUrl
                            )
                            
                            // Store first video info
                            if (index == 0 && signedUrl != null) {
                                synchronized(this@DataPrefetchService) {
                                    firstVideoId = videoId
                                    firstVideoUrl = signedUrl
                                }
                                Log.d(TAG, "First video URL fetched: $videoId")
                            }
                            
                            Log.d(TAG, "Prefetched video: $videoId with URL: ${signedUrl?.take(50)}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error prefetching video $videoId", e)
                        }
                    }
                }
                
                // Wait for all URL fetches to complete
                jobs.forEach { it.join() }
            }
            
            // Now pre-initialize the first video player on main thread
            if (firstVideoId != null && firstVideoUrl != null) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    preinitializeFirstVideoPlayer(context, firstVideoId!!, firstVideoUrl!!)
                    
                    // Wait a bit for initial buffering
                    kotlinx.coroutines.delay(1000)
                    firstVideoReady = true
                }
            }
            
            Log.d(TAG, "Completed prefetching ${videoCache.size} videos")
        } catch (e: Exception) {
            Log.e(TAG, "Error prefetching videos", e)
        }
    }
    
    /**
     * Pre-initialize ExoPlayer for the first video
     */
    private fun preinitializeFirstVideoPlayer(context: Context, videoId: String, videoUrl: String) {
        try {
            Log.d(TAG, "=== PRE-INITIALIZING EXOPLAYER ===")
            Log.d(TAG, "Video ID: $videoId")
            Log.d(TAG, "Video URL: ${videoUrl.take(100)}")
            Log.d(TAG, "Context: ${context.javaClass.simpleName}")
            
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    5000,   // min buffer
                    30000,  // max buffer
                    500,    // playback buffer - very low for instant playback
                    1000    // rebuffer
                )
                .build()
            
            // Use application context to avoid memory leaks
            val appContext = context.applicationContext
            
            preloadedPlayer = ExoPlayer.Builder(appContext)
                .setLoadControl(loadControl)
                .build()
                .apply {
                    val mediaItem = MediaItem.fromUri(videoUrl)
                    setMediaItem(mediaItem)
                    repeatMode = ExoPlayer.REPEAT_MODE_ONE
                    volume = 0f  // Muted during preload
                    prepare()
                    // Don't set playWhenReady - just prepare and buffer
                    
                    Log.d(TAG, "ExoPlayer created and preparing...")
                    
                    // Add listener to track buffering progress
                    addListener(object : androidx.media3.common.Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                androidx.media3.common.Player.STATE_BUFFERING -> {
                                    Log.d(TAG, "ExoPlayer STATE_BUFFERING")
                                }
                                androidx.media3.common.Player.STATE_READY -> {
                                    Log.d(TAG, "ExoPlayer STATE_READY - Video fully buffered and ready!")
                                }
                                androidx.media3.common.Player.STATE_ENDED -> {
                                    Log.d(TAG, "ExoPlayer STATE_ENDED")
                                }
                                androidx.media3.common.Player.STATE_IDLE -> {
                                    Log.d(TAG, "ExoPlayer STATE_IDLE")
                                }
                            }
                        }
                    })
                }
            
            preloadedVideoId = videoId
            Log.d(TAG, "=== EXOPLAYER PRE-INITIALIZATION COMPLETE ===")
        } catch (e: Exception) {
            Log.e(TAG, "Error pre-initializing ExoPlayer", e)
            e.printStackTrace()
        }
    }
    
    /**
     * Fetch signed URL for video
     */
    private suspend fun fetchSignedUrl(videoId: String): String? {
        // Check cache first
        signedUrlCache[videoId]?.let { 
            Log.d(TAG, "Using cached signed URL for $videoId")
            return it 
        }
        
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
                    Log.d(TAG, "Fetched and cached signed URL for $videoId")
                    return signedUrl
                }
            }
            Log.w(TAG, "Failed to get signed URL for $videoId - no success in response")
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
     * Get all cached videos
     */
    fun getAllCachedVideos(): Map<String, VideoMetadata> {
        return videoCache.toMap()
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
        firstVideoReady = false
        
        // Release preloaded player
        preloadedPlayer?.release()
        preloadedPlayer = null
        preloadedVideoId = null
        
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
