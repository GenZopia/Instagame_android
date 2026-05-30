package com.genzopia.Instagame.features.home.ui

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

private val StoryGradient = Brush.sweepGradient(
    colors = listOf(
        Color(0xFFFF6B35),
        Color(0xFFFF3CAC),
        Color(0xFF784BA0),
        Color(0xFF2B86C5),
        Color(0xFFFF6B35),
    )
)

@Composable
fun FollowingStoriesBar(
    users: List<FollowedUser>,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) Color(0xFF0E0E0E) else Color(0xFFFAFAFA)
    val textColor = if (isDark) Color(0xFFE0E0E0) else Color(0xFF1A1A1A)
    val dividerColor = if (isDark) Color(0xFF1F1F1F) else Color(0xFFEEEEEE)

    if (isLoading || users.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFFF6B35))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Following",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = textColor,
                letterSpacing = 0.3.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${users.size}",
                fontSize = 12.sp,
                color = Color(0xFFFF6B35),
                fontWeight = FontWeight.SemiBold
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(items = users, key = { it.userId }) { user ->
                StoryItem(user = user, textColor = textColor)
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(dividerColor)
        )
    }
}

@Composable
private fun StoryItem(user: FollowedUser, textColor: Color) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val avatarBg = if (isDark) Color(0xFF2A2A2A) else Color(0xFFE8E8E8)

    Column(
        modifier = Modifier
            .width(68.dp)
            .clickable {
                try {
                    com.genzopia.Instagame.analytics.InstagameAnalytics.trackFollowingUserTapped(
                        targetUid = user.userId,
                        targetName = user.fullName
                    )
                    val intent = Intent(context, ChannelActivity::class.java)
                    intent.putExtra("developer_id", user.userId)
                    intent.putExtra("channel_source", "home_following_bar")
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
                .size(60.dp)
                .background(StoryGradient, CircleShape)
                .padding(2.5.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isDark) Color(0xFF0E0E0E) else Color.White, CircleShape)
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(user.profilePhotoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = user.fullName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(avatarBg),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(Modifier.height(5.dp))

        Text(
            text = user.fullName.split(" ").first(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
