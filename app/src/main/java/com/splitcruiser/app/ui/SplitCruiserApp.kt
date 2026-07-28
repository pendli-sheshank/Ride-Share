@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.splitcruiser.app.ui

import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.*
import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import coil.compose.AsyncImage
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.window.Dialog
import com.splitcruiser.app.R
import com.splitcruiser.app.data.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// --- Custom Theme Colors for Split Cruiser (Vibrant Palette Theme) ---
val SplitCruiserDarkBg = Color(0xFFF8F9FF)
val SplitCruiserCardBg = Color(0xFFFFFFFF)
val SplitCruiserSaffron = Color(0xFF0061A4)
val SplitCruiserIndigo = Color(0xFFD1E4FF)
val SplitCruiserEmerald = Color(0xFF10B981)
val SplitCruiserLightGray = Color(0xFF64748B)
val SplitCruiserDivider = Color(0xFFE2E8F0)

val SplitCruiserTextPrimary = Color(0xFF0F172A)
val SplitCruiserTextSecondary = Color(0xFF64748B)

// --- Material 3 Animation Utilities ---

@Composable
fun AnimatedButtonScale(
    isPressed: Boolean,
    enabled: Boolean = true
): Float {
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "button_scale"
    )
    return scale
}

@Composable
fun rememberButtonPressState(): MutableState<Boolean> {
    return remember { mutableStateOf(false) }
}

fun Modifier.withButtonScale(scale: Float): Modifier {
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

@Composable
fun SplitCruiserLoadingSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation }
    ) {
        CircularProgressIndicator(
            modifier = Modifier.fillMaxSize(),
            color = SplitCruiserSaffron,
            strokeWidth = 4.dp
        )
    }
}

@Composable
fun ShimmerSkeleton(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(SplitCruiserDivider.copy(alpha = alpha))
    )
}

// --- Haptic Feedback Utilities ---

fun vibrate(context: Context, duration: Long = 50) {
    try {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        duration,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        }
    } catch (e: Exception) {
        // Silently fail if vibrator not available
    }
}

fun vibrateSuccess(context: Context) {
    try {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 30, 30, 100), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 30, 30, 100), -1)
            }
        }
    } catch (e: Exception) {
        // Silently fail if vibrator not available
    }
}

@Composable
fun ExpandableCard(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = 500f
                )
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = SplitCruiserTextPrimary)
                Icon(
                    imageVector = if (isExpanded)
                        Icons.Default.ExpandLess
                    else
                        Icons.Default.ExpandMore,
                    contentDescription = "Toggle",
                    modifier = Modifier.rotate(if (isExpanded) 0f else 180f)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    content()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitCruiserApp(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val currentUser by viewModel.currentUser.collectAsState()
    val uiError by viewModel.uiError.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Observe Auth changes to route properly
    LaunchedEffect(currentUser) {
        if (currentUser == null) {
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
            }
        } else if (currentUser?.name.isNullOrEmpty()) {
            navController.navigate("profile_setup") {
                popUpTo("login") { inclusive = true }
            }
        } else {
            navController.navigate("dashboard") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SplitCruiserDarkBg
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = if (currentUser == null) "login" else "dashboard",
                modifier = Modifier.fillMaxSize()
            ) {
                composable(
                    "login",
                    enterTransition = { fadeIn() + slideInHorizontally { 1000 } },
                    exitTransition = { fadeOut() + slideOutHorizontally { -1000 } }
                ) {
                    EmailPasswordLoginScreen(viewModel, navController)
                }
                composable(
                    "profile_setup",
                    enterTransition = { fadeIn() + slideInHorizontally { 1000 } },
                    exitTransition = { fadeOut() + slideOutHorizontally { -1000 } }
                ) {
                    ProfileSetupScreen(viewModel, navController)
                }
                composable(
                    "dashboard",
                    enterTransition = { fadeIn() + slideInHorizontally { 1000 } },
                    exitTransition = { fadeOut() + slideOutHorizontally { -1000 } }
                ) {
                    DashboardScreen(viewModel, navController)
                }
                composable(
                    "post_offer",
                    enterTransition = { fadeIn() + slideInHorizontally { 1000 } },
                    exitTransition = { fadeOut() + slideOutHorizontally { -1000 } }
                ) {
                    PostOfferScreen(viewModel, navController)
                }
                composable(
                    "post_request",
                    enterTransition = { fadeIn() + slideInHorizontally { 1000 } },
                    exitTransition = { fadeOut() + slideOutHorizontally { -1000 } }
                ) {
                    PostRequestScreen(viewModel, navController)
                }
                composable(
                    route = "trip_detail/{id}/{type}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.StringType },
                        navArgument("type") { type = NavType.StringType }
                    ),
                    enterTransition = { fadeIn() + slideInHorizontally { 1000 } },
                    exitTransition = { fadeOut() + slideOutHorizontally { -1000 } }
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("id") ?: ""
                    val type = backStackEntry.arguments?.getString("type") ?: ""
                    TripDetailScreen(id, type, viewModel, navController)
                }
                composable(
                    "chat/{matchId}",
                    enterTransition = { fadeIn() + slideInHorizontally { 1000 } },
                    exitTransition = { fadeOut() + slideOutHorizontally { -1000 } }
                ) { backStackEntry ->
                    val matchId = backStackEntry.arguments?.getString("matchId") ?: ""
                    ChatScreen(matchId, viewModel, navController)
                }
                composable(
                    "profile",
                    enterTransition = { fadeIn() + slideInHorizontally { 1000 } },
                    exitTransition = { fadeOut() + slideOutHorizontally { -1000 } }
                ) {
                    ProfileScreen(viewModel, navController)
                }
                composable(
                    "blocked_list",
                    enterTransition = { fadeIn() + slideInHorizontally { 1000 } },
                    exitTransition = { fadeOut() + slideOutHorizontally { -1000 } }
                ) {
                    BlockedListScreen(viewModel, navController)
                }
                composable(
                    "host_dashboard",
                    enterTransition = { fadeIn() + slideInHorizontally { 1000 } },
                    exitTransition = { fadeOut() + slideOutHorizontally { -1000 } }
                ) {
                    HostDashboard(viewModel, navController)
                }
            }

            // Global Loader
            if (isLoading) {
                SplitCruiserLoadingState(isFullScreen = true, message = "Securing your ride...")
            }

            // Error Snackbar/Dialog Display
            uiError?.let { error ->
                AlertDialog(
                    onDismissRequest = { viewModel.clearError() },
                    confirmButton = {
                        TextButton(
                            onClick = { viewModel.clearError() },
                            colors = ButtonDefaults.textButtonColors(contentColor = SplitCruiserSaffron)
                        ) {
                            Text("Got it")
                        }
                    },
                    title = { Text("Information", color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold) },
                    text = { Text(error, color = SplitCruiserTextPrimary.copy(alpha = 0.85f)) },
                    containerColor = SplitCruiserCardBg,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }
}

// --- Common UI Components ---

@Composable
fun FirebaseStatusPill(isFirebaseEnabled: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (isFirebaseEnabled) SplitCruiserEmerald.copy(alpha = 0.15f) else SplitCruiserSaffron.copy(alpha = 0.15f))
            .border(
                1.dp,
                if (isFirebaseEnabled) SplitCruiserEmerald.copy(alpha = 0.5f) else SplitCruiserSaffron.copy(alpha = 0.5f),
                RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isFirebaseEnabled) Icons.Default.CloudQueue else Icons.Default.CloudOff,
            contentDescription = "Status",
            tint = if (isFirebaseEnabled) SplitCruiserEmerald else SplitCruiserSaffron,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (isFirebaseEnabled) "Firebase Live" else "Sandbox Mode",
            color = if (isFirebaseEnabled) SplitCruiserEmerald else SplitCruiserSaffron,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// --- Screen 1: Email & Password Login ---

@Composable
fun EmailPasswordLoginScreen(viewModel: MainViewModel, navController: NavController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isSignUpMode by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var authButtonPressed by remember { mutableStateOf(false) }
    val isFirebaseEnabled = viewModel.repository.isFirebaseEnabled
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        item {
            // Header Illustration card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_auth_illustration_1783446671433),
                    contentDescription = "Carpool Illustration",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_split_cruiser_logo),
                    contentDescription = "Split Cruiser Logo",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, SplitCruiserIndigo, CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Split Cruiser",
                    color = SplitCruiserTextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        item {
            Text(
                text = "US Desi Student Carpools. Cost-split, trust-matched.",
                color = SplitCruiserLightGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        item {
            // Live status pill
            FirebaseStatusPill(isFirebaseEnabled = isFirebaseEnabled)
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Email field
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    placeholder = { Text("your.email@example.com") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("email_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SplitCruiserSaffron,
                        unfocusedBorderColor = SplitCruiserDivider,
                        focusedLabelColor = SplitCruiserSaffron,
                        unfocusedLabelColor = SplitCruiserLightGray,
                        focusedTextColor = SplitCruiserTextPrimary,
                        unfocusedTextColor = SplitCruiserTextPrimary,
                        focusedContainerColor = SplitCruiserCardBg,
                        unfocusedContainerColor = SplitCruiserCardBg
                    ),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Email, contentDescription = "Email", tint = SplitCruiserLightGray)
                    }
                )

                // Password field
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    placeholder = { Text("At least 6 characters") },
                    visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("password_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SplitCruiserSaffron,
                        unfocusedBorderColor = SplitCruiserDivider,
                        focusedLabelColor = SplitCruiserSaffron,
                        unfocusedLabelColor = SplitCruiserLightGray,
                        focusedTextColor = SplitCruiserTextPrimary,
                        unfocusedTextColor = SplitCruiserTextPrimary,
                        focusedContainerColor = SplitCruiserCardBg,
                        unfocusedContainerColor = SplitCruiserCardBg
                    ),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock", tint = SplitCruiserLightGray)
                    },
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        val description = if (passwordVisible) "Hide password" else "Show password"
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = description, tint = SplitCruiserLightGray)
                        }
                    }
                )

                if (isSignUpMode) {
                    // Confirm Password field
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Password") },
                        placeholder = { Text("Repeat password") },
                        visualTransformation = if (confirmPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("confirm_password_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SplitCruiserSaffron,
                            unfocusedBorderColor = SplitCruiserDivider,
                            focusedLabelColor = SplitCruiserSaffron,
                            unfocusedLabelColor = SplitCruiserLightGray,
                            focusedTextColor = SplitCruiserTextPrimary,
                            unfocusedTextColor = SplitCruiserTextPrimary,
                            focusedContainerColor = SplitCruiserCardBg,
                            unfocusedContainerColor = SplitCruiserCardBg
                        ),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock", tint = SplitCruiserLightGray)
                        },
                        trailingIcon = {
                            val image = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            val description = if (confirmPasswordVisible) "Hide password" else "Show password"
                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                Icon(imageVector = image, contentDescription = description, tint = SplitCruiserLightGray)
                            }
                        }
                    )
                }

                val authScale = AnimatedButtonScale(authButtonPressed)
                Button(
                    onClick = {
                        authButtonPressed = true
                        vibrate(context, 50)
                        val emailTrimmed = email.trim()
                        val passTrimmed = password.trim()
                        if (emailTrimmed.isEmpty() || passTrimmed.isEmpty()) {
                            authButtonPressed = false
                            vibrate(context, 100)
                            viewModel.setError("Email and password cannot be empty")
                            return@Button
                        }
                        if (!emailTrimmed.contains("@") || !emailTrimmed.contains(".")) {
                            authButtonPressed = false
                            vibrate(context, 100)
                            viewModel.setError("Please enter a valid email address.")
                            return@Button
                        }
                        if (isSignUpMode) {
                            if (passTrimmed.length < 6) {
                                authButtonPressed = false
                                vibrate(context, 100)
                                viewModel.setError("Password must be at least 6 characters")
                                return@Button
                            }
                            if (passTrimmed != confirmPassword.trim()) {
                                authButtonPressed = false
                                vibrate(context, 100)
                                viewModel.setError("Passwords do not match")
                                return@Button
                            }
                            viewModel.signUpWithEmail(emailTrimmed, passTrimmed) { isNewUser ->
                                authButtonPressed = false
                                vibrateSuccess(context)
                                // Navigates automatically based on global StateFlow observer
                            }
                        } else {
                            viewModel.loginWithEmail(emailTrimmed, passTrimmed) { isNewUser ->
                                authButtonPressed = false
                                vibrateSuccess(context)
                                // Navigates automatically based on global StateFlow observer
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("auth_submit_button")
                        .withButtonScale(authScale),
                    colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserSaffron),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isSignUpMode) "Sign Up" else "Log In",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                // Hidden rather than disabled when GOOGLE_WEB_CLIENT_ID is unset: without it the
                // account picker fails at the tap with a Play Services error that means nothing
                // to whoever is looking at it.
                if (viewModel.repository.isGoogleSignInEnabled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = SplitCruiserDivider)
                        Text(
                            text = "or",
                            color = SplitCruiserLightGray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = SplitCruiserDivider)
                    }

                    OutlinedButton(
                        onClick = {
                            vibrate(context, 50)
                            viewModel.signInWithGoogle(context) {
                                vibrateSuccess(context)
                                // Navigates automatically based on global StateFlow observer
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("google_sign_in_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SplitCruiserDivider),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = SplitCruiserCardBg)
                    ) {
                        Text(
                            text = "G",
                            color = SplitCruiserSaffron,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Continue with Google",
                            color = SplitCruiserTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                TextButton(
                    onClick = { isSignUpMode = !isSignUpMode },
                    colors = ButtonDefaults.textButtonColors(contentColor = SplitCruiserSaffron),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(text = if (isSignUpMode) "Already have an account? Log In" else "Don't have an account? Sign Up")
                }
            }
        }

        item {
            Text(
                text = "Split Cruiser connects verified US college students safely. Cost-split, trust-matched.",
                color = SplitCruiserLightGray.copy(alpha = 0.5f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

// --- Screen 2: Invite Code Redemption ---

@Composable
fun InviteCodeScreen(viewModel: MainViewModel, navController: NavController) {
    var inviteCode by remember { mutableStateOf("") }
    var redeemButtonPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_split_cruiser_logo),
            contentDescription = "Split Cruiser Logo",
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .border(2.dp, SplitCruiserIndigo, CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Enter Invite Code",
            color = SplitCruiserTextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "To keep Split Cruiser secure, we require a voucher code from an existing student.",
            color = SplitCruiserLightGray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = inviteCode,
            onValueChange = { inviteCode = it.uppercase() },
            label = { Text("Student Voucher Code") },
            placeholder = { Text("e.g. SPLITCRUISER") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("invite_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SplitCruiserSaffron,
                unfocusedBorderColor = SplitCruiserDivider,
                focusedLabelColor = SplitCruiserSaffron,
                unfocusedLabelColor = SplitCruiserLightGray,
                focusedTextColor = SplitCruiserTextPrimary,
                unfocusedTextColor = SplitCruiserTextPrimary,
                focusedContainerColor = SplitCruiserCardBg,
                unfocusedContainerColor = SplitCruiserCardBg
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Demo tip: Enter code 'SPLITCRUISER' to get vouched instantly!",
            color = SplitCruiserEmerald,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        val redeemScale = AnimatedButtonScale(redeemButtonPressed)
        Button(
            onClick = {
                if (inviteCode.isNotEmpty()) {
                    redeemButtonPressed = true
                    viewModel.redeemInviteCode(inviteCode) {
                        redeemButtonPressed = false
                        // Routing handled by state flow
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("redeem_invite_button")
                .withButtonScale(redeemScale),
            colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserSaffron),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Redeem & Activate Account", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { viewModel.logout() }) {
            Text("Cancel & Log Out", color = Color.Red.copy(alpha = 0.8f))
        }
    }
}

// --- Screen 3: Profile Setup ---

@Composable
fun ProfileSetupScreen(viewModel: MainViewModel, navController: NavController) {
    var name by remember { mutableStateOf("") }
    var lastInitial by remember { mutableStateOf("") }
    var homeArea by remember { mutableStateOf("") }
    var selectedCommunityId by remember { mutableStateOf("") }
    val communities by viewModel.allCommunities.collectAsState()

    // Host Vehicle state (optional during setup)
    var isHostExpanded by remember { mutableStateOf(false) }
    var vMake by remember { mutableStateOf("") }
    var vModel by remember { mutableStateOf("") }
    var vYear by remember { mutableStateOf("") }
    var vColor by remember { mutableStateOf("") }
    var vPlate by remember { mutableStateOf("") }

    // Profile picture state
    var selectedAvatarUrl by remember { mutableStateOf("") }
    var uploadingProfilePicture by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            uploadingProfilePicture = true
            coroutineScope.launch {
                try {
                    val user = viewModel.currentUser.value
                    if (user != null) {
                        val success = viewModel.uploadProfilePicture(user.id, uri)
                        uploadingProfilePicture = false
                        if (success) {
                            selectedAvatarUrl = viewModel.currentUser.value?.avatarUrl ?: ""
                            vibrate(context, 50)
                            Toast.makeText(context, "Profile picture updated", Toast.LENGTH_SHORT).show()
                        } else {
                            vibrate(context, 100)
                            Toast.makeText(context, "Failed to upload picture", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    uploadingProfilePicture = false
                    vibrate(context, 100)
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Image(
                painter = painterResource(id = R.drawable.img_split_cruiser_logo),
                contentDescription = "Split Cruiser Logo",
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .border(2.dp, SplitCruiserIndigo, CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Setup Your Profile",
                color = SplitCruiserTextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Add your details so matches can coordinate.",
                color = SplitCruiserLightGray,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Profile Picture Upload Section
            Card(
                colors = CardDefaults.cardColors(containerColor = SplitCruiserIndigo.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .clickable(enabled = !uploadingProfilePicture) {
                        imagePickerLauncher.launch("image/*")
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Profile Picture",
                        color = SplitCruiserTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (uploadingProfilePicture) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            color = SplitCruiserSaffron,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Uploading...", color = SplitCruiserSaffron, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    } else if (selectedAvatarUrl.isNotEmpty()) {
                        StudentAvatar(
                            avatarUrl = selectedAvatarUrl,
                            name = name.ifEmpty { "?" },
                            size = 80.dp,
                            fontSize = 32.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("✓ Image selected", color = SplitCruiserEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Add photo",
                            tint = SplitCruiserSaffron,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Choose a profile photo", color = SplitCruiserLightGray, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "(Optional - can be added later)",
                        color = SplitCruiserLightGray,
                        fontSize = 10.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("First Name") },
                placeholder = { Text("Amit") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("name_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SplitCruiserSaffron,
                    unfocusedBorderColor = SplitCruiserDivider,
                    focusedTextColor = SplitCruiserTextPrimary,
                    unfocusedTextColor = SplitCruiserTextPrimary,
                    focusedContainerColor = SplitCruiserCardBg,
                    unfocusedContainerColor = SplitCruiserCardBg
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Last Initial
            OutlinedTextField(
                value = lastInitial,
                onValueChange = { if (it.length <= 1) lastInitial = it.uppercase() },
                label = { Text("Last Initial (Only 1 letter)") },
                placeholder = { Text("S") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("initial_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SplitCruiserSaffron,
                    unfocusedBorderColor = SplitCruiserDivider,
                    focusedTextColor = SplitCruiserTextPrimary,
                    unfocusedTextColor = SplitCruiserTextPrimary,
                    focusedContainerColor = SplitCruiserCardBg,
                    unfocusedContainerColor = SplitCruiserCardBg
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Community Pick
            Text(
                text = "Select Student Community",
                color = SplitCruiserTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SplitCruiserCardBg, RoundedCornerShape(12.dp))
                    .border(1.dp, SplitCruiserDivider, RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                communities.forEach { community ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCommunityId = community.id }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedCommunityId == community.id),
                            onClick = { selectedCommunityId = community.id },
                            colors = RadioButtonDefaults.colors(selectedColor = SplitCruiserSaffron)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(community.name, color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(community.location, color = SplitCruiserLightGray, fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Home Area
            OutlinedTextField(
                value = homeArea,
                onValueChange = { homeArea = it },
                label = { Text("Home Area / Sub-Campus Location") },
                placeholder = { Text("e.g. Hillside Apartments or Mission Hill") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_area_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SplitCruiserSaffron,
                    unfocusedBorderColor = SplitCruiserDivider,
                    focusedTextColor = SplitCruiserTextPrimary,
                    unfocusedTextColor = SplitCruiserTextPrimary,
                    focusedContainerColor = SplitCruiserCardBg,
                    unfocusedContainerColor = SplitCruiserCardBg
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Vehicle setup (Optional toggle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isHostExpanded = !isHostExpanded }
                    .background(SplitCruiserCardBg, RoundedCornerShape(12.dp))
                    .border(1.dp, SplitCruiserDivider, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.DirectionsCar, contentDescription = "Car", tint = SplitCruiserSaffron)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Are you offering rides?", color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Add your vehicle details now (Optional)", color = SplitCruiserLightGray, fontSize = 11.sp)
                    }
                }
                Icon(
                    imageVector = if (isHostExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand",
                    tint = SplitCruiserLightGray
                )
            }

            if (isHostExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SplitCruiserCardBg, RoundedCornerShape(12.dp))
                        .border(1.dp, SplitCruiserDivider, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    OutlinedTextField(
                        value = vMake,
                        onValueChange = { vMake = it },
                        label = { Text("Car Make") },
                        placeholder = { Text("Toyota") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SplitCruiserSaffron,
                            unfocusedBorderColor = SplitCruiserDivider,
                            focusedTextColor = SplitCruiserTextPrimary,
                            unfocusedTextColor = SplitCruiserTextPrimary,
                            focusedContainerColor = SplitCruiserCardBg,
                            unfocusedContainerColor = SplitCruiserCardBg
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = vModel,
                        onValueChange = { vModel = it },
                        label = { Text("Car Model") },
                        placeholder = { Text("Camry") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SplitCruiserSaffron,
                            unfocusedBorderColor = SplitCruiserDivider,
                            focusedTextColor = SplitCruiserTextPrimary,
                            unfocusedTextColor = SplitCruiserTextPrimary,
                            focusedContainerColor = SplitCruiserCardBg,
                            unfocusedContainerColor = SplitCruiserCardBg
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = vYear,
                        onValueChange = { vYear = it },
                        label = { Text("Car Year") },
                        placeholder = { Text("2021") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SplitCruiserSaffron,
                            unfocusedBorderColor = SplitCruiserDivider,
                            focusedTextColor = SplitCruiserTextPrimary,
                            unfocusedTextColor = SplitCruiserTextPrimary,
                            focusedContainerColor = SplitCruiserCardBg,
                            unfocusedContainerColor = SplitCruiserCardBg
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = vColor,
                        onValueChange = { vColor = it },
                        label = { Text("Color") },
                        placeholder = { Text("Silver") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SplitCruiserSaffron,
                            unfocusedBorderColor = SplitCruiserDivider,
                            focusedTextColor = SplitCruiserTextPrimary,
                            unfocusedTextColor = SplitCruiserTextPrimary,
                            focusedContainerColor = SplitCruiserCardBg,
                            unfocusedContainerColor = SplitCruiserCardBg
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = vPlate,
                        onValueChange = { vPlate = it.uppercase() },
                        label = { Text("License Plate") },
                        placeholder = { Text("7XYZ99") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SplitCruiserSaffron,
                            unfocusedBorderColor = SplitCruiserDivider,
                            focusedTextColor = SplitCruiserTextPrimary,
                            unfocusedTextColor = SplitCruiserTextPrimary,
                            focusedContainerColor = SplitCruiserCardBg,
                            unfocusedContainerColor = SplitCruiserCardBg
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (name.isNotEmpty() && selectedCommunityId.isNotEmpty()) {
                        val vehicle = if (isHostExpanded && vMake.isNotEmpty()) {
                            Vehicle(
                                ownerId = viewModel.currentUser.value?.id ?: "",
                                make = vMake,
                                model = vModel,
                                year = vYear,
                                color = vColor,
                                licensePlate = vPlate
                            )
                        } else null
                        
                        viewModel.completeProfile(
                            name = name,
                            lastInitial = lastInitial,
                            communityId = selectedCommunityId,
                            homeArea = homeArea,
                            vehicle = vehicle
                        ) {
                            // Routed automatically
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("submit_profile_button"),
                colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserSaffron),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Launch Split Cruiser", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// --- Screen 4: Main Dashboard ---

@Composable
fun DashboardScreen(viewModel: MainViewModel, navController: NavController) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf("explore") }
    val currentUser by viewModel.currentUser.collectAsState()
    val activeOffers by viewModel.activeOffers.collectAsState()
    val activeRequests by viewModel.activeRequests.collectAsState()
    val userMatches by viewModel.userMatches.collectAsState()
    val communities by viewModel.allCommunities.collectAsState()
    val hostedRides by viewModel.hostedRides.collectAsState()
    val joinedRides by viewModel.joinedRides.collectAsState()
    val myRideRequests by viewModel.myRideRequests.collectAsState()
    val activeMode = viewModel.currentMode
    val isLoading by viewModel.isLoading.collectAsState()
    val currentUserId = currentUser?.id ?: ""

    val activeHosted = remember(hostedRides) {
        hostedRides.filter { it.status == "active" }
    }
    val activeJoined = remember(joinedRides) {
        joinedRides.filter { it.status == "active" }
    }
    val activeMyRequests = remember(myRideRequests) {
        myRideRequests.filter { it.status == "active" }
    }
    val pastRides = remember(hostedRides, joinedRides) {
        val hostedPast = hostedRides.filter { it.status != "active" }
        val joinedPast = joinedRides.filter { it.status != "active" }
        (hostedPast + joinedPast).distinctBy { it.id }.sortedByDescending { it.departureTime }
    }

    var showSuccessDialog by remember { mutableStateOf(false) }
    var selectedOfferForDialog by remember { mutableStateOf<TripOffer?>(null) }

    val userCommunity = communities.find { it.id == currentUser?.communityId }?.name ?: "Indian Student Community"

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(SplitCruiserDarkBg)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Main Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.img_split_cruiser_logo),
                                contentDescription = "Split Cruiser Logo",
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, SplitCruiserIndigo, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedTab == "trips") "My Travel Schedule" else "Namaste, ${currentUser?.name ?: "Student"}",
                                color = SplitCruiserTextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black
                            )
                            if (currentUser?.verifiedTier == "vouched" && selectedTab != "trips") {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Vouched",
                                    tint = SplitCruiserSaffron,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = if (selectedTab == "trips") "Manage your hosted and joined rides" else userCommunity,
                            color = SplitCruiserLightGray,
                            fontSize = 11.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selectedTab == "trips") {
                            IconButton(
                                onClick = { viewModel.refreshMyTrips() },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(SplitCruiserIndigo.copy(alpha = 0.2f))
                                    .size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = SplitCruiserSaffron,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            // Rating indicator
                            if (currentUser != null && currentUser!!.ratingCount > 0) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFEEF1FF))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = "Rating", tint = Color(0xFFEAB308), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        String.format(Locale.US, "%.1f", currentUser!!.ratingAvg),
                                        color = SplitCruiserTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            // Profile Icon (Polished Avatar style)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFD1E4FF))
                                    .clickable { navController.navigate("profile") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.Person, contentDescription = "Profile", tint = Color(0xFF001D36))
                            }
                        }
                    }
                }

                if (selectedTab != "trips") {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Mode Selector Bar (Styled exactly like Design HTML rounded tab pills)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFE1E2EC))
                            .padding(4.dp)
                    ) {
                        listOf("Rider", "Host").forEach { mode ->
                            val active = (activeMode == mode)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (active) Color.White else Color.Transparent)
                                    .clickable { viewModel.switchMode(mode) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (mode == "Rider") "Rider Mode (Find Ride)" else "Host Mode (Give Ride)",
                                    color = if (active) SplitCruiserTextPrimary else Color(0xFF64748B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .border(1.dp, Color(0xFFF1F5F9))
                    .navigationBarsPadding()
                    .height(80.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Explore (Active)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { selectedTab = "explore" }
                        .padding(vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selectedTab == "explore") SplitCruiserIndigo else Color.Transparent)
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = "Explore",
                            tint = if (selectedTab == "explore") SplitCruiserSaffron else SplitCruiserLightGray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Explore",
                        color = if (selectedTab == "explore") SplitCruiserSaffron else SplitCruiserLightGray,
                        fontWeight = if (selectedTab == "explore") FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp
                    )
                }

                // My Trips
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { selectedTab = "trips" }
                        .padding(vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selectedTab == "trips") SplitCruiserIndigo else Color.Transparent)
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "My Trips",
                            tint = if (selectedTab == "trips") SplitCruiserSaffron else SplitCruiserLightGray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "My Trips",
                        color = if (selectedTab == "trips") SplitCruiserSaffron else SplitCruiserLightGray,
                        fontWeight = if (selectedTab == "trips") FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp
                    )
                }

                // Chats
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            val latestMatch = userMatches.firstOrNull()
                            if (latestMatch != null) {
                                navController.navigate("chat/${latestMatch.id}")
                            }
                        }
                        .padding(vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Transparent)
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = "Chats",
                                tint = SplitCruiserLightGray,
                                modifier = Modifier.size(24.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color.Red)
                                    .align(Alignment.TopEnd)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Chats",
                        color = SplitCruiserLightGray,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    )
                }

                // Profile
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            navController.navigate("profile")
                        }
                        .padding(vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Transparent)
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonOutline,
                            contentDescription = "Profile",
                            tint = SplitCruiserLightGray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Profile",
                        color = SplitCruiserLightGray,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == "explore") {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (activeMode == "Rider") {
                            navController.navigate("post_request")
                        } else {
                            navController.navigate("post_offer")
                        }
                    },
                    containerColor = SplitCruiserSaffron,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.Add, contentDescription = "Post") },
                    text = { Text(if (activeMode == "Rider") "Post Request" else "Post Offer", fontWeight = FontWeight.Bold) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("action_fab")
                )
            }
        },
        containerColor = SplitCruiserDarkBg
    ) { innerPadding ->
        if (selectedTab == "explore") {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                // Hero Illustration Banner card
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(
                                painter = painterResource(id = R.drawable.img_carpool_banner),
                                contentDescription = "Carpool Banner",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            // Gradient Overlay for readability
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                        )
                                    )
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = if (activeMode == "Rider") "Direct cost splitting with host" else "Fill empty seats & share gas cost",
                                    color = Color(0xFFF59E0B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = if (activeMode == "Rider") "Select a host to split cash" else "Accept ride requests on your route",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Sub-Section 1: My Active Matches / Active Coordination
                val activeMatches = userMatches.filter { it.status == "pending" || it.status == "accepted" }
                if (activeMatches.isNotEmpty()) {
                    item {
                        Text(
                            text = "Active Trip Coordination",
                            color = SplitCruiserTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    items(activeMatches) { match ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SplitCruiserCardBg)
                                .clickable {
                                    // Navigate to appropriate details or Chat directly
                                    if (match.status == "accepted") {
                                        navController.navigate("chat/${match.id}")
                                    } else {
                                        navController.navigate("trip_detail/${match.offerId}/offer")
                                    }
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (match.status == "accepted") SplitCruiserEmerald.copy(alpha = 0.15f) else SplitCruiserSaffron.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (match.status == "accepted") Icons.AutoMirrored.Filled.Chat else Icons.Default.HourglassEmpty,
                                        contentDescription = "Match",
                                        tint = if (match.status == "accepted") SplitCruiserEmerald else SplitCruiserSaffron
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (match.hostId == currentUserId) "Ride with ${match.riderName}" else "Ride with Host",
                                        color = SplitCruiserTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Status: ${match.status.replaceFirstChar { it.uppercase() }} • Contribution: $${match.contribution}",
                                        color = SplitCruiserLightGray,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Open", tint = SplitCruiserLightGray)
                        }
                    }
                }

                // Section 2: Core Feed Lists
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (activeMode == "Rider") "Trip Offers Near You" else "Local Ride Requests",
                            color = SplitCruiserTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                if (activeMode == "Rider") {
                    // RIDER FEED: List active host trip offers
                    if (isLoading && activeOffers.isEmpty()) {
                        item {
                            SplitCruiserFeedLoadingSkeleton()
                        }
                    } else if (activeOffers.isEmpty()) {
                        item {
                            SplitCruiserEmptyState(
                                title = "No Active Offers Yet",
                                description = "Be the first to post a Ride Request so student hosts can find you!",
                                icon = Icons.Default.DirectionsCar,
                                actionLabel = "Post Ride Request",
                                onActionClick = { navController.navigate("post_request") }
                            )
                        }
                    } else {
                        item {
                            TripOfferList(
                                offers = activeOffers,
                                currentUserId = currentUserId,
                                userMatches = userMatches,
                                viewModel = viewModel,
                                navController = navController,
                                onJoinClick = { offer ->
                                    val dummyRequestId = "req_joined_${System.currentTimeMillis().toString().takeLast(6)}"
                                    viewModel.requestJoin(offer.id, dummyRequestId, offer.costPerRider) {
                                        selectedOfferForDialog = offer
                                        showSuccessDialog = true
                                    }
                                }
                            )
                        }
                    }
                } else {
                    // HOST FEED: List active rider requests
                    if (isLoading && activeRequests.isEmpty()) {
                        item {
                            SplitCruiserFeedLoadingSkeleton()
                        }
                    } else if (activeRequests.isEmpty()) {
                        item {
                            SplitCruiserEmptyState(
                                title = "No Open Requests",
                                description = "Post a trip offer or wait until a local student submits a ride request.",
                                icon = Icons.Default.DirectionsCar,
                                actionLabel = "Post Trip Offer",
                                onActionClick = { navController.navigate("post_offer") }
                            )
                        }
                    } else {
                        items(activeRequests) { request ->
                            RideRequestCard(request) {
                                navController.navigate("trip_detail/${request.id}/request")
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Hosted Rides (Driver Mode)",
                        color = SplitCruiserTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (activeHosted.isEmpty()) {
                    item {
                        SplitCruiserEmptyState(
                            title = "No Hosted Rides",
                            description = "You haven't posted any trip offers as a host yet.",
                            icon = Icons.Default.DirectionsCar,
                            actionLabel = "Post Trip Offer",
                            onActionClick = { navController.navigate("post_offer") },
                            illustrationType = "hosted"
                        )
                    }
                } else {
                    items(activeHosted) { offer ->
                        HostedRideScheduleCard(
                            offer = offer,
                            onCardClick = { navController.navigate("trip_detail/${offer.id}/offer") },
                            onStatusChange = { newStatus ->
                                viewModel.updateTripOfferStatus(offer.id, newStatus) {
                                    Toast.makeText(context, "Ride status updated!", Toast.LENGTH_SHORT).show()
                                    viewModel.refreshMyTrips()
                                }
                            }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Joined Rides (Passenger Mode)",
                        color = SplitCruiserTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (activeJoined.isEmpty()) {
                    item {
                        SplitCruiserEmptyState(
                            title = "No Joined Rides",
                            description = "You haven't reserved seats on any student's ride yet.",
                            icon = Icons.Default.Map,
                            actionLabel = "Find a Ride",
                            onActionClick = { selectedTab = "explore" },
                            illustrationType = "joined"
                        )
                    }
                } else {
                    items(activeJoined) { offer ->
                        JoinedRideScheduleCard(
                            offer = offer,
                            onCardClick = { navController.navigate("trip_detail/${offer.id}/offer") }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "My Ride Requests (Rider Mode)",
                        color = SplitCruiserTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (activeMyRequests.isEmpty()) {
                    item {
                        SplitCruiserEmptyState(
                            title = "No Posted Ride Requests",
                            description = "You haven't requested any rides as a passenger yet.",
                            icon = Icons.Default.DirectionsCar,
                            actionLabel = "Post Ride Request",
                            onActionClick = { navController.navigate("post_request") },
                            illustrationType = "joined"
                        )
                    }
                } else {
                    items(activeMyRequests) { request ->
                        MyRideRequestCard(
                            request = request,
                            onCancelClick = {
                                viewModel.cancelRideRequest(request.id) {
                                    Toast.makeText(context, "Ride request cancelled!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Past Rides & Reference History",
                        color = SplitCruiserTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (pastRides.isEmpty()) {
                    item {
                        SplitCruiserEmptyState(
                            title = "No Past Rides",
                            description = "Your completed and cancelled rides will show up here for future reference.",
                            icon = Icons.Default.History,
                            illustrationType = "past"
                        )
                    }
                } else {
                    items(pastRides) { offer ->
                        PastRideCard(
                            offer = offer,
                            currentUserId = currentUserId,
                            onCardClick = { navController.navigate("trip_detail/${offer.id}/offer") }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }

    if (showSuccessDialog) {
        JoinSuccessDialog(
            offer = selectedOfferForDialog,
            onDismiss = { showSuccessDialog = false },
            onViewTrips = {
                showSuccessDialog = false
                selectedTab = "trips"
            }
        )
    }
}

@Composable
fun HostDashboard(viewModel: MainViewModel, navController: NavController) {
    val context = LocalContext.current
    val hostedRides by viewModel.hostedRides.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    var filterStatus by remember { mutableStateOf("all") } // all, active, completed, cancelled

    val filteredRides = remember(hostedRides, filterStatus) {
        when (filterStatus) {
            "active" -> hostedRides.filter { it.status == "active" }
            "completed" -> hostedRides.filter { it.status == "completed" }
            "cancelled" -> hostedRides.filter { it.status == "cancelled" }
            else -> hostedRides
        }.sortedByDescending { it.departureTime }
    }

    val activeRides = remember(hostedRides) { hostedRides.filter { it.status == "active" } }
    val totalPassengers = remember(hostedRides) { hostedRides.sumOf { it.passengers.size } }
    val totalRevenue = remember(hostedRides) { hostedRides.sumOf { it.costPerRider * (it.totalSeats - it.seatsLeft) } }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(SplitCruiserDarkBg)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Host Dashboard",
                            color = SplitCruiserTextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Manage your hosted rides",
                            color = SplitCruiserLightGray,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SplitCruiserIndigo.copy(alpha = 0.2f))
                            .size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = SplitCruiserSaffron,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Statistics Overview
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HostStatCard(
                        label = "Active Rides",
                        value = "${activeRides.size}",
                        icon = Icons.Default.DirectionsCar,
                        modifier = Modifier.weight(1f)
                    )
                    HostStatCard(
                        label = "Total Passengers",
                        value = "$totalPassengers",
                        icon = Icons.Default.People,
                        modifier = Modifier.weight(1f)
                    )
                    HostStatCard(
                        label = "Revenue",
                        value = "$${String.format("%.2f", totalRevenue)}",
                        icon = Icons.Default.AttachMoney,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Filter Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("all" to "All Rides", "active" to "Active", "completed" to "Completed", "cancelled" to "Cancelled").forEach { (status, label) ->
                        FilterChip(
                            selected = filterStatus == status,
                            onClick = { filterStatus = status },
                            label = { Text(label, fontSize = 12.sp) },
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }
            }

            // Hosted Rides List
            item {
                if (filteredRides.isEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    SplitCruiserEmptyState(
                        title = "No Hosted Rides",
                        description = "You haven't posted any trip offers yet.",
                        icon = Icons.Default.DirectionsCar,
                        actionLabel = "Post a Ride",
                        onActionClick = { navController.navigate("post_offer") }
                    )
                }
            }

            items(filteredRides) { offer ->
                HostedRideScheduleCard(
                    offer = offer,
                    onCardClick = { navController.navigate("trip_detail/${offer.id}/offer") },
                    onStatusChange = { newStatus ->
                        viewModel.updateTripOfferStatus(offer.id, newStatus) {
                            Toast.makeText(context, "Ride status updated!", Toast.LENGTH_SHORT).show()
                            viewModel.refreshMyTrips()
                        }
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
fun HostStatCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg),
        modifier = modifier
            .border(1.dp, SplitCruiserDivider, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = SplitCruiserSaffron,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                color = SplitCruiserTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = SplitCruiserLightGray,
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PassengerManagementCard(
    passengerName: String,
    passengerRating: Float,
    passengerId: String,
    offerRoute: String,
    onMessageClick: () -> Unit,
    onMarkNoShowClick: () -> Unit,
    onViewProfileClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SplitCruiserDivider, RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SplitCruiserIndigo),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Passenger",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = passengerName,
                            color = SplitCruiserTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Rating",
                                tint = Color(0xFFEAB308),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = String.format("%.1f", passengerRating),
                                color = SplitCruiserTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                IconButton(onClick = onViewProfileClick) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "View profile",
                        tint = SplitCruiserLightGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = SplitCruiserDivider, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onMessageClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = "Message",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Message", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onMarkNoShowClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = "No Show",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("No Show", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun HostedRideScheduleCard(
    offer: TripOffer,
    onCardClick: () -> Unit,
    onStatusChange: (String) -> Unit
) {
    val formatter = remember { SimpleDateFormat("EEE, d MMM • h:mm a", Locale.US) }
    val dateStr = formatter.format(Date(offer.departureTime))

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onCardClick() }
            .border(1.dp, SplitCruiserDivider, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = "Hosted Ride",
                        tint = SplitCruiserSaffron,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "HOSTED RIDE",
                        color = SplitCruiserSaffron,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                val badgeColor = when (offer.status) {
                    "active" -> SplitCruiserEmerald
                    "completed" -> Color(0xFF3B82F6)
                    "cancelled" -> Color(0xFFEF4444)
                    else -> SplitCruiserSaffron
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = offer.status.uppercase(),
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.Top) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 4.dp, end = 12.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SplitCruiserIndigo))
                    Box(modifier = Modifier.width(2.dp).height(24.dp).background(SplitCruiserDivider))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SplitCruiserSaffron))
                }

                Column {
                    Text(
                        text = offer.origin,
                        color = SplitCruiserTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = offer.destination,
                        color = SplitCruiserTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = SplitCruiserDivider, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "DEPARTURE", color = SplitCruiserLightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(text = dateStr, color = SplitCruiserTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "SEATS OCCUPIED", color = SplitCruiserLightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${offer.totalSeats - offer.seatsLeft} / ${offer.totalSeats}",
                        color = SplitCruiserTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (offer.passengerNames.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = SplitCruiserDivider, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "PASSENGERS:",
                    color = SplitCruiserLightGray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    offer.passengerNames.forEach { name ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(SplitCruiserIndigo.copy(alpha = 0.2f))
                                .border(1.dp, SplitCruiserIndigo.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(text = name, color = SplitCruiserTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (offer.status == "active") {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onStatusChange("cancelled") },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                    ) {
                        Text("Cancel Ride", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { onStatusChange("completed") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserEmerald)
                    ) {
                        Text("Complete Ride", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun JoinedRideScheduleCard(
    offer: TripOffer,
    onCardClick: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("EEE, d MMM • h:mm a", Locale.US) }
    val dateStr = formatter.format(Date(offer.departureTime))

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onCardClick() }
            .border(1.dp, SplitCruiserDivider, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = "Joined Ride",
                        tint = SplitCruiserSaffron,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "JOINED RIDE",
                        color = SplitCruiserSaffron,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                val badgeColor = when (offer.status) {
                    "active" -> SplitCruiserEmerald
                    "completed" -> Color(0xFF3B82F6)
                    "cancelled" -> Color(0xFFEF4444)
                    else -> SplitCruiserSaffron
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = offer.status.uppercase(),
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.Top) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 4.dp, end = 12.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SplitCruiserIndigo))
                    Box(modifier = Modifier.width(2.dp).height(24.dp).background(SplitCruiserDivider))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SplitCruiserSaffron))
                }

                Column {
                    Text(
                        text = offer.origin,
                        color = SplitCruiserTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = offer.destination,
                        color = SplitCruiserTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = SplitCruiserDivider, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "HOST", color = SplitCruiserLightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(text = offer.hostName, color = SplitCruiserTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "DEPARTURE", color = SplitCruiserLightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(text = dateStr, color = SplitCruiserTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "CONTRIBUTION", color = SplitCruiserLightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(text = "$${offer.costPerRider}", color = SplitCruiserSaffron, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PastRideCard(
    offer: TripOffer,
    currentUserId: String,
    onCardClick: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("EEE, d MMM yyyy • h:mm a", Locale.US) }
    val dateStr = formatter.format(Date(offer.departureTime))
    val isHost = offer.hostId == currentUserId

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onCardClick() }
            .border(1.dp, SplitCruiserDivider.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isHost) Icons.Default.DirectionsCar else Icons.Default.History,
                        contentDescription = "Past Ride",
                        tint = SplitCruiserLightGray,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isHost) "PAST HOSTED" else "PAST JOINED",
                        color = SplitCruiserLightGray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }

                val badgeColor = when (offer.status) {
                    "completed" -> SplitCruiserEmerald
                    "cancelled" -> Color(0xFFEF4444)
                    else -> SplitCruiserLightGray
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor.copy(alpha = 0.1f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = offer.status.uppercase(),
                        color = badgeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.Top) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 3.dp, end = 10.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(SplitCruiserIndigo.copy(alpha = 0.5f)))
                    Box(modifier = Modifier.width(1.5.dp).height(18.dp).background(SplitCruiserDivider.copy(alpha = 0.5f)))
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(SplitCruiserSaffron.copy(alpha = 0.5f)))
                }

                Column {
                    Text(
                        text = offer.origin,
                        color = SplitCruiserTextPrimary.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = offer.destination,
                        color = SplitCruiserTextPrimary.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = SplitCruiserDivider.copy(alpha = 0.3f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "DATE & TIME", color = SplitCruiserLightGray, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                    Text(text = dateStr, color = SplitCruiserTextPrimary.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "ROLE / DETAIL", color = SplitCruiserLightGray, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = if (isHost) "Driver" else "Passenger (with ${offer.hostName})",
                        color = SplitCruiserSaffron.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun MyRideRequestCard(
    request: RideRequest,
    onCancelClick: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("EEE, d MMM • h:mm a", Locale.US) }
    val dateStr = formatter.format(Date(request.departureTime))

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, SplitCruiserDivider, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = "My Ride Request",
                        tint = SplitCruiserEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "MY RIDE REQUEST",
                        color = SplitCruiserEmerald,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                val badgeColor = when (request.status) {
                    "active" -> SplitCruiserEmerald
                    "matched" -> Color(0xFF3B82F6)
                    "cancelled" -> Color(0xFFEF4444)
                    else -> SplitCruiserSaffron
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = request.status.uppercase(),
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.Top) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 4.dp, end = 12.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SplitCruiserIndigo))
                    Box(modifier = Modifier.width(2.dp).height(24.dp).background(SplitCruiserDivider))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SplitCruiserSaffron))
                }

                Column {
                    Text(
                        text = request.origin,
                        color = SplitCruiserTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = request.destination,
                        color = SplitCruiserTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = SplitCruiserDivider, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "PREFERRED DEPARTURE", color = SplitCruiserLightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(text = dateStr, color = SplitCruiserTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "SEATS NEEDED", color = SplitCruiserLightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${request.seatsNeeded}",
                        color = SplitCruiserTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (request.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = SplitCruiserDivider, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "NOTES", color = SplitCruiserLightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(text = request.notes, color = SplitCruiserTextPrimary, fontSize = 12.sp)
            }

            if (request.status == "active") {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onCancelClick,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Text("Cancel Request", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SplitCruiserEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Map,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    illustrationType: String = "generic"
) {
    val infiniteTransition = rememberInfiniteTransition(label = "split_cruiser_empty_state_anim")
    
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_offset"
    )

    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    val flowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 120f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flow_offset"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 220.dp, height = 130.dp),
            contentAlignment = Alignment.Center
        ) {
            when (illustrationType) {
                "hosted" -> {
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val w = size.width
                        val h = size.height
                        
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(SplitCruiserIndigo.copy(alpha = 0.25f), Color.Transparent),
                                center = androidx.compose.ui.geometry.Offset(w / 2, h / 2),
                                radius = h * 0.7f
                            )
                        )

                        val roadPath = Path().apply {
                            moveTo(w * 0.45f, h * 0.25f)
                            lineTo(w * 0.55f, h * 0.25f)
                            lineTo(w * 0.85f, h * 0.95f)
                            lineTo(w * 0.15f, h * 0.95f)
                            close()
                        }
                        drawPath(
                            path = roadPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(SplitCruiserIndigo.copy(alpha = 0.1f), SplitCruiserIndigo.copy(alpha = 0.45f)),
                                startY = h * 0.25f,
                                endY = h * 0.95f
                            )
                        )

                        drawLine(
                            color = SplitCruiserSaffron.copy(alpha = 0.4f),
                            start = androidx.compose.ui.geometry.Offset(w * 0.45f, h * 0.25f),
                            end = androidx.compose.ui.geometry.Offset(w * 0.15f, h * 0.95f),
                            strokeWidth = 3f
                        )
                        drawLine(
                            color = SplitCruiserSaffron.copy(alpha = 0.4f),
                            start = androidx.compose.ui.geometry.Offset(w * 0.55f, h * 0.25f),
                            end = androidx.compose.ui.geometry.Offset(w * 0.85f, h * 0.95f),
                            strokeWidth = 3f
                        )

                        val centerLinePath = Path().apply {
                            moveTo(w * 0.5f, h * 0.25f)
                            lineTo(w * 0.5f, h * 0.95f)
                        }
                        drawPath(
                            path = centerLinePath,
                            color = SplitCruiserSaffron.copy(alpha = 0.7f),
                            style = Stroke(
                                width = 4f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), flowOffset)
                            )
                        )

                        drawCircle(
                            color = SplitCruiserSaffron.copy(alpha = 0.3f),
                            radius = 4f,
                            center = androidx.compose.ui.geometry.Offset(w * 0.25f, h * 0.35f)
                        )
                        drawCircle(
                            color = SplitCruiserSaffron.copy(alpha = 0.5f),
                            radius = 3f,
                            center = androidx.compose.ui.geometry.Offset(w * 0.78f, h * 0.45f)
                        )
                        drawCircle(
                            color = SplitCruiserEmerald.copy(alpha = 0.4f),
                            radius = 5f,
                            center = androidx.compose.ui.geometry.Offset(w * 0.12f, h * 0.65f)
                        )
                    }
                }
                "joined" -> {
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val w = size.width
                        val h = size.height
                        
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(SplitCruiserSaffron.copy(alpha = 0.08f), Color.Transparent),
                                center = androidx.compose.ui.geometry.Offset(w / 2, h / 2),
                                radius = h * 0.8f
                            )
                        )

                        drawCircle(
                            color = SplitCruiserIndigo.copy(alpha = 0.35f),
                            radius = (h * 0.45f) * glowPulse,
                            center = androidx.compose.ui.geometry.Offset(w / 2, h / 2),
                            style = Stroke(width = 2f)
                        )
                        drawCircle(
                            color = SplitCruiserIndigo.copy(alpha = 0.2f),
                            radius = h * 0.3f,
                            center = androidx.compose.ui.geometry.Offset(w / 2, h / 2),
                            style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                        )

                        val routePath = Path().apply {
                            moveTo(w * 0.2f, h * 0.75f)
                            quadraticTo(w * 0.45f, h * 0.15f, w * 0.8f, h * 0.5f)
                        }
                        drawPath(
                            path = routePath,
                            color = SplitCruiserSaffron.copy(alpha = 0.6f),
                            style = Stroke(
                                width = 3f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), -flowOffset)
                            )
                        )

                        drawCircle(
                            color = SplitCruiserSaffron.copy(alpha = 0.2f * glowPulse),
                            radius = 16f,
                            center = androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.75f)
                        )
                        drawCircle(
                            color = SplitCruiserSaffron,
                            radius = 6f,
                            center = androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.75f)
                        )

                        drawCircle(
                            color = SplitCruiserEmerald.copy(alpha = 0.25f),
                            radius = 14f,
                            center = androidx.compose.ui.geometry.Offset(w * 0.8f, h * 0.5f)
                        )
                        drawCircle(
                            color = SplitCruiserEmerald,
                            radius = 5f,
                            center = androidx.compose.ui.geometry.Offset(w * 0.8f, h * 0.5f)
                        )
                    }
                }
                "past" -> {
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val w = size.width
                        val h = size.height
                        
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(SplitCruiserIndigo.copy(alpha = 0.15f), Color.Transparent),
                                center = androidx.compose.ui.geometry.Offset(w / 2, h / 2),
                                radius = h * 0.7f
                            )
                        )

                        // Draw a clock outline
                        drawCircle(
                            color = SplitCruiserIndigo.copy(alpha = 0.4f),
                            radius = h * 0.4f * glowPulse,
                            center = androidx.compose.ui.geometry.Offset(w / 2, h / 2),
                            style = Stroke(width = 3f)
                        )
                        
                        // Draw clock hands
                        drawLine(
                            color = SplitCruiserSaffron.copy(alpha = 0.7f),
                            start = androidx.compose.ui.geometry.Offset(w / 2, h / 2),
                            end = androidx.compose.ui.geometry.Offset(w / 2 + (h * 0.25f) * kotlin.math.cos(Math.toRadians(30.0).toFloat()).toFloat(), h / 2 + (h * 0.25f) * kotlin.math.sin(Math.toRadians(30.0).toFloat()).toFloat()),
                            strokeWidth = 4f
                        )
                        drawLine(
                            color = SplitCruiserSaffron.copy(alpha = 0.5f),
                            start = androidx.compose.ui.geometry.Offset(w / 2, h / 2),
                            end = androidx.compose.ui.geometry.Offset(w / 2 + (h * 0.18f) * kotlin.math.cos(Math.toRadians(120.0).toFloat()).toFloat(), h / 2 + (h * 0.18f) * kotlin.math.sin(Math.toRadians(120.0).toFloat()).toFloat()),
                            strokeWidth = 4f
                        )

                        // Outer dash circle
                        drawCircle(
                            color = SplitCruiserIndigo.copy(alpha = 0.2f),
                            radius = h * 0.55f,
                            center = androidx.compose.ui.geometry.Offset(w / 2, h / 2),
                            style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), -flowOffset))
                        )
                    }
                }
                else -> {
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val w = size.width
                        val h = size.height

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(SplitCruiserIndigo.copy(alpha = 0.2f), Color.Transparent),
                                center = androidx.compose.ui.geometry.Offset(w / 2, h / 2),
                                radius = h * 0.6f
                            )
                        )
                        drawCircle(
                            color = SplitCruiserIndigo.copy(alpha = 0.3f),
                            radius = (h * 0.35f) * glowPulse,
                            center = androidx.compose.ui.geometry.Offset(w / 2, h / 2),
                            style = Stroke(width = 2f)
                        )
                        drawCircle(
                            color = SplitCruiserIndigo.copy(alpha = 0.15f),
                            radius = h * 0.5f,
                            center = androidx.compose.ui.geometry.Offset(w / 2, h / 2),
                            style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), flowOffset))
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .offset(y = floatOffset.dp)
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(SplitCruiserCardBg)
                    .border(2.dp, SplitCruiserSaffron, CircleShape)
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SplitCruiserSaffron,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            color = SplitCruiserTextPrimary,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = description,
            color = SplitCruiserTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        if (actionLabel != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SplitCruiserSaffron,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text(
                    text = actionLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun SplitCruiserFeedLoadingSkeleton(
    modifier: Modifier = Modifier,
    itemsCount: Int = 3
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(itemsCount) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SplitCruiserDivider, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .graphicsLayer(alpha = alpha)
                ) {
                    // Header row: Profile avatar circle & Name + Rating line
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SplitCruiserLightGray.copy(alpha = 0.3f))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SplitCruiserLightGray.copy(alpha = 0.3f))
                                )
                                Box(
                                    modifier = Modifier
                                        .width(60.dp)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SplitCruiserLightGray.copy(alpha = 0.3f))
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .width(50.dp)
                                .height(20.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SplitCruiserLightGray.copy(alpha = 0.3f))
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Route details lines
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(SplitCruiserLightGray.copy(alpha = 0.3f))
                            )
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(24.dp)
                                    .background(SplitCruiserLightGray.copy(alpha = 0.2f))
                            )
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(SplitCruiserLightGray.copy(alpha = 0.3f))
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SplitCruiserLightGray.copy(alpha = 0.3f))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SplitCruiserLightGray.copy(alpha = 0.3f))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Footer Row: Time/Status and Price pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(140.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(SplitCruiserLightGray.copy(alpha = 0.3f))
                        )
                        Box(
                            modifier = Modifier
                                .width(70.dp)
                                .height(28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SplitCruiserLightGray.copy(alpha = 0.3f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SplitCruiserLoadingState(
    modifier: Modifier = Modifier,
    message: String = "Loading...",
    isFullScreen: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing)
        ),
        label = "rotation"
    )

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val content = @Composable {
        Card(
            colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier
                .padding(24.dp)
                .border(1.dp, SplitCruiserDivider, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer rotating vibrant indicator
                    CircularProgressIndicator(
                        progress = { 0.75f },
                        color = SplitCruiserSaffron,
                        strokeWidth = 4.dp,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(rotationZ = rotation)
                    )

                    // Inner Split Cruiser logo pulsing beautifully
                    Image(
                        painter = painterResource(id = R.drawable.img_split_cruiser_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .graphicsLayer(scaleX = scale, scaleY = scale),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = message,
                    color = SplitCruiserTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (isFullScreen) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
fun EmptyStateWidget(title: String, description: String) {
    SplitCruiserEmptyState(title = title, description = description)
}

@Composable
fun TripOfferCard(
    offer: TripOffer,
    isJoinable: Boolean = false,
    hasPendingRequest: Boolean = false,
    hasAlreadyJoined: Boolean = false,
    onJoinClick: (() -> Unit)? = null,
    isHost: Boolean = false,
    onClick: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("EEE, d MMM • h:mm a", Locale.US) }
    val dateStr = formatter.format(Date(offer.departureTime))
    var joinButtonPressed by remember { mutableStateOf(false) }
    val joinScale = AnimatedButtonScale(joinButtonPressed)

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .border(1.dp, SplitCruiserDivider, RoundedCornerShape(16.dp))
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isWide = maxWidth >= 500.dp

            if (isWide) {
                // Side-by-side Responsive Grid
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Side: Trip and Driver Info
                    Column(
                        modifier = Modifier.weight(1.3f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Driver profile info
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SplitCruiserIndigo),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = offer.hostName.take(1).uppercase(),
                                    color = Color(0xFF001D36),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = offer.hostName,
                                        color = SplitCruiserTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    if (offer.womenOnly) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFFF472B6).copy(alpha = 0.2f))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "WOMEN ONLY",
                                                color = Color(0xFFF472B6),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Rating",
                                        tint = Color(0xFFEAB308),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = String.format(Locale.US, "%.1f", offer.hostRating),
                                        color = SplitCruiserLightGray,
                                        fontSize = 11.sp
                                    )
                                    if (offer.vehicleInfo.isNotEmpty()) {
                                        Text(
                                            text = " • ${offer.vehicleInfo}",
                                            color = SplitCruiserLightGray,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // Trip Route Connectors
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.RadioButtonChecked, contentDescription = "Start", tint = SplitCruiserSaffron, modifier = Modifier.size(14.dp))
                                Box(modifier = Modifier.width(1.5.dp).height(18.dp).background(SplitCruiserDivider))
                                Icon(Icons.Default.Place, contentDescription = "End", tint = SplitCruiserIndigo, modifier = Modifier.size(14.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(offer.origin, color = SplitCruiserTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(offer.destination, color = SplitCruiserTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        // Trip meta info: Time & Seats
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccessTime, contentDescription = "Time", tint = SplitCruiserLightGray, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(dateStr, color = SplitCruiserLightGray, fontSize = 11.sp)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.EventSeat, contentDescription = "Seats", tint = SplitCruiserEmerald, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${offer.seatsLeft} of ${offer.totalSeats} seats open", color = SplitCruiserEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Right Side: Price Display & Action CTAs
                    Column(
                        modifier = Modifier.weight(0.7f),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("$${offer.costPerRider}", color = SplitCruiserSaffron, fontWeight = FontWeight.Black, fontSize = 22.sp)
                            Text("per rider", color = SplitCruiserLightGray, fontSize = 10.sp)
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Secondary CTA: View Details
                            OutlinedButton(
                                onClick = onClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SplitCruiserSaffron),
                                border = BorderStroke(1.dp, SplitCruiserSaffron.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("View Details", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Primary CTA / Status Pill
                            if (isJoinable && onJoinClick != null) {
                                val context = LocalContext.current
                                Button(
                                    onClick = {
                                        joinButtonPressed = true
                                        vibrate(context, 50)
                                        onJoinClick()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .testTag("card_join_button_${offer.id}")
                                        .withButtonScale(joinScale),
                                    colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserSaffron),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Join Ride", tint = Color.White, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Join Ride", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            } else if (hasPendingRequest) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFD97706).copy(alpha = 0.12f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .border(1.dp, Color(0xFFD97706).copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.HourglassEmpty, contentDescription = "Pending", tint = Color(0xFFD97706), modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Pending Approval", color = Color(0xFFD97706), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                    }
                                }
                            } else if (hasAlreadyJoined) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = SplitCruiserEmerald.copy(alpha = 0.12f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .border(1.dp, SplitCruiserEmerald.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "Joined", tint = SplitCruiserEmerald, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Joined", color = SplitCruiserEmerald, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                    }
                                }
                            } else if (isHost) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = SplitCruiserIndigo.copy(alpha = 0.12f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .border(1.dp, SplitCruiserIndigo.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.DirectionsCar, contentDescription = "Your Trip", tint = SplitCruiserSaffron, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Your Trip", color = SplitCruiserSaffron, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Compact Vertical Layout (Mobile Phones)
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header: Driver, Info, and Price
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(SplitCruiserIndigo),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = offer.hostName.take(1).uppercase(),
                                    color = Color(0xFF001D36),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(offer.hostName, color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    if (offer.womenOnly) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFFF472B6).copy(alpha = 0.2f))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text("WOMEN ONLY", color = Color(0xFFF472B6), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, contentDescription = "Rating", tint = Color(0xFFEAB308), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = String.format(Locale.US, "%.1f", offer.hostRating),
                                        color = SplitCruiserLightGray,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("$${offer.costPerRider}", color = SplitCruiserSaffron, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            Text("per rider", color = SplitCruiserLightGray, fontSize = 9.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Route details
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.RadioButtonChecked, contentDescription = "Start", tint = SplitCruiserSaffron, modifier = Modifier.size(14.dp))
                            Box(modifier = Modifier.width(1.5.dp).height(24.dp).background(SplitCruiserDivider))
                            Icon(Icons.Default.Place, contentDescription = "End", tint = SplitCruiserIndigo, modifier = Modifier.size(14.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(offer.origin, color = SplitCruiserTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(offer.destination, color = SplitCruiserTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Footer: Departure Date & Open Seats Left
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, contentDescription = "Time", tint = SplitCruiserLightGray, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(dateStr, color = SplitCruiserLightGray, fontSize = 11.sp)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EventSeat, contentDescription = "Seats", tint = SplitCruiserEmerald, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${offer.seatsLeft} of ${offer.totalSeats} seats open", color = SplitCruiserEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Bottom CTAs
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = SplitCruiserDivider)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Secondary CTA
                        OutlinedButton(
                            onClick = onClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SplitCruiserSaffron),
                            border = BorderStroke(1.dp, SplitCruiserSaffron.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Text("View Details", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        // Primary CTA
                        if (isJoinable && onJoinClick != null) {
                            Button(
                                onClick = onJoinClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("card_join_button_${offer.id}"),
                                colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserSaffron),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Join Ride", tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Join Ride", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        } else if (hasPendingRequest) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFD97706).copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .border(1.dp, Color(0xFFD97706).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.HourglassEmpty, contentDescription = "Request Pending", tint = Color(0xFFD97706), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pending Approval", color = Color(0xFFD97706), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        } else if (hasAlreadyJoined) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SplitCruiserEmerald.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .border(1.dp, SplitCruiserEmerald.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Joined", tint = SplitCruiserEmerald, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Joined", color = SplitCruiserEmerald, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        } else if (isHost) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SplitCruiserIndigo.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .border(1.dp, SplitCruiserIndigo.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.DirectionsCar, contentDescription = "Your Trip", tint = SplitCruiserSaffron, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Your Trip", color = SplitCruiserSaffron, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TripOfferList(
    offers: List<TripOffer>,
    currentUserId: String,
    userMatches: List<TripMatch>,
    viewModel: MainViewModel,
    navController: NavController,
    onJoinClick: (TripOffer) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") } // "All", "Under $15", "Women Only", "With Seats"

    // Filtered list
    val filteredOffers = remember(offers, searchQuery, selectedFilter) {
        offers.filter { offer ->
            val matchesSearch = offer.origin.contains(searchQuery, ignoreCase = true) ||
                    offer.destination.contains(searchQuery, ignoreCase = true) ||
                    offer.hostName.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "Under $15" -> offer.costPerRider <= 15
                "Women Only" -> offer.womenOnly
                "With Seats" -> offer.seatsLeft > 0
                else -> true
            }

            matchesSearch && matchesFilter
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by origin, destination or host...", color = SplitCruiserLightGray, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = SplitCruiserLightGray) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SplitCruiserLightGray)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag("trip_list_search_input"),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SplitCruiserSaffron,
                unfocusedBorderColor = SplitCruiserDivider,
                focusedContainerColor = SplitCruiserCardBg,
                unfocusedContainerColor = SplitCruiserCardBg,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        )

        // Filter chips row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            val filters = listOf("All", "Under $15", "Women Only", "With Seats")
            items(filters) { filter ->
                val isSelected = selectedFilter == filter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) SplitCruiserSaffron else SplitCruiserCardBg)
                        .border(1.dp, if (isSelected) Color.Transparent else SplitCruiserDivider, RoundedCornerShape(20.dp))
                        .clickable { selectedFilter = filter }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("filter_chip_$filter")
                ) {
                    Text(
                        text = filter,
                        color = if (isSelected) Color.White else SplitCruiserLightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Popular Auto Places Shortcut Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            val quickPlaces = listOf("Snell", "Airport", "Ruggles", "South Station", "Harvard", "Mission Hill")
            items(quickPlaces) { placeTag ->
                val isSelected = searchQuery.contains(placeTag, ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) SplitCruiserEmerald.copy(alpha = 0.25f) else Color(0xFF252D3C),
                    border = BorderStroke(1.dp, if (isSelected) SplitCruiserEmerald else SplitCruiserDivider)
                ) {
                    Row(
                        modifier = Modifier
                            .clickable {
                                searchQuery = if (isSelected) "" else placeTag
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = if (isSelected) SplitCruiserEmerald else SplitCruiserSaffron,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = placeTag,
                            color = if (isSelected) SplitCruiserEmerald else SplitCruiserTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        if (filteredOffers.isEmpty()) {
            Box(modifier = Modifier.padding(vertical = 12.dp)) {
                SplitCruiserEmptyState(
                    title = "No Matching Offers",
                    description = "Try adjusting your search query or filters to find other carpools.",
                    icon = Icons.Default.Search,
                    actionLabel = "Clear Filters",
                    onActionClick = {
                        searchQuery = ""
                        selectedFilter = "All"
                    }
                )
            }
        } else {
            filteredOffers.forEach { offer ->
                val isHost = (offer.hostId == currentUserId)
                val hasAlreadyJoined = offer.passengers.contains(currentUserId)
                val hasPendingRequest = userMatches.any { it.offerId == offer.id && it.riderId == currentUserId && it.status == "pending" }
                val isJoinable = !isHost && !hasAlreadyJoined && !hasPendingRequest && offer.seatsLeft > 0 && offer.status == "active"

                TripOfferCard(
                    offer = offer,
                    isJoinable = isJoinable,
                    hasPendingRequest = hasPendingRequest,
                    hasAlreadyJoined = hasAlreadyJoined,
                    isHost = isHost,
                    onJoinClick = { onJoinClick(offer) }
                ) {
                    navController.navigate("trip_detail/${offer.id}/offer")
                }
            }
        }
    }
}

@Composable
fun JoinSuccessDialog(
    offer: TripOffer?,
    onDismiss: () -> Unit,
    onViewTrips: () -> Unit
) {
    if (offer == null) return

    val formatter = remember { SimpleDateFormat("EEEE, d MMMM • h:mm a", Locale.US) }
    val dateStr = formatter.format(Date(offer.departureTime))

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = SplitCruiserCardBg,
            border = BorderStroke(1.dp, SplitCruiserDivider),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Animated success/check ring
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(SplitCruiserIndigo.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(SplitCruiserIndigo),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = SplitCruiserSaffron,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Request Submitted!",
                    color = SplitCruiserTextPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "We've notified ${offer.hostName} of your request. Once accepted, you'll be able to coordinate details.",
                    color = SplitCruiserLightGray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Trip Card details in dialog
                Card(
                    colors = CardDefaults.cardColors(containerColor = SplitCruiserDarkBg),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SplitCruiserDivider, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Driver and contribution amount
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(SplitCruiserIndigo),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = offer.hostName.take(1).uppercase(),
                                        color = Color(0xFF001D36),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = offer.hostName,
                                    color = SplitCruiserTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }

                            Text(
                                text = "$${offer.costPerRider}",
                                color = SplitCruiserSaffron,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                        }

                        HorizontalDivider(color = SplitCruiserDivider)

                        // Route Connectors
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.RadioButtonChecked, contentDescription = "Start", tint = SplitCruiserSaffron, modifier = Modifier.size(12.dp))
                                Box(modifier = Modifier.width(1.5.dp).height(14.dp).background(SplitCruiserDivider))
                                Icon(Icons.Default.Place, contentDescription = "End", tint = SplitCruiserSaffron, modifier = Modifier.size(12.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(offer.origin, color = SplitCruiserTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(offer.destination, color = SplitCruiserTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        HorizontalDivider(color = SplitCruiserDivider)

                        // Time
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, contentDescription = "Time", tint = SplitCruiserLightGray, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(dateStr, color = SplitCruiserLightGray, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // CTAs
                Button(
                    onClick = onViewTrips,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("dialog_view_trips_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserSaffron),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("View My Trips", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("dialog_dismiss_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SplitCruiserSaffron),
                    border = BorderStroke(1.dp, SplitCruiserSaffron.copy(alpha = 0.5f))
                ) {
                    Text("Done", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun RideRequestCard(request: RideRequest, onClick: () -> Unit) {
    val formatter = remember { SimpleDateFormat("EEE, d MMM • h:mm a", Locale.US) }
    val dateStr = formatter.format(Date(request.departureTime))

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .border(1.dp, SplitCruiserDivider, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SplitCruiserSaffron.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = request.riderName.take(1).uppercase(),
                            color = SplitCruiserSaffron,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(request.riderName, color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = "Rating", tint = Color(0xFFEAB308), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = String.format(Locale.US, "%.1f", request.riderRating),
                                color = SplitCruiserLightGray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Seats needed tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SplitCruiserSaffron.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${request.seatsNeeded} Seat${if (request.seatsNeeded > 1) "s" else ""}",
                        color = SplitCruiserSaffron,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Routes
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.RadioButtonChecked, contentDescription = "Start", tint = SplitCruiserSaffron, modifier = Modifier.size(14.dp))
                    Box(modifier = Modifier.width(1.5.dp).height(24.dp).background(SplitCruiserDivider))
                    Icon(Icons.Default.Place, contentDescription = "End", tint = SplitCruiserIndigo, modifier = Modifier.size(14.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(request.origin, color = SplitCruiserTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(request.destination, color = SplitCruiserTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessTime, contentDescription = "Time", tint = SplitCruiserLightGray, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(dateStr, color = SplitCruiserLightGray, fontSize = 11.sp)
                }

                if (request.womenOnly) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFE91E63).copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Female, contentDescription = "Female Only", tint = Color(0xFFE91E63), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Women Only", color = Color(0xFFE91E63), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// --- Screen 5: Post Ride Offer (Host) ---

@Composable
fun PostOfferScreen(viewModel: MainViewModel, navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var origin by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var originLat by remember { mutableStateOf(42.34) }
    var originLng by remember { mutableStateOf(-71.10) }
    var destLat by remember { mutableStateOf(42.33) }
    var destLng by remember { mutableStateOf(-71.08) }
    
    val calendar = remember { java.util.Calendar.getInstance().apply { add(java.util.Calendar.HOUR_OF_DAY, 4) } }
    val dateFormatter = remember { java.text.SimpleDateFormat("EEE, MMM d, yyyy", java.util.Locale.getDefault()) }
    val timeFormatter = remember { java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()) }
    
    var dateInput by remember { mutableStateOf(dateFormatter.format(calendar.time)) }
    var timeInput by remember { mutableStateOf(timeFormatter.format(calendar.time)) }
    var departureEpoch by remember { mutableStateOf(calendar.timeInMillis) }

    var totalSeats by remember { mutableStateOf("4") }
    var costPerRider by remember { mutableStateOf("15.00") }
    var womenOnly by remember { mutableStateOf(false) }

    val userVehicle = viewModel.getVehicleInfo(viewModel.currentUser.value?.id ?: "")

    fun showDatePicker() {
        val datePickerDialog = android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                calendar.set(java.util.Calendar.YEAR, year)
                calendar.set(java.util.Calendar.MONTH, month)
                calendar.set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth)
                dateInput = dateFormatter.format(calendar.time)
                departureEpoch = calendar.timeInMillis
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    fun showTimePicker() {
        val timePickerDialog = android.app.TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                calendar.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(java.util.Calendar.MINUTE, minute)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                timeInput = timeFormatter.format(calendar.time)
                departureEpoch = calendar.timeInMillis
            },
            calendar.get(java.util.Calendar.HOUR_OF_DAY),
            calendar.get(java.util.Calendar.MINUTE),
            false
        )
        timePickerDialog.show()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SplitCruiserTextPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Post a Trip Offer", color = SplitCruiserTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Origin AutoComplete
            LocationAutoCompleteTextField(
                value = origin,
                onValueChange = { origin = it },
                onLocationSelected = { place ->
                    originLat = place.lat
                    originLng = place.lng
                },
                label = "Pickup Location (Origin)",
                placeholder = "e.g. Mission Hill, Boston or Snell Library",
                testTag = "offer_origin_input",
                focusedBorderColor = SplitCruiserEmerald,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Pickup location icon",
                        tint = SplitCruiserEmerald
                    )
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Destination AutoComplete
            LocationAutoCompleteTextField(
                value = destination,
                onValueChange = { destination = it },
                onLocationSelected = { place ->
                    destLat = place.lat
                    destLng = place.lng
                },
                label = "Dropoff Location (Destination)",
                placeholder = "e.g. Logan Airport or NEU Campus",
                testTag = "offer_destination_input",
                focusedBorderColor = Color(0xFFF97316),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = "Dropoff location icon",
                        tint = Color(0xFFF97316)
                    )
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            GoogleMapsMatrixCard(origin = origin, destination = destination)

            Spacer(modifier = Modifier.height(14.dp))

            // Departure Date & Time Fields
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Departure Date Field
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showDatePicker() }
                ) {
                    OutlinedTextField(
                        value = dateInput,
                        onValueChange = { },
                        readOnly = true,
                        enabled = false,
                        label = { Text("Departure Date") },
                        modifier = Modifier.fillMaxWidth().testTag("offer_date_input"),
                        shape = RoundedCornerShape(14.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "Departure date icon",
                                tint = SplitCruiserSaffron
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledBorderColor = SplitCruiserDivider,
                            disabledTextColor = SplitCruiserTextPrimary,
                            disabledLabelColor = SplitCruiserLightGray,
                            disabledContainerColor = SplitCruiserCardBg
                        )
                    )
                }

                // Departure Time Field
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showTimePicker() }
                ) {
                    OutlinedTextField(
                        value = timeInput,
                        onValueChange = { },
                        readOnly = true,
                        enabled = false,
                        label = { Text("Departure Time") },
                        modifier = Modifier.fillMaxWidth().testTag("offer_time_input"),
                        shape = RoundedCornerShape(14.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = "Departure time icon",
                                tint = SplitCruiserSaffron
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledBorderColor = SplitCruiserDivider,
                            disabledTextColor = SplitCruiserTextPrimary,
                            disabledLabelColor = SplitCruiserLightGray,
                            disabledContainerColor = SplitCruiserCardBg
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                // Cost per rider
                OutlinedTextField(
                    value = costPerRider,
                    onValueChange = { costPerRider = it },
                    label = { Text("Cost Per Rider ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp)
                        .testTag("offer_cost_input"),
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AttachMoney,
                            contentDescription = "Cost icon",
                            tint = Color(0xFFEAB308)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFEAB308),
                        unfocusedBorderColor = SplitCruiserDivider,
                        focusedTextColor = SplitCruiserTextPrimary,
                        unfocusedTextColor = SplitCruiserTextPrimary,
                        focusedLabelColor = Color(0xFFEAB308),
                        unfocusedLabelColor = SplitCruiserLightGray,
                        focusedContainerColor = SplitCruiserCardBg,
                        unfocusedContainerColor = SplitCruiserCardBg
                    )
                )

                // Seats
                OutlinedTextField(
                    value = totalSeats,
                    onValueChange = { totalSeats = it },
                    label = { Text("Seats Available") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp)
                        .testTag("offer_seats_input"),
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = "Seats icon",
                            tint = Color(0xFF8B5CF6)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF8B5CF6),
                        unfocusedBorderColor = SplitCruiserDivider,
                        focusedTextColor = SplitCruiserTextPrimary,
                        unfocusedTextColor = SplitCruiserTextPrimary,
                        focusedLabelColor = Color(0xFF8B5CF6),
                        unfocusedLabelColor = SplitCruiserLightGray,
                        focusedContainerColor = SplitCruiserCardBg,
                        unfocusedContainerColor = SplitCruiserCardBg
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Women Only Offer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SplitCruiserCardBg, RoundedCornerShape(12.dp))
                    .border(1.dp, SplitCruiserDivider, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Female, contentDescription = "Women Only", tint = Color(0xFFE91E63))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Women-Only Trip Offer", color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Only visible to other female riders", color = SplitCruiserLightGray, fontSize = 10.sp)
                    }
                }
                Switch(
                    checked = womenOnly,
                    onCheckedChange = { womenOnly = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFE91E63))
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Vehicle Check
            if (userVehicle == null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SplitCruiserSaffron.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = "No vehicle", tint = SplitCruiserSaffron)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "You haven't setup your vehicle details. We'll post using a standard Sedan. Setup vehicle in Profile anytime.",
                            color = SplitCruiserTextPrimary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = {
                    if (origin.isNotEmpty() && destination.isNotEmpty() && costPerRider.isNotEmpty()) {
                        val cost = costPerRider.toDoubleOrNull() ?: 10.0
                        val seats = totalSeats.toIntOrNull() ?: 4
                        val vehicleLabel = if (userVehicle != null) {
                            "${userVehicle.color} ${userVehicle.make} ${userVehicle.model}"
                        } else {
                            "Shared Student Sedan"
                        }

                        val epoch = departureEpoch

                        val offer = TripOffer(
                            origin = origin,
                            destination = destination,
                            originLat = originLat,
                            originLng = originLng,
                            destLat = destLat,
                            destLng = destLng,
                            costPerRider = cost,
                            totalSeats = seats,
                            seatsLeft = seats,
                            departureTime = epoch,
                            womenOnly = womenOnly,
                            vehicleInfo = vehicleLabel
                        )

                        viewModel.postOffer(offer) {
                            Toast.makeText(context, "Ride offer posted successfully!", Toast.LENGTH_LONG).show()
                            navController.popBackStack()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("submit_offer_button"),
                colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserSaffron),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Broadcast Ride Offer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

// --- Screen 6: Post Ride Request (Rider) ---

@Composable
fun PostRequestScreen(viewModel: MainViewModel, navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var origin by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var originLat by remember { mutableStateOf(42.33) }
    var originLng by remember { mutableStateOf(-71.08) }
    var destLat by remember { mutableStateOf(42.36) }
    var destLng by remember { mutableStateOf(-71.01) }
    
    val calendar = remember { java.util.Calendar.getInstance().apply { add(java.util.Calendar.HOUR_OF_DAY, 6) } }
    val formatter = remember { java.text.SimpleDateFormat("EEE, MMM d, yyyy - h:mm a", java.util.Locale.getDefault()) }
    var departureTimeInput by remember { mutableStateOf(formatter.format(calendar.time)) }
    var departureEpoch by remember { mutableStateOf(calendar.timeInMillis) }

    var seatsNeeded by remember { mutableStateOf("1") }
    var notes by remember { mutableStateOf("") }
    var womenOnly by remember { mutableStateOf(false) }

    fun showDateTimePicker() {
        val datePickerDialog = android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                calendar.set(java.util.Calendar.YEAR, year)
                calendar.set(java.util.Calendar.MONTH, month)
                calendar.set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth)
                
                val timePickerDialog = android.app.TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        calendar.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(java.util.Calendar.MINUTE, minute)
                        calendar.set(java.util.Calendar.SECOND, 0)
                        calendar.set(java.util.Calendar.MILLISECOND, 0)
                        
                        departureTimeInput = formatter.format(calendar.time)
                        departureEpoch = calendar.timeInMillis
                    },
                    calendar.get(java.util.Calendar.HOUR_OF_DAY),
                    calendar.get(java.util.Calendar.MINUTE),
                    false
                )
                timePickerDialog.show()
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SplitCruiserTextPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Post a Ride Request", color = SplitCruiserTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Origin AutoComplete
            LocationAutoCompleteTextField(
                value = origin,
                onValueChange = { origin = it },
                onLocationSelected = { place ->
                    originLat = place.lat
                    originLng = place.lng
                },
                label = "Where to pick you up?",
                placeholder = "e.g. Snell Library lobby or Ruggles Station",
                testTag = "request_origin_input",
                focusedBorderColor = SplitCruiserEmerald,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Pickup location icon",
                        tint = SplitCruiserEmerald
                    )
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Destination AutoComplete
            LocationAutoCompleteTextField(
                value = destination,
                onValueChange = { destination = it },
                onLocationSelected = { place ->
                    destLat = place.lat
                    destLng = place.lng
                },
                label = "Where are you going?",
                placeholder = "e.g. Logan International Airport",
                testTag = "request_destination_input",
                focusedBorderColor = Color(0xFFF97316),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = "Dropoff location icon",
                        tint = Color(0xFFF97316)
                    )
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            GoogleMapsMatrixCard(origin = origin, destination = destination)

            Spacer(modifier = Modifier.height(14.dp))

            // Departure Time
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDateTimePicker() }
            ) {
                OutlinedTextField(
                    value = departureTimeInput,
                    onValueChange = { },
                    readOnly = true,
                    enabled = false,
                    label = { Text("Preferred Departure Time") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Departure time icon",
                            tint = SplitCruiserSaffron
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledBorderColor = SplitCruiserDivider,
                        disabledTextColor = SplitCruiserTextPrimary,
                        disabledLabelColor = SplitCruiserLightGray,
                        disabledContainerColor = SplitCruiserCardBg
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Seats needed
            OutlinedTextField(
                value = seatsNeeded,
                onValueChange = { seatsNeeded = it },
                label = { Text("Seats needed") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = "Seats icon",
                        tint = Color(0xFF8B5CF6)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF8B5CF6),
                    unfocusedBorderColor = SplitCruiserDivider,
                    focusedTextColor = SplitCruiserTextPrimary,
                    unfocusedTextColor = SplitCruiserTextPrimary,
                    focusedLabelColor = Color(0xFF8B5CF6),
                    unfocusedLabelColor = SplitCruiserLightGray,
                    focusedContainerColor = SplitCruiserCardBg,
                    unfocusedContainerColor = SplitCruiserCardBg
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes for host (Luggage details, etc.)") },
                placeholder = { Text("e.g. 1 big suitcase. Can pay via Venmo/cash.") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Notes icon",
                        tint = Color(0xFF14B8A6)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF14B8A6),
                    unfocusedBorderColor = SplitCruiserDivider,
                    focusedTextColor = SplitCruiserTextPrimary,
                    unfocusedTextColor = SplitCruiserTextPrimary,
                    focusedLabelColor = Color(0xFF14B8A6),
                    unfocusedLabelColor = SplitCruiserLightGray,
                    focusedContainerColor = SplitCruiserCardBg,
                    unfocusedContainerColor = SplitCruiserCardBg
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Women Only
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SplitCruiserCardBg, RoundedCornerShape(12.dp))
                    .border(1.dp, SplitCruiserDivider, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Female, contentDescription = "Women Only", tint = Color(0xFFE91E63))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Women-Only Request", color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Only visible to other female hosts", color = SplitCruiserLightGray, fontSize = 10.sp)
                    }
                }
                Switch(
                    checked = womenOnly,
                    onCheckedChange = { womenOnly = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFE91E63))
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (origin.isNotEmpty() && destination.isNotEmpty() && seatsNeeded.isNotEmpty()) {
                        val needed = seatsNeeded.toIntOrNull() ?: 1
                        val epoch = departureEpoch

                        val request = RideRequest(
                            origin = origin,
                            destination = destination,
                            originLat = originLat,
                            originLng = originLng,
                            destLat = destLat,
                            destLng = destLng,
                            seatsNeeded = needed,
                            departureTime = epoch,
                            notes = notes,
                            womenOnly = womenOnly
                        )

                        viewModel.postRequest(request) {
                            Toast.makeText(context, "Ride request posted successfully!", Toast.LENGTH_LONG).show()
                            navController.popBackStack()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("submit_request_button"),
                colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserSaffron),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Broadcast Ride Request", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

// --- Screen 7: Ride Detail Screen (Join / Accept / Decline matches) ---

@Composable
fun TripDetailScreen(id: String, type: String, viewModel: MainViewModel, navController: NavController) {
    val context = LocalContext.current
    val offers by viewModel.activeOffers.collectAsState()
    val requests by viewModel.activeRequests.collectAsState()
    val matches by viewModel.userMatches.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    var customContribution by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }

    if (type == "offer") {
        val offer = offers.find { it.id == id }
            ?: viewModel.repository.activeOffers.value.find { it.id == id } // fallback safety

        if (offer == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SplitCruiserDarkBg),
                contentAlignment = Alignment.Center
            ) {
                SplitCruiserEmptyState(
                    title = "Offer Unavailable",
                    description = "This carpool offer details are no longer available. It may have been completed, cancelled, or deleted by the host.",
                    icon = Icons.Default.Warning,
                    actionLabel = "Back to Feed",
                    onActionClick = { navController.popBackStack() }
                )
            }
            return
        }

        val formatter = remember { SimpleDateFormat("EEEE, d MMMM • h:mm a", Locale.US) }
        val dateStr = formatter.format(Date(offer.departureTime))

        // Precompute standard cost cap (2x cost per rider)
        val costLimit = offer.costPerRider * 2.0

        // Check if there is already an active match request on this offer from the current user
        val existingMatch = matches.find { it.offerId == offer.id && it.riderId == currentUser?.id }

        var isHostCardExpanded by remember { mutableStateOf(false) }
        var showDriverModal by remember { mutableStateOf(false) }

        // Animation state for action buttons
        var completeButtonPressed by remember { mutableStateOf(false) }
        var cancelButtonPressed by remember { mutableStateOf(false) }
        var joinButtonPressed by remember { mutableStateOf(false) }
        val completeScale = AnimatedButtonScale(completeButtonPressed)
        val cancelScale = AnimatedButtonScale(cancelButtonPressed)
        val joinScale = AnimatedButtonScale(joinButtonPressed)

        // Coordination state
        val matchMessages = existingMatch?.let { match ->
            remember(match.id) { viewModel.repository.allMessages.value.filter { it.matchId == match.id }.takeLast(3) }
        } ?: emptyList()

        val hostUser = remember(offer.hostId) { viewModel.getUserPublicProfile(offer.hostId) }
        val hostVehicle = remember(offer.hostId) { viewModel.getVehicleInfo(offer.hostId) }

        val hostEmail = hostUser?.email?.ifEmpty { null } ?: (offer.hostName.trim().lowercase().replace(" ", "") + "@example.com")
        val hostPhone = hostUser?.phoneNumber?.ifEmpty { null } ?: "+1 (555) 722-2469"
        val hostVerifiedTier = hostUser?.verifiedTier ?: (if (offer.hostRating >= 4.5f) "vouched" else "guest")

        val vehicleMakeModel = hostVehicle?.let { "${it.color} ${it.make} ${it.model}" } ?: offer.vehicleInfo.ifEmpty { "Shared Sedan" }
        val vehiclePlate = hostVehicle?.licensePlate?.ifEmpty { null } ?: "STU-1829"
        val vehicleYear = hostVehicle?.year?.ifEmpty { null } ?: "2022"
        val vehicleColor = hostVehicle?.color?.ifEmpty { null } ?: "Slate Gray"

        if (showDriverModal) {
            DriverContactModal(
                hostName = offer.hostName,
                hostRating = offer.hostRating,
                hostPhone = hostPhone,
                hostEmail = hostEmail,
                verifiedTier = hostVerifiedTier,
                vehicleMakeModel = vehicleMakeModel,
                vehicleYear = vehicleYear,
                vehicleColor = vehicleColor,
                vehiclePlate = vehiclePlate,
                onDismiss = { showDriverModal = false }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SplitCruiserTextPrimary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Trip Offer Details", color = SplitCruiserTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Route card
                Card(
                    colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SplitCruiserDivider, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.RadioButtonChecked, contentDescription = "Start", tint = SplitCruiserSaffron, modifier = Modifier.size(16.dp))
                                Box(modifier = Modifier.width(2.dp).height(40.dp).background(SplitCruiserDivider))
                                Icon(Icons.Default.Place, contentDescription = "End", tint = SplitCruiserIndigo, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("PICKUP", color = SplitCruiserLightGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text(offer.origin, color = SplitCruiserTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(22.dp))
                                Text("DROPOFF", color = SplitCruiserLightGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text(offer.destination, color = SplitCruiserTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                GoogleMapsMatrixCard(origin = offer.origin, destination = offer.destination)

                Spacer(modifier = Modifier.height(16.dp))

                // Status and Seat info Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SplitCruiserDivider, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("STATUS & SEATS", color = SplitCruiserSaffron, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            
                            // Badge with status color
                            val badgeBg = when (offer.status.lowercase()) {
                                "active" -> SplitCruiserIndigo
                                "full" -> Color(0xFFFEF3C7) // Amber
                                "completed" -> SplitCruiserEmerald.copy(alpha = 0.2f)
                                "cancelled" -> Color(0xFFFEE2E2) // Light red
                                else -> SplitCruiserIndigo
                            }
                            val badgeText = when (offer.status.lowercase()) {
                                "active" -> SplitCruiserSaffron
                                "full" -> Color(0xFFD97706)
                                "completed" -> SplitCruiserEmerald
                                "cancelled" -> Color(0xFFDC2626)
                                else -> SplitCruiserSaffron
                            }
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(badgeBg)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = offer.status.uppercase(),
                                    color = badgeText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Departure Time:", color = SplitCruiserTextPrimary, fontSize = 13.sp)
                            Text(dateStr, color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Available Seats:", color = SplitCruiserTextPrimary, fontSize = 13.sp)
                            Text("${offer.seatsLeft} of ${offer.totalSeats} seats left", color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Host Details
                Card(
                    colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isHostCardExpanded = !isHostCardExpanded }
                        .border(1.dp, SplitCruiserDivider, RoundedCornerShape(16.dp))
                ) {
                    Column {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            val hostUser = remember(offer.hostId) { viewModel.getUserPublicProfile(offer.hostId) }
                            StudentAvatar(
                                avatarUrl = hostUser?.avatarUrl ?: "",
                                name = offer.hostName,
                                size = 48.dp
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(offer.hostName, color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Host Rating: ${String.format(Locale.US, "%.1f", offer.hostRating)} ★", color = SplitCruiserLightGray, fontSize = 12.sp)
                                    if (hostUser?.collegeName?.isNotEmpty() == true) {
                                        Text(" • ${hostUser.collegeName}", color = SplitCruiserSaffron, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            IconButton(onClick = {
                                isHostCardExpanded = !isHostCardExpanded
                            }) {
                                Icon(
                                    imageVector = if (isHostCardExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Expand info",
                                    tint = SplitCruiserSaffron
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    viewModel.blockUser(offer.hostId) {
                                        navController.popBackStack()
                                    }
                                }
                            }) {
                                Icon(imageVector = Icons.Default.Block, contentDescription = "Block Host", tint = Color.Red.copy(alpha = 0.6f))
                            }
                        }

                        AnimatedVisibility(visible = isHostCardExpanded) {
                            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                                HorizontalDivider(color = SplitCruiserDivider)
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Text(
                                    text = "VEHICLE & CONTACT OVERVIEW",
                                    color = SplitCruiserSaffron,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.DirectionsCar, contentDescription = "Vehicle", tint = SplitCruiserLightGray, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Vehicle: $vehicleMakeModel", color = SplitCruiserTextPrimary, fontSize = 13.sp)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Phone, contentDescription = "Phone", tint = SplitCruiserLightGray, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Phone: $hostPhone", color = SplitCruiserTextPrimary, fontSize = 13.sp)
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { showDriverModal = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserIndigo),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(36.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = "Contact", tint = SplitCruiserSaffron, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("View Full Driver & Contact Card", color = SplitCruiserSaffron, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Cost split calculations
                Card(
                    colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SplitCruiserDivider, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("COST ALLOCATION", color = SplitCruiserSaffron, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Suggested Gas Contribution:", color = SplitCruiserTextPrimary, fontSize = 13.sp)
                            Text("$${offer.costPerRider}", color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Server Max Limit (2x Cost Cap):", color = SplitCruiserLightGray, fontSize = 12.sp)
                            Text("$${costLimit}", color = SplitCruiserLightGray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Divider(color = SplitCruiserDivider)
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = SplitCruiserEmerald, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Cash split is paid in-person directly to the host. No commission or app fees.",
                                color = SplitCruiserLightGray,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (offer.passengers.isNotEmpty()) {
                    Text("RESERVED PASSENGERS", color = SplitCruiserSaffron, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SplitCruiserDivider, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            offer.passengerNames.zip(offer.passengers).forEachIndexed { index, (name, id) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = "Passenger", tint = SplitCruiserLightGray, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (id == currentUser?.id) "$name (You)" else name,
                                        color = SplitCruiserTextPrimary,
                                        fontWeight = if (id == currentUser?.id) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp
                                    )
                                }
                                if (index < offer.passengerNames.size - 1) {
                                    HorizontalDivider(color = SplitCruiserDivider, modifier = Modifier.padding(vertical = 4.dp))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Message history section (if match exists)
                if (existingMatch != null && matchMessages.isNotEmpty()) {
                    Text("RECENT COORDINATION", color = SplitCruiserSaffron, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SplitCruiserDivider, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            matchMessages.forEachIndexed { index, message ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(
                                        imageVector = if (message.senderId == currentUser?.id) Icons.AutoMirrored.Filled.Send else Icons.AutoMirrored.Filled.Chat,
                                        contentDescription = "Message",
                                        tint = SplitCruiserLightGray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = message.text,
                                            color = SplitCruiserTextPrimary,
                                            fontSize = 12.sp,
                                            lineHeight = 15.sp
                                        )
                                        Text(
                                            text = "${if (message.senderId == currentUser?.id) "You" else offer.hostName}",
                                            color = SplitCruiserLightGray,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                if (index < matchMessages.size - 1) {
                                    HorizontalDivider(color = SplitCruiserDivider, modifier = Modifier.padding(vertical = 8.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { navController.navigate("chat/${existingMatch.id}") },
                                colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserIndigo),
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                contentPadding = PaddingValues(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat", tint = SplitCruiserSaffron, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("View Full Chat", color = SplitCruiserSaffron, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                val isHost = (offer.hostId == currentUser?.id)

                if (isHost) {
                    Text("HOST CONTROLS", color = SplitCruiserSaffron, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SplitCruiserDivider, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Manage ride status in Firestore:", color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (offer.status != "active") {
                                    Button(
                                        onClick = {
                                            viewModel.updateTripOfferStatus(offer.id, "active") {
                                                Toast.makeText(context, "Ride is now active!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserSaffron),
                                        modifier = Modifier.weight(1f).testTag("host_status_active_btn")
                                    ) {
                                        Text("Set Active", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (offer.status != "full" && offer.seatsLeft > 0) {
                                    Button(
                                        onClick = {
                                            viewModel.updateTripOfferStatus(offer.id, "full") {
                                                Toast.makeText(context, "Ride marked as full!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                        modifier = Modifier.weight(1f).testTag("host_status_full_btn")
                                    ) {
                                        Text("Set Full", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (offer.status != "completed") {
                                    Button(
                                        onClick = {
                                            completeButtonPressed = true
                                            vibrate(context, 50)
                                            viewModel.updateTripOfferStatus(offer.id, "completed") {
                                                completeButtonPressed = false
                                                vibrateSuccess(context)
                                                Toast.makeText(context, "Ride completed!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserEmerald),
                                        modifier = Modifier.weight(1f).testTag("host_status_completed_btn").withButtonScale(completeScale)
                                    ) {
                                        Text("Complete", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (offer.status != "cancelled") {
                                    Button(
                                        onClick = {
                                            cancelButtonPressed = true
                                            vibrate(context, 50)
                                            viewModel.updateTripOfferStatus(offer.id, "cancelled") {
                                                cancelButtonPressed = false
                                                Toast.makeText(context, "Ride cancelled!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                        modifier = Modifier.weight(1f).testTag("host_status_cancelled_btn").withButtonScale(cancelScale)
                                    ) {
                                        Text("Cancel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Current user is a potential rider/passenger
                    val hasAlreadyJoined = offer.passengers.contains(currentUser?.id)
                    
                    if (hasAlreadyJoined) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SplitCruiserEmerald.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Reserved", tint = SplitCruiserEmerald, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Your seat is reserved!",
                                    color = SplitCruiserTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "You have successfully joined this ride. Coordinate details with ${offer.hostName}.",
                                    color = SplitCruiserTextSecondary,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    } else {
                        // Show "Join ride" button if active and seats left
                        when {
                            offer.status == "completed" -> {
                                Button(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier.fillMaxWidth().height(54.dp),
                                    colors = ButtonDefaults.buttonColors(disabledContainerColor = SplitCruiserDivider)
                                ) {
                                    Text("This Trip is Completed", color = SplitCruiserLightGray, fontWeight = FontWeight.Bold)
                                }
                            }
                            offer.status == "cancelled" -> {
                                Button(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier.fillMaxWidth().height(54.dp),
                                    colors = ButtonDefaults.buttonColors(disabledContainerColor = SplitCruiserDivider)
                                ) {
                                    Text("This Trip is Cancelled", color = SplitCruiserLightGray, fontWeight = FontWeight.Bold)
                                }
                            }
                            offer.status == "full" || offer.seatsLeft <= 0 -> {
                                Button(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier.fillMaxWidth().height(54.dp),
                                    colors = ButtonDefaults.buttonColors(disabledContainerColor = SplitCruiserDivider)
                                ) {
                                    Text("Ride is Full", color = SplitCruiserLightGray, fontWeight = FontWeight.Bold)
                                }
                            }
                            else -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // Show Direct Join Button
                                    Button(
                                        onClick = {
                                            joinButtonPressed = true
                                            vibrate(context, 50)
                                            viewModel.joinTripOfferDirect(offer.id) {
                                                joinButtonPressed = false
                                                vibrateSuccess(context)
                                                Toast.makeText(context, "Successfully joined the ride!", Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp)
                                            .testTag("direct_join_button")
                                            .withButtonScale(joinScale),
                                        colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserSaffron),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Join", tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Join Ride (Reserve Seat)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                    
                                    Spacer(modifier = Modifier.height(20.dp))
                                    
                                    // Alternatively keep the original Request to Join Match system as secondary option
                                    Text(
                                        text = "OR PROPOSE CUSTOM CONTRIBUTION:",
                                        color = SplitCruiserLightGray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    )
                                    
                                    Spacer(modifier = Modifier.height(12.dp))

                                    if (existingMatch == null) {
                                        if (customContribution.isEmpty()) {
                                            customContribution = offer.costPerRider.toString()
                                        }

                                        OutlinedTextField(
                                            value = customContribution,
                                            onValueChange = { customContribution = it },
                                            label = { Text("Your Proposed Contribution ($)") },
                                            placeholder = { Text(offer.costPerRider.toString()) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = SplitCruiserSaffron,
                                                unfocusedBorderColor = SplitCruiserDivider,
                                                focusedTextColor = SplitCruiserTextPrimary,
                                                unfocusedTextColor = SplitCruiserTextPrimary,
                                                focusedContainerColor = SplitCruiserCardBg,
                                                unfocusedContainerColor = SplitCruiserCardBg
                                            )
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Button(
                                            onClick = {
                                                val contributionDouble = customContribution.toDoubleOrNull() ?: offer.costPerRider
                                                val dummyRequestId = "req_joined_${System.currentTimeMillis().toString().takeLast(6)}"
                                                viewModel.requestJoin(offer.id, dummyRequestId, contributionDouble) {
                                                    showSuccessDialog = true
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("propose_contribution_button"),
                                            colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserIndigo),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Propose Contribution", color = SplitCruiserSaffron, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = if (existingMatch.status == "accepted") SplitCruiserEmerald.copy(alpha = 0.15f) else SplitCruiserSaffron.copy(alpha = 0.15f)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = if (existingMatch.status == "accepted") Icons.Default.CheckCircle else Icons.Default.HourglassEmpty,
                                                        contentDescription = "Status",
                                                        tint = if (existingMatch.status == "accepted") SplitCruiserEmerald else SplitCruiserSaffron
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Text(
                                                        text = if (existingMatch.status == "accepted") "Host accepted your request!" else "Request is pending host approval",
                                                        color = SplitCruiserTextPrimary,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    )
                                                }
                                                if (existingMatch.status == "accepted") {
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    Button(
                                                        onClick = { navController.navigate("chat/${existingMatch.id}") },
                                                        colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserEmerald),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text("Open Coordinator Chat", color = Color.White, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // REQUEST DETAIL (Host views this to offer/accept ride)
        val request = requests.find { it.id == id }
            ?: viewModel.repository.activeRequests.value.find { it.id == id }

        if (request == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SplitCruiserDarkBg),
                contentAlignment = Alignment.Center
            ) {
                SplitCruiserEmptyState(
                    title = "Request Unavailable",
                    description = "This student ride request details are no longer available. It may have been matched, cancelled, or deleted by the rider.",
                    icon = Icons.Default.Warning,
                    actionLabel = "Back to Feed",
                    onActionClick = { navController.popBackStack() }
                )
            }
            return
        }

        // Host views student rider request
        val hostMatches = matches.filter { it.requestId == request.id && it.hostId == currentUser?.id }
        val activePendingMatch = hostMatches.find { it.status == "pending" }
        val activeAcceptedMatch = hostMatches.find { it.status == "accepted" }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SplitCruiserTextPrimary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ride Request Details", color = SplitCruiserTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Route card
                Card(
                    colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SplitCruiserDivider, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.RadioButtonChecked, contentDescription = "Start", tint = SplitCruiserSaffron, modifier = Modifier.size(16.dp))
                                Box(modifier = Modifier.width(2.dp).height(40.dp).background(SplitCruiserDivider))
                                Icon(Icons.Default.Place, contentDescription = "End", tint = SplitCruiserIndigo, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("RIDER PICKUP", color = SplitCruiserLightGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text(request.origin, color = SplitCruiserTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(22.dp))
                                Text("RIDER DROPOFF", color = SplitCruiserLightGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text(request.destination, color = SplitCruiserTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                GoogleMapsMatrixCard(origin = request.origin, destination = request.destination)

                Spacer(modifier = Modifier.height(16.dp))

                // Rider details
                Card(
                    colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SplitCruiserDivider, RoundedCornerShape(16.dp))
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = 0.8f,
                                stiffness = 500f
                            )
                        )
                ) {
                    Column {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(SplitCruiserSaffron.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(request.riderName.take(1).uppercase(), color = SplitCruiserSaffron, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(request.riderName, color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Rider Rating: ${String.format(Locale.US, "%.1f", request.riderRating)} ★", color = SplitCruiserLightGray, fontSize = 12.sp)
                        }
                        IconButton(onClick = {
                            coroutineScope.launch {
                                viewModel.blockUser(request.riderId) {
                                    navController.popBackStack()
                                }
                            }
                        }) {
                            Icon(imageVector = Icons.Default.Block, contentDescription = "Block Rider", tint = Color.Red.copy(alpha = 0.6f))
                        }
                    }
                }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (request.notes.isNotEmpty()) {
                    Text("RIDER NOTES", color = SplitCruiserSaffron, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SplitCruiserDivider, RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            text = "\"${request.notes}\"",
                            color = SplitCruiserTextPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Match status actions for Host
                if (activeAcceptedMatch != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SplitCruiserEmerald.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("You accepted this ride request!", color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { navController.navigate("chat/${activeAcceptedMatch.id}") },
                                colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserEmerald),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open Chat Room", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (activePendingMatch != null) {
                    Text("PENDING COST-SPLIT MATCH", color = SplitCruiserSaffron, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SplitCruiserDivider, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Rider offered gas contribution split:", color = SplitCruiserLightGray, fontSize = 12.sp)
                            Text("$${activePendingMatch.contribution}", color = SplitCruiserTextPrimary, fontWeight = FontWeight.Black, fontSize = 24.sp)
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { viewModel.declineMatch(activePendingMatch.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                                    modifier = Modifier.weight(1f).padding(end = 6.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Decline", color = Color.White)
                                }
                                Button(
                                    onClick = { viewModel.acceptMatch(activePendingMatch.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserEmerald),
                                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Accept & Chat", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            // Propose a quick direct match
                            val dummyOfferId = "offer_quick_${System.currentTimeMillis().toString().takeLast(6)}"
                            viewModel.requestJoin(dummyOfferId, request.id, 15.0) {
                                // Match request submitted
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserSaffron),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Accept & Offer Ride Share", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showSuccessDialog && type == "offer") {
        val offer = offers.find { it.id == id } ?: viewModel.repository.activeOffers.value.find { it.id == id }
        if (offer != null) {
            JoinSuccessDialog(
                offer = offer,
                onDismiss = { showSuccessDialog = false },
                onViewTrips = {
                    showSuccessDialog = false
                    navController.navigate("dashboard") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}

// --- Screen 8: Real-time Coordinate & Coordination Chat ---

@Composable
fun ChatScreen(matchId: String, viewModel: MainViewModel, navController: NavController) {
    val context = LocalContext.current
    val messageList by viewModel.getChatMessages(matchId).collectAsState(initial = emptyList())
    var currentMsgText by remember { mutableStateOf("") }
    val currentUser by viewModel.currentUser.collectAsState()
    val matches by viewModel.userMatches.collectAsState()

    val currentMatch = matches.find { it.id == matchId }
    val coroutineScope = rememberCoroutineScope()

    val currentOffer = remember(currentMatch) { currentMatch?.let { viewModel.getTripOfferById(it.offerId) } }
    var isOfferDetailsExpanded by remember { mutableStateOf(false) }
    var showProposeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (currentMatch?.hostId == currentUser?.id) "Ride with ${currentMatch?.riderName ?: "Student"}" else "Ride Coordinator Chat",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = SplitCruiserTextPrimary
                        )
                        Text(
                            text = "Split Contribution: $${currentMatch?.contribution ?: 0.0}",
                            fontSize = 11.sp,
                            color = SplitCruiserSaffron
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SplitCruiserTextPrimary)
                    }
                },
                actions = {
                    // Fast OS Share sheet button to share trip coordination details
                    IconButton(onClick = {
                        val shareText = "Hey! I'm carpooling on Split Cruiser. Match details: contribution $${currentMatch?.contribution}, status: ${currentMatch?.status}. Coordinate on app!"
                        val intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Trip Details"))
                    }) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = SplitCruiserSaffron)
                    }

                    // Complete Trip / Rating Action
                    IconButton(onClick = {
                        coroutineScope.launch {
                            viewModel.completeTrip(matchId)
                            // Prompt Rating dialog / screen
                            navController.navigate("profile")
                        }
                    }) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Complete Trip", tint = SplitCruiserEmerald)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SplitCruiserCardBg)
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SplitCruiserCardBg)
                    .border(1.dp, SplitCruiserDivider)
                    .navigationBarsPadding()
                    .padding(vertical = 8.dp)
            ) {
                // Coordinate & Quick Replies row
                val quickReplies = listOf(
                    "I'm here! 📍",
                    "A few minutes late ⏳",
                    "Leaving now! 🚗",
                    "I'm at the entrance 🏫",
                    "Suggest meeting spot? 🤔",
                    "No problem! 👍"
                )
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(quickReplies) { text ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(SplitCruiserIndigo.copy(alpha = 0.4f))
                                .clickable {
                                    viewModel.sendMessage(matchId, text)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = text, color = SplitCruiserSaffron, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = currentMsgText,
                        onValueChange = { currentMsgText = it },
                        placeholder = { Text("Coordinate pickup...") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SplitCruiserTextPrimary,
                            unfocusedTextColor = SplitCruiserTextPrimary,
                            focusedBorderColor = SplitCruiserSaffron,
                            unfocusedBorderColor = SplitCruiserDivider,
                            focusedContainerColor = SplitCruiserDarkBg,
                            unfocusedContainerColor = SplitCruiserDarkBg
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = {
                            if (currentMsgText.trim().isNotEmpty()) {
                                viewModel.sendMessage(matchId, currentMsgText.trim())
                                currentMsgText = ""
                            }
                        },
                        containerColor = SplitCruiserSaffron,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("send_msg_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        containerColor = SplitCruiserDarkBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (currentOffer != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = SplitCruiserIndigo.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SplitCruiserIndigo)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isOfferDetailsExpanded = !isOfferDetailsExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    tint = SplitCruiserSaffron,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Ride Details (ID: ${currentOffer.id.take(8).uppercase()})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = SplitCruiserTextPrimary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isOfferDetailsExpanded) "Hide" else "Show",
                                    fontSize = 12.sp,
                                    color = SplitCruiserSaffron,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (isOfferDetailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = SplitCruiserSaffron,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (isOfferDetailsExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Divider(color = SplitCruiserDivider.copy(alpha = 0.5f))
                                
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(imageVector = Icons.Default.Place, contentDescription = null, tint = SplitCruiserSaffron, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text("FROM:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SplitCruiserLightGray)
                                        Text(currentOffer.origin, fontSize = 12.sp, color = SplitCruiserTextPrimary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("TO:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SplitCruiserLightGray)
                                        Text(currentOffer.destination, fontSize = 12.sp, color = SplitCruiserTextPrimary)
                                    }
                                }

                                val formattedTime = remember(currentOffer.departureTime) {
                                    try {
                                        val sdf = SimpleDateFormat("EEE, MMM dd 'at' hh:mm a", Locale.US)
                                        sdf.format(Date(currentOffer.departureTime))
                                    } catch (e: Exception) {
                                        "Flexible Departure"
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = SplitCruiserSaffron, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Departure: $formattedTime",
                                        fontSize = 12.sp,
                                        color = SplitCruiserTextPrimary
                                    )
                                }

                                if (currentOffer.vehicleInfo.isNotEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.DirectionsCar, contentDescription = null, tint = SplitCruiserSaffron, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Vehicle: ${currentOffer.vehicleInfo}",
                                            fontSize = 12.sp,
                                            color = SplitCruiserTextPrimary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Button(
                                    onClick = { showProposeDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserSaffron),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Coordinate Pickup Spot & Time", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                items(messageList) { msg ->
                    val isMe = (msg.senderId == currentUser?.id)
                    val isSystem = (msg.senderId == "system")
                    val isProposal = msg.text.startsWith("[PROPOSAL]")
                    val isConfirmed = msg.text.startsWith("[CONFIRMED]")

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = if (isSystem) Arrangement.Center else if (isMe) Arrangement.End else Arrangement.Start
                    ) {
                        if (isProposal) {
                            val parsed = remember(msg.text) {
                                val loc = msg.text.substringAfter("Location: ").substringBefore(" | Time:")
                                val time = msg.text.substringAfter("| Time: ")
                                Pair(loc, time)
                            }
                            Card(
                                modifier = Modifier
                                    .widthIn(max = 280.dp)
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isMe) SplitCruiserSaffron.copy(alpha = 0.05f) else SplitCruiserIndigo.copy(alpha = 0.15f)
                                ),
                                border = BorderStroke(1.dp, SplitCruiserSaffron),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Place,
                                            contentDescription = null,
                                            tint = SplitCruiserSaffron,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Proposed Pickup Info",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = SplitCruiserSaffron
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "📍 Spot: ${parsed.first}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = SplitCruiserTextPrimary
                                    )
                                    Text(
                                        text = "⏰ Time: ${parsed.second}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = SplitCruiserTextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (!isMe) {
                                        Button(
                                            onClick = {
                                                viewModel.sendMessage(
                                                    matchId,
                                                    "[CONFIRMED] Meet at ${parsed.first} at ${parsed.second}"
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserEmerald),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(32.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.White)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Accept & Confirm", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(SplitCruiserLightGray.copy(alpha = 0.1f))
                                                .padding(vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Awaiting other's confirmation...",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = SplitCruiserLightGray
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (isConfirmed) {
                            val details = msg.text.substringAfter("[CONFIRMED] ")
                            Card(
                                modifier = Modifier
                                    .widthIn(max = 280.dp)
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = SplitCruiserEmerald.copy(alpha = 0.12f)
                                ),
                                border = BorderStroke(1.5.dp, SplitCruiserEmerald),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = SplitCruiserEmerald,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Pickup Confirmed!",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = SplitCruiserEmerald
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = details,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = SplitCruiserTextPrimary
                                    )
                                }
                            }
                        } else {
                            val bubbleShape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isMe) 16.dp else 0.dp,
                                bottomEnd = if (isMe) 0.dp else 16.dp
                            )
                            Box(
                                modifier = Modifier
                                    .clip(bubbleShape)
                                    .background(
                                        if (isSystem) Color(0xFFE2E8F0) else if (isMe) SplitCruiserSaffron else SplitCruiserCardBg
                                    )
                                    .then(
                                        if (isMe || isSystem) Modifier else Modifier.border(1.dp, SplitCruiserDivider, bubbleShape)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Column {
                                    if (!isSystem && !isMe) {
                                        Text(msg.senderName, color = SplitCruiserSaffron, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }
                                    Text(
                                        text = msg.text,
                                        color = if (isSystem) Color(0xFF64748B) else if (isMe) Color.White else SplitCruiserTextPrimary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProposeDialog) {
        ProposePickupDialog(
            onDismiss = { showProposeDialog = false },
            onPropose = { location, time ->
                viewModel.sendMessage(matchId, "[PROPOSAL] Location: $location | Time: $time")
                showProposeDialog = false
            }
        )
    }
}

@Composable
fun ProposePickupDialog(
    onDismiss: () -> Unit,
    onPropose: (location: String, time: String) -> Unit
) {
    var location by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Place, contentDescription = null, tint = SplitCruiserSaffron)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Propose Pickup Spot", color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        containerColor = SplitCruiserCardBg,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Suggest a specific meeting spot and time for your carpool buddy.",
                    color = SplitCruiserLightGray,
                    fontSize = 13.sp
                )

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Meeting Spot") },
                    placeholder = { Text("e.g. Science Library entrance") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SplitCruiserTextPrimary,
                        unfocusedTextColor = SplitCruiserTextPrimary,
                        focusedBorderColor = SplitCruiserSaffron,
                        unfocusedBorderColor = SplitCruiserDivider,
                        focusedLabelColor = SplitCruiserSaffron,
                        unfocusedLabelColor = SplitCruiserLightGray
                    )
                )

                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Proposed Time") },
                    placeholder = { Text("e.g. 5:45 PM or in 10 mins") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SplitCruiserTextPrimary,
                        unfocusedTextColor = SplitCruiserTextPrimary,
                        focusedBorderColor = SplitCruiserSaffron,
                        unfocusedBorderColor = SplitCruiserDivider,
                        focusedLabelColor = SplitCruiserSaffron,
                        unfocusedLabelColor = SplitCruiserLightGray
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (location.trim().isNotEmpty() && time.trim().isNotEmpty()) {
                        onPropose(location.trim(), time.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserSaffron),
                enabled = location.trim().isNotEmpty() && time.trim().isNotEmpty()
            ) {
                Text("Send Proposal", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SplitCruiserLightGray)
            }
        }
    )
}

@Composable
fun StudentAvatar(
    avatarUrl: String,
    name: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 64.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 24.sp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(SplitCruiserSaffron, SplitCruiserIndigo))),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUrl.isNotEmpty()) {
            if (avatarUrl.startsWith("http")) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Profile Picture",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = painterResource(id = R.drawable.ic_launcher_foreground)
                )
            } else {
                val emoji = when (avatarUrl) {
                    "preset_grad" -> "🎓"
                    "preset_driver" -> "🚗"
                    "preset_tech" -> "💻"
                    "preset_explorer" -> "🎒"
                    "preset_star" -> "⭐"
                    "preset_globe" -> "🌐"
                    else -> ""
                }
                if (emoji.isNotEmpty()) {
                    Text(
                        text = emoji,
                        fontSize = (size.value * 0.5f).sp,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = name.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = fontSize
                    )
                }
            }
        } else {
            Text(
                text = name.take(1).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = fontSize
            )
        }
    }
}

@Composable
fun EditProfileDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    var name by remember { mutableStateOf(currentUser?.name ?: "") }
    var lastInitial by remember { mutableStateOf(currentUser?.lastInitial ?: "") }
    var collegeName by remember { mutableStateOf(currentUser?.collegeName ?: "") }
    var verifiedEmail by remember { mutableStateOf(currentUser?.verifiedEmail ?: "") }
    var avatarUrl by remember { mutableStateOf(currentUser?.avatarUrl ?: "") }
    var customUrlInput by remember { mutableStateOf(if (currentUser?.avatarUrl?.startsWith("http") == true) currentUser!!.avatarUrl else "") }

    val presets = listOf(
        "preset_grad" to "🎓",
        "preset_driver" to "🚗",
        "preset_tech" to "💻",
        "preset_explorer" to "🎒",
        "preset_star" to "⭐",
        "preset_globe" to "🌐"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Edit Your Profile", color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold)
        },
        containerColor = SplitCruiserCardBg,
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("PROFILE PICTURE", color = SplitCruiserSaffron, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StudentAvatar(
                            avatarUrl = if (customUrlInput.isNotEmpty()) customUrlInput else avatarUrl,
                            name = name,
                            size = 72.dp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Select a Preset Avatar:", color = SplitCruiserLightGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        presets.forEach { (presetKey, emoji) ->
                            val isSelected = avatarUrl == presetKey && customUrlInput.isEmpty()
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) SplitCruiserSaffron.copy(alpha = 0.25f) else SplitCruiserDarkBg)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) SplitCruiserSaffron else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        avatarUrl = presetKey
                                        customUrlInput = ""
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 18.sp)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = customUrlInput,
                        onValueChange = {
                            customUrlInput = it
                            if (it.isNotEmpty()) {
                                avatarUrl = it
                            }
                        },
                        label = { Text("Or Paste Custom Image URL") },
                        placeholder = { Text("https://example.com/avatar.jpg") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SplitCruiserSaffron,
                            unfocusedBorderColor = SplitCruiserDivider,
                            focusedTextColor = SplitCruiserTextPrimary,
                            unfocusedTextColor = SplitCruiserTextPrimary,
                            focusedLabelColor = SplitCruiserSaffron,
                            unfocusedLabelColor = SplitCruiserLightGray
                        )
                    )
                }

                item {
                    Divider(color = SplitCruiserDivider, modifier = Modifier.padding(vertical = 8.dp))
                    Text("PERSONAL DETAILS", color = SplitCruiserSaffron, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("First Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SplitCruiserSaffron,
                                unfocusedBorderColor = SplitCruiserDivider,
                                focusedTextColor = SplitCruiserTextPrimary,
                                unfocusedTextColor = SplitCruiserTextPrimary,
                                focusedLabelColor = SplitCruiserSaffron,
                                unfocusedLabelColor = SplitCruiserLightGray
                            )
                        )
                        OutlinedTextField(
                            value = lastInitial,
                            onValueChange = { lastInitial = it },
                            label = { Text("Initial") },
                            singleLine = true,
                            modifier = Modifier.width(60.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SplitCruiserSaffron,
                                unfocusedBorderColor = SplitCruiserDivider,
                                focusedTextColor = SplitCruiserTextPrimary,
                                unfocusedTextColor = SplitCruiserTextPrimary,
                                focusedLabelColor = SplitCruiserSaffron,
                                unfocusedLabelColor = SplitCruiserLightGray
                            )
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = collegeName,
                        onValueChange = { collegeName = it },
                        label = { Text("College / University") },
                        placeholder = { Text("e.g. Stanford University") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SplitCruiserSaffron,
                            unfocusedBorderColor = SplitCruiserDivider,
                            focusedTextColor = SplitCruiserTextPrimary,
                            unfocusedTextColor = SplitCruiserTextPrimary,
                            focusedLabelColor = SplitCruiserSaffron,
                            unfocusedLabelColor = SplitCruiserLightGray
                        )
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = verifiedEmail,
                        onValueChange = { verifiedEmail = it },
                        label = { Text("Verified Email") },
                        placeholder = { Text("e.g. user@example.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SplitCruiserSaffron,
                            unfocusedBorderColor = SplitCruiserDivider,
                            focusedTextColor = SplitCruiserTextPrimary,
                            unfocusedTextColor = SplitCruiserTextPrimary,
                            focusedLabelColor = SplitCruiserSaffron,
                            unfocusedLabelColor = SplitCruiserLightGray
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalAvatar = if (customUrlInput.isNotEmpty()) customUrlInput else avatarUrl
                    viewModel.updateUserProfileDetails(
                        name = name,
                        lastInitial = lastInitial,
                        collegeName = collegeName,
                        avatarUrl = finalAvatar,
                        verifiedEmail = verifiedEmail,
                        onSuccess = onDismiss
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserSaffron)
            ) {
                Text("Save Changes", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SplitCruiserLightGray)
            }
        }
    )
}

// --- Screen 9: Profile and Rating Settings ---

@Composable
fun ProfileScreen(viewModel: MainViewModel, navController: NavController) {
    val currentUser by viewModel.currentUser.collectAsState()
    val isFirebaseEnabled = viewModel.repository.isFirebaseEnabled
    var selectedCommunityId = currentUser?.communityId ?: ""
    val communities by viewModel.allCommunities.collectAsState()
    val userVehicle = viewModel.getVehicleInfo(currentUser?.id ?: "")
    val userAlerts by viewModel.notifications.collectAsState()

    var showEditProfileDialog by remember { mutableStateOf(false) }

    // Rating Submit State
    var ratingTargetUserId by remember { mutableStateOf("") }
    var ratingValue by remember { mutableStateOf(5f) }
    var ratingComment by remember { mutableStateOf("") }

    val userCommunity = communities.find { it.id == selectedCommunityId }?.name ?: "Indian Student Community"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Your Split Cruiser Account", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // User Identity Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    StudentAvatar(
                        avatarUrl = currentUser?.avatarUrl ?: "",
                        name = currentUser?.name ?: "S",
                        size = 72.dp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = currentUser?.displayName ?: "Verified Student",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    val college = currentUser?.collegeName ?: ""
                    if (college.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = "College",
                                tint = SplitCruiserSaffron,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = college,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "No college set yet. Add yours below!",
                            color = SplitCruiserLightGray,
                            fontSize = 11.sp
                        )
                    }

                    val verifiedEmail = currentUser?.verifiedEmail ?: ""
                    if (verifiedEmail.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Verified Email",
                                tint = SplitCruiserSaffron,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = verifiedEmail,
                                color = SplitCruiserLightGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FirebaseStatusPill(isFirebaseEnabled = isFirebaseEnabled)
                        if (currentUser?.verifiedTier == "vouched" || verifiedEmail.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SplitCruiserEmerald.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Verified",
                                        tint = SplitCruiserEmerald,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Verified Student", color = SplitCruiserEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = { showEditProfileDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserSaffron.copy(alpha = 0.15f), contentColor = SplitCruiserSaffron),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("edit_profile_button")
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Profile", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit Profile Details", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = SplitCruiserDivider)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (currentUser?.ratingCount ?: 0 > 0) String.format(Locale.US, "%.1f ★", currentUser!!.ratingAvg) else "N/A",
                                color = SplitCruiserSaffron,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                            Text("Rating Avg", color = SplitCruiserLightGray, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${currentUser?.ratingCount ?: 0}",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                            Text("Trips Shared", color = SplitCruiserLightGray, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${currentUser?.noShowCount ?: 0}",
                                color = Color.Red.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                            Text("No Shows", color = SplitCruiserLightGray, fontSize = 11.sp)
                        }
                    }
                }
            }

            if (currentUser?.verifiedTier != "vouched") {
                Spacer(modifier = Modifier.height(20.dp))
                Text("VERIFY COLLEGE STUDENT STATUS", color = SplitCruiserSaffron, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(8.dp))

                var collegeEmailInput by remember { mutableStateOf("") }
                var verifyError by remember { mutableStateOf("") }
                var verifySuccess by remember { mutableStateOf("") }

                Card(
                    colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Verify an alternate email address to secure your account and unlock full vouched benefits!",
                            color = SplitCruiserLightGray,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = collegeEmailInput,
                            onValueChange = { 
                                collegeEmailInput = it
                                verifyError = ""
                                verifySuccess = ""
                            },
                            label = { Text("Alternate/Official Email") },
                            placeholder = { Text("e.g. user@example.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SplitCruiserSaffron,
                                unfocusedBorderColor = SplitCruiserDivider,
                                focusedTextColor = SplitCruiserTextPrimary,
                                unfocusedTextColor = SplitCruiserTextPrimary,
                                focusedLabelColor = SplitCruiserSaffron,
                                unfocusedLabelColor = SplitCruiserLightGray,
                                focusedContainerColor = SplitCruiserCardBg,
                                unfocusedContainerColor = SplitCruiserCardBg
                            )
                        )
                        if (verifyError.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(verifyError, color = Color.Red, fontSize = 11.sp)
                        }
                        if (verifySuccess.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(verifySuccess, color = SplitCruiserEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (collegeEmailInput.isNotEmpty()) {
                                    viewModel.verifyCollegeEmail(
                                        email = collegeEmailInput,
                                        onSuccess = {
                                            verifySuccess = "Successfully verified email!"
                                            collegeEmailInput = ""
                                        },
                                        onFailure = { err ->
                                            verifyError = err
                                        }
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserSaffron),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Verify & Update Profile", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Notification Preferences Settings
            Text("NOTIFICATION PREFERENCES", color = SplitCruiserSaffron, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Get real-time alerts whenever another student posts a carpool trip that matches your exact active ride requests.",
                        color = SplitCruiserLightGray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Email, contentDescription = "Email Settings", tint = SplitCruiserSaffron)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Email Notifications", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Receive matching routes via inbox", color = SplitCruiserLightGray, fontSize = 10.sp)
                            }
                        }
                        Switch(
                            checked = currentUser?.emailNotificationsEnabled ?: false,
                            onCheckedChange = { viewModel.toggleEmailNotifications(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = SplitCruiserSaffron)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = SplitCruiserDivider)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, contentDescription = "Push Settings", tint = SplitCruiserSaffron)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Push Notifications", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Instantly alert on device screen", color = SplitCruiserLightGray, fontSize = 10.sp)
                            }
                        }
                        Switch(
                            checked = currentUser?.pushNotificationsEnabled ?: false,
                            onCheckedChange = { viewModel.togglePushNotifications(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = SplitCruiserSaffron)
                        )
                    }
                }
            }

            if (userAlerts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ACTIVE TRIP ALERT MATCHES", color = SplitCruiserSaffron, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    TextButton(onClick = { viewModel.clearNotifications() }) {
                        Text("Clear All", color = Color.Red.copy(alpha = 0.8f), fontSize = 11.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                userAlerts.forEach { alert ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (alert.isRead) SplitCruiserCardBg.copy(alpha = 0.5f) else SplitCruiserCardBg
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .border(
                                width = if (alert.isRead) 0.dp else 1.dp,
                                color = SplitCruiserSaffron.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (alert.type == "email") Icons.Default.Email else Icons.Default.Notifications,
                                        contentDescription = "Alert",
                                        tint = if (alert.isRead) SplitCruiserLightGray else SplitCruiserSaffron,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = alert.title,
                                        color = if (alert.isRead) SplitCruiserLightGray else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                if (!alert.isRead) {
                                    TextButton(
                                        onClick = { viewModel.markNotificationAsRead(alert.id) },
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Text("Mark Read", color = SplitCruiserSaffron, fontSize = 10.sp)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = alert.message,
                                color = SplitCruiserLightGray,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Safety Filters Settings
            Text("SAFETY AND PRIVACY", color = SplitCruiserSaffron, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Female, contentDescription = "Women Filter", tint = Color(0xFFE91E63))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Women-Only Filter", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Only match with other women", color = SplitCruiserLightGray, fontSize = 10.sp)
                            }
                        }
                        Switch(
                            checked = currentUser?.isWomenOnlyFilterEnabled ?: false,
                            onCheckedChange = { viewModel.toggleWomenOnlyFilter(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFE91E63))
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = SplitCruiserDivider)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate("blocked_list") },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Block, contentDescription = "Blocked", tint = SplitCruiserLightGray)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Manage Blocked Students", color = Color.White, fontSize = 13.sp)
                        }
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Open", tint = SplitCruiserLightGray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Sub-Section: Fast submit mutual ratings
            Text("SUBMIT COMPANION RATING", color = SplitCruiserSaffron, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = SplitCruiserCardBg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = ratingTargetUserId,
                        onValueChange = { ratingTargetUserId = it },
                        label = { Text("Companion User ID (e.g. host_abc)") },
                        placeholder = { Text("user_123") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SplitCruiserSaffron)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Give Stars (1 to 5)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Slider(
                        value = ratingValue,
                        onValueChange = { ratingValue = it },
                        valueRange = 1f..5f,
                        steps = 3,
                        colors = SliderDefaults.colors(thumbColor = SplitCruiserSaffron, activeTrackColor = SplitCruiserSaffron)
                    )
                    Text("${ratingValue.toInt()} Stars Selected", color = SplitCruiserSaffron, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = ratingComment,
                        onValueChange = { ratingComment = it },
                        label = { Text("Comment (Vouch notes)") },
                        placeholder = { Text("Super friendly host, safe driving!") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SplitCruiserSaffron)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (ratingTargetUserId.isNotEmpty()) {
                                viewModel.submitRating(ratingTargetUserId, ratingValue, ratingComment) {
                                    ratingTargetUserId = ""
                                    ratingComment = ""
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserSaffron),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Submit Star Rating", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { viewModel.logout() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Log Out", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    if (showEditProfileDialog) {
        EditProfileDialog(
            viewModel = viewModel,
            onDismiss = { showEditProfileDialog = false }
        )
    }
}

// --- Screen 10: Block List Screen ---

@Composable
fun BlockedListScreen(viewModel: MainViewModel, navController: NavController) {
    val blockedUsers = viewModel.getBlockedUsers()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SplitCruiserTextPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Blocked Students", color = SplitCruiserTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (blockedUsers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SplitCruiserEmptyState(
                    title = "High Trust Community!",
                    description = "You haven't blocked any student. Everyone is vouched and trusted.",
                    icon = Icons.Default.Verified
                )
            }
        } else {
            LazyColumn {
                items(blockedUsers) { user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SplitCruiserCardBg)
                            .border(1.dp, SplitCruiserDivider, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(user.displayName, color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold)
                            Text("User ID: ${user.id}", color = SplitCruiserLightGray, fontSize = 11.sp)
                        }
                        Button(
                            onClick = { viewModel.unblockUser(user.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserSaffron),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Unblock", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DriverContactModal(
    hostName: String,
    hostRating: Float,
    hostPhone: String,
    hostEmail: String,
    verifiedTier: String,
    vehicleMakeModel: String,
    vehicleYear: String,
    vehicleColor: String,
    vehiclePlate: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = SplitCruiserCardBg,
            border = BorderStroke(1.dp, SplitCruiserDivider)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with icon and Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DRIVER & CONTACT CARD",
                        color = SplitCruiserSaffron,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SplitCruiserLightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Host Avatar and Basic info
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(SplitCruiserIndigo),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = hostName.take(1).uppercase(),
                        color = Color(0xFF001D36),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = hostName,
                    color = SplitCruiserTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                // Rating and verification badges
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = Color(0xFFEAB308),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f", hostRating),
                            color = SplitCruiserLightGray,
                            fontSize = 12.sp
                        )
                    }

                    // Vouched / Student badge
                    val isVouched = verifiedTier.lowercase() == "vouched"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isVouched) SplitCruiserEmerald.copy(alpha = 0.2f) else SplitCruiserLightGray.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isVouched) "VERIFIED STUDENT" else "GUEST USER",
                            color = if (isVouched) SplitCruiserEmerald else SplitCruiserLightGray,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = SplitCruiserDivider)
                Spacer(modifier = Modifier.height(16.dp))

                // Contact Information
                Text(
                    text = "CONTACT DETAILS",
                    color = SplitCruiserLightGray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Phone Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Phone Number", color = SplitCruiserLightGray, fontSize = 11.sp)
                        Text(hostPhone, color = SplitCruiserTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:$hostPhone"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot dial: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = SplitCruiserIndigo)
                    ) {
                        Icon(imageVector = Icons.Default.Phone, contentDescription = "Call", tint = SplitCruiserSaffron)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Email Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Email Address", color = SplitCruiserLightGray, fontSize = 11.sp)
                        Text(hostEmail, color = SplitCruiserTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:$hostEmail"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot email: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = SplitCruiserIndigo)
                    ) {
                        Icon(imageVector = Icons.Default.Email, contentDescription = "Email", tint = SplitCruiserSaffron)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = SplitCruiserDivider)
                Spacer(modifier = Modifier.height(16.dp))

                // Vehicle Information
                Text(
                    text = "VEHICLE DETAILS",
                    color = SplitCruiserLightGray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SplitCruiserIndigo),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = "Car",
                            tint = SplitCruiserSaffron
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = vehicleMakeModel,
                            color = SplitCruiserTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Color: $vehicleColor • Year: $vehicleYear",
                            color = SplitCruiserLightGray,
                            fontSize = 11.sp
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(SplitCruiserIndigo.copy(alpha = 0.3f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "License Plate: $vehiclePlate",
                                color = SplitCruiserSaffron,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserSaffron),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close Card", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun LocationAutoCompleteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onLocationSelected: (LocationPlace) -> Unit = {},
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    testTag: String = "",
    focusedBorderColor: Color = SplitCruiserEmerald,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var photonResults by remember { mutableStateOf<List<PhotonPlaceResult>>(emptyList()) }
    var isSearchingPhoton by remember { mutableStateOf(false) }
    var isReverseGeocodingGps by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Query Photon API with debounce when user types
    LaunchedEffect(value) {
        if (value.length >= 2) {
            isSearchingPhoton = true
            kotlinx.coroutines.delay(250) // Debounce
            val results = OsmLocationService.autocompletePhoton(value)
            photonResults = results
            isSearchingPhoton = false
        } else {
            photonResults = emptyList()
            isSearchingPhoton = false
        }
    }

    val filteredPlaces = remember(value, photonResults) {
        if (photonResults.isNotEmpty()) {
            photonResults.map { photon ->
                LocationPlace(
                    name = photon.name,
                    address = photon.formattedAddress,
                    category = when {
                        photon.type.contains("university", ignoreCase = true) || photon.type.contains("college", ignoreCase = true) -> "Campus"
                        photon.type.contains("aeroway", ignoreCase = true) || photon.name.contains("airport", ignoreCase = true) -> "Airport"
                        photon.type.contains("station", ignoreCase = true) || photon.type.contains("bus", ignoreCase = true) -> "Transit"
                        else -> "OSM Place"
                    },
                    lat = photon.lat,
                    lng = photon.lon
                )
            }
        } else if (value.isBlank()) {
            DEFAULT_LOCATION_PLACES.take(6)
        } else {
            DEFAULT_LOCATION_PLACES.filter {
                it.name.contains(value, ignoreCase = true) ||
                it.address.contains(value, ignoreCase = true) ||
                it.category.contains(value, ignoreCase = true)
            }.take(8)
        }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        expanded = true
                    }
                }
                .testTag(testTag),
            shape = RoundedCornerShape(14.dp),
            leadingIcon = leadingIcon,
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSearchingPhoton) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = SplitCruiserEmerald,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (value.isNotEmpty()) {
                        IconButton(onClick = {
                            onValueChange("")
                            expanded = true
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear location", tint = SplitCruiserLightGray)
                        }
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = "Toggle location suggestions",
                            tint = SplitCruiserLightGray
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = focusedBorderColor,
                unfocusedBorderColor = SplitCruiserDivider,
                focusedTextColor = SplitCruiserTextPrimary,
                unfocusedTextColor = SplitCruiserTextPrimary,
                focusedLabelColor = focusedBorderColor,
                unfocusedLabelColor = SplitCruiserLightGray,
                focusedContainerColor = SplitCruiserCardBg,
                unfocusedContainerColor = SplitCruiserCardBg
            ),
            singleLine = true
        )

        AnimatedVisibility(
            visible = expanded && (filteredPlaces.isNotEmpty() || value.isBlank()),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF202634)),
                border = BorderStroke(1.dp, SplitCruiserDivider),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (photonResults.isNotEmpty()) "OPENSTREETMAP PHOTON SUGGESTIONS" else if (value.isBlank()) "POPULAR CAMPUS & TRANSIT SPOTS" else "AUTO MATCHING PLACES",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (photonResults.isNotEmpty()) Color(0xFF38BDF8) else SplitCruiserSaffron,
                            letterSpacing = 0.5.sp
                        )

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SplitCruiserEmerald.copy(alpha = 0.15f))
                                .clickable {
                                    if (!isReverseGeocodingGps) {
                                        isReverseGeocodingGps = true
                                        scope.launch {
                                            val gpsLat = 42.3383
                                            val gpsLon = -71.0881
                                            val revResult = OsmLocationService.reverseGeocodeNominatim(gpsLat, gpsLon)
                                            val placeName = revResult?.road?.let { "$it (Northeastern Univ)" } ?: revResult?.displayName ?: "Snell Library, Boston"
                                            val placeAddr = revResult?.displayName ?: "360 Huntington Ave, Boston, MA"
                                            val gpsPlace = LocationPlace(placeName, placeAddr, "Nominatim GPS", gpsLat, gpsLon)
                                            onValueChange(gpsPlace.name)
                                            onLocationSelected(gpsPlace)
                                            isReverseGeocodingGps = false
                                            expanded = false
                                        }
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isReverseGeocodingGps) {
                                CircularProgressIndicator(modifier = Modifier.size(10.dp), color = SplitCruiserEmerald, strokeWidth = 1.5.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Nominatim GPS...", fontSize = 10.sp, color = SplitCruiserEmerald, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.MyLocation, contentDescription = null, tint = SplitCruiserEmerald, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Use GPS (Nominatim)", fontSize = 10.sp, color = SplitCruiserEmerald, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HorizontalDivider(color = SplitCruiserDivider, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                    filteredPlaces.forEach { place ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onValueChange(place.name)
                                    onLocationSelected(place)
                                    expanded = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (place.category) {
                                            "Campus" -> Color(0xFF3B82F6).copy(alpha = 0.2f)
                                            "Airport" -> Color(0xFFEAB308).copy(alpha = 0.2f)
                                            "Transit" -> Color(0xFF10B981).copy(alpha = 0.2f)
                                            "Neighborhood" -> Color(0xFFA855F7).copy(alpha = 0.2f)
                                            else -> Color(0xFF38BDF8).copy(alpha = 0.2f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (place.category) {
                                        "Campus" -> Icons.Default.School
                                        "Airport" -> Icons.Default.Flight
                                        "Transit" -> Icons.Default.DirectionsBus
                                        "Neighborhood" -> Icons.Default.LocationCity
                                        else -> Icons.Default.Place
                                    },
                                    contentDescription = null,
                                    tint = when (place.category) {
                                        "Campus" -> Color(0xFF60A5FA)
                                        "Airport" -> Color(0xFFFACC15)
                                        "Transit" -> SplitCruiserEmerald
                                        "Neighborhood" -> Color(0xFFC084FC)
                                        else -> Color(0xFF38BDF8)
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = place.name,
                                    color = SplitCruiserTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = place.address,
                                    color = SplitCruiserLightGray,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SplitCruiserCardBg
                            ) {
                                Text(
                                    text = place.category,
                                    color = SplitCruiserLightGray,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GoogleMapsMatrixCard(
    origin: String,
    destination: String,
    modifier: Modifier = Modifier
) {
    var isLoading by remember { mutableStateOf(false) }
    var matrixResult by remember { mutableStateOf<MapsRouteMatrixResult?>(null) }
    val scope = rememberCoroutineScope()

    if (origin.isBlank() || destination.isBlank()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2430)),
        border = BorderStroke(1.dp, Color(0xFF4285F4).copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4285F4).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            tint = Color(0xFF669DF6),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Google Maps Data Matrix",
                        color = SplitCruiserTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF34A853).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Gemini Grounded",
                        color = Color(0xFF34A853),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (matrixResult == null && !isLoading) {
                Button(
                    onClick = {
                        isLoading = true
                        scope.launch {
                            try {
                                val originResults = OsmLocationService.autocompletePhoton(origin, limit = 1)
                                val destResults = OsmLocationService.autocompletePhoton(destination, limit = 1)

                                if (originResults.isNotEmpty() && destResults.isNotEmpty()) {
                                    val originPlace = originResults[0]
                                    val destPlace = destResults[0]

                                    // The shared service throws rather than returning Result, which
                                    // does not survive the Swift export; this is the null-returning
                                    // variant for callers that just want to skip the estimate.
                                    val route = OsrmRouteService.getRouteOrNull(
                                        originLat = originPlace.lat,
                                        originLon = originPlace.lon,
                                        destLat = destPlace.lat,
                                        destLon = destPlace.lon
                                    )

                                    matrixResult = if (route != null) {
                                        MapsRouteMatrixResult(
                                            distanceText = route.distanceText,
                                            durationText = route.durationText,
                                            routeSummary = "Route from ${originPlace.name} to ${destPlace.name}",
                                            pickupRecommendation = originPlace.formattedAddress,
                                            dropoffRecommendation = destPlace.formattedAddress,
                                            universityContext = "Route between ${originPlace.city ?: "the area"} and ${destPlace.city ?: "the area"}",
                                            fullGroundedText = "Distance: ${route.distanceText}, Duration: ${route.durationText}"
                                        )
                                    } else {
                                        val straightDist = GeoUtils.distanceInMiles(originPlace.lat, originPlace.lon, destPlace.lat, destPlace.lon)
                                        val estimatedTime = straightDist * 1.3
                                        MapsRouteMatrixResult(
                                            distanceText = "~%.1f mi".format(straightDist),
                                            durationText = "~%.0f min".format(estimatedTime),
                                            routeSummary = "Estimated route from ${originPlace.name} to ${destPlace.name}",
                                            pickupRecommendation = originPlace.formattedAddress,
                                            dropoffRecommendation = destPlace.formattedAddress,
                                            universityContext = "Route between ${originPlace.city ?: "the area"} and ${destPlace.city ?: "the area"}",
                                            fullGroundedText = "Estimated distance: ~%.1f mi".format(straightDist)
                                        )
                                    }
                                } else {
                                    matrixResult = MapsRouteMatrixResult(
                                        distanceText = "Unknown",
                                        durationText = "Unknown",
                                        routeSummary = "Could not find route from $origin to $destination",
                                        pickupRecommendation = origin,
                                        dropoffRecommendation = destination,
                                        universityContext = "Location search failed",
                                        fullGroundedText = "Unable to geocode locations"
                                    )
                                }
                            } catch (e: Exception) {
                                matrixResult = MapsRouteMatrixResult(
                                    distanceText = "Error",
                                    durationText = "Error",
                                    routeSummary = "Failed to calculate route",
                                    pickupRecommendation = origin,
                                    dropoffRecommendation = destination,
                                    universityContext = "Route calculation failed",
                                    fullGroundedText = "Error: ${e.message}"
                                )
                            }
                            isLoading = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("calculate_maps_matrix_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Directions,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Calculate Distance & Route",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFF4285F4),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Calculating route...",
                        color = SplitCruiserLightGray,
                        fontSize = 12.sp
                    )
                }
            } else if (matrixResult != null) {
                val data = matrixResult!!
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ESTIMATED DISTANCE", fontSize = 9.sp, color = SplitCruiserLightGray, fontWeight = FontWeight.Bold)
                        Text(data.distanceText, fontSize = 14.sp, color = Color(0xFF4285F4), fontWeight = FontWeight.ExtraBold)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("DRIVING TIME", fontSize = 9.sp, color = SplitCruiserLightGray, fontWeight = FontWeight.Bold)
                        Text(data.durationText, fontSize = 14.sp, color = Color(0xFF34A853), fontWeight = FontWeight.ExtraBold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = SplitCruiserDivider.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                Text("ROUTE SUMMARY", fontSize = 9.sp, color = SplitCruiserSaffron, fontWeight = FontWeight.Bold)
                Text(data.routeSummary, fontSize = 12.sp, color = SplitCruiserTextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)

                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.School, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(data.universityContext, fontSize = 11.sp, color = SplitCruiserLightGray)
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, tint = SplitCruiserEmerald, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pickup Hub: ${data.pickupRecommendation}", fontSize = 11.sp, color = SplitCruiserTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFFF97316), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Dropoff Hub: ${data.dropoffRecommendation}", fontSize = 11.sp, color = SplitCruiserTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

