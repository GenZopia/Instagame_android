package com.genzopia.Instagame.features.home.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.genzopia.Instagame.features.home.data.FollowingRepository
import com.genzopia.Instagame.features.home.domain.FollowedUser
import com.genzopia.Instagame.utils.DataPrefetchService
import com.genzopia.Instagame.utils.GameSearchEngine
import com.genzopia.Instagame.utils.PhotoUrlResolver
import com.genzopia.Instagame.utils.ProfilePhotoUtils
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PAGE_SIZE = 20
private const val SEARCH_DEBOUNCE_MS = 300L

@OptIn(FlowPreview::class)
class HomeViewModel : ViewModel() {

    private val followingRepository = FollowingRepository()

    private val _followedUsers = MutableStateFlow<List<FollowedUser>>(emptyList())
    val followedUsers = _followedUsers.asStateFlow()
    private val _followedUsersLoading = MutableStateFlow(true)
    val followedUsersLoading = _followedUsersLoading.asStateFlow()

    private val _games = MutableStateFlow<List<HomeGameItem>>(emptyList())
    val games = _games.asStateFlow()
    private val _gamesLoading = MutableStateFlow(true)
    val gamesLoading = _gamesLoading.asStateFlow()

    // Game selection from deep link
    private val _selectedGameFromDeepLink = MutableStateFlow<HomeGameItem?>(null)
    val selectedGameFromDeepLink = _selectedGameFromDeepLink.asStateFlow()

    // true while a page fetch is in progress
    private val _loadingMore = MutableStateFlow(false)
    val loadingMore = _loadingMore.asStateFlow()

    // true when all pages have been loaded
    private val _allLoaded = MutableStateFlow(false)
    val allLoaded = _allLoaded.asStateFlow()

    // Search — debounced raw input, filtered results computed off main thread
    val searchQuery = MutableStateFlow("")

    val filteredGames = searchQuery
        .debounce(SEARCH_DEBOUNCE_MS)
        .combine(_games) { query, allGames ->
            if (query.isBlank()) allGames
            else GameSearchEngine.search(query, allGames)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ordered list of all game keys fetched from Firebase (lightweight)
    private val allGameKeys = mutableListOf<String>()
    private var nextPageIndex = 0
    private var keysReady = false

    init {
        loadFollowedUsers()
        loadGameKeys()
        // Rebuild search index whenever the game list grows
        viewModelScope.launch {
            _games.collect { games ->
                if (games.isNotEmpty()) {
                    withContext(Dispatchers.Default) {
                        GameSearchEngine.buildIndex(games)
                    }
                }
            }
        }
    }

    // No-op — kept so existing call sites compile
    fun initializePlayer(context: Context) {}

    /** Step 1: fetch only the keys (no data) — very cheap even for 100k games */
    private fun loadGameKeys() {
        _gamesLoading.value = true
        val db = FirebaseDatabase.getInstance()
        db.getReference("games")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    allGameKeys.clear()
                    for (child in snapshot.children) {
                        child.key?.let { allGameKeys.add(it) }
                    }
                    keysReady = true
                    if (allGameKeys.isEmpty()) {
                        _gamesLoading.value = false
                        _allLoaded.value = true
                    } else {
                        fetchNextPage()
                    }
                }
                override fun onCancelled(e: DatabaseError) {
                    _gamesLoading.value = false
                }
            })
    }

    /** Step 2: called on init and whenever the user scrolls near the bottom */
    fun loadMoreGames() {
        if (_loadingMore.value || _allLoaded.value || !keysReady) return
        fetchNextPage()
    }

    private fun fetchNextPage() {
        val from = nextPageIndex
        val to = minOf(from + PAGE_SIZE, allGameKeys.size)
        if (from >= allGameKeys.size) {
            _allLoaded.value = true
            _gamesLoading.value = false
            return
        }

        _loadingMore.value = true
        val pageKeys = allGameKeys.subList(from, to)
        nextPageIndex = to

        val db = FirebaseDatabase.getInstance()
        val pageList = mutableListOf<HomeGameItem>()
        var completed = 0
        val total = pageKeys.size

        fun onResolved() {
            synchronized(pageList) {
                completed++
                if (completed >= total) {
                    _games.value = _games.value + pageList
                    _loadingMore.value = false
                    _gamesLoading.value = false
                    if (nextPageIndex >= allGameKeys.size) _allLoaded.value = true
                }
            }
        }

        for (gameId in pageKeys) {
            db.getReference("games").child(gameId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(gameSnap: DataSnapshot) {
                        val gameName = gameSnap.child("game_name").getValue(String::class.java) ?: "Unknown"
                        val description = gameSnap.child("description").getValue(String::class.java) ?: ""
                        val devId = gameSnap.child("user_id").getValue(String::class.java) ?: ""
                        val photoId = gameSnap.child("photo_id").getValue(String::class.java) ?: ""

                        fun addGame(imageUrl: String, devName: String, devPhoto: String) {
                            synchronized(pageList) {
                                pageList.add(
                                    HomeGameItem(
                                        gameId, gameName, description, imageUrl, devId, devName, devPhoto
                                    )
                                )
                            }
                            onResolved()
                        }

                        fun fetchWithDev(imageUrl: String) {
                            if (devId.isEmpty()) { addGame(imageUrl, "", ""); return }
                            db.getReference("users").child(devId)
                                .addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(u: DataSnapshot) {
                                        val devName = u.child("full_name").getValue(String::class.java)
                                            ?: u.child("username").getValue(String::class.java) ?: "Developer"
                                        val devPhoto = ProfilePhotoUtils.sanitize(
                                            u.child("profile_photo_url").getValue(String::class.java) ?: ""
                                        ) ?: ""
                                        addGame(imageUrl, devName, devPhoto)
                                    }
                                    override fun onCancelled(e: DatabaseError) { addGame(imageUrl, "", "") }
                                })
                        }

                        if (photoId.isNotEmpty()) {
                            db.getReference("photos").child(photoId)
                                .addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(photoSnap: DataSnapshot) {
                                        val fileExt = photoSnap.child("file_ext").getValue(String::class.java)
                                            ?: photoSnap.child("file_name").getValue(String::class.java)
                                                ?.substringAfterLast('.', "jpg") ?: "jpg"
                                        Thread { fetchWithDev(PhotoUrlResolver.resolveSync(photoId, fileExt) ?: "") }.start()
                                    }
                                    override fun onCancelled(e: DatabaseError) { fetchWithDev("") }
                                })
                        } else fetchWithDev("")
                    }
                    override fun onCancelled(e: DatabaseError) { onResolved() }
                })
        }
    }

    private fun loadFollowedUsers() {
        val cached = DataPrefetchService.getCachedFollowedUsers()
        if (cached != null) { _followedUsers.value = cached; _followedUsersLoading.value = false; return }
        viewModelScope.launch {
            followingRepository.getFollowedUsers()
                .catch { _followedUsersLoading.value = false }
                .collect { users -> _followedUsers.value = users; _followedUsersLoading.value = false }
        }
    }

    /**
     * Open game detail sheet from deep link.
     * Fetches the game data from Firebase if not already loaded.
     */
    fun openGameByIdFromDeepLink(gameId: String) {
        // First check if game is already loaded
        val existingGame = _games.value.find { it.gameId == gameId }
        if (existingGame != null) {
            _selectedGameFromDeepLink.value = existingGame
            return
        }

        // Otherwise fetch from Firebase
        viewModelScope.launch(Dispatchers.IO) {
            val db = FirebaseDatabase.getInstance()
            db.getReference("games").child(gameId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(gameSnap: DataSnapshot) {
                        val gameName = gameSnap.child("game_name").getValue(String::class.java) ?: "Unknown"
                        val description = gameSnap.child("description").getValue(String::class.java) ?: ""
                        val devId = gameSnap.child("user_id").getValue(String::class.java) ?: ""
                        val photoId = gameSnap.child("photo_id").getValue(String::class.java) ?: ""

                        fun setGame(imageUrl: String, devName: String, devPhoto: String) {
                            _selectedGameFromDeepLink.value = HomeGameItem(
                                gameId, gameName, description, imageUrl, devId, devName, devPhoto
                            )
                        }

                        fun fetchWithDev(imageUrl: String) {
                            if (devId.isEmpty()) { setGame(imageUrl, "", ""); return }
                            db.getReference("users").child(devId)
                                .addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(u: DataSnapshot) {
                                        val devName = u.child("full_name").getValue(String::class.java)
                                            ?: u.child("username").getValue(String::class.java) ?: "Developer"
                                        val devPhoto = ProfilePhotoUtils.sanitize(
                                            u.child("profile_photo_url").getValue(String::class.java) ?: ""
                                        ) ?: ""
                                        setGame(imageUrl, devName, devPhoto)
                                    }
                                    override fun onCancelled(e: DatabaseError) { setGame(imageUrl, "", "") }
                                })
                        }

                        if (photoId.isNotEmpty()) {
                            db.getReference("photos").child(photoId)
                                .addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(photoSnap: DataSnapshot) {
                                        val fileExt = photoSnap.child("file_ext").getValue(String::class.java)
                                            ?: photoSnap.child("file_name").getValue(String::class.java)
                                                ?.substringAfterLast('.', "jpg") ?: "jpg"
                                        Thread { fetchWithDev(PhotoUrlResolver.resolveSync(photoId, fileExt) ?: "") }.start()
                                    }
                                    override fun onCancelled(e: DatabaseError) { fetchWithDev("") }
                                })
                        } else fetchWithDev("")
                    }
                    override fun onCancelled(e: DatabaseError) {
                        // Game not found or error
                        Log.e("HomeViewModel", "Failed to load game $gameId: ${e.message}")
                    }
                })
        }
    }

    fun clearSelectedGameFromDeepLink() {
        _selectedGameFromDeepLink.value = null
    }
}
