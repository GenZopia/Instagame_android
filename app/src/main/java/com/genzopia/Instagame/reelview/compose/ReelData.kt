package com.genzopia.Instagame.reelview.compose

/**
 * Data class representing a single reel/video
 */
data class ReelData(
    val videoId: String,
    val videoUrl: String? = null,
    val title: String,
    val description: String = "",
    val likeCount: String = "0",
    val developerId: String = "",
    val developerName: String = "",
    val developerPhotoUrl: String? = null,
    val gameId: String = "",
    val gameName: String = "",
    val isLiked: Boolean = false,
    val isFollowing: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
