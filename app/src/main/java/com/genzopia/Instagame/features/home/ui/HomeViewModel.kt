import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
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

class HomeViewModel : ViewModel() {

    // Paging flow for home videos (unchanged)
    val videosFlow: Flow<PagingData<HomeVideoData>> = Pager(
        config = PagingConfig(
            pageSize = 5,
            prefetchDistance = 5,
            enablePlaceholders = false,
            initialLoadSize = 5,
            maxSize = 50
        ),
        pagingSourceFactory = { HomePagingSource() }
    ).flow.cachedIn(viewModelScope)

    // Shared ExoPlayer instance
    private var _exoPlayer: ExoPlayer? = null
    val exoPlayer: ExoPlayer
        get() = _exoPlayer ?: throw IllegalStateException("Player not initialized. Call initializePlayer() first.")

    // Track currently playing video URL
    private val _currentVideoUrl = MutableStateFlow<String?>(null)
    val currentVideoUrl = _currentVideoUrl.asStateFlow()

    // Initialize the player (call from the screen when context is available)
    fun initializePlayer(context: Context) {
        if (_exoPlayer == null) {
            _exoPlayer = ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(context))
                .build()
                .apply {
                    repeatMode = ExoPlayer.REPEAT_MODE_ONE   // or OFF, depending on your needs
                    volume = 1f
                }
        }
    }

    // Set the current video URL and prepare the player
    fun setCurrentVideo(url: String?) {
        _currentVideoUrl.value = url
        url?.let {
            val mediaItem = MediaItem.fromUri(it)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        }
    }

    // Play/pause controls
    fun play() {
        exoPlayer.playWhenReady = true
    }

    fun pause() {
        exoPlayer.playWhenReady = false
    }

    override fun onCleared() {
        super.onCleared()
        _exoPlayer?.release()
        _exoPlayer = null
    }
}