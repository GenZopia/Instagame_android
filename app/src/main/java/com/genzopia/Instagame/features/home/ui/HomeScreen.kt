package com.genzopia.Instagame.ui.home.compose

import HomeViewModel
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.genzopia.Instagame.channel_view.ChannelActivity
import com.genzopia.Instagame.features.home.ui.FollowingStoriesBar
import com.genzopia.Instagame.features.home.ui.HomeGamesSection
import com.genzopia.Instagame.webgl_gameloading.Game_mode
import com.google.firebase.database.FirebaseDatabase

private val Orange = Color(0xFFFF6B35)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val videos = viewModel.followingVideosFlow.collectAsLazyPagingItems()
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) Color(0xFF0E0E0E) else Color(0xFFF2F2F2)
    val textColor = if (isDark) Color(0xFFE0E0E0) else Color(0xFF1A1A1A)
    val subColor = if (isDark) Color(0xFF9E9E9E) else Color(0xFF757575)
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val followedUsers by viewModel.followedUsers.collectAsState()
    val followedUsersLoading by viewModel.followedUsersLoading.collectAsState()

    LaunchedEffect(Unit) { viewModel.initializePlayer(context) }

    var currentVisibleIndex by remember { mutableStateOf(-1) }
    var shouldPauseAll by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val lifecycleOwner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> { shouldPauseAll = true; viewModel.pauseAll() }
                Lifecycle.Event.ON_RESUME -> {
                    shouldPauseAll = false
                    if (videos.itemCount > 0 && currentVisibleIndex in 0 until videos.itemCount) {
                        videos[currentVisibleIndex]?.let { viewModel.playVideo(it.videoId) }
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, videos.itemCount) {
        val newIndex = listState.firstVisibleItemIndex
        if (newIndex != currentVisibleIndex) {
            currentVisibleIndex = newIndex
            if (videos.itemCount > 0 && currentVisibleIndex < videos.itemCount) {
                videos[currentVisibleIndex]?.let { video ->
                    viewModel.setCurrentVideo(video.videoId, video.videoUrl)
                    if (!shouldPauseAll) viewModel.playVideo(video.videoId)
                    viewModel.preloadVideos(currentVisibleIndex, (0 until videos.itemCount).mapNotNull { videos[it] })
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().background(bg),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // ── Following bar ──────────────────────────────────────────────────────
        item(key = "following_bar") {
            FollowingStoriesBar(users = followedUsers, isLoading = followedUsersLoading)
        }

        // ── Games grid ─────────────────────────────────────────────────────────
        item(key = "games_section") {
            HomeGamesSection()
        }

        // ── Videos section header ──────────────────────────────────────────────
        if (videos.itemCount > 0) {
            item(key = "videos_header") {
                VideosSectionHeader(isDark = isDark, textColor = textColor)
            }
        }

        // ── Video items ────────────────────────────────────────────────────────
        items(
            count = videos.itemCount,
            key = { index -> videos[index]?.videoId ?: index }
        ) { index ->
            val video = videos[index] ?: return@items
            HomeVideoItem(
                video = video,
                isDark = isDark,
                textColor = textColor,
                subColor = subColor,
                isVisible = index == currentVisibleIndex && !shouldPauseAll,
                viewModel = viewModel
            )
        }

        // ── Empty state ────────────────────────────────────────────────────────
        if (videos.itemCount == 0) {
            item(key = "empty_state") {
                EmptyFeedState(isDark = isDark, textColor = textColor, subColor = subColor)
            }
        }
    }
}

@Composable
private fun VideosSectionHeader(isDark: Boolean, textColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp),
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
            "Latest Videos",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = textColor,
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
private fun EmptyFeedState(isDark: Boolean, textColor: Color, subColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.PlayArrow,
            contentDescription = null,
            tint = Orange.copy(alpha = 0.4f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "No videos from followed developers",
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = textColor,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Follow a developer to see their game videos here. Browse games above to discover creators.",
            fontSize = 13.sp,
            color = subColor,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 19.sp
        )
    }
}

@Composable
fun HomeVideoItem(
    video: HomeVideoData,
    isDark: Boolean,
    textColor: Color,
    subColor: Color,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel
) {
    val context = LocalContext.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val videoHeight = screenHeight * 0.52f

    var isLiked by remember { mutableStateOf(video.isLiked) }
    var likeCount by remember { mutableStateOf(video.likeCount.toIntOrNull() ?: 0) }
    var isFollowing by remember { mutableStateOf(video.isFollowing) }
    var isPlaying by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }

    val player = remember(video.videoId) {
        viewModel.getPlayerForVideo(video.videoId, video.videoUrl)
    }

    LaunchedEffect(isVisible, player) {
        if (player != null) {
            isPlaying = isVisible
            player.playWhenReady = isVisible
        }
    }

    val cardBg = if (isDark) Color(0xFF1A1A1A) else Color.White

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 2.dp)
    ) {
        Column {
            // ── Channel header ─────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            context.startActivity(
                                Intent(context, ChannelActivity::class.java)
                                    .putExtra("developer_id", video.developerId)
                            )
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error opening channel", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            Brush.linearGradient(listOf(Orange, Color(0xFFFF3CAC))),
                            CircleShape
                        )
                        .padding(2.dp)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(video.developerPhotoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF2A2A2A) else Color(0xFFEEEEEE)),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.developerName.ifEmpty { "Developer" },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (video.gameName.isNotEmpty()) {
                        Text(
                            text = video.gameName,
                            fontSize = 11.sp,
                            color = Orange,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // Follow chip
                if (!isFollowing) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Orange.copy(alpha = 0.12f),
                        modifier = Modifier.clickable {
                            isFollowing = true
                            FirebaseDatabase.getInstance().reference
                                .child("users").child(video.developerId)
                                .child("following_list").child(video.developerId)
                                .setValue(true)
                        }
                    ) {
                        Text(
                            "+ Follow",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Orange,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // ── Video player ───────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(videoHeight)
                    .background(Color.Black)
                    .clipToBounds()
                    .clickable {
                        if (player != null) {
                            isPlaying = !isPlaying
                            player.playWhenReady = isPlaying
                        }
                    }
            ) {
                if (player != null && (isVisible || isPlaying)) {
                    VideoPlayer(
                        isPlaying = isPlaying,
                        modifier = Modifier.fillMaxSize().clipToBounds(),
                        onPlayerReady = {},
                        onPlayerError = {},
                        player = player
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0xFF111111)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }

                if (!isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.25f))
                            .zIndex(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            // ── Title + description ────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = video.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (video.description.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = video.description,
                        fontSize = 12.sp,
                        color = subColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${formatCount(video.viewCount.toIntOrNull() ?: 0)} views",
                    fontSize = 11.sp,
                    color = subColor.copy(alpha = 0.7f)
                )
            }

            // ── Action bar ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Like
                ActionButton(
                    icon = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    label = if (likeCount > 0) formatCount(likeCount) else "",
                    tint = if (isLiked) Color(0xFFE53935) else subColor,
                    isDark = isDark
                ) {
                    isLiked = !isLiked
                    likeCount += if (isLiked) 1 else -1
                    FirebaseDatabase.getInstance().reference
                        .child("videos").child(video.videoId)
                        .child("like_count").setValue(likeCount.toString())
                }

                // Comment
                ActionButton(
                    icon = Icons.Outlined.MailOutline,
                    label = "",
                    tint = subColor,
                    isDark = isDark
                ) { showComments = true }

                // Share
                ActionButton(
                    icon = Icons.Outlined.Share,
                    label = "",
                    tint = subColor,
                    isDark = isDark
                ) {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Check out: ${video.title}")
                            }, "Share"
                        )
                    )
                }

                Spacer(Modifier.weight(1f))

                // Play Game button
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(context, Game_mode::class.java)
                                .putExtra("game_id", video.gameId)
                                .putExtra("game_name", video.gameName)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Orange),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("Play", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showComments) {
        val fm = (context as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager
        if (fm != null) {
            val tag = "comments_${video.videoId}"
            if (fm.findFragmentByTag(tag) == null) {
                com.genzopia.Instagame.comments.ui.CommentsBottomSheetFragment
                    .newInstance(video.videoId)
                    .show(fm, tag)
            }
        }
        showComments = false
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val rippleBg = if (isDark) Color(0xFF2A2A2A) else Color(0xFFF0F0F0)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        if (label.isNotEmpty()) {
            Text(label, fontSize = 12.sp, color = tint, fontWeight = FontWeight.Medium)
        }
    }
}

fun formatCount(count: Int): String = when {
    count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000f)
    count >= 1_000 -> String.format("%.1fK", count / 1_000f)
    else -> count.toString()
}
