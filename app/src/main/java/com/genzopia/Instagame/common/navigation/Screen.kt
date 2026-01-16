package com.genzopia.Instagame.common.navigation

/**
 * Sealed class representing all navigation destinations in the app.
 * Each screen has a unique route for navigation.
 */
sealed class Screen(val route: String) {
    
    /**
     * Home feed screen
     */
    object Home : Screen("home")
    
    /**
     * Reels/Dashboard screen
     */
    object Reels : Screen("reels")
    
    /**
     * Post/Upload screen
     */
    object Post : Screen("post")
    
    /**
     * Profile screen
     */
    object Profile : Screen("profile")
    
    /**
     * Notifications screen
     */
    object Notifications : Screen("notifications")
    
    /**
     * Channel screen for viewing a specific user's channel
     * 
     * @param userId The ID of the user whose channel to view
     */
    data class Channel(val userId: String) : Screen("channel/$userId") {
        companion object {
            const val route = "channel/{userId}"
        }
    }
    
    /**
     * Comments screen for a specific video
     * 
     * @param videoId The ID of the video to show comments for
     */
    data class Comments(val videoId: String) : Screen("comments/$videoId") {
        companion object {
            const val route = "comments/{videoId}"
        }
    }
    
    /**
     * Video detail screen
     * 
     * @param videoId The ID of the video to display
     */
    data class VideoDetail(val videoId: String) : Screen("video/$videoId") {
        companion object {
            const val route = "video/{videoId}"
        }
    }
    
    /**
     * Edit profile screen
     */
    object EditProfile : Screen("edit_profile")
    
    /**
     * Login screen
     */
    object Login : Screen("login")
    
    /**
     * Register screen
     */
    object Register : Screen("register")
    
    /**
     * Forgot password screen
     */
    object ForgotPassword : Screen("forgot_password")
}
