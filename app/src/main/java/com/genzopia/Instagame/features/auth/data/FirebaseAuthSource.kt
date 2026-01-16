package com.genzopia.Instagame.features.auth.data

import com.genzopia.Instagame.common.models.DataError
import com.genzopia.Instagame.features.auth.domain.User
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Data source for Firebase Authentication operations.
 * Handles all direct Firebase Auth and Realtime Database interactions.
 */
class FirebaseAuthSource {
    
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference
    
    /**
     * Get the current authenticated user ID
     */
    fun getCurrentUserId(): String? = auth.currentUser?.uid
    
    /**
     * Check if a user is currently authenticated
     */
    fun isUserAuthenticated(): Boolean = auth.currentUser != null
    
    /**
     * Sign in with email and password
     */
    suspend fun signInWithEmail(email: String, password: String): Result<User> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val userId = authResult.user?.uid 
                ?: return Result.failure(DataError.Unauthorized)
            
            // Fetch user data from database
            fetchUserData(userId)
        } catch (e: Exception) {
            Result.failure(DataError.Firebase("auth_error", e.message ?: "Authentication failed"))
        }
    }
    
    /**
     * Sign up with email and password
     */
    suspend fun signUpWithEmail(
        email: String, 
        password: String, 
        fullName: String,
        dateOfBirth: String,
        mobileNo: String
    ): Result<User> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val userId = authResult.user?.uid 
                ?: return Result.failure(DataError.Unauthorized)
            
            // Create user profile
            val user = User(
                userId = userId,
                email = email,
                fullName = fullName,
                dateOfBirth = dateOfBirth,
                mobileNo = mobileNo,
                profilePhotoUrl = "",
                followers = "0"
            )
            
            // Save to database
            database.child("users").child(userId).setValue(user.toLegacyUser()).await()
            
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(DataError.Firebase("signup_error", e.message ?: "Sign up failed"))
        }
    }
    
    /**
     * Sign in with Google account
     */
    suspend fun signInWithGoogle(account: GoogleSignInAccount): Result<User> {
        return try {
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val userId = authResult.user?.uid 
                ?: return Result.failure(DataError.Unauthorized)
            
            // Check if user exists
            val userSnapshot = database.child("users").child(userId).get().await()
            
            if (!userSnapshot.exists()) {
                // New user - create profile
                val user = User(
                    userId = userId,
                    email = account.email ?: "",
                    fullName = account.displayName ?: "",
                    dateOfBirth = "",
                    mobileNo = "",
                    profilePhotoUrl = account.photoUrl?.toString() ?: "",
                    followers = "0"
                )
                
                database.child("users").child(userId).setValue(user.toLegacyUser()).await()
                Result.success(user)
            } else {
                // Existing user - fetch data
                fetchUserData(userId)
            }
        } catch (e: Exception) {
            Result.failure(DataError.Firebase("google_signin_error", e.message ?: "Google sign in failed"))
        }
    }
    
    /**
     * Send password reset email
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(DataError.Firebase("reset_error", e.message ?: "Failed to send reset email"))
        }
    }
    
    /**
     * Sign out the current user
     */
    fun signOut() {
        auth.signOut()
    }
    
    /**
     * Fetch user data from database (public for repository use)
     */
    suspend fun fetchUserData(userId: String): Result<User> {
        return suspendCoroutine { continuation ->
            database.child("users").child(userId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val legacyUser = snapshot.getValue(
                            com.genzopia.Instagame.LoginActivities.User::class.java
                        )
                        
                        if (legacyUser != null) {
                            continuation.resume(Result.success(User.fromLegacyUser(legacyUser)))
                        } else {
                            continuation.resume(
                                Result.failure(DataError.NotFound)
                            )
                        }
                    }
                    
                    override fun onCancelled(error: DatabaseError) {
                        continuation.resume(
                            Result.failure(
                                DataError.Firebase("db_error", error.message)
                            )
                        )
                    }
                })
        }
    }
    
    /**
     * Observe user data changes
     */
    fun observeUserData(userId: String): Flow<User> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val legacyUser = snapshot.getValue(
                    com.genzopia.Instagame.LoginActivities.User::class.java
                )
                if (legacyUser != null) {
                    trySend(User.fromLegacyUser(legacyUser))
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                close(DataError.Firebase("db_error", error.message))
            }
        }
        
        database.child("users").child(userId).addValueEventListener(listener)
        
        awaitClose {
            database.child("users").child(userId).removeEventListener(listener)
        }
    }
}
