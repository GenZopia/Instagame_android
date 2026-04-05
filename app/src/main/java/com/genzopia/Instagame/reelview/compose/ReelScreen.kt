package com.genzopia.Instagame.reelview.compose

import ReelViewModel
import VideoPlayer
import android.annotation.SuppressLint
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.exoplayer.ExoPlayer
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.firestore.FieldValue

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

    // Initialize the player when the screen first appears
    LaunchedEffect(Unit) {
        viewModel.initializePlayer(context)
    }

    // Handle page changes with preloading
    LaunchedEffect(pagerState.currentPage, reels.itemCount) {
        if (reels.itemCount > 0 && pagerState.currentPage < reels.itemCount) {
            val reel = reels[pagerState.currentPage]
            Log.d("ReelScreen", "Page ${pagerState.currentPage}, videoUrl=${reel?.videoUrl}")
            reel?.let {
                viewModel.setCurrentVideo(it.videoId, it.playbackUrl)
                if (!shouldPauseAll) {
                    viewModel.playVideo(it.videoId)
                }
                val currentReelsList = (0 until reels.itemCount).mapNotNull { reels[it] }
                viewModel.preloadVideos(pagerState.currentPage, currentReelsList)
            }
        }
    }

    // Lifecycle observer for pause/resume — use LocalLifecycleOwner (Fragment lifecycle)
    // instead of casting context to LifecycleOwner (Activity), so it respects fragment navigation
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    shouldPauseAll = true
                    viewModel.pauseAll()
                }
                Lifecycle.Event.ON_RESUME -> {
                    shouldPauseAll = false
                    if (reels.itemCount > 0 && pagerState.currentPage < reels.itemCount) {
                        reels[pagerState.currentPage]?.let { reel ->
                            viewModel.playVideo(reel.videoId)
                        }
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Only show spinner if paging is loading AND we have no prefetched data at all
        val isInitialLoading = reels.loadState.refresh is LoadState.Loading && reels.itemCount == 0
        if (isInitialLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFFFF6B35),
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp
                )
            }
        }
        // Always show pager — it renders as soon as items arrive
        if (reels.itemCount > 0) {
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
                        viewModel = viewModel,
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
    viewModel: ReelViewModel,
    modifier: Modifier = Modifier
) {
    // Use ViewModel to persist like state across scrolls
    val (defaultIsLiked, defaultLikeCount) = remember(reel.videoId) {
        viewModel.getLikeState(reel.videoId, reel.isLiked, reel.likeCount.toIntOrNull() ?: 0)
    }
    var isLiked by remember(reel.videoId) { mutableStateOf(defaultIsLiked) }
    var likeCount by remember(reel.videoId) { mutableStateOf(defaultLikeCount) }
    
    // Use ViewModel to persist follow state across scrolls
    var isFollowing by remember(reel.developerId) { 
        mutableStateOf(viewModel.getFollowState(reel.developerId, reel.isFollowing)) 
    }
    var showThumbnail by remember { mutableStateOf(true) }
    // Don't show spinner if player is already buffered (prefetch case)
    var isLoading by remember(reel.videoId) {
        mutableStateOf(viewModel.getPlayerForVideo(reel.videoId, reel.playbackUrl)
            ?.let { it.playbackState != androidx.media3.common.Player.STATE_READY } ?: true)
    }
    var showComments by remember { mutableStateOf(false) }
    var showLikeAnimation by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Get player for this video — use playbackUrl (HLS manifest preferred over MP4)
    val player = remember(reel.videoId) {
        viewModel.getPlayerForVideo(reel.videoId, reel.playbackUrl)
    }

    // Hide spinner as soon as player reaches STATE_READY
    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose {}
        // Already ready — hide immediately
        if (player.playbackState == androidx.media3.common.Player.STATE_READY) {
            isLoading = false
            showThumbnail = false
        }
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == androidx.media3.common.Player.STATE_READY) {
                    isLoading = false
                    showThumbnail = false
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Control playback based on active state
    LaunchedEffect(isActive, player) {
        if (player != null) {
            if (isActive) {
                player.playWhenReady = true
                // Fallback: hide loading after 1.5s even if STATE_READY hasn't fired
                delay(1500)
                isLoading = false
                showThumbnail = false
            } else {
                player.playWhenReady = false
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
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
        if (player != null) {
            VideoPlayer(
                isPlaying = isActive,
                modifier = Modifier.fillMaxSize(),
                player = player,
                onPlayerReady = {
                    showThumbnail = false
                    isLoading = false
                },
                onPlayerError = {
                    isLoading = false
                }
            )
        }
        
        // Thumbnail overlay - only show initially
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
                    color = Color(0xFFFF6B35),
                    modifier = Modifier.size(40.dp),
                    strokeWidth = 3.dp
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
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                if (currentUserId == null) {
                    Toast.makeText(context, "Please login to like", Toast.LENGTH_SHORT).show()
                    return@ReelOverlay
                }
                
                // Toggle like state
                val newLikedState = !isLiked
                val newLikeCount = likeCount + if (newLikedState) 1 else -1
                
                // Update UI immediately
                isLiked = newLikedState
                likeCount = newLikeCount
                
                // Update ViewModel state immediately for persistence
                viewModel.updateLikeState(reel.videoId, newLikedState, newLikeCount)
                
                // Update Firebase
                val videoRef = FirebaseDatabase.getInstance().reference
                    .child("videos").child(reel.videoId)
                    .child("like_count")
                
                val userLikedRef = FirebaseDatabase.getInstance().reference
                    .child("users")
                    .child(currentUserId)
                    .child("liked_videos")
                    .child(reel.videoId)
                
                if (newLikedState) {
                    // Like the video
                    videoRef.setValue(newLikeCount.toString())
                        .addOnSuccessListener {
                            userLikedRef.setValue(true)
                        }
                        .addOnFailureListener { e ->
                            // Revert on failure
                            isLiked = !newLikedState
                            likeCount = likeCount - if (newLikedState) 1 else -1
                            viewModel.updateLikeState(reel.videoId, !newLikedState, likeCount)
                            Toast.makeText(context, "Failed to like: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    // Unlike the video
                    videoRef.setValue(newLikeCount.toString())
                        .addOnSuccessListener {
                            userLikedRef.removeValue()
                        }
                        .addOnFailureListener { e ->
                            // Revert on failure
                            isLiked = !newLikedState
                            likeCount = likeCount + if (newLikedState) 1 else -1
                            viewModel.updateLikeState(reel.videoId, !newLikedState, likeCount)
                            Toast.makeText(context, "Failed to unlike: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            },
            onFollowClick = {
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                if (currentUserId == null) {
                    Toast.makeText(context, "Please login to follow", Toast.LENGTH_SHORT).show()
                    return@ReelOverlay
                }

                if (reel.developerId.isEmpty()) {
                    Toast.makeText(context, "Invalid developer ID", Toast.LENGTH_SHORT).show()
                    return@ReelOverlay
                }

                val newFollowState = !isFollowing
                isFollowing = newFollowState

                viewModel.updateFollowState(reel.developerId, newFollowState)

                val db = FirebaseDatabase.getInstance().reference

                val followingRef = db
                    .child("users")
                    .child(currentUserId)
                    .child("following_list")
                    .child(reel.developerId)

                val followersCountRef = db
                    .child("users")
                    .child(reel.developerId)
                    .child("followers_count")

                if (newFollowState) {

                    // FOLLOW
                    followingRef.setValue(true)
                        .addOnSuccessListener {

                            followersCountRef.runTransaction(object : Transaction.Handler {

                                override fun doTransaction(currentData: MutableData): Transaction.Result {
                                    var count = currentData.getValue(Int::class.java) ?: 0
                                    currentData.value = count + 1
                                    return Transaction.success(currentData)
                                }

                                override fun onComplete(
                                    error: DatabaseError?,
                                    committed: Boolean,
                                    snapshot: DataSnapshot?
                                ) {
                                    if (committed) {
                                        Toast.makeText(context, "Following ${reel.developerName}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            })

                        }
                        .addOnFailureListener { e ->
                            isFollowing = !newFollowState
                            viewModel.updateFollowState(reel.developerId, !newFollowState)
                            Toast.makeText(context, "Failed to follow: ${e.message}", Toast.LENGTH_SHORT).show()
                        }

                } else {

                    // UNFOLLOW
                    followingRef.removeValue()
                        .addOnSuccessListener {

                            followersCountRef.runTransaction(object : Transaction.Handler {

                                override fun doTransaction(currentData: MutableData): Transaction.Result {
                                    var count = currentData.getValue(Int::class.java) ?: 0
                                    if (count > 0) count -= 1
                                    currentData.value = count
                                    return Transaction.success(currentData)
                                }

                                override fun onComplete(
                                    error: DatabaseError?,
                                    committed: Boolean,
                                    snapshot: DataSnapshot?
                                ) {
                                    if (committed) {
                                        Toast.makeText(context, "Unfollowed ${reel.developerName}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            })

                        }
                        .addOnFailureListener { e ->
                            isFollowing = !newFollowState
                            viewModel.updateFollowState(reel.developerId, !newFollowState)
                            Toast.makeText(context, "Failed to unfollow: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            },
            onShareClick = { },
            onCommentClick = { showComments = true },
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )

        // ── Glowing seekable progress bar ──────────────────────────────────────
        if (player != null) {
            GlowingSeekBar(
                player = player,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
    
    if (showComments) {
        val fragmentManager = (context as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager
        if (fragmentManager != null) {
            val tag = "comments_${reel.videoId}"
            if (fragmentManager.findFragmentByTag(tag) == null) {
                com.genzopia.Instagame.comments.ui.CommentsBottomSheetFragment
                    .newInstance(reel.videoId)
                    .show(fragmentManager, tag)
            }
        }
        showComments = false
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
    viewModel: ReelViewModel,
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
                
                // Follow/Following button - always clickable
                Button(
                    onClick = onFollowClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFollowing) Color.White.copy(alpha = 0.2f) else Color.Transparent
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = if (isFollowing) "Following" else "Follow",
                        color = Color.White,
                        fontSize = 14.sp
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
 * Glowing orange seekable progress bar pinned to the bottom of the reel.
 * Polls player position every 200ms. Drag left/right to seek.
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun GlowingSeekBar(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
    barHeight: Dp = 3.dp,
    glowRadius: Dp = 8.dp
) {
    val Orange = Color(0xFFFF6B35)
    val TrackColor = Color.White.copy(alpha = 0.18f)

    var progress by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(player) {
        while (true) {
            if (!isDragging) {
                val dur = player.duration
                val pos = player.currentPosition
                progress = if (dur > 0L) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
            }
            delay(200)
        }
    }

    val displayProgress = if (isDragging) dragProgress else progress
    val density = LocalDensity.current
    val glowPx = with(density) { glowRadius.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight + 16.dp)
            .pointerInput(player) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isDragging = true
                        dragProgress = (down.position.x / size.width).coerceIn(0f, 1f)
                        do {
                            val event = awaitPointerEvent()
                            val drag = event.changes.firstOrNull() ?: break
                            drag.consume()
                            dragProgress = (drag.position.x / size.width).coerceIn(0f, 1f)
                        } while (event.changes.any { it.pressed })
                        val dur = player.duration
                        if (dur > 0L) player.seekTo((dragProgress * dur).toLong())
                        progress = dragProgress
                        isDragging = false
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {

        // Track background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(50))
                .background(TrackColor)
        )

        // Filled progress with glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(50))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(displayProgress.coerceAtLeast(0f))
                    .drawBehind {
                        drawIntoCanvas { canvas ->
                            val paint = Paint().apply {
                                asFrameworkPaint().apply {
                                    isAntiAlias = true
                                    color = android.graphics.Color.TRANSPARENT
                                    setShadowLayer(glowPx, 0f, 0f, Orange.copy(alpha = 0.9f).toArgb())
                                }
                            }
                            canvas.drawRect(0f, 0f, size.width, size.height, paint)
                        }
                    }
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Orange.copy(alpha = 0.8f), Orange)
                        )
                    )
            )
        }
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
