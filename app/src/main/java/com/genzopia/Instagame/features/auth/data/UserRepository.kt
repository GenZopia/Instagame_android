package com.genzopia.Instagame.features.auth.data

import com.genzopia.Instagame.features.auth.domain.User
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for user authentication and profile management.
 * Provides a single source of truth for user data with caching.
 */
class UserRepository(
    private val authSource: FirebaseAuthSource = FirebaseAuthSource()
) {
    
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()
    
    private val _isAuthenticated = MutableStateFlow(authSource.isUserAuthenticated())
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()
    
    /**
     * Initialize repository and load current user if authenticated
     */
    suspend fun initialize() {
        if (authSource.isUserAuthenticated()) {
            authSource.getCurrentUserId()?.let { userId ->
                authSource.fetchUserData(userId).onSuccess { user ->
                    _currentUser.value = user
                    _isAuthenticated.value = true
                }
            }
        }
    }
    
    /**
     * Sign in with email and password
     */
    suspend fun signInWithEmail(email: String, password: String): Result<User> {
        return authSource.signInWithEmail(email, password).also { result ->
            result.onSuccess { user ->
                _currentUser.value = user
                _isAuthenticated.value = true
            }
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
        return authSource.signUpWithEmail(
            email, password, fullName, dateOfBirth, mobileNo
        ).also { result ->
            result.onSuccess { user ->
                _currentUser.value = user
                _isAuthenticated.value = true
            }
        }
    }
    
    /**
     * Sign in with Google account
     */
    suspend fun signInWithGoogle(account: GoogleSignInAccount): Result<User> {
        return authSource.signInWithGoogle(account).also { result ->
            result.onSuccess { user ->
                _currentUser.value = user
                _isAuthenticated.value = true
            }
        }
    }
    
    /**
     * Send password reset email
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return authSource.sendPasswordResetEmail(email)
    }
    
    /**
     * Sign out the current user
     */
    fun signOut() {
        authSource.signOut()
        _currentUser.value = null
        _isAuthenticated.value = false
        clearCache()
    }
    
    /**
     * Observe user data changes for a specific user
     */
    fun observeUser(userId: String): Flow<User> {
        return authSource.observeUserData(userId)
    }
    
    /**
     * Get current user ID
     */
    fun getCurrentUserId(): String? = authSource.getCurrentUserId()
    
    /**
     * Clear cached user data
     */
    fun clearCache() {
        _currentUser.value = null
    }
}
