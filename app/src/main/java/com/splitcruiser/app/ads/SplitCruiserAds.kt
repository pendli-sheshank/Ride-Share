package com.splitcruiser.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.splitcruiser.app.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The whole AdMob surface for Android, in one file.
 *
 * Ads are the second and last place this app uses a native Google SDK; the backend is still REST
 * in `:shared` (see CLAUDE.md). There is no REST path for ad serving — an ad has to be requested
 * and rendered by the platform SDK — which is the same reason push needed one.
 *
 * Two things this file exists to get right, both of which fail silently or catastrophically
 * otherwise:
 *
 * 1. **Consent before initialisation.** Serving a personalised ad in the EEA/UK without a consent
 *    decision is a policy violation, and the SDK will happily do it if initialised first. The
 *    manifest sets `DELAY_APP_MEASUREMENT_INIT` so nothing self-initialises at process start, and
 *    [ensureConsentThenInitialize] is the only call that starts the SDK.
 * 2. **An unconfigured build shows nothing.** Without `ADMOB_APP_ID`, the build falls back to
 *    Google's sample id so the app can launch at all — and [isEnabled] is false, so no slot is
 *    drawn. Test ads occupying real layout in a developer build is how a screenshot ends up in a
 *    store listing with "Test Ad" across it.
 */
object SplitCruiserAds {

    private const val TAG = "SplitCruiserAds"

    /** Google's public test units. Documented for exactly this, and safe to click. */
    private const val TEST_BANNER_UNIT = "ca-app-pub-3940256099942544/6300978111"

    private val started = AtomicBoolean(false)

    /**
     * Whether this build has a real AdMob configuration.
     *
     * False when `ADMOB_APP_ID` was never supplied and the build fell back to the sample id. Every
     * placement is gated on this, so an unconfigured build renders the feed exactly as it did
     * before ads existed — no empty slot, no reserved height, no "Test Ad".
     */
    val isEnabled: Boolean
        get() = BuildConfig.ADMOB_APP_ID != BuildConfig.ADMOB_SAMPLE_APP_ID &&
            BuildConfig.ADMOB_APP_ID.isNotBlank()

    /** The banner unit to request, or the test unit in a debug build. */
    val bannerUnitId: String
        get() = BuildConfig.ADMOB_BANNER_UNIT_ID.takeIf { it.isNotBlank() && !BuildConfig.DEBUG }
            ?: TEST_BANNER_UNIT

    /**
     * The in-feed unit. Also a banner — see the note on [feedUnitId] in the PR that added this:
     * an adaptive banner inside a labelled card gives the same sponsored-card result as a true
     * NativeAd without asset-view registration, which is the main source of policy violations.
     */
    val feedUnitId: String
        get() = BuildConfig.ADMOB_FEED_UNIT_ID.takeIf { it.isNotBlank() && !BuildConfig.DEBUG }
            ?: TEST_BANNER_UNIT

    /**
     * Resolves consent, then starts the ads SDK. Safe to call repeatedly; only the first call does
     * anything.
     *
     * Needs an `Activity` rather than a `Context` because the consent form is a dialog. Called
     * from the dashboard rather than at launch, for the same reason the notification permission is:
     * a consent sheet over a login screen has no context, and this one cannot be re-shown casually.
     *
     * Never throws. A consent or initialisation failure means no ads, which is the same state
     * every build starts in — not a reason to break a working app.
     */
    fun ensureConsentThenInitialize(activity: Activity) {
        if (!isEnabled) return
        if (!started.compareAndSet(false, true)) return

        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val parameters = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .apply {
                if (BuildConfig.DEBUG) {
                    // Without this a developer outside the EEA never sees the form and cannot tell
                    // whether it works. Debug-only: forcing a geography in release would show the
                    // form to people it does not apply to.
                    setConsentDebugSettings(
                        ConsentDebugSettings.Builder(activity)
                            .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                            .build()
                    )
                }
            }
            .build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            parameters,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form: ${formError.message}")
                    }
                    // Initialise either way. `canRequestAds` is what decides whether a *personalised*
                    // ad may be served; a user who declined still gets non-personalised ads, which
                    // is the documented behaviour and why this is not gated on the form succeeding.
                    initializeIfPermitted(activity, consentInformation)
                }
            },
            { requestError ->
                Log.w(TAG, "Consent info update failed: ${requestError.message}")
                // A network failure here must not permanently disable ads, but it also must not
                // initialise without a decision. Allow a later attempt.
                started.set(false)
            },
        )
    }

    private fun initializeIfPermitted(context: Context, consent: ConsentInformation) {
        if (!consent.canRequestAds()) {
            Log.i(TAG, "Consent does not permit ad requests; not initialising.")
            return
        }
        runCatching { MobileAds.initialize(context) }
            .onFailure { Log.w(TAG, "Ads SDK failed to initialise", it) }
    }

    /**
     * Whether a slot should actually be drawn right now.
     *
     * [isEnabled] says the build is configured; this says the SDK is up and consent allows a
     * request. A slot drawn before that reserves height for an ad that never arrives, which is a
     * gap in the feed rather than an advert.
     */
    fun canShowAds(context: Context): Boolean =
        isEnabled &&
            started.get() &&
            runCatching {
                UserMessagingPlatform.getConsentInformation(context).canRequestAds()
            }.getOrDefault(false)
}
