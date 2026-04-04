package com.genzopia.Instagame.features.home.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

data class HomeGameItem(
    val gameId: String,
    val gameName: String,
    val description: String,
    val imageUrl: String,
    val developerId: String,
    val developerName: String,
    val developerPhotoUrl: String
)

/**
 * Horizontal scrollable games row for the Home feed.
 * Loads all games from Firebase /games node.
 */
@Composable
fun HomeGamesSection(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) Color(0xFF121212) else Color.White
    val textColor = if (isDark) Color.White else Color.Black
    val subColor = if (isDark) Color.LightGray else Color.Gray

    var games by remember { mutableStateOf<List<HomeGameItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedGame by remember { mutableStateOf<HomeGameItem?>(null) }

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

                        // Resolve thumbnail: look up /photos/{photo_id} for the URL
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
                                        // Always use worker URL (pre-signed photo_url is expired)
                                        // Priority: r2_key → construct from photo_id + file_ext
                                        val photoUrl = run {
                                            val r2Key = photoSnap.child("r2_key").getValue(String::class.java)
                                            if (!r2Key.isNullOrEmpty()) {
                                                "https://file-upload-worker.genzopia.workers.dev/?key=$r2Key"
                                            } else {
                                                val ext = photoSnap.child("file_ext").getValue(String::class.java)
                                                    ?: photoSnap.child("file_name").getValue(String::class.java)
                                                        ?.substringAfterLast('.', "jpg")
                                                    ?: "jpg"
                                                "https://file-upload-worker.genzopia.workers.dev/?key=photo/$photoId.$ext"
                                            }
                                        }
                                        fetchWithPhoto(photoUrl)
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Games", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textColor)
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(games, key = { it.gameId }) { game ->
                GameCard(game = game, textColor = textColor, subColor = subColor) {
                    selectedGame = game
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        HorizontalDivider(
            thickness = 8.dp,
            color = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)
        )
    }

    // Game detail bottom sheet
    selectedGame?.let { game ->
        GameDetailSheet(game = game, onDismiss = { selectedGame = null })
    }
}

@Composable
private fun GameCard(
    game: HomeGameItem,
    textColor: Color,
    subColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.width(160.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(game.imageUrl.ifEmpty { null })
                    .crossfade(true)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .build(),
                contentDescription = game.gameName,
                modifier = Modifier.fillMaxWidth().height(110.dp),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = game.gameName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (game.developerName.isNotEmpty()) {
                    Text(
                        text = game.developerName,
                        fontSize = 11.sp,
                        color = subColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
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
    val bg = if (isDark) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isDark) Color.White else Color.Black
    val subColor = if (isDark) Color.LightGray else Color.Gray

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = bg) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Game thumbnail
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(game.imageUrl.ifEmpty { null })
                    .crossfade(true)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .build(),
                contentDescription = game.gameName,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Game name
            Text(game.gameName, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = textColor)

            // Description
            if (game.description.isNotEmpty()) {
                Text(
                    game.description,
                    fontSize = 14.sp,
                    color = subColor,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Creator row — clickable to channel
            if (game.developerId.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFF5F5F5))
                        .clickable {
                            val intent = Intent(context, ChannelActivity::class.java)
                            intent.putExtra("developer_id", game.developerId)
                            context.startActivity(intent)
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(game.developerPhotoUrl.ifEmpty { null })
                            .crossfade(true)
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_gallery)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Gray),
                        contentScale = ContentScale.Crop
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Creator", fontSize = 11.sp, color = subColor)
                        Text(game.developerName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = textColor)
                    }
                    Text("View Channel →", fontSize = 12.sp, color = Orange)
                }

                Spacer(modifier = Modifier.height(16.dp))
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
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Play Now", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
