package com.genzopia.Instagame.reelview.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.Flow

/**
 * ViewModel for managing reel data with Paging 3
 * Optimized for faster loading
 */
class ReelViewModel : ViewModel() {
    
    val reelsFlow: Flow<PagingData<ReelData>> = Pager(
        config = PagingConfig(
            pageSize = 3, // Reduced from 10 for faster initial load
            prefetchDistance = 5, // Increased from 3 for better prefetching
            enablePlaceholders = false,
            initialLoadSize = 3, // Reduced from 10 for faster first display
            maxSize = 30 // Limit memory usage
        ),
        pagingSourceFactory = { ReelPagingSource() }
    ).flow.cachedIn(viewModelScope)
}
