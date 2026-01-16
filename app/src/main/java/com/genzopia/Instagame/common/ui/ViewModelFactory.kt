package com.genzopia.Instagame.common.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Base ViewModelProvider.Factory that simplifies ViewModel creation with dependencies.
 * 
 * Usage example:
 * ```
 * class HomeViewModelFactory(
 *     private val videoRepository: VideoRepository
 * ) : ViewModelFactory() {
 *     override fun <T : ViewModel> create(modelClass: Class<T>): T {
 *         if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
 *             @Suppress("UNCHECKED_CAST")
 *             return HomeViewModel(videoRepository) as T
 *         }
 *         throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
 *     }
 * }
 * ```
 */
abstract class ViewModelFactory : ViewModelProvider.Factory {
    
    /**
     * Creates a new instance of the given ViewModel class.
     * 
     * @param modelClass The class of the ViewModel to create
     * @return A newly created ViewModel instance
     * @throws IllegalArgumentException if the ViewModel class is not supported
     */
    abstract override fun <T : ViewModel> create(modelClass: Class<T>): T
}

/**
 * Helper function to create a ViewModelFactory for a single ViewModel type.
 * 
 * Usage example:
 * ```
 * val factory = viewModelFactory { HomeViewModel(videoRepository) }
 * val viewModel: HomeViewModel by viewModels { factory }
 * ```
 */
inline fun <reified VM : ViewModel> viewModelFactory(
    crossinline create: () -> VM
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VM::class.java)) {
                return create() as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
