package com.splitcruiser.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.splitcruiser.app.ui.theme.SplitCruiserColors as Tokens

/**
 * Compose wrappers over the shared palette in `:shared`. Nothing here invents a value — every
 * constant is `Tokens.X` wrapped in a `Color`, so Android and iOS cannot drift apart.
 *
 * The default `Purple80/PurpleGrey/Pink` template palette that used to live here is gone: it was
 * never referenced for branding, and while it sat in the `MaterialTheme` it did nothing except let
 * dynamic wallpaper colour leak into unstyled Material components on API 31+.
 */

// --- Surfaces ---
val SplitCruiserSurface = Color(Tokens.Surface)
val SplitCruiserSurfaceCard = Color(Tokens.SurfaceCard)
val SplitCruiserSurfaceMuted = Color(Tokens.SurfaceMuted)
val SplitCruiserSurfaceTrack = Color(Tokens.SurfaceTrack)

// --- Brand ---
val SplitCruiserPrimary = Color(Tokens.Primary)
val SplitCruiserPrimaryContainer = Color(Tokens.PrimaryContainer)
val SplitCruiserOnPrimaryContainer = Color(Tokens.OnPrimaryContainer)
val SplitCruiserOnPrimary = Color(Tokens.OnPrimary)

// --- Semantic status ---
val SplitCruiserSuccess = Color(Tokens.Success)
val SplitCruiserDanger = Color(Tokens.Danger)
val SplitCruiserInfo = Color(Tokens.Info)
val SplitCruiserWarning = Color(Tokens.Warning)
val SplitCruiserAccent = Color(Tokens.Accent)

// --- Text and lines ---
val SplitCruiserTextPrimary = Color(Tokens.TextPrimary)
val SplitCruiserTextSecondary = Color(Tokens.TextSecondary)
val SplitCruiserOutline = Color(Tokens.Outline)
