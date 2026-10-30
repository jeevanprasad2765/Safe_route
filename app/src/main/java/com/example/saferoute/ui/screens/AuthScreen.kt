package com.example.saferoute.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.saferoute.ui.viewmodel.AuthUiState
import com.example.saferoute.util.LanguageManager
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.GlassSurfaceBorder
import com.example.ui.theme.SafeRouteBlue
import com.example.ui.theme.SafeRouteTeal
import com.example.ui.theme.SafetyGreen
import com.example.ui.theme.SafetyRed
import com.example.ui.theme.bounceClick

@Composable
fun AuthScreen(
    authState: AuthUiState = AuthUiState.Idle,
    resetPasswordMessage: String? = null,
    resetPasswordLoading: Boolean = false,
    onGoogleSignIn: () -> Unit = {},
    onEmailAuth: (isSignUp: Boolean, name: String, email: String, password: String) -> Unit = { _, _, _, _ -> },
    onResetPassword: (String) -> Unit = {},
    onClearResetMessage: () -> Unit = {}
) {
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var forgotPasswordEmail by remember { mutableStateOf("") }

    val isLoading = authState is AuthUiState.Loading
    val errorMessage = validationError ?: (authState as? AuthUiState.Error)?.message

    // Password strength evaluation (0 to 1)
    val passwordStrength = remember(password) {
        var score = 0f
        if (password.length >= 8) score += 0.35f
        if (password.any { it.isUpperCase() }) score += 0.25f
        if (password.any { it.isDigit() }) score += 0.2f
        if (password.any { !it.isLetterOrDigit() }) score += 0.2f
        score.coerceIn(0f, 1f)
    }

    val strengthLabel = when {
        passwordStrength >= 0.8f -> "Strong"
        passwordStrength >= 0.5f -> "Medium"
        else -> "Weak"
    }

    val strengthColor = when {
        passwordStrength >= 0.8f -> SafetyGreen
        passwordStrength >= 0.5f -> Color(0xFFFFD166)
        else -> SafetyRed
    }

    Box(
        modifier = Modifier
            .testTag("auth_screen")
            .fillMaxSize()
            .background(DarkNavyBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_icon_saferoute),
                contentDescription = "SafeRoute",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isSignUp) LanguageManager.get("create_account") else LanguageManager.get("welcome_back"),
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )

            Text(
                text = LanguageManager.get("auth_subtitle"),
                fontSize = 13.sp,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(DarkNavyCard)
                    .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column {
                    AnimatedVisibility(visible = isSignUp) {
                        Column {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text(LanguageManager.get("full_name"), color = Color(0xFF94A3B8)) },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = SafeRouteTeal) },
                                modifier = Modifier
                                    .testTag("auth_name_input")
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = DarkNavySurface,
                                    unfocusedContainerColor = DarkNavySurface,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedIndicatorColor = SafeRouteBlue,
                                    unfocusedIndicatorColor = Color(0x22FFFFFF)
                                )
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(LanguageManager.get("email_address"), color = Color(0xFF94A3B8)) },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = SafeRouteTeal) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        modifier = Modifier
                            .testTag("auth_email_input")
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkNavySurface,
                            unfocusedContainerColor = DarkNavySurface,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = SafeRouteBlue,
                            unfocusedIndicatorColor = Color(0x22FFFFFF)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(LanguageManager.get("password"), color = Color(0xFF94A3B8)) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = SafeRouteTeal) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (isSignUp) ImeAction.Next else ImeAction.Done),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Password Visibility",
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier
                            .testTag("auth_password_input")
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkNavySurface,
                            unfocusedContainerColor = DarkNavySurface,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = SafeRouteBlue,
                            unfocusedIndicatorColor = Color(0x22FFFFFF)
                        )
                    )

                    // Confirm Password for Sign Up
                    AnimatedVisibility(visible = isSignUp) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                label = { Text(LanguageManager.get("confirm_password"), color = Color(0xFF94A3B8)) },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = SafeRouteTeal) },
                                trailingIcon = {
                                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                        Icon(
                                            imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Confirm Password Visibility",
                                            tint = Color(0xFF94A3B8)
                                        )
                                    }
                                },
                                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                modifier = Modifier
                                    .testTag("auth_confirm_password_input")
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = DarkNavySurface,
                                    unfocusedContainerColor = DarkNavySurface,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedIndicatorColor = SafeRouteBlue,
                                    unfocusedIndicatorColor = Color(0x22FFFFFF)
                                )
                            )
                        }
                    }

                    // Password Strength Indicator for sign up
                    if (isSignUp && password.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Strength: $strengthLabel", fontSize = 11.sp, color = strengthColor, fontWeight = FontWeight.Bold)
                            Text("${(passwordStrength * 100).toInt()}%", fontSize = 11.sp, color = strengthColor)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { passwordStrength },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = strengthColor,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                    }

                    // Forgot Password link for login
                    if (!isSignUp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                forgotPasswordEmail = email
                                onClearResetMessage()
                                showForgotPasswordDialog = true
                            }) {
                                Text(
                                    text = LanguageManager.get("forgot_password"),
                                    color = SafeRouteTeal,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Error Message Display
                    errorMessage?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = err,
                            color = SafetyRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .testTag("auth_submit_button")
                            .fillMaxWidth()
                            .height(50.dp)
                            .bounceClick(scaleDown = 0.95f) {
                                validationError = null
                                if (isSignUp && name.isBlank()) {
                                    validationError = "Please enter your full name."
                                    return@bounceClick
                                }
                                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
                                    validationError = "Please enter a valid email address."
                                    return@bounceClick
                                }
                                if (password.length < 6) {
                                    validationError = "Password must be at least 6 characters long."
                                    return@bounceClick
                                }
                                if (isSignUp && password != confirmPassword) {
                                    validationError = "Passwords do not match. Please re-enter."
                                    return@bounceClick
                                }
                                onEmailAuth(isSignUp, name, email.trim(), password)
                            }
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(SafeRouteBlue, SafeRouteTeal)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = if (isSignUp) LanguageManager.get("sign_up") else LanguageManager.get("log_in"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // SIGN IN WITH GOOGLE BUTTON (Interactive Google Sign-In via Credential Manager)
            Box(
                modifier = Modifier
                    .testTag("google_sign_in_button")
                    .fillMaxWidth()
                    .height(50.dp)
                    .bounceClick(scaleDown = 0.96f) {
                        if (!isLoading) {
                            validationError = null
                            onGoogleSignIn()
                        }
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = SafeRouteBlue,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Google",
                            tint = SafeRouteBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = LanguageManager.get("sign_in_with_google"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF1F2937)
                        )
                    }
                }
            }

            if (errorMessage != null && errorMessage.contains("Google", ignoreCase = true)) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = errorMessage,
                    color = SafetyRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0x33FFFFFF))
                Text(
                    text = "  ${LanguageManager.get("or_divider")}  ",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0x33FFFFFF))
            }

            Spacer(modifier = Modifier.height(10.dp))

            TextButton(onClick = {
                isSignUp = !isSignUp
                validationError = null
            }) {
                Text(
                    text = if (isSignUp) "Already have an account? Log In" else "Don't have an account? Sign Up",
                    color = SafeRouteTeal,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = SafeRouteTeal,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Connected to Firebase Cloud Authentication",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // FORGOT PASSWORD RESET DIALOG
        if (showForgotPasswordDialog) {
            AlertDialog(
                onDismissRequest = { showForgotPasswordDialog = false },
                containerColor = DarkNavySurface,
                title = { Text(LanguageManager.get("reset_password"), fontWeight = FontWeight.Bold, color = Color.White) },
                text = {
                    Column {
                        Text(
                            text = "Enter your email address to receive password reset instructions.",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = forgotPasswordEmail,
                            onValueChange = { forgotPasswordEmail = it },
                            label = { Text(LanguageManager.get("email_address"), color = Color(0xFF94A3B8)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = DarkNavyCard,
                                unfocusedContainerColor = DarkNavyCard,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedIndicatorColor = SafeRouteBlue
                            )
                        )
                        resetPasswordMessage?.let { msg ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = msg, fontSize = 12.sp, color = SafeRouteTeal, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                confirmButton = {
                    Box(
                        modifier = Modifier
                            .bounceClick(scaleDown = 0.92f) {
                                if (forgotPasswordEmail.isNotBlank()) {
                                    onResetPassword(forgotPasswordEmail.trim())
                                }
                            }
                            .clip(RoundedCornerShape(10.dp))
                            .background(SafeRouteBlue)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        if (resetPasswordLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(LanguageManager.get("send_reset_link"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showForgotPasswordDialog = false }) {
                        Text(LanguageManager.get("cancel"), color = Color(0xFF94A3B8))
                    }
                }
            )
        }
    }
}
