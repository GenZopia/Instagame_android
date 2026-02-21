package com.genzopia.Instagame.features.home.domain

/**
 * Domain model representing a user that the current user follows.
 * Used in the Instagram-style stories bar at the top of the home feed.
 */
data class FollowedUser(
    val userId: String,
    val fullName: String,
    val profilePhotoUrl: String?
)
