package com.genzopia.Instagame.reelview.compose

/**
 * Data class representing a single reel/video
 */
data class ReelData(
    val videoId: String,
    val videoUrl: String? = null,          // MP4 signed URL (for direct playback)
    val hlsManifestUrl: String? = null,    // HLS .m3u8 URL (preferred when present)
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
) {
    /** The best URL to feed ExoPlayer — HLS manifest takes priority over MP4 */
    val playbackUrl: String? get() = hlsManifestUrl ?: videoUrl
}
