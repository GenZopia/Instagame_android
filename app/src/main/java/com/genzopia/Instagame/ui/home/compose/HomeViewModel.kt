package com.genzopia.Instagame.ui.home.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.Flow

/**
 * ViewModel for managing home feed data with Paging 3
 * Optimized for faster loading
 */
class HomeViewModel : ViewModel() {
    
    val videosFlow: Flow<PagingData<HomeVideoData>> = Pager(
        config = PagingConfig(
            pageSize = 5, // Reduced from 10 for faster initial load
            prefetchDistance = 5, // Increased from 3 for better prefetching
            enablePlaceholders = false,
            initialLoadSize = 5, // Reduced from 10 for faster first display
            maxSize = 50 // Limit memory usage
        ),
        pagingSourceFactory = { HomePagingSource() }
    ).flow.cachedIn(viewModelScope)
}
