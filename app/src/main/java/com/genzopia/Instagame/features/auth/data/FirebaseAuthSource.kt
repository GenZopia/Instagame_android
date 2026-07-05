package com.genzopia.Instagame.features.auth.data

import com.genzopia.Instagame.common.models.DataError
import com.genzopia.Instagame.features.auth.domain.User
import com.genzopia.Instagame.gateway.GatewayClient
import com.genzopia.Instagame.gateway.GoogleLoginRequest
import com.genzopia.Instagame.gateway.LoginRequest
import com.genzopia.Instagame.gateway.RegisterRequest
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow


class FirebaseAuthSource {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun isUserAuthenticated(): Boolean = auth.currentUser != null

    /**
     * Sign in with email and password via the Gateway.
     * Returns the locally-signed-in user on success.
     */
    suspend fun signInWithEmail(email: String, password: String): Result<User> {
        return try {
            val response = GatewayClient.api.loginEmail(LoginRequest(email, password))
            if (!response.isSuccessful || response.body() == null) {
                return Result.failure(DataError.Firebase("auth_error", "Login failed: ${response.code()}"))
            }
            // Re-sign in locally so FirebaseAuth.currentUser is populated for token refreshes
            auth.signInWithEmailAndPassword(email, password)
            val body = response.body()!!
            Result.success(
                User(
                    userId         = body.userId,
                    email          = body.email,
                    fullName       = body.fullName,
                    dateOfBirth    = "",
                    mobileNo       = "",
                    profilePhotoUrl = body.profilePhotoUrl ?: "",
                    followers      = "0"
                )
            )
        } catch (e: Exception) {
            Result.failure(DataError.Firebase("auth_error", e.message ?: "Authentication failed"))
        }
    }

    /**
     * Register with email and password via the Gateway.
     */
    suspend fun signUpWithEmail(
        email: String,
        password: String,
        fullName: String,
        dateOfBirth: String,
        mobileNo: String
    ): Result<User> {
        return try {
            val response = GatewayClient.api.register(
                RegisterRequest(email, password, fullName, dateOfBirth, mobileNo)
            )
            if (!response.isSuccessful || response.body() == null) {
                return Result.failure(DataError.Firebase("signup_error", "Registration failed: ${response.code()}"))
            }
            val body = response.body()!!
            // Sign in locally so FirebaseAuth.currentUser is populated
            auth.signInWithEmailAndPassword(email, password)
            Result.success(
                User(
                    userId         = body.userId,
                    email          = body.email,
                    fullName       = body.fullName,
                    dateOfBirth    = dateOfBirth,
                    mobileNo       = mobileNo,
                    profilePhotoUrl = body.profilePhotoUrl ?: "",
                    followers      = "0"
                )
            )
        } catch (e: Exception) {
            Result.failure(DataError.Firebase("signup_error", e.message ?: "Sign up failed"))
        }
    }

    /**
     * Sign in with a Google account via the Gateway.
     */
    suspend fun signInWithGoogle(account: GoogleSignInAccount): Result<User> {
        return try {
            val idToken = account.idToken
                ?: return Result.failure(DataError.Firebase("google_signin_error", "Missing Google ID token"))

            val response = GatewayClient.api.loginGoogle(GoogleLoginRequest(idToken))
            if (!response.isSuccessful || response.body() == null) {
                return Result.failure(DataError.Firebase("google_signin_error", "Google login failed: ${response.code()}"))
            }
            val body = response.body()!!
            Result.success(
                User(
                    userId         = body.userId,
                    email          = body.email,
                    fullName       = body.fullName,
                    dateOfBirth    = "",
                    mobileNo       = "",
                    profilePhotoUrl = body.profilePhotoUrl ?: account.photoUrl?.toString() ?: "",
                    followers      = "0"
                )
            )
        } catch (e: Exception) {
            Result.failure(DataError.Firebase("google_signin_error", e.message ?: "Google sign in failed"))
        }
    }

    /**
     * Send password reset email — still handled by Firebase client SDK directly
     * (no auth token needed for this operation).
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(DataError.Firebase("reset_error", e.message ?: "Failed to send reset email"))
        }
    }

    /** Sign out the current user locally. */
    fun signOut() {
        auth.signOut()
    }

    /**
     * Fetch user data from gateway GET /users/me (used after sign-in to hydrate the domain model).
     */
    suspend fun fetchUserData(userId: String): Result<User> {
        return try {
            val resp = GatewayClient.api.getMyProfile()
            if (resp.isSuccessful && resp.body() != null) {
                val p = resp.body()!!
                Result.success(
                    User(
                        userId = p.userId.ifEmpty { userId },
                        email = "",
                        fullName = p.full_name,
                        dateOfBirth = "",
                        mobileNo = "",
                        profilePhotoUrl = p.profile_photo_url ?: "",
                        followers = p.followers_count.toString()
                    )
                )
            } else {
                Result.failure(DataError.NotFound)
            }
        } catch (e: Exception) {
            Result.failure(DataError.Firebase("fetch_error", e.message ?: "Failed to fetch user"))
        }
    }

    /** Observe user data — polls gateway once; real-time updates not needed for profile. */
    fun observeUserData(userId: String): Flow<User> = callbackFlow {
        try {
            val resp = GatewayClient.api.getMyProfile()
            if (resp.isSuccessful && resp.body() != null) {
                val p = resp.body()!!
                trySend(
                    User(
                        userId = p.userId.ifEmpty { userId },
                        email = "",
                        fullName = p.full_name,
                        dateOfBirth = "",
                        mobileNo = "",
                        profilePhotoUrl = p.profile_photo_url ?: "",
                        followers = p.followers_count.toString()
                    )
                )
            }
        } catch (_: Exception) {}
        awaitClose {}
    }
}
