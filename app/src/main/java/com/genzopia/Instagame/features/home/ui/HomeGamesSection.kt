package com.genzopia.Instagame.features.home.ui

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.BorderStroke
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
    selectedGameFromDeepLink: HomeGameItem? = null,
    onDeepLinkGameDismissed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) Color(0xFF0E0E0E) else Color(0xFFFAFAFA)
    val textColor = if (isDark) Color(0xFFE0E0E0) else Color(0xFF1A1A1A)
    val subColor = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575)

    var selectedGame by remember { mutableStateOf<HomeGameItem?>(null) }

    // Handle deep link game selection
    LaunchedEffect(selectedGameFromDeepLink) {
        if (selectedGameFromDeepLink != null) {
            selectedGame = selectedGameFromDeepLink
        }
    }

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
                source = if (game == selectedGameFromDeepLink) "deep_link" else "home_card"
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
                if (game == selectedGameFromDeepLink) {
                    onDeepLinkGameDismissed()
                }
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

// ── Game Detail Sheet ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GameDetailSheet(game: HomeGameItem, onDismiss: (didPlay: Boolean) -> Unit) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val sheetBg = if (isDark) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isDark) Color(0xFFE0E0E0) else Color(0xFF1A1A1A)
    val subColor = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575)
    val rowBg = if (isDark) Color(0xFF2A2A2A) else Color(0xFFF5F5F5)
    val dividerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFEEEEEE)

    val density = androidx.compose.ui.platform.LocalDensity.current
    val sheetState = remember {
        androidx.compose.material3.SheetState(
            skipPartiallyExpanded = true,
            density = density,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 180)
        )
    }

    fun launchGame() {
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
        containerColor = sheetBg,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = null
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Scrollable content ──────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                // Hero image — tapping opens the game
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { launchGame() }
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
                    // Top rounded handle + dark gradient for readability
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                                )
                            )
                    )
                    // Tap-to-play hint
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(50.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Tap to Play", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    // Game name at bottom of image
                    Text(
                        text = game.gameName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 14.dp, end = 16.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Drag handle at top
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.5f))
                    )
                }

                // Description
                if (game.description.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        game.description,
                        fontSize = 14.sp,
                        color = subColor,
                        lineHeight = 21.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Text(
                        text = if (expanded) "View less" else "View more",
                        fontSize = 13.sp,
                        color = Orange,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                            .clickable { expanded = !expanded }
                    )
                }

                // Developer row
                if (game.developerId.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(rowBg)
                            .clickable {
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
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(game.developerPhotoUrl.ifEmpty { null })
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0xFF3A3A3A) else Color(0xFFDDDDDD)),
                            contentScale = ContentScale.Crop
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Developer", fontSize = 10.sp, color = subColor, letterSpacing = 0.5.sp)
                            Text(game.developerName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = textColor)
                        }
                        Text("View →", fontSize = 12.sp, color = Orange, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            // ── Sticky bottom action bar ────────────────────────────────────
            HorizontalDivider(color = dividerColor, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(sheetBg)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Share button — fixed square, big icon inside
                OutlinedButton(
                    onClick = {
                        val shareText = "Hey! Checkout this game 🎮\nhttps://www.genzopia.com/games/${game.gameId}"
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share game via"))
                    },
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Orange),
                    border = BorderStroke(1.5.dp, Orange),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share",
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Play Now button — fills remaining space
                Button(
                    onClick = { launchGame() },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Play Now", fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.5.sp)
                }
            }
        }
    }
}
