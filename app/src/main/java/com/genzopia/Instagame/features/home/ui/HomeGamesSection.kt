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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val developerPhotoUrl: String
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

// ── Game Detail Sheet (Full-Screen Overlay) ───────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameDetailSheet(game: HomeGameItem, onDismiss: (didPlay: Boolean) -> Unit) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val sheetBg = if (isDark) Color(0xFF121212) else Color(0xFFFAFAFA)
    val textColor = if (isDark) Color(0xFFE0E0E0) else Color(0xFF1A1A1A)
    val subColor = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575)
    val cardBg = if (isDark) Color(0xFF1E1E1E) else Color.White
    val scrollState = rememberScrollState()

    // Calculate hero image height as a fraction of screen
    val heroHeight = 280.dp

    ModalBottomSheet(
        onDismissRequest = { onDismiss(false) },
        containerColor = sheetBg,
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp),
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            // ── Scrollable Content ────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // ── Hero Image Section ────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroHeight)
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

                    // Top gradient (for close button visibility)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.6f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Bottom gradient (for game name readability)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    )

                    // Game name overlay
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 20.dp, vertical = 20.dp)
                    ) {
                        Text(
                            text = game.gameName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            letterSpacing = 0.5.sp
                        )
                        if (game.developerName.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "by ",
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = game.developerName,
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Close button - top left
                    IconButton(
                        onClick = { onDismiss(false) },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .size(36.dp)
                            .background(
                                Color.Black.copy(alpha = 0.4f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // ── Primary Actions ─────────────────────────────────────
                // Play Now, Share always visible at top of scrollable content
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 20.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Play Now - prominent primary action
                    Button(
                        onClick = {
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
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Orange),
                        shape = RoundedCornerShape(14.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 8.dp
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Play Now",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Share button
                    OutlinedButton(
                        onClick = {
                            val shareText = buildString {
                                append("🎮 Check out \"${game.gameName}\" on Instagame!\n\n")
                                append("https://instagame.genzopia.com/game/${game.gameId}")
                                append("\n\nDownload the app: https://play.google.com/store/apps/details?id=com.genzopia.Instagame")
                            }
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share game"))
                        },
                        modifier = Modifier
                            .widthIn(min = 56.dp)
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            1.5.dp,
                            Orange.copy(alpha = 0.5f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Orange
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // ── Info Card ───────────────────────────────────────────
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = if (isDark) 0.dp else 1.dp
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Game stats row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            GameStat("Game ID", game.gameId.take(8) + "...", Orange, subColor)
                            GameStat("Plays", "—", subColor, subColor)
                            GameStat("Rating", "—", subColor, subColor)
                        }

                        if (game.description.isNotEmpty()) {
                            HorizontalDivider(
                                color = if (isDark) Color(0xFF333333) else Color(0xFFEEEEEE),
                                modifier = Modifier.padding(vertical = 12.dp)
                            )

                            Text(
                                "About",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = textColor,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                game.description,
                                fontSize = 14.sp,
                                color = subColor,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Developer Section ───────────────────────────────────
                if (game.developerId.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = if (isDark) 0.dp else 1.dp
                        ),
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
                                .padding(16.dp),
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
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0)
                                    ),
                                contentScale = ContentScale.Crop
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Developer",
                                    fontSize = 11.sp,
                                    color = subColor,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    game.developerName,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = textColor
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(
                                        Orange.copy(alpha = 0.15f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    "View →",
                                    fontSize = 13.sp,
                                    color = Orange,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Bottom spacer for scroll + safe area
                Spacer(Modifier.height(100.dp))
            }

            // ── Sticky Bottom Button ─────────────────────────────────
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter),
                shadowElevation = 8.dp,
                color = sheetBg
            ) {
                Button(
                    onClick = {
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
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 10.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Play Now",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun GameStat(
    label: String,
    value: String,
    valueColor: Color,
    subColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = valueColor
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = subColor,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
