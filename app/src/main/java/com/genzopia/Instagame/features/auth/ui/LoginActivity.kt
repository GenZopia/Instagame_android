package com.genzopia.Instagame.features.auth.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.genzopia.Instagame.LoginActivities.ForgotPassword
import com.genzopia.Instagame.LoginActivities.RegisterActivity
import com.genzopia.Instagame.MainActivity
import com.genzopia.Instagame.common.ui.viewModelFactory
import com.genzopia.Instagame.common.utils.RepositoryProvider

class LoginActivity : ComponentActivity() {
    
    private val sharedPrefFile = "LoginPrefs"
    
    private val viewModel: LoginViewModel by viewModels {
        viewModelFactory {
            LoginViewModel(RepositoryProvider.provideUserRepository())
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Load saved credentials
        val savedEmail = getSharedPreferences(sharedPrefFile, Context.MODE_PRIVATE)
            .getString("email", "") ?: ""
        val savedPassword = getSharedPreferences(sharedPrefFile, Context.MODE_PRIVATE)
            .getString("password", "") ?: ""
        
        // Pre-fill if available
        if (savedEmail.isNotEmpty()) {
            viewModel.onEvent(LoginEvent.EmailChanged(savedEmail))
        }
        if (savedPassword.isNotEmpty()) {
            viewModel.onEvent(LoginEvent.PasswordChanged(savedPassword))
        }
        
        setContent {
            MaterialTheme {
                Surface {
                    LoginScreen(
                        viewModel = viewModel,
                        onNavigateToMain = {
                            // Save credentials on successful login
                            getSharedPreferences(sharedPrefFile, Context.MODE_PRIVATE).edit()
                                .putString("email", viewModel.uiState.value.email)
                                .putString("password", viewModel.uiState.value.password)
                                .apply()
                            
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        },
                        onNavigateToRegister = {
                            startActivity(Intent(this, RegisterActivity::class.java))
                        },
                        onNavigateToForgotPassword = {
                            startActivity(Intent(this, ForgotPassword::class.java))
                        }
                    )
                }
            }
        }
    }
}
