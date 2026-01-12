package com.genzopia.Instagame.ui.home.compose

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Paging source for loading home feed videos from Firebase
 */
class HomePagingSource : PagingSource<String, HomeVideoData>() {
    
    private val database = FirebaseDatabase.getInstance()
    private val httpClient = OkHttpClient()
    
    companion object {
        private const val TAG = "HomePagingSource"
        private const val PAGE_SIZE = 10
    }
    
    override suspend fun load(params: LoadParams<String>): LoadResult<String, HomeVideoData> {
        return try {
            val startKey = params.key
            
            Log.d(TAG, "Loading page with startKey: $startKey")
            
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
                Log.d(TAG, "No more videos found")
                return LoadResult.Page(
                    data = emptyList(),
                    prevKey = null,
                    nextKey = null
                )
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
            
            // Fetch signed URLs for all videos
            videos.forEach { video ->
                try {
                    val signedUrl = fetchSignedUrl(video.videoId)
                    val index = videos.indexOf(video)
                    if (index >= 0) {
                        videos[index] = video.copy(videoUrl = signedUrl)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching signed URL for ${video.videoId}", e)
                }
            }
            
            Log.d(TAG, "Loaded ${videos.size} videos, nextKey: $lastKey")
            
            LoadResult.Page(
                data = videos,
                prevKey = null,
                nextKey = if (videos.size < PAGE_SIZE) null else lastKey
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading videos", e)
            LoadResult.Error(e)
        }
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
            val name = userSnapshot.child("name").getValue(String::class.java) 
                ?: userSnapshot.child("username").getValue(String::class.java) 
                ?: "User"
            val photoUrl = userSnapshot.child("profile_image_url").getValue(String::class.java)
                ?: userSnapshot.child("photoUrl").getValue(String::class.java)
            
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
                                continuation.resume(signedUrl)
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
