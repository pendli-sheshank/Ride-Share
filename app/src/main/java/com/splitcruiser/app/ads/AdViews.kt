package com.splitcruiser.app.ads

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.splitcruiser.app.ui.theme.SplitCruiserOutline
import com.splitcruiser.app.ui.theme.SplitCruiserRadius
import com.splitcruiser.app.ui.theme.SplitCruiserSpacing
import com.splitcruiser.app.ui.theme.SplitCruiserSurfaceCard
import com.splitcruiser.app.ui.theme.SplitCruiserTextSecondary

/**
 * The two ad surfaces, as Compose.
 *
 * Both are adaptive banners. The in-feed one sits inside a card with a visible "Ad" label, which
 * is what makes it distinguishable from the bookable rides around it — AdMob policy requires that,
 * and in a feed of ride cards a sponsored card that looks like a ride is the exact confusion the
 * rule exists for.
 *
 * Every composable here returns nothing at all when [SplitCruiserAds.canShowAds] is false. Not an
 * empty box with reserved height — nothing. An unconfigured or pre-consent build renders the feed
 * exactly as it did before ads existed.
 */

/** Width-adaptive banner height and request, derived from the current screen width. */
@Composable
private fun rememberAdaptiveAdSize(): AdSize {
    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    return remember(widthDp) {
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
    }
}

/**
 * Builds and owns an [AdView]'s lifecycle.
 *
 * `DisposableEffect` destroying the view is load-bearing: an `AdView` that is not destroyed leaks
 * its activity, and on a screen the user rotates or navigates away from repeatedly that is a
 * steadily growing leak rather than a one-off.
 */
@Composable
private fun BannerAd(unitId: String, adSize: AdSize, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adView = remember(unitId, adSize) {
        AdView(context).apply {
            adUnitId = unitId
            setAdSize(adSize)
            loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx: Context ->
            FrameLayout(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                )
                addView(adView)
            }
        },
    )
}

/**
 * The anchored banner above the bottom navigation bar, on the browse feed only.
 *
 * Not on chat, ride detail or the post forms: chat is where a pickup is being agreed with a
 * stranger, and ride detail carries the host's contact and safety information. Neither is a
 * surface to monetise.
 */
@Composable
fun AnchoredFeedBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (!SplitCruiserAds.canShowAds(context)) return
    val adSize = rememberAdaptiveAdSize()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(adSize.height.dp),
        contentAlignment = Alignment.Center,
    ) {
        BannerAd(unitId = SplitCruiserAds.bannerUnitId, adSize = adSize)
    }
}

/**
 * A sponsored card in the feed, at the positions `AdSlotting.adSlotIndices` chooses.
 *
 * The "Ad" label is not decoration. These sit between cards a rider can tap to book a seat, so the
 * one thing this card must never do is read as a ride.
 */
@Composable
fun FeedAdCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (!SplitCruiserAds.canShowAds(context)) return
    val adSize = rememberAdaptiveAdSize()

    Card(
        colors = CardDefaults.cardColors(containerColor = SplitCruiserSurfaceCard),
        shape = RoundedCornerShape(SplitCruiserRadius.Lg),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, SplitCruiserOutline, RoundedCornerShape(SplitCruiserRadius.Lg)),
    ) {
        Column(modifier = Modifier.padding(SplitCruiserSpacing.Md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                Box(
                    modifier = Modifier
                        .border(1.dp, SplitCruiserOutline, RoundedCornerShape(SplitCruiserRadius.Sm))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "Ad",
                        color = SplitCruiserTextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.width(SplitCruiserSpacing.Sm))
                Text(
                    text = "Sponsored",
                    color = SplitCruiserTextSecondary,
                    fontSize = 11.sp,
                )
            }
            Spacer(modifier = Modifier.height(SplitCruiserSpacing.Sm))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(adSize.height.dp),
                contentAlignment = Alignment.Center,
            ) {
                BannerAd(unitId = SplitCruiserAds.feedUnitId, adSize = adSize)
            }
        }
    }
}
