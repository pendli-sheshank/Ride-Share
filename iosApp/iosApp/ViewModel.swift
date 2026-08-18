import CoreLocation
import Foundation
import Shared

// MARK: - iOS bridge to the shared backend

/// Drives the SwiftUI layer from `SplitCruiserRepository` in `:shared`.
///
/// This used to be a stub over local `@Published` arrays that vanished on relaunch, because the
/// repository was Android-only — it took an `android.content.Context` and drove the native Firebase
/// SDKs. The backend now speaks Firebase's REST APIs from `commonMain`, so this file calls exactly
/// the same code Android does.
///
/// Two interop rules the shared API is shaped around:
/// - Kotlin `suspend` maps to Swift `async`, and the shared functions are annotated `@Throws`, so
///   failures arrive as Swift errors rather than terminating the process.
/// - `Flow` is not usable from Swift, so state arrives through `observe*` callbacks that hand back
///   a `FlowSubscription` to cancel. Those callbacks are delivered on the main queue.
@MainActor
final class AppViewModel: ObservableObject {

    @Published var currentUser: User?
    @Published var activeOffers: [TripOffer] = []
    @Published var activeRequests: [RideRequest] = []
    @Published var myRideRequests: [RideRequest] = []
    @Published var userMatches: [TripMatch] = []
    @Published var isConnected = false
    @Published var isLoading = false
    @Published var errorMessage: String?

    /// Whether a pull-to-refresh is in flight.
    ///
    /// Kept separate from `isLoading` because they drive different things: `isLoading` raises a
    /// modal full-screen overlay, and routing a background refresh through it meant a pull-to-
    /// refresh dimmed the whole app and — since `perform` clears `errorMessage` on entry — could
    /// erase an error the user had not read yet. Android has always split these
    /// (`_isRefreshing` vs `_isLoading`).
    @Published var isRefreshing = false

    /// What the loading overlay says it is doing. Android parameterises the same string.
    /// "Securing your ride…" belongs to reserving a seat, not to logging in.
    @Published var loadingMessage = AppViewModel.defaultLoadingMessage

    /// The transient confirmation shown by `ToastHost`, standing in for Android's `Toast`.
    @Published var transientMessage: String?

    /// Which side of the marketplace Explore is showing. Android's `currentMode`.
    @Published var mode: RideMode = .rider

    /// `nonisolated` because it is used as a default argument value, and default arguments are
    /// evaluated in the caller's context — which for a `@MainActor` class member is not
    /// necessarily the main actor. Without this it is a warning today and an error under the
    /// Swift 6 language mode.
    nonisolated static let defaultLoadingMessage = "Just a moment…"

    /// The current user's own hosted/joined rides. `activeOffers` deliberately excludes a host's
    /// own rides (`FeedProjector` filters `hostId != currentUserId` so a host doesn't see their own
    /// ride in the browse feed), so deriving "my rides" from it — as this used to — always came up
    /// empty. `fetchMyTrips()` reads the unfiltered `hostId`/`passengers` queries directly, the same
    /// source Android's `MainViewModel.hostedRides`/`joinedRides` use.
    @Published var hostedRides: [TripOffer] = []
    @Published var joinedRides: [TripOffer] = []

    /// In-app alerts (not real push — see `toggleEmailNotifications`/`togglePushNotifications`).
    @Published var notifications: [NotificationAlert] = []

    /// Where the user lives, so a ride request can prefill its pickup. iOS never collected this
    /// before, which is why its post-request form started blank while Android's arrived filled in.
    @Published var contactDetails: ContactDetails?

    /// True once a profile exists; the UI routes to profile setup when it does not.
    @Published var needsProfileSetup = false

    let repository: SplitCruiserRepository
    private var subscriptions: [FlowSubscription] = []

    init() {
        repository = SplitCruiserRepository(
            config: FirebaseConfig.companion.fromBuild(),
            // Keychain-backed: the session (with the long-lived refresh token) is stored in the
            // Keychain, not a plaintext plist that ends up in unencrypted backups.
            store: KeychainStore()
        )

        subscriptions.append(repository.observeCurrentUser { [weak self] user in
            self?.currentUser = user
            // Mirrors Android's `currentUser.collect { ... refreshMyTrips() }` (MainViewModel.kt)
            // so hosted/joined rides load right after login, not only on a manual pull-to-refresh.
            if user != nil {
                Task { await self?.refreshMyTrips() }
            }
        })
        subscriptions.append(repository.observeActiveOffers { [weak self] offers in
            self?.activeOffers = offers
        })
        subscriptions.append(repository.observeActiveRequests { [weak self] requests in
            self?.activeRequests = requests
        })
        subscriptions.append(repository.observeMyRideRequests { [weak self] requests in
            self?.myRideRequests = requests
        })
        subscriptions.append(repository.observeUserMatches { [weak self] matches in
            self?.userMatches = matches
        })
        subscriptions.append(repository.observeConnection { [weak self] connected in
            // A Bool in a generic position arrives as KotlinBoolean.
            self?.isConnected = connected.boolValue
        })
        subscriptions.append(repository.observeContactDetails { [weak self] details in
            self?.contactDetails = details
        })
        subscriptions.append(repository.observeNotifications { [weak self] alerts in
            self?.notifications = alerts
        })

        // Restores a stored session and starts the polling refreshers.
        repository.start()
    }

    deinit {
        subscriptions.forEach { $0.cancel() }
    }

    var isSignedIn: Bool { currentUser != nil }

    /// False when this build has no Firebase configuration, so the UI can say so plainly rather
    /// than looking broken.
    var isBackendConfigured: Bool { repository.isFirebaseEnabled }

    // MARK: - Authentication

    func logIn(email: String, password: String) async {
        await perform {
            let needsProfile = try await self.repository.logInWithEmail(email: email, password: password)
            self.needsProfileSetup = needsProfile.boolValue
        }
    }

    func signUp(email: String, password: String) async {
        await perform {
            let needsProfile = try await self.repository.signUpWithEmail(email: email, password: password)
            self.needsProfileSetup = needsProfile.boolValue
        }
    }

    /// Onboarding, now collecting what Android has always collected: a contact number, a home
    /// address, and optionally a vehicle.
    ///
    /// Without the phone number, an Android rider matched with an iOS-onboarded host opened the
    /// contact card to a blank row; without the address, this app could not prefill a pickup.
    func completeProfile(
        name: String,
        lastInitial: String,
        homeArea: String,
        phoneNumber: String,
        homeAddress: String,
        homeLat: Double,
        homeLng: Double,
        vehicle: Vehicle?
    ) async {
        await perform("Setting up your profile…") {
            try await self.repository.createUserProfile(
                name: name,
                lastInitial: lastInitial,
                homeArea: homeArea,
                contact: ContactDetails(
                    phoneNumber: phoneNumber,
                    homeAddress: homeAddress,
                    homeLat: homeLat,
                    homeLng: homeLng
                ),
                vehicle: vehicle
            )
            self.needsProfileSetup = false
        }
    }

    func sendPasswordReset(email: String) async {
        await perform { try await self.repository.sendPasswordReset(email: email) }
    }

    func logOut() {
        repository.logout()
        needsProfileSetup = false
    }

    // MARK: - Posting rides

    func postRideOffer(
        origin: String,
        destination: String,
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        departureTime: Date,
        totalSeats: Int,
        costPerRider: Double,
        womenOnly: Bool,
        vehicleInfo: String,
        exitLocation: String = ""
    ) async -> Bool {
        await perform("Posting your ride offer…") {
            let offer = RideFactory.shared.makeTripOffer(
                origin: origin,
                destination: destination,
                originLat: originLat,
                originLng: originLng,
                destLat: destLat,
                destLng: destLng,
                departureTime: departureTime.epochMillis,
                totalSeats: Int32(totalSeats),
                costPerRider: costPerRider,
                womenOnly: womenOnly,
                vehicleInfo: vehicleInfo,
                exitLocation: exitLocation
            )
            try await self.repository.postTripOffer(offer: offer)
        }
    }

    func postRideRequest(
        origin: String,
        destination: String,
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        departureTime: Date,
        seatsNeeded: Int,
        notes: String,
        womenOnly: Bool,
        exitLocation: String = ""
    ) async -> Bool {
        await perform("Posting your ride request…") {
            let request = RideFactory.shared.makeRideRequest(
                origin: origin,
                destination: destination,
                originLat: originLat,
                originLng: originLng,
                destLat: destLat,
                destLng: destLng,
                departureTime: departureTime.epochMillis,
                seatsNeeded: Int32(seatsNeeded),
                notes: notes,
                womenOnly: womenOnly,
                exitLocation: exitLocation
            )
            try await self.repository.postRideRequest(request: request)
        }
    }

    // MARK: - Joining and matching

    func joinRide(offerId: String) async -> Bool {
        await perform("Securing your ride…") {
            try await self.repository.joinTripOfferDirect(offerId: offerId)
        }
    }

    func acceptMatch(matchId: String) async {
        await perform("Securing your ride…") {
            try await self.repository.acceptMatch(matchId: matchId)
        }
    }

    func declineMatch(matchId: String) async {
        await perform { try await self.repository.declineMatch(matchId: matchId) }
    }

    /// A driver taking a rider's request without having posted a ride of their own.
    ///
    /// The shared side mints the backing offer, which is what used to make this impossible: the
    /// host entry point needed an offer id, so a driver with nothing posted was told to "post a
    /// ride first" and left to do the app's bookkeeping.
    ///
    /// Returns the match rather than a `Bool` because the caller opens chat on it, so this manages
    /// its own loading and error state the way `offerSeat` does.
    func acceptRequestDirect(requestId: String, contribution: Double) async -> TripMatch? {
        loadingMessage = "Offering the seat…"
        isLoading = true
        errorMessage = nil
        defer {
            isLoading = false
            loadingMessage = AppViewModel.defaultLoadingMessage
        }
        do {
            return try await repository.acceptRideRequestDirect(
                requestId: requestId, contribution: contribution
            )
        } catch {
            errorMessage = error.localizedDescription
            return nil
        }
    }

    /// What to prefill the contribution field with. Never throws out to the caller — a missing
    /// suggestion leaves the field empty rather than blocking the accept.
    func suggestedContribution(for request: RideRequest) async -> Double {
        let suggested = try? await repository.suggestedContribution(request: request)
        return suggested?.doubleValue ?? 0
    }

    /// A host offering one of their own rides to an open request. Auto-accepts on the shared side,
    /// so the returned match can be opened straight into chat.
    ///
    /// Returns the match rather than a `Bool` because the caller navigates to it; `perform` can't
    /// carry a value out, so this handles its own loading and error state.
    func offerSeat(requestId: String, offerId: String, contribution: Double) async -> TripMatch? {
        loadingMessage = "Offering the seat…"
        isLoading = true
        errorMessage = nil
        defer {
            isLoading = false
            loadingMessage = AppViewModel.defaultLoadingMessage
        }
        do {
            return try await repository.offerSeatForRequest(
                requestId: requestId, offerId: offerId, contribution: contribution
            )
        } catch {
            errorMessage = error.localizedDescription
            return nil
        }
    }

    /// Switches the Explore feed between rider and host. Android's `switchMode`.
    func switchMode(_ newMode: RideMode) {
        mode = newMode
    }

    func refresh() async {
        await performRefresh { try await self.repository.refreshNow() }
    }

    /// Populates `hostedRides`/`joinedRides` from the unfiltered `hostId`/`passengers` queries —
    /// see the doc comment on those `@Published` properties for why `activeOffers` can't be used.
    func refreshMyTrips() async {
        await performRefresh {
            let trips = try await self.repository.fetchMyTrips()
            self.hostedRides = trips.hosted
            self.joinedRides = trips.joined
        }
    }

    // MARK: - Ride detail

    /// The host's public profile, for the trust information the detail screen shows before
    /// somebody commits to getting in a stranger's car. Reads the cache first; falls back to a
    /// fetch, which also populates the cache for next time.
    func hostProfile(userId: String) async -> User? {
        if let cached = repository.getUserPublicProfile(userId: userId) { return cached }
        return try? await repository.fetchUserProfile(userId: userId)
    }

    func vehicle(userId: String) async -> Vehicle? {
        if let cached = repository.getVehicleInfo(userId: userId) { return cached }
        return try? await repository.fetchVehicleInfo(userId: userId)
    }

    /// The signed-in user's vehicle, if they have registered one. A synchronous cache read, used
    /// to decide whether the post-offer form shows Android's "no vehicle set up" notice.
    var vehicleForCurrentUser: Vehicle? {
        guard let id = currentUser?.id else { return nil }
        return repository.getVehicleInfo(userId: id)
    }

    /// Cancelling one of the current user's own open ride requests.
    func updateRequestStatus(requestId: String, newStatus: String) async -> Bool {
        await perform("Cancelling your request…") {
            try await self.repository.updateRideRequestStatus(
                requestId: requestId, newStatus: newStatus
            )
        }
    }

    /// The host's two real decisions — see `HostControlsPolicy` in `:shared` for which statuses
    /// still allow them.
    func updateOfferStatus(offerId: String, newStatus: String) async -> Bool {
        await perform("Updating the ride…") {
            try await self.repository.updateTripOfferStatus(offerId: offerId, newStatus: newStatus)
        }
    }

    // MARK: - Chat

    /// Subscribes to one conversation. The returned handle must be cancelled when the screen
    /// goes away, and `openChat` tells the repository to poll this conversation quickly (3s)
    /// while it is on screen.
    func observeChat(matchId: String, onChange: @escaping ([Message]) -> Void) -> FlowSubscription {
        repository.openChat(matchId: matchId)
        return repository.observeChat(matchId: matchId) { messages in
            onChange(messages)
        }
    }

    func closeChat() {
        repository.openChat(matchId: nil)
    }

    func sendMessage(matchId: String, text: String) async {
        await perform { try await self.repository.sendMessage(matchId: matchId, text: text) }
    }

    /// Proposes where to meet, where the ride ends, when, and what it costs.
    func sendPickupProposal(
        matchId: String,
        pickupAddress: String,
        dropoffAddress: String,
        pickupTime: String,
        contribution: Double
    ) async {
        await perform {
            try await self.repository.sendPickupProposal(
                matchId: matchId,
                pickupAddress: pickupAddress,
                dropoffAddress: dropoffAddress,
                pickupTime: pickupTime,
                contribution: contribution
            )
        }
    }

    /// Agrees to a proposal. Safe to call twice — the confirmation's document id is derived from
    /// the proposal, so a repeat overwrites instead of posting another card.
    func confirmPickup(proposalMessageId: String) async {
        await perform {
            try await self.repository.confirmPickupProposal(proposalMessageId: proposalMessageId)
        }
    }

    /// The offer behind a match, so the chat screen can show what ride is being coordinated.
    func offer(for match: TripMatch) -> TripOffer? {
        repository.getTripOfferById(offerId: match.offerId)
    }

    // MARK: - Ratings

    func submitRating(toUserId: String, rating: Float, comment: String) async -> Bool {
        await perform("Submitting your rating…") {
            try await self.repository.submitRating(
                toUserId: toUserId,
                ratingValue: rating,
                comment: comment
            )
        }
    }

    /// Who this user has already rated, so the rating form stops offering them.
    ///
    /// A synchronous cache read, like `blockedUsers()` — the shared side keeps the list warm from
    /// `refreshNow()` and updates it optimistically on submit. Callers must hold the result in
    /// `@State`; reading it inline from a computed property gives SwiftUI nothing to invalidate.
    func ratedUserIds() -> [String] {
        repository.getRatedUserIds()
    }

    // MARK: - Profile management

    func updateProfile(name: String, lastInitial: String, avatarUrl: String) async -> Bool {
        await perform("Saving your profile…") {
            try await self.repository.updateUserProfileDetails(
                name: name, lastInitial: lastInitial, avatarUrl: avatarUrl
            )
        }
    }

    /// `bytes` should already be resized/JPEG-encoded to match Android's contract (512px max
    /// edge, quality 85) — see `ProfileImages.kt` on the Android side and the picker views that
    /// call this on iOS.
    func uploadProfilePicture(userId: String, imageData: Data) async -> Bool {
        await perform("Uploading your picture…") {
            _ = try await self.repository.uploadProfilePicture(userId: userId, bytes: imageData.toKotlinByteArray())
        }
    }

    // MARK: - Blocking

    func blockUser(_ userId: String) async -> Bool {
        await perform("Blocking this rider…") {
            try await self.repository.blockUser(blockedUserId: userId)
        }
    }

    func unblockUser(_ userId: String) async -> Bool {
        await perform { try await self.repository.unblockUser(blockedUserId: userId) }
    }

    /// Not `suspend` on the Kotlin side — a plain cache read, already populated by the time this
    /// is called (`refreshBlocks()` runs during `repository.start()`'s session-restore path).
    func blockedUsers() -> [User] {
        repository.getBlockedUsers()
    }

    // MARK: - Notifications

    /// In-app alerts only — neither platform has real push (FCM/APNs) today; these toggles are
    /// stored preference flags, not OS-level notification permissions.
    func toggleEmailNotifications(_ enabled: Bool) async {
        await perform { try await self.repository.toggleEmailNotifications(enabled: enabled) }
    }

    func togglePushNotifications(_ enabled: Bool) async {
        await perform { try await self.repository.togglePushNotifications(enabled: enabled) }
    }

    func markNotificationAsRead(_ id: String) async {
        await perform { try await self.repository.markNotificationAsRead(id: id) }
    }

    func clearNotifications() async {
        await perform { try await self.repository.clearNotifications() }
    }

    // MARK: - Safety filters

    func toggleWomenOnlyFilter(_ enabled: Bool) async {
        await perform { try await self.repository.toggleWomenOnlyFilter(enabled: enabled) }
    }

    // MARK: - Search

    /// Address suggestions ordered nearest-first from `fromLat`/`fromLon`.
    ///
    /// Pass `0, 0` when there is no location fix; the shared searcher then leaves Photon's own
    /// order alone rather than sorting against a point in the Gulf of Guinea. The ranking itself
    /// lives in `PlaceRanking` in `:shared`, so both platforms order results identically.
    ///
    /// Kotlin/Native exports every `suspend fun` as `async throws` to Swift regardless of whether
    /// the Kotlin side is annotated `@Throws` — the completion handler it bridges to always carries
    /// an NSError slot. `try?` is therefore required here even though `OsmLocationService` itself
    /// never lets an exception escape (it wraps its network call in `runCatching`).
    func searchPlacesRanked(_ query: String, fromLat: Double, fromLon: Double) async -> [RankedPlace] {
        guard !query.trimmingCharacters(in: .whitespaces).isEmpty else { return [] }
        return (try? await OsmLocationService.companion.searchPlacesRanked(
            query: query, fromLat: fromLat, fromLon: fromLon
        )) ?? []
    }

    /// Resolves a Google prediction to coordinates (a Place Details lookup). Returns nil for a
    /// result that already has coordinates — a Photon result or seed place — or when Google is off.
    func resolvePlace(providerId: String, sessionToken: String) async -> ResolvedPlace? {
        guard !providerId.isEmpty else { return nil }
        return try? await OsmLocationService.companion.resolvePlace(
            providerId: providerId, sessionToken: sessionToken
        )
    }

    // MARK: - Error handling

    func clearError() {
        errorMessage = nil
    }

    /// Surfaces a message through the same alert an action failure would use, for the cases the
    /// UI decides are errors before any call is made — a host with no eligible ride to offer, say.
    func setError(_ message: String) {
        errorMessage = message
    }

    /// Shows a transient confirmation, standing in for one of Android's `Toast` calls.
    func notify(_ message: String) {
        transientMessage = message
    }

    /// The load/error dance every action repeated. Returns whether the block succeeded.
    ///
    /// `message` names the action for the overlay; it is reset afterwards so the next caller that
    /// forgets to pass one gets the neutral default rather than the previous action's words.
    @discardableResult
    private func perform(
        _ message: String = AppViewModel.defaultLoadingMessage,
        _ block: @escaping () async throws -> Void
    ) async -> Bool {
        loadingMessage = message
        isLoading = true
        errorMessage = nil
        defer {
            isLoading = false
            loadingMessage = AppViewModel.defaultLoadingMessage
        }
        do {
            try await block()
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    /// The refresh counterpart of `perform`.
    ///
    /// Raises `isRefreshing` rather than `isLoading`, so a background or pull-to-refresh does not
    /// throw up the modal overlay, does not disable every form's submit button, and does not wipe
    /// an unread error.
    @discardableResult
    private func performRefresh(_ block: @escaping () async throws -> Void) async -> Bool {
        isRefreshing = true
        defer { isRefreshing = false }
        do {
            try await block()
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }
}

// MARK: - Device location

/// Where the device actually is, for ranking address suggestions by distance.
///
/// iOS has been shipping `NSLocationWhenInUseUsageDescription` in `Info.plist` since before there
/// was any CoreLocation code to use it. Android's equivalent chip had Northeastern's campus
/// hardcoded. Neither platform had ever read a real location, which is why a search for a home
/// address returned whatever OSM considered most important instead of what was nearest.
///
/// Kept here rather than in a new file on purpose: a new `.swift` file has to be added to the
/// hardcoded `SWIFT_SOURCES` list in `iosApp/generate-project.py` and the regenerated
/// `project.pbxproj` committed, and CI enforces both halves. Nothing about this needs its own file.
///
/// The Kotlin half is not shared either — `CLLocationManager` cannot be compile-checked on Linux,
/// the same reason `KeychainStore` was deferred. Only `PlaceRanking` is shared.
final class DeviceLocationProvider: NSObject, ObservableObject, CLLocationManagerDelegate {

    /// One manager for the whole app. Every address field observes this rather than owning its own:
    /// a form with an origin and a destination box would otherwise spin up two `CLLocationManager`s
    /// and ask twice, and a fix obtained by one field would not reach the other.
    static let shared = DeviceLocationProvider()

    /// The latest fix, or nil until one arrives. Callers pass `0, 0` downstream when it is nil.
    @Published private(set) var coordinate: CLLocationCoordinate2D?

    private let manager = CLLocationManager()
    private var hasRequested = false

    override init() {
        super.init()
        manager.delegate = self
        // Ranking suggestions needs a neighbourhood, not a lane. Asking for less is faster to fix
        // and lets a user who only granted "approximate" still get useful ordering.
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    /// Asks once, the first time an address field is focused, where the reason is on screen.
    /// A denial is final and silent — suggestions still work, just unordered.
    func requestIfNeeded() {
        guard !hasRequested else { return }
        hasRequested = true

        switch manager.authorizationStatus {
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse, .authorizedAlways:
            manager.requestLocation()
        default:
            break
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        switch manager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            manager.requestLocation()
        default:
            coordinate = nil
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let latest = locations.last else { return }
        coordinate = latest.coordinate
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Nothing to surface: every caller's fallback is "rank by something else".
        coordinate = nil
    }
}

extension Date {
    /// The models carry epoch milliseconds as Int64 throughout.
    var epochMillis: Int64 { Int64(timeIntervalSince1970 * 1000) }

    init(epochMillis: Int64) {
        self.init(timeIntervalSince1970: Double(epochMillis) / 1000)
    }
}

extension Data {
    /// Kotlin/Native exports `ByteArray` params as `KotlinByteArray`, which has no direct `Data`
    /// bridging — `uploadProfilePicture` is the first iOS call site to need this conversion.
    func toKotlinByteArray() -> KotlinByteArray {
        let array = KotlinByteArray(size: Int32(count))
        withUnsafeBytes { (raw: UnsafeRawBufferPointer) in
            for i in 0..<count {
                array.set(index: Int32(i), value: Int8(bitPattern: raw[i]))
            }
        }
        return array
    }
}
