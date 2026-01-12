package com.genzopia.Instagame.comments.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * Compose Comments Bottom Sheet
 * Integrates with existing comment system
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    videoId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { CommentsRepository() }
    
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var commentText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    val currentUser = FirebaseAuth.getInstance().currentUser
    
    // Load comments
    LaunchedEffect(videoId) {
        isLoading = true
        repository.fetchCommentsFirstPage(videoId, object : CommentsRepository.CommentsCallback {
            override fun onLoaded(loadedComments: List<Comment>, lastCreatedAt: Long?, hasMore: Boolean) {
                comments = loadedComments
                isLoading = false
            }
            
            override fun onError(message: String) {
                isLoading = false
                android.util.Log.e("CommentsBottomSheet", "Error loading comments: $message")
            }
        })
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
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
                Text(
                    text = "Comments",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close"
                    )
                }
            }
            
            Divider()
            
            // Comments list
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (comments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No comments yet. Be the first to comment!",
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(comments) { comment ->
                        CommentItem(
                            comment = comment,
                            videoId = videoId,
                            repository = repository
                        )
                    }
                }
            }
            
            Divider()
            
            // Input row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // User avatar
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(currentUser?.photoUrl)
                        .crossfade(true)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_gallery)
                        .build(),
                    contentDescription = "Your avatar",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Gray),
                    contentScale = ContentScale.Crop
                )
                
                // Input field
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Add a comment") },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (commentText.isNotBlank() && currentUser != null) {
                                    scope.launch {
                                        repository.postComment(
                                            videoId,
                                            commentText,
                                            currentUser.uid,
                                            currentUser.displayName ?: "User",
                                            currentUser.photoUrl?.toString() ?: "",
                                            object : CommentsRepository.CompletionCallback {
                                                override fun onComplete(success: Boolean, errorMessage: String?) {
                                                    if (success) {
                                                        commentText = ""
                                                        // Reload comments
                                                        repository.fetchCommentsFirstPage(videoId, object : CommentsRepository.CommentsCallback {
                                                            override fun onLoaded(loadedComments: List<Comment>, lastCreatedAt: Long?, hasMore: Boolean) {
                                                                comments = loadedComments
                                                            }
                                                            
                                                            override fun onError(message: String) {
                                                                android.util.Log.e("CommentsBottomSheet", "Error reloading: $message")
                                                            }
                                                        })
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            },
                            enabled = commentText.isNotBlank()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = if (commentText.isNotBlank()) Color(0xFFFF6B35) else Color.Gray
                            )
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF6B35),
                        unfocusedBorderColor = Color.LightGray
                    )
                )
            }
        }
    }
}

@Composable
fun CommentItem(
    comment: Comment,
    videoId: String,
    repository: CommentsRepository
) {
    val context = LocalContext.current
    var isLiked by remember { mutableStateOf(false) }
    var likeCount by remember { mutableStateOf(comment.like_count ?: 0L) }
    
    val currentUser = FirebaseAuth.getInstance().currentUser
    
    // Check if liked
    LaunchedEffect(comment.comment_id) {
        if (currentUser != null) {
            repository.isCommentLiked(videoId, comment.comment_id, currentUser.uid, object : CommentsRepository.BooleanCallback {
                override fun onResult(value: Boolean) {
                    isLiked = value
                }
            })
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // User avatar - clickable to open channel
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(comment.user_photo_url)
                .crossfade(true)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .build(),
            contentDescription = "User avatar",
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Gray)
                .clickable {
                    // Navigate to channel
                    val intent = Intent(context, com.genzopia.Instagame.channel_view.ChannelActivity::class.java)
                    intent.putExtra("user_id", comment.user_id)
                    context.startActivity(intent)
                },
            contentScale = ContentScale.Crop
        )
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Username
            Text(
                text = comment.user_display_name ?: "User",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            
            // Comment text
            Text(
                text = comment.text ?: "",
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            
            // Actions row
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Like button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        if (currentUser != null) {
                            val newLikeState = !isLiked
                            isLiked = newLikeState
                            likeCount += if (newLikeState) 1 else -1
                            
                            repository.setCommentLike(
                                videoId,
                                comment.comment_id,
                                currentUser.uid,
                                newLikeState,
                                object : CommentsRepository.CompletionCallback {
                                    override fun onComplete(success: Boolean, errorMessage: String?) {
                                        if (!success) {
                                            // Revert on error
                                            isLiked = !newLikeState
                                            likeCount += if (newLikeState) -1 else 1
                                        }
                                    }
                                }
                            )
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = "Like",
                        tint = if (isLiked) Color(0xFFFF6B35) else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    if (likeCount > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = likeCount.toString(),
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
                
                // Reply button
                Text(
                    text = "Reply",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.clickable {
                        // TODO: Implement reply functionality
                        android.widget.Toast.makeText(context, "Reply coming soon!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
                
                // Time ago
                Text(
                    text = getTimeAgo(comment.created_at ?: 0L),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

fun getTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> "${diff / 3600000}h ago"
        diff < 604800000 -> "${diff / 86400000}d ago"
        else -> "${diff / 604800000}w ago"
    }
}
