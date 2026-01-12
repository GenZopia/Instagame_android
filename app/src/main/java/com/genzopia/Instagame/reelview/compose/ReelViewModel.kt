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
 */
class ReelViewModel : ViewModel() {
    
    val reelsFlow: Flow<PagingData<ReelData>> = Pager(
        config = PagingConfig(
            pageSize = 10,
            prefetchDistance = 3,
            enablePlaceholders = false,
            initialLoadSize = 10
        ),
        pagingSourceFactory = { ReelPagingSource() }
    ).flow.cachedIn(viewModelScope)
}
