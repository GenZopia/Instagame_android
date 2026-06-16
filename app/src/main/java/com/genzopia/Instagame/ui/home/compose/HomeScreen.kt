package com.genzopia.Instagame.ui.home.compose

import HomeViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.genzopia.Instagame.features.home.ui.FollowingStoriesBar
import com.genzopia.Instagame.features.home.ui.HomeGamesSection

@Composable
fun HomeScreen(viewModel: HomeViewModel, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) Color(0xFF0E0E0E) else Color(0xFFF2F2F2)
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.initializePlayer(context) }

    val followedUsers by viewModel.followedUsers.collectAsState()
    val followedUsersLoading by viewModel.followedUsersLoading.collectAsState()
    val games by viewModel.games.collectAsState()
    val filteredGames by viewModel.filteredGames.collectAsState()
    val gamesLoading by viewModel.gamesLoading.collectAsState()
    val loadingMore by viewModel.loadingMore.collectAsState()
    val allLoaded by viewModel.allLoaded.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchSuggestions by viewModel.searchSuggestions.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(bg),
        contentPadding = PaddingValues(
            top = 0.dp,
            bottom = 16.dp
        )
    ) {
        item { FollowingStoriesBar(users = followedUsers, isLoading = followedUsersLoading) }
        item {
            HomeGamesSection(
                games = games,
                filteredGames = filteredGames,
                isLoading = gamesLoading,
                loadingMore = loadingMore,
                allLoaded = allLoaded,
                searchQuery = searchQuery,
                onSearchQueryChange = { viewModel.searchQuery.value = it },
                onLoadMore = { viewModel.loadMoreGames() },
                suggestions = searchSuggestions
            )
        }
        item {
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}
