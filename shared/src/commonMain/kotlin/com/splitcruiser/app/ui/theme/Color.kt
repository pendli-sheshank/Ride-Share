package com.splitcruiser.app.ui.theme

/**
 * The one place the brand's colours are written down.
 *
 * Plain ARGB longs rather than Compose `Color` objects, because this module compiles for iOS too:
 * Android wraps these in `Color(...)`, iOS wraps them in a SwiftUI `Color` (see `Theme.swift`).
 * Both platforms read this file, so a palette change lands on both at once.
 *
 * The names describe what the value *renders as*. They used to describe a saffron/indigo brand
 * that the hex values never matched — `Saffron` was `0xFF0061A4`, a medium blue, and `DarkBg` was
 * `0xFFF8F9FF`, a near-white. Everyone reading the code had to hold that translation in their head,
 * and one screen's author did not: `ProfileScreen` painted white text onto a card it had already
 * set to white.
 */
object SplitCruiserColors {
    // --- Surfaces ---
    /** App background. Near-white, cool-tinted. */
    const val Surface = 0xFFF8F9FF
    /** Cards and sheets sitting on [Surface]. */
    const val SurfaceCard = 0xFFFFFFFF
    /** Faintly tinted surface for chips and inset rows. */
    const val SurfaceMuted = 0xFFEEF1FF
    /** The unselected track behind a segmented control. */
    const val SurfaceTrack = 0xFFE1E2EC

    // --- Brand ---
    /** Primary action colour: buttons, active tabs, links. Material 3 baseline blue. */
    const val Primary = 0xFF0061A4
    /** The tonal container that pairs with [Primary]. */
    const val PrimaryContainer = 0xFFD1E4FF
    /** Text and icons drawn on top of [PrimaryContainer]. */
    const val OnPrimaryContainer = 0xFF001D36
    /** Text and icons drawn on top of [Primary]. */
    const val OnPrimary = 0xFFFFFFFF

    // --- Semantic status ---
    const val Success = 0xFF10B981
    const val Danger = 0xFFEF4444
    const val Info = 0xFF3B82F6
    const val Warning = 0xFFEAB308
    /** The women-only safety filter's accent. */
    const val Accent = 0xFFE91E63

    // --- Text and lines ---
    const val TextPrimary = 0xFF0F172A
    const val TextSecondary = 0xFF64748B
    const val Outline = 0xFFE2E8F0
}

/**
 * The spacing, radius and type steps the UI is allowed to use.
 *
 * Values are unitless numbers: Android reads them as `dp`/`sp`, iOS as points. Before this existed
 * every padding and corner radius was an ad-hoc literal, so a text field was 14 and the button
 * underneath it was 12 for no reason anyone could name.
 */
object SplitCruiserScale {
    // Spacing, a 4-point grid.
    const val SpaceXs = 4
    const val SpaceSm = 8
    const val SpaceMd = 12
    const val SpaceLg = 16
    const val SpaceXl = 24
    const val SpaceXxl = 32

    // Corner radii, one per family of control.
    /** Chips, badges and other small pills. */
    const val RadiusSm = 8
    /** Buttons and text fields — the same value, deliberately. */
    const val RadiusMd = 12
    /** Cards, sheets and dialogs. */
    const val RadiusLg = 16
    /** Fully rounded: FABs, avatars, the message input. */
    const val RadiusPill = 999

    // Type scale.
    /** All-caps eyebrow labels above a section. */
    const val TextEyebrow = 11
    const val TextCaption = 12
    const val TextBody = 14
    const val TextTitle = 16
    const val TextHeadline = 20
}
