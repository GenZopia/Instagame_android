package com.genzopia.Instagame.features.auth.ui

import androidx.lifecycle.viewModelScope
import com.genzopia.Instagame.common.models.DataError
import com.genzopia.Instagame.common.navigation.NavigationEvent
import com.genzopia.Instagame.common.ui.BaseViewModel
import com.genzopia.Instagame.features.auth.data.UserRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * UI state for the forgot password screen
 */
data class ForgotPasswordUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isEmailSent: Boolean = false,
    val successMessage: String? = null
)

/**
 * Events that can be triggered from the forgot password screen
 */
sealed class ForgotPasswordEvent {
    data class EmailChanged(val email: String) : ForgotPasswordEvent()
    object SendResetEmailClicked : ForgotPasswordEvent()
    object BackToLoginClicked : ForgotPasswordEvent()
    object ErrorDismissed : ForgotPasswordEvent()
}

/**
 * ViewModel for the forgot password screen
 */
class ForgotPasswordViewModel(
    private val userRepository: UserRepository
) : BaseViewModel<ForgotPasswordUiState, ForgotPasswordEvent>() {
    
    override val _uiState = MutableStateFlow(ForgotPasswordUiState())
    
    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()
    
    override fun onEvent(event: ForgotPasswordEvent) {
        when (event) {
            is ForgotPasswordEvent.EmailChanged -> updateEmail(event.email)
            ForgotPasswordEvent.SendResetEmailClicked -> sendResetEmail()
            ForgotPasswordEvent.BackToLoginClicked -> navigateToLogin()
            ForgotPasswordEvent.ErrorDismissed -> dismissError()
        }
    }
    
    private fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(email = email)
    }
    
    private fun sendResetEmail() {
        val currentState = _uiState.value
        
        if (currentState.email.isBlank()) {
            _uiState.value = currentState.copy(error = "Please enter your email")
            return
        }
        
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(currentState.email).matches()) {
            _uiState.value = currentState.copy(error = "Please enter a valid email address")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, error = null)
            
            userRepository.sendPasswordResetEmail(currentState.email)
                .onSuccess {
                    _uiState.value = currentState.copy(
                        isLoading = false,
                        isEmailSent = true,
                        successMessage = "Password reset email sent! Please check your inbox."
                    )
                }
                .onFailure { error ->
                    _uiState.value = currentState.copy(
                        isLoading = false,
                        error = getErrorMessage(error)
                    )
                }
        }
    }
    
    private fun navigateToLogin() {
        viewModelScope.launch {
            _navigationEvent.emit(NavigationEvent.NavigateToLogin)
        }
    }
    
    private fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    private fun getErrorMessage(error: Throwable): String {
        return when (error) {
            is DataError.Network -> "Network error. Please check your connection."
            is DataError.Firebase -> error.message
            else -> "An unexpected error occurred. Please try again."
        }
    }
}
