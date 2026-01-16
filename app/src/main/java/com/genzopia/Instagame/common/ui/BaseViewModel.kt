package com.genzopia.Instagame.common.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Base ViewModel class that provides common functionality for all ViewModels.
 * 
 * @param State The type of UI state this ViewModel manages
 * @param Event The type of events this ViewModel handles
 */
abstract class BaseViewModel<State, Event> : ViewModel() {
    
    /**
     * Mutable state flow for internal state updates
     */
    protected abstract val _uiState: MutableStateFlow<State>
    
    /**
     * Public immutable state flow for UI observation
     */
    val uiState: StateFlow<State> get() = _uiState.asStateFlow()
    
    /**
     * Handle user events from the UI
     * 
     * @param event The event to handle
     */
    abstract fun onEvent(event: Event)
}
