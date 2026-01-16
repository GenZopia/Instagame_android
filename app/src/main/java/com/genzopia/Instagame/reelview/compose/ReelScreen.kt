package com.genzopia.Instagame.reelview.compose

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.VerticalPager
import com.google.accompanist.pager.rememberPagerState
import com.google.firebase.database.FirebaseDatabase
import com.genzopia.Instagame.comments.ui.CommentsBottomSheet
import kotlinx.coroutines.delay
import androidx.paging.LoadState

/**
 * Main Reel Screen with vertical paging
 */
@OptIn(ExperimentalPagerApi::class)
@Composable
fun ReelScreen(
    viewModel: ReelViewModel,
    modifier: Modifier = Modifier
) {
    val reels = viewModel.reelsFlow.collectAsLazyPagingItems()
    val pagerState = rememberPagerState()
    val context = LocalContext.current
    var shouldPauseAll by remember { mutableStateOf(false) }
    
    // Lifecycle observer for pause/resume
    DisposableEffect(Unit) {
        val lifecycleOwner = context as? androidx.lifecycle.LifecycleOwner
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    shouldPauseAll = true
                    android.util.Log.d("ReelScreen", "Lifecycle paused - stopping reels")
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    shouldPauseAll = false
                    android.util.Log.d("ReelScreen", "Lifecycle resumed - resuming reels")
                }
                else -> {}
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black) // Black background for reels
    ) {
        // Show loading indicator when loading initial data
        if (reels.loadState.refresh is LoadState.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Loading reels...",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            // Show reels when loaded
            VerticalPager(
                count = reels.itemCount,
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val reel = reels[page]
                if (reel != null) {
                    ReelItem(
                        reel = reel,
                        isActive = page == pagerState.currentPage && !shouldPauseAll,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Single reel item with video and UI overlay
 */
@Composable
fun ReelItem(
    reel: ReelData,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    var isLiked by remember { mutableStateOf(reel.isLiked) }
    var likeCount by remember { mutableStateOf(reel.likeCount.toIntOrNull() ?: 0) }
    var isFollowing by remember { mutableStateOf(reel.isFollowing) }
    var showThumbnail by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }
    var showComments by remember { mutableStateOf(false) }
    var showLikeAnimation by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black) // Always black background for reels
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        // Double-click to launch game
                        if (reel.gameId.isNotEmpty()) {
                            val intent = Intent(context, com.genzopia.Instagame.webgl_gameloading.Game_mode::class.java)
                            intent.putExtra("game_id", reel.gameId)
                            context.startActivity(intent)
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                "No game associated with this video",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
    ) {
        // Video Player
        VideoPlayer(
            videoUrl = reel.videoUrl,
            isPlaying = isActive,
            modifier = Modifier.fillMaxSize(),
            onPlayerReady = {
                showThumbnail = false
                isLoading = false
            },
            onPlayerError = {
                isLoading = false
            }
        )
        
        // Thumbnail overlay (shown while loading) - with black background
        if (showThumbnail && reel.videoUrl != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(reel.videoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        
        // Loading indicator
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        
        // Like animation
        if (showLikeAnimation) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = "Like",
                tint = Color.White,
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.Center)
            )
            
            LaunchedEffect(Unit) {
                delay(800)
                showLikeAnimation = false
            }
        }
        
        // UI Overlay
        ReelOverlay(
            reel = reel,
            isLiked = isLiked,
            likeCount = likeCount,
            isFollowing = isFollowing,
            onLikeClick = {
                isLiked = !isLiked
                likeCount += if (isLiked) 1 else -1
                // Update Firebase
                FirebaseDatabase.getInstance().reference
                    .child("videos").child(reel.videoId)
                    .child("like_count").setValue(likeCount.toString())
            },
            onFollowClick = {
                isFollowing = true
                // Update Firebase follow status
                var devid=FirebaseDatabase.getInstance().reference
                    .child(reel.videoId).child("user_id").get()
                devid.addOnSuccessListener {dataSnapshot ->
                    val developerId = dataSnapshot.getValue(String::class.java) ?: ""
                    FirebaseDatabase.getInstance().reference
                        .child("users").child(developerId)
                        .child("following_list").child(devid.result.toString())
                        .setValue(true)
                }

            },
            onShareClick = { /* Share handled in overlay */ },
            onCommentClick = { showComments = true },
            modifier = Modifier.fillMaxSize()
        )
    }
    
    // Show comments bottom sheet
    if (showComments) {
        CommentsBottomSheet(
            videoId = reel.videoId,
            onDismiss = { showComments = false }
        )
    }
}

/**
 * UI overlay with user info and action buttons
 */
@Composable
fun ReelOverlay(
    reel: ReelData,
    isLiked: Boolean,
    likeCount: Int,
    isFollowing: Boolean,
    onLikeClick: () -> Unit,
    onFollowClick: () -> Unit,
    onShareClick: () -> Unit,
    onCommentClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Box(modifier = modifier) {
        // Bottom info section
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .fillMaxWidth(0.7f)
        ) {
            // User info - clickable to navigate to channel
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .clickable {
                        // Navigate to channel activity with developer_id
                        try {
                            val intent = Intent(context, com.genzopia.Instagame.channel_view.ChannelActivity::class.java)
                            intent.putExtra("developer_id", reel.developerId)
                            intent.putExtra("user_id", reel.developerId) // Also add user_id for compatibility
                            android.util.Log.d("ReelScreen", "Opening channel for developer: ${reel.developerId}")
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.util.Log.e("ReelScreen", "Error opening channel", e)
                            android.widget.Toast.makeText(context, "Error opening channel", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
            ) {
                // Profile picture with proper loading
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(reel.developerPhotoUrl)
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
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Username
                Text(
                    text = reel.developerName.ifEmpty { "User" },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Follow button - show "Following" when followed
                if (!isFollowing) {
                    Button(
                        onClick = onFollowClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = "Follow",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    Text(
                        text = "Following",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
            
            // Title
            Text(
                text = reel.title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            // Description
            if (reel.description.isNotEmpty()) {
                Text(
                    text = reel.description,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            // Game name
            if (reel.gameName.isNotEmpty()) {
                Text(
                    text = "@${reel.gameName}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        
        // Right action buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Like button
            ActionButton(
                icon = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                text = formatCount(likeCount),
                tint = if (isLiked) Color.Red else Color.White,
                onClick = onLikeClick
            )
            
            // Comment button
            ActionButton(
                icon = Icons.Outlined.Star,
                text = "Comment",
                onClick = onCommentClick
            )
            
            // Share button with proper intent
            ActionButton(
                icon = Icons.Filled.Share,
                text = "Share",
                onClick = {
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Check out this video: ${reel.title}\nVideo ID: ${reel.videoId}")
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share video"))
                }
            )
        }
    }
}

/**
 * Action button component
 */
@Composable
fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = tint,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
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
