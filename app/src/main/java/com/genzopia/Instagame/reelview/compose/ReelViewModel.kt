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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

@androidx.annotation.OptIn(UnstableApi::class)
class ReelViewModel : ViewModel() {

    // Tracks how many videos in the prefetch cache now have a resolved URL.
    // ReelScreen observes this and triggers pager.refresh() when it increases,
    // so that reels which were emitted with null URLs get re-loaded with real URLs.
    private val _urlsReadyCount = MutableStateFlow(0)
    val urlsReadyCount = _urlsReadyCount.asStateFlow()

    val reelsFlow: Flow<PagingData<ReelData>> = Pager(
        config = PagingConfig(
            pageSize = 5,
            prefetchDistance = 2,
            enablePlaceholders = false,
            initialLoadSize = 3,
            maxSize = 20
        ),
        pagingSourceFactory = { ReelPagingSource() }
    ).flow.cachedIn(viewModelScope)

    /**
     * Called by ReelScreen after initializePlayer(). Polls the prefetch cache
     * until all URLs are resolved, then signals the UI to refresh the pager.
     * This is the bridge between DataPrefetchService's background Phase-2 work
     * and the Compose paging layer.
     */
    fun watchForUrlResolution() {
        viewModelScope.launch(Dispatchers.IO) {
            var lastCount = 0
            // Poll every 500 ms for up to 60 s. Stop early once all cached
            // videos have URLs (signedUrlCache count stops growing).
            repeat(120) {
                val allCached = com.genzopia.Instagame.utils.DataPrefetchService.getAllCachedVideos()
                val withUrl = allCached.keys.count { videoId ->
                    com.genzopia.Instagame.utils.DataPrefetchService.getCachedSignedUrl(videoId) != null
                }
                if (withUrl > lastCount) {
                    lastCount = withUrl
                    _urlsReadyCount.value = withUrl
                    android.util.Log.d("ReelViewModel", "URL resolution progress: $withUrl/${allCached.size}")
                }
                // Stop polling once all videos have URLs
                if (allCached.isNotEmpty() && withUrl >= allCached.size) return@launch
                delay(500)
            }
        }
    }

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

    // ── LoadControl ───────────────────────────────────────────────────────────
    // One config for all players. Preload players stay paused+muted so they
    // naturally buffer very little without needing a separate lean config.
    private fun buildLoadControl(): DefaultLoadControl =
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1500,   // min buffer before playback starts / resumes
                15000,  // max buffer
                50,     // playback start threshold — near-instant first frame
                1500    // rebuffer threshold
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

    fun initializePlayer(context: Context) {
        if (isPlayerInitialized) return
        appContext = context.applicationContext
        isPlayerInitialized = true
        // Seed followStates and likeStates from Firebase so the Follow/Like
        // buttons show the correct state immediately on first render, even when
        // tryLoadFromPrefetchCache() emits ReelData with isFollowing/isLiked=false.
        prefillUserStates()
    }

    private fun prefillUserStates() {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                ?: return@launch
            try {
                val db = com.google.firebase.database.FirebaseDatabase.getInstance().reference

                // Fetch following_list and liked_videos in parallel
                val followSnap = async { db.child("users").child(uid).child("following_list").get().await() }
                val likeSnap  = async { db.child("users").child(uid).child("liked_videos").get().await() }

                followSnap.await().children.mapNotNull { it.key }.forEach { devId ->
                    followStates[devId] = true
                }
                likeSnap.await().children.mapNotNull { it.key }.forEach { videoId ->
                    // Preserve existing like count if already tracked, default to 0
                    val existing = likeStates[videoId]
                    if (existing == null) likeStates[videoId] = Pair(true, 0)
                    else likeStates[videoId] = existing.copy(first = true)
                }

                android.util.Log.d("ReelViewModel",
                    "Pre-filled: ${followStates.size} following, ${likeStates.size} liked videos")
            } catch (e: Exception) {
                android.util.Log.e("ReelViewModel", "prefillUserStates error", e)
            }
        }
    }

    // Get or create player for a video — always returns the SAME instance for a videoId
    fun getPlayerForVideo(videoId: String, videoUrl: String?): ExoPlayer? {
        if (videoUrl == null) return null

        // Return existing player — never swap it out (that caused the frozen-frame bug)
        playerPool[videoId]?.let { return it }

        // Take ownership from prefetch service if available
        val preloaded = com.genzopia.Instagame.utils.DataPrefetchService.getPreloadedPlayer(videoId)
        if (preloaded != null && preloaded.playbackState != Player.STATE_IDLE) {
            com.genzopia.Instagame.utils.DataPrefetchService.removeFromPool(videoId)
            com.genzopia.Instagame.utils.DataPrefetchService.clearPreloadedPlayer()
            preloaded.volume = 0f
            preloaded.playWhenReady = false
            attachErrorRecovery(preloaded, videoId, videoUrl)
            playerPool[videoId] = preloaded
            android.util.Log.d("ReelViewModel", "Took prefetched player for $videoId")
            return preloaded
        } else if (preloaded != null) {
            preloaded.release()
            com.genzopia.Instagame.utils.DataPrefetchService.removeFromPool(videoId)
            com.genzopia.Instagame.utils.DataPrefetchService.clearPreloadedPlayer()
        }

        android.util.Log.d("ReelViewModel", "Creating player for $videoId")
        return createPlayer(videoId, videoUrl)
    }

    private fun createPlayer(videoId: String, videoUrl: String): ExoPlayer {
        val player = ExoPlayer.Builder(appContext!!)
            .setLoadControl(buildLoadControl())
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(videoUrl))
                repeatMode = ExoPlayer.REPEAT_MODE_ONE
                volume = 0f            // muted until activated
                playWhenReady = false  // paused until activated
                setSeekParameters(SeekParameters.CLOSEST_SYNC)
                prepare()
            }
        attachErrorRecovery(player, videoId, videoUrl)
        playerPool[videoId] = player
        return player
    }

    private fun attachErrorRecovery(player: ExoPlayer, videoId: String, videoUrl: String) {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("ReelViewModel", "Player error $videoId: ${error.message}")
                playerPool.remove(videoId)
                player.removeListener(this)
                player.release()

                val fresh = createPlayer(videoId, videoUrl)
                if (currentVideoId == videoId) {
                    fresh.volume = 1f
                    fresh.playWhenReady = true
                }
            }
        })
    }

    fun preloadVideos(currentIndex: Int, reels: List<ReelData>) {
        viewModelScope.launch {
            val indicesToPreload = buildList {
                for (i in 1..3) add(currentIndex + i) // next 3
                for (i in 1..2) add(currentIndex - i) // prev 2
            }
            for (index in indicesToPreload) {
                if (index in reels.indices) {
                    val reel = reels[index]
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
        val keepRange = (currentIndex - 3)..(currentIndex + 4)
        val idsToKeep = reels.filterIndexed { index, _ -> index in keepRange }
            .map { it.videoId }.toSet()

        playerPool.keys.toList().forEach { videoId ->
            if (videoId !in idsToKeep) {
                playerPool[videoId]?.release()
                playerPool.remove(videoId)
            }
        }
    }

    fun setCurrentVideo(videoId: String, videoUrl: String?) {
        if (currentVideoId == videoId) return

        // Mute + pause the previous video
        currentVideoId?.let { prevId ->
            playerPool[prevId]?.let { prev ->
                prev.playWhenReady = false
                prev.volume = 0f
            }
        }

        currentVideoId = videoId
        _currentVideoUrl.value = videoUrl
    }

    fun playVideo(videoId: String) {
        playerPool[videoId]?.let {
            it.volume = 1f
            it.playWhenReady = true
        }
    }

    fun pauseVideo(videoId: String) {
        playerPool[videoId]?.playWhenReady = false
    }

    fun pauseAll() {
        playerPool.values.forEach {
            it.playWhenReady = false
            it.volume = 0f
        }
    }

    fun getFollowState(developerId: String, defaultState: Boolean) =
        followStates[developerId] ?: defaultState

    fun updateFollowState(developerId: String, isFollowing: Boolean) {
        followStates[developerId] = isFollowing
    }

    fun getLikeState(videoId: String, defaultIsLiked: Boolean, defaultLikeCount: Int): Pair<Boolean, Int> {
        val cached = likeStates[videoId] ?: return Pair(defaultIsLiked, defaultLikeCount)
        // If we prefilled isLiked=true but count=0, use the real count from the reel
        val count = if (cached.second == 0 && defaultLikeCount > 0) defaultLikeCount else cached.second
        return Pair(cached.first, count)
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
