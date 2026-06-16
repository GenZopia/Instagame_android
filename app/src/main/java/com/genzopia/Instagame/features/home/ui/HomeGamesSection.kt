package com.genzopia.Instagame.features.home.ui

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.genzopia.Instagame.channel_view.ChannelActivity
import com.genzopia.Instagame.webgl_gameloading.Game_mode

private val Orange = Color(0xFFFF6B35)

data class HomeGameItem(
    val gameId: String,
    val gameName: String,
    val description: String,
    val imageUrl: String,
    val developerId: String,
    val developerName: String,
    val developerPhotoUrl: String,
    val totalPlays: String = "—",
    val categoryTags: List<String> = emptyList()
)

@Composable
fun HomeGamesSection(
    games: List<HomeGameItem>,
    filteredGames: List<HomeGameItem>,
    isLoading: Boolean,
    loadingMore: Boolean = false,
    allLoaded: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onLoadMore: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) Color(0xFF0E0E0E) else Color(0xFFFAFAFA)
    val textColor = if (isDark) Color(0xFFE0E0E0) else Color(0xFF1A1A1A)
    val subColor = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575)

    var selectedGame by remember { mutableStateOf<HomeGameItem?>(null) }

    if (isLoading && games.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Orange, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth().background(bg)) {

        // Section header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Orange)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "All Games",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = textColor,
                letterSpacing = 0.3.sp
            )
            Spacer(Modifier.weight(1f))
            // Show filtered count when searching
            val countLabel = if (searchQuery.isNotBlank())
                "${filteredGames.size} of ${games.size}"
            else
                "${games.size} games"
            Text(countLabel, fontSize = 12.sp, color = subColor)
        }

        // Smart search bar
        SmartSearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            isDark = isDark,
            textColor = textColor,
            subColor = subColor
        )

        Spacer(Modifier.height(8.dp))

        // Grid wrapped in AnimatedContent so result changes fade smoothly
        AnimatedContent(
            targetState = filteredGames,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "games_grid"
        ) { displayGames ->
            val gridState = rememberLazyGridState()

            val shouldLoadMore = remember {
                derivedStateOf {
                    if (searchQuery.isNotBlank()) return@derivedStateOf false
                    val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    val total = gridState.layoutInfo.totalItemsCount
                    total > 0 && lastVisible >= total - 4
                }
            }
            LaunchedEffect(shouldLoadMore.value) {
                if (shouldLoadMore.value) onLoadMore()
            }

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 10000.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                userScrollEnabled = false
            ) {
                items(displayGames, key = { it.gameId }) { game ->
                    val position = displayGames.indexOf(game)
                    GameCard(
                        game = game,
                        isDark = isDark,
                        textColor = textColor,
                        subColor = subColor,
                        onClick = {
                            com.genzopia.Instagame.analytics.InstagameAnalytics.trackHomeGameCardTapped(
                                gameId = game.gameId,
                                gameName = game.gameName,
                                positionInList = position
                            )
                            selectedGame = game
                        }
                    )
                }
            }
        }

        // Empty search state
        if (filteredGames.isEmpty() && searchQuery.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎮", fontSize = 36.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No games found for",
                        fontSize = 14.sp,
                        color = subColor
                    )
                    Text(
                        "\"$searchQuery\"",
                        fontSize = 14.sp,
                        color = textColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Try a different name or keyword",
                        fontSize = 12.sp,
                        color = subColor
                    )
                }
            }
        }

        // Load-more footer (only shown when not searching)
        if (searchQuery.isBlank()) {
            if (loadingMore) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Orange, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
            } else if (allLoaded && games.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("All ${games.size} games loaded", fontSize = 12.sp, color = subColor)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    selectedGame?.let { game ->
        LaunchedEffect(game.gameId) {
            com.genzopia.Instagame.analytics.InstagameAnalytics.trackGameDetailSheetOpened(
                gameId = game.gameId,
                gameName = game.gameName,
                source = "home_card"
            )
        }
        GameDetailSheet(
            game = game,
            relatedGames = (if (searchQuery.isBlank()) games else filteredGames)
                .filter { it.gameId != game.gameId }
                .take(8),
            onDismiss = { didPlay ->
                com.genzopia.Instagame.analytics.InstagameAnalytics.trackGameDetailSheetDismissed(
                    gameId = game.gameId,
                    gameName = game.gameName,
                    didPlay = didPlay
                )
                selectedGame = null
            }
        )
    }
}

// ── Search Bar ────────────────────────────────────────────────────────────────
// Local state handles every keystroke instantly — ViewModel only gets notified
// after the user pauses (via LaunchedEffect + snapshotFlow), so the grid never
// recomposes while typing.
@Composable
private fun SmartSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isDark: Boolean,
    textColor: Color,
    subColor: Color
) {
    // Local text state — updates immediately, no upstream recomposition
    var localQuery by remember { mutableStateOf(query) }
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }

    // Sync local → ViewModel only when local value actually changes
    LaunchedEffect(localQuery) {
        onQueryChange(localQuery)
    }

    // If parent clears the query (e.g. clear button), sync back down
    LaunchedEffect(query) {
        if (query != localQuery) localQuery = query
    }

    val searchBg = if (isDark) Color(0xFF2A2A2A) else Color(0xFFEEEEEE)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(searchBg)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Search",
                tint = if (isFocused) Orange else subColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = localQuery,
                onValueChange = { localQuery = it },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isFocused = it.isFocused },
                singleLine = true,
                textStyle = TextStyle(
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                ),
                cursorBrush = SolidColor(Orange),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    if (localQuery.isNotBlank()) {
                        com.genzopia.Instagame.analytics.InstagameAnalytics.trackHomeSearchUsed(
                            query = localQuery,
                            resultsCount = 0 // count not available here; ViewModel filters async
                        )
                    }
                }),
                decorationBox = { innerTextField ->
                    if (localQuery.isEmpty()) {
                        Text("Search", fontSize = 13.sp, color = subColor)
                    }
                    innerTextField()
                }
            )
            AnimatedVisibility(
                visible = localQuery.isNotEmpty(),
                enter = fadeIn(tween(150)) + scaleIn(tween(150)),
                exit = fadeOut(tween(100))
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Clear search",
                    tint = subColor,
                    modifier = Modifier
                        .size(15.dp)
                        .clickable {
                            localQuery = ""
                            focusManager.clearFocus()
                        }
                )
            }
        }
    }
}

@Composable
fun HomeGamesSectionPreview() {
    val sampleGames = listOf(
        HomeGameItem(
            gameId = "1",
            gameName = "Super Runner",
            description = "An endless runner game with amazing graphics and smooth controls. Collect coins and avoid obstacles to set high scores.",
            imageUrl = "https://images.unsplash.com/photo-1550745165-9bc0b252726f?auto=format&fit=crop&w=400&q=80",
            developerId = "d1",
            developerName = "Pixel Studio",
            developerPhotoUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=100&q=80"
        ),
        HomeGameItem(
            gameId = "2",
            gameName = "Magic Quest",
            description = "Embark on an epic journey through mystical lands. Solve puzzles, fight monsters, and discover hidden treasures in this RPG adventure.",
            imageUrl = "https://images.unsplash.com/photo-1542751371-adc38448a05e?auto=format&fit=crop&w=400&q=80",
            developerId = "d2",
            developerName = "Game Wizards",
            developerPhotoUrl = "https://images.unsplash.com/photo-1527980965255-d3b416303d12?auto=format&fit=crop&w=100&q=80"
        ),
        HomeGameItem(
            gameId = "3",
            gameName = "Space Wars",
            description = "Defend the galaxy from alien invaders. Upgrade your spaceship and master advanced weaponry in this fast-paced shooter.",
            imageUrl = "https://images.unsplash.com/photo-1614732414444-096e5f1122d5?auto=format&fit=crop&w=400&q=80",
            developerId = "d3",
            developerName = "Astro Games",
            developerPhotoUrl = "https://images.unsplash.com/photo-1599566150163-29194dcaad36?auto=format&fit=crop&w=100&q=80"
        ),
        HomeGameItem(
            gameId = "4",
            gameName = "Puzzle Master",
            description = "Test your brain with hundreds of challenging puzzles. Simple to learn, hard to master. Perfect for quick gaming sessions.",
            imageUrl = "https://images.unsplash.com/photo-1516280440614-37939bbacd81?auto=format&fit=crop&w=400&q=80",
            developerId = "d4",
            developerName = "Logic Lab",
            developerPhotoUrl = "https://images.unsplash.com/photo-1580489944761-15a19d654956?auto=format&fit=crop&w=100&q=80"
        )
    )

    MaterialTheme {
        Surface {
            HomeGamesSection(
                games = sampleGames,
                filteredGames = sampleGames,
                isLoading = false,
                loadingMore = false,
                allLoaded = true,
                searchQuery = "",
                onSearchQueryChange = {}
            )
        }
    }
}

// ── Game Card ─────────────────────────────────────────────────────────────────

@Composable
private fun GameCard(
    game: HomeGameItem,
    isDark: Boolean,
    textColor: Color,
    subColor: Color,
    onClick: () -> Unit
) {
    val cardBg = if (isDark) Color(0xFF1A1A1A) else Color.White

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 3.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(game.imageUrl.ifEmpty { null })
                        .crossfade(true)
                        .build(),
                    contentDescription = game.gameName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(28.dp)
                        .background(Orange, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = game.gameName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (game.developerName.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = game.developerName,
                        fontSize = 11.sp,
                        color = subColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
// ── Game Detail Sheet ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameDetailSheet(
    game: HomeGameItem,
    relatedGames: List<HomeGameItem> = emptyList(),
    onDismiss: (didPlay: Boolean) -> Unit
) {
    val context = LocalContext.current
    val bg = Color(0xFF0E0F11)
    val panelBg = Color(0xEE17191C)
    val cardBg = Color(0x991B1D20)
    val textColor = Color.White
    val subColor = Color.White.copy(alpha = 0.68f)
    val favoritePrefs = remember(context) {
        context.getSharedPreferences("game_favorites", android.content.Context.MODE_PRIVATE)
    }
    var isFavorited by remember(game.gameId) {
        mutableStateOf(favoritePrefs.getBoolean(game.gameId, false))
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val launchGame = {
        com.genzopia.Instagame.analytics.InstagameAnalytics.trackGameLaunchInitiated(
            gameId = game.gameId,
            gameName = game.gameName,
            source = "home_card"
        )
        val intent = Intent(context, Game_mode::class.java)
        intent.putExtra("game_id", game.gameId)
        intent.putExtra("game_name", game.gameName)
        intent.putExtra("launch_source", "home_card")
        context.startActivity(intent)
        onDismiss(true)
    }

    ModalBottomSheet(
        onDismissRequest = { onDismiss(false) },
        sheetState = sheetState,
        containerColor = bg,
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp),
        tonalElevation = 0.dp,
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(bg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(356.dp)
                    .align(Alignment.TopCenter)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(game.imageUrl.ifEmpty { null })
                        .crossfade(true)
                        .build(),
                    contentDescription = game.gameName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    bg.copy(alpha = 0.12f),
                                    bg
                                ),
                                startY = 190f
                            )
                        )
                )
            }

            IconButton(
                onClick = { onDismiss(false) },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 18.dp, top = 22.dp)
                    .size(54.dp)
                    .background(Color(0xB5121720), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 312.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(panelBg)
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(game.imageUrl.ifEmpty { null })
                                    .crossfade(true)
                                    .build(),
                                contentDescription = game.gameName,
                                modifier = Modifier
                                    .size(112.dp)
                                    .clip(RoundedCornerShape(18.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 4.dp)
                            ) {
                                Text(
                                    text = game.gameName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 30.sp,
                                    color = textColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (game.categoryTags.isNotEmpty()) {
                                    Spacer(Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        game.categoryTags.take(2).forEach { tag ->
                                            Surface(
                                                color = Color.White.copy(alpha = 0.10f),
                                                shape = RoundedCornerShape(18.dp)
                                            ) {
                                                Text(
                                                    text = tag,
                                                    fontSize = 13.sp,
                                                    color = Color.White.copy(alpha = 0.86f),
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(18.dp))
                                Text(
                                    text = "${formatPlayCount(game.totalPlays)}  Plays",
                                    fontSize = 15.sp,
                                    color = Color.White.copy(alpha = 0.86f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(Modifier.height(22.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = launchGame,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(62.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Orange),
                                shape = RoundedCornerShape(16.dp),
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = 0.dp,
                                    pressedElevation = 2.dp
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(30.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Play Now",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    isFavorited = !isFavorited
                                    favoritePrefs.edit().putBoolean(game.gameId, isFavorited).apply()
                                },
                                modifier = Modifier.size(62.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.06f),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = if (isFavorited) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = if (isFavorited) "Remove from favorites" else "Add to favorites",
                                    modifier = Modifier.size(30.dp),
                                    tint = if (isFavorited) Orange else Color.White
                                )
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "About the game",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = textColor,
                                        modifier = Modifier.padding(bottom = 10.dp)
                                    )
                                    if (game.description.isNotEmpty()) {
                                        var descExpanded by remember { mutableStateOf(false) }
                                        Text(
                                            text = game.description,
                                            fontSize = 16.sp,
                                            color = Color.White.copy(alpha = 0.78f),
                                            lineHeight = 23.sp,
                                            maxLines = if (descExpanded) Int.MAX_VALUE else 4,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (descExpanded) "Show less" else "Read more",
                                            color = Orange,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .padding(top = 12.dp)
                                                .clickable { descExpanded = !descExpanded }
                                        )
                                    } else {
                                        Text(
                                            "No description available",
                                            fontSize = 16.sp,
                                            color = subColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (game.developerId.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                        onClick = {
                            com.genzopia.Instagame.analytics.InstagameAnalytics.trackGameDetailDeveloperTapped(
                                gameId = game.gameId,
                                gameName = game.gameName,
                                developerId = game.developerId,
                                developerName = game.developerName
                            )
                            com.genzopia.Instagame.analytics.InstagameAnalytics.trackChannelViewed(
                                developerId = game.developerId,
                                developerName = game.developerName,
                                source = "home_game_card"
                            )
                            val intent = Intent(context, ChannelActivity::class.java)
                            intent.putExtra("developer_id", game.developerId)
                            intent.putExtra("channel_source", "home_game_card")
                            context.startActivity(intent)
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(game.developerPhotoUrl.ifEmpty { null })
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f)),
                                contentScale = ContentScale.Crop
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Developer",
                                    fontSize = 14.sp,
                                    color = subColor
                                )
                                Text(
                                    game.developerName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                "View",
                                fontSize = 17.sp,
                                color = Orange,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (relatedGames.isNotEmpty()) {
                    Spacer(Modifier.height(22.dp))
                    Text(
                        "You may also like",
                        fontWeight = FontWeight.Bold,
                        fontSize = 21.sp,
                        color = textColor,
                        modifier = Modifier.padding(start = 2.dp, bottom = 12.dp)
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(relatedGames, key = { it.gameId }) { recGame ->
                            RecommendGameCard(
                                game = recGame,
                                isDark = true,
                                textColor = textColor,
                                subColor = subColor,
                                onClick = { onDismiss(false) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatPlayCount(rawCount: String): String {
    val count = rawCount.replace(",", "").trim().toLongOrNull() ?: return rawCount
    return when {
        count >= 1_000_000 -> "${count / 100_000 / 10.0}M"
        count >= 1_000 -> "${count / 100 / 10.0}K"
        else -> count.toString()
    }
}

// ── Recommended Game Card ────────────────────────────────────────────────────

@Composable
private fun RecommendGameCard(
    game: HomeGameItem,
    isDark: Boolean,
    textColor: Color,
    subColor: Color,
    onClick: () -> Unit
) {
    val cardBg = if (isDark) Color(0xFF1A1A1A) else Color.White

    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(game.imageUrl.ifEmpty { null })
                        .crossfade(true)
                        .build(),
                    contentDescription = game.gameName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                            )
                        )
                )
            }
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    text = game.gameName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (game.developerName.isNotEmpty()) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = game.developerName,
                        fontSize = 10.sp,
                        color = subColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
