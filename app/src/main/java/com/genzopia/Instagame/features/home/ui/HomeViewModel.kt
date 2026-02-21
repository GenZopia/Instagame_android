import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.genzopia.Instagame.ui.home.compose.HomePagingSource
import com.genzopia.Instagame.ui.home.compose.HomeVideoData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
class HomeViewModel : ViewModel() {

    // Paging flow for home videos
    val videosFlow: Flow<PagingData<HomeVideoData>> = Pager(
        config = PagingConfig(
            pageSize = 5,
            prefetchDistance = 3,
            enablePlaceholders = false,
            initialLoadSize = 5,
            maxSize = 20
        ),
        pagingSourceFactory = { HomePagingSource() }
    ).flow.cachedIn(viewModelScope)

    // Player pool for smooth scrolling
    private val playerPool = mutableMapOf<String, ExoPlayer>()
    private var currentVideoId: String? = null
    
    // Track currently playing video
    private val _currentVideoUrl = MutableStateFlow<String?>(null)
    val currentVideoUrl = _currentVideoUrl.asStateFlow()
    
    private var appContext: Context? = null

    // Initialize the player pool
    fun initializePlayer(context: Context) {
        appContext = context.applicationContext
    }

    // Get or create player for a video
    fun getPlayerForVideo(videoId: String, videoUrl: String?): ExoPlayer? {
        if (videoUrl == null) return null
        
        return playerPool.getOrPut(videoId) {
            createPlayer(videoUrl)
        }
    }
    
    private fun createPlayer(videoUrl: String): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000,  // min buffer
                50000,  // max buffer
                2500,   // playback buffer
                5000    // rebuffer
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
            }
    }

    // Preload adjacent videos for smooth scrolling
    fun preloadVideos(currentIndex: Int, videos: List<HomeVideoData>) {
        viewModelScope.launch {
            // Preload next 2 videos
            for (i in 1..2) {
                val nextIndex = currentIndex + i
                if (nextIndex < videos.size) {
                    val video = videos[nextIndex]
                    if (video.videoUrl != null && !playerPool.containsKey(video.videoId)) {
                        getPlayerForVideo(video.videoId, video.videoUrl)
                    }
                }
            }
            
            // Preload previous video
            val prevIndex = currentIndex - 1
            if (prevIndex >= 0) {
                val video = videos[prevIndex]
                if (video.videoUrl != null && !playerPool.containsKey(video.videoId)) {
                    getPlayerForVideo(video.videoId, video.videoUrl)
                }
            }
            
            // Clean up players that are too far away
            cleanupDistantPlayers(currentIndex, videos)
        }
    }
    
    private fun cleanupDistantPlayers(currentIndex: Int, videos: List<HomeVideoData>) {
        val keepRange = (currentIndex - 2)..(currentIndex + 3)
        val idsToKeep = videos.filterIndexed { index, _ -> index in keepRange }
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

    // Play video
    fun playVideo(videoId: String) {
        playerPool[videoId]?.playWhenReady = true
    }

    // Pause video
    fun pauseVideo(videoId: String) {
        playerPool[videoId]?.playWhenReady = false
    }
    
    // Pause all videos
    fun pauseAll() {
        playerPool.values.forEach { it.playWhenReady = false }
    }

    override fun onCleared() {
        super.onCleared()
        playerPool.values.forEach { it.release() }
        playerPool.clear()
    }
}