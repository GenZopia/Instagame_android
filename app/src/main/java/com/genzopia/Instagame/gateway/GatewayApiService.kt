package com.genzopia.Instagame.gateway

import retrofit2.Response
import retrofit2.http.*
import okhttp3.MultipartBody

// ── DTOs ──────────────────────────────────────────────────────────────────────

data class ReelDTO(
    val videoId: String = "",
    val title: String = "",
    val description: String = "",
    val likeCount: String = "0",
    val viewCount: String = "0",
    val commentCount: String = "0",
    val developerId: String = "",
    val developerName: String = "",
    val developerPhotoUrl: String? = null,
    val gameId: String = "",
    val gameName: String = "",
    val gameImageUrl: String = "",
    val playbackUrl: String? = null,
    val hlsManifestUrl: String? = null,
    val isLiked: Boolean = false,
    val isFollowing: Boolean = false,
    val timestamp: Long = 0L
)

data class ReelsPageResponse(
    val data: List<ReelDTO> = emptyList(),
    val nextCursor: String? = null
)

data class LikeResponse(
    val liked: Boolean = false,
    val likeCount: Int = 0
)

data class FollowResponse(
    val following: Boolean = false
)

data class CommentDTO(
    val comment_id: String = "",
    val user_id: String = "",
    val user_display_name: String = "",
    val user_photo_url: String? = null,
    val text: String = "",
    val created_at: Long = 0L,
    val like_count: Long = 0L,
    val dislike_count: Long = 0L,
    val reply_count: Long = 0L,
    val isLiked: Boolean = false,
    val isDisliked: Boolean = false
)

data class CommentsPageResponse(
    val data: List<CommentDTO> = emptyList(),
    val nextCursor: Long? = null,
    val hasMore: Boolean = false
)

data class ReplyDTO(
    val reply_id: String = "",
    val parent_comment_id: String = "",
    val user_id: String = "",
    val user_display_name: String = "",
    val user_photo_url: String? = null,
    val text: String = "",
    val created_at: Long = 0L,
    val like_count: Long = 0L,
    val dislike_count: Long = 0L
)

data class RepliesPageResponse(
    val data: List<ReplyDTO> = emptyList(),
    val nextCursor: Long? = null,
    val hasMore: Boolean = false
)

data class PostCommentRequest(
    val text: String,
    val displayName: String,
    val photoUrl: String?
)

data class PostReplyRequest(
    val text: String,
    val displayName: String,
    val photoUrl: String?
)

data class ChannelDTO(
    val developerId: String = "",
    val fullName: String = "",
    val bio: String = "",
    val profilePhotoUrl: String? = null,
    val bannerUrl: String? = null,
    val followersCount: Long = 0L,
    val website: String = "",
    val story: String = "",
    val videoCount: Int = 0,
    val gameCount: Int = 0,
    val isFollowing: Boolean = false
)

data class ChannelGameDTO(
    val gameId: String = "",
    val gameName: String = "",
    val gameImageUrl: String = ""
)

/** Wrapper for GET /channels/:developerId/games — gateway returns { data: [...] } */
data class ChannelGamesResponse(
    val data: List<ChannelGameDTO> = emptyList()
)

data class LaunchUrlResponse(
    val url: String = "",
    val launchUrl: String = "",
    val linkType: String = "",
    val gameId: String = "",
    val gameName: String = "",
    val orientation: String = "landscape",
    val description: String = "",
    val category: String = "",
    val compatibility: String = "both",
    val photoId: String = "",
    val userId: String = "",
    val isVerified: Boolean = false,
    val versionNumber: String = "",
    val playCount: String = "0",
    val createdAt: String = ""
) {
    fun resolvedUrl(): String = launchUrl.ifEmpty { url }
}

data class GameListItem(
    val gameId: String = "",
    val gameName: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val developerId: String = "",
    val developerName: String = "",
    val developerPhotoUrl: String = ""
)

data class GameListResponse(
    val data: List<GameListItem> = emptyList()
)

data class UserProfileDTO(
    val userId: String = "",
    val full_name: String = "",
    val bio: String = "",
    val website: String = "",
    val phone: String = "",
    val story: String = "",
    val profile_photo_url: String? = null,
    val banner_url: String? = null,
    val followers_count: Long = 0L
)

data class UpdateProfileRequest(
    val full_name: String? = null,
    val bio: String? = null,
    val website: String? = null,
    val phone: String? = null,
    val story: String? = null
)

data class FcmTokenRequest(
    val token: String
)

data class FollowedUserDTO(
    val userId: String = "",
    val full_name: String = "",
    val profile_photo_url: String? = null
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val fullName: String,
    val dateOfBirth: String,
    val mobileNo: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class GoogleLoginRequest(
    val googleIdToken: String
)

data class AuthResponse(
    val idToken: String = "",
    val userId: String = "",
    val email: String = "",
    val fullName: String = "",
    val profilePhotoUrl: String? = null
)

data class ForgotPasswordRequest(
    val email: String
)

data class ProfileStatusResponse(
    val exists: Boolean = false,
    val needsCompletion: Boolean = false,
    val missingFields: List<String> = emptyList()
)

data class PrefetchVideoItem(
    val videoId: String = "",
    val title: String = "",
    val userId: String = "",
    val gameId: String = "",
    val developerName: String = "",
    val developerPhotoUrl: String? = null,
    val playbackUrl: String? = null,
    val hlsManifestUrl: String? = null
)

data class PrefetchResponse(
    val data: List<PrefetchVideoItem> = emptyList()
)

data class HlsConvertResponse(
    val taskId: String = "",
    val status: String = "",
    val message: String = ""
)

data class HlsStatusResponse(
    val status: String = "",
    val hlsManifestKey: String? = null
)

data class AppConfigResponse(
    val force_popup_minimum_version: String = "0",
    val smooth_popup_minimum_version: String = "0"
)

data class ProfileStoreResponse(
    val success: Boolean = false,
    val key: String = ""
)

data class ProfileRetrieveResponse(
    val imageUrl: String = "",
    val source: String = ""  // "google" or "worker"
)

// ── Retrofit interface ────────────────────────────────────────────────────────

interface GatewayApiService {

    // Health
    @GET("health")
    suspend fun health(): Response<Map<String, Any>>

    // Reels feed
    @GET("reels")
    suspend fun getReels(
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int = 20
    ): Response<ReelsPageResponse>

    // Reel like / unlike
    @POST("reels/{videoId}/like")
    suspend fun likeReel(@Path("videoId") videoId: String): Response<LikeResponse>

    @DELETE("reels/{videoId}/like")
    suspend fun unlikeReel(@Path("videoId") videoId: String): Response<LikeResponse>

    // View count
    @POST("reels/{videoId}/view")
    suspend fun recordView(@Path("videoId") videoId: String): Response<Void>

    // Share count
    @POST("reels/{videoId}/share")
    suspend fun recordShare(@Path("videoId") videoId: String): Response<Void>

    // Comments
    @GET("reels/{videoId}/comments")
    suspend fun getComments(
        @Path("videoId") videoId: String,
        @Query("cursor") cursor: Long?,
        @Query("limit") limit: Int = 20
    ): Response<CommentsPageResponse>

    @POST("reels/{videoId}/comments")
    suspend fun postComment(
        @Path("videoId") videoId: String,
        @Body body: PostCommentRequest
    ): Response<CommentDTO>

    @POST("reels/{videoId}/comments/{commentId}/like")
    suspend fun likeComment(
        @Path("videoId") videoId: String,
        @Path("commentId") commentId: String
    ): Response<LikeResponse>

    @DELETE("reels/{videoId}/comments/{commentId}/like")
    suspend fun unlikeComment(
        @Path("videoId") videoId: String,
        @Path("commentId") commentId: String
    ): Response<LikeResponse>

    @POST("reels/{videoId}/comments/{commentId}/dislike")
    suspend fun dislikeComment(
        @Path("videoId") videoId: String,
        @Path("commentId") commentId: String
    ): Response<Void>

    @DELETE("reels/{videoId}/comments/{commentId}/dislike")
    suspend fun undislikeComment(
        @Path("videoId") videoId: String,
        @Path("commentId") commentId: String
    ): Response<Void>

    @POST("reels/{videoId}/comments/{commentId}/replies")
    suspend fun postReply(
        @Path("videoId") videoId: String,
        @Path("commentId") commentId: String,
        @Body body: PostReplyRequest
    ): Response<ReplyDTO>

    // Channels
    @GET("channels/{developerId}")
    suspend fun getChannel(@Path("developerId") developerId: String): Response<ChannelDTO>

    @GET("channels/{developerId}/games")
    suspend fun getChannelGames(@Path("developerId") developerId: String): Response<ChannelGamesResponse>

    @GET("channels/{developerId}/videos")
    suspend fun getChannelVideos(@Path("developerId") developerId: String): Response<ReelsPageResponse>

    // Games
    @GET("games")
    suspend fun getGames(): Response<GameListResponse>

    @GET("games/{gameId}/launch-url")
    suspend fun getGameLaunchUrl(@Path("gameId") gameId: String): Response<LaunchUrlResponse>

    // Users / profile
    @GET("users/me")
    suspend fun getMyProfile(): Response<UserProfileDTO>

    @PATCH("users/me")
    suspend fun updateMyProfile(@Body body: UpdateProfileRequest): Response<UserProfileDTO>

    @POST("users/me/fcm-token")
    suspend fun registerFcmToken(@Body body: FcmTokenRequest): Response<Void>

    @GET("users/me/following")
    suspend fun getFollowing(): Response<List<FollowedUserDTO>>

    // Follow / unfollow
    @POST("users/{developerId}/follow")
    suspend fun followUser(@Path("developerId") developerId: String): Response<FollowResponse>

    @DELETE("users/{developerId}/follow")
    suspend fun unfollowUser(@Path("developerId") developerId: String): Response<FollowResponse>

    // Auth
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun loginEmail(@Body body: LoginRequest): Response<AuthResponse>

    @POST("auth/google")
    suspend fun loginGoogle(@Body body: GoogleLoginRequest): Response<AuthResponse>

    @POST("auth/logout")
    suspend fun logout(): Response<Void>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): Response<Void>

    @GET("auth/profile-status/{uid}")
    suspend fun getProfileStatus(@Path("uid") uid: String): Response<ProfileStatusResponse>

    // Prefetch
    @GET("videos/prefetch")
    suspend fun getPrefetch(@Query("limit") limit: Int = 30): Response<PrefetchResponse>

    // HLS conversion
    @POST("videos/{videoId}/hls-convert")
    suspend fun triggerHlsConversion(@Path("videoId") videoId: String): Response<HlsConvertResponse>

    @GET("videos/{videoId}/hls-status/{taskId}")
    suspend fun getHlsStatus(
        @Path("videoId") videoId: String,
        @Path("taskId") taskId: String
    ): Response<HlsStatusResponse>

    @GET("app-config")
    suspend fun getAppConfig(): Response<AppConfigResponse>

    // Profile photo store & retrieve
    @Multipart
    @POST("profile/store")
    suspend fun storeProfilePhoto(
        @Part profileId: okhttp3.MultipartBody.Part,
        @Part file: okhttp3.MultipartBody.Part
    ): Response<ProfileStoreResponse>

    @GET("profile/retrieve")
    suspend fun retrieveProfilePhoto(
        @Query("profileId") profileId: String
    ): Response<ProfileRetrieveResponse>
}
