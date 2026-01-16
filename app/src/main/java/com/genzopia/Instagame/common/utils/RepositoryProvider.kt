package com.genzopia.Instagame.common.utils

import com.genzopia.Instagame.features.auth.data.UserRepository

/**
 * Singleton object that provides repository instances throughout the app.
 * This serves as a simple dependency injection mechanism without requiring
 * a full DI framework like Hilt or Dagger.
 * 
 * Repositories are lazily initialized and cached as singletons.
 */
object RepositoryProvider {
    
    private val userRepository: UserRepository by lazy {
        UserRepository()
    }
    
    fun provideUserRepository(): UserRepository = userRepository
    
    /**
     * Clear all repository caches.
     * Useful for logout or when forcing a data refresh.
     */
    fun clearAllCaches() {
        userRepository.clearCache()
    }
}
