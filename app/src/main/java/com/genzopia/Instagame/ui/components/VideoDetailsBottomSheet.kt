package com.genzopia.Instagame.ui.components

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genzopia.Instagame.R

private val Orange = Color(0xFFFF6B35)

/**
 * Compose content for the Video Details Bottom Sheet.
 *
 * NOTE: This is NOT wrapped in [ModalBottomSheet] because it's hosted inside a
 * [BottomSheetDialogFragment] which already provides the sheet container, scrim,
 * drag handle, and animation. Wrapping it in another [ModalBottomSheet] would
 * create a double-sheet conflict.
 *
 * All video data is passed directly as parameters — no Firebase loading.
 * This aligns with removing videoId-based Firebase queries from the sheet.
 */
@Composable
fun VideoDetailsBottomSheet(
    // videoId kept only for share link generation
    videoId: String,
    title: String,
    description: String,
    viewCount: String = "0",
    likeCount: String = "0",
    shareCount: String = "0",
    uploadDate: String = "",
    gameId: String = "",
    gameName: String = "",
    channelName: String = "",
    developerId: String = "",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isDark) Color.White else Color.Black
    val subColor = if (isDark) Color(0xFFEBEBF5) else Color(0xFF8E8E93)

    val hasGame = gameId.isNotEmpty()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(bottom = 32.dp)
    ) {
        // ── Drag Handle ───────────────────────────────────────────────
        Box(
            modifier = Modifier
                .padding(top = 12.dp, bottom = 16.dp)
                .align(Alignment.CenterHorizontally)
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(
                        color = if (isDark) Color(0xFF38383A) else Color(0xFFDBDBDB),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }

        // ── Video Title ───────────────────────────────────────────────
        Text(
            text = title.ifEmpty { "Untitled Video" },
            color = textColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        )

        // ── Primary Action Area (Always Visible — from PR #15) ─────
        // Play Now, Share, and Report buttons stay at the top so they're
        // always visible regardless of scroll position.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Play Now button — only shown when gameId is present
            if (hasGame) {
                Button(
                    onClick = {
                        val intent = Intent(
                            context,
                            com.genzopia.Instagame.webgl_gameloading.Game_mode::class.java
                        )
                        intent.putExtra("game_id", gameId)
                        context.startActivity(intent)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Orange
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(2f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Play Now",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Share button
            OutlinedButton(
                onClick = {
                    val deepLink = "https://instagame.genzopia.com/video/$videoId"
                    val customSchemeLink = "instagame://video/$videoId"
                    val playStoreUrl = "https://play.google.com/store/apps/details?id=com.genzopia.Instagame"
                    val shareText = buildString {
                        append("🎮 Check out \"$title\" on Instagame!\n\n")
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
                    context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = textColor
                ),
                border = BorderStroke(1.dp, SolidColor(subColor.copy(alpha = 0.3f))),
                modifier = Modifier
                    .weight(if (hasGame) 1f else 1.5f)
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Orange
                )
                Spacer(Modifier.width(4.dp))
                Text("Share", fontSize = 13.sp)
            }

            // Report button — uses existing drawable for safety
            OutlinedButton(
                onClick = {
                    android.widget.Toast
                        .makeText(context, "Report functionality coming soon!", android.widget.Toast.LENGTH_SHORT)
                        .show()
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = textColor
                ),
                border = BorderStroke(1.dp, SolidColor(subColor.copy(alpha = 0.3f))),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_report),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Orange
                )
                Spacer(Modifier.width(4.dp))
                Text("Report", fontSize = 13.sp)
            }
        }

        // ── Stats Row ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                icon = Icons.Filled.Visibility,
                value = formatCountSafe(viewCount),
                label = "Views",
                textColor = textColor,
                subColor = subColor
            )
            StatItem(
                icon = Icons.Filled.Favorite,
                value = formatCountSafe(likeCount),
                label = "Likes",
                textColor = textColor,
                subColor = subColor
            )
            StatItem(
                icon = Icons.Filled.Share,
                value = formatCountSafe(shareCount),
                label = "Shares",
                textColor = textColor,
                subColor = subColor
            )
        }

        // ── Divider ───────────────────────────────────────────────────
        HorizontalDivider(
            color = if (isDark) Color(0xFF38383A) else Color(0xFFF0F0F0),
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        // ── Scrollable details area ─────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
        ) {
            // Description header with close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Description",
                    color = textColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                // Close button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = subColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Description text
            Text(
                text = description.ifEmpty { "No description available" },
                color = subColor,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Game name if present
            if (gameName.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = "@$gameName",
                        color = Orange,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Upload date
            Text(
                text = if (uploadDate.isNotEmpty()) "Uploaded on $uploadDate" else "",
                color = subColor.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Bottom spacer for scroll comfort
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    value: String,
    label: String,
    textColor: Color,
    subColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Orange,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            color = textColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = subColor,
            fontSize = 12.sp
        )
    }
}

private fun formatCountSafe(count: String): String {
    val num = count.toLongOrNull() ?: return if (count.isEmpty()) "0" else count
    return when {
        num < 1_000 -> num.toString()
        num < 1_000_000 -> String.format("%.1fK", num / 1000.0)
        else -> String.format("%.1fM", num / 1_000_000.0)
    }
}
