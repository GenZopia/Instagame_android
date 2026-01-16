package com.genzopia.Instagame.common.models

import kotlinx.coroutines.flow.Flow

/**
 * Base repository interface that defines common data access patterns.
 * 
 * @param T The type of data this repository manages
 */
interface Repository<T> {
    
    /**
     * Fetch a single item by ID
     * 
     * @param id The unique identifier of the item
     * @return Result containing the item or an error
     */
    suspend fun fetch(id: String): Result<T>
    
    /**
     * Observe changes to a single item by ID
     * 
     * @param id The unique identifier of the item
     * @return Flow emitting updates to the item
     */
    fun observe(id: String): Flow<T>
    
    /**
     * Refresh data from the remote source
     */
    suspend fun refresh()
    
    /**
     * Clear all cached data
     */
    fun clearCache()
}
