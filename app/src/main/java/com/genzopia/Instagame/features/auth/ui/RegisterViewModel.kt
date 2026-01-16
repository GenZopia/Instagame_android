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
 * UI state for the register screen
 */
data class RegisterUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val fullName: String = "",
    val dateOfBirth: String = "",
    val mobileNo: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isRegistrationSuccessful: Boolean = false
)

/**
 * Events that can be triggered from the register screen
 */
sealed class RegisterEvent {
    data class EmailChanged(val email: String) : RegisterEvent()
    data class PasswordChanged(val password: String) : RegisterEvent()
    data class ConfirmPasswordChanged(val confirmPassword: String) : RegisterEvent()
    data class FullNameChanged(val fullName: String) : RegisterEvent()
    data class DateOfBirthChanged(val dateOfBirth: String) : RegisterEvent()
    data class MobileNoChanged(val mobileNo: String) : RegisterEvent()
    object RegisterClicked : RegisterEvent()
    object LoginClicked : RegisterEvent()
    object ErrorDismissed : RegisterEvent()
}

/**
 * ViewModel for the register screen
 */
class RegisterViewModel(
    private val userRepository: UserRepository
) : BaseViewModel<RegisterUiState, RegisterEvent>() {
    
    override val _uiState = MutableStateFlow(RegisterUiState())
    
    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()
    
    override fun onEvent(event: RegisterEvent) {
        when (event) {
            is RegisterEvent.EmailChanged -> updateEmail(event.email)
            is RegisterEvent.PasswordChanged -> updatePassword(event.password)
            is RegisterEvent.ConfirmPasswordChanged -> updateConfirmPassword(event.confirmPassword)
            is RegisterEvent.FullNameChanged -> updateFullName(event.fullName)
            is RegisterEvent.DateOfBirthChanged -> updateDateOfBirth(event.dateOfBirth)
            is RegisterEvent.MobileNoChanged -> updateMobileNo(event.mobileNo)
            RegisterEvent.RegisterClicked -> register()
            RegisterEvent.LoginClicked -> navigateToLogin()
            RegisterEvent.ErrorDismissed -> dismissError()
        }
    }
    
    private fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(email = email)
    }
    
    private fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }
    
    private fun updateConfirmPassword(confirmPassword: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = confirmPassword)
    }
    
    private fun updateFullName(fullName: String) {
        _uiState.value = _uiState.value.copy(fullName = fullName)
    }
    
    private fun updateDateOfBirth(dateOfBirth: String) {
        _uiState.value = _uiState.value.copy(dateOfBirth = dateOfBirth)
    }
    
    private fun updateMobileNo(mobileNo: String) {
        _uiState.value = _uiState.value.copy(mobileNo = mobileNo)
    }
    
    private fun register() {
        val currentState = _uiState.value
        
        // Validation
        if (currentState.email.isBlank()) {
            _uiState.value = currentState.copy(error = "Please enter your email")
            return
        }
        
        if (currentState.password.isBlank()) {
            _uiState.value = currentState.copy(error = "Please enter your password")
            return
        }
        
        if (currentState.password.length < 6) {
            _uiState.value = currentState.copy(error = "Password must be at least 6 characters")
            return
        }
        
        if (currentState.password != currentState.confirmPassword) {
            _uiState.value = currentState.copy(error = "Passwords do not match")
            return
        }
        
        if (currentState.fullName.isBlank()) {
            _uiState.value = currentState.copy(error = "Please enter your full name")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, error = null)
            
            userRepository.signUpWithEmail(
                email = currentState.email,
                password = currentState.password,
                fullName = currentState.fullName,
                dateOfBirth = currentState.dateOfBirth,
                mobileNo = currentState.mobileNo
            )
                .onSuccess {
                    _uiState.value = currentState.copy(
                        isLoading = false,
                        isRegistrationSuccessful = true
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
