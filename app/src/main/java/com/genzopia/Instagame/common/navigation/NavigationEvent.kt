package com.genzopia.Instagame.common.navigation

/**
 * Sealed class representing navigation events that can be triggered from ViewModels.
 */
sealed class NavigationEvent {
    
    /**
     * Navigate to a user's channel
     * 
     * @param userId The ID of the user whose channel to view
     */
    data class NavigateToChannel(val userId: String) : NavigationEvent()
    
    /**
     * Navigate to comments for a video
     * 
     * @param videoId The ID of the video to show comments for
     */
    data class NavigateToComments(val videoId: String) : NavigationEvent()
    
    /**
     * Navigate to video detail screen
     * 
     * @param videoId The ID of the video to display
     */
    data class NavigateToVideoDetail(val videoId: String) : NavigationEvent()
    
    /**
     * Navigate to profile screen
     * 
     * @param userId The ID of the user profile to view (null for current user)
     */
    data class NavigateToProfile(val userId: String? = null) : NavigationEvent()
    
    /**
     * Navigate to edit profile screen
     */
    object NavigateToEditProfile : NavigationEvent()
    
    /**
     * Navigate to post/upload screen
     */
    object NavigateToPost : NavigationEvent()
    
    /**
     * Navigate to home screen
     */
    object NavigateToHome : NavigationEvent()
    
    /**
     * Navigate to reels screen
     */
    object NavigateToReels : NavigationEvent()
    
    /**
     * Navigate to login screen
     */
    object NavigateToLogin : NavigationEvent()
    
    /**
     * Navigate to register screen
     */
    object NavigateToRegister : NavigationEvent()
    
    /**
     * Navigate back to previous screen
     */
    object NavigateBack : NavigationEvent()
}
