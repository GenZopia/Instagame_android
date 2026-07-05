package com.genzopia.Instagame.features.home.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.genzopia.Instagame.features.home.data.FollowingRepository
import com.genzopia.Instagame.features.home.domain.FollowedUser
import com.genzopia.Instagame.gateway.GatewayClient
import com.genzopia.Instagame.utils.DataPrefetchService
import com.genzopia.Instagame.utils.GameSearchEngine
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

    private val _selectedGameFromDeepLink = MutableStateFlow<HomeGameItem?>(null)
    val selectedGameFromDeepLink = _selectedGameFromDeepLink.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore = _loadingMore.asStateFlow()

    private val _allLoaded = MutableStateFlow(false)
    val allLoaded = _allLoaded.asStateFlow()

    val searchQuery = MutableStateFlow("")

    val filteredGames = searchQuery
        .debounce(SEARCH_DEBOUNCE_MS)
        .combine(_games) { query, allGames ->
            if (query.isBlank()) allGames
            else GameSearchEngine.search(query, allGames)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        loadFollowedUsers()
        loadGames()
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

    fun initializePlayer(context: Context) {}

    /** Fetch all games via gateway — no direct Firebase reads */
    private fun loadGames() {
        _gamesLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resp = GatewayClient.api.getGames()
                if (resp.isSuccessful) {
                    val items = resp.body()?.data?.map { g ->
                        HomeGameItem(
                            gameId = g.gameId,
                            gameName = g.gameName,
                            description = g.description,
                            imageUrl = g.imageUrl,
                            developerId = g.developerId,
                            developerName = g.developerName,
                            developerPhotoUrl = g.developerPhotoUrl
                        )
                    } ?: emptyList()
                    _games.value = items
                    _allLoaded.value = true
                } else {
                    Log.e("HomeViewModel", "getGames HTTP ${resp.code()}")
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "loadGames error: ${e.message}", e)
            } finally {
                _gamesLoading.value = false
                _loadingMore.value = false
            }
        }
    }

    /** No-op — all games loaded in one call from gateway */
    fun loadMoreGames() {}

    private fun loadFollowedUsers() {
        val cached = DataPrefetchService.getCachedFollowedUsers()
        if (cached != null) { _followedUsers.value = cached; _followedUsersLoading.value = false; return }
        viewModelScope.launch {
            followingRepository.getFollowedUsers()
                .catch { _followedUsersLoading.value = false }
                .collect { users -> _followedUsers.value = users; _followedUsersLoading.value = false }
        }
    }

    /** Open game from deep link — check loaded list first, else fetch via gateway */
    fun openGameByIdFromDeepLink(gameId: String) {
        val existing = _games.value.find { it.gameId == gameId }
        if (existing != null) { _selectedGameFromDeepLink.value = existing; return }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resp = GatewayClient.api.getGames()
                if (resp.isSuccessful) {
                    val found = resp.body()?.data?.find { it.gameId == gameId }
                    found?.let {
                        _selectedGameFromDeepLink.value = HomeGameItem(
                            it.gameId, it.gameName, it.description,
                            it.imageUrl, it.developerId, it.developerName, it.developerPhotoUrl
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "openGameByIdFromDeepLink error: ${e.message}", e)
            }
        }
    }

    fun clearSelectedGameFromDeepLink() {
        _selectedGameFromDeepLink.value = null
    }
}
