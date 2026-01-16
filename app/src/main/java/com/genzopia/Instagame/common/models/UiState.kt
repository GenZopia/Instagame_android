package com.genzopia.Instagame.common.models

/**
 * Generic UI state wrapper for handling loading, success, and error states.
 * 
 * @param T The type of data in the success state
 */
sealed class UiState<out T> {
    
    /**
     * Loading state - data is being fetched
     */
    object Loading : UiState<Nothing>()
    
    /**
     * Success state - data has been loaded successfully
     * 
     * @param data The loaded data
     */
    data class Success<T>(val data: T) : UiState<T>()
    
    /**
     * Error state - an error occurred while loading data
     * 
     * @param message The error message to display
     */
    data class Error(val message: String) : UiState<Nothing>()
}
