package com.genzopia.Instagame.features.auth.domain

/**
 * Domain model representing a user in the system.
 * This is the Kotlin version of the User model with improved naming conventions.
 */
data class User(
    val userId: String = "",
    val email: String = "",
    val fullName: String = "",
    val dateOfBirth: String = "",
    val mobileNo: String = "",
    val profilePhotoUrl: String = "",
    val followers: String = "0",
    val following: String = "0",
    val bio: String = "",
    val website: String = "",
    val isVerified: Boolean = false
) {
    /**
     * Convert to the legacy User model for Firebase compatibility.
     * This will be removed once all code is migrated.
     */
    fun toLegacyUser(): com.genzopia.Instagame.LoginActivities.User {
        return com.genzopia.Instagame.LoginActivities.User().apply {
            setuser_id(userId)
            setEmail(email)
            setFull_name(fullName)
            setDate_of_birth(dateOfBirth)
            setMobile_no(mobileNo)
            setProfile_photo_url(profilePhotoUrl)
            setFollowers(followers)
        }
    }
    
    companion object {
        /**
         * Convert from the legacy User model.
         * This will be removed once all code is migrated.
         */
        fun fromLegacyUser(legacyUser: com.genzopia.Instagame.LoginActivities.User): User {
            return User(
                userId = legacyUser.getuser_id() ?: "",
                email = legacyUser.email ?: "",
                fullName = legacyUser.full_name ?: "",
                dateOfBirth = legacyUser.date_of_birth ?: "",
                mobileNo = legacyUser.mobile_no ?: "",
                profilePhotoUrl = legacyUser.profile_photo_url ?: "",
                followers = legacyUser.followers ?: "0"
            )
        }
    }
}
