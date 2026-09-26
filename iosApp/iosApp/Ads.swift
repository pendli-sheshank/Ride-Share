import AppTrackingTransparency
import GoogleMobileAds
import SwiftUI
import UIKit
import UserMessagingPlatform

/// The whole AdMob surface for iOS, in one file.
///
/// Mirrors `app/src/main/java/com/splitcruiser/app/ads/SplitCruiserAds.kt` and `AdViews.kt`. Ads
/// are the second and last place either platform uses a native Google SDK; the backend is still
/// REST in `:shared`. There is no REST path for ad serving — an ad has to be requested and
/// rendered by the platform SDK — which is the same reason push needed one.
///
/// Three things this file exists to get right, each of which otherwise fails silently or
/// catastrophically:
///
/// 1. **Consent, then tracking, then the SDK.** UMP first, `ATTrackingManager` second,
///    `MobileAds.shared.start` last. That is Google's documented sequence, and backwards means
///    asking for tracking permission before a consent decision exists.
/// 2. **An unconfigured build shows nothing.** While `GADApplicationIdentifier` is Google's
///    sample id, `isEnabled` is false and no slot is drawn at all — not an empty box, not
///    reserved height. Same sentinel discipline as `SplitCruiserPush.isConfigured`.
/// 3. **Ad unit ids are per-app, so per-platform.** The ids here come from `Info.plist`, written
///    by the release workflow from the iOS-only `ADMOB_IOS_*` secrets. An iOS app requesting an
///    Android unit id gets no fill — not an error, just a slot that never fills, forever.
///
/// The Kotlin/Swift API names of the ads SDK cannot be compile-checked on Linux, the same
/// position `Theme.swift` is in. They are all in this one file for that reason.

// MARK: - Gate

/// Whether ads may be drawn right now, as something SwiftUI can observe.
///
/// Android reads `SplitCruiserAds.canShowAds(context)` on every recomposition and that is enough,
/// because the feed recomposes on each 20s poll anyway. SwiftUI does not re-render on a `static`
/// changing, so readiness has to be published — otherwise the first feed after consent keeps
/// rendering the pre-consent state until something unrelated invalidates it.
///
/// Deliberately **not** `@MainActor`, for the reason `PushTokenStore` is not: a SwiftUI View's
/// property initialiser is a non-isolated context, and reading a main-actor-isolated `static` from
/// there is a warning under Swift 5 and an error under Swift 6. The hop happens inside
/// `markReady` instead, which is where it matters — the SDK's callbacks arrive on unspecified
/// queues and `@Published` must be mutated on the main thread.
final class AdsGate: ObservableObject {

    static let shared = AdsGate()

    /// True once consent permits ad requests *and* the ads SDK has started.
    ///
    /// Both halves are required. A slot drawn before the SDK is up reserves height for an ad that
    /// never arrives, which reads as a gap in the feed rather than an advert.
    @Published private(set) var isReady = false

    private init() {}

    func markReady() {
        DispatchQueue.main.async { [weak self] in
            guard let self, !self.isReady else { return }
            self.isReady = true
        }
    }
}

// MARK: - Configuration and start-up

enum SplitCruiserAds {

    /// Google's public sample **iOS** application id, which is what `Info.plist` ships with.
    ///
    /// Committed rather than left absent because a missing or malformed `GADApplicationIdentifier`
    /// makes `MobileAds.shared.start` raise `GADInvalidInitializationException`, and the PR
    /// simulator build has no secrets. It doubles as the sentinel [isEnabled] tests. Must match
    /// the value in `Info.plist`.
    static let sampleAppIdSentinel = "ca-app-pub-3940256099942544~1458002511"

    /// Google's public test banner unit for iOS. Documented for exactly this, and safe to click.
    private static let testBannerUnit = "ca-app-pub-3940256099942544/2934735716"

    private static let bannerUnitKey = "SCAdBannerUnitId"
    private static let feedUnitKey = "SCAdFeedUnitId"

    private static var applicationIdentifier: String {
        (Bundle.main.object(forInfoDictionaryKey: "GADApplicationIdentifier") as? String) ?? ""
    }

    /// Whether this build has a real AdMob configuration.
    ///
    /// False when `ADMOB_IOS_APP_ID` was never supplied and the bundle still carries the sample
    /// id. Every placement is gated on this, so an unconfigured build renders the feed exactly as
    /// it did before ads existed. Test ads occupying real layout in a developer build is how a
    /// screenshot ends up in a store listing with "Test Ad" across it.
    static var isEnabled: Bool {
        let id = applicationIdentifier
        return !id.isEmpty && id != sampleAppIdSentinel
    }

    /// The banner unit to request, or Google's test unit in a debug build.
    static var bannerUnitId: String { configuredUnit(bannerUnitKey) }

    /// The in-feed unit. Also a banner: an adaptive banner inside a labelled card gives the same
    /// sponsored-card result as a true native ad without per-asset view registration, which is
    /// the main source of policy violations. Android takes the same position.
    static var feedUnitId: String { configuredUnit(feedUnitKey) }

    private static func configuredUnit(_ key: String) -> String {
        #if DEBUG
        // Always the test unit in debug, whatever is configured: a developer clicking live
        // inventory is how an AdMob account gets suspended.
        return testBannerUnit
        #else
        let configured = (Bundle.main.object(forInfoDictionaryKey: key) as? String) ?? ""
        return configured.isEmpty ? testBannerUnit : configured
        #endif
    }

    /// Guards [ensureConsentThenStart] against the second call.
    ///
    /// `@MainActor` rather than a bare `static var`: this is mutable global state touched from
    /// SDK completion handlers, which arrive on unspecified queues. Pinning it to the main actor
    /// is both correct and what keeps it from being a Swift 6 concurrency error — every caller is
    /// a SwiftUI lifecycle hook already, and the UMP form must be presented from the main thread
    /// regardless.
    @MainActor
    private static var hasStarted = false

    /// Resolves consent, asks for tracking permission, then starts the ads SDK.
    ///
    /// Safe to call repeatedly; only the first call does anything. Called on reaching the
    /// dashboard rather than at launch, for the same reason the notification prompt is: a consent
    /// sheet over a login screen has no context, and this one cannot be re-shown casually.
    ///
    /// Never throws and never reports failure to the user. A consent or start-up failure means no
    /// ads, which is the state every build begins in — not a reason to interrupt a working app.
    @MainActor
    static func ensureConsentThenStart() {
        guard isEnabled, !Self.hasStarted else { return }
        Self.hasStarted = true

        // An empty parameters object on purpose. Android sets `tagForUnderAgeOfConsent(false)`
        // explicitly, but that is already UMP's default and Split Cruiser is not a child-directed
        // app — so setting it here would add nothing except one more SDK property name that
        // cannot be compile-checked on Linux.
        //
        // Also deliberately absent: Android's debug-only `DebugGeography.EEA` override, which lets
        // a developer outside the EEA see the consent form. On iOS that additionally requires
        // registering a test device hash, and none of it can be verified from here. The asymmetry
        // is known; the iOS form has to be checked on a real device in the EEA or with debug
        // settings added locally.
        let parameters = RequestParameters()

        ConsentInformation.shared.requestConsentInfoUpdate(with: parameters) { error in
            if let error {
                // A network failure here must not permanently disable ads, but it also must not
                // start the SDK without a decision. Allow a later attempt.
                NSLog("[SplitCruiserAds] Consent info update failed: \(error.localizedDescription)")
                Task { @MainActor in Self.hasStarted = false }
                return
            }
            Task { @MainActor in Self.presentFormThenStart() }
        }
    }

    @MainActor
    private static func presentFormThenStart() {
        ConsentForm.loadAndPresentIfRequired(from: nil) { formError in
            if let formError {
                NSLog("[SplitCruiserAds] Consent form: \(formError.localizedDescription)")
            }
            // Continue either way. `canRequestAds` is what decides whether a *personalised* ad
            // may be served; someone who declined still gets non-personalised ads, which is the
            // documented behaviour and why this is not gated on the form succeeding.
            Task { @MainActor in Self.requestTrackingThenStart() }
        }
    }

    /// ATT **after** UMP and **before** the SDK starts.
    ///
    /// Google documents this order. ATT governs access to the IDFA; UMP governs consent to
    /// personalised advertising. Asking for the identifier before the user has been told what it
    /// is for is both the wrong order and a worse prompt.
    @MainActor
    private static func requestTrackingThenStart() {
        ATTrackingManager.requestTrackingAuthorization { _ in
            // The outcome deliberately changes nothing here. Denied tracking means Google serves
            // without the IDFA and attributes through SKAdNetwork; it does not mean no ads.
            Task { @MainActor in Self.startIfPermitted() }
        }
    }

    @MainActor
    private static func startIfPermitted() {
        guard ConsentInformation.shared.canRequestAds else {
            NSLog("[SplitCruiserAds] Consent does not permit ad requests; not starting the SDK.")
            Self.hasStarted = false
            return
        }
        MobileAds.shared.start { _ in
            // The status object lists per-adapter readiness. With no mediation adapters there is
            // nothing here to act on, and a partially ready SDK still serves Google's own demand.
            AdsGate.shared.markReady()
        }
    }
}

// MARK: - The banner, as UIKit

/// Hosts a `BannerView` and owns its lifecycle.
///
/// A `UIView` subclass rather than a bare `BannerView` because the banner needs a
/// `rootViewController`, and at `makeUIView` time a SwiftUI-hosted view is not in a window yet.
/// Loading with a nil `rootViewController` fails the request outright, so the load waits for
/// `didMoveToWindow`.
final class BannerAdContainer: UIView {

    private var bannerView: BannerView?
    private var pendingUnitId: String?
    private var pendingWidth: CGFloat = 0
    /// What has already been loaded, so a SwiftUI re-render does not request a second ad for the
    /// same unit at the same width. Every load is a billable request.
    private var loadedKey: String?

    func apply(unitId: String, width: CGFloat) {
        guard pendingUnitId != unitId || pendingWidth != width else { return }
        pendingUnitId = unitId
        pendingWidth = width
        // Deferred to the next main-queue turn rather than loading inline. `apply` is called from
        // `UIViewRepresentable.sizeThatFits`, which runs *during* a layout pass, and adding a
        // subview with constraints from inside layout is how a layout loop starts.
        DispatchQueue.main.async { [weak self] in self?.loadIfPossible() }
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        loadIfPossible()
    }

    private func loadIfPossible() {
        guard let unitId = pendingUnitId, pendingWidth > 0, let root = hostViewController else {
            return
        }
        let key = "\(unitId)@\(Int(pendingWidth.rounded()))"
        guard key != loadedKey else { return }
        loadedKey = key

        bannerView?.removeFromSuperview()

        let banner = BannerView(adSize: largeAnchoredAdaptiveBanner(width: pendingWidth))
        banner.adUnitID = unitId
        banner.rootViewController = root
        banner.translatesAutoresizingMaskIntoConstraints = false
        addSubview(banner)
        NSLayoutConstraint.activate([
            banner.centerXAnchor.constraint(equalTo: centerXAnchor),
            banner.centerYAnchor.constraint(equalTo: centerYAnchor),
        ])
        banner.load(Request())
        bannerView = banner
    }

    private var hostViewController: UIViewController? {
        if let fromWindow = window?.rootViewController { return fromWindow }
        return UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }?
            .rootViewController
    }
}

/// A width-adaptive banner that sizes its own slot.
///
/// `sizeThatFits` is where the width arrives, and it is the reason this needs no `GeometryReader`
/// and no `UIScreen.main.bounds`: SwiftUI proposes the real width during layout, which is both the
/// width to request the ad at and the width the banner's height is derived from. Reporting that
/// height means the slot is the right size *before* the ad arrives, instead of growing under the
/// reader's thumb when it does.
///
/// `largeAnchoredAdaptiveBanner(width:)` is the current SDK's anchored format.
/// `currentOrientationAnchoredAdaptiveBanner` was its predecessor and no longer exists in Swift —
/// only as the legacy Objective-C `GADCurrentOrientationAnchoredAdaptiveBannerAdSizeWithWidth`.
/// The height comes from the returned `AdSize`, so switching formats needs no layout change here.
private struct BannerAd: UIViewRepresentable {
    let unitId: String

    func makeUIView(context: Context) -> BannerAdContainer {
        BannerAdContainer()
    }

    /// Nothing to do here: the width is only known during layout, so the load is kicked off from
    /// `sizeThatFits` and retried by the container once it has a window.
    func updateUIView(_ container: BannerAdContainer, context: Context) {}

    func sizeThatFits(
        _ proposal: ProposedViewSize,
        uiView container: BannerAdContainer,
        context: Context
    ) -> CGSize? {
        guard let width = proposal.width, width > 0, width.isFinite else { return nil }
        // Idempotent: the container ignores a repeat of the same unit at the same width, and
        // layout runs many times more often than an ad should be requested.
        container.apply(unitId: unitId, width: width)
        return CGSize(
            width: width,
            height: largeAnchoredAdaptiveBanner(width: width).size.height
        )
    }
}

// MARK: - The two placements

/// The anchored banner above the dashboard tab bar, on the browse feed only.
///
/// Not on chat, ride detail or the post forms: chat is where a pickup is being agreed with a
/// stranger, and ride detail carries the host's contact and safety information. Neither is a
/// surface to monetise. `AdSlotting` in `:shared` decides the in-feed positions; this one is
/// structural, and mirrors Android's `bottomBar`.
struct AnchoredFeedBanner: View {

    @ObservedObject private var gate = AdsGate.shared

    var body: some View {
        if SplitCruiserAds.isEnabled && gate.isReady {
            BannerAd(unitId: SplitCruiserAds.bannerUnitId)
                .frame(maxWidth: .infinity)
        }
    }
}

/// A sponsored card in the feed, at the positions `AdSlotting.adSlotIndices` chooses.
///
/// The "Ad" label is not decoration. These sit between cards a rider taps to book a seat, so the
/// one thing this card must never do is read as a ride — AdMob policy requires an ad be
/// distinguishable from content, and this is precisely the case the rule exists for.
struct FeedAdCard: View {

    @ObservedObject private var gate = AdsGate.shared

    var body: some View {
        if SplitCruiserAds.isEnabled && gate.isReady {
            // Hand-rolled rather than wrapped in `BrandCard`, which is otherwise the card to
            // reuse: `BrandCard` pads at `spaceLg`, and Android's `FeedAdCard` pads at `Md`.
            // Matching Android matters more here than matching the other iOS cards, because this
            // is one of the screens the parity checklist compares side by side.
            VStack(alignment: .leading, spacing: BrandScale.spaceSm) {
                HStack(spacing: BrandScale.spaceSm) {
                    Text("Ad")
                        .font(BrandFont.fixed(10, .bold))
                        .foregroundColor(Brand.textSecondary)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .overlay(
                            RoundedRectangle(cornerRadius: BrandScale.radiusSm)
                                .stroke(Brand.outline, lineWidth: 1)
                        )
                    Text("Sponsored")
                        .font(BrandFont.fixed(11))
                        .foregroundColor(Brand.textSecondary)
                    Spacer()
                }
                BannerAd(unitId: SplitCruiserAds.feedUnitId)
                    .frame(maxWidth: .infinity)
            }
            .padding(BrandScale.spaceMd)
            .background(Brand.surfaceCard)
            .cornerRadius(BrandScale.radiusLg)
            .overlay(
                RoundedRectangle(cornerRadius: BrandScale.radiusLg)
                    .stroke(Brand.outline, lineWidth: 1)
            )
        }
    }
}
