package com.genzopia.Instagame.features.home.ui

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.genzopia.Instagame.channel_view.ChannelActivity
import com.genzopia.Instagame.features.home.domain.FollowedUser

// Instagram-style gradient for the story ring
private val StoryGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFFDE0046),
        Color(0xFFF7A34B),
        Color(0xFFFED373),
        Color(0xFFDE0046),
    )
)

// Height for one story item (circle 72dp + spacing 4dp + text ~14dp)
private val STORY_ITEM_HEIGHT = 94.dp

/**
 * Instagram-style horizontal grid showing circular profile pics of followed users
 * in two rows. Takes a list of FollowedUser directly.
 */
@Composable
fun FollowingStoriesBar(
    users: List<FollowedUser>,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()
    val backgroundColor = if (isDarkTheme) Color(0xFF121212) else Color.White
    val textColor = if (isDarkTheme) Color.White else Color.Black

    if (!isLoading && users.isNotEmpty()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(backgroundColor)
        ) {
            LazyHorizontalGrid(
                rows = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(STORY_ITEM_HEIGHT * 2 + 8.dp) // 2 rows + spacing
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                items(items = users, key = { it.userId }) { user ->
                    StoryItem(user = user, textColor = textColor)
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = if (isDarkTheme) Color(0xFF2A2A2A) else Color(0xFFDDDDDD)
            )
        }
    }
}

/**
 * Single circular profile pic with gradient ring + name below
 */
@Composable
private fun StoryItem(user: FollowedUser, textColor: Color) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .width(76.dp)
            .clickable {
                try {
                    val intent = Intent(context, ChannelActivity::class.java)
                    intent.putExtra("developer_id", user.userId)
                    intent.putExtra("user_id", user.userId)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e("FollowerDesign", "Error opening channel", e)
                    Toast.makeText(context, "Error opening profile", Toast.LENGTH_SHORT).show()
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Gradient ring
        Box(
            modifier = Modifier
                .size(72.dp)
                .border(width = 2.5.dp, brush = StoryGradient, shape = CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(user.profilePhotoUrl)
                    .crossfade(true)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .build(),
                contentDescription = "${user.fullName}'s profile",
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(Color.Gray),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = user.fullName,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Shimmer placeholder circle while loading
 */
@Composable
private fun ShimmerStoryItem() {
    val shimmerColor = if (isSystemInDarkTheme()) Color(0xFF2A2A2A) else Color(0xFFE8E8E8)

    Column(
        modifier = Modifier.width(76.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(shimmerColor)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(10.dp)
                .clip(CircleShape)
                .background(shimmerColor)
        )
    }
}
