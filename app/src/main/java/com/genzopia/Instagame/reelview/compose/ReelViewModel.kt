import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
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

        // Check if we have a preloaded player for this video
        val preloaded = com.genzopia.Instagame.utils.DataPrefetchService.getPreloadedPlayer(videoId)
        if (preloaded != null) {
            android.util.Log.d("ReelViewModel", "=== USING PRELOADED PLAYER ===")
            android.util.Log.d("ReelViewModel", "Video ID: $videoId, state: ${preloaded.playbackState}")

            // Only accept the preloaded player if it's in a usable state (not IDLE/ERROR)
            if (preloaded.playbackState != Player.STATE_IDLE) {
                com.genzopia.Instagame.utils.DataPrefetchService.clearPreloadedPlayer()
                preloaded.volume = 1f
                attachErrorRecovery(preloaded, videoId, videoUrl)
                playerPool[videoId] = preloaded
                android.util.Log.d("ReelViewModel", "Preloaded player accepted")
                return preloaded
            } else {
                // Preloaded player is in a bad state — release it and fall through to create a fresh one
                android.util.Log.w("ReelViewModel", "Preloaded player in IDLE state, discarding")
                preloaded.release()
                com.genzopia.Instagame.utils.DataPrefetchService.clearPreloadedPlayer()
            }
        }

        android.util.Log.d("ReelViewModel", "No preloaded player for $videoId, creating new one")
        return playerPool.getOrPut(videoId) {
            createPlayer(videoId, videoUrl)
        }
    }

    private fun createPlayer(videoId: String, videoUrl: String): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                5000,   // min buffer
                30000,  // max buffer
                500,    // playback buffer - low for instant start
                1000    // rebuffer
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

    // Preload adjacent videos for smooth scrolling
    fun preloadVideos(currentIndex: Int, reels: List<ReelData>) {
        viewModelScope.launch {
            // Preload next 2 videos
            for (i in 1..2) {
                val nextIndex = currentIndex + i
                if (nextIndex < reels.size) {
                    val reel = reels[nextIndex]
                    if (reel.videoUrl != null && !playerPool.containsKey(reel.videoId)) {
                        getPlayerForVideo(reel.videoId, reel.videoUrl)
                    }
                }
            }
            
            // Preload previous video
            val prevIndex = currentIndex - 1
            if (prevIndex >= 0) {
                val reel = reels[prevIndex]
                if (reel.videoUrl != null && !playerPool.containsKey(reel.videoId)) {
                    getPlayerForVideo(reel.videoId, reel.videoUrl)
                }
            }
            
            // Clean up players that are too far away
            cleanupDistantPlayers(currentIndex, reels)
        }
    }
    
    private fun cleanupDistantPlayers(currentIndex: Int, reels: List<ReelData>) {
        val keepRange = (currentIndex - 2)..(currentIndex + 3)
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