package com.splitcruiser.app.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitcruiser.app.ui.theme.SplitCruiserScale as Scale

/**
 * The spacing, radius and type steps screens are expected to use, wrapping [SplitCruiserScale].
 *
 * Before this existed, every padding, corner radius and font size in `SplitCruiserApp.kt` was a
 * bare literal, and visually-equivalent elements did not match: a text field was `14.dp` rounded
 * while the submit button below it was `12.dp`, and section eyebrow labels drifted between 10 and
 * 11sp across screens.
 */
object SplitCruiserSpacing {
    val Xs = Scale.SpaceXs.dp
    val Sm = Scale.SpaceSm.dp
    val Md = Scale.SpaceMd.dp
    val Lg = Scale.SpaceLg.dp
    val Xl = Scale.SpaceXl.dp
    val Xxl = Scale.SpaceXxl.dp
}

/** One radius per family of control — chips, then buttons/fields, then cards. */
object SplitCruiserRadius {
    val Sm = Scale.RadiusSm.dp
    val Md = Scale.RadiusMd.dp
    val Lg = Scale.RadiusLg.dp
    val Pill = Scale.RadiusPill.dp
}

object SplitCruiserTextSize {
    val Eyebrow = Scale.TextEyebrow.sp
    val Caption = Scale.TextCaption.sp
    val Body = Scale.TextBody.sp
    val Title = Scale.TextTitle.sp
    val Headline = Scale.TextHeadline.sp
}
