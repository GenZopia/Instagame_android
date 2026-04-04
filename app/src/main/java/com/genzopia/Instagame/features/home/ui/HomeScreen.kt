package com.genzopia.Instagame.ui.home.compose

import HomeViewModel
import com.genzopia.Instagame.features.home.ui.FollowingStoriesBar
import VideoPlayer
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.genzopia.Instagame.comments.ui.CommentsBottomSheet
import com.genzopia.Instagame.webgl_gameloading.Game_mode
import com.google.firebase.database.FirebaseDatabase
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.genzopia.Instagame.channel_view.ChannelActivity

// App's orange theme color
private val OrangeTheme = Color(0xFFFF6B35)

/**
 * Home feed screen with vertical scrolling list of videos
 * Shows only videos from followed users (Instagram style)
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    // Show only videos from followed users (Instagram style)
    val videos = viewModel.followingVideosFlow.collectAsLazyPagingItems()

    val isDarkTheme = isSystemInDarkTheme()
    val backgroundColor = if (isDarkTheme) Color(0xFF121212) else Color.White
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val secondaryTextColor = if (isDarkTheme) Color.LightGray else Color.Gray
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Followed users for stories bar
    val followedUsers by viewModel.followedUsers.collectAsState()
    val followedUsersLoading by viewModel.followedUsersLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.initializePlayer(context)
    }

    // Track currently visible video index
    var currentVisibleIndex by remember { mutableStateOf(-1) }
    var shouldPauseAll by remember { mutableStateOf(false) }

    // Lifecycle observer for pause/resume
    DisposableEffect(Unit) {
        val lifecycleOwner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    shouldPauseAll = true
                    viewModel.pauseAll()
                    Log.d("HomeScreen", "Lifecycle paused - stopping videos")
                }
                Lifecycle.Event.ON_RESUME -> {
                    shouldPauseAll = false
                    if (videos.itemCount > 0 && currentVisibleIndex >= 0 && currentVisibleIndex < videos.itemCount) {
                        videos[currentVisibleIndex]?.let { video ->
                            viewModel.playVideo(video.videoId)
                        }
                    }
                    Log.d("HomeScreen", "Lifecycle resumed - resuming videos")
                }
                else -> {}
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)

        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
        }
    }

    // Detect which video is currently most visible and preload adjacent videos
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, videos.itemCount) {
        val newIndex = listState.firstVisibleItemIndex
        if (newIndex != currentVisibleIndex) {
            currentVisibleIndex = newIndex
            
            // Set current video and preload adjacent ones
            if (videos.itemCount > 0 && currentVisibleIndex < videos.itemCount) {
                videos[currentVisibleIndex]?.let { video ->
                    viewModel.setCurrentVideo(video.videoId, video.videoUrl)
                    if (!shouldPauseAll) {
                        viewModel.playVideo(video.videoId)
                    }
                    
                    // Preload adjacent videos
                    val currentVideosList = (0 until videos.itemCount).mapNotNull { videos[it] }
                    viewModel.preloadVideos(currentVisibleIndex, currentVideosList)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Instagram-style followers bar at the top
            item(key = "followers_bar") {
                FollowingStoriesBar(
                    users = followedUsers,
                    isLoading = followedUsersLoading
                )
            }

            // Games section
            item(key = "games_section") {
                com.genzopia.Instagame.features.home.ui.HomeGamesSection()
            }

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
                        isVisible = index == currentVisibleIndex && !shouldPauseAll,
                        modifier = Modifier.fillMaxWidth(),
                        viewModel = viewModel
                    )
                }
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
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val videoHeight = (screenHeight * 3) / 4 // Fixed 3/4 of screen height
    
    var isLiked by remember { mutableStateOf(video.isLiked) }
    var likeCount by remember { mutableStateOf(video.likeCount.toIntOrNull() ?: 0) }
    var isFollowing by remember { mutableStateOf(video.isFollowing) }
    var isPlaying by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    
    // Get player for this video
    val player = remember(video.videoId) {
        viewModel.getPlayerForVideo(video.videoId, video.videoUrl)
    }
    
    // Auto-play when visible, pause when not visible
    LaunchedEffect(isVisible, player) {
        if (player != null) {
            isPlaying = isVisible
            if (isVisible) {
                player.playWhenReady = true
            } else {
                player.playWhenReady = false
            }
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isSystemInDarkTheme()) Color(0xFF121212) else Color.White)
            .padding(bottom = 16.dp)
            .clipToBounds() // Prevent overflow
    ) {
        // Header with channel info - clickable to navigate to channel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable {
                    // Navigate to channel activity with developer_id
                    try {
                        val intent = Intent(context, ChannelActivity::class.java)
                        intent.putExtra("developer_id", video.developerId)
                        intent.putExtra("user_id", video.developerId) // Also add user_id for compatibility
                        Log.d("HomeScreen", "Opening channel for developer: ${video.developerId}")
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("HomeScreen", "Error opening channel", e)
                        Toast.makeText(context, "Error opening channel", Toast.LENGTH_SHORT).show()
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile picture with proper loading
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(video.developerPhotoUrl)
                    .crossfade(true)
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
        
        // Video player with fixed height (1/3 of screen)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(videoHeight)
                .background(Color.Black)
                .clipToBounds() // Prevent video overflow
                .clickable { 
                    if (player != null) {
                        isPlaying = !isPlaying
                        player.playWhenReady = isPlaying
                    }
                }
        ) {
            // Only render video player when visible to prevent overlap
            if (player != null && (isVisible || isPlaying)) {
                VideoPlayer(
                    isPlaying = isPlaying,
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds(),
                    onPlayerReady = { /* Video ready */ },
                    onPlayerError = { /* Handle error */ },
                    player = player
                )
            } else {
                // Show thumbnail or placeholder when not visible
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            
            // Play button overlay
            if (!isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .zIndex(1f), // Ensure overlay is on top
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .padding(20.dp)
                    )
                }
            }
        }
        
        // Action buttons row - properly separated from video
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (isSystemInDarkTheme()) Color(0xFF121212) else Color.White,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                        var devid=FirebaseDatabase.getInstance().reference
                            .child(video.videoId).child("user_id").get()
                        devid.addOnSuccessListener {dataSnapshot ->
                            val developerId = dataSnapshot.getValue(String::class.java) ?: ""
                            FirebaseDatabase.getInstance().reference
                                .child("users").child(developerId)
                                .child("following_list").child(devid.result.toString())
                                .setValue(true)
                        }
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Check out this video: ${video.title}\nVideo ID: ${video.videoId}")
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share video"))
                }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = "Share",
                    tint = textColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Comment button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    showComments = true
                }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Star,
                    contentDescription = "Comment",
                    tint = textColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            
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
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Play Game", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        }
        
        // Content section - properly separated
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (isSystemInDarkTheme()) Color(0xFF121212) else Color.White,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
        
            // Title
            Text(
                text = video.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            // Description
            if (video.description.isNotEmpty()) {
                Text(
                    text = video.description,
                    fontSize = 13.sp,
                    color = secondaryTextColor,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            // Views and time
            Text(
                text = "${formatCount(video.viewCount.toIntOrNull() ?: 0)} views",
                fontSize = 12.sp,
                color = secondaryTextColor,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
        }
        }
        
        // Divider
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            thickness = 8.dp,
            color = if (isSystemInDarkTheme()) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)
        )
    }
    
    // Show comments bottom sheet
    if (showComments) {
        val fragmentManager = (context as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager
        if (fragmentManager != null) {
            val tag = "comments_${video.videoId}"
            if (fragmentManager.findFragmentByTag(tag) == null) {
                com.genzopia.Instagame.comments.ui.CommentsBottomSheetFragment
                    .newInstance(video.videoId)
                    .show(fragmentManager, tag)
            }
        }
        showComments = false
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
