package com.genzopia.Instagame.gateway

import retrofit2.Call
import retrofit2.http.*

/**
 * Retrofit interface using [Call] return types so Java code can call
 * `.enqueue()` directly without coroutine machinery.
 *
 * Kotlin code should use [GatewayApiService] (suspend functions).
 * Java code (CommentsRepository, ChannelActivity, etc.) should use this.
 */
interface GatewayCallService {

    @POST("reels/{videoId}/like")
    fun likeReel(@Path("videoId") videoId: String): Call<LikeResponse>

    @DELETE("reels/{videoId}/like")
    fun unlikeReel(@Path("videoId") videoId: String): Call<LikeResponse>

    @GET("reels/{videoId}/comments")
    fun getComments(
        @Path("videoId") videoId: String,
        @Query("cursor") cursor: Long?,
        @Query("limit") limit: Int
    ): Call<CommentsPageResponse>

    @POST("reels/{videoId}/comments")
    fun postComment(
        @Path("videoId") videoId: String,
        @Body body: PostCommentRequest
    ): Call<CommentDTO>

    @POST("reels/{videoId}/comments/{commentId}/like")
    fun likeComment(
        @Path("videoId") videoId: String,
        @Path("commentId") commentId: String
    ): Call<LikeResponse>

    @DELETE("reels/{videoId}/comments/{commentId}/like")
    fun unlikeComment(
        @Path("videoId") videoId: String,
        @Path("commentId") commentId: String
    ): Call<LikeResponse>

    @POST("reels/{videoId}/comments/{commentId}/dislike")
    fun dislikeComment(
        @Path("videoId") videoId: String,
        @Path("commentId") commentId: String
    ): Call<Void>

    @DELETE("reels/{videoId}/comments/{commentId}/dislike")
    fun undislikeComment(
        @Path("videoId") videoId: String,
        @Path("commentId") commentId: String
    ): Call<Void>

    @POST("reels/{videoId}/comments/{commentId}/replies")
    fun postReply(
        @Path("videoId") videoId: String,
        @Path("commentId") commentId: String,
        @Body body: PostReplyRequest
    ): Call<ReplyDTO>

    @POST("reels/{videoId}/comments/{commentId}/report")
    fun reportComment(
        @Path("videoId") videoId: String,
        @Path("commentId") commentId: String,
        @Body body: Map<String, String>
    ): Call<Void>

    @POST("reels/{videoId}/comments/{commentId}/report")
    fun reportComment(
        @Path("videoId") videoId: String,
        @Path("commentId") commentId: String
    ): Call<Void>

    @GET("channels/{developerId}")
    fun getChannel(@Path("developerId") developerId: String): Call<ChannelDTO>

    @GET("channels/{developerId}/games")
    fun getChannelGames(@Path("developerId") developerId: String): Call<ChannelGamesResponse>

    @GET("channels/{developerId}/videos")
    fun getChannelVideos(@Path("developerId") developerId: String): Call<ReelsPageResponse>

    @POST("users/{developerId}/follow")
    fun followUser(@Path("developerId") developerId: String): Call<FollowResponse>

    @DELETE("users/{developerId}/follow")
    fun unfollowUser(@Path("developerId") developerId: String): Call<FollowResponse>

    @GET("games/{gameId}/launch-url")
    fun getGameLaunchUrl(@Path("gameId") gameId: String): Call<LaunchUrlResponse>

    @POST("reels/{videoId}/view")
    fun recordView(@Path("videoId") videoId: String): Call<Void>

    @POST("reels/{videoId}/share")
    fun recordShare(@Path("videoId") videoId: String): Call<Void>

    @PATCH("reels/{videoId}")
    fun updateVideo(
        @Path("videoId") videoId: String,
        @Body body: Map<String, String>
    ): Call<Void>

    @DELETE("reels/{videoId}")
    fun deleteVideo(@Path("videoId") videoId: String): Call<Void>

    @POST("users/me/fcm-token")
    fun registerFcmToken(@Body body: FcmTokenRequest): Call<Void>

    @GET("users/me")
    fun getMyProfile(): Call<UserProfileDTO>

    @PATCH("users/me")
    fun updateMyProfile(@Body body: UpdateProfileRequest): Call<UserProfileDTO>

    @GET("users/me/following")
    fun getFollowing(): Call<List<FollowedUserDTO>>

    @POST("auth/forgot-password")
    fun forgotPassword(@Body body: ForgotPasswordRequest): Call<Void>

    @GET("auth/profile-status/{uid}")
    fun getProfileStatus(@Path("uid") uid: String): Call<ProfileStatusResponse>

    @GET("videos/prefetch")
    fun getPrefetch(@Query("limit") limit: Int): Call<PrefetchResponse>

    @POST("videos/{videoId}/hls-convert")
    fun triggerHlsConversion(@Path("videoId") videoId: String): Call<HlsConvertResponse>

    @GET("videos/{videoId}/hls-status/{taskId}")
    fun getHlsStatus(
        @Path("videoId") videoId: String,
        @Path("taskId") taskId: String
    ): Call<HlsStatusResponse>

    @GET("app-config")
    fun getAppConfig(): Call<AppConfigResponse>

    @GET("profile/retrieve")
    fun retrieveProfilePhoto(@Query("profileId") profileId: String): Call<ProfileRetrieveResponse>
}
