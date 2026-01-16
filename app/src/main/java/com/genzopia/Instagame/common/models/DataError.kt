package com.genzopia.Instagame.common.models

/**
 * Sealed class representing different types of data errors that can occur.
 */
sealed class DataError : Exception() {
    
    /**
     * Network-related error
     * 
     * @param message Description of the network error
     */
    data class Network(override val message: String) : DataError()
    
    /**
     * Firebase-specific error
     * 
     * @param code Firebase error code
     * @param message Description of the Firebase error
     */
    data class Firebase(val code: String, override val message: String) : DataError()
    
    /**
     * Cache-related error
     * 
     * @param message Description of the cache error
     */
    data class Cache(override val message: String) : DataError()
    
    /**
     * Resource not found error
     */
    object NotFound : DataError() {
        override val message: String = "Resource not found"
    }
    
    /**
     * Unauthorized access error
     */
    object Unauthorized : DataError() {
        override val message: String = "Unauthorized access"
    }
}
