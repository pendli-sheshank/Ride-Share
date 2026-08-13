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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.splitcruiser.app.BuildConfig
import com.splitcruiser.app.R
import com.splitcruiser.app.data.*
import com.splitcruiser.app.ui.theme.SplitCruiserAvatars
import com.splitcruiser.app.ui.theme.SplitCruiserAccent
import com.splitcruiser.app.ui.theme.SplitCruiserDanger
import com.splitcruiser.app.ui.theme.SplitCruiserInfo
import com.splitcruiser.app.ui.theme.SplitCruiserOnPrimary
import com.splitcruiser.app.ui.theme.SplitCruiserOnPrimaryContainer
import com.splitcruiser.app.ui.theme.SplitCruiserOutline
import com.splitcruiser.app.ui.theme.SplitCruiserPrimary
import com.splitcruiser.app.ui.theme.SplitCruiserPrimaryContainer
import com.splitcruiser.app.ui.theme.SplitCruiserRadius
import com.splitcruiser.app.ui.theme.SplitCruiserSpacing
import com.splitcruiser.app.ui.theme.SplitCruiserSuccess
import com.splitcruiser.app.ui.theme.SplitCruiserSurface
import com.splitcruiser.app.ui.theme.SplitCruiserSurfaceCard
import com.splitcruiser.app.ui.theme.SplitCruiserSurfaceMuted
import com.splitcruiser.app.ui.theme.SplitCruiserSurfaceTrack
import com.splitcruiser.app.ui.theme.SplitCruiserTextPrimary
import com.splitcruiser.app.ui.theme.SplitCruiserTextSecondary
import com.splitcruiser.app.ui.theme.SplitCruiserTextSize
import com.splitcruiser.app.ui.theme.SplitCruiserWarning
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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
            color = SplitCruiserPrimary,
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
            .background(SplitCruiserOutline.copy(alpha = alpha))
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
    val loadingMessage by viewModel.loadingMessage.collectAsState()
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
        color = SplitCruiserSurface
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

            // Global loader. The message follows whatever action set `isLoading`; it used to be
            // hardcoded to "Securing your ride...", which is what a user saw while blocking
            // somebody, logging in, or submitting a rating.
            if (isLoading) {
                SplitCruiserLoadingState(isFullScreen = true, message = loadingMessage)
            }

            // Error Snackbar/Dialog Display
            uiError?.let { error ->
                AlertDialog(
                    onDismissRequest = { viewModel.clearError() },
                    confirmButton = {
                        TextButton(
                            onClick = { viewModel.clearError() },
                            colors = ButtonDefaults.textButtonColors(contentColor = SplitCruiserPrimary)
                        ) {
                            Text("Got it")
                        }
                    },
                    title = { Text("Information", color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold) },
                    text = { Text(error, color = SplitCruiserTextPrimary.copy(alpha = 0.85f)) },
                    containerColor = SplitCruiserSurfaceCard,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }
}

// --- Common UI Components ---

/** How prominent a [RouteIndicator] should be. */
enum class RouteScale {
    /** Inside a dense card — the join-success dialog. */
    Compact,

    /** The default for every feed and schedule card. */
    Card,

    /** The route header on a trip detail screen, with `PICKUP` / `DROPOFF` labels. */
    Detail,
}

/**
 * The origin → line → destination rail, plus the two place names beside it.
 *
 * Seven places used to draw this by hand — `TripOfferCard` (twice, once per breakpoint),
 * `HostedRideScheduleCard`, `JoinedRideScheduleCard`, `MyRideRequestCard`, `PastRideCard`,
 * `RideRequestCard`, `JoinSuccessDialog` and both halves of `TripDetailScreen` — each with its own
 * dot size, rail height and text size, none of which agreed.
 *
 * [pins] picks the marker style: filled dots (schedule cards) or start/place icons (feed and
 * detail screens). [muted] fades and shrinks it for the past-rides card.
 */
@Composable
fun RouteIndicator(
    origin: String,
    destination: String,
    modifier: Modifier = Modifier,
    scale: RouteScale = RouteScale.Card,
    pins: Boolean = false,
    muted: Boolean = false,
    originLabel: String? = null,
    destinationLabel: String? = null,
) {
    val markerSize = when {
        muted -> 6.dp
        scale == RouteScale.Compact -> 12.dp
        scale == RouteScale.Detail -> SplitCruiserSpacing.Lg
        else -> if (pins) 14.dp else SplitCruiserSpacing.Sm
    }
    val railWidth = if (muted || scale == RouteScale.Compact) 1.5.dp else 2.dp
    val railHeight = when {
        muted -> 18.dp
        scale == RouteScale.Compact -> 14.dp
        scale == RouteScale.Detail -> 40.dp
        else -> SplitCruiserSpacing.Xl
    }
    val gap = when {
        muted -> 6.dp
        scale == RouteScale.Compact -> 10.dp
        scale == RouteScale.Detail -> 22.dp
        else -> SplitCruiserSpacing.Md
    }
    val alpha = if (muted) 0.5f else 1f
    val textColor = if (muted) SplitCruiserTextPrimary.copy(alpha = 0.85f) else SplitCruiserTextPrimary
    val textSize = when {
        muted -> 13.sp
        scale == RouteScale.Compact -> SplitCruiserTextSize.Caption
        scale == RouteScale.Detail -> 15.sp
        else -> SplitCruiserTextSize.Body
    }
    val weight = if (muted || pins) FontWeight.SemiBold else FontWeight.Bold

    Row(
        modifier = modifier,
        verticalAlignment = if (pins) Alignment.CenterVertically else Alignment.Top
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(
                top = if (pins) 0.dp else if (muted) 3.dp else SplitCruiserSpacing.Xs,
                end = when {
                    muted -> 10.dp
                    scale == RouteScale.Detail -> SplitCruiserSpacing.Lg
                    else -> SplitCruiserSpacing.Md
                },
            )
        ) {
            if (pins) {
                Icon(
                    imageVector = Icons.Default.RadioButtonChecked,
                    contentDescription = null,
                    tint = SplitCruiserPrimary.copy(alpha = alpha),
                    modifier = Modifier.size(markerSize)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(markerSize)
                        .clip(CircleShape)
                        .background(SplitCruiserPrimaryContainer.copy(alpha = alpha))
                )
            }

            Box(
                modifier = Modifier
                    .width(railWidth)
                    .height(railHeight)
                    .background(SplitCruiserOutline.copy(alpha = alpha))
            )

            if (pins) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = SplitCruiserPrimary.copy(alpha = alpha),
                    modifier = Modifier.size(markerSize)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(markerSize)
                        .clip(CircleShape)
                        .background(SplitCruiserPrimary.copy(alpha = alpha))
                )
            }
        }

        Column {
            if (originLabel != null) {
                Text(
                    text = originLabel,
                    color = SplitCruiserTextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = origin,
                color = textColor,
                fontSize = textSize,
                fontWeight = weight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(gap))
            if (destinationLabel != null) {
                Text(
                    text = destinationLabel,
                    color = SplitCruiserTextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = destination,
                color = textColor,
                fontSize = textSize,
                fontWeight = weight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The colour a ride or request status is drawn in.
 *
 * One `when` rather than the five near-identical ones that used to be inlined in each card, so a
 * new status value cannot be handled by some cards and fall through to the `else` on others.
 */
fun statusColor(status: String): Color = when (status.lowercase(Locale.US)) {
    "active" -> SplitCruiserSuccess
    "full" -> SplitCruiserWarning
    "closed" -> SplitCruiserTextSecondary
    "completed" -> SplitCruiserInfo
    "matched" -> SplitCruiserInfo
    "cancelled", "declined" -> SplitCruiserDanger
    "pending" -> SplitCruiserWarning
    else -> SplitCruiserPrimary
}

/** The tinted status pill every ride card shows in its top-right corner. */
@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val color = statusColor(status)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(if (compact) 6.dp else SplitCruiserRadius.Sm))
            .background(color.copy(alpha = if (compact) 0.1f else 0.15f))
            .padding(
                horizontal = if (compact) 6.dp else SplitCruiserSpacing.Sm,
                vertical = if (compact) 2.dp else SplitCruiserSpacing.Xs,
            )
    ) {
        Text(
            text = status.uppercase(Locale.US),
            color = color,
            fontSize = if (compact) 9.sp else 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** The `ICON + ALL-CAPS LABEL` eyebrow every ride card leads with. */
@Composable
fun CardEyebrow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(if (compact) 14.dp else SplitCruiserSpacing.Lg)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = tint,
            fontSize = if (compact) 10.sp else SplitCruiserTextSize.Eyebrow,
            fontWeight = FontWeight.Bold,
            letterSpacing = if (compact) 0.8.sp else 1.sp
        )
    }
}

/**
 * Somebody the signed-in user has actually shared a ride with, and may therefore rate.
 *
 * The rating form used to ask for a raw Firebase uid in a text field. This is what replaces it:
 * the uid stays internal, and the user picks a name.
 */
data class RatingCompanion(
    val userId: String,
    val displayName: String,
    /** True when they were driving, so the chip can show a car rather than a person. */
    val wasHost: Boolean,
)

/**
 * A titled group of form fields, the Compose equivalent of SwiftUI's `Section` inside a `Form`.
 *
 * The post-offer and post-request screens were one continuous `Column`, so "where you're going"
 * and "when and how much" ran together, while the iOS versions of the same two screens got that
 * separation for free from `Form`/`Section`.
 */
@Composable
fun FormSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(Locale.US),
            color = SplitCruiserTextSecondary,
            fontSize = SplitCruiserTextSize.Eyebrow,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = SplitCruiserSpacing.Xs, bottom = SplitCruiserSpacing.Sm)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SplitCruiserRadius.Lg))
                .background(SplitCruiserSurfaceCard)
                .border(1.dp, SplitCruiserOutline, RoundedCornerShape(SplitCruiserRadius.Lg))
                .padding(SplitCruiserSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(SplitCruiserSpacing.Md),
            content = content,
        )
    }
}

/** The heading above each section of the Trips tab. */
@Composable
fun TripsSectionHeader(title: String, topSpacing: Dp = SplitCruiserSpacing.Xl) {
    Spacer(modifier = Modifier.height(topSpacing))
    Text(
        text = title,
        color = SplitCruiserTextPrimary,
        fontSize = SplitCruiserTextSize.Title,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(bottom = SplitCruiserSpacing.Sm)
    )
}

/** A `LABEL` / `value` pair, the unit every ride card's footer row is built from. */
@Composable
fun CardStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.Start,
    valueColor: Color = SplitCruiserTextPrimary,
) {
    Column(modifier = modifier, horizontalAlignment = alignment) {
        Text(
            text = label,
            color = SplitCruiserTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = SplitCruiserTextSize.Caption,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Backend connectivity, for whoever is developing the app — not for riders.
 *
 * It used to sit on the Profile screen at the same visual weight as the "Verified" trust badge,
 * next to the user's own name, in release builds. It renders nothing outside a debug build now.
 */
@Composable
fun FirebaseStatusPill(isFirebaseEnabled: Boolean) {
    if (!BuildConfig.DEBUG) return

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (isFirebaseEnabled) SplitCruiserSuccess.copy(alpha = 0.15f) else SplitCruiserPrimary.copy(alpha = 0.15f))
            .border(
                1.dp,
                if (isFirebaseEnabled) SplitCruiserSuccess.copy(alpha = 0.5f) else SplitCruiserPrimary.copy(alpha = 0.5f),
                RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isFirebaseEnabled) Icons.Default.CloudQueue else Icons.Default.CloudOff,
            contentDescription = "Status",
            tint = if (isFirebaseEnabled) SplitCruiserSuccess else SplitCruiserPrimary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (isFirebaseEnabled) "Firebase Live" else "Sandbox Mode",
            color = if (isFirebaseEnabled) SplitCruiserSuccess else SplitCruiserPrimary,
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
                colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
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
                        .border(1.5.dp, SplitCruiserPrimaryContainer, CircleShape),
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
                text = "US Desi Rideshare. Cost-split, trust-matched.",
                color = SplitCruiserTextSecondary,
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
                        focusedBorderColor = SplitCruiserPrimary,
                        unfocusedBorderColor = SplitCruiserOutline,
                        focusedLabelColor = SplitCruiserPrimary,
                        unfocusedLabelColor = SplitCruiserTextSecondary,
                        focusedTextColor = SplitCruiserTextPrimary,
                        unfocusedTextColor = SplitCruiserTextPrimary,
                        focusedContainerColor = SplitCruiserSurfaceCard,
                        unfocusedContainerColor = SplitCruiserSurfaceCard
                    ),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Email, contentDescription = "Email", tint = SplitCruiserTextSecondary)
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
                        focusedBorderColor = SplitCruiserPrimary,
                        unfocusedBorderColor = SplitCruiserOutline,
                        focusedLabelColor = SplitCruiserPrimary,
                        unfocusedLabelColor = SplitCruiserTextSecondary,
                        focusedTextColor = SplitCruiserTextPrimary,
                        unfocusedTextColor = SplitCruiserTextPrimary,
                        focusedContainerColor = SplitCruiserSurfaceCard,
                        unfocusedContainerColor = SplitCruiserSurfaceCard
                    ),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock", tint = SplitCruiserTextSecondary)
                    },
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        val description = if (passwordVisible) "Hide password" else "Show password"
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = description, tint = SplitCruiserTextSecondary)
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
                            focusedBorderColor = SplitCruiserPrimary,
                            unfocusedBorderColor = SplitCruiserOutline,
                            focusedLabelColor = SplitCruiserPrimary,
                            unfocusedLabelColor = SplitCruiserTextSecondary,
                            focusedTextColor = SplitCruiserTextPrimary,
                            unfocusedTextColor = SplitCruiserTextPrimary,
                            focusedContainerColor = SplitCruiserSurfaceCard,
                            unfocusedContainerColor = SplitCruiserSurfaceCard
                        ),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock", tint = SplitCruiserTextSecondary)
                        },
                        trailingIcon = {
                            val image = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            val description = if (confirmPasswordVisible) "Hide password" else "Show password"
                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                Icon(imageVector = image, contentDescription = description, tint = SplitCruiserTextSecondary)
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
                    colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserPrimary),
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
                        HorizontalDivider(modifier = Modifier.weight(1f), color = SplitCruiserOutline)
                        Text(
                            text = "or",
                            color = SplitCruiserTextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = SplitCruiserOutline)
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
                        border = BorderStroke(1.dp, SplitCruiserOutline),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = SplitCruiserSurfaceCard)
                    ) {
                        Text(
                            text = "G",
                            color = SplitCruiserPrimary,
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
                    colors = ButtonDefaults.textButtonColors(contentColor = SplitCruiserPrimary),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(text = if (isSignUpMode) "Already have an account? Log In" else "Don't have an account? Sign Up")
                }
            }
        }

        item {
            Text(
                text = "Split Cruiser connects verified riders safely. Cost-split, trust-matched.",
                color = SplitCruiserTextSecondary.copy(alpha = 0.5f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

// --- Screen 2: Profile Setup ---

@Composable
fun ProfileSetupScreen(viewModel: MainViewModel, navController: NavController) {
    var name by remember { mutableStateOf("") }
    var lastInitial by remember { mutableStateOf("") }
    var homeArea by remember { mutableStateOf("") }

    // Contact and home location. The address is picked from autocomplete so it carries
    // coordinates, which is what lets a ride request fill its own pickup in later.
    var phoneNumber by remember { mutableStateOf("") }
    var homeAddress by remember { mutableStateOf("") }
    var homeLat by remember { mutableStateOf(0.0) }
    var homeLng by remember { mutableStateOf(0.0) }

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
                    .border(2.dp, SplitCruiserPrimaryContainer, CircleShape),
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
                color = SplitCruiserTextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Profile Picture Upload Section
            Card(
                colors = CardDefaults.cardColors(containerColor = SplitCruiserPrimaryContainer.copy(alpha = 0.15f)),
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
                            color = SplitCruiserPrimary,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Uploading...", color = SplitCruiserPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    } else if (selectedAvatarUrl.isNotEmpty()) {
                        StudentAvatar(
                            avatarUrl = selectedAvatarUrl,
                            name = name.ifEmpty { "?" },
                            size = 80.dp,
                            fontSize = 32.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("✓ Image selected", color = SplitCruiserSuccess, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Add photo",
                            tint = SplitCruiserPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Choose a profile photo", color = SplitCruiserTextSecondary, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "(Optional - can be added later)",
                        color = SplitCruiserTextSecondary,
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
                    focusedBorderColor = SplitCruiserPrimary,
                    unfocusedBorderColor = SplitCruiserOutline,
                    focusedTextColor = SplitCruiserTextPrimary,
                    unfocusedTextColor = SplitCruiserTextPrimary,
                    focusedContainerColor = SplitCruiserSurfaceCard,
                    unfocusedContainerColor = SplitCruiserSurfaceCard
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
                    focusedBorderColor = SplitCruiserPrimary,
                    unfocusedBorderColor = SplitCruiserOutline,
                    focusedTextColor = SplitCruiserTextPrimary,
                    unfocusedTextColor = SplitCruiserTextPrimary,
                    focusedContainerColor = SplitCruiserSurfaceCard,
                    unfocusedContainerColor = SplitCruiserSurfaceCard
                ),
                shape = RoundedCornerShape(12.dp)
            )

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
                    focusedBorderColor = SplitCruiserPrimary,
                    unfocusedBorderColor = SplitCruiserOutline,
                    focusedTextColor = SplitCruiserTextPrimary,
                    unfocusedTextColor = SplitCruiserTextPrimary,
                    focusedContainerColor = SplitCruiserSurfaceCard,
                    unfocusedContainerColor = SplitCruiserSurfaceCard
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Contact number. This one does go on the public user document: the trip detail screen
            // has always shown a matched host's number.
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Contact Number") },
                placeholder = { Text("+1 617 555 0100") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("phone_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SplitCruiserPrimary,
                    unfocusedBorderColor = SplitCruiserOutline,
                    focusedTextColor = SplitCruiserTextPrimary,
                    unfocusedTextColor = SplitCruiserTextPrimary,
                    focusedContainerColor = SplitCruiserSurfaceCard,
                    unfocusedContainerColor = SplitCruiserSurfaceCard
                ),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = {
                    Icon(Icons.Default.Phone, contentDescription = "Phone", tint = SplitCruiserTextSecondary)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Home address, kept private to the account and used to prefill pickups.
            LocationAutoCompleteTextField(
                value = homeAddress,
                onValueChange = { homeAddress = it },
                onLocationSelected = { place ->
                    homeLat = place.lat
                    homeLng = place.lng
                },
                label = "Home Address",
                placeholder = "Where should pickups start from?",
                testTag = "home_address_input",
                leadingIcon = {
                    Icon(Icons.Default.Home, contentDescription = "Home", tint = SplitCruiserSuccess)
                }
            )

            Text(
                text = "Private to you. Ride requests start from here so you don't retype it.",
                color = SplitCruiserTextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Vehicle setup (Optional toggle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isHostExpanded = !isHostExpanded }
                    .background(SplitCruiserSurfaceCard, RoundedCornerShape(12.dp))
                    .border(1.dp, SplitCruiserOutline, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.DirectionsCar, contentDescription = "Car", tint = SplitCruiserPrimary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Are you offering rides?", color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Add your vehicle details now (Optional)", color = SplitCruiserTextSecondary, fontSize = 11.sp)
                    }
                }
                Icon(
                    imageVector = if (isHostExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand",
                    tint = SplitCruiserTextSecondary
                )
            }

            if (isHostExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SplitCruiserSurfaceCard, RoundedCornerShape(12.dp))
                        .border(1.dp, SplitCruiserOutline, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    OutlinedTextField(
                        value = vMake,
                        onValueChange = { vMake = it },
                        label = { Text("Car Make") },
                        placeholder = { Text("Toyota") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SplitCruiserPrimary,
                            unfocusedBorderColor = SplitCruiserOutline,
                            focusedTextColor = SplitCruiserTextPrimary,
                            unfocusedTextColor = SplitCruiserTextPrimary,
                            focusedContainerColor = SplitCruiserSurfaceCard,
                            unfocusedContainerColor = SplitCruiserSurfaceCard
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
                            focusedBorderColor = SplitCruiserPrimary,
                            unfocusedBorderColor = SplitCruiserOutline,
                            focusedTextColor = SplitCruiserTextPrimary,
                            unfocusedTextColor = SplitCruiserTextPrimary,
                            focusedContainerColor = SplitCruiserSurfaceCard,
                            unfocusedContainerColor = SplitCruiserSurfaceCard
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
                            focusedBorderColor = SplitCruiserPrimary,
                            unfocusedBorderColor = SplitCruiserOutline,
                            focusedTextColor = SplitCruiserTextPrimary,
                            unfocusedTextColor = SplitCruiserTextPrimary,
                            focusedContainerColor = SplitCruiserSurfaceCard,
                            unfocusedContainerColor = SplitCruiserSurfaceCard
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
                            focusedBorderColor = SplitCruiserPrimary,
                            unfocusedBorderColor = SplitCruiserOutline,
                            focusedTextColor = SplitCruiserTextPrimary,
                            unfocusedTextColor = SplitCruiserTextPrimary,
                            focusedContainerColor = SplitCruiserSurfaceCard,
                            unfocusedContainerColor = SplitCruiserSurfaceCard
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
                            focusedBorderColor = SplitCruiserPrimary,
                            unfocusedBorderColor = SplitCruiserOutline,
                            focusedTextColor = SplitCruiserTextPrimary,
                            unfocusedTextColor = SplitCruiserTextPrimary,
                            focusedContainerColor = SplitCruiserSurfaceCard,
                            unfocusedContainerColor = SplitCruiserSurfaceCard
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    // The button used to do nothing at all when a field was missing, which reads
                    // as a broken app rather than as a validation failure.
                    val missing = when {
                        name.isBlank() -> "Please enter your first name."
                        lastInitial.isBlank() -> "Please enter your last initial."
                        homeArea.isBlank() -> "Please enter your home area."
                        phoneNumber.isBlank() -> "Please enter a contact number so riders can reach you."
                        else -> null
                    }
                    if (missing != null) {
                        viewModel.setError(missing)
                    } else {
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
                            homeArea = homeArea,
                            contact = ContactDetails(
                                phoneNumber = phoneNumber,
                                homeAddress = homeAddress,
                                homeLat = homeLat,
                                homeLng = homeLng,
                            ),
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
                colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Finish setup", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// --- Screen 3: Main Dashboard ---

@Composable
fun DashboardScreen(viewModel: MainViewModel, navController: NavController) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf("explore") }
    val currentUser by viewModel.currentUser.collectAsState()
    val activeOffers by viewModel.activeOffers.collectAsState()
    val activeRequests by viewModel.activeRequests.collectAsState()
    val userMatches by viewModel.userMatches.collectAsState()
    val hostedRides by viewModel.hostedRides.collectAsState()
    val joinedRides by viewModel.joinedRides.collectAsState()
    val myRideRequests by viewModel.myRideRequests.collectAsState()
    val activeMode = viewModel.currentMode
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val currentUserId = currentUser?.id ?: ""

    // RideSchedule, not `status == "active"`: a ride whose last seat has gone is "full" and still
    // happening, but the old filter dropped it out of the schedule and into Past rides.
    val activeHosted = remember(hostedRides) {
        hostedRides.filter { RideSchedule.isCurrent(it.status) }
    }
    val activeJoined = remember(joinedRides) {
        joinedRides.filter { RideSchedule.isCurrent(it.status) }
    }
    val activeMyRequests = remember(myRideRequests) {
        myRideRequests.filter { it.status == "active" }
    }
    val pastRides = remember(hostedRides, joinedRides) {
        val hostedPast = hostedRides.filter { RideSchedule.isPast(it.status) }
        val joinedPast = joinedRides.filter { RideSchedule.isPast(it.status) }
        (hostedPast + joinedPast).distinctBy { it.id }.sortedByDescending { it.departureTime }
    }

    var showSuccessDialog by remember { mutableStateOf(false) }
    var selectedOfferForDialog by remember { mutableStateOf<TripOffer?>(null) }

    val dashboardSubtitle = currentUser?.homeArea?.takeIf { it.isNotBlank() } ?: "Find your next ride"

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(SplitCruiserSurface)
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
                                    .border(1.dp, SplitCruiserPrimaryContainer, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedTab == "trips") "My Travel Schedule" else "Namaste, ${currentUser?.name ?: "Rider"}",
                                color = SplitCruiserTextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black
                            )
                            if (currentUser?.verifiedTier == "vouched" && selectedTab != "trips") {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Vouched",
                                    tint = SplitCruiserPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = if (selectedTab == "trips") "Manage your hosted and joined rides" else dashboardSubtitle,
                            color = SplitCruiserTextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selectedTab == "trips") {
                            IconButton(
                                onClick = { viewModel.refreshMyTrips() },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(SplitCruiserPrimaryContainer.copy(alpha = 0.2f))
                                    .size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = SplitCruiserPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            // Rating indicator
                            if (currentUser != null && currentUser!!.ratingCount > 0) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SplitCruiserSurfaceMuted)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = "Rating", tint = SplitCruiserWarning, modifier = Modifier.size(14.dp))
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
                                    .background(SplitCruiserPrimaryContainer)
                                    .clickable { navController.navigate("profile") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.Person, contentDescription = "Profile", tint = SplitCruiserOnPrimaryContainer)
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
                            .background(SplitCruiserSurfaceTrack)
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
                                    text = if (mode == "Rider") "Find a ride" else "Give a ride",
                                    color = if (active) SplitCruiserTextPrimary else SplitCruiserTextSecondary,
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
            // Material 3's NavigationBar rather than a hand-built Row of clickable Columns. The
            // hand-built version looked the same but announced nothing to a screen reader: no
            // tab role, no selected state, no "1 of 4". The pill behind the selected icon is
            // reproduced with a custom `indicatorColor`.
            NavigationBar(
                containerColor = SplitCruiserSurfaceCard,
                contentColor = SplitCruiserTextSecondary,
                tonalElevation = 0.dp,
            ) {
                val itemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = SplitCruiserPrimary,
                    selectedTextColor = SplitCruiserPrimary,
                    unselectedIconColor = SplitCruiserTextSecondary,
                    unselectedTextColor = SplitCruiserTextSecondary,
                    indicatorColor = SplitCruiserPrimaryContainer,
                )

                NavigationBarItem(
                    selected = selectedTab == "explore",
                    onClick = { selectedTab = "explore" },
                    icon = { Icon(Icons.Default.DirectionsCar, contentDescription = null) },
                    label = { Text("Explore", fontSize = SplitCruiserTextSize.Eyebrow) },
                    colors = itemColors,
                )

                NavigationBarItem(
                    selected = selectedTab == "trips",
                    onClick = { selectedTab = "trips" },
                    icon = { Icon(Icons.Default.Map, contentDescription = null) },
                    label = { Text("My trips", fontSize = SplitCruiserTextSize.Eyebrow) },
                    colors = itemColors,
                )

                NavigationBarItem(
                    selected = false,
                    enabled = userMatches.isNotEmpty(),
                    onClick = {
                        userMatches.firstOrNull()?.let { navController.navigate("chat/${it.id}") }
                    },
                    icon = {
                        // The dot only shows when there is actually a conversation to open. It
                        // used to be drawn unconditionally, so a user with no matches saw an
                        // unread badge on a tab that did nothing when tapped.
                        BadgedBox(
                            badge = { if (userMatches.isNotEmpty()) Badge(containerColor = SplitCruiserDanger) }
                        ) {
                            Icon(Icons.Default.ChatBubbleOutline, contentDescription = null)
                        }
                    },
                    label = { Text("Chats", fontSize = SplitCruiserTextSize.Eyebrow) },
                    colors = itemColors,
                )

                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("profile") },
                    icon = { Icon(Icons.Default.PersonOutline, contentDescription = null) },
                    label = { Text("Profile", fontSize = SplitCruiserTextSize.Eyebrow) },
                    colors = itemColors,
                )
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
                    containerColor = SplitCruiserPrimary,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.Add, contentDescription = "Post") },
                    text = { Text(if (activeMode == "Rider") "Post Request" else "Post Offer", fontWeight = FontWeight.Bold) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("action_fab")
                )
            }
        },
        containerColor = SplitCruiserSurface
    ) { innerPadding ->
        // Pull-to-refresh on the lists themselves, matching iOS's `.refreshable`. The Trips tab's
        // toolbar refresh button stays: this app polls rather than streaming, so a manual sync
        // affordance is worth having twice.
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshFeeds() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
        if (selectedTab == "explore") {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = SplitCruiserSpacing.Lg)
            ) {
                // Hero Illustration Banner card
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
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
                                .background(SplitCruiserSurfaceCard)
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
                                        .background(if (match.status == "accepted") SplitCruiserSuccess.copy(alpha = 0.15f) else SplitCruiserPrimary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (match.status == "accepted") Icons.AutoMirrored.Filled.Chat else Icons.Default.HourglassEmpty,
                                        contentDescription = "Match",
                                        tint = if (match.status == "accepted") SplitCruiserSuccess else SplitCruiserPrimary
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
                                        color = SplitCruiserTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Open", tint = SplitCruiserTextSecondary)
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
                                description = "Be the first to post a Ride Request so hosts can find you!",
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
                                    // The request id is the repository's to generate: this used to
                                    // be the clock's last six digits, which repeat every 17 minutes.
                                    viewModel.requestSeat(offer.id, offer.costPerRider) {
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
                                description = "Post a trip offer or wait until someone nearby submits a ride request.",
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
            // A rider who has never hosted should not have to scroll past a "Rides you're
            // hosting" heading and its empty state to reach the rides they actually joined.
            // Sections appear once there is history of that kind; when *nothing* has happened
            // yet, one empty state stands in for all four.
            val hasHostedEver = hostedRides.isNotEmpty()
            val hasJoinedEver = joinedRides.isNotEmpty()
            val hasRequestedEver = myRideRequests.isNotEmpty()
            val hasAnyHistory = hasHostedEver || hasJoinedEver || hasRequestedEver

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = SplitCruiserSpacing.Lg)
            ) {
                if (!hasAnyHistory) {
                    item {
                        Spacer(modifier = Modifier.height(SplitCruiserSpacing.Xxl))
                        SplitCruiserEmptyState(
                            title = "Nothing on your schedule yet",
                            description = "Post a ride offer if you're driving, or a request if you need a seat. Both show up here.",
                            icon = Icons.Default.Map,
                            actionLabel = if (activeMode == "Rider") "Post a ride request" else "Post a ride offer",
                            onActionClick = {
                                navController.navigate(if (activeMode == "Rider") "post_request" else "post_offer")
                            },
                            illustrationType = "joined"
                        )
                    }
                }

                if (hasHostedEver) {
                    item {
                        TripsSectionHeader("Rides you're hosting", topSpacing = SplitCruiserSpacing.Lg)
                    }

                    if (activeHosted.isEmpty()) {
                        item {
                            SplitCruiserEmptyState(
                                title = "Nothing scheduled",
                                description = "You've hosted before — post another offer when you're next driving.",
                                icon = Icons.Default.DirectionsCar,
                                actionLabel = "Post a ride offer",
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
                                        Toast.makeText(context, "Ride status updated", Toast.LENGTH_SHORT).show()
                                        viewModel.refreshMyTrips()
                                    }
                                }
                            )
                        }
                    }
                }

                if (hasJoinedEver) {
                    item { TripsSectionHeader("Rides you've joined") }

                    if (activeJoined.isEmpty()) {
                        item {
                            SplitCruiserEmptyState(
                                title = "No upcoming seats",
                                description = "Nothing booked right now. Have a look at what's going your way.",
                                icon = Icons.Default.Map,
                                actionLabel = "Find a ride",
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
                }

                if (hasRequestedEver) {
                    item { TripsSectionHeader("Your ride requests") }

                    if (activeMyRequests.isEmpty()) {
                        item {
                            SplitCruiserEmptyState(
                                title = "No open requests",
                                description = "Post one and hosts heading your way will see it.",
                                icon = Icons.Default.DirectionsCar,
                                actionLabel = "Post a ride request",
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
                                        Toast.makeText(context, "Ride request cancelled", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }

                // Past rides only exist once something has finished, so there is never an empty
                // state to show here — the section is simply absent until there is history.
                if (pastRides.isNotEmpty()) {
                    item { TripsSectionHeader("Past rides") }

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
    var filterStatus by remember { mutableStateOf("all") } // all, active, closed, completed, cancelled

    val filteredRides = remember(hostedRides, filterStatus) {
        when (filterStatus) {
            "active" -> hostedRides.filter { RideSchedule.isCurrent(it.status) }
            "closed" -> hostedRides.filter { it.status == "closed" }
            "completed" -> hostedRides.filter { it.status == "completed" }
            "cancelled" -> hostedRides.filter { it.status == "cancelled" }
            else -> hostedRides
        }.sortedByDescending { it.departureTime }
    }

    val activeRides = remember(hostedRides) { hostedRides.filter { it.status == "active" } }
    val totalPassengers = remember(hostedRides) { hostedRides.sumOf { it.passengers.size } }
    // Named for what it is: what passengers have chipped in toward this host's costs. Calling it
    // "Revenue" here while the rest of the app calls the product a cost split told two stories.
    val totalContributions = remember(hostedRides) { hostedRides.sumOf { it.costPerRider * (it.totalSeats - it.seatsLeft) } }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(SplitCruiserSurface)
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
                            color = SplitCruiserTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SplitCruiserPrimaryContainer.copy(alpha = 0.2f))
                            .size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = SplitCruiserPrimary,
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
                        label = "Chipped in",
                        value = "$${String.format(Locale.US, "%.2f", totalContributions)}",
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
                    listOf("all" to "All Rides", "active" to "Active", "closed" to "Closed", "completed" to "Completed", "cancelled" to "Cancelled").forEach { (status, label) ->
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
        colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
        modifier = modifier
            .border(1.dp, SplitCruiserOutline, RoundedCornerShape(12.dp))
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
                tint = SplitCruiserPrimary,
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
                color = SplitCruiserTextSecondary,
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
        colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SplitCruiserOutline, RoundedCornerShape(12.dp))
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
                            .background(SplitCruiserPrimaryContainer),
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
                                tint = SplitCruiserWarning,
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
                        tint = SplitCruiserTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = SplitCruiserOutline, thickness = 1.dp)
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
                    border = BorderStroke(1.dp, SplitCruiserDanger),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SplitCruiserDanger)
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
        shape = RoundedCornerShape(SplitCruiserRadius.Lg),
        colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SplitCruiserSpacing.Sm)
            .clickable { onCardClick() }
            .border(1.dp, SplitCruiserOutline, RoundedCornerShape(SplitCruiserRadius.Lg))
    ) {
        Column(modifier = Modifier.padding(SplitCruiserSpacing.Lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardEyebrow("HOSTED RIDE", Icons.Default.DirectionsCar, SplitCruiserPrimary)
                StatusBadge(offer.status)
            }

            Spacer(modifier = Modifier.height(SplitCruiserSpacing.Md))

            RouteIndicator(origin = offer.origin, destination = offer.destination)

            Spacer(modifier = Modifier.height(SplitCruiserSpacing.Lg))
            HorizontalDivider(color = SplitCruiserOutline, thickness = 1.dp)
            Spacer(modifier = Modifier.height(SplitCruiserSpacing.Md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardStat("DEPARTURE", dateStr)
                CardStat(
                    label = "SEATS OCCUPIED",
                    value = "${offer.totalSeats - offer.seatsLeft} / ${offer.totalSeats}",
                    alignment = Alignment.End
                )
            }

            if (offer.passengerNames.isNotEmpty()) {
                Spacer(modifier = Modifier.height(SplitCruiserSpacing.Md))
                HorizontalDivider(color = SplitCruiserOutline, thickness = 1.dp)
                Spacer(modifier = Modifier.height(SplitCruiserSpacing.Md))

                Text(
                    text = "PASSENGERS",
                    color = SplitCruiserTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SplitCruiserSpacing.Sm)
                ) {
                    offer.passengerNames.forEach { name ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(SplitCruiserRadius.Md))
                                .background(SplitCruiserPrimaryContainer.copy(alpha = 0.2f))
                                .border(
                                    1.dp,
                                    SplitCruiserPrimaryContainer.copy(alpha = 0.4f),
                                    RoundedCornerShape(SplitCruiserRadius.Md)
                                )
                                .padding(horizontal = SplitCruiserSpacing.Sm, vertical = SplitCruiserSpacing.Xs)
                        ) {
                            Text(
                                text = name,
                                color = SplitCruiserTextPrimary,
                                fontSize = SplitCruiserTextSize.Eyebrow,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            val availability = HostControlsPolicy.availability(offer)
            if (availability.canComplete || availability.canCancel) {
                Spacer(modifier = Modifier.height(SplitCruiserSpacing.Lg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SplitCruiserSpacing.Sm)
                ) {
                    if (availability.canCancel) {
                        OutlinedButton(
                            onClick = { onStatusChange("cancelled") },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, SplitCruiserDanger),
                            shape = RoundedCornerShape(SplitCruiserRadius.Md),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SplitCruiserDanger)
                        ) {
                            Text("Cancel ride", fontSize = SplitCruiserTextSize.Caption, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (availability.canComplete) {
                        Button(
                            onClick = { onStatusChange("completed") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(SplitCruiserRadius.Md),
                            colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserSuccess)
                        ) {
                            Text(
                                "Complete ride",
                                color = SplitCruiserOnPrimary,
                                fontSize = SplitCruiserTextSize.Caption,
                                fontWeight = FontWeight.Bold
                            )
                        }
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
        shape = RoundedCornerShape(SplitCruiserRadius.Lg),
        colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SplitCruiserSpacing.Sm)
            .clickable { onCardClick() }
            .border(1.dp, SplitCruiserOutline, RoundedCornerShape(SplitCruiserRadius.Lg))
    ) {
        Column(modifier = Modifier.padding(SplitCruiserSpacing.Lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardEyebrow("JOINED RIDE", Icons.Default.DirectionsCar, SplitCruiserPrimary)
                StatusBadge(offer.status)
            }

            Spacer(modifier = Modifier.height(SplitCruiserSpacing.Md))

            RouteIndicator(origin = offer.origin, destination = offer.destination)

            Spacer(modifier = Modifier.height(SplitCruiserSpacing.Lg))
            HorizontalDivider(color = SplitCruiserOutline, thickness = 1.dp)
            Spacer(modifier = Modifier.height(SplitCruiserSpacing.Md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardStat("HOST", offer.hostName)
                CardStat("DEPARTURE", dateStr, alignment = Alignment.CenterHorizontally)
                CardStat(
                    label = "CONTRIBUTION",
                    value = "$${offer.costPerRider}",
                    alignment = Alignment.End,
                    valueColor = SplitCruiserPrimary
                )
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
        shape = RoundedCornerShape(SplitCruiserRadius.Lg),
        colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onCardClick() }
            .border(1.dp, SplitCruiserOutline.copy(alpha = 0.5f), RoundedCornerShape(SplitCruiserRadius.Lg))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardEyebrow(
                    label = if (isHost) "PAST HOSTED" else "PAST JOINED",
                    icon = if (isHost) Icons.Default.DirectionsCar else Icons.Default.History,
                    tint = SplitCruiserTextSecondary,
                    compact = true
                )
                StatusBadge(offer.status, compact = true)
            }

            Spacer(modifier = Modifier.height(10.dp))

            RouteIndicator(origin = offer.origin, destination = offer.destination, muted = true)

            Spacer(modifier = Modifier.height(SplitCruiserSpacing.Md))
            HorizontalDivider(color = SplitCruiserOutline.copy(alpha = 0.3f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(SplitCruiserSpacing.Sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardStat("DATE & TIME", dateStr, valueColor = SplitCruiserTextPrimary.copy(alpha = 0.7f))
                CardStat(
                    label = "YOUR ROLE",
                    value = if (isHost) "Driver" else "Passenger (with ${offer.hostName})",
                    alignment = Alignment.End,
                    valueColor = SplitCruiserPrimary.copy(alpha = 0.8f)
                )
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
        shape = RoundedCornerShape(SplitCruiserRadius.Lg),
        colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SplitCruiserSpacing.Sm)
            .border(1.dp, SplitCruiserOutline, RoundedCornerShape(SplitCruiserRadius.Lg))
    ) {
        Column(modifier = Modifier.padding(SplitCruiserSpacing.Lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardEyebrow("MY RIDE REQUEST", Icons.Default.DirectionsCar, SplitCruiserSuccess)
                StatusBadge(request.status)
            }

            Spacer(modifier = Modifier.height(SplitCruiserSpacing.Md))

            RouteIndicator(origin = request.origin, destination = request.destination)

            Spacer(modifier = Modifier.height(SplitCruiserSpacing.Lg))
            HorizontalDivider(color = SplitCruiserOutline, thickness = 1.dp)
            Spacer(modifier = Modifier.height(SplitCruiserSpacing.Md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardStat("PREFERRED DEPARTURE", dateStr)
                CardStat("SEATS NEEDED", "${request.seatsNeeded}", alignment = Alignment.End)
            }

            if (request.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(SplitCruiserSpacing.Md))
                HorizontalDivider(color = SplitCruiserOutline, thickness = 1.dp)
                Spacer(modifier = Modifier.height(SplitCruiserSpacing.Md))
                Text(
                    text = "NOTES",
                    color = SplitCruiserTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = request.notes,
                    color = SplitCruiserTextPrimary,
                    fontSize = SplitCruiserTextSize.Caption
                )
            }

            if (request.status == "active") {
                Spacer(modifier = Modifier.height(SplitCruiserSpacing.Lg))
                OutlinedButton(
                    onClick = onCancelClick,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, SplitCruiserDanger),
                    shape = RoundedCornerShape(SplitCruiserRadius.Md),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SplitCruiserDanger)
                ) {
                    Text("Cancel request", fontSize = SplitCruiserTextSize.Caption, fontWeight = FontWeight.Bold)
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
    // One backdrop for every empty state, not four hand-drawn Canvas illustrations running three
    // concurrent infinite animations each. `illustrationType` now only picks the accent colour,
    // so existing call sites keep working and nothing spins forever behind a list nobody is
    // looking at. A single slow pulse is enough to stop the screen feeling dead.
    val infiniteTransition = rememberInfiniteTransition(label = "split_cruiser_empty_state_anim")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val accent = when (illustrationType) {
        "hosted" -> SplitCruiserPrimary
        "joined" -> SplitCruiserSuccess
        "past" -> SplitCruiserTextSecondary
        else -> SplitCruiserPrimary
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = SplitCruiserSpacing.Xxl, horizontal = SplitCruiserSpacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(140.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centre = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.18f), Color.Transparent),
                        center = centre,
                        radius = size.minDimension * 0.5f
                    )
                )
                drawCircle(
                    color = accent.copy(alpha = 0.25f),
                    radius = (size.minDimension * 0.34f) * pulse,
                    center = centre,
                    style = Stroke(width = 2f)
                )
            }

            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(SplitCruiserSurfaceCard)
                    .border(2.dp, accent, CircleShape)
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(SplitCruiserSpacing.Lg))

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
            modifier = Modifier.padding(horizontal = SplitCruiserSpacing.Xl)
        )

        if (actionLabel != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(SplitCruiserSpacing.Xl))
            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SplitCruiserPrimary,
                    contentColor = SplitCruiserOnPrimary
                ),
                shape = RoundedCornerShape(SplitCruiserRadius.Md),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text(
                    text = actionLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = SplitCruiserTextSize.Body
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
                colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SplitCruiserOutline, RoundedCornerShape(16.dp))
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
                                    .background(SplitCruiserTextSecondary.copy(alpha = 0.3f))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SplitCruiserTextSecondary.copy(alpha = 0.3f))
                                )
                                Box(
                                    modifier = Modifier
                                        .width(60.dp)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SplitCruiserTextSecondary.copy(alpha = 0.3f))
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .width(50.dp)
                                .height(20.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SplitCruiserTextSecondary.copy(alpha = 0.3f))
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
                                    .background(SplitCruiserTextSecondary.copy(alpha = 0.3f))
                            )
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(24.dp)
                                    .background(SplitCruiserTextSecondary.copy(alpha = 0.2f))
                            )
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(SplitCruiserTextSecondary.copy(alpha = 0.3f))
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
                                    .background(SplitCruiserTextSecondary.copy(alpha = 0.3f))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SplitCruiserTextSecondary.copy(alpha = 0.3f))
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
                                .background(SplitCruiserTextSecondary.copy(alpha = 0.3f))
                        )
                        Box(
                            modifier = Modifier
                                .width(70.dp)
                                .height(28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SplitCruiserTextSecondary.copy(alpha = 0.3f))
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
            colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier
                .padding(24.dp)
                .border(1.dp, SplitCruiserOutline, RoundedCornerShape(20.dp))
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
                        color = SplitCruiserPrimary,
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
        colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .border(1.dp, SplitCruiserOutline, RoundedCornerShape(16.dp))
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
                                    .background(SplitCruiserPrimaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = offer.hostName.take(1).uppercase(),
                                    color = SplitCruiserOnPrimaryContainer,
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
                                        tint = SplitCruiserWarning,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = String.format(Locale.US, "%.1f", offer.hostRating),
                                        color = SplitCruiserTextSecondary,
                                        fontSize = 11.sp
                                    )
                                    if (offer.vehicleInfo.isNotEmpty()) {
                                        Text(
                                            text = " • ${offer.vehicleInfo}",
                                            color = SplitCruiserTextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        RouteIndicator(origin = offer.origin, destination = offer.destination, pins = true)

                        // Trip meta info: Time & Seats
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccessTime, contentDescription = "Time", tint = SplitCruiserTextSecondary, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(dateStr, color = SplitCruiserTextSecondary, fontSize = 11.sp)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.EventSeat, contentDescription = "Seats", tint = SplitCruiserSuccess, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${offer.seatsLeft} of ${offer.totalSeats} seats open", color = SplitCruiserSuccess, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                            Text("$${offer.costPerRider}", color = SplitCruiserPrimary, fontWeight = FontWeight.Black, fontSize = 22.sp)
                            Text("per rider", color = SplitCruiserTextSecondary, fontSize = 10.sp)
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
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SplitCruiserPrimary),
                                border = BorderStroke(1.dp, SplitCruiserPrimary.copy(alpha = 0.5f)),
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
                                    colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserPrimary),
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
                                    colors = CardDefaults.cardColors(containerColor = SplitCruiserSuccess.copy(alpha = 0.12f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .border(1.dp, SplitCruiserSuccess.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "Joined", tint = SplitCruiserSuccess, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Joined", color = SplitCruiserSuccess, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                    }
                                }
                            } else if (isHost) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = SplitCruiserPrimaryContainer.copy(alpha = 0.12f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .border(1.dp, SplitCruiserPrimaryContainer.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.DirectionsCar, contentDescription = "Your Trip", tint = SplitCruiserPrimary, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Your Trip", color = SplitCruiserPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
                                    .background(SplitCruiserPrimaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = offer.hostName.take(1).uppercase(),
                                    color = SplitCruiserOnPrimaryContainer,
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
                                    Icon(Icons.Default.Star, contentDescription = "Rating", tint = SplitCruiserWarning, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = String.format(Locale.US, "%.1f", offer.hostRating),
                                        color = SplitCruiserTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("$${offer.costPerRider}", color = SplitCruiserPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            Text("per rider", color = SplitCruiserTextSecondary, fontSize = 9.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    RouteIndicator(origin = offer.origin, destination = offer.destination, pins = true)

                    Spacer(modifier = Modifier.height(12.dp))

                    // Footer: Departure Date & Open Seats Left
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, contentDescription = "Time", tint = SplitCruiserTextSecondary, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(dateStr, color = SplitCruiserTextSecondary, fontSize = 11.sp)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EventSeat, contentDescription = "Seats", tint = SplitCruiserSuccess, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${offer.seatsLeft} of ${offer.totalSeats} seats open", color = SplitCruiserSuccess, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Bottom CTAs
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = SplitCruiserOutline)
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
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SplitCruiserPrimary),
                            border = BorderStroke(1.dp, SplitCruiserPrimary.copy(alpha = 0.5f)),
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
                                colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserPrimary),
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
                                colors = CardDefaults.cardColors(containerColor = SplitCruiserSuccess.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .border(1.dp, SplitCruiserSuccess.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Joined", tint = SplitCruiserSuccess, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Joined", color = SplitCruiserSuccess, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        } else if (isHost) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SplitCruiserPrimaryContainer.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .border(1.dp, SplitCruiserPrimaryContainer.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.DirectionsCar, contentDescription = "Your Trip", tint = SplitCruiserPrimary, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Your Trip", color = SplitCruiserPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
            placeholder = { Text("Search by origin, destination or host...", color = SplitCruiserTextSecondary, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = SplitCruiserTextSecondary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SplitCruiserTextSecondary)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag("trip_list_search_input"),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SplitCruiserPrimary,
                unfocusedBorderColor = SplitCruiserOutline,
                focusedContainerColor = SplitCruiserSurfaceCard,
                unfocusedContainerColor = SplitCruiserSurfaceCard,
                focusedTextColor = SplitCruiserTextPrimary,
                unfocusedTextColor = SplitCruiserTextPrimary
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
                        .background(if (isSelected) SplitCruiserPrimary else SplitCruiserSurfaceCard)
                        .border(1.dp, if (isSelected) Color.Transparent else SplitCruiserOutline, RoundedCornerShape(20.dp))
                        .clickable { selectedFilter = filter }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("filter_chip_$filter")
                ) {
                    Text(
                        text = filter,
                        color = if (isSelected) Color.White else SplitCruiserTextSecondary,
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
                    color = if (isSelected) SplitCruiserSuccess.copy(alpha = 0.25f) else SplitCruiserSurfaceCard,
                    border = BorderStroke(1.dp, if (isSelected) SplitCruiserSuccess else SplitCruiserOutline)
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
                            tint = if (isSelected) SplitCruiserSuccess else SplitCruiserPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = placeTag,
                            color = if (isSelected) SplitCruiserSuccess else SplitCruiserTextPrimary,
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
            color = SplitCruiserSurfaceCard,
            border = BorderStroke(1.dp, SplitCruiserOutline),
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
                        .background(SplitCruiserPrimaryContainer.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(SplitCruiserPrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = SplitCruiserPrimary,
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
                    color = SplitCruiserTextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Trip Card details in dialog
                Card(
                    colors = CardDefaults.cardColors(containerColor = SplitCruiserSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SplitCruiserOutline, RoundedCornerShape(16.dp))
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
                                        .background(SplitCruiserPrimaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = offer.hostName.take(1).uppercase(),
                                        color = SplitCruiserOnPrimaryContainer,
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
                                color = SplitCruiserPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                        }

                        HorizontalDivider(color = SplitCruiserOutline)

                        RouteIndicator(
                            origin = offer.origin,
                            destination = offer.destination,
                            scale = RouteScale.Compact,
                            pins = true
                        )

                        HorizontalDivider(color = SplitCruiserOutline)

                        // Time
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, contentDescription = "Time", tint = SplitCruiserTextSecondary, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(dateStr, color = SplitCruiserTextSecondary, fontSize = 11.sp)
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
                    colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserPrimary),
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
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SplitCruiserPrimary),
                    border = BorderStroke(1.dp, SplitCruiserPrimary.copy(alpha = 0.5f))
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
        colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .border(1.dp, SplitCruiserOutline, RoundedCornerShape(16.dp))
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
                            .background(SplitCruiserPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = request.riderName.take(1).uppercase(),
                            color = SplitCruiserPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(request.riderName, color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = "Rating", tint = SplitCruiserWarning, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = String.format(Locale.US, "%.1f", request.riderRating),
                                color = SplitCruiserTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Seats needed tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SplitCruiserPrimary.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${request.seatsNeeded} Seat${if (request.seatsNeeded > 1) "s" else ""}",
                        color = SplitCruiserPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            RouteIndicator(origin = request.origin, destination = request.destination, pins = true)

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessTime, contentDescription = "Time", tint = SplitCruiserTextSecondary, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(dateStr, color = SplitCruiserTextSecondary, fontSize = 11.sp)
                }

                if (request.womenOnly) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SplitCruiserAccent.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Female, contentDescription = "Female Only", tint = SplitCruiserAccent, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Women Only", color = SplitCruiserAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// --- Screen 4: Post Ride Offer (Host) ---

@Composable
fun PostOfferScreen(viewModel: MainViewModel, navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Prefilled from onboarding, like the rider screen: a host's ride usually starts from home.
    val home by viewModel.contactDetails.collectAsState()
    var origin by remember(home) { mutableStateOf(home?.homeAddress.orEmpty()) }
    var destination by remember { mutableStateOf("") }
    var exitLocation by remember { mutableStateOf("") }
    var originLat by remember(home) { mutableStateOf(home?.homeLat?.takeIf { it != 0.0 } ?: 42.34) }
    var originLng by remember(home) { mutableStateOf(home?.homeLng?.takeIf { it != 0.0 } ?: -71.10) }
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

            FormSection(title = "Route") {
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
                    focusedBorderColor = SplitCruiserSuccess,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Pickup location icon",
                            tint = SplitCruiserSuccess
                        )
                    },
                    biasLat = home?.homeLat?.takeIf { it != 0.0 },
                    biasLng = home?.homeLng?.takeIf { it != 0.0 }
                )

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
                    },
                    biasLat = originLat,
                    biasLng = originLng
                )

                OutlinedTextField(
                    value = exitLocation,
                    onValueChange = { exitLocation = it },
                    label = { Text("Exact Meeting Spot (optional)") },
                    placeholder = { Text("e.g. North Gate, by the flagpole") },
                    modifier = Modifier.fillMaxWidth().testTag("offer_exit_location_input"),
                    shape = RoundedCornerShape(SplitCruiserRadius.Md),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.PinDrop,
                            contentDescription = "Exact meeting spot icon",
                            tint = SplitCruiserTextSecondary
                        )
                    }
                )

                GoogleMapsMatrixCard(origin = origin, destination = destination)
            }

            Spacer(modifier = Modifier.height(SplitCruiserSpacing.Lg))

            FormSection(title = "Trip") {
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
                        shape = RoundedCornerShape(SplitCruiserRadius.Md),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "Departure date icon",
                                tint = SplitCruiserPrimary
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledBorderColor = SplitCruiserOutline,
                            disabledTextColor = SplitCruiserTextPrimary,
                            disabledLabelColor = SplitCruiserTextSecondary,
                            disabledContainerColor = SplitCruiserSurfaceCard
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
                        shape = RoundedCornerShape(SplitCruiserRadius.Md),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = "Departure time icon",
                                tint = SplitCruiserPrimary
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledBorderColor = SplitCruiserOutline,
                            disabledTextColor = SplitCruiserTextPrimary,
                            disabledLabelColor = SplitCruiserTextSecondary,
                            disabledContainerColor = SplitCruiserSurfaceCard
                        )
                    )
                }
            }

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
                    shape = RoundedCornerShape(SplitCruiserRadius.Md),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AttachMoney,
                            contentDescription = "Cost icon",
                            tint = SplitCruiserWarning
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SplitCruiserWarning,
                        unfocusedBorderColor = SplitCruiserOutline,
                        focusedTextColor = SplitCruiserTextPrimary,
                        unfocusedTextColor = SplitCruiserTextPrimary,
                        focusedLabelColor = SplitCruiserWarning,
                        unfocusedLabelColor = SplitCruiserTextSecondary,
                        focusedContainerColor = SplitCruiserSurfaceCard,
                        unfocusedContainerColor = SplitCruiserSurfaceCard
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
                    shape = RoundedCornerShape(SplitCruiserRadius.Md),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = "Seats icon",
                            tint = Color(0xFF8B5CF6)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF8B5CF6),
                        unfocusedBorderColor = SplitCruiserOutline,
                        focusedTextColor = SplitCruiserTextPrimary,
                        unfocusedTextColor = SplitCruiserTextPrimary,
                        focusedLabelColor = Color(0xFF8B5CF6),
                        unfocusedLabelColor = SplitCruiserTextSecondary,
                        focusedContainerColor = SplitCruiserSurfaceCard,
                        unfocusedContainerColor = SplitCruiserSurfaceCard
                    )
                )
            }

            // Women Only Offer
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Female, contentDescription = "Women Only", tint = SplitCruiserAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Women-Only Trip Offer", color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Only visible to other female riders", color = SplitCruiserTextSecondary, fontSize = 10.sp)
                    }
                }
                Switch(
                    checked = womenOnly,
                    onCheckedChange = { womenOnly = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = SplitCruiserAccent)
                )
            }
            }

            Spacer(modifier = Modifier.height(SplitCruiserSpacing.Lg))

            // Vehicle Check
            if (userVehicle == null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SplitCruiserPrimary.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = "No vehicle", tint = SplitCruiserPrimary)
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
                            "Shared Sedan"
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
                            vehicleInfo = vehicleLabel,
                            exitLocation = exitLocation
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
                colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Post ride offer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

// --- Screen 5: Post Ride Request (Rider) ---

@Composable
fun PostRequestScreen(viewModel: MainViewModel, navController: NavController) {
    // What the home address in onboarding is for: the rider should not retype where they live.
    val home by viewModel.contactDetails.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var origin by remember(home) { mutableStateOf(home?.homeAddress.orEmpty()) }
    var destination by remember { mutableStateOf("") }
    var exitLocation by remember { mutableStateOf("") }
    var originLat by remember(home) { mutableStateOf(home?.homeLat?.takeIf { it != 0.0 } ?: 42.33) }
    var originLng by remember(home) { mutableStateOf(home?.homeLng?.takeIf { it != 0.0 } ?: -71.08) }
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

            FormSection(title = "Route") {
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
                    focusedBorderColor = SplitCruiserSuccess,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Pickup location icon",
                            tint = SplitCruiserSuccess
                        )
                    },
                    biasLat = home?.homeLat?.takeIf { it != 0.0 },
                    biasLng = home?.homeLng?.takeIf { it != 0.0 }
                )

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
                    },
                    biasLat = originLat,
                    biasLng = originLng
                )

                OutlinedTextField(
                    value = exitLocation,
                    onValueChange = { exitLocation = it },
                    label = { Text("Exact Meeting Spot (optional)") },
                    placeholder = { Text("e.g. North Gate, by the flagpole") },
                    modifier = Modifier.fillMaxWidth().testTag("request_exit_location_input"),
                    shape = RoundedCornerShape(SplitCruiserRadius.Md),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.PinDrop,
                            contentDescription = "Exact meeting spot icon",
                            tint = SplitCruiserTextSecondary
                        )
                    }
                )

                GoogleMapsMatrixCard(origin = origin, destination = destination)
            }

            Spacer(modifier = Modifier.height(SplitCruiserSpacing.Lg))

            FormSection(title = "Trip") {
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
                    shape = RoundedCornerShape(SplitCruiserRadius.Md),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Departure time icon",
                            tint = SplitCruiserPrimary
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledBorderColor = SplitCruiserOutline,
                        disabledTextColor = SplitCruiserTextPrimary,
                        disabledLabelColor = SplitCruiserTextSecondary,
                        disabledContainerColor = SplitCruiserSurfaceCard
                    )
                )
            }

            // Seats needed
            OutlinedTextField(
                value = seatsNeeded,
                onValueChange = { seatsNeeded = it },
                label = { Text("Seats needed") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(SplitCruiserRadius.Md),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = "Seats icon",
                        tint = Color(0xFF8B5CF6)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF8B5CF6),
                    unfocusedBorderColor = SplitCruiserOutline,
                    focusedTextColor = SplitCruiserTextPrimary,
                    unfocusedTextColor = SplitCruiserTextPrimary,
                    focusedLabelColor = Color(0xFF8B5CF6),
                    unfocusedLabelColor = SplitCruiserTextSecondary,
                    focusedContainerColor = SplitCruiserSurfaceCard,
                    unfocusedContainerColor = SplitCruiserSurfaceCard
                )
            )

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes for host (Luggage details, etc.)") },
                placeholder = { Text("e.g. 1 big suitcase. Can pay via Venmo/cash.") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(SplitCruiserRadius.Md),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Notes icon",
                        tint = Color(0xFF14B8A6)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF14B8A6),
                    unfocusedBorderColor = SplitCruiserOutline,
                    focusedTextColor = SplitCruiserTextPrimary,
                    unfocusedTextColor = SplitCruiserTextPrimary,
                    focusedLabelColor = Color(0xFF14B8A6),
                    unfocusedLabelColor = SplitCruiserTextSecondary,
                    focusedContainerColor = SplitCruiserSurfaceCard,
                    unfocusedContainerColor = SplitCruiserSurfaceCard
                )
            )

            // Women Only
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Female, contentDescription = "Women Only", tint = SplitCruiserAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Women-Only Request", color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Only visible to other female hosts", color = SplitCruiserTextSecondary, fontSize = 10.sp)
                    }
                }
                Switch(
                    checked = womenOnly,
                    onCheckedChange = { womenOnly = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = SplitCruiserAccent)
                )
            }
            }

            Spacer(modifier = Modifier.height(SplitCruiserSpacing.Xl))

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
                            womenOnly = womenOnly,
                            exitLocation = exitLocation
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
                colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Post ride request", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

// --- Screen 6: Ride Detail Screen (Join / Accept / Decline matches) ---

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
        // `activeOffers` is deliberately filtered to exclude the viewer's own rides and anything
        // past/completed — exactly the offers a host or a returning rider need to open. The cache
        // accessor below reads the same underlying store without that filter; the network fetch is
        // only needed for a cold cache (e.g. a deep link before the first feed poll completes).
        var fetchedOffer by remember(id) { mutableStateOf<TripOffer?>(null) }
        var offerFetchAttempted by remember(id) { mutableStateOf(false) }
        val cachedOffer = offers.find { it.id == id } ?: viewModel.getTripOfferById(id)
        val offer = cachedOffer ?: fetchedOffer

        LaunchedEffect(id, cachedOffer) {
            if (cachedOffer == null && !offerFetchAttempted) {
                viewModel.fetchTripOffer(id) { fetched ->
                    fetchedOffer = fetched
                    offerFetchAttempted = true
                }
            }
        }

        if (offer == null) {
            if (!offerFetchAttempted) {
                Box(
                    modifier = Modifier.fillMaxSize().background(SplitCruiserSurface),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SplitCruiserPrimary)
                }
                return
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SplitCruiserSurface),
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
                    colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SplitCruiserOutline, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        RouteIndicator(
                            origin = offer.origin,
                            destination = offer.destination,
                            scale = RouteScale.Detail,
                            pins = true,
                            originLabel = "PICKUP",
                            destinationLabel = "DROPOFF"
                        )
                        if (offer.exitLocation.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PinDrop,
                                    contentDescription = "Exact meeting spot icon",
                                    tint = SplitCruiserTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = offer.exitLocation,
                                    color = SplitCruiserTextSecondary,
                                    fontSize = SplitCruiserTextSize.Caption
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                GoogleMapsMatrixCard(origin = offer.origin, destination = offer.destination)

                Spacer(modifier = Modifier.height(16.dp))

                // Status and Seat info Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SplitCruiserOutline, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("STATUS & SEATS", color = SplitCruiserPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            
                            // Badge with status color
                            val badgeBg = when (offer.status.lowercase()) {
                                "active" -> SplitCruiserPrimaryContainer
                                "full" -> Color(0xFFFEF3C7) // Amber
                                "completed" -> SplitCruiserSuccess.copy(alpha = 0.2f)
                                "cancelled" -> Color(0xFFFEE2E2) // Light red
                                else -> SplitCruiserPrimaryContainer
                            }
                            val badgeText = when (offer.status.lowercase()) {
                                "active" -> SplitCruiserPrimary
                                "full" -> Color(0xFFD97706)
                                "completed" -> SplitCruiserSuccess
                                "cancelled" -> Color(0xFFDC2626)
                                else -> SplitCruiserPrimary
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
                    colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isHostCardExpanded = !isHostCardExpanded }
                        .border(1.dp, SplitCruiserOutline, RoundedCornerShape(16.dp))
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
                                Text("Host Rating: ${String.format(Locale.US, "%.1f", offer.hostRating)} ★", color = SplitCruiserTextSecondary, fontSize = 12.sp)
                            }
                            IconButton(onClick = {
                                isHostCardExpanded = !isHostCardExpanded
                            }) {
                                Icon(
                                    imageVector = if (isHostCardExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Expand info",
                                    tint = SplitCruiserPrimary
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
                                HorizontalDivider(color = SplitCruiserOutline)
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Text(
                                    text = "VEHICLE & CONTACT OVERVIEW",
                                    color = SplitCruiserPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.DirectionsCar, contentDescription = "Vehicle", tint = SplitCruiserTextSecondary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Vehicle: $vehicleMakeModel", color = SplitCruiserTextPrimary, fontSize = 13.sp)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Phone, contentDescription = "Phone", tint = SplitCruiserTextSecondary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Phone: $hostPhone", color = SplitCruiserTextPrimary, fontSize = 13.sp)
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { showDriverModal = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserPrimaryContainer),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(36.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = "Contact", tint = SplitCruiserPrimary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("View Full Driver & Contact Card", color = SplitCruiserPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Cost split calculations
                Card(
                    colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SplitCruiserOutline, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("COST ALLOCATION", color = SplitCruiserPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Suggested Gas Contribution:", color = SplitCruiserTextPrimary, fontSize = 13.sp)
                            Text("$${offer.costPerRider}", color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Server Max Limit (2x Cost Cap):", color = SplitCruiserTextSecondary, fontSize = 12.sp)
                            Text("$${costLimit}", color = SplitCruiserTextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Divider(color = SplitCruiserOutline)
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = SplitCruiserSuccess, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Cash split is paid in-person directly to the host. No commission or app fees.",
                                color = SplitCruiserTextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (offer.passengers.isNotEmpty()) {
                    Text("RESERVED PASSENGERS", color = SplitCruiserPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SplitCruiserOutline, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            offer.passengerNames.zip(offer.passengers).forEachIndexed { index, (name, id) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = "Passenger", tint = SplitCruiserTextSecondary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (id == currentUser?.id) "$name (You)" else name,
                                        color = SplitCruiserTextPrimary,
                                        fontWeight = if (id == currentUser?.id) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp
                                    )
                                }
                                if (index < offer.passengerNames.size - 1) {
                                    HorizontalDivider(color = SplitCruiserOutline, modifier = Modifier.padding(vertical = 4.dp))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Message history section (if match exists)
                if (existingMatch != null && matchMessages.isNotEmpty()) {
                    Text("RECENT COORDINATION", color = SplitCruiserPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SplitCruiserOutline, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            matchMessages.forEachIndexed { index, message ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(
                                        imageVector = if (message.senderId == currentUser?.id) Icons.AutoMirrored.Filled.Send else Icons.AutoMirrored.Filled.Chat,
                                        contentDescription = "Message",
                                        tint = SplitCruiserTextSecondary,
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
                                            color = SplitCruiserTextSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                if (index < matchMessages.size - 1) {
                                    HorizontalDivider(color = SplitCruiserOutline, modifier = Modifier.padding(vertical = 8.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { navController.navigate("chat/${existingMatch.id}") },
                                colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserPrimaryContainer),
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                contentPadding = PaddingValues(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat", tint = SplitCruiserPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("View Full Chat", color = SplitCruiserPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                val isHost = (offer.hostId == currentUser?.id)

                if (isHost) {
                    // Seats and departure time now drive "active"/"full"/"closed" automatically —
                    // the host is left with exactly two real decisions. HostControlsPolicy is the
                    // single source of truth for when they still apply, shared with iOS.
                    val availability = HostControlsPolicy.availability(offer)
                    if (availability.canComplete || availability.canCancel) {
                        Text("HOST CONTROLS", color = SplitCruiserPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, SplitCruiserOutline, RoundedCornerShape(16.dp))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = if (offer.status == "closed") {
                                        "This ride's departure time has passed. Did it happen?"
                                    } else {
                                        "Manage this ride:"
                                    },
                                    color = SplitCruiserTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (availability.canComplete) {
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
                                            colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserSuccess),
                                            modifier = Modifier.weight(1f).testTag("host_status_completed_btn").withButtonScale(completeScale)
                                        ) {
                                            Text("Complete", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (availability.canCancel) {
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
                    }
                } else {
                    // Current user is a potential rider/passenger
                    val hasAlreadyJoined = offer.passengers.contains(currentUser?.id)
                    
                    if (hasAlreadyJoined) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SplitCruiserSuccess.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Reserved", tint = SplitCruiserSuccess, modifier = Modifier.size(32.dp))
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
                                if (existingMatch != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { navController.navigate("chat/${existingMatch.id}") },
                                        colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserSuccess),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Open Chat with ${offer.hostName}", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
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
                                    colors = ButtonDefaults.buttonColors(disabledContainerColor = SplitCruiserOutline)
                                ) {
                                    Text("This Trip is Completed", color = SplitCruiserTextSecondary, fontWeight = FontWeight.Bold)
                                }
                            }
                            offer.status == "cancelled" -> {
                                Button(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier.fillMaxWidth().height(54.dp),
                                    colors = ButtonDefaults.buttonColors(disabledContainerColor = SplitCruiserOutline)
                                ) {
                                    Text("This Trip is Cancelled", color = SplitCruiserTextSecondary, fontWeight = FontWeight.Bold)
                                }
                            }
                            offer.status == "closed" -> {
                                Button(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier.fillMaxWidth().height(54.dp),
                                    colors = ButtonDefaults.buttonColors(disabledContainerColor = SplitCruiserOutline)
                                ) {
                                    Text("This Trip's Window Has Closed", color = SplitCruiserTextSecondary, fontWeight = FontWeight.Bold)
                                }
                            }
                            offer.status == "full" || offer.seatsLeft <= 0 -> {
                                Button(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier.fillMaxWidth().height(54.dp),
                                    colors = ButtonDefaults.buttonColors(disabledContainerColor = SplitCruiserOutline)
                                ) {
                                    Text("Ride is Full", color = SplitCruiserTextSecondary, fontWeight = FontWeight.Bold)
                                }
                            }
                            else -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // Show Direct Join Button
                                    Button(
                                        onClick = {
                                            joinButtonPressed = true
                                            vibrate(context, 50)
                                            viewModel.joinTripOfferDirect(offer.id) { match ->
                                                joinButtonPressed = false
                                                vibrateSuccess(context)
                                                Toast.makeText(
                                                    context,
                                                    "Seat reserved! Opening chat with ${offer.hostName}...",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                navController.navigate("chat/${match.id}")
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp)
                                            .testTag("direct_join_button")
                                            .withButtonScale(joinScale),
                                        colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserPrimary),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Join", tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Join Ride (Reserve Seat)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                    Text(
                                        text = "Reserves your seat immediately at $${offer.costPerRider} — no host approval needed.",
                                        color = SplitCruiserTextSecondary,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                    )

                                    Spacer(modifier = Modifier.height(20.dp))

                                    // Alternatively keep the original Request to Join Match system as secondary option
                                    Text(
                                        text = "OR PROPOSE A DIFFERENT AMOUNT (HOST MUST APPROVE):",
                                        color = SplitCruiserTextSecondary,
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
                                                focusedBorderColor = SplitCruiserPrimary,
                                                unfocusedBorderColor = SplitCruiserOutline,
                                                focusedTextColor = SplitCruiserTextPrimary,
                                                unfocusedTextColor = SplitCruiserTextPrimary,
                                                focusedContainerColor = SplitCruiserSurfaceCard,
                                                unfocusedContainerColor = SplitCruiserSurfaceCard
                                            )
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Button(
                                            onClick = {
                                                val contributionDouble = customContribution.toDoubleOrNull() ?: offer.costPerRider
                                                viewModel.requestSeat(offer.id, contributionDouble) {
                                                    showSuccessDialog = true
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("propose_contribution_button"),
                                            colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserPrimaryContainer),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Propose Contribution", color = SplitCruiserPrimary, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = if (existingMatch.status == "accepted") SplitCruiserSuccess.copy(alpha = 0.15f) else SplitCruiserPrimary.copy(alpha = 0.15f)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = if (existingMatch.status == "accepted") Icons.Default.CheckCircle else Icons.Default.HourglassEmpty,
                                                        contentDescription = "Status",
                                                        tint = if (existingMatch.status == "accepted") SplitCruiserSuccess else SplitCruiserPrimary
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
                                                        colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserSuccess),
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
        //
        // `activeRequests` only holds status == "active" requests. A request the host just
        // successfully accepted flips to "matched" as part of that acceptance — so reading only
        // from that feed made a *successful* accept look identical to a deleted request. The cache
        // accessor below reads the underlying store without that status filter.
        var fetchedRequest by remember(id) { mutableStateOf<RideRequest?>(null) }
        var requestFetchAttempted by remember(id) { mutableStateOf(false) }
        val cachedRequest = requests.find { it.id == id } ?: viewModel.getRideRequestById(id)
        val request = cachedRequest ?: fetchedRequest

        LaunchedEffect(id, cachedRequest) {
            if (cachedRequest == null && !requestFetchAttempted) {
                viewModel.fetchRideRequest(id) { fetched ->
                    fetchedRequest = fetched
                    requestFetchAttempted = true
                }
            }
        }

        if (request == null) {
            if (!requestFetchAttempted) {
                Box(
                    modifier = Modifier.fillMaxSize().background(SplitCruiserSurface),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SplitCruiserPrimary)
                }
                return
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SplitCruiserSurface),
                contentAlignment = Alignment.Center
            ) {
                SplitCruiserEmptyState(
                    title = "Request Unavailable",
                    description = "This ride request's details are no longer available. It may have been matched, cancelled, or deleted by the rider.",
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
                    colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SplitCruiserOutline, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        RouteIndicator(
                            origin = request.origin,
                            destination = request.destination,
                            scale = RouteScale.Detail,
                            pins = true,
                            originLabel = "RIDER PICKUP",
                            destinationLabel = "RIDER DROPOFF"
                        )
                        if (request.exitLocation.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PinDrop,
                                    contentDescription = "Exact meeting spot icon",
                                    tint = SplitCruiserTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = request.exitLocation,
                                    color = SplitCruiserTextSecondary,
                                    fontSize = SplitCruiserTextSize.Caption
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                GoogleMapsMatrixCard(origin = request.origin, destination = request.destination)

                Spacer(modifier = Modifier.height(16.dp))

                // Rider details
                Card(
                    colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SplitCruiserOutline, RoundedCornerShape(16.dp))
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
                                .background(SplitCruiserPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(request.riderName.take(1).uppercase(), color = SplitCruiserPrimary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(request.riderName, color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Rider Rating: ${String.format(Locale.US, "%.1f", request.riderRating)} ★", color = SplitCruiserTextSecondary, fontSize = 12.sp)
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
                    Text("RIDER NOTES", color = SplitCruiserPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SplitCruiserOutline, RoundedCornerShape(12.dp))
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
                        colors = CardDefaults.cardColors(containerColor = SplitCruiserSuccess.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("You accepted this ride request!", color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { navController.navigate("chat/${activeAcceptedMatch.id}") },
                                colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserSuccess),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open Chat Room", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (activePendingMatch != null) {
                    Text("PENDING COST-SPLIT MATCH", color = SplitCruiserPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SplitCruiserOutline, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Rider offered gas contribution split:", color = SplitCruiserTextSecondary, fontSize = 12.sp)
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
                                    onClick = {
                                        viewModel.acceptMatch(activePendingMatch.id) {
                                            navController.navigate("chat/${activePendingMatch.id}")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserSuccess),
                                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Accept & Chat", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    // A seat can only be offered on a ride that exists. This used to pass an id
                    // made up from the clock, so the button failed with "Trip offer not found"
                    // every time it was pressed.
                    val hostedRides by viewModel.hostedRides.collectAsState()
                    val offerable = hostedRides.filter {
                        it.status == "active" &&
                            it.departureTime > System.currentTimeMillis() &&
                            it.seatsLeft >= request.seatsNeeded
                    }
                    var showOfferPicker by remember { mutableStateOf(false) }
                    var showAcceptDialog by remember { mutableStateOf(false) }

                    if (showAcceptDialog) {
                        AcceptRequestDialog(
                            request = request,
                            viewModel = viewModel,
                            onDismiss = { showAcceptDialog = false },
                            onAccepted = { match ->
                                showAcceptDialog = false
                                Toast.makeText(
                                    context,
                                    "You're giving this ride. Opening chat with ${request.riderName}...",
                                    Toast.LENGTH_LONG
                                ).show()
                                navController.navigate("chat/${match.id}")
                            },
                        )
                    }

                    if (showOfferPicker) {
                        AlertDialog(
                            onDismissRequest = { showOfferPicker = false },
                            containerColor = SplitCruiserSurfaceCard,
                            title = {
                                Text(
                                    "Which ride are you offering?",
                                    color = SplitCruiserTextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            text = {
                                Column {
                                    offerable.forEach { hostedOffer ->
                                        TextButton(
                                            onClick = {
                                                showOfferPicker = false
                                                viewModel.offerSeat(
                                                    request.id,
                                                    hostedOffer.id,
                                                    hostedOffer.costPerRider * request.seatsNeeded,
                                                ) { match ->
                                                    Toast.makeText(
                                                        context,
                                                        "Ride offered! Opening chat with ${request.riderName}...",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                    navController.navigate("chat/${match.id}")
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                "${hostedOffer.origin} → ${hostedOffer.destination} " +
                                                    "(${hostedOffer.seatsLeft} seats left)",
                                                color = SplitCruiserTextPrimary
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showOfferPicker = false }) {
                                    Text("Cancel", color = SplitCruiserPrimary)
                                }
                            }
                        )
                    }

                    // Accepting no longer depends on having posted a ride: the shared repository
                    // mints the backing offer, the same way instant-reserve mints a backing
                    // request for a rider. Attaching it to an existing ride stays available for
                    // hosts who do have one, but it is no longer the only way through.
                    Button(
                        onClick = { showAcceptDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Accept & give this ride", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        "You don't need a posted ride. Accepting creates one for this trip.",
                        fontSize = SplitCruiserTextSize.Eyebrow,
                        color = SplitCruiserTextSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = SplitCruiserSpacing.Sm),
                        textAlign = TextAlign.Center
                    )

                    if (offerable.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showOfferPicker = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = SplitCruiserSpacing.Sm)
                                .height(48.dp),
                            shape = RoundedCornerShape(SplitCruiserRadius.Md),
                            border = BorderStroke(1.dp, SplitCruiserPrimary.copy(alpha = 0.5f))
                        ) {
                            Text(
                                "Add to one of my posted rides",
                                color = SplitCruiserPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = SplitCruiserTextSize.Caption
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSuccessDialog && type == "offer") {
        val offer = offers.find { it.id == id } ?: viewModel.getTripOfferById(id)
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

// --- Screen 7: Real-time Coordinate & Coordination Chat ---

@Composable
fun ChatScreen(matchId: String, viewModel: MainViewModel, navController: NavController) {
    val context = LocalContext.current
    // remember(matchId), not a fresh call per recomposition: viewModel.getChatMessages builds a new
    // Flow each time it is invoked, so an unremembered call restarts the collection on every frame.
    val messageFlow = remember(matchId) { viewModel.getChatMessages(matchId) }
    val messageList by messageFlow.collectAsState(initial = emptyList())
    var currentMsgText by remember { mutableStateOf("") }
    val currentUser by viewModel.currentUser.collectAsState()
    val matches by viewModel.userMatches.collectAsState()

    // Opening the chat tightens the message poll to 3s; closing it must hand the tightening back.
    // This used to happen as a side effect inside getChatMessages with no matching close, which
    // left the repository polling a conversation the user had long since left.
    DisposableEffect(matchId) {
        viewModel.openChat(matchId)
        onDispose { viewModel.closeChat() }
    }

    val currentMatch = matches.find { it.id == matchId }
    val coroutineScope = rememberCoroutineScope()

    val currentOffer = remember(currentMatch) { currentMatch?.let { viewModel.getTripOfferById(it.offerId) } }
    var isOfferDetailsExpanded by remember { mutableStateOf(false) }
    var showProposeDialog by remember { mutableStateOf(false) }

    // Which proposals already have a confirmation. Without this the Accept button never went away,
    // so every extra tap posted another "Pickup confirmed" card.
    val confirmedProposalIds = remember(messageList) {
        messageList.filter { it.kind == MessageType.PICKUP_CONFIRMED }
            .map { it.proposalId }
            .filter { it.isNotEmpty() }
            .toSet()
    }
    var confirmingProposalId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (currentMatch?.hostId == currentUser?.id) "Ride with ${currentMatch?.riderName ?: "Rider"}" else "Ride Coordinator Chat",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = SplitCruiserTextPrimary
                        )
                        Text(
                            text = "Split Contribution: $${currentMatch?.contribution ?: 0.0}",
                            fontSize = 11.sp,
                            color = SplitCruiserPrimary
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
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = SplitCruiserPrimary)
                    }

                    // Complete Trip / Rating Action
                    IconButton(onClick = {
                        coroutineScope.launch {
                            viewModel.completeTrip(matchId)
                            // Prompt Rating dialog / screen
                            navController.navigate("profile")
                        }
                    }) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Complete Trip", tint = SplitCruiserSuccess)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SplitCruiserSurfaceCard)
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SplitCruiserSurfaceCard)
                    .border(1.dp, SplitCruiserOutline)
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
                                .background(SplitCruiserPrimaryContainer.copy(alpha = 0.4f))
                                .clickable {
                                    viewModel.sendMessage(matchId, text)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = text, color = SplitCruiserPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                            focusedBorderColor = SplitCruiserPrimary,
                            unfocusedBorderColor = SplitCruiserOutline,
                            focusedContainerColor = SplitCruiserSurface,
                            unfocusedContainerColor = SplitCruiserSurface
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
                        containerColor = SplitCruiserPrimary,
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
        containerColor = SplitCruiserSurface
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
                    colors = CardDefaults.cardColors(containerColor = SplitCruiserPrimaryContainer.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SplitCruiserPrimaryContainer)
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
                                    tint = SplitCruiserPrimary,
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
                                    color = SplitCruiserPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (isOfferDetailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = SplitCruiserPrimary,
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
                                Divider(color = SplitCruiserOutline.copy(alpha = 0.5f))
                                
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(imageVector = Icons.Default.Place, contentDescription = null, tint = SplitCruiserPrimary, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text("FROM:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SplitCruiserTextSecondary)
                                        Text(currentOffer.origin, fontSize = 12.sp, color = SplitCruiserTextPrimary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("TO:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SplitCruiserTextSecondary)
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
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = SplitCruiserPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Departure: $formattedTime",
                                        fontSize = 12.sp,
                                        color = SplitCruiserTextPrimary
                                    )
                                }

                                if (currentOffer.vehicleInfo.isNotEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.DirectionsCar, contentDescription = null, tint = SplitCruiserPrimary, modifier = Modifier.size(16.dp))
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
                                    colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserPrimary),
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
                    val isSystem = msg.isSystem
                    // Read off the message's own type. This used to be `text.startsWith
                    // ("[PROPOSAL]")`, so anything a user typed beginning with that literal
                    // rendered as a system proposal card instead of their message.
                    val isProposal = msg.kind == MessageType.PICKUP_PROPOSAL
                    val isConfirmed = msg.kind == MessageType.PICKUP_CONFIRMED

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = if (isSystem) Arrangement.Center else if (isMe) Arrangement.End else Arrangement.Start
                    ) {
                        if (isProposal) {
                            Card(
                                modifier = Modifier
                                    .widthIn(max = 280.dp)
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isMe) SplitCruiserPrimary.copy(alpha = 0.05f) else SplitCruiserPrimaryContainer.copy(alpha = 0.15f)
                                ),
                                border = BorderStroke(1.dp, SplitCruiserPrimary),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Place,
                                            contentDescription = null,
                                            tint = SplitCruiserPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Proposed Pickup Info",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = SplitCruiserPrimary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    PickupDetailRow(label = "Pick up", value = msg.spot)
                                    if (msg.dropoffSpot.isNotEmpty()) {
                                        PickupDetailRow(label = "Drop off", value = msg.dropoffSpot)
                                    }
                                    PickupDetailRow(label = "Time", value = msg.time)
                                    if (msg.contribution > 0.0) {
                                        PickupDetailRow(
                                            label = "Your share",
                                            value = formatContribution(msg.contribution),
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val isAlreadyConfirmed = msg.id in confirmedProposalIds
                                    if (!isMe && !isAlreadyConfirmed) {
                                        val isConfirming = confirmingProposalId == msg.id
                                        Button(
                                            onClick = {
                                                confirmingProposalId = msg.id
                                                viewModel.confirmPickup(msg.id) { confirmingProposalId = null }
                                            },
                                            enabled = !isConfirming,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = SplitCruiserSuccess,
                                                disabledContainerColor = SplitCruiserSuccess.copy(alpha = 0.5f),
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(32.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.White)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isConfirming) "Confirming…" else "Accept and confirm",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                            )
                                        }
                                    } else if (isAlreadyConfirmed) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(SplitCruiserSuccess.copy(alpha = 0.15f))
                                                .padding(vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Confirmed",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SplitCruiserSuccess
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(SplitCruiserTextSecondary.copy(alpha = 0.1f))
                                                .padding(vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Awaiting other's confirmation...",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = SplitCruiserTextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (isConfirmed) {
                            Card(
                                modifier = Modifier
                                    .widthIn(max = 280.dp)
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = SplitCruiserSuccess.copy(alpha = 0.12f)
                                ),
                                border = BorderStroke(1.5.dp, SplitCruiserSuccess),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = SplitCruiserSuccess,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Pickup confirmed",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = SplitCruiserSuccess
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(SplitCruiserSpacing.Xs))
                                    PickupDetailRow(label = "Pick up", value = msg.spot)
                                    if (msg.dropoffSpot.isNotEmpty()) {
                                        PickupDetailRow(label = "Drop off", value = msg.dropoffSpot)
                                    }
                                    PickupDetailRow(label = "Time", value = msg.time)
                                    if (msg.contribution > 0.0) {
                                        PickupDetailRow(
                                            label = "Agreed share",
                                            value = formatContribution(msg.contribution),
                                        )
                                        Spacer(modifier = Modifier.height(SplitCruiserSpacing.Xs))
                                        Text(
                                            text = "Both of you have agreed to this amount. Pay in cash when you meet.",
                                            fontSize = SplitCruiserTextSize.Eyebrow,
                                            color = SplitCruiserTextSecondary
                                        )
                                    }
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
                                        if (isSystem) SplitCruiserOutline else if (isMe) SplitCruiserPrimary else SplitCruiserSurfaceCard
                                    )
                                    .then(
                                        if (isMe || isSystem) Modifier else Modifier.border(1.dp, SplitCruiserOutline, bubbleShape)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Column {
                                    if (!isSystem && !isMe) {
                                        Text(msg.senderName, color = SplitCruiserPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }
                                    Text(
                                        text = msg.text,
                                        color = if (isSystem) SplitCruiserTextSecondary else if (isMe) Color.White else SplitCruiserTextPrimary,
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
            initialPickup = currentOffer?.origin.orEmpty(),
            initialDropoff = currentOffer?.destination.orEmpty(),
            initialContribution = currentMatch?.contribution ?: 0.0,
            // 0.0 is the model's default, not a location — passing it would rank results toward
            // the Gulf of Guinea.
            biasLat = currentOffer?.originLat?.takeIf { it != 0.0 },
            biasLng = currentOffer?.originLng?.takeIf { it != 0.0 },
            onDismiss = { showProposeDialog = false },
            onPropose = { pickup, dropoff, time, contribution ->
                viewModel.sendPickupProposal(
                    matchId = matchId,
                    pickupAddress = pickup,
                    dropoffAddress = dropoff,
                    pickupTime = time,
                    contribution = contribution,
                )
                showProposeDialog = false
            }
        )
    }
}

/** One labelled line inside a pickup card, so proposal and confirmation read identically. */
@Composable
private fun PickupDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            fontSize = SplitCruiserTextSize.Eyebrow,
            fontWeight = FontWeight.Bold,
            color = SplitCruiserTextSecondary,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            fontSize = SplitCruiserTextSize.Caption,
            fontWeight = FontWeight.Medium,
            color = SplitCruiserTextPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}

/** `12.5` -> `"$12.50"`. */
private fun formatContribution(amount: Double): String =
    "$" + String.format(Locale.US, "%.2f", amount)

/**
 * Confirms a driver taking a rider's request, and collects the one thing the request cannot
 * carry: what each rider should chip in.
 *
 * A [RideRequest] records where and when, never a price, so this is the only point in the flow
 * where the cost split can be set. It prefills from the shared `suggestedContribution` — distance
 * based, and shared so both platforms propose the same number — because a driver looking at an
 * empty box has nothing to anchor on.
 */
@Composable
fun AcceptRequestDialog(
    request: RideRequest,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onAccepted: (TripMatch) -> Unit,
) {
    var contribution by remember { mutableStateOf("") }
    var isSuggesting by remember { mutableStateOf(true) }

    LaunchedEffect(request.id) {
        viewModel.suggestedContribution(request) { suggested ->
            // 0.0 means the route could not be resolved; an empty field beats a wrong number.
            if (suggested > 0.0) contribution = String.format(Locale.US, "%.2f", suggested)
            isSuggesting = false
        }
    }

    val amount = contribution.trim().toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SplitCruiserSurfaceCard,
        shape = RoundedCornerShape(SplitCruiserRadius.Lg),
        title = {
            Text(
                "Give this ride",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = SplitCruiserTextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(SplitCruiserSpacing.Md)) {
                RouteIndicator(
                    origin = request.origin,
                    destination = request.destination,
                    scale = RouteScale.Compact,
                    pins = true,
                    originLabel = "PICKUP",
                    destinationLabel = "DROPOFF",
                )
                HorizontalDivider(color = SplitCruiserOutline)
                CardStat(
                    label = "DEPARTS",
                    value = SimpleDateFormat("EEEE, d MMMM • h:mm a", Locale.US)
                        .format(Date(request.departureTime)),
                )
                CardStat(
                    label = "SEATS",
                    value = "${request.seatsNeeded} seat${if (request.seatsNeeded == 1) "" else "s"}",
                )

                OutlinedTextField(
                    value = contribution,
                    onValueChange = { contribution = it },
                    label = { Text("Each rider chips in ($)") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    leadingIcon = {
                        Icon(Icons.Default.AttachMoney, null, tint = SplitCruiserWarning)
                    },
                    trailingIcon = {
                        if (isSuggesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = SplitCruiserPrimary,
                            )
                        }
                    },
                    shape = RoundedCornerShape(SplitCruiserRadius.Md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("accept_contribution_input"),
                )

                // Say why, not just what: the number is a suggestion, and no money moves in-app.
                Text(
                    "Suggested from the distance. Cash is settled in person — you can agree " +
                        "something else in chat.",
                    fontSize = SplitCruiserTextSize.Eyebrow,
                    color = SplitCruiserTextSecondary,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    amount?.let { viewModel.acceptRequestDirect(request.id, it, onAccepted) }
                },
                enabled = amount != null && amount >= 0.0,
                colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserPrimary),
                shape = RoundedCornerShape(SplitCruiserRadius.Md),
                modifier = Modifier.testTag("confirm_accept_button"),
            ) {
                Text("Accept & open chat", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SplitCruiserTextSecondary)
            }
        },
    )
}

@Composable
fun ProposePickupDialog(
    initialPickup: String,
    initialDropoff: String,
    initialContribution: Double,
    biasLat: Double?,
    biasLng: Double?,
    onDismiss: () -> Unit,
    onPropose: (pickup: String, dropoff: String, time: String, contribution: Double) -> Unit
) {
    // Prefilled from the ride itself. A proposal usually only needs the addresses sharpened —
    // "the Science Library entrance" rather than "Northeastern" — not typed from nothing.
    var pickup by remember { mutableStateOf(initialPickup) }
    var dropoff by remember { mutableStateOf(initialDropoff) }
    var time by remember { mutableStateOf("") }
    var contribution by remember {
        mutableStateOf(
            if (initialContribution > 0.0) String.format(Locale.US, "%.2f", initialContribution) else ""
        )
    }

    val amount = contribution.trim().toDoubleOrNull()
    val canSend = pickup.trim().isNotEmpty() && time.trim().isNotEmpty() &&
        (contribution.isBlank() || (amount != null && amount >= 0.0))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Place, contentDescription = null, tint = SplitCruiserPrimary)
                Spacer(modifier = Modifier.width(SplitCruiserSpacing.Sm))
                Text("Propose pickup details", color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold, fontSize = SplitCruiserTextSize.Headline)
            }
        },
        containerColor = SplitCruiserSurfaceCard,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SplitCruiserSpacing.Md)
            ) {
                Text(
                    text = "Agree the exact addresses, the time, and what the ride costs. The other " +
                        "person confirms it, and the amount becomes the ride's split.",
                    color = SplitCruiserTextSecondary,
                    fontSize = SplitCruiserTextSize.Body
                )

                LocationAutoCompleteTextField(
                    value = pickup,
                    onValueChange = { pickup = it },
                    label = "Exact pickup address",
                    placeholder = "e.g. 360 Huntington Ave, Boston",
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "propose_location_input",
                    focusedBorderColor = SplitCruiserPrimary,
                    biasLat = biasLat,
                    biasLng = biasLng,
                )

                LocationAutoCompleteTextField(
                    value = dropoff,
                    onValueChange = { dropoff = it },
                    label = "Exact drop-off address",
                    placeholder = "e.g. 700 Commonwealth Ave, Boston",
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "propose_dropoff_input",
                    focusedBorderColor = SplitCruiserPrimary,
                    biasLat = biasLat,
                    biasLng = biasLng,
                )

                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Pickup time") },
                    placeholder = { Text("e.g. 5:45 PM or in 10 mins") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("propose_time_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SplitCruiserTextPrimary,
                        unfocusedTextColor = SplitCruiserTextPrimary,
                        focusedBorderColor = SplitCruiserPrimary,
                        unfocusedBorderColor = SplitCruiserOutline,
                        focusedLabelColor = SplitCruiserPrimary,
                        unfocusedLabelColor = SplitCruiserTextSecondary
                    )
                )

                OutlinedTextField(
                    value = contribution,
                    onValueChange = { contribution = it },
                    label = { Text("Rider's share") },
                    placeholder = { Text("0.00") },
                    prefix = { Text("$", color = SplitCruiserTextSecondary) },
                    singleLine = true,
                    isError = contribution.isNotBlank() && amount == null,
                    supportingText = {
                        Text(
                            text = if (contribution.isNotBlank() && amount == null) {
                                "Enter an amount like 12.50"
                            } else {
                                "Cash, settled in person when you meet"
                            },
                            fontSize = SplitCruiserTextSize.Eyebrow,
                            color = SplitCruiserTextSecondary
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("propose_contribution_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SplitCruiserTextPrimary,
                        unfocusedTextColor = SplitCruiserTextPrimary,
                        focusedBorderColor = SplitCruiserPrimary,
                        unfocusedBorderColor = SplitCruiserOutline,
                        focusedLabelColor = SplitCruiserPrimary,
                        unfocusedLabelColor = SplitCruiserTextSecondary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (canSend) {
                        onPropose(pickup.trim(), dropoff.trim(), time.trim(), amount ?: 0.0)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserPrimary),
                enabled = canSend
            ) {
                Text("Send proposal", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SplitCruiserTextSecondary)
            }
        }
    )
}

/**
 * `avatar_07` -> `R.drawable.avatar_07`.
 *
 * A `when` rather than `resources.getIdentifier`, which R8 cannot see through and which would let
 * a release build strip drawables nothing appears to reference. The list is generated by
 * `scripts/generate-avatars.py` and mirrored by `SplitCruiserAvatars.ALL` in `:shared`.
 */
private fun avatarDrawable(key: String): Int = when (key) {
    "avatar_01" -> R.drawable.avatar_01
    "avatar_02" -> R.drawable.avatar_02
    "avatar_03" -> R.drawable.avatar_03
    "avatar_04" -> R.drawable.avatar_04
    "avatar_05" -> R.drawable.avatar_05
    "avatar_06" -> R.drawable.avatar_06
    "avatar_07" -> R.drawable.avatar_07
    "avatar_08" -> R.drawable.avatar_08
    "avatar_09" -> R.drawable.avatar_09
    "avatar_10" -> R.drawable.avatar_10
    "avatar_11" -> R.drawable.avatar_11
    else -> R.drawable.avatar_12
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
            // Both stops are dark: the fallback initial inside is drawn in white, and the pale
            // PrimaryContainer this used to end on left it barely visible.
            .background(Brush.linearGradient(listOf(SplitCruiserPrimary, SplitCruiserOnPrimaryContainer))),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUrl.isNotEmpty()) {
            if (avatarUrl.startsWith("http")) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Profile Picture",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = painterResource(id = R.drawable.img_split_cruiser_logo)
                )
            } else if (SplitCruiserAvatars.isAvatarKey(avatarUrl)) {
                Image(
                    painter = painterResource(id = avatarDrawable(avatarUrl)),
                    contentDescription = SplitCruiserAvatars.accessibilityLabel(avatarUrl),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                // The six object emoji this replaced. Still resolved, because the key lives in the
                // user's avatarUrl — anyone who picked one before the change still has it stored,
                // and dropping the branch would turn their avatar into a bare letter.
                val emoji = SplitCruiserAvatars.legacyEmoji(avatarUrl) ?: ""
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
    var avatarUrl by remember { mutableStateOf(currentUser?.avatarUrl ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Edit Your Profile", color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold)
        },
        containerColor = SplitCruiserSurfaceCard,
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("PROFILE PICTURE", color = SplitCruiserPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StudentAvatar(
                            avatarUrl = avatarUrl,
                            name = name,
                            size = 72.dp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "Pick an avatar, or upload a photo when you set up your profile.",
                        color = SplitCruiserTextSecondary,
                        fontSize = SplitCruiserTextSize.Caption
                    )
                    Spacer(modifier = Modifier.height(SplitCruiserSpacing.Sm))

                    // Two rows of six: twelve avatars do not fit across a phone.
                    SplitCruiserAvatars.ALL.chunked(6).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            row.forEach { key ->
                                val isSelected = avatarUrl == key
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) SplitCruiserPrimary else SplitCruiserOutline,
                                            shape = CircleShape
                                        )
                                        .clickable { avatarUrl = key },
                                    contentAlignment = Alignment.Center
                                ) {
                                    StudentAvatar(avatarUrl = key, name = name, size = 44.dp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(SplitCruiserSpacing.Sm))
                    }
                }

                item {
                    Divider(color = SplitCruiserOutline, modifier = Modifier.padding(vertical = 8.dp))
                    Text("PERSONAL DETAILS", color = SplitCruiserPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("First Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SplitCruiserPrimary,
                                unfocusedBorderColor = SplitCruiserOutline,
                                focusedTextColor = SplitCruiserTextPrimary,
                                unfocusedTextColor = SplitCruiserTextPrimary,
                                focusedLabelColor = SplitCruiserPrimary,
                                unfocusedLabelColor = SplitCruiserTextSecondary
                            )
                        )
                        OutlinedTextField(
                            value = lastInitial,
                            onValueChange = { lastInitial = it },
                            label = { Text("Initial") },
                            singleLine = true,
                            modifier = Modifier.width(60.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SplitCruiserPrimary,
                                unfocusedBorderColor = SplitCruiserOutline,
                                focusedTextColor = SplitCruiserTextPrimary,
                                unfocusedTextColor = SplitCruiserTextPrimary,
                                focusedLabelColor = SplitCruiserPrimary,
                                unfocusedLabelColor = SplitCruiserTextSecondary
                            )
                        )
                    }
                }

            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.updateUserProfileDetails(
                        name = name,
                        lastInitial = lastInitial,
                        avatarUrl = avatarUrl,
                        onSuccess = onDismiss
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserPrimary)
            ) {
                Text("Save Changes", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SplitCruiserTextSecondary)
            }
        }
    )
}

// --- Screen 8: Profile and Rating Settings ---

@Composable
fun ProfileScreen(viewModel: MainViewModel, navController: NavController) {
    val currentUser by viewModel.currentUser.collectAsState()
    val isFirebaseEnabled = viewModel.repository.isFirebaseEnabled
    val userAlerts by viewModel.notifications.collectAsState()

    var showEditProfileDialog by remember { mutableStateOf(false) }

    // Rating submit state. The target is picked from the user's own match history — it used to
    // be a free-text field asking for the other person's Firebase uid, which nobody can know.
    val userMatches by viewModel.userMatches.collectAsState()
    // Who this user has already rated, so the same person stops being offered forever. This is a
    // plain cache read, so it has to be a `remember` key of its own — without it the list would
    // not recompute after a submit and the person just rated would stay on screen.
    var ratedIds by remember { mutableStateOf(viewModel.ratedUserIds()) }
    val rateableCompanions = remember(userMatches, currentUser, ratedIds) {
        val me = currentUser?.id.orEmpty()
        userMatches
            .filter { it.status == "accepted" || it.status == "completed" }
            .mapNotNull { match ->
                val otherId = if (match.hostId == me) match.riderId else match.hostId
                if (otherId.isBlank() || otherId == me) return@mapNotNull null
                if (otherId in ratedIds) return@mapNotNull null
                val name = if (match.hostId == me) {
                    match.riderName.takeIf { it.isNotBlank() } ?: "Your rider"
                } else {
                    viewModel.getUserPublicProfile(otherId)?.displayName
                        ?: viewModel.getTripOfferById(match.offerId)?.hostName
                        ?: "Your host"
                }
                RatingCompanion(userId = otherId, displayName = name, wasHost = match.hostId != me)
            }
            .distinctBy { it.userId }
    }
    var ratingTarget by remember { mutableStateOf<RatingCompanion?>(null) }
    var ratingValue by remember { mutableStateOf(5f) }
    var ratingComment by remember { mutableStateOf("") }

    // A companion who leaves the list (e.g. the match was cancelled) must not stay selected.
    LaunchedEffect(rateableCompanions) {
        if (rateableCompanions.none { it.userId == ratingTarget?.userId }) ratingTarget = null
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
                Text("Your Split Cruiser Account", color = SplitCruiserTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // User Identity Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
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
                        text = currentUser?.displayName ?: "Rider",
                        color = SplitCruiserTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FirebaseStatusPill(isFirebaseEnabled = isFirebaseEnabled)
                        if (currentUser?.verifiedTier == "vouched") {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SplitCruiserSuccess.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Verified",
                                        tint = SplitCruiserSuccess,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Verified", color = SplitCruiserSuccess, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = { showEditProfileDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserPrimary.copy(alpha = 0.15f), contentColor = SplitCruiserPrimary),
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
                    Divider(color = SplitCruiserOutline)
                    Spacer(modifier = Modifier.height(16.dp))

                    // The rating you were given moved to its own section below; what is left here
                    // is what the identity card is actually for.
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${currentUser?.ratingCount ?: 0}",
                                color = SplitCruiserTextPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                            Text("Trips shared", color = SplitCruiserTextSecondary, fontSize = SplitCruiserTextSize.Eyebrow)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${currentUser?.noShowCount ?: 0}",
                                color = SplitCruiserDanger,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                            Text("No-shows", color = SplitCruiserTextSecondary, fontSize = SplitCruiserTextSize.Eyebrow)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(SplitCruiserSpacing.Xl))

            // What other people said about you — separate from the form for rating them.
            Text(
                "YOUR RATING",
                color = SplitCruiserPrimary,
                fontSize = SplitCruiserTextSize.Eyebrow,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(SplitCruiserSpacing.Sm))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(SplitCruiserRadius.Lg),
                colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard)
            ) {
                Column(modifier = Modifier.padding(SplitCruiserSpacing.Lg)) {
                    if ((currentUser?.ratingCount ?: 0) > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.1f", currentUser!!.ratingAvg),
                                color = SplitCruiserPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 32.sp
                            )
                            Spacer(modifier = Modifier.width(SplitCruiserSpacing.Sm))
                            Icon(Icons.Default.Star, null, tint = SplitCruiserWarning, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "from ${currentUser!!.ratingCount} ride${if (currentUser!!.ratingCount == 1) "" else "s"}",
                                color = SplitCruiserTextSecondary,
                                fontSize = SplitCruiserTextSize.Caption
                            )
                        }
                    } else {
                        Text(
                            "No ratings yet. Share a ride and whoever you travel with can rate you.",
                            color = SplitCruiserTextSecondary,
                            fontSize = SplitCruiserTextSize.Caption
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(SplitCruiserSpacing.Xl))

            Text("RATE SOMEONE YOU RODE WITH", color = SplitCruiserPrimary, fontSize = SplitCruiserTextSize.Eyebrow, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(SplitCruiserSpacing.Sm))

            Card(
                colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
                shape = RoundedCornerShape(SplitCruiserRadius.Md),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(SplitCruiserSpacing.Lg)) {
                    if (rateableCompanions.isEmpty()) {
                        Text(
                            text = if (ratedIds.isEmpty()) {
                                "Once you've shared a ride, whoever you rode with shows up here to rate."
                            } else {
                                "You've rated everyone you've ridden with. Share another ride to rate someone new."
                            },
                            color = SplitCruiserTextSecondary,
                            fontSize = SplitCruiserTextSize.Caption,
                            lineHeight = 16.sp
                        )
                    } else {
                        Text(
                            text = "Who did you ride with?",
                            color = SplitCruiserTextPrimary,
                            fontSize = SplitCruiserTextSize.Caption,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(SplitCruiserSpacing.Sm))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(SplitCruiserSpacing.Sm)) {
                            items(rateableCompanions) { companion ->
                                val isSelected = companion.userId == ratingTarget?.userId
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(SplitCruiserRadius.Pill))
                                        .background(
                                            if (isSelected) SplitCruiserPrimary
                                            else SplitCruiserPrimaryContainer.copy(alpha = 0.4f)
                                        )
                                        .clickable { ratingTarget = companion }
                                        .padding(horizontal = SplitCruiserSpacing.Md, vertical = SplitCruiserSpacing.Sm)
                                        .testTag("rating_companion_${companion.userId}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (companion.wasHost) Icons.Default.DirectionsCar else Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (isSelected) SplitCruiserOnPrimary else SplitCruiserPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = companion.displayName,
                                        color = if (isSelected) SplitCruiserOnPrimary else SplitCruiserTextPrimary,
                                        fontSize = SplitCruiserTextSize.Caption,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(SplitCruiserSpacing.Md))

                        Text(
                            text = "How did it go?",
                            color = SplitCruiserTextPrimary,
                            fontSize = SplitCruiserTextSize.Caption,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = ratingValue,
                            onValueChange = { ratingValue = it },
                            valueRange = 1f..5f,
                            steps = 3,
                            colors = SliderDefaults.colors(thumbColor = SplitCruiserPrimary, activeTrackColor = SplitCruiserPrimary)
                        )
                        Text(
                            text = "${ratingValue.toInt()} of 5 stars",
                            color = SplitCruiserPrimary,
                            fontSize = SplitCruiserTextSize.Eyebrow,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(SplitCruiserSpacing.Md))

                        OutlinedTextField(
                            value = ratingComment,
                            onValueChange = { ratingComment = it },
                            label = { Text("Add a note (optional)") },
                            placeholder = { Text("Friendly, easy to find, safe driving") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(SplitCruiserRadius.Md),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SplitCruiserPrimary)
                        )

                        Spacer(modifier = Modifier.height(SplitCruiserSpacing.Md))

                        Button(
                            onClick = {
                                ratingTarget?.let { target ->
                                    viewModel.submitRating(target.userId, ratingValue, ratingComment) {
                                        // Re-read so the person just rated drops off the list.
                                        ratedIds = viewModel.ratedUserIds()
                                        ratingTarget = null
                                        ratingComment = ""
                                        ratingValue = 5f
                                    }
                                }
                            },
                            enabled = ratingTarget != null,
                            shape = RoundedCornerShape(SplitCruiserRadius.Md),
                            colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("submit_rating_button")
                        ) {
                            Text("Submit rating", color = SplitCruiserOnPrimary, fontWeight = FontWeight.Bold)
                        }
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
                    Text("ACTIVE TRIP ALERT MATCHES", color = SplitCruiserPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    TextButton(onClick = { viewModel.clearNotifications() }) {
                        Text("Clear All", color = Color.Red.copy(alpha = 0.8f), fontSize = 11.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                userAlerts.forEach { alert ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (alert.isRead) SplitCruiserSurfaceCard.copy(alpha = 0.5f) else SplitCruiserSurfaceCard
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .border(
                                width = if (alert.isRead) 0.dp else 1.dp,
                                color = SplitCruiserPrimary.copy(alpha = 0.3f),
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
                                        tint = if (alert.isRead) SplitCruiserTextSecondary else SplitCruiserPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = alert.title,
                                        color = if (alert.isRead) SplitCruiserTextSecondary else SplitCruiserTextPrimary,
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
                                        Text("Mark Read", color = SplitCruiserPrimary, fontSize = 10.sp)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = alert.message,
                                color = SplitCruiserTextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Safety Filters Settings
            // Notification Preferences Settings
            Text("NOTIFICATION PREFERENCES", color = SplitCruiserPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Get real-time alerts whenever another rider posts a carpool trip that matches your exact active ride requests.",
                        color = SplitCruiserTextSecondary,
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
                            Icon(Icons.Default.Email, contentDescription = "Email Settings", tint = SplitCruiserPrimary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Email Notifications", color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Receive matching routes via inbox", color = SplitCruiserTextSecondary, fontSize = 10.sp)
                            }
                        }
                        Switch(
                            checked = currentUser?.emailNotificationsEnabled ?: false,
                            onCheckedChange = { viewModel.toggleEmailNotifications(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = SplitCruiserPrimary)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = SplitCruiserOutline)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, contentDescription = "Push Settings", tint = SplitCruiserPrimary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Push Notifications", color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Instantly alert on device screen", color = SplitCruiserTextSecondary, fontSize = 10.sp)
                            }
                        }
                        Switch(
                            checked = currentUser?.pushNotificationsEnabled ?: false,
                            onCheckedChange = { viewModel.togglePushNotifications(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = SplitCruiserPrimary)
                        )
                    }
                }
            }

            Text("SAFETY AND PRIVACY", color = SplitCruiserPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
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
                            Icon(Icons.Default.Female, contentDescription = "Women Filter", tint = SplitCruiserAccent)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Women-Only Filter", color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Only match with other women", color = SplitCruiserTextSecondary, fontSize = 10.sp)
                            }
                        }
                        Switch(
                            checked = currentUser?.isWomenOnlyFilterEnabled ?: false,
                            onCheckedChange = { viewModel.toggleWomenOnlyFilter(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = SplitCruiserAccent)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = SplitCruiserOutline)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate("blocked_list") },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Block, contentDescription = "Blocked", tint = SplitCruiserTextSecondary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Manage blocked users", color = SplitCruiserTextPrimary, fontSize = 13.sp)
                        }
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Open", tint = SplitCruiserTextSecondary)
                    }

                    Spacer(modifier = Modifier.height(SplitCruiserSpacing.Md))
                    Divider(color = SplitCruiserOutline)
                    Spacer(modifier = Modifier.height(SplitCruiserSpacing.Md))

                    // The host_dashboard route has existed since it was written and nothing ever
                    // navigated to it, so the screen was unreachable on Android while iOS shipped
                    // an entry point for it. This is that entry point.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate("host_dashboard") },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BarChart, contentDescription = null, tint = SplitCruiserTextSecondary)
                            Spacer(modifier = Modifier.width(SplitCruiserSpacing.Md))
                            Text("Host dashboard", color = SplitCruiserTextPrimary, fontSize = 13.sp)
                        }
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Open", tint = SplitCruiserTextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Sub-Section: Fast submit mutual ratings
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

// --- Screen 9: Block List Screen ---

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
            Text("Blocked Users", color = SplitCruiserTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (blockedUsers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SplitCruiserEmptyState(
                    title = "High Trust Community!",
                    description = "You haven't blocked anyone. Everyone is vouched and trusted.",
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
                            .background(SplitCruiserSurfaceCard)
                            .border(1.dp, SplitCruiserOutline, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(user.displayName, color = SplitCruiserTextPrimary, fontWeight = FontWeight.Bold)
                            // The Firebase uid used to be printed here. It means nothing to the
                            // person reading it; "what happens if I unblock" does.
                            Text(
                                text = "Hidden from your feed and can't message you",
                                color = SplitCruiserTextSecondary,
                                fontSize = SplitCruiserTextSize.Eyebrow
                            )
                        }
                        Button(
                            onClick = { viewModel.unblockUser(user.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserPrimary),
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
            color = SplitCruiserSurfaceCard,
            border = BorderStroke(1.dp, SplitCruiserOutline)
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
                        color = SplitCruiserPrimary,
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
                            tint = SplitCruiserTextSecondary,
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
                        .background(SplitCruiserPrimaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = hostName.take(1).uppercase(),
                        color = SplitCruiserOnPrimaryContainer,
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
                            tint = SplitCruiserWarning,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f", hostRating),
                            color = SplitCruiserTextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    // Vouched badge
                    val isVouched = verifiedTier.lowercase() == "vouched"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isVouched) SplitCruiserSuccess.copy(alpha = 0.2f) else SplitCruiserTextSecondary.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isVouched) "VERIFIED" else "UNVERIFIED",
                            color = if (isVouched) SplitCruiserSuccess else SplitCruiserTextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = SplitCruiserOutline)
                Spacer(modifier = Modifier.height(16.dp))

                // Contact Information
                Text(
                    text = "CONTACT DETAILS",
                    color = SplitCruiserTextSecondary,
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
                        Text("Phone Number", color = SplitCruiserTextSecondary, fontSize = 11.sp)
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
                        colors = IconButtonDefaults.iconButtonColors(containerColor = SplitCruiserPrimaryContainer)
                    ) {
                        Icon(imageVector = Icons.Default.Phone, contentDescription = "Call", tint = SplitCruiserPrimary)
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
                        Text("Email Address", color = SplitCruiserTextSecondary, fontSize = 11.sp)
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
                        colors = IconButtonDefaults.iconButtonColors(containerColor = SplitCruiserPrimaryContainer)
                    ) {
                        Icon(imageVector = Icons.Default.Email, contentDescription = "Email", tint = SplitCruiserPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = SplitCruiserOutline)
                Spacer(modifier = Modifier.height(16.dp))

                // Vehicle Information
                Text(
                    text = "VEHICLE DETAILS",
                    color = SplitCruiserTextSecondary,
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
                            .background(SplitCruiserPrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = "Car",
                            tint = SplitCruiserPrimary
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
                            color = SplitCruiserTextSecondary,
                            fontSize = 11.sp
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(SplitCruiserPrimaryContainer.copy(alpha = 0.3f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "License Plate: $vehiclePlate",
                                color = SplitCruiserPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = SplitCruiserPrimary),
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
    focusedBorderColor: Color = SplitCruiserSuccess,
    leadingIcon: @Composable (() -> Unit)? = null,
    /**
     * A fallback anchor for ranking, used only when there is no location fix — an already-resolved
     * origin when this field is the destination, or the user's home address. The device's own
     * location takes precedence over it, because "nearest first" means nearest to the person
     * typing.
     */
    biasLat: Double? = null,
    biasLng: Double? = null,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var rankedResults by remember { mutableStateOf<List<RankedPlace>>(emptyList()) }
    var isSearchingPhoton by remember { mutableStateOf(false) }
    var isReverseGeocodingGps by remember { mutableStateOf(false) }
    var deviceLocation by remember { mutableStateOf<DeviceCoordinate?>(null) }
    var hasAskedForLocation by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) {
            scope.launch { deviceLocation = DeviceLocationProvider.current(context) }
        }
    }

    // Asked the first time a field is focused rather than at launch, so the prompt arrives with an
    // obvious reason on screen. A denial is final and silent — suggestions still work, unranked.
    fun ensureLocation() {
        if (hasAskedForLocation) return
        hasAskedForLocation = true
        if (DeviceLocationProvider.hasPermission(context)) {
            scope.launch { deviceLocation = DeviceLocationProvider.current(context) }
        } else {
            locationPermissionLauncher.launch(DeviceLocationProvider.PERMISSIONS)
        }
    }

    // The device wins; the caller's anchor is the fallback. 0.0/0.0 means "no anchor" to the
    // shared searcher, which then leaves Photon's own order alone.
    val anchorLat = deviceLocation?.lat ?: biasLat ?: 0.0
    val anchorLon = deviceLocation?.lon ?: biasLng ?: 0.0

    // Query Photon with a debounce as the user types.
    LaunchedEffect(value, anchorLat, anchorLon) {
        if (value.length >= 2) {
            isSearchingPhoton = true
            kotlinx.coroutines.delay(250) // Debounce
            rankedResults = OsmLocationService.searchPlacesRanked(
                value,
                OsmLocationService.DISPLAY_LIMIT,
                anchorLat,
                anchorLon,
            )
            isSearchingPhoton = false
        } else {
            rankedResults = emptyList()
            isSearchingPhoton = false
        }
    }

    val photonResults = rankedResults

    val filteredPlaces = remember(value, rankedResults) {
        if (rankedResults.isNotEmpty()) {
            rankedResults.map { photon ->
                LocationPlace(
                    name = photon.name,
                    address = listOfNotNull(
                        photon.formattedAddress.ifBlank { null },
                        photon.distanceText.ifBlank { null },
                    ).joinToString(" • "),
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
                        ensureLocation()
                    }
                }
                .testTag(testTag),
            shape = RoundedCornerShape(SplitCruiserRadius.Md),
            leadingIcon = leadingIcon,
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSearchingPhoton) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = SplitCruiserSuccess,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (value.isNotEmpty()) {
                        IconButton(onClick = {
                            onValueChange("")
                            expanded = true
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear location", tint = SplitCruiserTextSecondary)
                        }
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = "Toggle location suggestions",
                            tint = SplitCruiserTextSecondary
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = focusedBorderColor,
                unfocusedBorderColor = SplitCruiserOutline,
                focusedTextColor = SplitCruiserTextPrimary,
                unfocusedTextColor = SplitCruiserTextPrimary,
                focusedLabelColor = focusedBorderColor,
                unfocusedLabelColor = SplitCruiserTextSecondary,
                focusedContainerColor = SplitCruiserSurfaceCard,
                unfocusedContainerColor = SplitCruiserSurfaceCard
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
                shape = RoundedCornerShape(SplitCruiserRadius.Md),
                colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
                border = BorderStroke(1.dp, SplitCruiserOutline),
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
                            // Names what the list is, not which vendor produced it — a rider has no
                            // use for the word "Photon". "Nearest first" is also the one thing
                            // worth saying about the order.
                            text = when {
                                photonResults.isNotEmpty() && deviceLocation != null -> "NEAREST FIRST"
                                photonResults.isNotEmpty() -> "SEARCH RESULTS"
                                value.isBlank() -> "POPULAR CAMPUS & TRANSIT SPOTS"
                                else -> "MATCHING PLACES"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (photonResults.isNotEmpty()) Color(0xFF38BDF8) else SplitCruiserPrimary,
                            letterSpacing = 0.5.sp
                        )

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SplitCruiserSuccess.copy(alpha = 0.15f))
                                .clickable {
                                    // Reads the real device location. This used to hardcode
                                    // 42.3383/-71.0881 — Northeastern's campus — so it filled in
                                    // the same Boston address wherever in the world you tapped it.
                                    if (!isReverseGeocodingGps) {
                                        isReverseGeocodingGps = true
                                        scope.launch {
                                            val fix = deviceLocation
                                                ?: DeviceLocationProvider.current(context)?.also { deviceLocation = it }
                                            if (fix == null) {
                                                ensureLocation()
                                                PlatformContext.showMessage(
                                                    "Turn on location to use this."
                                                )
                                            } else {
                                                val resolved = OsmLocationService.reverseGeocodeNominatim(fix.lat, fix.lon)
                                                val placeName = resolved?.road
                                                    ?: resolved?.displayName
                                                    ?: "My current location"
                                                val placeAddr = resolved?.displayName ?: placeName
                                                val gpsPlace = LocationPlace(placeName, placeAddr, "Current location", fix.lat, fix.lon)
                                                onValueChange(gpsPlace.name)
                                                onLocationSelected(gpsPlace)
                                                expanded = false
                                            }
                                            isReverseGeocodingGps = false
                                        }
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isReverseGeocodingGps) {
                                CircularProgressIndicator(modifier = Modifier.size(10.dp), color = SplitCruiserSuccess, strokeWidth = 1.5.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Finding you…", fontSize = 10.sp, color = SplitCruiserSuccess, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.MyLocation, contentDescription = null, tint = SplitCruiserSuccess, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Use my location", fontSize = 10.sp, color = SplitCruiserSuccess, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HorizontalDivider(color = SplitCruiserOutline, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

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
                                            "Campus" -> SplitCruiserInfo.copy(alpha = 0.2f)
                                            "Airport" -> SplitCruiserWarning.copy(alpha = 0.2f)
                                            "Transit" -> SplitCruiserSuccess.copy(alpha = 0.2f)
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
                                        "Transit" -> SplitCruiserSuccess
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
                                    color = SplitCruiserTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SplitCruiserSurfaceCard
                            ) {
                                Text(
                                    text = place.category,
                                    color = SplitCruiserTextSecondary,
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
        colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
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
                        color = SplitCruiserTextSecondary,
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
                        Text("ESTIMATED DISTANCE", fontSize = 9.sp, color = SplitCruiserTextSecondary, fontWeight = FontWeight.Bold)
                        Text(data.distanceText, fontSize = 14.sp, color = Color(0xFF4285F4), fontWeight = FontWeight.ExtraBold)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("DRIVING TIME", fontSize = 9.sp, color = SplitCruiserTextSecondary, fontWeight = FontWeight.Bold)
                        Text(data.durationText, fontSize = 14.sp, color = Color(0xFF34A853), fontWeight = FontWeight.ExtraBold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = SplitCruiserOutline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                Text("ROUTE SUMMARY", fontSize = 9.sp, color = SplitCruiserPrimary, fontWeight = FontWeight.Bold)
                Text(data.routeSummary, fontSize = 12.sp, color = SplitCruiserTextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)

                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.School, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(data.universityContext, fontSize = 11.sp, color = SplitCruiserTextSecondary)
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, tint = SplitCruiserSuccess, modifier = Modifier.size(14.dp))
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

