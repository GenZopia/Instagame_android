import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.genzopia.Instagame.reelview.compose.ReelData
import com.genzopia.Instagame.reelview.compose.ReelPagingSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@androidx.annotation.OptIn(UnstableApi::class)
class ReelViewModel : ViewModel() {

    val reelsFlow: Flow<PagingData<ReelData>> = Pager(
        config = PagingConfig(
            pageSize = 5,
            prefetchDistance = 2,
            enablePlaceholders = false,
            initialLoadSize = 3,  // Reduced from 5 to show content faster
            maxSize = 20
        ),
        pagingSourceFactory = { ReelPagingSource() }
    ).flow.cachedIn(viewModelScope)

    // Player pool for smooth transitions
    private val playerPool = mutableMapOf<String, ExoPlayer>()
    private var currentVideoId: String? = null
    
    // Track currently playing video
    private val _currentVideoUrl = MutableStateFlow<String?>(null)
    val currentVideoUrl = _currentVideoUrl.asStateFlow()
    
    // Track follow states (developerId -> isFollowing)
    private val followStates = mutableMapOf<String, Boolean>()
    
    // Track like states (videoId -> Pair(isLiked, likeCount))
    private val likeStates = mutableMapOf<String, Pair<Boolean, Int>>()
    
    private var appContext: Context? = null
    private var isPlayerInitialized = false

    // Initialize the player pool
    fun initializePlayer(context: Context) {
        if (isPlayerInitialized) return
        appContext = context.applicationContext
        isPlayerInitialized = true
    }

    // Get or create player for a video
    fun getPlayerForVideo(videoId: String, videoUrl: String?): ExoPlayer? {
        if (videoUrl == null) return null

        // Return from our own pool first (already taken ownership)
        playerPool[videoId]?.let { return it }

        // Check prefetch pool — take ownership
        val preloaded = com.genzopia.Instagame.utils.DataPrefetchService.getPreloadedPlayer(videoId)
        if (preloaded != null && preloaded.playbackState != Player.STATE_IDLE) {
            com.genzopia.Instagame.utils.DataPrefetchService.removeFromPool(videoId)
            com.genzopia.Instagame.utils.DataPrefetchService.clearPreloadedPlayer()
            preloaded.volume = 1f
            attachErrorRecovery(preloaded, videoId, videoUrl)
            playerPool[videoId] = preloaded
            android.util.Log.d("ReelViewModel", "Prefetched player ready for $videoId state=${preloaded.playbackState}")
            return preloaded
        } else if (preloaded != null) {
            preloaded.release()
            com.genzopia.Instagame.utils.DataPrefetchService.removeFromPool(videoId)
            com.genzopia.Instagame.utils.DataPrefetchService.clearPreloadedPlayer()
        }

        // Fallback: create fresh player — must run on main thread
        android.util.Log.d("ReelViewModel", "Creating fresh player for $videoId")
        return playerPool.getOrPut(videoId) { createPlayer(videoId, videoUrl) }
    }

    private fun createPlayer(videoId: String, videoUrl: String): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                2000,   // min buffer — reduced from 5000
                30000,  // max buffer
                100,    // playback start threshold — reduced from 500 for instant start (Option 1)
                500     // rebuffer threshold — reduced from 1000 (Option 1)
            )
            .build()

        return ExoPlayer.Builder(appContext!!)
            .setLoadControl(loadControl)
            .build()
            .apply {
                val mediaItem = MediaItem.fromUri(videoUrl)
                setMediaItem(mediaItem)
                repeatMode = ExoPlayer.REPEAT_MODE_ONE
                volume = 1f
                // Option 2: CLOSEST_SYNC seek — snaps to nearest keyframe instantly
                // avoids decoding delay when seeking or starting mid-stream
                setSeekParameters(SeekParameters.CLOSEST_SYNC)
                prepare()
                attachErrorRecovery(this, videoId, videoUrl)
            }
    }

    /**
     * Attaches an error listener that recovers from codec crashes (e.g. AAC decoder failure)
     * by releasing the broken player and creating a fresh one for the same video.
     */
    private fun attachErrorRecovery(player: ExoPlayer, videoId: String, videoUrl: String) {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("ReelViewModel", "Player error for $videoId: ${error.message}")
                // Remove the broken player from the pool
                playerPool.remove(videoId)
                player.removeListener(this)
                player.release()

                // Recreate a fresh player and put it back in the pool
                val fresh = createPlayer(videoId, videoUrl)
                playerPool[videoId] = fresh

                // If this was the currently playing video, resume playback
                if (currentVideoId == videoId) {
                    fresh.playWhenReady = true
                }
            }
        })
    }

    fun preloadVideos(currentIndex: Int, reels: List<ReelData>) {
        viewModelScope.launch {
            // Preload next 10 reels
            for (i in 1..10) {
                val nextIndex = currentIndex + i
                if (nextIndex < reels.size) {
                    val reel = reels[nextIndex]
                    if (reel.playbackUrl != null && !playerPool.containsKey(reel.videoId)) {
                        withContext(Dispatchers.Main) {
                            getPlayerForVideo(reel.videoId, reel.playbackUrl)
                        }
                    }
                }
            }
            // Preload previous 10 reels
            for (i in 1..10) {
                val prevIndex = currentIndex - i
                if (prevIndex >= 0) {
                    val reel = reels[prevIndex]
                    if (reel.playbackUrl != null && !playerPool.containsKey(reel.videoId)) {
                        withContext(Dispatchers.Main) {
                            getPlayerForVideo(reel.videoId, reel.playbackUrl)
                        }
                    }
                }
            }
            cleanupDistantPlayers(currentIndex, reels)
        }
    }
    
    private fun cleanupDistantPlayers(currentIndex: Int, reels: List<ReelData>) {
        val keepRange = (currentIndex - 10)..(currentIndex + 10)
        val idsToKeep = reels.filterIndexed { index, _ -> index in keepRange }
            .map { it.videoId }
            .toSet()
        
        playerPool.keys.toList().forEach { videoId ->
            if (videoId !in idsToKeep) {
                playerPool[videoId]?.release()
                playerPool.remove(videoId)
            }
        }
    }

    // Set current playing video
    fun setCurrentVideo(videoId: String, videoUrl: String?) {
        if (currentVideoId == videoId) return
        
        // Pause previous video
        currentVideoId?.let { prevId ->
            playerPool[prevId]?.playWhenReady = false
        }
        
        currentVideoId = videoId
        _currentVideoUrl.value = videoUrl
    }

    // Play current video
    fun playVideo(videoId: String) {
        playerPool[videoId]?.playWhenReady = true
    }

    // Pause current video
    fun pauseVideo(videoId: String) {
        playerPool[videoId]?.playWhenReady = false
    }
    
    // Pause all videos
    fun pauseAll() {
        playerPool.values.forEach { it.playWhenReady = false }
    }
    
    // Follow state management
    fun getFollowState(developerId: String, defaultState: Boolean): Boolean {
        return followStates[developerId] ?: defaultState
    }
    
    fun updateFollowState(developerId: String, isFollowing: Boolean) {
        followStates[developerId] = isFollowing
    }
    
    // Like state management
    fun getLikeState(videoId: String, defaultIsLiked: Boolean, defaultLikeCount: Int): Pair<Boolean, Int> {
        return likeStates[videoId] ?: Pair(defaultIsLiked, defaultLikeCount)
    }
    
    fun updateLikeState(videoId: String, isLiked: Boolean, likeCount: Int) {
        likeStates[videoId] = Pair(isLiked, likeCount)
    }

    override fun onCleared() {
        super.onCleared()
        playerPool.values.forEach { it.release() }
        playerPool.clear()
        followStates.clear()
        likeStates.clear()
    }
}