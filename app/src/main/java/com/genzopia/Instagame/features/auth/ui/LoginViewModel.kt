package com.genzopia.Instagame.features.auth.ui

import androidx.lifecycle.viewModelScope
import com.genzopia.Instagame.common.models.DataError
import com.genzopia.Instagame.common.navigation.NavigationEvent
import com.genzopia.Instagame.common.ui.BaseViewModel
import com.genzopia.Instagame.features.auth.data.UserRepository
import com.genzopia.Instagame.features.auth.domain.User
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * UI state for the login screen
 */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoginSuccessful: Boolean = false
)

/**
 * Events that can be triggered from the login screen
 */
sealed class LoginEvent {
    data class EmailChanged(val email: String) : LoginEvent()
    data class PasswordChanged(val password: String) : LoginEvent()
    object LoginClicked : LoginEvent()
    data class GoogleSignInCompleted(val account: GoogleSignInAccount) : LoginEvent()
    object RegisterClicked : LoginEvent()
    object ForgotPasswordClicked : LoginEvent()
    object ErrorDismissed : LoginEvent()
}

/**
 * ViewModel for the login screen
 */
class LoginViewModel(
    private val userRepository: UserRepository
) : BaseViewModel<LoginUiState, LoginEvent>() {
    
    override val _uiState = MutableStateFlow(LoginUiState())
    
    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()
    
    override fun onEvent(event: LoginEvent) {
        when (event) {
            is LoginEvent.EmailChanged -> updateEmail(event.email)
            is LoginEvent.PasswordChanged -> updatePassword(event.password)
            LoginEvent.LoginClicked -> login()
            is LoginEvent.GoogleSignInCompleted -> handleGoogleSignIn(event.account)
            LoginEvent.RegisterClicked -> navigateToRegister()
            LoginEvent.ForgotPasswordClicked -> navigateToForgotPassword()
            LoginEvent.ErrorDismissed -> dismissError()
        }
    }
    
    private fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(email = email)
    }
    
    private fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }
    
    private fun login() {
        val currentState = _uiState.value
        
        if (currentState.email.isBlank()) {
            _uiState.value = currentState.copy(error = "Please enter your email")
            return
        }
        
        if (currentState.password.isBlank()) {
            _uiState.value = currentState.copy(error = "Please enter your password")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, error = null)
            
            userRepository.signInWithEmail(currentState.email, currentState.password)
                .onSuccess {
                    _uiState.value = currentState.copy(
                        isLoading = false,
                        isLoginSuccessful = true
                    )
                    _navigationEvent.emit(NavigationEvent.NavigateToHome)
                }
                .onFailure { error ->
                    _uiState.value = currentState.copy(
                        isLoading = false,
                        error = getErrorMessage(error)
                    )
                }
        }
    }
    
    private fun handleGoogleSignIn(account: GoogleSignInAccount) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            userRepository.signInWithGoogle(account)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoginSuccessful = true
                    )
                    _navigationEvent.emit(NavigationEvent.NavigateToHome)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = getErrorMessage(error)
                    )
                }
        }
    }
    
    private fun navigateToRegister() {
        viewModelScope.launch {
            _navigationEvent.emit(NavigationEvent.NavigateToRegister)
        }
    }
    
    private fun navigateToForgotPassword() {
        viewModelScope.launch {
            _navigationEvent.emit(NavigationEvent.NavigateToRegister) // Will be updated to ForgotPassword
        }
    }
    
    private fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    private fun getErrorMessage(error: Throwable): String {
        return when (error) {
            is DataError.Network -> "Network error. Please check your connection."
            is DataError.Firebase -> error.message
            is DataError.Unauthorized -> "Invalid email or password."
            else -> "An unexpected error occurred. Please try again."
        }
    }
}
