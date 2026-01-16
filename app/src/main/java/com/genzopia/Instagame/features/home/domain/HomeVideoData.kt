package com.genzopia.Instagame.ui.home.compose

/**
 * Data class for home feed videos
 */
data class HomeVideoData(
    val videoId: String,
    val videoUrl: String? = null,
    val title: String,
    val description: String = "",
    val viewCount: String = "0",
    val likeCount: String = "0",
    val developerId: String = "",
    val developerName: String = "",
    val developerPhotoUrl: String? = null,
    val gameId: String = "",
    val gameName: String = "",
    val thumbnailUrl: String? = null,
    val timestamp: Long = 0,
    val isLiked: Boolean = false,
    val isFollowing: Boolean = false
)
