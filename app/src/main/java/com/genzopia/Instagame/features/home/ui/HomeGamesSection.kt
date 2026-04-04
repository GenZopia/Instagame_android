package com.genzopia.Instagame.features.home.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.genzopia.Instagame.channel_view.ChannelActivity
import com.genzopia.Instagame.webgl_gameloading.Game_mode
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

private val Orange = Color(0xFFFF6B35)
private val OrangeLight = Color(0xFFFF8C5A)

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
fun HomeGamesSection(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) Color(0xFF0E0E0E) else Color(0xFFFAFAFA)
    val textColor = if (isDark) Color(0xFFE0E0E0) else Color(0xFF1A1A1A)
    val subColor = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575)

    var games by remember { mutableStateOf<List<HomeGameItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedGame by remember { mutableStateOf<HomeGameItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredGames = remember(games, searchQuery) {
        if (searchQuery.isBlank()) games
        else games.filter {
            it.gameName.contains(searchQuery, ignoreCase = true) ||
            it.developerName.contains(searchQuery, ignoreCase = true) ||
            it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    LaunchedEffect(Unit) {
        FirebaseDatabase.getInstance().getReference("games")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = mutableListOf<HomeGameItem>()
                    var pending = snapshot.childrenCount.toInt()
                    if (pending == 0) { isLoading = false; return }

                    for (gameSnap in snapshot.children) {
                        val gameId = gameSnap.key ?: continue
                        val gameName = gameSnap.child("game_name").getValue(String::class.java) ?: "Unknown"
                        val description = gameSnap.child("description").getValue(String::class.java) ?: ""
                        val devId = gameSnap.child("user_id").getValue(String::class.java) ?: ""
                        val photoId = gameSnap.child("photo_id").getValue(String::class.java) ?: ""

                        fun addGame(imageUrl: String, devName: String, devPhoto: String) {
                            list.add(HomeGameItem(gameId, gameName, description, imageUrl, devId, devName, devPhoto))
                            pending--
                            if (pending == 0) { games = list.toList(); isLoading = false }
                        }

                        fun fetchWithPhoto(imageUrl: String) {
                            if (devId.isEmpty()) { addGame(imageUrl, "", ""); return }
                            FirebaseDatabase.getInstance().getReference("users").child(devId)
                                .addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(userSnap: DataSnapshot) {
                                        val devName = userSnap.child("full_name").getValue(String::class.java)
                                            ?: userSnap.child("username").getValue(String::class.java) ?: "Developer"
                                        val rawDevPhoto = userSnap.child("profile_photo_url").getValue(String::class.java) ?: ""
                                        val devPhoto = com.genzopia.Instagame.utils.ProfilePhotoUtils.sanitize(rawDevPhoto) ?: ""
                                        addGame(imageUrl, devName, devPhoto)
                                    }
                                    override fun onCancelled(e: DatabaseError) { addGame(imageUrl, "", "") }
                                })
                        }

                        if (photoId.isNotEmpty()) {
                            FirebaseDatabase.getInstance().getReference("photos").child(photoId)
                                .addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(photoSnap: DataSnapshot) {
                                        val fileExt = photoSnap.child("file_ext").getValue(String::class.java)
                                            ?: photoSnap.child("file_name").getValue(String::class.java)
                                                ?.substringAfterLast('.', "jpg")
                                            ?: "jpg"
                                        Thread {
                                            val signedUrl = com.genzopia.Instagame.utils.PhotoUrlResolver.resolveSync(photoId, fileExt)
                                            fetchWithPhoto(signedUrl ?: "")
                                        }.start()
                                    }
                                    override fun onCancelled(e: DatabaseError) { fetchWithPhoto("") }
                                })
                        } else {
                            fetchWithPhoto("")
                        }
                    }
                }
                override fun onCancelled(e: DatabaseError) { isLoading = false }
            })
    }

    if (isLoading || games.isEmpty()) return

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
            Text(
                "${games.size} games",
                fontSize = 12.sp,
                color = subColor
            )
        }

        // Search bar
        val searchBg = if (isDark) Color(0xFF2A2A2A) else Color(0xFFEEEEEE)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(searchBg)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Outlined.Search,
                contentDescription = "Search",
                tint = subColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = textColor,
                    fontSize = 14.sp
                ),
                decorationBox = { inner ->
                    if (searchQuery.isEmpty()) {
                        Text("Search games...", fontSize = 14.sp, color = subColor)
                    }
                    inner()
                }
            )
            if (searchQuery.isNotEmpty()) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.Close,
                    contentDescription = "Clear",
                    tint = subColor,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { searchQuery = "" }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 2-column vertical grid — fixed height so it scrolls within the parent LazyColumn
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 2000.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            userScrollEnabled = false
        ) {
            items(filteredGames, key = { it.gameId }) { game ->
                GameCard(game = game, isDark = isDark, textColor = textColor, subColor = subColor) {
                    selectedGame = game
                }
            }
        }

        if (filteredGames.isEmpty() && searchQuery.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No games found for \"$searchQuery\"",
                    fontSize = 14.sp,
                    color = subColor
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    selectedGame?.let { game ->
        GameDetailSheet(game = game, onDismiss = { selectedGame = null })
    }
}

@Composable
private fun GameCard(
    game: HomeGameItem,
    isDark: Boolean,
    textColor: Color,
    subColor: Color,
    onClick: () -> Unit
) {
    val cardBg = if (isDark) Color(0xFF1A1A1A) else Color.White
    val shadowColor = if (isDark) Color.Transparent else Color(0x14000000)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDark) 0.dp else 3.dp
        )
    ) {
        Column {
            // Thumbnail with gradient overlay
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
                // Bottom gradient for text readability
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
                // Play badge
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

            // Info
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameDetailSheet(game: HomeGameItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val sheetBg = if (isDark) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isDark) Color(0xFFE0E0E0) else Color(0xFF1A1A1A)
    val subColor = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575)
    val rowBg = if (isDark) Color(0xFF2A2A2A) else Color(0xFFF5F5F5)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetBg,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
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
                // Gradient overlay at bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                )
                Text(
                    text = game.gameName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(16.dp))

            // Description
            if (game.description.isNotEmpty()) {
                Text(
                    game.description,
                    fontSize = 14.sp,
                    color = subColor,
                    lineHeight = 21.sp
                )
                Spacer(Modifier.height(16.dp))
            }

            // Creator row
            if (game.developerId.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(rowBg)
                        .clickable {
                            val intent = Intent(context, ChannelActivity::class.java)
                            intent.putExtra("developer_id", game.developerId)
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
                        Text(
                            game.developerName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = textColor
                        )
                    }
                    Text(
                        "View →",
                        fontSize = 12.sp,
                        color = Orange,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // Play button
            Button(
                onClick = {
                    val intent = Intent(context, Game_mode::class.java)
                    intent.putExtra("game_id", game.gameId)
                    intent.putExtra("game_name", game.gameName)
                    context.startActivity(intent)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Play Now",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
