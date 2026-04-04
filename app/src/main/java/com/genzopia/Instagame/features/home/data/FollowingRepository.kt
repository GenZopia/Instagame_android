package com.genzopia.Instagame.features.home.data

import android.util.Log
import com.genzopia.Instagame.features.home.domain.FollowedUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository for fetching the list of users that the current user follows.
 * Reads from Firebase: follows/{currentUserId} → list of followed user IDs,
 * then fetches each user's profile from users/{userId}.
 */
class FollowingRepository {

    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "FollowingRepository"
    }

    /**
     * Returns a Flow that emits the list of followed users whenever it changes.
     * Listens to the follows/{currentUserId} node in real-time.
     */
    fun getFollowedUsers(): Flow<List<FollowedUser>> = callbackFlow {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Log.w(TAG, "No authenticated user, returning empty list")
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val followsRef = database.reference.child("users").child(currentUserId).child("following_list")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val followedUserIds = snapshot.children.mapNotNull { it.key }
                Log.d(TAG, "User $currentUserId follows ${followedUserIds.size} users")

                if (followedUserIds.isEmpty()) {
                    trySend(emptyList())
                    return
                }

                // Fetch profile info for each followed user
                var loadedCount = 0
                val users = mutableListOf<FollowedUser>()

                for (userId in followedUserIds) {
                    database.reference.child("users").child(userId)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(userSnapshot: DataSnapshot) {
                                val fullName = userSnapshot.child("full_name")
                                    .getValue(String::class.java)
                                    ?: userSnapshot.child("name")
                                        .getValue(String::class.java)
                                    ?: userSnapshot.child("username")
                                        .getValue(String::class.java)
                                    ?: "User"

                                val profilePhotoUrl = com.genzopia.Instagame.utils.ProfilePhotoUtils.sanitize(
                                    userSnapshot.child("profile_photo_url")
                                        .getValue(String::class.java)
                                        ?: userSnapshot.child("profile_image_url")
                                            .getValue(String::class.java)
                                        ?: userSnapshot.child("photoUrl")
                                            .getValue(String::class.java)
                                )

                                users.add(
                                    FollowedUser(
                                        userId = userId,
                                        fullName = fullName,
                                        profilePhotoUrl = profilePhotoUrl
                                    )
                                )

                                loadedCount++
                                if (loadedCount == followedUserIds.size) {
                                    Log.d(TAG, "Loaded ${users.size} followed user profiles")
                                    trySend(users.toList())
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                Log.e(TAG, "Error fetching user $userId", error.toException())
                                loadedCount++
                                if (loadedCount == followedUserIds.size) {
                                    trySend(users.toList())
                                }
                            }
                        })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error fetching follows list", error.toException())
                trySend(emptyList())
            }
        }

        followsRef.addValueEventListener(listener)

        awaitClose {
            followsRef.removeEventListener(listener)
        }
    }
}
