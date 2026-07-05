package com.genzopia.Instagame.features.home.data

import android.util.Log
import com.genzopia.Instagame.features.home.domain.FollowedUser
import com.genzopia.Instagame.gateway.GatewayClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Repository for fetching the list of users the current user follows.
 * All data now comes from the backend Gateway (GET /users/me/following).
 *
 * Requirements: 13.1, 13.2
 */
class FollowingRepository {

    companion object {
        private const val TAG = "FollowingRepository"
    }

    /**
     * Returns a Flow that emits the list of followed users once from the gateway.
     * The previous real-time Firebase listener is replaced with a single gateway call;
     * callers that need live updates should re-collect the flow on user actions.
     */
    fun getFollowedUsers(): Flow<List<FollowedUser>> = flow {
        try {
            val response = GatewayClient.api.getFollowing()
            if (response.isSuccessful) {
                val users = (response.body() ?: emptyList()).map { dto ->
                    FollowedUser(
                        userId        = dto.userId,
                        fullName      = dto.full_name,
                        profilePhotoUrl = dto.profile_photo_url
                    )
                }
                Log.d(TAG, "Loaded ${users.size} followed users from gateway")
                emit(users)
            } else {
                Log.e(TAG, "Gateway /users/me/following returned ${response.code()}")
                emit(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching following list from gateway", e)
            emit(emptyList())
        }
    }
}
