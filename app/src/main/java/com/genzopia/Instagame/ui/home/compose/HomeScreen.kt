package com.genzopia.Instagame.ui.home.compose

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.genzopia.Instagame.reelview.compose.VideoPlayer
import com.genzopia.Instagame.comments.ui.CommentsBottomSheet
import com.genzopia.Instagame.webgl_gameloading.Game_mode
import com.google.firebase.database.FirebaseDatabase

// App's orange theme color
private val OrangeTheme = Color(0xFFFF6B35)

/**
 * Home feed screen with vertical scrolling list of videos
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val videos = viewModel.videosFlow.collectAsLazyPagingItems()
    val isDarkTheme = isSystemInDarkTheme()
    val backgroundColor = if (isDarkTheme) Color(0xFF121212) else Color.White
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val secondaryTextColor = if (isDarkTheme) Color.LightGray else Color.Gray
    
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(
            count = videos.itemCount,
            key = { index -> videos[index]?.videoId ?: index }
        ) { index ->
            val video = videos[index]
            if (video != null) {
                HomeVideoItem(
                    video = video,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Single video item in home feed
 */
@Composable
fun HomeVideoItem(
    video: HomeVideoData,
    textColor: Color,
    secondaryTextColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isLiked by remember { mutableStateOf(video.isLiked) }
    var likeCount by remember { mutableStateOf(video.likeCount.toIntOrNull() ?: 0) }
    var isFollowing by remember { mutableStateOf(video.isFollowing) }
    var isPlaying by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        // Header with channel info - clickable to navigate to channel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    // Navigate to channel activity
                    val intent = Intent(context, com.genzopia.Instagame.channel_view.ChannelActivity::class.java)
                    intent.putExtra("user_id", video.developerId)
                    context.startActivity(intent)
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile picture with proper loading
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(video.developerPhotoUrl)
                    .crossfade(true)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .build(),
                contentDescription = "Profile",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Gray),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Channel name
            Text(
                text = video.developerName.ifEmpty { "Channel Name" },
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = textColor,
                modifier = Modifier.weight(1f)
            )
        }
        
        // Video player with fixed aspect ratio
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f) // Fixed 9:16 aspect ratio
                .clickable { isPlaying = !isPlaying }
        ) {
            VideoPlayer(
                videoUrl = video.videoUrl,
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxSize(),
                onPlayerReady = { /* Video ready */ },
                onPlayerError = { /* Handle error */ }
            )
            
            // Play button overlay
            if (!isPlaying) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier
                        .size(64.dp)
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .padding(16.dp)
                        .clickable { isPlaying = true }
                )
            }
        }
        
        // Action buttons row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Like button with Firebase update
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    isLiked = !isLiked
                    likeCount += if (isLiked) 1 else -1
                    // Update Firebase
                    FirebaseDatabase.getInstance().reference
                        .child("videos").child(video.videoId)
                        .child("like_count").setValue(likeCount.toString())
                }
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (isLiked) Color.Red else textColor,
                    modifier = Modifier.size(24.dp)
                )
                if (likeCount > 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatCount(likeCount),
                        fontSize = 12.sp,
                        color = secondaryTextColor
                    )
                }
            }
            
            // Follow button with Firebase update - using orange theme
            if (!isFollowing) {
                TextButton(
                    onClick = {
                        isFollowing = true
                        // Update Firebase follow status
                        FirebaseDatabase.getInstance().reference
                            .child("follows")
                            .child(com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "")
                            .child(video.developerId)
                            .setValue(true)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = OrangeTheme
                    )
                ) {
                    Text(
                        text = "Follow",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            } else {
                Text(
                    text = "Following",
                    fontSize = 14.sp,
                    color = secondaryTextColor,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            
            // Share button with proper intent
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = "Share",
                tint = textColor,
                modifier = Modifier
                    .size(24.dp)
                    .clickable {
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Check out this video: ${video.title}\nVideo ID: ${video.videoId}")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share video"))
                    }
            )
            
            // Comment button
            Icon(
                imageVector = Icons.Outlined.Star,
                contentDescription = "Comment",
                tint = textColor,
                modifier = Modifier
                    .size(24.dp)
                    .clickable {
                        showComments = true
                    }
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Play game button - using orange theme
            Button(
                onClick = {
                    val intent = Intent(context, Game_mode::class.java)
                    intent.putExtra("game_id", video.gameId)
                    intent.putExtra("game_name", video.gameName)
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = OrangeTheme
                ),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Play", fontSize = 14.sp)
            }
        }
        
        // Title
        Text(
            text = video.title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        
        // Description
        if (video.description.isNotEmpty()) {
            Text(
                text = video.description,
                fontSize = 13.sp,
                color = secondaryTextColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        
        // Views and time
        Text(
            text = "${formatCount(video.viewCount.toIntOrNull() ?: 0)} views",
            fontSize = 12.sp,
            color = secondaryTextColor,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        
        // Divider
        Divider(
            modifier = Modifier.padding(top = 12.dp),
            thickness = 0.5.dp,
            color = if (isSystemInDarkTheme()) Color.DarkGray else Color.LightGray
        )
    }
    
    // Show comments bottom sheet
    if (showComments) {
        CommentsBottomSheet(
            videoId = video.videoId,
            onDismiss = { showComments = false }
        )
    }
}

/**
 * Format large numbers (e.g., 1000 -> 1K)
 */
fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "${count / 1_000_000}M"
        count >= 1_000 -> "${count / 1_000}K"
        else -> count.toString()
    }
}
