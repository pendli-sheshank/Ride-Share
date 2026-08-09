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

    static let defaultLoadingMessage = "Just a moment…"

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
            store: UserDefaultsStore()
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

    /// A structured pickup proposal or confirmation — see `MessageType` in `:shared`.
    func sendPickupMessage(matchId: String, type: String, spot: String, time: String) async {
        let summary = type == MessageType.shared.PICKUP_CONFIRMED
            ? "Confirmed: meet at \(spot) at \(time)"
            : "Pickup proposal: \(spot) at \(time)"
        await perform {
            try await self.repository.sendMessage(
                matchId: matchId,
                text: summary,
                type: type,
                pickupSpot: spot,
                pickupTime: time
            )
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

    // Kotlin/Native exports every `suspend fun` as `async throws` to Swift regardless of whether
    // the Kotlin side is annotated `@Throws` — the completion handler it bridges to always carries
    // an NSError slot. `try` is therefore required here even though OsmLocationService itself
    // never lets an exception escape (it wraps its network call in `runCatching`).
    func searchPlaces(_ query: String) async -> [PhotonPlaceResult] {
        guard !query.trimmingCharacters(in: .whitespaces).isEmpty else { return [] }
        return (try? await OsmLocationService.companion.autocompletePhoton(query: query, limit: 6)) ?? []
    }

    /// [searchPlaces], ranked toward `biasLat`/`biasLon` — see `OsmLocationService.autocompletePhotonNear`
    /// in `:shared` for why this surfaces "Maryland Heights" ahead of the state of Maryland.
    func searchPlaces(_ query: String, biasLat: Double, biasLon: Double) async -> [PhotonPlaceResult] {
        guard !query.trimmingCharacters(in: .whitespaces).isEmpty else { return [] }
        return (try? await OsmLocationService.companion.autocompletePhotonNear(
            query: query, limit: 6, biasLat: biasLat, biasLon: biasLon
        )) ?? []
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
