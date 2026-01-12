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
 */
class HomeViewModel : ViewModel() {
    
    val videosFlow: Flow<PagingData<HomeVideoData>> = Pager(
        config = PagingConfig(
            pageSize = 10,
            prefetchDistance = 3,
            enablePlaceholders = false,
            initialLoadSize = 10
        ),
        pagingSourceFactory = { HomePagingSource() }
    ).flow.cachedIn(viewModelScope)
}
