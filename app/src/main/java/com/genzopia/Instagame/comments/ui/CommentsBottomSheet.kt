package com.genzopia.Instagame.comments.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.genzopia.Instagame.comments.data.CommentsRepository
import com.genzopia.Instagame.comments.models.Comment
import com.genzopia.Instagame.comments.models.Reply
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val Orange = Color(0xFFFF6B35)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    videoId: String,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val repository = remember { CommentsRepository() }

    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var commentText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var replyingTo by remember { mutableStateOf<Comment?>(null) }

    // replies per comment: commentId -> list
    var repliesMap by remember { mutableStateOf<Map<String, List<Reply>>>(emptyMap()) }
    // which comments have their replies expanded
    var expandedReplies by remember { mutableStateOf<Set<String>>(emptySet()) }
    // liked comment ids
    var likedComments by remember { mutableStateOf<Set<String>>(emptySet()) }
    // liked reply ids
    var likedReplies by remember { mutableStateOf<Set<String>>(emptySet()) }

    val currentUser = FirebaseAuth.getInstance().currentUser
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isDark) Color.White else Color.Black
    val subColor = if (isDark) Color.LightGray else Color.Gray

    // Load current user's profile photo from RTD
    var currentUserPhotoUrl by remember { mutableStateOf<String?>(currentUser?.photoUrl?.toString()) }
    LaunchedEffect(currentUser?.uid) {
        val uid = currentUser?.uid ?: return@LaunchedEffect
        val snap = FirebaseDatabase.getInstance().getReference("users").child(uid).get().await()
        val rtdPhoto = snap.child("profile_photo_url").getValue(String::class.java)
        val sanitized = com.genzopia.Instagame.utils.ProfilePhotoUtils.sanitize(rtdPhoto)
        if (!sanitized.isNullOrBlank()) currentUserPhotoUrl = sanitized
    }

    // ── Load comments ──────────────────────────────────────────────────────────
    LaunchedEffect(videoId) {
        isLoading = true
        repository.fetchCommentsFirstPage(videoId, object : CommentsRepository.CommentsCallback {
            override fun onLoaded(list: List<Comment>, last: Long?, more: Boolean) {
                comments = list
                isLoading = false
                // check liked state for each comment
                val uid = currentUser?.uid ?: return
                list.forEach { c ->
                    repository.isCommentLiked(videoId, c.comment_id, uid,
                        CommentsRepository.BooleanCallback { liked ->
                            if (liked) likedComments = likedComments + c.comment_id
                        })
                }
            }
            override fun onError(msg: String) { isLoading = false }
        })
    }

    // ── Helper: load replies for a comment ────────────────────────────────────
    fun loadReplies(commentId: String) {
        repository.fetchRepliesFirstPage(videoId, commentId, object : CommentsRepository.RepliesCallback {
            override fun onLoaded(list: List<Reply>, last: Long?, more: Boolean) {
                repliesMap = repliesMap + (commentId to list)
                val uid = currentUser?.uid ?: return
                list.forEach { r ->
                    repository.isReplyLiked(videoId, commentId, r.reply_id, uid,
                        CommentsRepository.BooleanCallback { liked ->
                            if (liked) likedReplies = likedReplies + r.reply_id
                        })
                }
            }
            override fun onError(msg: String) {}
        })
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = bg) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Comments", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = textColor)
                }
            }
            HorizontalDivider(color = if (isDark) Color.DarkGray else Color.LightGray)

            // List
            when {
                isLoading -> Box(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = Orange) }

                comments.isEmpty() -> Box(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) { Text("No comments yet. Be the first!", color = subColor) }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(comments, key = { it.comment_id ?: it.hashCode().toString() }) { comment ->
                        CommentItemFull(
                            comment = comment,
                            videoId = videoId,
                            repository = repository,
                            textColor = textColor,
                            subColor = subColor,
                            isLiked = likedComments.contains(comment.comment_id),
                            replies = repliesMap[comment.comment_id] ?: emptyList(),
                            repliesExpanded = expandedReplies.contains(comment.comment_id),
                            likedReplies = likedReplies,
                            onLikeComment = { c ->
                                val uid = currentUser?.uid ?: return@CommentItemFull
                                val cid = c.comment_id ?: return@CommentItemFull
                                val wasLiked = likedComments.contains(cid)
                                likedComments = if (wasLiked) likedComments - cid else likedComments + cid
                                repository.setCommentLike(videoId, cid, uid, !wasLiked) { _, _ -> }
                            },
                            onReply = { c -> replyingTo = c; commentText = "" },
                            onToggleReplies = { c ->
                                val cid = c.comment_id ?: return@CommentItemFull
                                if (expandedReplies.contains(cid)) {
                                    expandedReplies = expandedReplies - cid
                                } else {
                                    expandedReplies = expandedReplies + cid
                                    if (!repliesMap.containsKey(cid)) loadReplies(cid)
                                }
                            },
                            onLikeReply = { commentId, r ->
                                val uid = currentUser?.uid ?: return@CommentItemFull
                                val rid = r.reply_id ?: return@CommentItemFull
                                val wasLiked = likedReplies.contains(rid)
                                likedReplies = if (wasLiked) likedReplies - rid else likedReplies + rid
                                repository.setReplyLike(videoId, commentId, rid, uid, !wasLiked) { _, _ -> }
                            }
                        )
                    }
                }
            }

            HorizontalDivider(color = if (isDark) Color.DarkGray else Color.LightGray)

            // Reply-to banner
            AnimatedVisibility(visible = replyingTo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Replying to ${replyingTo?.user_display_name?.takeIf { it.isNotBlank() } ?: "user"}",
                        fontSize = 12.sp,
                        color = Orange
                    )
                    IconButton(onClick = { replyingTo = null; commentText = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel reply",
                            tint = subColor, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Input row
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(currentUserPhotoUrl)
                        .crossfade(true)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_gallery)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Gray),
                    contentScale = ContentScale.Crop
                )
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(if (replyingTo != null) "Write a reply…" else "Add a comment…")
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (commentText.isNotBlank() && currentUser != null) {
                                    val text = commentText.trim()
                                    val target = replyingTo
                                    commentText = ""
                                    replyingTo = null
                                    scope.launch {
                                        val uid = currentUser.uid
                                        val userSnap = FirebaseDatabase.getInstance()
                                            .getReference("users").child(uid).get().await()
                                        val name = userSnap.child("full_name").getValue(String::class.java)
                                            ?: userSnap.child("username").getValue(String::class.java)
                                            ?: currentUser.displayName ?: "User"
                                        val photo = com.genzopia.Instagame.utils.ProfilePhotoUtils.sanitize(
                                            userSnap.child("profile_photo_url").getValue(String::class.java)
                                        ) ?: currentUser.photoUrl?.toString() ?: ""

                                        if (target != null) {
                                            repository.postReply(
                                                videoId, target.comment_id, text, uid, name, photo,
                                                CommentsRepository.CompletionCallback { success, _ ->
                                                    if (success) {
                                                        // refresh replies for that comment
                                                        loadReplies(target.comment_id)
                                                        // bump reply_count optimistically
                                                        comments = comments.map { c ->
                                                            if (c.comment_id == target.comment_id) {
                                                                c.reply_count = (c.reply_count ?: 0L) + 1L; c
                                                            } else c
                                                        }
                                                        expandedReplies = expandedReplies + target.comment_id
                                                    }
                                                }
                                            )
                                        } else {
                                            repository.postComment(
                                                videoId, text, uid, name, photo,
                                                CommentsRepository.CompletionCallback { success, _ ->
                                                    if (success) {
                                                        repository.fetchCommentsFirstPage(videoId,
                                                            object : CommentsRepository.CommentsCallback {
                                                                override fun onLoaded(list: List<Comment>, l: Long?, m: Boolean) {
                                                                    comments = list
                                                                }
                                                                override fun onError(msg: String) {}
                                                            })
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            },
                            enabled = commentText.isNotBlank()
                        ) {
                            Icon(
                                Icons.Default.Send, contentDescription = "Send",
                                tint = if (commentText.isNotBlank()) Orange else Color.Gray
                            )
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Orange,
                        unfocusedBorderColor = Color.LightGray
                    )
                )
            }
        }
    }
}

// ── Single comment row with inline replies ─────────────────────────────────────

@Composable
fun CommentItemFull(
    comment: Comment,
    videoId: String,
    repository: CommentsRepository,
    textColor: Color,
    subColor: Color,
    isLiked: Boolean,
    replies: List<Reply>,
    repliesExpanded: Boolean,
    likedReplies: Set<String>,
    onLikeComment: (Comment) -> Unit,
    onReply: (Comment) -> Unit,
    onToggleReplies: (Comment) -> Unit,
    onLikeReply: (commentId: String, Reply) -> Unit
) {
    val context = LocalContext.current

    // Local state for instant UI feedback — no waiting for parent recompose
    // isLiked from parent only sets the initial value; after that this is the source of truth
    var localLiked by remember(comment.comment_id) { mutableStateOf(isLiked) }
    var localLikeCount by remember(comment.comment_id) { mutableStateOf(comment.like_count ?: 0L) }

    // Only sync from parent when the initial liked state arrives from Firebase (goes false→true on load)
    // We do NOT sync on every recompose to avoid overwriting rapid taps
    val prevIsLiked = remember(comment.comment_id) { mutableStateOf(isLiked) }
    if (isLiked != prevIsLiked.value) {
        prevIsLiked.value = isLiked
        localLiked = isLiked
    }

    val replyCount = comment.reply_count ?: 0L

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Avatar
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(comment.user_photo_url?.takeIf { it.isNotBlank() })
                    .crossfade(true)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .build(),
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Gray),
                contentScale = ContentScale.Crop
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = comment.user_display_name?.takeIf { it.isNotBlank() } ?: "User",
                    fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textColor
                )
                Text(
                    text = comment.text ?: "",
                    fontSize = 14.sp, color = textColor,
                    modifier = Modifier.padding(top = 2.dp)
                )

                // Actions
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Like — purely local toggle, fires Firebase in background
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            val nowLiked = !localLiked
                            localLiked = nowLiked
                            localLikeCount = maxOf(0L, localLikeCount + if (nowLiked) 1L else -1L)
                            onLikeComment(comment)
                        }
                    ) {
                        Icon(
                            imageVector = if (localLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Like",
                            tint = if (localLiked) Orange else subColor,
                            modifier = Modifier.size(16.dp)
                        )
                        if (localLikeCount > 0) {
                            Spacer(Modifier.width(4.dp))
                            Text(localLikeCount.toString(), fontSize = 12.sp, color = subColor)
                        }
                    }

                    // Reply
                    Text(
                        "Reply", fontSize = 12.sp, color = subColor,
                        modifier = Modifier.clickable { onReply(comment) }
                    )

                    // Time
                    Text(
                        getTimeAgo(comment.created_at ?: 0L),
                        fontSize = 12.sp, color = subColor
                    )
                }

                // View / hide replies toggle
                if (replyCount > 0 || replies.isNotEmpty()) {
                    val label = when {
                        repliesExpanded -> "Hide replies"
                        replies.isNotEmpty() -> "View ${replies.size} ${if (replies.size == 1) "reply" else "replies"}"
                        else -> "View ${replyCount} ${if (replyCount == 1L) "reply" else "replies"}"
                    }
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        color = Orange,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clickable { onToggleReplies(comment) }
                    )
                }
            }
        }

        // Inline replies
        AnimatedVisibility(visible = repliesExpanded && replies.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 44.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                replies.forEach { reply ->
                    ReplyItemRow(
                        reply = reply,
                        isLiked = likedReplies.contains(reply.reply_id),
                        textColor = textColor,
                        subColor = subColor,
                        onLike = { onLikeReply(comment.comment_id ?: "", reply) }
                    )
                }
            }
        }
    }
}

// ── Single reply row ───────────────────────────────────────────────────────────

@Composable
fun ReplyItemRow(
    reply: Reply,
    isLiked: Boolean,
    textColor: Color,
    subColor: Color,
    onLike: () -> Unit
) {
    // Local state for instant UI feedback
    // isLiked from parent only sets the initial value; after that this is the source of truth
    var localLiked by remember(reply.reply_id) { mutableStateOf(isLiked) }
    var localLikeCount by remember(reply.reply_id) { mutableStateOf(reply.like_count ?: 0L) }

    val prevIsLiked = remember(reply.reply_id) { mutableStateOf(isLiked) }
    if (isLiked != prevIsLiked.value) {
        prevIsLiked.value = isLiked
        localLiked = isLiked
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(reply.user_photo_url?.takeIf { it.isNotBlank() })
                .crossfade(true)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .build(),
            contentDescription = null,
            modifier = Modifier.size(28.dp).clip(CircleShape).background(Color.Gray),
            contentScale = ContentScale.Crop
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = reply.user_display_name?.takeIf { it.isNotBlank() } ?: "User",
                fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textColor
            )
            Text(
                text = reply.text ?: "",
                fontSize = 13.sp, color = textColor,
                modifier = Modifier.padding(top = 2.dp)
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        val nowLiked = !localLiked
                        localLiked = nowLiked
                        localLikeCount = maxOf(0L, localLikeCount + if (nowLiked) 1L else -1L)
                        onLike()
                    }
                ) {
                    Icon(
                        imageVector = if (localLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = "Like reply",
                        tint = if (localLiked) Orange else subColor,
                        modifier = Modifier.size(14.dp)
                    )
                    if (localLikeCount > 0) {
                        Spacer(Modifier.width(4.dp))
                        Text(localLikeCount.toString(), fontSize = 11.sp, color = subColor)
                    }
                }
                Text(
                    getTimeAgo(reply.created_at ?: 0L),
                    fontSize = 11.sp, color = subColor
                )
            }
        }
    }
}

fun getTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000}h ago"
        diff < 604_800_000L -> "${diff / 86_400_000}d ago"
        else -> "${diff / 604_800_000}w ago"
    }
}
