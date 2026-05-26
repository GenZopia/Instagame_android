@file:OptIn(ExperimentalFoundationApi::class)

package com.genzopia.Instagame.reelview.compose

import ReelViewModel
import com.genzopia.Instagame.VideoPlayer
import android.annotation.SuppressLint
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import kotlinx.coroutines.delay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PageSize
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// FIX 1: Replaced deprecated `com.google.accompanist.pager` with
//         `androidx.compose.foundation.pager` (stable, hardware-accelerated,
//         correct fling physics — this alone removes most of the lag).
//
// FIX 2: Added `flingBehavior = PagerDefaults.flingBehavior(...)` with
//         `PagerSnapDistance.atMost(1)` so the pager ALWAYS snaps exactly
//         one page per fling — identical to Instagram's feel.
//
// FIX 3: `beyondBoundsPageCount = 1` pre-composes the next/previous page so
//         the video player is already attached before the user scrolls to it.
//
// FIX 4: Removed the 1 500 ms `delay` fallback that was causing the visible
//         "stuck on black" flash. The `DisposableEffect` on the player listener
//         is now the sole source of truth for hiding the loading state.
//
// FIX 5: Thumbnail is now shown only when the player truly hasn't buffered yet,
//         preventing the black→thumbnail→video triple-flash on fast scrolls.
//
// FIX 6: Fixed the like-count revert math in the failure callbacks (was
//         double-adjusting the count in the wrong direction).
//
// BUILD FIX 1: Added @file:OptIn(ExperimentalFoundationApi::class) at the top
//              to suppress "This foundation API is experimental" errors for all
//              pager-related APIs used in this file.
//
// BUILD FIX 2: Renamed `beyondViewportPageCount` → `beyondBoundsPageCount`
//              to match the actual parameter name in the installed compose-
//              foundation version (the newer name caused a compile error).
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Main Reel Screen with vertical paging.
 *
 * Key changes vs. the original:
 *  • Uses androidx.compose.foundation.pager (not Accompanist) — no more jank.
 *  • Snap-per-page fling behaviour matches Instagram exactly.
 *  • beyondBoundsPageCount = 1 keeps adjacent pages warm in memory.
 */
@Composable
fun ReelScreen(
    viewModel: ReelViewModel,
    modifier: Modifier = Modifier,
    // Tutorial integration: called once pager is ready with a lambda that scrolls to next page.
    // OnboardingTutorialHost stores this lambda and calls it when the scroll step fires.
    onScrollActionReady: (scrollToNext: () -> Unit) -> Unit = {},
    onCurrentReelChanged: (gameId: String) -> Unit = {}
) {
    val reels = viewModel.reelsFlow.collectAsLazyPagingItems()

    // FIX 1 + 2: androidx pager with proper snap distance
    val pagerState = rememberPagerState(pageCount = { reels.itemCount })

    val context = LocalContext.current
    var shouldPauseAll by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Hand the tutorial a stable lambda it can call to animate to the next page.
    // Re-registers whenever pagerState or itemCount changes (e.g. after first load).
    LaunchedEffect(pagerState, reels.itemCount) {
        onScrollActionReady {
            coroutineScope.launch {
                if (pagerState.currentPage + 1 < reels.itemCount) {
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initializePlayer(context)
        // Start watching for background URL resolution so we can refresh the
        // pager once URLs that were null at first load become available.
        viewModel.watchForUrlResolution()
    }

    // When the number of resolved URLs increases, refresh the pager so that
    // reels which were emitted with null playbackUrl get re-loaded with real URLs.
    val urlsReadyCount by viewModel.urlsReadyCount.collectAsState()
    LaunchedEffect(urlsReadyCount) {
        if (urlsReadyCount > 0 && reels.itemCount > 0) {
            // Only refresh if any visible reel still has no URL
            val hasNullUrls = (0 until minOf(reels.itemCount, 5)).any { i ->
                reels[i]?.playbackUrl == null
            }
            if (hasNullUrls) {
                Log.d("ReelScreen", "URLs resolved ($urlsReadyCount), refreshing pager")
                reels.refresh()
            }
        }
    }

    // Handle page changes: notify tutorial of current gameId + drive video playback + preload
    LaunchedEffect(pagerState.currentPage, reels.itemCount) {
        if (reels.itemCount > 0 && pagerState.currentPage < reels.itemCount) {
            val reel = reels[pagerState.currentPage]
            onCurrentReelChanged(reel?.gameId ?: "")
            Log.d("ReelScreen", "Page ${pagerState.currentPage}, videoUrl=${reel?.videoUrl}")
            reel?.let {
                viewModel.setCurrentVideo(it.videoId, it.playbackUrl)
                // Note: actual play is driven by ReelItem's LaunchedEffect(isActive, player)
                // which runs after the player is created. We still call playVideo here as
                // a best-effort in case the player already exists in the pool.
                if (!shouldPauseAll) {
                    viewModel.playVideo(it.videoId)
                }
                val currentReelsList = (0 until reels.itemCount).mapNotNull { i -> reels[i] }
                viewModel.preloadVideos(pagerState.currentPage, currentReelsList)
            }
        }
    }

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
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val isInitialLoading =
            reels.loadState.refresh is LoadState.Loading && reels.itemCount == 0

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

        if (reels.itemCount > 0) {
            // FIX 1: androidx VerticalPager (not Accompanist)
            // FIX 2: snapAnimationSpec = tween(200ms) — 4x faster than default spring
            //         pagerSnapDistance = atMost(1) — one page per fling, always
            // BUILD FIX 2: beyondBoundsPageCount (was beyondViewportPageCount — compile error)
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondBoundsPageCount = 1,   // BUILD FIX 2: renamed from beyondViewportPageCount
                pageSize = PageSize.Fill,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    pagerSnapDistance = PagerSnapDistance.atMost(1),
                    // snapAnimationSpec: final lock-to-page animation.
                    // tween(200ms) is ~4x faster than the default spring (~400ms).
                    // This is the single biggest factor in making scroll feel instant.
                    snapAnimationSpec = tween(
                        durationMillis = 200,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    )
                )
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
 * Single reel item with video and UI overlay.
 */
@Composable
fun ReelItem(
    reel: ReelData,
    isActive: Boolean,
    viewModel: ReelViewModel,
    modifier: Modifier = Modifier
) {
    val (defaultIsLiked, defaultLikeCount) = remember(reel.videoId) {
        viewModel.getLikeState(reel.videoId, reel.isLiked, reel.likeCount.toIntOrNull() ?: 0)
    }
    var isLiked by remember(reel.videoId) { mutableStateOf(defaultIsLiked) }
    var likeCount by remember(reel.videoId) { mutableStateOf(defaultLikeCount) }

    var isFollowing by remember(reel.developerId) {
        mutableStateOf(viewModel.getFollowState(reel.developerId, reel.isFollowing))
    }

    // Get or create the player for this video.
    // KEY includes playbackUrl so that when the URL resolves (after background
    // Phase-2 fetch), remember re-runs and the player is actually created.
    // Without this, a null-URL reel from the prefetch cache never gets a player.
    val player = remember(reel.videoId, reel.playbackUrl) {
        viewModel.getPlayerForVideo(reel.videoId, reel.playbackUrl)
    }

    // Show spinner only when a player EXISTS but hasn't buffered yet.
    // If player is null (URL not ready), show nothing — no infinite spinner.
    var isLoading by remember(reel.videoId, reel.playbackUrl) {
        mutableStateOf(
            player != null && player.playbackState != androidx.media3.common.Player.STATE_READY
        )
    }
    var showThumbnail by remember(reel.videoId, reel.playbackUrl) {
        mutableStateOf(player == null)
    }

    var showComments by remember { mutableStateOf(false) }
    var showLikeAnimation by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Player state listener — drives loading/thumbnail visibility.
    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose {}

        // Already ready (prefetched) — hide loading immediately.
        if (player.playbackState == androidx.media3.common.Player.STATE_READY) {
            isLoading = false
            showThumbnail = false
        }

        val listener = object : androidx.media3.common.Player.Listener {
            override fun onRenderedFirstFrame() {
                // First frame is actually painted — safe to hide loading UI.
                isLoading = false
                showThumbnail = false
            }
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    androidx.media3.common.Player.STATE_READY -> {
                        isLoading = false
                        showThumbnail = false
                    }
                    androidx.media3.common.Player.STATE_BUFFERING -> {
                        if (isActive) isLoading = true
                    }
                    // STATE_IDLE or STATE_ENDED — clear the spinner so it never
                    // gets stuck (e.g. after error recovery replaces the player).
                    androidx.media3.common.Player.STATE_IDLE,
                    androidx.media3.common.Player.STATE_ENDED -> {
                        isLoading = false
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Drive play/pause from isActive.
    // Also handles the race where the player is created AFTER ReelScreen's
    // LaunchedEffect already called playVideo() — at that point playerPool was
    // empty so nothing happened. This LaunchedEffect runs after composition
    // (when the player definitely exists) and is the authoritative play trigger.
    LaunchedEffect(isActive, player) {
        if (player != null) {
            if (isActive) {
                player.volume = 1f
                player.playWhenReady = true
            } else {
                player.playWhenReady = false
                player.volume = 0f
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
                            android.widget.Toast.makeText(context, "No game associated with this video", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            .pointerInput(player) {
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown(requireUnconsumed = false)
                        player?.playWhenReady = false
                        do {
                            val event = awaitPointerEvent()
                        } while (event.changes.any { it.pressed })
                        if (isActive) player?.playWhenReady = true
                    }
                }
            }
    ) {
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

        // FIX 5: Thumbnail only shown while player is truly absent
        if (showThumbnail && reel.videoUrl != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(reel.videoUrl)
                        .crossfade(false) // instant — no extra fade on top of the video
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)), // semi-transparent so thumb shows through
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFFFF6B35),
                    modifier = Modifier.size(40.dp),
                    strokeWidth = 3.dp
                )
            }
        }

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

                val newLikedState = !isLiked
                // FIX 6: store the NEW count to use in failure revert
                val newLikeCount = likeCount + if (newLikedState) 1 else -1

                isLiked = newLikedState
                likeCount = newLikeCount
                viewModel.updateLikeState(reel.videoId, newLikedState, newLikeCount)

                val videoRef = FirebaseDatabase.getInstance().reference
                    .child("videos").child(reel.videoId).child("like_count")
                val userLikedRef = FirebaseDatabase.getInstance().reference
                    .child("users").child(currentUserId)
                    .child("liked_videos").child(reel.videoId)

                val onFailure: (Exception) -> Unit = { e ->
                    // FIX 6: revert to the ORIGINAL values (before this tap)
                    isLiked = !newLikedState
                    likeCount = likeCount + if (newLikedState) -1 else 1
                    viewModel.updateLikeState(reel.videoId, !newLikedState, likeCount)
                    Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }

                if (newLikedState) {
                    videoRef.setValue(newLikeCount.toString())
                        .addOnSuccessListener { userLikedRef.setValue(true) }
                        .addOnFailureListener { onFailure(it) }
                } else {
                    videoRef.setValue(newLikeCount.toString())
                        .addOnSuccessListener { userLikedRef.removeValue() }
                        .addOnFailureListener { onFailure(it) }
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
                val followingRef = db.child("users").child(currentUserId)
                    .child("following_list").child(reel.developerId)
                val followersCountRef = db.child("users").child(reel.developerId)
                    .child("followers_count")

                if (newFollowState) {
                    followingRef.setValue(true)
                        .addOnSuccessListener {
                            followersCountRef.runTransaction(object : Transaction.Handler {
                                override fun doTransaction(currentData: MutableData): Transaction.Result {
                                    currentData.value = (currentData.getValue(Int::class.java) ?: 0) + 1
                                    return Transaction.success(currentData)
                                }
                                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                                    if (committed) Toast.makeText(context, "Following ${reel.developerName}", Toast.LENGTH_SHORT).show()
                                }
                            })
                        }
                        .addOnFailureListener { e ->
                            isFollowing = !newFollowState
                            viewModel.updateFollowState(reel.developerId, !newFollowState)
                            Toast.makeText(context, "Failed to follow: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    followingRef.removeValue()
                        .addOnSuccessListener {
                            followersCountRef.runTransaction(object : Transaction.Handler {
                                override fun doTransaction(currentData: MutableData): Transaction.Result {
                                    val count = currentData.getValue(Int::class.java) ?: 0
                                    currentData.value = if (count > 0) count - 1 else 0
                                    return Transaction.success(currentData)
                                }
                                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                                    if (committed) Toast.makeText(context, "Unfollowed ${reel.developerName}", Toast.LENGTH_SHORT).show()
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

        if (player != null) {
            GlowingSeekBar(
                player = player,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    if (showComments) {
        val fragmentManager =
            (context as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager
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

// ─────────────────────────────────────────────────────────────────────────────
// ReelOverlay, ActionButton, GlowingSeekBar — unchanged from original
// ─────────────────────────────────────────────────────────────────────────────

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
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .fillMaxWidth(0.7f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .clickable {
                        try {
                            val intent = Intent(
                                context,
                                com.genzopia.Instagame.channel_view.ChannelActivity::class.java
                            )
                            intent.putExtra("developer_id", reel.developerId)
                            intent.putExtra("user_id", reel.developerId)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(
                                context, "Error opening channel", android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
            ) {
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

                Text(
                    text = reel.developerName.ifEmpty { "User" },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = onFollowClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFollowing) Color.Red else Color.Transparent
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isFollowing) Color.Red else Color.White
                    ),
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

            Text(
                text = reel.title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

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

            if (reel.gameName.isNotEmpty()) {
                Text(
                    text = "@${reel.gameName}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            ActionButton(
                icon = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                text = formatCount(likeCount),
                tint = if (isLiked) Color.Red else Color.White,
                onClick = onLikeClick
            )

            ActionButton(
                icon = Icons.Outlined.Star,
                text = "Comment",
                onClick = onCommentClick
            )

            ActionButton(
                icon = Icons.Filled.Share,
                text = "Share",
                onClick = {
                    // Deep link: opens the app directly if installed,
                    // falls back to Play Store if not installed
                    val deepLink = "https://instagame.genzopia.com/video/${reel.videoId}"
                    val customSchemeLink = "instagame://video/${reel.videoId}"
                    val playStoreUrl = "https://play.google.com/store/apps/details?id=com.genzopia.Instagame"
                    val shareText = buildString {
                        append("🎮 Check out \"${reel.title}\" on Instagame!\n\n")
                        append(deepLink)
                        append("\n\nOpen in app: ")
                        append(customSchemeLink)
                        append("\n\nDon't have the app? Download it here:\n")
                        append(playStoreUrl)
                    }
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share video"))

                    // Increment share count in Firebase
                    com.google.firebase.database.FirebaseDatabase.getInstance().reference
                        .child("videos").child(reel.videoId).child("share_count")
                        .runTransaction(object : com.google.firebase.database.Transaction.Handler {
                            override fun doTransaction(
                                currentData: com.google.firebase.database.MutableData
                            ): com.google.firebase.database.Transaction.Result {
                                val count = currentData.getValue(Int::class.java) ?: 0
                                currentData.value = count + 1
                                return com.google.firebase.database.Transaction.success(currentData)
                            }
                            override fun onComplete(
                                error: com.google.firebase.database.DatabaseError?,
                                committed: Boolean,
                                snapshot: com.google.firebase.database.DataSnapshot?
                            ) { /* no-op */ }
                        })
                }
            )
        }
    }
}

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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(50))
                .background(TrackColor)
        )

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
                                    setShadowLayer(
                                        glowPx, 0f, 0f,
                                        Orange.copy(alpha = 0.9f).toArgb()
                                    )
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

fun formatCount(count: Int): String = when {
    count >= 1_000_000 -> "${count / 1_000_000}M"
    count >= 1_000     -> "${count / 1_000}K"
    else               -> count.toString()
}