package com.genzopia.Instagame.features.auth.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genzopia.Instagame.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

private val BrandOrange = Color(0xFFFF6B35)
private val BrandOrangeDark = Color(0xFFE55A2B)
private val BrandPurple = Color(0xFF7C3AED)
private val BrandPurpleDark = Color(0xFF4C1D95)
private val BrandGradientVertical = Brush.verticalGradient(
    colors = listOf(BrandOrange, BrandOrangeDark)
)

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onNavigateToMain: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // Entrance animation state
    var entranceStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entranceStarted = true }

    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                viewModel.onEvent(LoginEvent.GoogleSignInCompleted(account))
            }
        } catch (_: ApiException) { }
    }

    // Navigate to main when login is successful
    LaunchedEffect(uiState.isLoginSuccessful) {
        if (uiState.isLoginSuccessful) onNavigateToMain()
    }

    // Observe navigation events
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is com.genzopia.Instagame.common.navigation.NavigationEvent.NavigateToHome -> onNavigateToMain()
                is com.genzopia.Instagame.common.navigation.NavigationEvent.NavigateToRegister -> onNavigateToRegister()
                else -> {}
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0A1E),
                        Color(0xFF1A1030),
                        Color(0xFF0D0A1A)
                    )
                )
            )
    ) {
        // Decorative background circles
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Large ambient glow circle top-right
            drawCircle(
                color = BrandOrange.copy(alpha = 0.08f),
                radius = w * 0.8f,
                center = Offset(w * 0.8f, h * 0.1f)
            )
            // Purple glow bottom-left
            drawCircle(
                color = BrandPurple.copy(alpha = 0.07f),
                radius = w * 0.7f,
                center = Offset(w * 0.2f, h * 0.85f)
            )
            // Small accent dots
            drawCircle(
                color = BrandOrange.copy(alpha = 0.15f),
                radius = 6f,
                center = Offset(w * 0.15f, h * 0.25f)
            )
            drawCircle(
                color = BrandPurple.copy(alpha = 0.12f),
                radius = 4f,
                center = Offset(w * 0.85f, h * 0.65f)
            )
            drawCircle(
                color = BrandOrange.copy(alpha = 0.1f),
                radius = 3f,
                center = Offset(w * 0.5f, h * 0.12f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 60.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Brand Section ──
            AnimatedVisibility(
                visible = entranceStarted,
                enter = fadeIn(animationSpec = tween(600)) +
                        slideInVertically(animationSpec = tween(600)) { it / 3 }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    // Logo icon with gradient ring
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(BrandOrange, BrandPurple)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.banner1),
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // App name with gradient
                    Text(
                        text = "InstaGame",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            brush = Brush.linearGradient(
                                colors = listOf(BrandOrange, Color(0xFFFF8A65), BrandPurple)
                            ),
                            fontWeight = FontWeight.Bold,
                            fontSize = 36.sp,
                            letterSpacing = 1.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Tagline
                    Text(
                        text = "Play. Share. Connect.",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color.White.copy(alpha = 0.6f),
                            letterSpacing = 2.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Login Form Card ──
            AnimatedVisibility(
                visible = entranceStarted,
                enter = fadeIn(animationSpec = tween(800, delayMillis = 200)) +
                        slideInVertically(animationSpec = tween(800, delayMillis = 200)) { it / 2 }
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.07f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        // Email Field
                        OutlinedTextField(
                            value = uiState.email,
                            onValueChange = { viewModel.onEvent(LoginEvent.EmailChanged(it)) },
                            label = { Text("Email Address") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            singleLine = true,
                            enabled = !uiState.isLoading,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandOrange,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedLabelColor = BrandOrange,
                                unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                                cursorColor = BrandOrange,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White.copy(alpha = 0.8f)
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Password Field
                        var passwordVisible by remember { mutableStateOf(false) }

                        OutlinedTextField(
                            value = uiState.password,
                            onValueChange = { viewModel.onEvent(LoginEvent.PasswordChanged(it)) },
                            label = { Text("Password") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    viewModel.onEvent(LoginEvent.LoginClicked)
                                }
                            ),
                            trailingIcon = {
                                if (uiState.password.isNotEmpty()) {
                                    TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Text(
                                            text = if (passwordVisible) "Hide" else "Show",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = BrandOrange
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            enabled = !uiState.isLoading,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandOrange,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedLabelColor = BrandOrange,
                                unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                                cursorColor = BrandOrange,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White.copy(alpha = 0.8f)
                            )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Forgot Password
                        TextButton(
                            onClick = { onNavigateToForgotPassword() },
                            modifier = Modifier.align(Alignment.End),
                            enabled = !uiState.isLoading
                        ) {
                            Text(
                                text = "Forgot Password?",
                                color = BrandOrange.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // ── Login Button ──
                        Button(
                            onClick = { viewModel.onEvent(LoginEvent.LoginClicked) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .background(
                                    brush = BrandGradientVertical,
                                    shape = RoundedCornerShape(14.dp)
                                ),
                            enabled = !uiState.isLoading,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent
                            )
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "Login",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // ── Divider with "OR" ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(1.dp)
                                    .background(Color.White.copy(alpha = 0.15f))
                            )
                            Text(
                                text = "  OR  ",
                                color = Color.White.copy(alpha = 0.4f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(1.dp)
                                    .background(Color.White.copy(alpha = 0.15f))
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // ── Google Sign-In Button ──
                        OutlinedButton(
                            onClick = {
                                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                    .requestIdToken(context.getString(R.string.default_web_client_id))
                                    .requestEmail()
                                    .build()
                                val googleSignInClient = GoogleSignIn.getClient(context, gso)
                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            enabled = !uiState.isLoading,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.3f),
                                        Color.White.copy(alpha = 0.15f)
                                    )
                                )
                            )
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.google),
                                    contentDescription = "Google",
                                    modifier = Modifier.size(22.dp),
                                    tint = Color.Unspecified
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Sign in with Google",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Sign Up Link ──
            AnimatedVisibility(
                visible = entranceStarted,
                enter = fadeIn(animationSpec = tween(1000, delayMillis = 500)) +
                        slideInVertically(animationSpec = tween(1000, delayMillis = 500)) { it / 4 }
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "Don't have an account? ",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(
                        onClick = { onNavigateToRegister() },
                        enabled = !uiState.isLoading
                    ) {
                        Text(
                            text = "Sign Up Now",
                            color = BrandOrange,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ── Error Message ──
            uiState.error?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFF6B35).copy(alpha = 0.15f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "!",
                            color = BrandOrange,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                        Text(
                            text = error,
                            color = BrandOrange.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
