import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.genzopia.Instagame.features.home.data.FollowingRepository
import com.genzopia.Instagame.features.home.domain.FollowedUser
import com.genzopia.Instagame.ui.home.compose.HomePagingSource
import com.genzopia.Instagame.ui.home.compose.HomeVideoData
import com.genzopia.Instagame.utils.DataPrefetchService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
class HomeViewModel : ViewModel() {

    // Paging flow for following videos only (Instagram style - default feed)
    val followingVideosFlow: Flow<PagingData<HomeVideoData>> = Pager(
        config = PagingConfig(
            pageSize = 10,          // Match PAGE_SIZE in HomePagingSource
            prefetchDistance = 5,    // Prefetch more for smooth scrolling
            enablePlaceholders = false,
            initialLoadSize = 10,    // Start with 10 videos
            maxSize = 100            // Cache up to 100 videos (increased for filtered content)
        ),
        pagingSourceFactory = { HomePagingSource(showOnlyFollowed = true) }
    ).flow.cachedIn(viewModelScope)

    // For backward compatibility, expose the original videosFlow (now shows following only)
    val videosFlow: Flow<PagingData<HomeVideoData>> = followingVideosFlow

    // All videos flow - kept for potential future use
    val allVideosFlow: Flow<PagingData<HomeVideoData>> = Pager(
        config = PagingConfig(
            pageSize = 10,
            prefetchDistance = 5,
            enablePlaceholders = false,
            initialLoadSize = 10,
            maxSize = 100
        ),
        pagingSourceFactory = { HomePagingSource(showOnlyFollowed = false) }
    ).flow.cachedIn(viewModelScope)


    // Followed users for the stories bar
    private val followingRepository = FollowingRepository()
    private val _followedUsers = MutableStateFlow<List<FollowedUser>>(emptyList())
    val followedUsers = _followedUsers.asStateFlow()
    private val _followedUsersLoading = MutableStateFlow(true)
    val followedUsersLoading = _followedUsersLoading.asStateFlow()

    // Games list — loaded once, survives fragment navigation
    private val _games = MutableStateFlow<List<com.genzopia.Instagame.features.home.ui.HomeGameItem>>(emptyList())
    val games = _games.asStateFlow()
    private val _gamesLoading = MutableStateFlow(true)
    val gamesLoading = _gamesLoading.asStateFlow()

    init {
        loadFollowedUsers()
        loadGames()
    }

    fun loadGames() {
        // Already loaded — don't reload
        if (_games.value.isNotEmpty()) return

        _gamesLoading.value = true
        val db = com.google.firebase.database.FirebaseDatabase.getInstance()

        db.getReference("games").addListenerForSingleValueEvent(object :
            com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val list = mutableListOf<com.genzopia.Instagame.features.home.ui.HomeGameItem>()
                var pending = snapshot.childrenCount.toInt()
                if (pending == 0) { _gamesLoading.value = false; return }

                for (gameSnap in snapshot.children) {
                    val gameId = gameSnap.key
                    if (gameId == null) {
                        pending--
                        if (pending == 0) { _games.value = list.toList(); _gamesLoading.value = false }
                        continue
                    }
                    val gameName = gameSnap.child("game_name").getValue(String::class.java) ?: "Unknown"
                    val description = gameSnap.child("description").getValue(String::class.java) ?: ""
                    val devId = gameSnap.child("user_id").getValue(String::class.java) ?: ""
                    val photoId = gameSnap.child("photo_id").getValue(String::class.java) ?: ""

                    fun addGame(imageUrl: String, devName: String, devPhoto: String) {
                        synchronized(list) {
                            list.add(com.genzopia.Instagame.features.home.ui.HomeGameItem(gameId, gameName, description, imageUrl, devId, devName, devPhoto))
                            pending--
                            if (pending == 0) { _games.value = list.toList(); _gamesLoading.value = false }
                        }
                    }

                    fun fetchWithPhoto(imageUrl: String) {
                        if (devId.isEmpty()) { addGame(imageUrl, "", ""); return }
                        db.getReference("users").child(devId)
                            .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                                override fun onDataChange(userSnap: com.google.firebase.database.DataSnapshot) {
                                    val devName = userSnap.child("full_name").getValue(String::class.java)
                                        ?: userSnap.child("username").getValue(String::class.java) ?: "Developer"
                                    val rawDevPhoto = userSnap.child("profile_photo_url").getValue(String::class.java) ?: ""
                                    val devPhoto = com.genzopia.Instagame.utils.ProfilePhotoUtils.sanitize(rawDevPhoto) ?: ""
                                    addGame(imageUrl, devName, devPhoto)
                                }
                                override fun onCancelled(e: com.google.firebase.database.DatabaseError) { addGame(imageUrl, "", "") }
                            })
                    }

                    if (photoId.isNotEmpty()) {
                        db.getReference("photos").child(photoId)
                            .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                                override fun onDataChange(photoSnap: com.google.firebase.database.DataSnapshot) {
                                    val fileExt = photoSnap.child("file_ext").getValue(String::class.java)
                                        ?: photoSnap.child("file_name").getValue(String::class.java)
                                            ?.substringAfterLast('.', "jpg")
                                        ?: "jpg"
                                    Thread {
                                        val signedUrl = com.genzopia.Instagame.utils.PhotoUrlResolver.resolveSync(photoId, fileExt)
                                        fetchWithPhoto(signedUrl ?: "")
                                    }.start()
                                }
                                override fun onCancelled(e: com.google.firebase.database.DatabaseError) { fetchWithPhoto("") }
                            })
                    } else {
                        fetchWithPhoto("")
                    }
                }
            }
            override fun onCancelled(e: com.google.firebase.database.DatabaseError) {
                _gamesLoading.value = false
            }
        })
    }

    private fun loadFollowedUsers() {
        // Check prefetch cache first (loaded during splash screen)
        val cached = DataPrefetchService.getCachedFollowedUsers()
        if (cached != null) {
            _followedUsers.value = cached
            _followedUsersLoading.value = false
            return
        }

        // Fallback: fetch fresh if cache wasn't ready
        viewModelScope.launch {
            followingRepository.getFollowedUsers()
                .catch { _followedUsersLoading.value = false }
                .collect { users ->
                    _followedUsers.value = users
                    _followedUsersLoading.value = false
                }
        }
    }

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